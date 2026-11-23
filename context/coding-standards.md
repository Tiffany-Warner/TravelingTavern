# Coding Standards

## Kotlin
- Idiomatic: `val`, data/sealed classes, extension functions, `object` singletons, `when` over if/else chains
- Explicit nullability, no platform types
- Named args when params share a type
- No `@Suppress` without a comment why

## Compose
- Compose only, no XML
- One job per composable; extract reusable UI
- Hoist state; keep composables stateless; no business logic (use ViewModel)
- Use `remember`/`derivedStateOf` to avoid recomputation; use effects correctly, not for logic
- Preview every composable (WebView host: preview loading/error states only)

## Architecture
- MVVM + clean layers: `ui/` (composables, ViewModels), `domain/` (use cases, repo interfaces, models), `data/` (repo impls, sources, mappers)
- One ViewModel per screen; exposes `StateFlow<UiState>` (sealed/data class), never mutable state
- Use cases: single responsibility, testable

## SillyTavern boundary (hard rules)
- ST is source of truth for characters, chats, prompts, extensions
- Never write ST chat/character files; never re-implement prompt assembly
- All ST reads and actions (select character, send, generate) go through JS bridge (later: TravelingTavern-Bridge endpoints for reads)
- `bridge.js` must work in any ST page: without `TravelingTavern` port it still hooks events and runs commands; only `post()` no-ops (final phase runs it headless on desktop, see `project-schema.md` → Built Toward the Final Phase)
- Chats keyed by ST's avatar file + chat file name; times from `send_date`. No app chat IDs or copies
- Anything affecting generation is saved in ST, never WebView-session-only. Exception: Phase 2 texting/story style, stored per chat in app (see `project-schema.md`)

## WebView
- Exactly one instance, owned by `@Singleton` `data/webview/WebViewHolder`
    - Built on `MutableContextWrapper(appContext)`; swap in Activity context on attach, back on detach (no leaks)
    - Screens attach/detach it; switching screens/tabs never reloads ST
- `setWebContentsDebuggingEnabled(BuildConfig.DEBUG)`
- `WebChromeClient` file chooser/permissions ported from Aegis (ISC): keep its copyright/permission notice in ported files

## JS bridge
- `WebViewCompat.addWebMessageListener`, allowed origin = configured server origin only. Never `addJavascriptInterface`
- If `WEB_MESSAGE_LISTENER` unsupported, show error state
- Messages: JSON with `v` and `type`, parsed (Kotlin Serialization) into sealed `BridgeMessage`. Unknown types: log and ignore, never crash
- Kotlin→JS only via `BridgeClient` (wraps `evaluateJavascript`); no JS strings in ViewModels
- Injected JS/CSS in `assets/web/`
- ST/extension selectors and globals only in `assets/web/st-selectors.js` and `data/bridge/StSelectors.kt` (if needed)

## DI
- Hilt only; no service locators or manual DI
- Scope correctly: `@Singleton`, `@ActivityScoped`, `@ViewModelScoped`

## Navigation
- Navigation Compose; routes as sealed class/`object` with `const val` strings
- Pass only primitive IDs; destination ViewModel fetches data
- No composable-to-composable navigation

## Data
- DataStore (Preferences) for settings
- Room only for app-owned data (notification history, reminder cache), never ST chats/cards. DAOs return `Flow`. Always migrate, never `fallbackToDestructiveMigration` in prod
- OkHttp for direct HTTP (connection test, images); Retrofit + Kotlin Serialization for TravelingTavern-Bridge APIs later
- One `@Singleton` `OkHttpClient` adds auth; Coil shares it
- One repository per feature domain; map to domain models, no DTOs/bridge messages in UI

## Security
- Basic-auth credentials encrypted (Tink + Keystore), never plain DataStore
- Never log credentials, cookies, credentialed URLs or message content at INFO+
- Cleartext HTTP allowed app-wide (tailnet `http://`), documented in `network_security_config.xml`

## Coroutines
- `viewModelScope`; never `GlobalScope`
- `IO` for disk/network, `Default` for CPU; WebView calls (`evaluateJavascript`, attach/detach) on `Main`
- `StateFlow` for UI state, `SharedFlow` for one-shot events; collect with `collectAsStateWithLifecycle()`

## Files
```
app/src/main/java/com/tiffles/travelingtavern/
├── ui/
│   ├── [feature]/  [Feature]Screen.kt, [Feature]ViewModel.kt, components/
│   ├── components/   # shared
│   ├── navigation/Routes.kt
│   └── theme/
├── domain/  model/, repository/, usecase/
├── data/
│   ├── bridge/      # BridgeClient, BridgeMessage, listeners
│   ├── webview/     # WebViewHolder, clients, file chooser, permissions
│   ├── settings/    # DataStore + encrypted credentials
│   ├── remote/
│   └── repository/
├── di/
└── util/
app/src/main/assets/web/  bridge.js, st-selectors.js, clean-theme.css
```

## Naming
- `PascalCase`: classes, interfaces, composables. `camelCase`: functions, vars. `SCREAMING_SNAKE_CASE`: constants. `snake_case`: resource IDs
- Files match primary class
- Injected CSS classes/DOM ids prefixed `tt-` (`tt-clean`, `tt-style-text`)

## Theming
- Material 3; dynamic color off, brand palette used. The "Use wallpaper colors" toggle comes with Appearance (`project-schema.md` → Later)
- All colors/type/shapes via `MaterialTheme`; no hardcoded colors or px (use `dp`/`sp`)
- Light + dark from the start
- Edge-to-edge with correct IME insets on every screen

## Errors
- `Result<T>` or sealed `Resource<T>` for async
- Surface via UiState, never crash silently; friendly messages, log details
- Every server-facing screen handles: loading, unreachable (likely Tailscale off), auth failed, empty

## Testing
- Unit (mockk, coroutines-test): ViewModels, use cases, `BridgeMessage` parsing incl. unknown/malformed
- Compose UI tests (`androidTest/`, `createComposeRule`) for new/changed native screens/components with behavior worth guarding:
    - Test stateless `…Content` with fixed `UiState` + fake callbacks (no Hilt/ViewModel/server)
    - Cover each state (loading/error/empty/content), taps → right callback, enabled/disabled buttons
    - Find by text/content description; `testTag` only if neither works
    - Skip layout-only composables and WebView host
    - Run only before release (`connectedDebugAndroidTest`, needs emulator, not in CI; see `ai-guidelines.md` → Release). Unit tests run every merge via `./gradlew build`
- WebView/bridge: verify manually on device vs real server; note what was checked in history entry

## Quality
- No unused imports/vars; no unexplained commented-out code
- Functions ≲40 lines; extract well-named helpers freely
- Self-documenting; KDoc for public APIs and non-obvious logic
- `./gradlew lint` clean before commit

## License
- This repo is AGPL-3.0-only (matches SillyTavern and the extension). No per-file license headers
- New dependencies and copied code must use a license that can be combined with AGPL v3 (Apache 2.0, MIT, ISC, BSD, LGPL, GPL-3.0, AGPL-3.0; fonts under OFL). Ask me before anything else. Copied code keeps its own notice
