# Current Feature

Phase 1.6 — Chats & Characters Tabs

## Status

Completed

## Goals

Replace the temporary full-screen WebView in Chats with native screens fed by the JS bridge: **Chats** is a messaging-style list of recent conversations, **Characters** a portrait grid of the whole library. Tapping a character in either opens Chat.

### Deliverables

- **Bridge:** `requestRecentChats()` in `bridge.js` — fetches SillyTavern's `/api/chats/recent` with `getRequestHeaders()` and posts a `recentChats` message (avatar, last message trimmed to 200 chars, last-message time, message count); sent after `ready`, refreshed on the same debounced events as `characters`. Parsed into `BridgeMessage.RecentChats`; content is never logged (count only)
- **Domain:** `RecentChat` model; `CharacterRepository` (interface) + implementation over `BridgeRepository` (`characters`, `recentChats`, `pageState`, `refresh()`, `fullAvatarUrl`); `GetRecentChatsUseCase` (one row per character — newest chat wins — sorted by last message, newest first); `GetCharacterLibraryUseCase` (sorted by name, filtered by query, case-insensitive)
- **WebView hosting:** the one WebView moves from the Chats screen to the app shell: one host layer behind the `NavHost` (inside the `Scaffold`), attached for the whole session (so JS keeps running), covered by the opaque tab screens, unfocusable and hidden from accessibility while covered; shown on Chat. It always has Chat's size (below the status bar and Chat's bar, above the navigation bar, clear of the cutout), covered or not — only the keyboard resizes it, only on Chat. Back navigation inside the WebView only while it is showing
- **Chats tab:** top app bar "Chats"; rows of 48dp avatar (thumbnail), name, one-line preview, trailing relative time ("now", "5m", "2h", "Yesterday", "Mar 3", "Mar 3, 2025"); pull to refresh; empty state "No chats yet." + "Browse characters"; re-select scrolls to top
- **Characters tab:** top app bar "Characters" with a search action that expands to a search field; 2-column grid of 3:4 cards with the **full-size** avatar (`ContentScale.Crop`, crossfade, fixed-size placeholder) and the name over a bottom scrim; FAB → bottom sheet "Create character" / "Import card", both "Coming soon"; empty state + "Open full SillyTavern" → Advanced; re-select scrolls to top
- **Banners:** "Can't reach your server — Retry" / "Login failed — Server settings" inline at the top of both tabs; nothing when connected
- **Chat (start of 1.7):** top app bar with back, avatar and name; body is the shared SillyTavern WebView (clean theme). `selectCharacter(id)` answers with a `chatOpened` message once SillyTavern has drawn the chat and it has stopped scrolling; until then Chat covers the page (spinner after 0.8 s). 1.7 adds the Text / Story tabs on top of this
- Avatars through Coil with the shared OkHttp client (basic auth applies); fallback is a round tile with the initial
- Tests: unit — parser (`recentChats`), both use cases, both ViewModels, relative time; Compose UI — `ChatsContent` and `CharactersContent` states and callbacks
- Version `0.1.8` / `versionCode 9`

## Notes

- **(verified on SillyTavern 1.19.0, emulator via DevTools)**
  - Character IDs are avatar file names (`Bob.png`, `Tide Pods.png`, `default_Seraphina.png`) — confirmed again
  - **Full-size avatar:** `/characters/<URL-encoded avatar>` → `200 image/png` (Bob: 78 KB vs. 3.5 KB thumbnail at `/thumbnail?type=avatar&file=…`); spaces work (`Tide%20Pods.png`)
  - **Last message per character:** `POST /api/chats/recent` (headers from `getRequestHeaders()`, body `{ max }` — honored: `max: 2` → 2 rows) returns one row per chat file: `file_id`, `file_name`, `file_size`, `chat_items`, `mes` (last message text), `last_mes` (ISO time), `avatar`. A chat with no messages has `chat_items: 0` and `mes: "[The message is empty]"` → shown with no preview. This is what SillyTavern's own welcome screen uses; read through the bridge, so the page's session and CSRF token apply
  - Character fields include `date_last_chat` (epoch ms) and `chat_size`; every character with a chat file appears in `/api/chats/recent`
