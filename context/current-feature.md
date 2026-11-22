# Current Feature

Session Lease — `0.1.20` (in progress, branch `feature/session-lease`)

## Status

**Implementing in slices** (see the spec's Implementation section). `context/specs/session-lease-app-spec.md` is reviewed (replaces the superseded `fix-stale-page-overwrites-spec.md`). Extension `v0.2.0-rc.1` is tagged and pinned. Slices 1–6 done (build plumbing and `leaseTest`; lease messages and state; document-start injection; native parked scrim; Kotlin pre-check; foreground and background release).

The decision, the spike's evidence and the requirement map are in that repo's `context/decisions/` (`session-lease-spike.md`, which now holds the direction change and the architecture assessment); the shared contract is its `context/specs/ext-0.2-session-lease-spec.md`.

## Order

1. [x] TravelingTavern-Extension builds the lease in slices and tags **`v0.2.0-rc.1`** on its `dev`
2. [ ] Extension fixes the SillyTavern context hook bug (`v0.2.0-rc.2`) and makes `onForeground()` always report (`v0.2.0-rc.3`); submodule re-pinned to rc.3 before slice 8, then re-test "unreadable read, then recovers" (spec Notes, slice 5)
3. [ ] Feature branch here: submodule pinned to `v0.2.0-rc.1`; `0.1.20` implemented; app spec checklist (A10) and the extension's E14 checklist pass with the `leaseTest` build on the test install
4. [ ] The extension releases `v0.2.0` to its `main`
5. [ ] Re-pin the submodule to `v0.2.0`, re-run the build and unit tests, then merge
6. [ ] After the release: privacy check of `spike/session-lease`, tag `archive/spike-session-lease`, push the tag, delete the branch (ask first)

## History

### 2026-11-21 — Docs: 1.0.0 MVP scope
- Phase 1 complete = `1.0.0`, released as a signed APK on a GitHub Release. 1.8 renamed Me & Advanced (`ui/me/MeScreen.kt`); no Notify me row (returns with Phase 8); Appearance moved to a new Later list (roadmap, Screen Map, dynamic color line)
- New 1.10 Create a character (simple), `0.1.22`: + on Characters opens one screen (picture, name, description, first message), saved with SillyTavern's own `/char-create` callback, then the chat opens; Import later; Phase 3 grows the screen
- Findings in the 1.10 spec Notes (SillyTavern 1.19.1): a taken name makes a second card with a numbered file name; `sanitize-filename` strips `? : / …` from the name itself
- No code, no bump

### 2026-11-21 — Wine logo
- Launcher icon (and the Connect screen's `AppLogo`, drawn from the same layers) now uses `docs/brand/travelingtavern_logo_wine.svg`: wine-red background, cream tavern with the door cut out and its knob; monochrome layer for themed icons is the same shapes in one color
- Same 1024 canvas and 18dp inset as the old logo, so it fits the safe zone; the old purple `travelingtavern_logo.svg` is deleted (nothing else referenced it)
- Verified on emulators: app drawer (API 37) and themed icon on the home screen (API 35); not verified: Pixel 8. `versionCode` 27 (installed builds reached 26 on the session lease branch)

### 2026-11-21 — Decision: continue without protection
- No document-start support (or `addDocumentStartJavaScript` throws) no longer blocks completely: the scrim (slice 4) offers Update (Play Store page for Android System WebView) and Continue without protection
- Continue loads SillyTavern without the lease for this launch only, with a persistent "Not protected: data from another device can be overwritten" banner; asks again on next launch (user decision). Spec A2, A3, D13, D14
- Slice 3 follow-up: a throwing `addDocumentStartJavaScript` now counts as no support (no `loadUrl`, WARN log)

### 2026-11-20 — Phase 1.9 Personas — `0.1.19`
- Chip "Chatting as {name}" below the chat's message box; full-screen Personas (Active / Default badges, ⋯ dropdown: Edit, Set / Remove default, Delete via the chat's delete dialog, now `ConfirmDeleteDialog`); editor with picture, name, title, description + token count. Picking in a chat locks it to that chat
- Bridge calls SillyTavern's `/persona-*` command callbacks directly (macros kept as typed, unedited fields kept); pictures shrunk on the phone to a ≤ 1024 px JPEG
- User changes: dropdown instead of a sheet; horizontal ⋯; "Locked to this chat" badge dropped (it only repeated Active)
- Gotcha: the page's settings copy goes stale, and SillyTavern saves the whole file from it, which erased a persona made on the desktop. Persona settings are now re-read from the server on opening Personas, on app return and before each persona action; other settings still at risk (fix next)
- Verified on an emulator (API 35); not verified: the Me-tab path (1.8), deleting the active persona

### 2026-11-19 — Connect screen refresh — `0.1.18`
- One full-width Connect (test, then save and open Chats; no confirmation) replaces Test connection + Save; spinner with "Connecting…" below it while connecting; failure shows an inline error and keeps the inputs
- App logo at the top (`AppLogo` draws the launcher icon's layers); toggle renamed "Server requires authentication", whole row toggles; no tailnet wording (any reachable http(s) server works)
- Verified on an emulator (API 35, dark); not verified: light theme, authentication (server has auth off)

### 2026-11-19 — Phase 1.6c Chats Hide and Delete — `0.1.17`
- Long press a chat → sheet: Hide (app only, DataStore `avatar/chatFile` keys, snackbar Undo; ⋮ Show hidden chats while any is hidden) and Delete (SillyTavern's `deleteCharacterChatByName`, imported from `/script.js`; success = `CHAT_DELETED`, answered with `chatResult`)
- Deleting the chat open in the page calls `replaceCurrentChat()`, as SillyTavern's Manage chats does (otherwise the page keeps the deleted messages and a later save writes them into the next chat)
- User changes: deleting a whole chat confirms in an alert dialog with a bold red "This also deletes the chat on your SillyTavern." (Delete here, and 1.7's Restart, renamed **Delete and restart**); no "memories stay" line (Memory Books' lorebook is bound in the chat file)
- Hidden entries for missing chats are ignored, not pruned (the app only sees the 100 most recent chats). Gotcha: file-backed DataStore can't overwrite its file on a Windows JVM, so the store test uses an in-memory DataStore
- Verified on an emulator (API 35); not verified: Tailscale off, Delete disabled while generating, deleting a character's only chat

### 2026-11-19 — App icon
- Logo (`docs/brand/travelingtavern_logo.svg`, replaced by the wine logo on 2026-11-21) as the adaptive launcher icon: purple background, TT + door foreground inside the safe zone, monochrome layer for themed icons; legacy webp mipmaps removed (min SDK 31)

<!-- Newest first. Verified against SillyTavern 1.19.0 over Tailscale on the emulator and Pixel 8 unless noted. -->

### 2026-11-19 — Phase 1.7 Chat Screen (Story Chat) — `0.1.16`
- Story chat: message cards over ST's background, tap → actions sheet (Regenerate, Continue, Copy, Edit, Branch, Hide/Show, Delete), ⋮ Start new / Restart chat, native input bar (Write for me, Send/Stop), "…" card, scroll to newest; swipes never generate; 1.7b mic removed
- Groundwork: `ProcessLifecycleOwner` → `setAppState`, chat re-read on return, `chatFile` route + chat following, `singleTop` deep link (handled without task flags: `FLAG_ACTIVITY_NEW_TASK` recreated the Activity and blanked the WebView)
- User changes: Chats list one row per chat; composer icons inside the field; reasoning bar recolored, can't open (CSS only); background picture stays still as the page height changes; hidden messages stay solid
- Gotchas: input text must be Compose state (StateFlow round trip dropped letters); ST stacks `#chat` at z-index 30; tap handler skips `details`
- Verified on an emulator (API 35); on-screen keyboard and Pixel 8 not verified

### 2026-11-19 — Docs: 1.7 story chat spec; texting style → Phase 2
- 1.7 spec: story chat design (cards, tap → actions sheet, ⋮ menu, native input bar, `PageThemeStore`), cleanup list, lifecycle/reload/deep-link rules. ST input area hidden; Regenerate = new swipe on latest message; swipes never generate; ⋮ disabled while generating
- Texting style: own spec, Phase 2, app-only, per chat. Later phases renumbered (final = Phase 8)
- New 1.9 personas spec (chip, full-screen list, editor; ST features only; picking in a chat locks it)
- 1.7b in-app mic replaced by keyboard voice typing (`RECORD_AUDIO` stays)
- No code, no bump

### 2026-11-18 — Docs: main chat only; proactive messages → final phase
- ST's main chat is the only conversation (EchoText dropped: invisible to Memory Books, lorebooks, vectors)
- Proactive messages/reminders generated on desktop by ST in a headless page via TravelingTavern-Bridge
- No code, no bump

### 2026-11-18 — Fix: new avatar not showing — `0.1.15`
- Cause: Coil caches by URL; ST list only read at load. Avatar URLs carry `tt_v=<date_added>`; `requestCharacters(fromServer)` calls `getCharacters()`; pull to refresh on Characters

### 2026-11-18 — Fix: stuck on "Can't reach" after reinstall — `0.1.14`
- Cause: Auto Backup restored stale server address. Settings DataStore excluded from backup (fresh install → Connect)
- Unreachable banner: Retry + Server settings; `WebViewHolder` reloads on settings save while in error

### 2026-11-18 — Phase 1.7b Voice Input — `0.1.13` (since removed in 1.7)
- On-device `SpeechRecognizer` mic in ST's input row; 4 s pause tolerance
- First connected Compose run uninstalled the app from the Pixel (now in release rules)

### 2026-11-18 — Fix: hide EchoText UI in clean mode — `0.1.12`
- `body.tt-clean ~ #et-portal > * { display: none !important }` (portal is a `<body>` sibling)

### 2026-11-18 — Fix: crash after reinstall with restored backup — `0.1.11`
- Cause: Tink keyset restored but its Keystore key wasn't. Keyset excluded from backup; `loadKeysetOrReset()` recreates it on failure
- Not verified on Pixel

### 2026-11-17 — Phase 1.6b Lorebooks Tab — `0.1.10`
- Lorebooks tab (search, filter chips, grid, create/delete), detail (global switch, entries) and entry editor (title, keys, content, enabled; other fields preserved)
- Gotcha: ST modules must be imported by absolute URL from injected scripts
- Not verified: persona links (none on server), manual pull to refresh

### 2026-11-17 — Fix: 3 s cap on opening a chat — `0.1.9`
- `openChat()` reveals chat after 3 s without `chatOpened` (WARN log)

### 2026-11-17 — Phase 1.6 Chats & Characters Tabs — `0.1.8`
- `recentChats` via `POST /api/chats/recent`; `chatOpened` after `selectCharacter`; Coil uses shared OkHttp
- WebView hosted in app shell, always attached; covered by opaque surface on non-Chat screens (unfocusable, hidden from accessibility)
- Chats list, Characters grid (search, add sheet "Coming soon"), Chat screen with cover until `chatOpened`
- No reload across 13 list↔Chat round trips

### 2026-11-17 — Docs: Compose UI tests going forward
- UI tests for new/changed native screens; run only before release

### 2026-11-17 — Phase 1.5 Clean Theme — `0.1.7`
- `clean-theme.css` under `body.tt-clean`: hides toolbar, drawers, TopInfoBar; full-height `#sheld`; ≥16px text, ≥48dp buttons
- `setClean`, `installStyle`; `selectCharacter` without editor
- Gotcha: forcing `display` on input buttons showed hidden `#mes_stop`

### 2026-11-17 — Fix: WebView load timeout + status-bar strip — `0.1.6`
- `LoadWatchdog` 15 s (reset by `onPageCommitVisible`); Tailscale-off error now ~18 s (was ~2 min)
- Strip: transparent WebView background + `clipToBounds()`

### 2026-11-17 — Phase 1.4 JS Bridge Foundation — `0.1.5`
- `bridge.js` + `st-selectors.js`; `{ v: 1, type }` messages; frozen `window.__ttBridge`; double-run guards
- Listener for saved origin only, main frame; scripts injected on every `onPageFinished`; parser never throws
- `PageError.WebViewOutdated` when `WEB_MESSAGE_LISTENER` unsupported

### 2026-11-17 — Android CI
- GitHub Actions: build, unit tests, lint on PRs/pushes to `dev`/`main`. `gradlew` made executable. `main` protected; `dev` created

### 2026-11-17 — Min SDK 31 — `0.1.4`

### 2026-11-17 — Phase 1.3 WebView Host — `0.1.3`
- `WebViewHolder` singleton on `MutableContextWrapper`; basic auth for saved host only; other hosts → browser; Aegis file chooser/permissions; `adjustResize`
- Not verified: basic auth, camera

### 2026-11-16 — Phase 1.2 Connect Screen — `0.1.2`
- URL normalization; DataStore + Tink/Keystore credentials; shared OkHttp with host-scoped basic auth; test = `GET /` checking `<title>SillyTavern</title>`; Save only after a passing test
- Auth paths covered by unit tests only (server has auth off)

### 2026-11-16 — Phase 1.1b Bottom Navigation & Style — `0.1.1`
- Tabs Chats/Characters/Me + full-screen routes; amber accent, Inter font, dynamic color off by default

### 2026-11-16 — Phase 1.1 Project Setup — `0.1.0`
- Toolchain (AGP 9.0.1, built-in Kotlin): SDK 36 pins `core-ktx 1.17.0`, `lifecycle 2.9.4`, `activity-compose 1.11.0`, Compose BOM `2025.09.01`; Kotlin plugins `2.2.10`; Hilt `2.60.1`; KSP `2.3.4`; `material-icons-core` explicit; `JAVA_HOME` → Android Studio JBR