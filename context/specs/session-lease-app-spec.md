# TravelingTavern `0.1.20` — Session Lease (App)

> **Reviewed** (2026-11-20). Items marked **(verify)** must be confirmed before they're built on.

Replaces `fix-stale-page-overwrites-spec.md` (superseded). The lease itself, its files, messages, hooks and version numbers are defined **once** in TravelingTavern-Extension's `context/specs/ext-0.2-session-lease-spec.md` (cited as **E1–E14**); this spec references them and never restates them. Requirements: that repo's `context/decisions/session-lease-spike.md` (cited as **R…**).

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

- `BridgeInstaller` adds `session-lease.js` with `WebViewCompat.addDocumentStartJavaScript` for the saved server's origin only, before the first load **(verify on the emulator and the Pixel 8)**. The lease then exists before ST's first request, and every request waits for the start decision (E6). This answers R33: first requests are held at document start by the injection itself
- **No document-start support** (`WebViewFeature.DOCUMENT_START_SCRIPT` missing) → the app doesn't load SillyTavern. It shows the native parked state "Update Android System WebView" (A3) with no Use here. No page-load fallback
- `bridge.js` and the clean theme are still injected at `onPageFinished`, as today
- **Login page:** the injected copy doesn't install on SillyTavern's login page (E11: URL pathname ending in `/login`), so logging in works before the lease can read its files; the lease starts with the next page load after login
- **Takeover reloads:** a page that parked while a message was being edited makes ST's `beforeunload` ask "Leave site?" on the reload (E13). The app answers `WebChromeClient.onJsBeforeUnload` with confirm **only while a takeover reload is in progress** (the last `leaseState` reason is `claiming`); otherwise it keeps the default behavior. Discarding the edit is correct: it is stale

**One instance per page** (R40, E11): the bundled copy is always first; an installed extension stands down and only announces itself, so `leaseInfo` reports its version (A8).

**Current code, never cached** (R26, safety)

- The bundled copy is injected from the APK's assets, never from the HTTP cache
- `leaseInfo.leaseVersion` ≠ `BuildConfig.LEASE_VERSION` → the page runs lease code other than the app's own: the app shows the parked state "Restart TravelingTavern" (A3) and refuses bridge commands (A4) **(verify whether this can happen with document-start injection from assets; keep the check either way)**
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
| app: lease version mismatch (A2) | Restart needed | Restart TravelingTavern to use SillyTavern here. | none |
| app: no document-start support (A2) | Update needed | Update Android System WebView to use SillyTavern here. | none |

- Follows `ui-guidelines.md`: neutral surface, one amber primary button, 48 dp touch targets, no decoration
- **Use here** calls `window.__ttLease.useHere()` (E11) and shows `claiming` at once
- Holder: no scrim. Before the page's first `leaseState`: no scrim; a command started then shows a loading state (A4)

## A4. Kotlin Lease Check (R27, R39)

**Before every bridge command** (`BridgeClient.run()`), unless exempt:

- Read the lease file (E2, through the app's `OkHttpClient` with the WebView's cookies, `Cache-Control: no-cache`) and compare its pair (`holder`, `epoch`) with the last `leaseState`'s `pageId` and `epoch` (E2 pair rule). A check at most 2 s old is reused
- **Parked → refused up front** (R39), never left waiting on a held request. The command isn't run; the scrim is already showing
- **Pair changed or unreadable** → the app shows the parked state at once (`lost` / `unreadable`), refuses the command, and calls `window.__ttLease.onForeground()` so the page parks too
- **Lease version mismatch** (A2) → refused
- **Before the page's first `leaseState`:** the command waits for it, bounded at **5 s**, with a loading state on the screen that started it. When it arrives, the normal check above runs. No report within 5 s → the command is refused and a short message says "SillyTavern is still loading, try again in a moment." (no parked screen: that appears only when the page reports parked or unreadable). A command is never run when the page would hold it

**Exempt: only commands that touch nothing but the page's DOM or CSS** (R27):

