# TravelingTavern `0.1.20` — Session Lease (App)

> **Reviewed** (2026-11-20). Items marked **(verify)** must be confirmed before they're built on.

Replaces `fix-stale-page-overwrites-spec.md` (superseded). The lease itself, its files, messages, hooks and version numbers are defined **once** in TravelingTavern-Extension's `context/specs/ext-0.2-session-lease-spec.md` (cited as **E1–E15**); this spec references them and never restates them. Requirements: that repo's `context/decisions/session-lease-spike.md` (cited as **R…**).

**Requirements this spec owns:** R25, R26, R27, R28, R29, R33, R37, R38, R39, R43, R44. **Links:** R14 (testing, E14), R15 (desktop release, E5), R40 (one instance per page, E11), R42 (CSRF, E11).

## A1. Purpose

**Rule:** nothing TravelingTavern runs may delete or overwrite SillyTavern (ST) data unless the user confirmed it in a dialog. Saving an older copy over newer data counts as deleting.

**Design:** one active page per ST user (E1). The page that holds the lease writes; every other page is parked, with its requests held, until the user taps **Use here**, which claims the lease and reloads the page from fresh data.

**What the app owns:** injecting the bundled lease at document start, the native parked state, the Kotlin pre-check before bridge commands, foreground and background behavior (incl. the release written from Kotlin), the reply foreground service, the no-connection banner, extension install and update, and the `leaseTest` build.

**Protection between devices needs the extension on every desktop browser used with SillyTavern.** A desktop tab without it doesn't take part in the lease (E13): it can save over the phone's changes, and the phone, as holder, can save its copy over changes that tab made, because nothing tells either side the other exists. The app offers to install the extension (A8) and, until one is reported, keeps its own re-read of persona settings (Changes to Existing Features).

## A2. Injection (R26, R33; links R40)

**Bundled copy**

