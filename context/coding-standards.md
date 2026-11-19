# Coding Standards

## Kotlin

- Use Kotlin idioms — prefer `val` over `var`, data classes, sealed classes, extension functions
- No platform types — always declare explicit nullability (`String?` vs `String`)
- Use `when` expressions over `if/else` chains for multiple branches
- Prefer `object` expressions/declarations for singletons
- Use named arguments for functions with multiple parameters of the same type
- No suppressed warnings without a comment explaining why

## Jetpack Compose

- Composable functions only (no XML layouts)
- Keep composables focused — one job per composable
- Extract reusable UI into standalone `@Composable` functions
- Use `remember` and `derivedStateOf` appropriately — avoid recomputation
- Hoist state up; composables should be as stateless as possible
- Ensure previews are made for each composable (the WebView host itself is exempt — preview its loading/error states instead)
- Use `LaunchedEffect`, `SideEffect`, and `DisposableEffect` correctly — don't misuse them for logic
- Avoid business logic inside composables — delegate to ViewModel

## Architecture

- **MVVM** with clean architecture layering:
  - `ui/` — Composables + ViewModels
  - `domain/` — Use cases, repository interfaces, domain models
  - `data/` — Repository implementations, data sources, mappers
- ViewModels expose `StateFlow<UiState>` — never expose mutable state directly
- Use `UiState` sealed classes or data classes per screen
- One ViewModel per screen/feature
- Use cases are single-responsibility and independently testable

## SillyTavern Boundary (hard rules)

- SillyTavern is the source of truth for characters, chats, prompts, and extensions
- Never write SillyTavern chat or character files directly, and never re-implement prompt assembly
- All reads from SillyTavern go through the JS bridge (or, later, TravelingTavern-Bridge endpoints)
- All actions inside SillyTavern (select character, send message, generate) go through the JS bridge so SillyTavern's own code runs
- `bridge.js` must run in any SillyTavern page, not only the app's WebView: with no `TravelingTavern` port it still hooks SillyTavern's events and runs its commands; only `post()` does nothing. The final phase runs it in a headless page on the desktop (see `project-schema.md` → Built Toward the Final Phase)
- Chats are identified by SillyTavern's own keys (character avatar file + chat file name); message times come from SillyTavern's `send_date`. No app-side chat IDs or chat copies
- Anything that affects generation is saved in SillyTavern, never applied only in the app's WebView session

## WebView

- Exactly **one** WebView instance for the app, owned by `data/webview/WebViewHolder` (`@Singleton`)
  - Create it with a `MutableContextWrapper` around the application context; swap in the Activity context while attached and back on detach, so the Activity never leaks
  - Screens attach/detach the same instance — switching screens or tabs must never reload SillyTavern
- Enable `WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)` — debug builds only
- File chooser and permission handling (`WebChromeClient`) is ported from the Aegis wrapper (ISC license) — keep its copyright and permission notice at the top of ported files

## JS Bridge

- Use `WebViewCompat.addWebMessageListener` with the allowed origin set to the configured server origin only. Never use `addJavascriptInterface`
- Check `WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)` and show an error state if unsupported
- Every message is JSON with a `v` (version) and `type` field, parsed with Kotlin Serialization into a sealed `BridgeMessage` class. Unknown types are logged and ignored, never crash
- Kotlin → JS calls go through one `BridgeClient` wrapper around `evaluateJavascript` — no scattered JS strings in ViewModels
- Injected scripts and CSS live in `app/src/main/assets/web/`
- All SillyTavern and extension selectors and global names live in `assets/web/st-selectors.js` (JS side) and `data/bridge/StSelectors.kt` (Kotlin side, if needed). Nowhere else

## Dependency Injection

- Use **Hilt** for all DI
- Annotate with `@HiltViewModel`, `@Inject`, `@Module`, `@Provides` appropriately
- Scope correctly: `@Singleton` for app-wide, `@ActivityScoped` / `@ViewModelScoped` as needed
- Never use service locators or manual DI

## Navigation

- Use **Jetpack Navigation Compose**
- Define routes as a sealed class or `object` with `const val` route strings
- Pass only primitive IDs between screens — fetch full objects in the destination ViewModel
- No direct composable-to-composable navigation

## Data Layer

- **DataStore (Preferences)** for settings (server URL, toggles)
- **Room** only for app-owned data (e.g. notification history, reminder cache) — never for SillyTavern chats or cards
  - Define `@Entity`, `@Dao`, and `@Database` clearly
  - Use `Flow<T>` return types in DAOs for reactive queries
  - Always run migrations — never use `fallbackToDestructiveMigration` in production