- **Decisions (user, 2026-11-17):**
  - **WebView hosted at the app shell**, attached for the whole session. Requirements: tab content is opaque and consumes touches; the covered WebView is hidden from accessibility and loses focus; WebView back handling only while it is revealed. Verify on the Pixel: no blank frame or reload on tab ↔ Chat, and the lists keep updating
  - **Chat in 1.6 is the start of the 1.7 `ChatScreen` / `ChatViewModel`**: Story mode only, top bar with back + avatar + name, nothing else — 1.7 extends it. Verify on the Pixel: list → Chat → back → another character, no reload or blank frame
- **Spec deviations:**
  - Spec says the WebView is hidden "behind the tab content"; it is hosted once at the app shell instead of per screen, so moving between tabs and Chat never detaches it (with a per-screen host, the leaving screen's release would pull the WebView out of the arriving screen)
  - Chat gets an interim body (the WebView) in 1.6 — otherwise tapping a character would lead to a placeholder and the app couldn't chat at all until 1.7
  - Chats list data comes from `/api/chats/recent` via the bridge, not only `BridgeRepository.characters`
- Group chats are left out (rows without an `avatar`)
- **Found during device testing:**
  - Resizing the WebView makes SillyTavern lay out the whole page again and the WebView paints nothing meanwhile — a blank on every tab ↔ Chat move while the WebView took the content area's size (which changes with the bottom bar and Chat's bar). Fixed by giving it one fixed size
  - SillyTavern's `CHAT_CHANGED` fires once the messages are in the DOM (~0.4–0.5 s after selecting on the Pixel), but a long chat keeps growing and scrolling for up to ~0.9 s more (images, long messages) while the page is too busy to paint — the WebView shows blank tiles. Hence `chatOpened`: posted after `CHAT_CHANGED` once the message list's scroll position and height are unchanged across two checks 100 ms apart (capped at 2 s). If the character is already open, `chatOpened` comes right away (SillyTavern sends no event then)
  - The `Scaffold`'s surface swallows touches: with the WebView behind the `Scaffold`, taps never reached the page (no keyboard). The WebView sits inside the `Scaffold` content instead
  - In landscape the tab screens leave the display-cutout strip uncovered; the WebView is inset by the cutout too, so nothing of the page shows there
  - Opening a chat with no messages (Oliver) makes SillyTavern rewrite the file, which moves it to the top of Recent Chats — SillyTavern's own behaviour, also on its welcome screen

## References

- @context/specs/phase1-6-home-character-list-spec.md
- @context/specs/phase1-7-chat-screen-spec.md
- @context/ui-guidelines.md
- @context/coding-standards.md

## History

### 2026-11-17 — Phase 1.6 Chats & Characters Tabs (branch `feature/chats-characters-tabs`)