- Submodule `TravelingTavern-Extension` at `extension/`, pinned to tag `v0.2.0`. While `0.1.20` is built and tested, it pins the release candidate `v0.2.0-rc.1` (tagged on the extension's `dev`); it is re-pinned to `v0.2.0` before this feature merges. Never an untagged commit. A Gradle `Copy` task puts `extension/session-lease.js` into a generated assets dir added to `main` (served as `web/session-lease.js`); no committed copy. Missing file → the build fails with "session-lease.js not found. Run: git submodule update --init"
- A Gradle task reads `LEASE_VERSION` from the submodule's `session-lease.js` into `BuildConfig.LEASE_VERSION`
- CI (`.github/workflows/android-ci.yml`) checks out with `submodules: true`

**Document start, required**

- `BridgeInstaller` adds `session-lease.js` with `WebViewCompat.addDocumentStartJavaScript` for the saved server's origin only, before the first load (verified: Notes, slice 3). The lease then exists before ST's first request, and every request waits for the start decision (E6). This answers R33: first requests are held at document start by the injection itself
- **No document-start support** (`WebViewFeature.DOCUMENT_START_SCRIPT` missing, or `addDocumentStartJavaScript` throws) → the app doesn't load SillyTavern at first. It shows the native state "Update Android System WebView" (A3) with **Update** (the Play Store page for Android System WebView) and **Continue without protection**. No page-load fallback for the lease
- **Continue without protection** (user decision 2026-11-21): loads SillyTavern without the lease, for this app launch only, with a persistent warning banner "Not protected: data from another device can be overwritten". The next launch asks again. Nothing is remembered
- `bridge.js` and the clean theme are still injected at `onPageFinished`, as today
- **Login page:** the injected copy doesn't install on SillyTavern's login page (E11: URL pathname ending in `/login`), so logging in works before the lease can read its files; the lease starts with the next page load after login
- **Takeover reloads:** a page that parked while a message was being edited makes ST's `beforeunload` ask "Leave site?" on the reload (E13). The app answers `WebChromeClient.onJsBeforeUnload` with confirm **only while a takeover reload is in progress** (the last `leaseState` reason is `claiming` or `reloadPending`); otherwise it keeps the default behavior. Discarding the edit is correct: it is stale

**One instance per page** (R40, E11): the bundled copy is always first; an installed extension stands down and only announces itself, so `leaseInfo` reports its version (A8).

**Current code, never cached** (R26, safety)

- The bundled copy is injected from the APK's assets, never from the HTTP cache
- `leaseInfo.leaseVersion` ≠ `BuildConfig.LEASE_VERSION` → the page runs lease code other than the app's own: the app shows the parked state "Restart TravelingTavern" (A3) and refuses bridge commands (A4). With document-start injection from assets it shouldn't happen (Notes, slice 3); the check stays as a safety net
- After an extension install or update from the app (A8), the app calls `WebView.clearCache(true)` before reloading, so the page loads the new extension files and `leaseInfo` reports the new version. **Only the cache:** never cookies (the ST session) or web storage (`sessionStorage`, which holds the page id, and ST's `localStorage`)

## A3. Native Parked State (R28, R29, R38)

**Where:** one scrim above the whole `Scaffold`, so it covers every screen **including the bottom tab bar** (the spike's scrim sat inside it). It swallows every touch below it.

**When it appears:** hides the keyboard (`LocalSoftwareKeyboardController`) and clears text focus (`LocalFocusManager`), so nothing can be typed behind it.

**Driven by** the page's `leaseState` (E11), plus two states only the app knows:

| State | Title | Line | Control |
|---|---|---|---|
| `held`, `lost` | In use on another device | Use SillyTavern on this phone instead? | **Use here** |
| `claiming` | Taking over… | Loading your chat on this phone. | progress |
| `waitingForReply` (R29) | Finishing a reply… | Your other device is still writing a reply. | progress |
| `lostRace` | Another device took over | It claimed SillyTavern first. | **Use here** |
| `unreadable` | Can't reach SillyTavern | Nothing is saved from this phone. | **Use here** |
| `unknownFormat` | Update needed | Update TravelingTavern to use SillyTavern here. (The app's copy is the outdated side, E2) | none |
| `reloadPending` (E7) | Reload needed | Reload to finish taking over on this phone. | **Reload**: `WebView.reload()` |
| app: lease version mismatch (A2) | Restart needed | Restart TravelingTavern to use SillyTavern here. | none |
| app: no document-start support (A2) | Update needed | Update Android System WebView to use SillyTavern here. | **Update** (Play Store), **Continue without protection** (A2) |

- Follows `ui-guidelines.md`: neutral surface, one amber primary button, 48 dp touch targets, no decoration
- **Use here** calls `window.__ttLease.useHere()` (E11) and shows `claiming` at once
- Holder: no scrim. Before the page's first `leaseState`: no scrim; a command the user started then shows a loading bar (A4)

## A4. Kotlin Lease Check (R27, R39)

**Before every bridge command** (`BridgeClient.run()`), unless exempt:

- Read the lease file (E2, through the app's `OkHttpClient` with the WebView's cookies, `Cache-Control: no-cache`) and compare its pair (`holder`, `epoch`) with the last `leaseState`'s `pageId` and `epoch` (E2 pair rule). A check at most 2 s old is reused
- **Parked → refused up front** (R39), never left waiting on a held request. The command isn't run; the scrim is already showing
- **Pair changed or unreadable** → the app shows the parked state at once (`lost` / `unreadable`), refuses the command, and calls `window.__ttLease.onForeground()` so the page parks too
- **Lease version mismatch** (A2) → refused
- **Before the page's first `leaseState`:** the command waits for it, bounded at **5 s**. When it arrives, the normal check above runs. No report within 5 s → the command is refused (no parked screen: that appears only when the page reports parked or unreadable). A command is never run when the page would hold it
    - **Started by the user** (a tap): a loading bar along the top of the app shows while it waits, and a refusal shows a short message, "SillyTavern is still loading, try again in a moment." Both are drawn once for the whole app shell (D24)
    - **Started by the app** (`setAppState` and the list and chat re-reads on return, the Chats list on leaving Chat, Personas' refresh on opening, token counts while typing): it waits the same way, but a refusal is only logged: no bar, no message, no retry. Each runs again the next time its trigger comes (D25)

**Exempt: only commands that touch nothing but the page's DOM or CSS** (R27):

- `revealMessage`, `endReveal`: scroll and a body class
- `setCleanMode` (`bridge.js` `setClean`: toggles one body class) and `setPageTheme` (`setTheme`: sets `--tt-*` CSS variables and a body class). Confirmed in `bridge.js`: no request, no ST call, no save. They bypass `run()` today and stay outside it
- **`setAppState` is checked:** on return it runs the persona sync, which reads and can save

Native screens keep the page's last data while parked, under the scrim; nothing refreshes until Use here.

## A5. Foreground and Background (R43, R44; links R15, R42)

**Foreground** (R43, `AppForegroundTracker.returns`): the app calls `window.__ttLease.onForeground()` (E11), before `setAppState(true)` on the same return. A release upload still in flight is waited for first (bounded, D28): otherwise the page could confirm its pair before the marker lands, and a marker landing afterwards would make a foreground holder look released, so the desktop could claim on its own.

- Holder: the page confirms its pair; if the app's release marker still matches (nobody took over), it bumps its epoch so the marker stops counting
- Parked: the page takes over automatically **only if the lease was released**; otherwise the native parked state stays and the user decides

**Background** (R44, `AppForegroundTracker.isForeground` → `false`):

- Only a holder releases. Kotlin writes the release marker (E2: `format`, `holder`, `epoch` from the last `leaseState`, `releasedAt`) with `POST /api/files/upload` through the app's `OkHttpClient`, the WebView's cookies and ST's CSRF header. It doesn't use the page, so it works with the page's JavaScript paused or frozen
- **Deferred while a reply generates** (`leaseState.busy`): A6 decides what happens (slice 6 releases nothing then; slice 7 fills the hook, D29)
- Started at once on going to the background, on the app scope, never a ViewModel's (Android may freeze the process about 20 s after Home)
- **CSRF token** (R42, E11): Kotlin fetches its own from `GET /csrf-token` with the WebView's cookies (ST returns the session's existing token, so the page's keeps working). Memory only; fetched again after a `403`; never logged or stored
- **Bounded retry**, the same as lease writes (E3): a failed send (e.g. Windows `EPERM`, answered as 500) is retried after a random 100–200 ms, at most 3 attempts in total. No retries after that; the desktop falls back to Use here
- **Killed app:** writes nothing; the desktop shows Use here (E5). A late marker written as a frozen app thaws is harmless (E2)

## A6. Reply in the Background (R25, decided)

**Android 14+ (API 34+): `shortService` foreground service, plus a 45 s deadline** (D30)

- Started **when a reply starts** (`leaseState.busy` turns `true`), while the app is still in the foreground (starting a foreground service from the background is restricted), and stopped once the reply and the release (or interrupt) that follows it are done (`stopServiceAfter`, D30). Verified: Notes, slice 7
- It keeps the app's process out of the cached state, so Android doesn't freeze the process or the WebView's renderer (verified with a heartbeat; `setRendererPriorityPolicy` isn't needed: the renderer follows the app's importance)
- **Why a deadline too:** Chromium freezes the hidden page itself 60 s after the app goes to the background, whatever the process state (measured: Notes, slice 7). The service can't prevent that, and a frozen page doesn't send SillyTavern's save of the partial reply until it's visible again. So a reply still running **45 s** (`BACKGROUND_REPLY_DEADLINE_MS`) after going to the background is interrupted (below), early enough that the whole interrupt finishes before the freeze
- When the reply ends in the background before the deadline: cancel the deadline, then the release (A5) **after ST's save of the reply**: the page's save watch is armed (`watchChatSave`) and the release waits for `chatSaved`, bounded by the same 3 s as the interrupt (D30). The service stops once that release is written (or the interrupt's, below), not at `busy: false`, so the process keeps its importance for the save and the upload. Returning to the foreground also cancels the deadline (the reply goes on there)
- **Time limit** (3 minutes for `shortService`, verified), a safety net now that the deadline comes first: `onTimeout` with the app in the foreground → stop the service, nothing else. In the background → interrupt (below) **before** stopping the service, then stop it. Android removes the service's importance 5 s after the limit and treats the app as not responding 10 s after it (verified), so the service stops at the latest 8.5 s after `onTimeout`
- Manifest: the service with `foregroundServiceType="shortService"`; `FOREGROUND_SERVICE` permission only (verified: no type-specific permission)
- **Not the extension's 60 s:** the 60 s `busy` limit in E4 / E8 is how long a *claiming* page waits for the holder's `busy` to clear. It is unrelated to Chromium's 60 s page freeze, and neither changes the other: an interrupted reply clears `busy` within the shared bound below, so a claimer never waits it out

**Android 12–13 (API 31–33): no foreground service.** Going to the background during a reply → interrupt (below). No other foreground-service type is used.

**Interrupt**, in this order (D30):

1. Stop the reply: `bridge.js` `interruptReply` arms a watch for the page's own chat save, then calls ST's stop
2. Wait, with **one** bound of 3 s for both, for `leaseState.busy` to turn `false` **and** for `chatSaved`: the page's report that ST's save of the partial reply completed (any status). **Answered (verify):** ST's chat save is **not** sent by the time `busy` turns `false`; the page reports `busy: false` as soon as the generation request ends and ST saves 0.2–1.1 s later (Notes, slice 7), hence the save report
3. Write the release (A5), as soon as both arrived or when the bound runs out (nothing was generated, so no save; or a failed save that wasn't seen)

On return the Chat screen shows "Your reply was interrupted when TravelingTavern went to the background." A takeover never waits out the 60 s `busy` limit for a reply that can't finish.

**Notification** (required for a foreground service)

- Channel "Replies in progress", low importance (no sound, no heads-up)
- Text "Finishing your reply…", no actions
- **Once `POST_NOTIFICATIONS` is granted (Phase 8):** shown only once the service has run about 10 s (Android's default for foreground-service notifications, D31), so short replies show none, in the foreground or not. Until then it isn't shown at all (below)
- With the notification permission denied (Android 13+), the notification is hidden and the service still runs (verified on API 35, Notes slice 7). The app doesn't ask for the permission for this. It doesn't declare `POST_NOTIFICATIONS` either, so on Android 13+ the permission is always denied and the notification never shows (`appops`: `POST_NOTIFICATION: ignore`)

## A7. No Model Connection After a Takeover; Notices (R37)

- `leaseNotice` `notConnected` (E11: the holder is still not connected after a takeover reload and the page's own connect attempt, E10) → a **banner on the Chat screen**: "Not connected to a model", shown until `connected` turns `true`
- **Action: Connect.** Asks the page to press ST's connect button for the selected API again (the same mechanism as E10). Still not connected → the banner says "Connect to a model in SillyTavern's API Connections."
- **`leaseNotice` `replyNotStarted`** (E3, E8: the page couldn't write `busy: true`, so the reply wasn't sent) → a snackbar on the Chat screen: "Couldn't reach SillyTavern to start the reply. Try again." The chat's Send / Stop state follows ST's own generation end, as for a Stop
- Unknown `leaseNotice` kinds are ignored
- The app has no native model-connection screen yet (Me and Advanced are placeholders). Revisit when Phase 1.8 adds one

## A8. Extension Install and Update

Prompts from `leaseInfo` (E11), carried over from the old spec and adjusted:

| `leaseInfo` | Prompt |
|---|---|
| `extensionLeaseVersion` null (no extension) | One-time dialog after connecting, "Keep your devices from overwriting each other": without the TravelingTavern extension, a SillyTavern tab on your computer can overwrite changes made on this phone, and this phone can overwrite changes made in that tab. Install it on every browser you use with SillyTavern. **Install** / Not now. Plus a Settings row "Desktop protection: Not installed" |
| `extensionLeaseVersion < leaseVersion` | Settings row "Update available" + **Update** |
| `extensionLeaseVersion > leaseVersion` | Settings row "Update TravelingTavern": the app's copy is older than the extension; link to the app's release |
| `untestedServer` | Settings row "Not tested with SillyTavern {stVersion} yet." |

**One-tap install and update** through ST's API (verified in ST 1.19.1 `src/endpoints/extensions.js`):

- Install: `POST /api/extensions/install` `{ "url": "<repo URL>" }`, per user (no `global`, which needs admin), **no `branch`**: ST then clones the repo's default branch, `main`. Answer `{ version, author, display_name, extensionPath, folderName }`; `409` = already installed → treat as installed and offer Update
- Update: `POST /api/extensions/update` `{ "extensionName": "TravelingTavern-Extension" }`, per user. ST pulls the current branch; answer `{ shortCommitHash, extensionPath, isUpToDate, remoteUrl }`
- Flow: confirmation dialog → call → `WebView.clearCache(true)` (A2; never cookies or web storage) → reload the page → "Reload SillyTavern on your computer to turn on protection there." On failure: show the repo URL for ST's **Install extension**
- Small client in `data/` behind a use case; the dialog and rows wherever Settings lives when this ships (Me tab placeholder today)

## A9. Builds

- A dedicated **`leaseTest` build type** (`initWith(debug)`): application id suffix `.leasetest`, its own app name, so it installs beside the real app. Debug and release are unchanged
- It allows exactly one server: `leaseTest.serverUrl` from `local.properties` (gitignored: `/local.properties` in `.gitignore`), read into `BuildConfig` for `leaseTest` only. A `leaseTest` build fails with "Set leaseTest.serverUrl in local.properties to the test install's URL" when it's missing. Any other server: the app refuses to load it and says why
- The test install's address never appears in committed code, specs or history
- Release build: binds the allow-all `ServerPolicy` (`AnyServerPolicy`); `SingleServerPolicy` is bound only in `leaseTest` (D7)

## A10. Tests (links R14)

**Unit (Kotlin, JUnit + MockK):**

- Parsing `leaseState`, `leaseInfo` and `leaseNotice`: every field, unknown `reason`, unknown fields ignored, malformed messages
- Pre-check: pair comparison; 2 s reuse; refused while parked; pair changed or unreadable → parked state and `onForeground()`; version mismatch refused; exempt commands; `setAppState` checked
- Release: only from a holder; body (`format`, pair) and headers; deferred while `busy`; 3 attempts 100–200 ms apart, then no more
- CSRF: fetched with cookies, reused, fetched again after `403`, never in logs
- Foreground: `onForeground()` called on return
- Before the first `leaseState`: a command waits, then runs the normal check when it arrives; no report within 5 s → refused, with no parked screen; never run while the page would hold it. User-started: loading bar and the "still loading" message; app-started: neither, refused quietly
- Reply service: started when `busy` turns `true` in the foreground, stopped once the reply and its release are done; timeout in foreground vs background; API 31–33 → interrupt; the 45 s deadline in the background, cancelled when the reply ends or on return
- Interrupt order (A6 steps 1–3): `interruptReply`, then one bounded wait for `busy: false` **and** `chatSaved`, then the release; a save reported before the interrupt doesn't count; a save before `busy: false` still waits for it; the release still goes out when the wait runs out; on a timeout, all of it before the service stops, within the allowed time
- A reply that ends in the background: `watchChatSave`, then the release once `chatSaved` arrives or at the bound; none after a return
- Persona re-read: done while `extensionLeaseVersion` is null, skipped once an extension is reported
- A8 prompts for each version case; install without `branch`; `409` → installed; `clearCache(true)` only
- `leaseTest`: missing `leaseTest.serverUrl` fails the build; another server refused

**Compose UI tests** (emulator, before release): the scrim covers every tab and the tab bar, swallows taps, hides the keyboard; each state's text and control; the no-connection banner.

**Manual checklist**, with the `leaseTest` build on the separate test install only, never the install in daily use. Throwaway data. Each item names the Android version tested.

- [ ] Starts parked while the desktop holds the lease; native parked state on every tab, incl. the tab bar
- [ ] + and send refused up front while parked; keyboard hidden when the scrim appears
- [ ] Page-load requests before the start decision: reads only, none from `bridge.js` or clean mode
- [ ] Use here: same chat reopened; timing recorded
- [ ] Background release with the page's JavaScript paused; the desktop then claims without a click; the returning app parks
- [ ] Return without a takeover: stays holder, marker invalidated
- [ ] Killed app → desktop shows Use here
- [ ] Takeover while generating, desktop → app and app → desktop
- [ ] Reply in the background on Android 14+: completes under the service; no notification (`POST_NOTIFICATIONS` not declared, D31); ST's save completes before "Release written"
- [ ] Reply in the background on Android 14+ still running 45 s after going to the background (D30): interrupted at the deadline, before the page freeze; ST's partial save completes before "Release written"; message on return
- [ ] Reply in the background on Android 12–13 (emulator): interrupted, partial reply saved before the release, message on return
- [ ] Commands right after a page load wait for the first `leaseState` instead of being held
- [ ] Reply that can't start (`replyNotStarted`): snackbar shown, nothing sent
- [ ] No model connection after a takeover: banner until connected; Connect presses ST's connect again; still not connected → API Connections message
- [ ] Extension install and update from the app; prompts for older, newer and missing extension
- [ ] WebView without document-start support (if reproducible): "Update Android System WebView"; Update opens the Play Store page; Continue loads SillyTavern with the "Not protected" banner; the next launch asks again
- [ ] The extension spec's checklist (E14)

## Changes to Existing Features

- **The 1.9 persona re-read becomes conditional** (on opening Personas, on app return, before each persona action). It works around a stale settings copy in the page. With the extension on the desktop, every page that writes loaded while holding the lease, so the phone's copy can't be stale: the re-read is skipped. **While `leaseInfo` reports no extension** (`extensionLeaseVersion` null), a desktop tab outside the lease can still change settings, so the re-read stays. `phase1-9-personas-spec.md` is updated to match
- **Consequence for Phase 1.8 (recorded only):** the old spec blocked 1.8 (full SillyTavern UI behind Advanced) until presets, Quick Replies, themes, groups and full character edits were guarded one by one. The lease covers every save from pages running the lease, so that blocker is gone. Starting 1.8 is a separate decision

## Investigate First

- Document-start script support (emulator, Pixel 8): answered, Notes slice 3
- The freeze cause; whether the renderer keeps running under a `shortService`, and whether `setRendererPriorityPolicy` is needed: answered, Notes slice 7 (Chromium's own 60 s page freeze, D30)
- `shortService` start rules, time limit and `onTimeout`: answered on API 37, Notes slice 7; the service with the notification permission denied: answered on API 35; API 34 and 36 not checked
- Whether a lease version mismatch can happen with document-start injection from assets: answered, Notes slice 3

## Docs to Update

- `CLAUDE.md` Related Repos: TravelingTavern-Extension as the source of `session-lease.js` (submodule; `git clone --recurse-submodules`)
- `context/project-schema.md`: the lease as a Phase 1 item and a "Built Toward the Final Phase" rule; the 1.8 consequence (done in this branch)
- `context/specs/phase1-9-personas-spec.md`: re-read removed
- `context/current-feature.md` history

## Implementation

**Slices** (one commit each on `feature/session-lease`, pushed for review, unmerged until the re-pin to `v0.2.0`):

1. [x] Build plumbing: A2 bundled copy, A9
2. [x] Lease messages and state (E11 → A3/A4 inputs)
3. [x] Document-start injection (A2)
4. [x] Native parked scrim (A3)
5. [x] Kotlin pre-check (A4)
6. [x] Foreground and background release (A5)
7. [x] Reply in the background (A6)
8. [ ] Notices (A7)
9. [ ] Extension install and update; conditional persona re-read (A8, Changes)
10. [ ] Manual and E14 checklists on the test install; docs

**Decisions** (running list)

- **D1:** `revealMessage` and `endReveal` go through `BridgeClient.run()` today; slice 5 gives them an exempt path with no lease check, like `setCleanMode` and `setPageTheme`
- **D2:** A7's Connect is an app `bridge.js` command (`__ttLease` exposes only `useHere` and `onForeground`). It reuses the connect-button ids and `main_api` mapping verified in the extension spec's E15, confirming the selectors match before use; selectors go in `st-selectors.js`
- **D3:** `0.1.20` / `versionCode` 21 set once in slice 1; `versionCode` goes up again for each later build that is installed
- **D4:** each slice: commit (asked), push the feature branch unmerged for review, stop. A draft PR `feature/session-lease` → `dev` runs CI on every slice push; it stays a draft and unmerged until the re-pin to `v0.2.0`
- **D5 (slice 1):** the bundled copy is copied per variant by `copy<Variant>SessionLease` into a generated assets dir (`variant.sources.assets.addGeneratedSourceDirectory`); `LEASE_VERSION` is read with a regex at configuration time. A missing `extension/session-lease.js` fails every Gradle invocation with the A2 message
- **D6 (slice 1):** a missing `leaseTest.serverUrl` fails only the packaging of a `leaseTest` APK (`checkLeaseTestServerUrl` before `packageLeaseTest`), so unit tests and lint still run without `local.properties` (CI). `./gradlew build` on a clone without it fails at `assembleLeaseTest`
- **D7 (slice 1):** single-server rule as a `ServerPolicy` (domain) bound per build type: `src/debug` and `src/release` bind `AnyServerPolicy`, `src/leaseTest` binds `SingleServerPolicy(BuildConfig.LEASE_TEST_SERVER_URL)`. `BuildConfig.LEASE_TEST_SERVER_URL` exists in `leaseTest` only. Checked in `TestConnectionUseCase` (`ConnectionResult.ServerNotAllowed`) and before every load in `WebViewHolder` (`PageError.ServerNotAllowed`, no Retry). `SingleServerPolicy` lives in `main` because AGP 9 runs unit tests for the debug build type only (`android.onlyEnableUnitTestForTheTestedBuildType`); release never binds it
- **D8 (slice 1):** `leaseTest` app name "TT Lease Test" (`src/leaseTest/res`)
- **D9 (slice 1 follow-up):** CI compiles the `leaseTest` variant (`compileLeaseTestKotlin hiltJavaCompileLeaseTest`) so its Hilt binding is built; it never packages it (no `leaseTest.serverUrl` in CI)
- **D10 (slice 2):** `BridgeMessage.LeaseState`, `LeaseInfo` and `LeaseNotice` keep E11's fields as sent: `state`, `reason` and `kind` are raw strings, so unknown values always parse. Every E11 field is required (nullable ones must be present, as `session-lease.js` always sends them); a missing field or a wrong type drops the message (logged as malformed), keeping the last state. kotlinx accepts quoted numbers (`"3"` for `epoch`); harmless, the page never sends them
- **D11 (slice 2):** domain `LeaseStatus` (`NotReported`, `Holder(busy, connected)`, `Parked(ParkReason)`, app-only `VersionMismatch` and `NoDocumentStart`) is what A3 and A4 go by; `LeaseMapper` types the page's state. **Fails closed:** any `state` other than `holder` counts as parked, an unknown state or reason becomes `ParkReason.Unknown(value)` (slice 4 picks its scrim text). A holder's `reason` is ignored
- **D12 (slice 2):** `LeaseStateStore` (`@Singleton`) holds `status`, plus the data-layer `lastState` (pair, `origin`, `busy` for A4–A6) and `info` (A8) as `StateFlow`s. Fed by `BridgeRepository` (now injected with it); `leaseState` and `leaseInfo` reset on every page start. Precedence: `NoDocumentStart` (set by slice 3, survives page loads) > `VersionMismatch` (`leaseInfo.leaseVersion` ≠ the app's `BuildConfig.LEASE_VERSION`, even before the first `leaseState`) > the page's state > `NotReported`. `leaseNotice` passes through `BridgeRepository.messages` only; slice 8 consumes it and ignores unknown kinds. **A late `leaseState` from the previous page** can land after the reset (the page re-sends only on change, so the store would then hold the old page's state for the new load). The Kotlin pre-check isn't the only guard: the new page's own lease still holds its requests until its start decision (E6), and its first `leaseState` replaces the stale one
- **D13 (slice 3):** `BridgeInstaller.addLeaseScript` adds the lease with `addDocumentStartJavaScript` for the same origin rule as the `TravelingTavern` listener, both right before `loadUrl` (each applies from the next navigation); a changed origin removes the old `ScriptHandler` first. The script's own guards (main frame, not `/login`, once per page) do the rest. The injected copy is read from the APK's assets once per origin change. It returns `false` when the feature is missing or `addDocumentStartJavaScript` throws (WARN log); the caller then must not load the page
- **D14 (slice 3, changed by the user 2026-11-21):** no `DOCUMENT_START_SCRIPT`, or `addLeaseScript` returns `false` → `WebViewHolder` doesn't call `loadUrl` and sets `LeaseStatus.NoDocumentStart` (WARN log). The page state stays `Loading` underneath. Slice 4's scrim offers Update and Continue without protection (A2); Continue loads the page without the lease for this launch (the status stays process-only, so the next launch asks again). `WEB_MESSAGE_LISTENER` missing is checked first and still shows `PageError.WebViewOutdated`. Was: never loads, no way past it
- **D15 (slice 3):** `onJsBeforeUnload` confirms only when `LeaseStateStore.isTakeoverReload` (the last `leaseState`'s `reason` is `claiming` or `reloadPending`, read from the raw state so a version mismatch doesn't hide it); otherwise `super`, i.e. the WebView's own "Confirm Navigation" dialog. A lease version mismatch now logs a WARN with both numbers
- **D16 (slice 4):** domain `LeaseRepository` (`status`, `unprotected`, `useHere`, `reload`, `continueWithoutProtection`); `LeaseRepositoryImpl` wraps `LeaseStateStore`, `BridgeClient` and `WebViewHolder`, so the UI never sees the store. `ui/lease/LeaseGuard` wraps the `Scaffold` in `TravelingTavernNavHost`; the stateless `LeaseGuardContent` draws the shell, the banner and the scrim (`components/LeaseScrim`, a non-clickable `Surface`, which consumes all pointer input). While the scrim shows, the shell is also cleared from the accessibility tree (`clearAndSetSemantics`), so TalkBack can't reach buttons under it. Back is not blocked: it navigates under the scrim, which stays
- **D17 (slice 4):** Use here shows `claiming` at once: `LeaseStateStore.onUseHere()` maps a parked page to `Parked(Claiming)` until its next `leaseState` or page start. The page ignores a tap it can't act on and then reports nothing, so after 5 s with no answer the store falls back to the page's state; each tap gets a token, so an earlier tap's timeout can't end a newer one. `BridgeClient.useHere()` runs `window.__ttLease && window.__ttLease.useHere();` (`BridgeScript.leaseCall`, which allows only `useHere` and `onForeground`) outside `run()`: Use here is how a parked page stops being parked, so slice 5's check must never gate it. Reload is `WebViewHolder.reload()` → `WebView.reload()`
- **D18 (slice 4):** `ParkReason.Unknown` (and an unknown `state`) gets the `held` row with Use here: the page's own lease decides whether it can claim, and the user always has a way forward. `unknownFormat` and the version mismatch have no control (as in A3); `Update` opens `market://details?id=com.google.android.webview`, falling back to the Play Store web page
- **D19 (slice 4):** Continue without protection: `WebViewHolder.continueWithoutLease()` sets a process-only `withoutLease` flag (loads skip the document-start check and `addLeaseScript`) and reloads; "this launch" means this app process, so the next cold start asks again. `LeaseStatus.Unprotected` is the status when nothing reports; the banner has its own `unprotected` flag. **Found on the emulator:** with the extension installed in SillyTavern, its own copy of the lease still loads in that page (late, from ST's extension loader, after ST's first requests), parks as `held` and holds every request. With `Unprotected` overriding it, ST hung with no scrim and no way out. Now a reported `leaseState` wins over `Unprotected` (Use here then claims through the extension's copy) and the banner stays, since the page's first requests weren't held. A version mismatch from an extension of another `LEASE_VERSION` still shows "Restart needed" here, and restarting leads back to the same choice (fails closed, known limit)
- **D20 (slice 4):** the banner is drawn edge to edge above the `Scaffold` (`errorContainer`, no action), pads itself by the status bar and top cutout, and the shell below consumes those insets, so app bars and the WebView geometry aren't pushed down twice. Under a scrim it's covered with everything else
- **D21 (slice 5):** `data/bridge/LeaseGate` (`@Singleton`, main thread) runs A4 for `BridgeClient.run()`; `LeaseFileReader` (`data/remote/OkHttpLeaseFileReader`) reads `/user/files/tt-session-lease.json` from the last `leaseState`'s `origin`, only when that is the saved server (else unreadable), through the shared `OkHttpClient` (basic auth) with `CookieManager`'s cookies, `Cache-Control: no-cache` and a 5 s call timeout. `LeaseFileParser` mirrors `parseLease` (E2), `parseActive` included (`active` is null or a non-empty `chatFile` plus a non-empty `groupId` or `avatar`; added after the slice 5 review): unknown or missing `format` → `UnknownFormat`, `404` → missing, anything else invalid → unreadable. Outcomes: match → run; changed pair or missing file → parked `lost`; unreadable → `unreadable`; unknown format → `unknownFormat` (the app's copy is the outdated side). Checks run one at a time (a `Mutex`), so commands sent together share a read; only a match is reused (≤ 2 s, same pair). If the page reports a new pair during the read (its own epoch bump), the file is read once more against the new pair instead of parking. `NoDocumentStart` is refused like parked
- **D22 (slice 5):** every `BridgeClient` command returns whether it was sent; `sendAndAwait` (subscribe, send, wait for the answer) returns null at once for a refused command, so a refused action fails immediately instead of after its timeout. Chat's Send puts the text back when the send is refused. `revealMessage` and `endReveal` use `runUnchecked` (D1); `BridgeClient.onForeground()` is the second never-gated lease hook
- **D23 (slice 5):** the pre-check's parked state is `LeaseStateStore.onPreCheckFailed(reason)`, ranked below a pending Use here and above the page's own state. The page's next `leaseState`, a page start, or an unanswered Use here (D17's 5 s) clears it: a page that is still holder after `onForeground()` doesn't report again (nothing changed), so the unanswered tap is the way back for a false alarm (e.g. one failed read over a flaky network)
- **D24 (slice 5, wording confirmed by the user 2026-11-21):** the loading bar and the "still loading" message are drawn once by `LeaseGuard` for the whole shell (a `LinearProgressIndicator` along the top while a user-started command waits; a snackbar through the `Scaffold`'s `snackbarHost`, which `LeaseGuard` hands to its content), since the screen on view is the one where the tap happened. A4 says so instead of "on the screen that started it". The bar pads itself by the status bar and top cutout (found on the emulator: the shell is edge to edge, so it was drawn under the status bar icons); under the "Not protected" banner those insets are already consumed and it sits right below the banner
- **D25 (slice 5, user decision 2026-11-21):** only commands the user started show the loading bar and the snackbar. `BridgeClient.run()` passes a `CommandOrigin` to `LeaseGate.check()`: `App` for `setAppState`, `reloadChat` (on return, and after a Stale action), `requestPersonas` (Personas' automatic refresh on opening), `countTokens` (while typing) and `requestRecentChats` on return or on leaving Chat; `User` for everything else, including pull to refresh. An app-started command still waits up to 5 s for the first report and gets the same pair check and parking; it just doesn't count towards `waiting`, and a refusal for lack of a report is a log line only (no `stillLoading`). Nothing retries it: the persona sync runs again on the next return
- **D26 (slice 6):** `data/bridge/LeaseLifecycle` (`@Singleton`, started in `Application.onCreate`) owns A5 and the page's `setAppState`, moved out of `CharacterRepositoryImpl`. One collector of `AppForegroundTracker.isForeground`: a return is a `false` → `true` after the first start (what `returns` emits), handled there so `onForeground()` always goes before `setAppState(true)`. `setAppState` calls are chained in order; `onForeground()` doesn't wait for a `setAppState(false)` still in its pre-check. The list and chat re-reads on return stay on `returns` in `CharacterRepositoryImpl`
- **D27 (slice 6):** a release needs `LeaseStatus.Holder` (so nothing while parked, after a failed pre-check, unprotected or before the first report) and a last `leaseState` that is `holder` with an epoch. The marker names that pair, `releasedAt` is UTC ISO 8601 with milliseconds; `ReleaseMarker` mirrors `markerFor` and `uploadBody` in `session-lease.js` (`{ name, data }`, base64 of the UTF-8 JSON). Retry (E3) in `LeaseLifecycle`: at most 3 attempts, a random 100–200 ms between them. A `403` fetches the token again and resends once **within** the attempt, as the page's own `leaseFetch` does
- **D28 (slice 6):** `data/remote/OkHttpReleaseUploader` sends only to the saved server (same origin rule as the lease file reader), with `CookieManager`'s cookies, `X-CSRF-Token` and basic auth from the shared client. The token is memory only, reused, dropped and fetched again after a `403`; logs carry outcomes only. One attempt, CSRF fetches included, has a 5 s budget (`RELEASE_ATTEMPT_BUDGET_MS`, each call's `Call.timeout()` set to what is left), so the return path's wait (5.5 s) outlasts any attempt in flight. On return no further attempt starts. If the bound ever ran out, the job is cancelled and `onForeground()` runs anyway (WARN): the one remaining gap is an upload the server receives after the app's client gave up on it. It matters because such a marker would land after `onForeground()` has checked and found nothing to invalidate, and it names the page's current pair: the phone would look released while in use, and another device could claim without a click
- **D29 (slice 6):** going to the background while `busy` calls `LeaseLifecycle.onBackgroundWhileBusy()`, which only logs. Slice 7 (A6) fills it: start or rely on the reply service (API 34+) and release when `busy` ends in the background, or interrupt (API 31–33)
- **D30 (slice 7, user decision 2026-11-21):** the reply service plus a **45 s deadline** (`LeaseLifecycle.BACKGROUND_REPLY_DEADLINE_MS`) and a **save-ordered release**. Why: the service keeps Android from freezing the process, but Chromium freezes the hidden page 60 s after it is hidden (6 runs, no spread), and a frozen page holds ST's save until the return. The deadline starts at `ProcessLifecycleOwner`'s stop (about 0.7 s after the page turns hidden); the interrupt takes at most 3 s (shared bound) plus one 5 s release attempt, so it ends by about 53.7 s after hiding, about 6 s before the freeze. **Why `busy: false` wasn't enough:** on the device the page reported `busy: false` 9 ms after Stop and ST sent its save 0.76 s later, so a release on `busy: false` landed first; a device claiming in that gap would hold the save and the phone would lose the partial reply. ST 1.19.1 has no "chat saved" event, so `bridge.js` `interruptReply` arms a passive `PerformanceObserver` on resource entries (the lease stays the only fetch wrapper) before ST's stop, and posts `chatSaved` once when a chat save that started after arming has completed (any status), or never; the watch disarms itself after 10 s. The save paths (`/api/chats/save`, `/api/chats/group/save`) are in `st-selectors.js`. Kotlin counts `chatSaved` in `LeaseStateStore.chatSaves` and waits for the next one after Stop, inside the one 3 s bound shared with `busy: false`; the release goes out when both have arrived or when the bound runs out. Same wait on all three paths: the deadline, API 31–33 (interrupt at once on going to the background) and `onTimeout` in the background. The interrupt's Stop is not gated by the Kotlin pre-check (`BridgeClient.interruptReply`): it must fit the time budget, and ST's save after it is still pair-checked by the page's own lease (E6). A return during the release upload is still waited for before `onForeground()` (D28); a return during the interrupt's wait releases nothing. **Added after the slice 7 review:** a reply that *finishes* in the background has the same race (the page reports `busy: false` at the end of the generation response, E8, and ST saves after it), so that release also waits for `chatSaved`: Kotlin arms the watch with `watchChatSave` when it sees `busy: false` (ST's save starts 0.2 s or more later) and waits with the same 3 s bound. And the service now stops only once the interrupt or that release has finished (`stopServiceAfter`), so the save and the upload keep the foreground-service importance instead of risking Android's freezer about 10 s after the app turns cached; a reply restarted meanwhile keeps its new service. A return cancels a pending interrupt or release-after-reply wait (an upload in flight is still waited for, D28), so a later background starts fresh; the time limit in the background also waits for a pending release after the reply
- **D31 (slice 7, corrected 2026-11-21):** the reply notification shows only if `POST_NOTIFICATIONS` is granted, and the app doesn't declare or ask for it until Phase 8 (notifications; A6). Until then no notification is shown; the service runs without it (verified on API 35, Notes slice 7). Once granted, Android's default foreground-service behavior applies: shown only after the service has run about 10 s, so short replies show none (A6 expected a brief one every time). Its small icon is the launcher's monochrome layer. The interrupted-reply message (A6) is a snackbar on the Chat screen, from a `LeaseStateStore` flag the screen collects only while visible, so an interrupt in the background shows on return

## Notes

- **Slice 7, A6 (verify) findings (2026-11-21):** `leaseTest` build on an emulator (API 37, WebView 157) against the test install (SillyTavern 1.19.1) with the test tools' mock model (one word every 2 s, about 88 s per reply). A page heartbeat (`setInterval` pushing `Date.now()`, read back after the return) and page lifecycle listeners (`freeze`, `resume`, `visibilitychange`) were added through the WebView's DevTools; process state from `dumpsys activity processes`
    - **Android's cached-app freezer is real:** Home, then other apps → the app goes `cch` and both the app and its renderer process show `isFrozen=true` about 10 s later; the heartbeat stops for the whole time. Home alone keeps the app `prev` (not cached) for a while
    - **The `shortService` works as an Android-side fix:** started on `busy: true` in the foreground (`types=0x800`, channel `replies_in_progress`), then Home and other apps → the app is `fg-service-short` and the renderer `FGS`, neither frozen, **without** `setRendererPriorityPolicy` (the renderer follows the app's importance). No type-specific permission: only `FOREGROUND_SERVICE` is declared, and `startForeground` with the short-service type didn't throw
    - **But Chromium freezes the hidden page itself, 60 s after it is hidden**, whatever the process state. First seen under the service: tokens stopped reaching the page about 60 s after Home (the mock had sent the whole reply; nothing on the Android side was frozen), so the reply hung until the time limit. **So a `shortService` alone can't finish a reply longer than about 60 s after Home.** This is the freeze the spike saw
    - **Freeze timing, measured** (API 37, WebView 157.0.8103.50): a page heartbeat every 250 ms (`Date.now()` into an array) plus `visibilitychange` / `freeze` / `resume` listeners with `Date.now()`, armed through DevTools; Home with `adb shell input keyevent HOME`, the PC's `Date.now()` taken just before it (the PC clock runs about 0.5 s ahead of the emulator's, so `hidden` shows at −0.5 s); read back after the return. 6 runs:

        | Run | Setup | `hidden` → `freeze` | Home → last heartbeat |
        |---|---|---|---|
        | 0 | Home only, 100 s (first sighting, second precision) | 60 s | — |
        | 1 | Home only, 75 s | 60.0 s | 59.2 s |
        | 2 | Home only, 75 s | 60.0 s | 59.3 s |
        | 3 | Home only, 75 s | 60.0 s | 58.5 s |
        | 4 | reply streaming under the service, Home then Settings and Chrome | 60.0 s | 59.3 s |
        | 5 | same as 4 | 60.0 s | 58.9 s |

        No spread between runs (the heartbeat's last beat is 0.2–1 s earlier, since hidden pages run timers about once a second). Runs 4 and 5 are the same while the service kept Android from freezing the app, so this is Chromium's own background-page freeze, not Android's. Not measured on other WebView versions or API levels
    - **Interrupt at a 45 s deadline, ST's save vs the release** (2 runs, reply under the service, Home then Settings and Chrome; resource timings from DevTools, buffer cleared and enlarged before the run; same emulator clock as logcat): the deadline fired 45.0 s after `ProcessLifecycleOwner`'s stop both times and the page was still running. Run 2 in ms after the interrupt: page reports `busy: false` +9; generation response ends +103; the lease's `busy: false` write +108–135; **Kotlin release written +124; `/api/chats/save` sent +763** (after the lease's pair re-read at +745). Run 1: release written +46 (its save wasn't in the full timing buffer). **So waiting for `leaseState.busy: false` does not put ST's partial save before the release:** the page reports `busy: false` as soon as the generation request ends, and ST saves afterwards. The save still landed (the lease file's pair was unchanged, so the page's own pair check passed), but a device claiming in that gap would hold it and the partial reply would be lost on the phone (nothing overwritten). SillyTavern 1.19.1 has no "chat saved" event (`public/scripts/events.js`; `saveChatConditional` emits none)
    - **`PerformanceObserver` in WebView 157:** it reports the page's fetch requests as resource entries, including those sent through the lease's fetch wrapper, **with the page's resource timing buffer full** (250 of 250 entries, the default size, as after a normal session): the app's `chatSaved` arrived in both runs below. Entries arrive once the response has ended
    - **Group chats** save to `/api/chats/group/save` (1.19.1 `group-chats.js`, three call sites), a character's chat to `/api/chats/save` (`script.js`); both are watched. Not exercised on a device (the test install has no group)
    - **Save-ordered release, re-checked** (D30; 2 runs, same setup as above, buffer left full; times in ms after the deadline's interrupt, page entries from a test-only observer, app lines from logcat, one clock):

        | Run | `busy: false` reported | generate ends | lease `busy: false` write | `/api/chats/save` sent → completed | `chatSaved` in Kotlin | **Release written** |
        |---|---|---|---|---|---|---|
        | 1 | +4 | +34 | +37–43 | +201 → **+213** | +264 | **+785** |
        | 2 | +5 | +30 | +33–39 | +1104 → **+1118** | +1169 | **+1687** |

        ("Release written" is logged when the upload's answer arrives; its request starts right after `chatSaved`.) The partial replies (24 and 23 words) are in the chat file. The deadline fired 45.0 s after the stop both times; the page wasn't frozen yet
    - **The ~0.5 s between `chatSaved` and "Release written"** is the Kotlin upload's connection setup, not the save wait: timed with temporary logs (not committed) on API 35, three background releases: first in the process, `/csrf-token` 529 ms then `/api/files/upload` 7 ms; with the token cached, the upload alone 113 and 141 ms. API 37 run 2 had the token cached and still took about 0.5 s, so most of it is opening a connection to the server after an idle spell (the page's own requests reuse Chromium's connections and take a few ms)
    - **API 35, notification permission denied** (Medium Phone API 35, WebView 157.0.8103.50; `POST_NOTIFICATION: ignore`, since the app doesn't declare it): a reply, then Home, Settings and Chrome. 33 s after Home the service ran (`isForeground=true types=0x800`), the app was `fg-service-short` and the renderer `FGS`, nothing frozen; no notification posted (`dumpsys notification` has none for the app; the expanded shade showed none). The 45 s deadline fired; ms after the interrupt: generate ends +35, lease `busy: false` write +39–63, `/api/chats/save` +1064 → **+1072**, `chatSaved` +1082, **release written +1606**
    - **A reply that finishes in the background (after the slice 7 review, D30):** API 35, mock at one word every 0.5 s (about 22 s), Home then Settings and Chrome; ms after the page reported `busy: false`: generate ended −26, lease `busy: false` write +4–9, `/api/chats/save` +971 → **+988**, `chatSaved` +1039, **release written +1567**; the service was gone 35 s after Home. Without the wait, the release would have gone out about 1 s before the save
    - **Android 12–13 (API 31–33) waits for the release checklist:** no system image is installed for them, so the interrupt at once on going to the background is covered by unit tests only
    - Not reproduced on a device: the `onTimeout` path with the save report (unit tests; the time limit's interrupt itself ran on the device before D30), a save that never comes (unit tests: release at the 3 s bound)
    - **Time limit (API 37):** `onTimeout` came exactly 180 s after the start. `dumpsys activity settings`: `short_fgs_timeout_duration=180000`, `short_fgs_proc_state_extra_wait_duration=5000` (the process loses its foreground-service importance 5 s after the limit), `short_fgs_anr_extra_wait_duration=10000` (ANR 10 s after it). The interrupt then ran: `evaluateJavascript` reached the frozen page (ST logged `generation_stopped` at once), and the release was written 0.5 s later
    - **ST's save of the partial reply did not go out before the release in a frozen page:** the 31-word partial reached the chat file only on the return, about 50 s after the release. Before the 60 s page freeze, the interrupt's wait isn't affected by this
    - Not yet checked: API 34 and 36 (no image or AVD); API 31–33 (above)
- **Slice 6 (2026-11-21):** `leaseTest` build on an emulator (API 37, WebView 157) against the test install (SillyTavern 1.19.1). Claude in Chrome wasn't connected, so the desktop was a stand-in: a Node script running the submodule's own `createSessionLease` (rc.1) with its own cookies and CSRF token, i.e. a real start decision without SillyTavern's page. Lease and marker were read the same way
    - **Background release with the page's JavaScript paused** (DevTools `Debugger.pause`, then Home): `Release written (attempt 1)` about 1 s after Home (`ProcessLifecycleOwner` reports the stop after about 0.7 s); the marker named the lease's exact pair. The stand-in's start decision then claimed without a click (`claimed, reloading`, epoch + 1). After the resume, the returning app reported `parked lost` and showed "In use on another device"
    - **Return without a takeover:** marker = lease pair while away; on return the page bumped the epoch (marker one behind, so no longer released) and stayed holder; a fresh stand-in page parked as `held`
    - **Killed app** (`am force-stop` in the foreground): no release logged, marker unchanged; a fresh stand-in page parked as `held`, i.e. Use here
    - Not reproduced on a device: the return while an upload is in flight, retries after a failed upload, the `403` path (unit tests: `LeaseLifecycleTest`, `OkHttpReleaseUploaderTest`); the desktop overlay itself (stand-in, no browser)
- **Slice 5 (2026-11-21):** `leaseTest` build on an emulator (API 37, WebView 157) against the test install (SillyTavern 1.19.1). Another device's takeover was simulated from the PC: a separate client (own cookies and CSRF token) read the lease file and wrote it back through `/api/files/upload` with another `holder` and `epoch + 1`, as another page's Use here would
    - **Scrim and accessibility:** `uiautomator dump` with the scrim up, in Chat (`lost` and `unreadable`) and on Chats (`held`): only the scrim's two texts and its button, no WebView nodes. `clearAndSetSemantics` (D16) already hides the WebView, so `importantForAccessibility` wasn't needed
    - **Refused while parked:** starting parked (`held`), `setAppState` and, after Home and back, `setAppState(false)`, `setAppState(true)` and the Chats re-read were each refused up front (`Command refused: Parked`, debug log, no message); nothing reached the page
    - **Takeover caught by the pre-check:** with the phone holding and the file rewritten, Send in Chat → `Pre-check failed (pair changed)` → scrim "In use on another device"; the page reported `parked lost` 47 ms later, i.e. after `onForeground()`, before its own lease had seen the change. The refused text came back in the message field (D22). Same from a pull to refresh on Chats
    - **Unreadable, by accident:** the first rewrite had a number for `claimedAt` (E2 wants a string): the pre-check said `unreadable` and so did the page, scrim "Can't reach SillyTavern". Use here then couldn't claim (`claiming` → `unreadable`: the lease fails closed on a file it can't parse), so the file was rewritten correctly by hand and Use here worked. The unreadable row is now seen on a device
    - **Wait after a page load:** after Use here, a pull to refresh started between the page-start reset and the first `leaseState` (about 1.4 s) showed the loading bar, then ran once `holder` arrived. App-started: a cold start in airplane mode (page never loads, nothing reports) → `setAppState(true)` waited, then `App command refused quietly` after 5 s, with no bar and no snackbar
    - **Known gap until extension `v0.2.0-rc.3` (from the slice 5 review):** after a failed pre-check the scrim clears only on the page's next `leaseState` (D23). Before rc.3 (rc.1 and rc.2), a page that is still holder after `onForeground()` doesn't report again (nothing changed), so a false alarm (e.g. one unreadable read over a flaky network) leaves the scrim up until Use here goes unanswered for 5 s (D17). rc.3 makes `onForeground()` always report, which clears it at once. **Re-test after re-pinning to rc.3:** an unreadable read, then the file readable again → the scrim clears by itself, with no Use here
    - Not reproduced: the "still loading" snackbar for a user command. User paths other than pull to refresh wait for the bridge to be ready first, and pull to refresh isn't offered while the page is in error; it needs `bridge.js` loaded but no lease report for 5 s. Covered by `LeaseGateTest`
- **Slice 4 (2026-11-21):** `leaseTest` build on an emulator (API 37, WebView 157) against the test install (SillyTavern 1.19.1, extension `0.0.1` / `LEASE_VERSION` 1), with a desktop Chrome tab running the extension's lease:
    - **Starts parked:** with the desktop holding the lease, the app opened on "In use on another device" covering every screen, including the tab bar; taps on the Characters and Me tab positions did nothing (still on Chats after the takeover)
    - **Use here:** "Taking over…" showed at once (screenshot 0.4 s after the tap), then `parked claiming` → page reload → `holder`; Chats loaded
    - **Parked over an open keyboard:** in Chat with the message field focused (`mInputShown=true`), the desktop claimed with `__ttLease.useHere()`. The phone only learns of that through `onForeground` or the pre-check (slices 5, 6), so `__ttLease.onForeground()` was called through the WebView's DevTools: the scrim appeared over Chat and the keyboard closed (`mInputShown=false`). Text typed with `adb shell input text` while parked didn't reach the message field
    - After that takeover reload, Chat showed ST's welcome page instead of the chat: the rc.1 context hook bug (slice 3 note), not the scrim
    - **No document start**, with a temporary local build reporting no support (not committed): "Update needed" with Update and Continue without protection; Update opened another app's activity and Back returned; Continue loaded SillyTavern with the "Not protected" banner under the status bar and the tab content below it; a cold start asked again. This is how D19's extension case was found and then checked: the scrim appeared, Use here claimed through the extension's copy, and the app came back as holder with the banner
    - Not reproduced on a device: `waitingForReply`, `lostRace`, `unreadable`, `unknownFormat`, `reloadPending`, the version mismatch. Their text and controls are covered by `LeaseGuardContentTest`, which passed on the emulator (`connectedDebugAndroidTest`, API 37) with the rest of the UI suite
- **Slice 3 (2026-11-21):** with the `leaseTest` build on an emulator and the Pixel 8 (both API 37, WebView 157) against the test install (SillyTavern 1.19.1, extension `0.0.1` / `LEASE_VERSION` 1 installed), checked through the WebView's DevTools:
    - **Document start works.** `window.__ttLease.source` is `'app'`, version 1, `fetch` wrapped. The lease's own `/csrf-token`, `/version` and both lease-file reads went out at about 110–380 ms, before ST's scripts; while parked, no `/api/` request went out at all; as holder, the start decision (incl. its lease write) finished before ST's first `/api/` request (about 1 s vs 3.6 s)
    - **Version mismatch can't happen in practice.** The page runs the APK's asset, and `BuildConfig.LEASE_VERSION` is read from the same submodule file in the same build; a new APK is a new process and a new WebView, so no older script survives. With the extension installed, ST loaded its `index.js`, `window.__ttExtension` was announced (a second `leaseInfo`) and the page kept the app's copy (`source: 'app'`, no `__ttLeaseSource`). The listener and the script share one origin rule, so a page that can message the app always got the bundled copy first. The check stays (A2)
    - **Page start before the first `leaseState`:** on every load (first launch, takeover reload, holder reload) `Page started: lease state reset` came 0.5–6 s before that load's first `leaseState`; no late `leaseState` from the previous page arrived after a reset. Same on the Pixel 8 (first load parked, then a takeover reload: reset, `holder` 1.3 s later); there, too, `source: 'app'`, the lease's reads at about 185 ms and no `/api/` request while parked
    - **`onJsBeforeUnload` (emulator):** with a page `beforeunload` that always asks (added through DevTools, after a user gesture), Use here while parked → `claiming` → the app confirmed it ("Leaving the page for a takeover reload") and the page reloaded as holder with no dialog; the same handler on a holder's reload showed the WebView's "Confirm Navigation" dialog (default kept). ST's own edit-in-progress prompt wasn't reproduced
    - Not reproducible: a WebView without document-start support (both devices on WebView 157). Extension bug: after the takeover reload the lease logged "SillyTavern context not found: no reopen, no reconnect" in the app's page. Until extension `v0.2.0-rc.2` fixes it, `connected`, the chat reopen and `active` from the phone don't work, and the app features that rely on them wait for rc.2. The submodule is re-pinned to `v0.2.0-rc.3` (which includes that fix and an `onForeground()` that always reports, slice 5 Notes) before slice 8 (notices, A7)
- **Slice 2 (2026-11-21):** message shapes checked against `session-lease.js` at `v0.2.0-rc.1`: `report()` sends `state`, `reason`, `pageId`, `epoch`, `busy`, `connected`; `install` adds `origin` for `leaseState` only; `buildLeaseInfo` always sends all five `leaseInfo` fields (nulls included); `makePost` wraps each in `{ v: 1, type }`. Unit tests only; nothing reaches the page until slice 3 injects the lease
- **Slice 1 (2026-11-21):** submodule at `v0.2.0-rc.1` (`a244fe3`): `manifest.json` `0.0.1`, `LEASE_VERSION = 1`. In rc.1 the extension spec has 15 sections (E15 = verified in SillyTavern's source); Tests are still E14. Verified: `web/session-lease.js` in debug, release and `leaseTest` APKs, identical to the submodule's file; both build failure messages; `leaseTest` installs beside the real app on an emulator (API 37), refuses another server on Connect and connects to the test install. The `WebViewHolder` refusal path (a saved non-allowed server) isn't reachable on a fresh `leaseTest` install and was not exercised

## Not Changed

- ST's chat integrity check
- No app-side copies of ST data
- The app's own `bridge.js` API (`window.__ttBridge`), except that `BridgeClient.run()` is gated by A4
