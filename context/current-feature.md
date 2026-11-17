# Current Feature

Phase 1.4 — JS Bridge Foundation

## Status

Completed

## Goals

A typed, two-way channel between Kotlin and the SillyTavern page. JS listens to SillyTavern's own events and posts messages to Kotlin; Kotlin calls into SillyTavern through one wrapper.

### Deliverables

- `st-selectors.js` (every SillyTavern global/event name) and `bridge.js` (`window.__ttBridge`, guarded against re-injection) in `assets/web/`
- `BridgeInstaller`: `addWebMessageListener("TravelingTavern")` limited to the saved server origin, registered before each load; scripts injected on `pageFinished`; "WebView too old" error state when the feature is missing
- `BridgeMessage` sealed interface + parser (`ready`, `characters`, `chatChanged`, `messageReceived`, `error`, `Unknown`); malformed input is logged and dropped
- `BridgeRepository` (`messages`, `characters`, `isReady`) and `BridgeClient` (`requestCharacters`, `selectCharacter`, `setMode`) — the only place JS call strings are built, all arguments JSON-encoded
- Domain `CharacterSummary`
- Unit tests for message parsing and call escaping
- Version bump to `0.1.5` / `versionCode 6`

## Notes

- **(verified on SillyTavern 1.19.0, live page via DevTools)**
    - Ready: `event_types.APP_READY` = `app_ready`. `eventSource.autoFireAfterEmit` contains `app_ready`, so a listener added after it fired is called immediately — no separate "already ready" flag needed
    - `CHAT_CHANGED` = `chat_id_changed`, `MESSAGE_RECEIVED` = `message_received`; list-changing events used: `CHARACTER_EDITED`, `CHARACTER_DELETED`, `CHARACTER_RENAMED`, `CHARACTER_DUPLICATED`, `CHARACTER_PAGE_LOADED`, `CHAT_CREATED`
    - `context.characterId` is an **index** into `context.characters` (undefined when no character / in a group); the stable ID is `character.avatar` (e.g. `Bob.png`). `selectCharacterById(index)` takes the index, so the bridge maps avatar → index
    - Character fields used: `name`, `avatar`, `date_last_chat` (epoch ms, may be fractional or missing)
    - Thumbnails: `context.getThumbnailUrl("avatar", file)` → `/thumbnail?type=avatar&file=<encoded>`
    - Avatar IDs are not always the name: the bundled Seraphina is `default_Seraphina.png`, and IDs can contain spaces (`Tide Pods.png`)

## References

- @context/specs/phase1-4-js-bridge-spec.md
- @context/coding-standards.md

## History

<!-- Completed work is added here, newest last -->

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
- Verified on Medium_Phone_API_35 against SillyTavern 1.19.0 at `100.127.52.41:8000`: page loads in the Chats tab; an open SillyTavern drawer survived tab switches, backgrounding and two rotations (no reload); airplane mode → Unreachable overlay → Retry recovers; Server settings opens Connect and back returns; character import opens the system file picker, cancel leaves the page usable (second open works); GitHub link opens Chrome, not the WebView
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
- Verified on Medium_Phone_API_35 against the real SillyTavern 1.19.0 at `100.127.52.41:8000`: fresh install opens Connect; bare `host:port` → "Connected to SillyTavern" → Save → Chats; back exits (Connect popped); cold relaunch opens Chats directly. Error cases shown correctly: invalid address, `example.com` → not SillyTavern, unreachable IP → timeout message after 10 s, login toggle reveals fields. `AuthRequired`/`AuthFailed` not exercised on device (server has basic auth off) — covered by unit tests
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