- Bridge: `requestRecentChats()` posts `recentChats` from SillyTavern's `POST /api/chats/recent` (`{ max: 100 }`, `getRequestHeaders()`): character ID, last message (≤ 200 chars, null for an empty chat), last-message time, message count; sent with `characters` after `ready` and on the same debounced events. `characters` now also carries `fullAvatarUrl` (`/characters/<encoded avatar>`). `selectCharacter` answers with `chatOpened` once the chat is drawn and settled (see Notes). New selectors (`getRequestHeaders`, `fullAvatarPath`, `api`, `recentChat`, `dom.chat`) in `st-selectors.js`
- Data / domain: `BridgeMessage.RecentChats` + `ChatOpened`; `BridgeRepository` exposes `chatActivity` and `characters` as nullable (null = not reported yet); `CharacterRepository` + `CharacterRepositoryImpl` (`characters`, `chatActivity`, `pageState`, `refresh()` waits for the answer ≤ 5 s, `retry()`, `openChat()` waits for `chatOpened` ≤ 10 s); `ChatActivity`, `RecentChat`; `CharacterSummary.portraitUrl`; `GetRecentChatsUseCase` (one row per character from its newest chat, newest first, undated last), `GetCharacterLibraryUseCase` (by name, case-insensitive, filtered by query). Coil uses the shared OkHttp client (`TravelingTavernApplication` is the `SingletonImageLoader.Factory`)
- WebView hosting moved to the app shell (`TravelingTavernNavHost`): always attached, fixed size, every destination but Chat wrapped in an opaque touch-consuming surface; while covered the WebView is unfocusable, gives up the keyboard and is hidden from accessibility (`importantForAccessibility = noHideDescendants`); `WebViewBackHandler` is registered by Chat so it wins over the NavHost
- UI: `ChatsScreen` / `ChatsViewModel` (list, pull to refresh, empty state → Characters, banner, re-select scrolls to top, times refresh every minute); `CharactersScreen` / `CharactersViewModel` (search field in the app bar, 2-column 3:4 grid with full-size portraits and name on a scrim, "no matches" text, FAB → Create / Import sheet "Coming soon", empty state → Advanced); `ChatScreen` / `ChatViewModel` (top bar, cover until `chatOpened`); shared `CharacterAvatar` / `CharacterImage` (initial until the image loads), `ConnectionBanner`, `EmptyState`, `RelativeTime`
- Unit tests: `GetRecentChatsUseCaseTest` (5), `GetCharacterLibraryUseCaseTest` (4), `ChatsViewModelTest` (6), `CharactersViewModelTest` (5), `ChatViewModelTest` (3), `RelativeTimeTest` (5), `BridgeMessageParserTest` +4; `./gradlew build` green. Compose UI tests: `ChatsContentTest` (5), `CharactersContentTest` (6) — compiled; run before release per the testing rule
- Verified on Medium_Phone_API_35 against SillyTavern 1.19.0 over Tailscale: Chats list with avatars, previews (emphasis markers stripped), empty-chat row without preview, relative times; Characters grid with full-size portraits, search filtering, add sheet; list → Chat → back for Bob, Seraphina and (from Characters) Oliver with the character panel closed and no reload
- Verified on the Pixel 8 (dark theme): list data 8 s after a cold start; covered WebView: 10 × 100 ms timers took ~1.01 s (no throttling), `visibilityState` visible, a SillyTavern event while covered produced fresh `characters` + `recentChats`, no WebView in the accessibility tree, a tap on the list where SillyTavern's input sits doesn't reach the page; Chat: WebView in the accessibility tree, tap on the input opens the keyboard (`mInputShown=true`) with the input row right above it; landscape in Chat and on the list (no page visible in the cutout strip); 13 list → Chat → back round trips across Bob, Tide Pods, Seraphina and Jasper with on-device frame capture: no blank or half-drawn page frame (cover, then the settled chat — Bob ~1 s, Tide Pods ~1.5–2 s, Seraphina with images ~2.5 s), reopening the already-open chat is immediate, and a JS marker survived the whole session (no reload); 0 crashes
- Not verified: pull to refresh by hand (covered by the ViewModel test; `refresh()` exercised through the bridge), the error banners on device (previews + Compose UI tests), a library with no characters, Advanced (still a placeholder)
- Version `0.1.8` / `versionCode 9`

### 2026-11-17 — Docs: Compose UI tests going forward (branch `docs/compose-ui-tests`)

- `coding-standards.md` → Testing: new and changed native Compose screens/components with real behavior get Compose UI tests in `androidTest` (stateless `…Content` composable, fixed `UiState`, fake callbacks; states render, taps call the right callback, enabled/disabled rules). Layout-only composables and the WebView host are exempt; WebView/bridge stays manual on device
- Unit tests (fast) still run before every commit and merge via `./gradlew build` — now stated explicitly in the workflow's Test step. Android tests (slow) run only before each release: `ai-guidelines.md` → Releasing to main has a new step 2 (run `./gradlew connectedDebugAndroidTest` on an emulator or device, note the result in the PR)
- `CLAUDE.md`: added `./gradlew connectedDebugAndroidTest`. Not in CI (would need an emulator runner); no tests backfilled for existing screens
- Dependencies were already present (`ui-test-junit4`, `ui-test-manifest`), so no build changes and no version bump

<!-- Completed work is added here, newest last -->

### 2026-11-17 — Phase 1.5 Clean Theme (branch `feature/clean-theme`)

