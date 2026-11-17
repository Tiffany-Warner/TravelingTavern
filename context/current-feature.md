# Current Feature

Phase 1.2 — Connect Screen

## Status

Completed

## Goals

Let the user point the app at their SillyTavern server, optionally log in, and test the connection. Shown on first launch when no server is saved; later reachable from error states and Me → Server → "Change server".

### Deliverables

- `ServerConfig`, `ConnectionResult`, `ServerConfigRepository`, `TestConnectionUseCase` in `domain/`
- Server URL in DataStore; username/password encrypted with Tink + Android Keystore; password never exposed to the UI
- Shared `OkHttpClient` (Hilt `@Singleton`) with a basic-auth interceptor that only applies stored credentials to the configured server host
- Connection test: `GET {baseUrl}/` with a 10 s timeout, mapped to `Success` / `NotSillyTavern` / `AuthRequired` / `AuthFailed` / `Unreachable` / `InvalidUrl`
- `ConnectScreen` + `ConnectViewModel`: URL field (adds `http://` when no scheme), "Server requires login" toggle, Test & Save, result messages per spec
- Start destination: no saved config → Connect, else Chats tab; saving navigates to Chats and clears Connect from the back stack
- Unit tests for URL normalization, `TestConnectionUseCase` result mapping, and `ConnectViewModel`
- Version bump to `0.1.2` / `versionCode 3`

## Notes

- SillyTavern detection marker **(verified)**: `GET /` returns 200 with `<title>SillyTavern</title>` on SillyTavern 1.19.0; `GET /version` also returns JSON with `agent: "SillyTavern:<ver>..."` as a fallback marker if the title ever changes
- Real server for testing: this PC's SillyTavern on the Tailscale IP `100.127.52.41:8000`

## References

- @context/specs/phase1-2-connect-screen-spec.md
- @context/coding-standards.md
- @context/ui-guidelines.md

## History

<!-- Completed work is added here, newest last -->

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