- `revealMessage`, `endReveal`: scroll and a body class
- `setCleanMode` (`bridge.js` `setClean`: toggles one body class) and `setPageTheme` (`setTheme`: sets `--tt-*` CSS variables and a body class). Confirmed in `bridge.js`: no request, no ST call, no save. They bypass `run()` today and stay outside it
- **`setAppState` is checked:** on return it runs the persona sync, which reads and can save

Native screens keep the page's last data while parked, under the scrim; nothing refreshes until Use here.

## A5. Foreground and Background (R43, R44; links R15, R42)

**Foreground** (R43, `AppForegroundTracker.returns`): the app calls `window.__ttLease.onForeground()` (E11).

- Holder: the page confirms its pair; if the app's release marker still matches (nobody took over), it bumps its epoch so the marker stops counting
- Parked: the page takes over automatically **only if the lease was released**; otherwise the native parked state stays and the user decides

**Background** (R44, `AppForegroundTracker.isForeground` → `false`):

- Only a holder releases. Kotlin writes the release marker (E2: `format`, `holder`, `epoch` from the last `leaseState`, `releasedAt`) with `POST /api/files/upload` through the app's `OkHttpClient`, the WebView's cookies and ST's CSRF header. It doesn't use the page, so it works with the page's JavaScript paused or frozen
- **Deferred while a reply generates** (`leaseState.busy`): A6 decides what happens
- **CSRF token** (R42, E11): Kotlin fetches its own from `GET /csrf-token` with the WebView's cookies (ST returns the session's existing token, so the page's keeps working). Memory only; fetched again after a `403`; never logged or stored
- **Bounded retry**, the same as lease writes (E3): a failed send (e.g. Windows `EPERM`, answered as 500) is retried after a random 100–200 ms, at most 3 attempts in total. No retries after that; the desktop falls back to Use here
- **Killed app:** writes nothing; the desktop shows Use here (E5). A late marker written as a frozen app thaws is harmless (E2)

## A6. Reply in the Background (R25, decided)

**Android 14+ (API 34+): `shortService` foreground service**

- Started **when a reply starts** (`leaseState.busy` turns `true`), while the app is still in the foreground (starting a foreground service from the background is restricted), and stopped when the reply ends (`busy` → `false`) **(verify)**
- It keeps the app's process out of the cached state, so Android doesn't freeze the WebView's renderer while the reply finishes. **Confirm the cause first:** the spike saw the renderer frozen (`isFrozen=true`) but couldn't confirm it with a heartbeat. Also confirm the renderer stays running under the service, and whether `WebView.setRendererPriorityPolicy` must be set for that **(verify)**
- When the reply ends in the background: stop the service, write the release (A5)
- **Time limit** (about 3 minutes for `shortService`, **(verify)**): `onTimeout` with the app in the foreground → stop the service, nothing else (a foreground WebView isn't frozen). In the background → interrupt (below) **before** stopping the service, then stop it. After `onTimeout` the service must stop itself promptly: Android allows only a short grace period before it treats the app as not responding **(verify the time Android allows)**, so the interrupt's bounded wait must fit inside it
- Manifest: the service with `foregroundServiceType="shortService"`; `FOREGROUND_SERVICE` permission **(verify no type-specific permission is needed)**

**Android 12–13 (API 31–33): no foreground service.** Going to the background during a reply → interrupt (below). No other foreground-service type is used.

**Interrupt**, in this order:

1. Stop the reply (`stopGeneration`)
2. Wait, bounded, for `leaseState.busy` to turn `false`, so ST's save of the partial reply lands first **(verify that ST's chat save is sent by then; the bound fits the timeout grace period above)**
3. Write the release (A5)

On return the Chat screen shows "Your reply was interrupted when TravelingTavern went to the background." A takeover never waits out the 60 s `busy` limit for a reply that can't finish.

**Notification** (required for a foreground service)

