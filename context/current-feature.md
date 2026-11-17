# Current Feature

Phase 1.3 — WebView Host

## Status

Completed

## Goals

One WebView, created once and kept alive for the whole app session, showing the user's SillyTavern. Screens attach and detach it — they never create their own and never reload it.

### Deliverables

- `PageState` / `PageError` in `domain/model/`
- `WebViewHolder` (`data/webview/`, `@Singleton`): lazy WebView on a `MutableContextWrapper`, `attach` / `detach`, loads the saved base URL once and reloads only when it changes, exposes `pageState`, `pageFinished`, `canGoBack`
- `TavernWebViewClient`: basic auth from stored credentials (one attempt), main-frame network / HTTP errors mapped to `PageState.Error`, links to other hosts open in the system browser
- `TavernWebChromeClient` (ported from Aegis, ISC notice kept): file chooser through an Activity Result launcher, mic / camera requests mapped to `RECORD_AUDIO` / `CAMERA` runtime permissions, granted only for the server's own origin
- `SillyTavernWebView` composable + `SillyTavernWebViewModel` (`ui/components/`): attaches the shared WebView, loading and error overlays (Retry, Server settings), back handling
- Manifest: `RECORD_AUDIO`, `CAMERA`, `adjustResize`
- Temporary host: the Chats tab shows the WebView full-size until 1.6 replaces it with the real list
- Unit tests for `PageStateTracker` and `isSameServer`
- Version bump to `0.1.3` / `versionCode 4`

## Notes

- The spec says to host the WebView on "Home" temporarily; Home was replaced by the tabs in 1.1b, so the Chats tab is the temporary host
- Mic access needs a secure origin, so over plain `http://` the mic prompt never fires — expected until an HTTPS (`tailscale serve`) address is used
- Aegis reference read from source (`android/app/src/main/java/com/sillytavern/android/MainActivity.java`): its file chooser is ported; its permission handler grants everything automatically, so ours is stricter by design
- **(verified)** A page that starts loading while the WebView is zero-sized keeps every CSS viewport unit (`vh`, `dvh`, `svh`, `lvh`) at 0 for good, even after the view grows (`innerHeight` is correct). SillyTavern's `body { height: 100dvh }` then collapses and the input bar sits at the top. Found on the Pixel 8 via DevTools after Connect → Save; `WebViewHolder` now waits for a non-zero layout before `loadUrl`

## References

- @context/specs/phase1-3-webview-host-spec.md
- @context/coding-standards.md
- @context/ui-guidelines.md

## History

<!-- Completed work is added here, newest last -->

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