# Fix: Stale Page Overwrites — `0.1.20`

Replaces the earlier draft. App side only; the guard's behavior is in `ext-0.1-save-guard-spec.md` (**read it first**). All decisions: user + design reviews, 2026-11-20.

## Overview

**Rule (very high priority):** nothing TravelingTavern runs may delete or overwrite SillyTavern (ST) data unless the user confirmed it in a dialog. Saving an older copy over newer data counts as deleting.

**Bug:** an ST page reads data once and later saves the **whole file** from its copy, erasing changes made elsewhere since. Found in 1.9 (a desktop-made persona erased by the app's settings save). It goes both ways: an old desktop tab erases the phone's changes.

**Invariant:** every whole-file save from a TravelingTavern-controlled page is conditional: sent only if the server's file is still the version that copy came from. Otherwise blocked (this version) or merged (later).

| Data | Whole-file save | Guarded |
|---|---|---|
| Settings (personas, presets in use, extension settings…) | `POST /api/settings/save`, debounced 1 s after many actions incl. opening a chat | Yes |
| Lorebooks | `POST /api/worldinfo/edit` from `worldInfoCache` (never re-read once loaded). App's entry editor, Memory Books | Yes |
| Chats | `POST /api/chats/save`. ST's `integrity` id names the chat, not a version: an old copy of the same chat saves over newer messages | Yes |
| Characters | `POST /api/characters/edit`. **Every chat open / new / restart saves the whole card** to record the current chat | Yes |
| Presets, Quick Replies, themes, groups | ST's own UI; not reachable in clean mode | **No. Blocker for 1.8 Advanced** |

**Parts**

1. **Save guard** `save-guard.js`: lives in the extension repo; one file for app, desktop tabs, headless page
2. **Extension** on the desktop: the only way to guard desktop tabs. Optional for the user, strongly prompted
3. **App (this spec):** inject the guard, keep the page fresh, react to guard messages, offer the extension

**Layers of protection:** app + extension are the complete baseline; no user needs more. The server plugin (Phase 8) is an optional upgrade (Roadmap).

## Which Guard Copy Runs Where

- **App WebView:** the app's bundled copy, injected at **document start**, before ST's scripts (sees the first settings load). Always injected. If the extension is installed, its copy finds `__ttSaveGuard`, stands down: it only sets `window.__ttExtension = { version, guardVersion }` and dispatches `tt-extension-ready`
- **Desktop tab:** the extension's copy
- **Phase 8 headless page:** Playwright injects the same file

## Freshness

`checkFresh` (bridge command → `__ttSaveGuard.checkFresh()`) runs:

- on return from background (`setAppState(true)` after a background period; `ProcessLifecycleOwner`, never Activity `onPause`/`onStart`, which fire on rotation)
- on opening Personas, the Lorebooks tab, a lorebook or entry editor, Advanced (1.8); on bottom-navigation tab switches. In the background while the screen opens
- before each persona action (pick, save, create, delete, set default)

**Before sending a chat message: `checkChat` only** (bridge command → `__ttSaveGuard.checkChat()`: the quick chat check, no settings read, never a page reload). Stale settings can't erase anything at send time; the guard blocks that save anyway

Results:

- **settings changed** (`staleSettings`) → reload the page through `WebViewHolder`, keeping the screen (Chat: cover, reopen the same chat via the `chatFile` route), then run the pending action on fresh data. ST can't re-read settings without a page load
- **lorebooks changed** (`staleLorebooks { names }`) → cache already dropped by the guard; lorebook screens re-read
- **chat changed** → re-read the chat (as 1.7 does on return)

Rules:

- **At most one automatic reload per 30 s.** If another is due sooner, mark the page stale and reload at the next safe moment after that. Stops reload loops while the desktop is changing settings
- **A pending action survives the reload:** "reload first, then the action" means the ViewModel holds the action (e.g. the persona pick) until the bridge reports ready again, then runs it once
- **A reload waits** while a reply is generating (as `reloadChat` does) and while a bridge command is in flight (its answer would be lost). One reload at a time. Chat input text is Compose state and survives
- Settings checks are limited by the guard to one per 5 s. A desktop change inside that window isn't seen by the check; the save that follows is then blocked by the guard, so it is safe, only less smooth
- **The 1.9 persona re-read is removed.** It copied server persona fields into the page behind the guard's back, so the page held data newer than its fingerprint and `hadChanges` was wrong. `checkFresh` replaces it
- No timer polling
- **Phase 2 note:** page-memory-only state (the texting instruction) must be re-applied after every reload, like clean mode and theme (`BridgeInstaller.injectScripts`)

## Guard Messages → App Reactions

**`saveBlocked { kind: "settings"|"lorebook"|"character", name?, reason: "changed"|"unreachable"|"unseen"|"chatStale", hadChanges: true|false|null }`**

| Case | App does | Snackbar |
|---|---|---|
| `staleSettings` (a no-change save wasn't sent, or `checkFresh` found a change) | reload (keep screen) | none |
| settings, `hadChanges: false` | reload (keep screen) | none |
| settings, `hadChanges: true` | reload | "Settings changed on another device, so a change made on this phone wasn't saved. The latest version is loaded." |
| settings, `hadChanges: null` (first save after load) | reload | Only if the app sent a settings-changing bridge command since this page loaded (persona pick / save / create / delete / set default; the app knows exactly): "Settings changed on another device. If you just changed a setting on this phone, check it and change it again." Otherwise none |
| lorebook | lorebook screens re-read | "Lorebook {name} changed on another device, so a change made on this phone wasn't saved. The latest version is loaded." |
| lorebook, `reason: "chatStale"` | nothing (the chat is being reloaded) | none. Only extension saves (Memory Books) can get this: the app's own entry-editor saves are marked by `bridge.js` (`__ttUser: true`) and skip the stale-chat freeze, so a user's lorebook edit is never dropped silently |
| character, `hadChanges: true` (a real card edit; only from Advanced) | re-read character list and open chat | "{name} changed on another device, so a change made on this phone wasn't saved. The latest version is loaded." |
| character, `hadChanges: false` / `null` (a plain chat open the guard couldn't classify; the current chat is still recorded) | re-read character list | none |
| `reason: "unreachable"` | — | "Couldn't reach your SillyTavern, so a change made on this phone wasn't saved." |

"A change made on this phone", not "your change": it may be Memory Books'. Unknown `kind` / `reason` → log, treat as settings with changes.

**`chatSaveRefused { avatar?, groupId?, chatFile }`** (ST's own keys, per the coding standards; chats are never merged):

- re-read the chat. ST's integrity popup never opens in the app (the guard answers for the server), and no reply is generated from a stale chat (the guard refuses generation)
- **restore unsent text:** the Chat ViewModel keeps the last sent message (text **+ send time**) until a re-read shows it saved. If the re-read chat has no message with that text at or after that time → text back in the input box. (Text alone fails for a repeated "ok")
- snackbar, no dialog: "This chat has new messages from another device. They're loaded now, and your message is back in the box."

**`guardInfo { guardVersion, extensionVersion?, extensionGuardVersion?, stVersion, untestedServer }`** (per page load; again when the extension announces itself):

- **extension missing** → one-time dialog after connecting + Settings row "Desktop protection: Not installed"
    - Title "Protect changes made on this phone"
    - Body "A SillyTavern tab left open on your computer can overwrite changes made on this phone. Install the TravelingTavern extension on your SillyTavern to prevent it."
    - **Install** / Not now
- **`extensionGuardVersion < guardVersion`** (both integers; `extensionVersion` is a semver for display only) → row "Update available" + **Update**
- **One-tap install / update** via ST's API: `/api/extensions/install` `{ url, branch: "release" }` (per-user; no admin), `/api/extensions/update` `{ extensionName, global }`. Confirmation dialog → call → reload the app's page → "Reload SillyTavern on your computer to turn on protection there." If the call fails: show the repo URL for ST's **Install extension**
- **`untestedServer`** → Settings row "Not tested with SillyTavern {stVersion} yet. Protection may be incomplete." Also checked on Connect
- The phone is guarded with or without the extension

Snackbars: existing per-screen pattern (`ChatsScreen`'s `SnackbarHostState`), one-shot flows from the repository **(decide when implementing)**.

## Kotlin Side

- **Submodule:** `TravelingTavern-Extension` at `extension/`, pinned to tag `v0.1.0`. Gradle `Copy` task: `extension/save-guard.js` → generated assets dir added to `main` (served as `web/save-guard.js`). No committed copy. Missing file → fail with "save-guard.js not found. Run: git submodule update --init". The tag's `GUARD_VERSION` = bundled guard version
- **CI:** `.github/workflows/android-ci.yml` checkout with `submodules: true` (missing today)
- **`BridgeInstaller`:** `WebViewCompat.addDocumentStartJavaScript(save-guard.js)` for the server origin only, when `WebViewFeature.DOCUMENT_START_SCRIPT` is supported **(verify on emulator + Pixel 8)**. Unsupported → inject with `bridge.js` at `onPageFinished`, log WARN; the guard then seeds itself from the page's own state (ST's exported `settings`), not a read-back, so nothing is missed
- **`BridgeMessage`:** `SaveBlocked(kind, name, reason, hadChanges)`, `StaleSettings`, `StaleLorebooks(names)`, `ChatSaveRefused(avatar, groupId, chatFile)`, `GuardInfo(guardVersion, extensionVersion, extensionGuardVersion, stVersion, untestedServer)` + parser cases + repository flows
- **`bridge.js`:** `checkFresh` and `checkChat` commands; remove the 1.9 settings re-read; set `__ttUser: true` on lorebook data saved from the app's entry editor (the guard strips it)
- **Refused generation:** the page sees an abort (like Stop). The Chat screen's "…" card and Send/Stop state must end cleanly **(verify with the extension spec's refusal mechanism)**
- The app tracks "sent a settings-changing bridge command since page load" (reset on `onPageStarted`) for the `hadChanges: null` snackbar
- **Extension install / update:** small client in `data/` behind a use case

## Investigate First

- **Two tabs + a throwaway chat: confirm an old copy of a chat saves over newer messages** (read from source, not run). The chat guard depends on it
- Extension install / update: what the calls return; that a per-user install shows for the signed-in user
- `/api/settings/get` size and time over Tailscale; quick chat check time with a 1000-message chat. Adjust the 5 s limit if needed
- Document-start script support (emulator WebView, Pixel 8)

## Order of Work

1. Create public `TravelingTavern-Extension`; build the extension spec; tag `v0.1.0`; point `release` at it
2. This spec: submodule, CI, app side

## Testing

- **Unit (Kotlin):** parse every new message (unknown kind / reason, malformed); reactions per table; text restored only when missing from the re-read chat (incl. repeated text); `checkFresh` triggers (return, screen open, persona action); `checkChat` before send (no reload from it); reload waits for generation and in-flight commands; extension prompt states (missing, outdated by integer guard version, current, untested server); reload limit (second reload within 30 s deferred); pending persona action runs once after the reload; `null` snackbar only after a settings-changing command; `chatStale` shows nothing
- Guard logic is tested in the extension repo
- **Never edit the live page's copy of real data** (erased a persona in 1.9 testing). Throwaway persona / lorebook / chat only
- **Manual** (emulator, ST 1.19.0, extension `v0.1.0` installed unless noted):
    - [ ] Desktop creates a persona, app in background → on return the page reloads; persona shows
    - [ ] Phone saves a setting once (exact base). Desktop creates a persona, app stays open → opening a chat sends no save; silent reload; persona kept
    - [ ] Desktop changes settings, phone (not refreshed) picks a persona → reload first, pick applied on fresh settings; both kept
    - [ ] Desktop edits a lorebook entry the phone has open → phone save blocked, snackbar, desktop edit kept, editor shows the latest
    - [ ] Desktop edits an entry; Memory Books on the phone adds a memory from its old copy → both in the file, no snackbar
    - [ ] Desktop edits a character description; stale phone opens that character's chat → description kept, no snackbar
    - [ ] Desktop adds chat messages; phone sends → refused, chat re-read, text back, snackbar; no reply generated; page not stuck
    - [ ] "ok" sent twice, second refused → "ok" back in the box
    - [ ] Chat goes stale while an entry is being edited in the Lorebooks tab → the entry save still goes through (or is blocked with the normal lorebook snackbar if the lorebook itself changed)
    - [ ] Desktop changes a setting; phone sends a message → no page reload at send; message sent normally
    - [ ] `force: true` never sent; a server `integrity` answer shows no popup and doesn't hang (open another chat after)
    - [ ] Desktop changes settings repeatedly while the phone is open → no reload loop, no repeated snackbars
    - [ ] Nothing changed elsewhere → saves go through; no reloads or snackbars
    - [ ] Return during a generation → check waits. Reload due during a persona save → waits for its answer
    - [ ] Server unreachable at save → not sent, snackbar
    - [ ] Extension not installed → dialog + Settings row; phone still guarded. One-tap install → on the desktop after a reload
    - [ ] Extension installed → one guard in the app's page; `guardInfo` shows its version
    - [ ] Old desktop tab clicked around after a phone-made persona → persona survives
    - [ ] CI builds from a fresh clone
- `./gradlew build`; `versionName` `0.1.20`, `versionCode` 21

## Docs to Update

- `CLAUDE.md` Related Repos: add **TravelingTavern-Extension** (save guard in every ST page; source of `save-guard.js`; submodule; `git clone --recurse-submodules`)
- `context/project-schema.md` → Built Toward the Final Phase: "**Every whole-file save is conditional.** No TravelingTavern-controlled page sends a whole-file save unless the server's file is still the version that copy came from"; add the Roadmap below
- `context/specs/phase1-9-personas-spec.md`: settings re-read replaced by `checkFresh`
- `context/specs/phase1-8-settings-advanced-spec.md`: **blocked** until presets, Quick Replies, themes, groups and full character edits are guarded
- `context/current-feature.md` history

## Not Changed

- ST's chat integrity check (covers a different case)
- No app-side copies of ST data; fingerprints live in the page and are only compared
- No snapshots, no merge in this version

## Roadmap (decided, not built here)

**Accepted risks for now:** (1) check-then-save gap: two saves of one file within a fraction of a second; (2) the quick chat check missing an edit to an older message. The plugin closes both. (The desktop tab's startup window is closed: the guard seeds from ST's exported `settings`.)

**Before 1.8:** guard presets, Quick Replies, themes, groups, full character edits.

**Next guard version: merge** (new extension tag, submodule bump)

- Three-way: base = what the saving copy came from, mine = payload, theirs = server. Never use a merged result as base unless the page now holds it: lorebooks → put the merge in `worldInfoCache`; settings → base stays the page's last payload, reload at the next safe moment
- Lorebooks: per entry by `uid`. Same uid added on both sides → conflict (**no renumbering**: Memory Books references uids). Same entry changed differently → keep server's
- Settings: per JSON path; arrays as one unit; noisy paths keep the server's value
- Conflict → keep the server's version, snackbar. Dialog only to offer "Use my version instead" (an overwrite needs confirmation)
- Chats never merged. No extra lorebooks or conflict copies, ever
- Lorebook snapshots before each merge / re-apply (ST has no lorebook backups), stored via ST user files, not as lorebooks

**Phase 8: TravelingTavern-Bridge server plugin (optional hardening, required only for proactive features)**

- Optional because it runs with full access to the PC and needs manual setup (`enableServerPlugins: true`, `node plugins.js install <url>`, restart; consider `enableServerPluginsAutoUpdate: false`)
- Can't intercept ST's save routes, so the guard's `conditionalWrite` calls the plugin's own endpoint when installed: lock file → compare version → write → unlock. Page-side compare stays as the fallback
- Cheap version tokens (file hash): replace the large settings read and the quick chat check
- Change notices to every page ("settings changed, version {hash}", no data; the page re-reads through ST's API and checks the hash). Replace focus / screen-open checks
- Leases per character (chat + lorebooks): one writer at a time (phone, headless page, desktop tab), kept alive by the app's heartbeat
- Writes through ST's routes, or clears ST's server caches when writing files directly; validates file names; only the calling user's data **(verify a plugin route sees the ST user)**