- **OkHttp** for direct HTTP (connection test, images); **Retrofit** + **Kotlin Serialization** for TravelingTavern-Bridge APIs when they arrive
- One shared `OkHttpClient` (Hilt `@Singleton`) that adds auth; Coil uses the same client
- Repository pattern: one repository per feature domain
- Map data models to domain models — don't leak DTOs or bridge messages into the UI layer

## Security

- Server credentials (basic auth) are stored encrypted (Tink + Android Keystore), never in plain DataStore
- Never log credentials, cookies, URLs with credentials, or message content at INFO level or above
- Cleartext HTTP is allowed app-wide (the user's server is usually `http://` on the tailnet); this is documented in `network_security_config.xml`

## Coroutines & Flow

- Use `viewModelScope` for ViewModel coroutines
- Use `Dispatchers.IO` for disk/network, `Dispatchers.Default` for CPU-heavy work
- WebView calls (`evaluateJavascript`, attach/detach) always run on `Dispatchers.Main`
- Never use `GlobalScope`
- Prefer `StateFlow` for UI state, `SharedFlow` for one-shot events
- Collect flows in the UI using `collectAsStateWithLifecycle()` (lifecycle-aware)

## File Organization

```
app/src/main/java/com/tiffles/travelingtavern/
├── ui/
│   ├── [feature]/
│   │   ├── [Feature]Screen.kt
│   │   ├── [Feature]ViewModel.kt
│   │   └── components/
│   ├── components/          # shared composables
│   ├── navigation/
│   │   └── Routes.kt
│   └── theme/
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
├── data/
│   ├── bridge/              # BridgeClient, BridgeMessage, listeners
│   ├── webview/             # WebViewHolder, clients, file chooser, permissions
│   ├── settings/            # DataStore + encrypted credentials
│   ├── speech/              # on-device speech recognizer (1.7b)
│   ├── remote/
│   └── repository/
├── di/
└── util/

app/src/main/assets/web/
├── bridge.js
├── st-selectors.js
└── clean-theme.css
```

## Naming

- Classes/Interfaces: `PascalCase`
- Functions/variables: `camelCase`
- Constants: `SCREAMING_SNAKE_CASE`
- Composables: `PascalCase` (they are functions but treated like components)
- Resource IDs: `snake_case`
- Files: match their primary class name
- Injected CSS classes and DOM ids: prefixed `tt-` (e.g. `tt-clean`, `tt-style-text`)

## Theming

- Use **Material 3** (`androidx.compose.material3`)
- Dynamic color (Material You) on Android 12+, with a TravelingTavern brand palette as fallback
- Define all colors, typography, and shapes via `MaterialTheme`
- No hardcoded colors — always reference theme tokens (`MaterialTheme.colorScheme.primary`, etc.)
- Support both light and dark mode from the start
- Edge-to-edge, with correct IME (keyboard) insets on every screen
- Use `dp` and `sp` — never hardcode pixel values

## Error Handling

- Use `Result<T>` or a sealed `Resource<T>` wrapper for async operations
- Surface errors through UiState — never crash silently
- Display user-friendly messages; log technical details
- Handle these states explicitly on every screen that touches the server: loading, server unreachable (likely Tailscale off), auth failed, empty

## Testing

- Unit tests for ViewModels and use cases (mockk, kotlinx-coroutines-test)
- Unit tests for `BridgeMessage` parsing, including unknown and malformed messages
- Compose UI tests (`app/src/androidTest/`, `createComposeRule`) for every new or changed native screen or component that has behavior worth guarding:
  - Test the stateless `…Content` composable with a fixed `UiState` and fake callbacks — no Hilt, no ViewModel, no server
  - Cover what the user can see and do: each state renders (loading, error, empty, content), taps call the right callback, buttons are enabled/disabled when they should be
  - Find nodes by visible text or content description; add a `Modifier.testTag` only when neither identifies the node
  - Skip composables that only lay things out, and the WebView host (manual testing below)
  - Unit tests are fast and run before every merge (`./gradlew build`). Android tests are slow, so they are not run on feature merges into `dev` — only before each release (`dev` → `main`): `./gradlew connectedDebugAndroidTest` on an emulator or device (see Releasing to main in `ai-guidelines.md`; not run in CI — it needs an emulator)
- WebView and bridge behavior is verified manually on device against the real server; note what was checked in the feature's history entry

## Code Quality

- No commented-out code unless explained
- No unused imports or variables
- Keep functions under 40 lines when possible — extract if longer
- Add helper functions liberally with names describing their purpose to maintain readability
- Write self-documenting code; add KDoc for public APIs and non-obvious logic
- Run `./gradlew lint` and fix warnings before committing