- `clean-theme.css` (all rules under `body.tt-clean`, colors left to SillyTavern's theme): hides the top toolbar and all nine drawer panels, the TopInfoBar chat bar, and the in-chat `.drawer-opener` shortcut buttons (they would open hidden drawers); `#sheld` fills the viewport (`top: 0`, `100dvh`); message text and input at least 16px (`max(16px, var(--mainFontSize))`); input-row buttons at least 48×48 without touching their `display`, so `#mes_stop` stays hidden until a generation runs
- Bridge: `setClean(enabled)`, `installStyle(id, base64)` (UTF-8 via `TextDecoder`, replaces an existing tag); `selectCharacter` opens the chat without the character editor (`switchMenu: false`). Kotlin: `BridgeScript.installStyle`, `PageModeStore` (clean on by default), `BridgeInstaller` injects the stylesheet + current mode after the bridge on every page load, `BridgeClient.setCleanMode()`
- Unit tests: 2 new in `BridgeScriptTest` (Base64 round-trip with quotes, backslashes, newlines and unicode; only Base64-safe characters in the script); `./gradlew build` green
- Verified on Medium_Phone_API_35 against SillyTavern 1.19.0 over Tailscale (DevTools + screenshots): cold start → `tt-clean` on, toolbar and TopInfoBar hidden, `#sheld` at the top and full height, all four input buttons 48×48, messages 16px, one style tag; welcome screen without the drawer shortcuts; opening Bob through the bridge shows his chat directly (character panel stays closed); `setClean(false)` brings back the full UI, `setClean(true)` hides it again; `location.reload()` re-applies clean mode with still one style tag
- First pass bug fixed during verification: forcing `display: flex` on the input buttons made the hidden stop button appear
- Not verified on device: `BridgeClient.setCleanMode` from Kotlin (no caller until 1.8; the JS it calls was exercised), the Pixel 8, a light SillyTavern theme
- Version `0.1.7` / `versionCode 8`

### 2026-11-17 — Fix: WebView load timeout and status-bar strip (branch `fix/webview-load-timeout`)

- **Load timeout:** new `LoadWatchdog` (15 s, coroutine timer on the app scope). Started when the app loads the server and on every `onPageStarted`; cancelled by `onPageCommitVisible` (the server's response started rendering), so a slow but reachable server is never cut off. On timeout, if the page is still loading: `stopLoading()` + `PageError.Unreachable`
- **Status-bar strip — cause found:** a blank WebView paints white, and a Compose `AndroidView` doesn't clip, so the white page also covered the status-bar area (invisible light icons in dark theme). Fix: WebView background transparent (also removes the white flash before SillyTavern paints) and `clipToBounds()` on the `AndroidView`
- Unit tests: `LoadWatchdogTest` (4, virtual time); `./gradlew build` green
- Verified on Medium_Phone_API_35 (dark theme): error state with a dark status bar and visible icons (white before the fix); emulator `network delay 120000` (a connection that never answers) → Unreachable overlay ~20 s after launch, including app start-up and polling (was ~2 min on the Pixel); `network delay none` + Retry → page and bridge (`Ready`) back; `network delay 5000` (slow but reachable) → page loads, `Ready` after ~14 s, no false timeout
- Verified on Pixel 8 (0.1.6): Tailscale off → cold start → Unreachable overlay ~18 s after launch (was ~120 s), dark status bar with visible icons in both the loading and error states; Tailscale on → Retry → SillyTavern and the bridge (`Ready`, `characters (3)`) back
- Version `0.1.6` / `versionCode 7`

### 2026-11-17 — Phase 1.4 JS Bridge Foundation (branch `feature/js-bridge`)

- Assets: `st-selectors.js` holds every SillyTavern name (event keys, context members, character/message fields); `bridge.js` waits for `SillyTavern.getContext()`, listens to SillyTavern's `eventSource`, posts `{ v: 1, type, ... }` messages (`ready`, `characters` debounced 300 ms, `chatChanged`, `messageReceived` for character messages only, `error`) and exposes a frozen `window.__ttBridge` (`requestCharacters`, `selectCharacter` maps avatar ID → index → `selectCharacterById`, `setMode` swaps the `tt-mode-*` body class). Both scripts guard against running twice
- Data: `BridgeInstaller` registers the `TravelingTavern` listener for the saved origin only (re-registered if the server changes, main frame only) right before each load, and injects the scripts on every `onPageFinished`; `BridgeMessageParser` never throws (malformed / missing `v` or `type` / missing fields → dropped and logged, unknown types → `Unknown`); `BridgeRepository` exposes `messages`, `characters`, `isReady` (reset on each page start) and logs only the message type at DEBUG; `BridgeScript` + `BridgeClient` are the only place JS call strings are built, arguments JSON-encoded with U+2028/2029 escaped
- Domain: `CharacterSummary` (`lastChatAt: Instant?`)
- WebView without `WEB_MESSAGE_LISTENER` → `PageError.WebViewOutdated` overlay ("update it in the Play Store", no Retry / Server settings since neither helps)
- Unit tests: `BridgeMessageParserTest` (10), `BridgeScriptTest` (6); `./gradlew build` green, lint only version suggestions
- Verified on Medium_Phone_API_35 against SillyTavern 1.19.0 (DevTools + logcat): cold start delivers `characters (3)` then `Ready`; `selectCharacter("default_Seraphina.png")` opens Seraphina in SillyTavern and Kotlin gets `ChatChanged`; an unknown ID produces a bridge `Error`; `setMode` sets `tt-mode-story` and rejects invalid names; `location.reload()` re-injects cleanly with exactly one `Ready`
- Verified on Pixel 8 (Android 17), user and Claude (logcat + DevTools): cold start → `characters` + `Ready`, full-height page; `selectCharacter` for `default_Seraphina.png`, `Tide Pods.png` (space in ID) and `Bob.png` each opened the chat and produced one `ChatChanged`; replies the user generated in Bob's and Tide Pods' chats produced `MessageReceived`; page reload → exactly one `Ready`; Tailscale off → Unreachable overlay, Tailscale on → Retry recovers with the bridge back; drawer + JS marker survived backgrounding and two rotations (no reload); import file picker opens, cancel returns to a usable page twice; GitHub link opens the browser. Whole run: 0 crashes, 0 bridge errors or malformed messages
- Found on the Pixel, not fixed here: with Tailscale **off** the page sits on the loading spinner for ~2 min (the WebView's own network timeout) before the Unreachable overlay appears — airplane mode fails fast, a silently dropped connection doesn't. Also, during the cold-start loading and error states the status-bar strip is white with invisible icons
- Not verified on device: the Kotlin `BridgeClient` path (no screen calls it until 1.6 — covered by `BridgeScriptTest`, the JS it produces was exercised through DevTools), the character-list events from editing/duplicating a character (skipped to avoid changing the user's data), the origin restriction (relies on the WebView API; other-host pages open in the browser anyway), the outdated-WebView overlay (preview only)
- Version `0.1.5` / `versionCode 6`

### 2026-11-17 — Android CI (branch `chore/android-ci`)

- Added `.github/workflows/android-ci.yml`: on PRs into `main`, pushes to `main` and manual runs, builds (`assembleDebug`), runs unit tests and lint on `ubuntu-latest` with Temurin JDK 21 and `gradle/actions/setup-gradle` caching; uploads the debug APK and test/lint reports as artifacts (14 days)
- Marked `gradlew` executable in Git (`100644` → `100755`); it was committed from Windows without the flag, so `./gradlew` failed with "Permission denied" on Linux/macOS. No effect on Windows (`gradlew.bat`)
- Branching: `main` is now protected by a GitHub ruleset (PR + green CI required, no force pushes or deletion); new long-lived `dev` branch for day-to-day merges. `ai-guidelines.md` updated (branch from and merge into `dev`, release to `main` by PR with a merge commit); CI also runs on PRs into and pushes to `dev`
- No app code changed, so no version bump
- Verified by the PR's own CI run; nothing on device changed

### 2026-11-17 — Min SDK 31 (branch `chore/min-sdk-31`)

- `minSdk` 26 → 31 (Android 12) so 1.7b's on-device speech recognizer (`createOnDeviceSpeechRecognizer`, API 31) is always available as an API; updated `CLAUDE.md`, `README.md` and the 1.7b spec (`Unavailable` now only means on-device recognition isn't available)
- Removed the now-redundant API 31 check around dynamic color in `Theme.kt`
- `./gradlew build` green; installed on Pixel 8 (`minSdk=31`, `0.1.4`), launches and loads SillyTavern normally
- Version `0.1.4` / `versionCode 5`

### 2026-11-17 — Phase 1.3 WebView Host (branch `feature/webview-host`)

- Domain: `PageState` (`Loading` / `Ready` / `Error`) and `PageError` (`Unreachable`, `AuthFailed`, `HttpError(code)`)
- Data: `WebViewHolder` (`@Singleton`) creates the one WebView lazily on a `MutableContextWrapper`, swaps in the Activity on `attach` and back on `detach` (guarded so a late detach can't undo a new attach), loads the saved URL once and reloads only when it changes or a settings change happens while in an error state. `PageStateTracker` keeps a main-frame error alive past the `onPageFinished` that follows it. `TavernWebViewClient`: basic auth answered once and only for the saved host, main-frame errors → `PageState.Error`, other-host links → system browser. `TavernWebChromeClient` (Aegis port, ISC notice kept): file chooser via Activity Result, mic/camera only for the server origin and only what the user granted. `@ApplicationScope` coroutine scope added (`CoroutineModule`)
- UI: `SillyTavernWebView` + `SillyTavernWebViewModel` attach the shared WebView (removed from its parent, never destroyed, on dispose), flush cookies on pause, `BackHandler` while the WebView can go back; `PageStateOverlay` with loading and error states (Retry, Server settings) and previews. Chats tab hosts it temporarily (spec said Home, which no longer exists)
- Navigation: Server settings opens Connect; saving from there pops back instead of rebuilding Chats. Scaffold padding is now consumed so `imePadding()` doesn't double-count the system bars; `adjustResize` set on the Activity
- Manifest: `RECORD_AUDIO`, `CAMERA` (requested only when the page asks), camera/mic features optional; `buildConfig` enabled for `BuildConfig.DEBUG`
- Unit tests: `PageStateTrackerTest` (5), `ServerOriginTest` (5); `./gradlew build` green, lint only version suggestions
- Verified on Medium_Phone_API_35 against SillyTavern 1.19.0 (over Tailscale): page loads in the Chats tab; an open SillyTavern drawer survived tab switches, backgrounding and two rotations (no reload); airplane mode → Unreachable overlay → Retry recovers; Server settings opens Connect and back returns; character import opens the system file picker, cancel leaves the page usable (second open works); GitHub link opens Chrome, not the WebView
- Verified on Pixel 8 (Android 17) against the same server: clean install → Connect → Save loads a full-height page (before the fix: collapsed layout, `100dvh` = 0); docked keyboard pushes SillyTavern's input bar above it; GitHub link opens the default browser and back returns; backgrounding and returning keeps the page (a JS marker survived, so no reload)
- Mic: voice input via the SillyTavern **Speech Recognition** extension tested manually on the Pixel 8 by the user — works. Which STT provider it uses, and whether it goes through our `onPermissionRequest` path over plain `http://`, was not checked. Speech Recognition is now listed under "SillyTavern Extensions" in `README.md`
- Not verified on device: back navigation inside the WebView (SillyTavern adds no history entries in normal use), basic auth (server has it off), camera
- Version `0.1.3` / `versionCode 4`

### 2026-11-16 — Phase 1.2 Connect Screen (branch `feature/connect-screen`)

- Domain: `ServerConfig`, `ConnectionResult`, `normalizeServerUrl()` (pure `java.net.URI`, adds `http://`, strips path/trailing slash, lowercases host), `ServerConfigRepository`, `ConnectionTester`, `TestConnectionUseCase`
- Data: Preferences DataStore (`settings`) for the base URL; username and password stored as Tink AES256-GCM ciphertext wrapped by an Android Keystore master key (`SecurityModule`, `CredentialCipher`); `ServerConfigRepositoryImpl` exposes decrypted credentials only to the data layer
- Network: one `@Singleton` `OkHttpClient` with `BasicAuthInterceptor` — adds basic auth only when scheme/host/port match the saved server and no `Authorization` header is already set. `OkHttpConnectionTester` does `GET {baseUrl}/` with a 10 s call timeout and classifies by status code and `<title>SillyTavern</title>` (first 64 KB of body)
- UI: `ConnectScreen` (stateless `ConnectContent` + previews for empty/testing/error/success), `ConnectViewModel` — any input edit resets the test result so Save is only enabled for tested inputs; `AuthRequired` auto-reveals the login fields
- Navigation: `StartDestinationViewModel` reads the saved config once at launch → Connect or Chats; saving navigates to Chats with Connect popped; tab switching now pops to the Chats graph instead of the root start destination (which may be Connect)
- Tink `1.24.0` added; `FilledTonalButton` used for Test (the only filled button is Save, per ui-guidelines)
- Unit tests: `ServerUrlTest` (6), `TestConnectionUseCaseTest` (3), `ConnectViewModelTest` (6) — all passing; `./gradlew build` green, lint only version suggestions
- Verified on Medium_Phone_API_35 against the real SillyTavern 1.19.0 (over Tailscale): fresh install opens Connect; bare `host:port` → "Connected to SillyTavern" → Save → Chats; back exits (Connect popped); cold relaunch opens Chats directly. Error cases shown correctly: invalid address, `example.com` → not SillyTavern, unreachable IP → timeout message after 10 s, login toggle reveals fields. `AuthRequired`/`AuthFailed` not exercised on device (server has basic auth off) — covered by unit tests
- Version `0.1.2` / `versionCode 3`

### 2026-11-16 — Phase 1.1b Bottom Navigation & Visual Style (branch `feature/bottom-navigation`)

- `Routes.kt` split into `Routes.Tab` (Chats, Characters, Me — nested graphs) and `Routes.FullScreen` (Connect, Chat, Advanced). Home and Settings screens removed
- `TravelingTavernNavHost` is now the shell: one `Scaffold`, `TravelingTavernBottomBar` shown only when the current destination is inside a tab graph and the IME is hidden; tab switches use `popUpTo(start) { saveState }` + `restoreState`; re-selecting the current tab emits on `TabReselectEvents` (via `LocalTabReselectEvents`) for 1.6 lists to scroll to top
- Tab icons are vector drawables (Material Icons, outlined/filled pairs) — `?attr/colorControlNormal` tint had to be dropped since the app has no AppCompat; Compose `Icon` tints from `LocalContentColor`
- Theme: neutral light/dark palette, amber on primary **and** secondary so the `NavigationBar` indicator uses the accent; `useDynamicColor` parameter defaults to `false`; Inter Regular/Medium/SemiBold bundled in `res/font/` (OFL text in `licenses/Inter-OFL.txt`) and applied to the whole M3 type scale; shapes 12/16/16dp
- Version `0.1.1` / `versionCode 2`
- `./gradlew build` passes; lint only reports newer-version suggestions
- Verified on Medium_Phone_API_35: all three tabs in dark and light, selected indicator and labels, back from Me returns to Chats, back again exits. Not device-verifiable yet: bottom bar hiding on full-screen routes and under the keyboard (no navigation to those routes or text fields exist until 1.2/1.6) — covered by code path only

### 2026-11-16 — Phase 1.1 Project Setup & App Shell (branch `feature/project-setup`)

- Added all spec dependencies to the version catalog; Hilt (`TravelingTavernApplication`, `MainActivity`), Compose navigation, brand theme (Color/Shape/Theme), `Routes.kt`, `TravelingTavernNavHost`, placeholder screens, `assets/web/` placeholders, `network_security_config.xml`, `INTERNET` permission, package scaffold
- **Toolchain notes (AGP 9.0.1 uses built-in Kotlin):**
  - Android Studio's template pulled `core-ktx 1.19.0` / `lifecycle 2.11.0`, which require compileSdk 37 + AGP 9.1. Only SDK 36 is installed and the spec targets 36, so pinned `core-ktx 1.17.0`, `lifecycle 2.9.4`, `activity-compose 1.11.0`, Compose BOM `2025.09.01`
  - Kotlin plugins (compose, serialization) set to `2.2.10` to match AGP 9.0's bundled Kotlin
  - Hilt `2.60.1` — 2.57.x fails on AGP 9 ("Android BaseExtension not found")
  - KSP `2.3.4` — KSP1 (`2.0.21-1.0.x`) and `2.2.10-2.0.2` both fail on AGP 9 (`kotlin.sourceSets` DSL not allowed with built-in Kotlin)
  - `material-icons-core` added explicitly (not transitive from Material 3 1.4)
  - Gradle needs `JAVA_HOME` pointed at Android Studio's JBR (`C:\Program Files\Android\Android Studio1\jbr`)
- `./gradlew build` passes (lint + unit tests). Remaining lint warnings are newer-version suggestions and unused template colors in `res/values/colors.xml`
- Verified on Medium_Phone_API_35 emulator: launches to Home, top bar Settings action navigates to Settings, back returns, light and dark both render, no crashes. Dynamic color was active (API 35), so the brand fallback palette is covered by previews only