- Channel "Replies in progress", low importance (no sound, no heads-up)
- Text "Finishing your reply…", no actions
- **It appears briefly during every reply, even with the app in the foreground,** because the service must start while the app is visible (choice above). Expected, not a bug
- With the notification permission denied (Android 13+), the notification is hidden; the service must still run **(verify on Android 14+)**. The app doesn't ask for the permission for this

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
- Release build: no test-only code paths

## A10. Tests (links R14)

**Unit (Kotlin, JUnit + MockK):**

- Parsing `leaseState`, `leaseInfo` and `leaseNotice`: every field, unknown `reason`, unknown fields ignored, malformed messages
- Pre-check: pair comparison; 2 s reuse; refused while parked; pair changed or unreadable → parked state and `onForeground()`; version mismatch refused; exempt commands; `setAppState` checked
- Release: only from a holder; body (`format`, pair) and headers; deferred while `busy`; 3 attempts 100–200 ms apart, then no more
- CSRF: fetched with cookies, reused, fetched again after `403`, never in logs
- Foreground: `onForeground()` called on return
- Before the first `leaseState`: a command waits (loading state), then runs the normal check when it arrives; no report within 5 s → refused with the "still loading" message and no parked screen; never run while the page would hold it
- Reply service: started when `busy` turns `true` in the foreground, stopped when it ends; timeout in foreground vs background; API 31–33 → interrupt
- Interrupt order: `stopGeneration`, then the bounded wait for `busy: false`, then the release; the release still goes out when the wait runs out; on a timeout, all of it before the service stops, within the allowed time
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
- [ ] Reply in the background on Android 14+: completes under the service; notification "Finishing your reply…" shown; runs with the notification permission denied
- [ ] Reply in the background on Android 12–13 (emulator): interrupted, partial reply saved before the release, message on return
- [ ] Commands right after a page load wait for the first `leaseState` instead of being held
- [ ] Reply that can't start (`replyNotStarted`): snackbar shown, nothing sent
- [ ] No model connection after a takeover: banner until connected; Connect presses ST's connect again; still not connected → API Connections message
- [ ] Extension install and update from the app; prompts for older, newer and missing extension
- [ ] WebView without document-start support (if reproducible): "Update Android System WebView"
- [ ] The extension spec's checklist (E14)

## Changes to Existing Features

- **The 1.9 persona re-read becomes conditional** (on opening Personas, on app return, before each persona action). It works around a stale settings copy in the page. With the extension on the desktop, every page that writes loaded while holding the lease, so the phone's copy can't be stale: the re-read is skipped. **While `leaseInfo` reports no extension** (`extensionLeaseVersion` null), a desktop tab outside the lease can still change settings, so the re-read stays. `phase1-9-personas-spec.md` is updated to match
- **Consequence for Phase 1.8 (recorded only):** the old spec blocked 1.8 (full SillyTavern UI behind Advanced) until presets, Quick Replies, themes, groups and full character edits were guarded one by one. The lease covers every save from pages running the lease, so that blocker is gone. Starting 1.8 is a separate decision

## Investigate First

- Document-start script support (emulator, Pixel 8)
- The freeze cause; whether the renderer keeps running under a `shortService`, and whether `setRendererPriorityPolicy` is needed
- `shortService` start rules, time limit and `onTimeout` on API 34–36; the service with the notification permission denied
- Whether a lease version mismatch can happen with document-start injection from assets

## Docs to Update

- `CLAUDE.md` Related Repos: TravelingTavern-Extension as the source of `session-lease.js` (submodule; `git clone --recurse-submodules`)
- `context/project-schema.md`: the lease as a Phase 1 item and a "Built Toward the Final Phase" rule; the 1.8 consequence (done in this branch)
- `context/specs/phase1-9-personas-spec.md`: re-read removed
- `context/current-feature.md` history

## Not Changed

- ST's chat integrity check
- No app-side copies of ST data
- The app's own `bridge.js` API (`window.__ttBridge`), except that `BridgeClient.run()` is gated by A4
