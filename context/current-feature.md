# Current Feature

Phase 1.1b — Bottom Navigation & Visual Style

## Status

Completed

## Goals

Replace the single-root navigation from 1.1 with a three-tab bottom navigation bar and apply the quiet visual style from `ui-guidelines.md`. Tabs are still placeholders — real content arrives in 1.6 and 1.8.

### Deliverables

- Material 3 `NavigationBar` with three tabs: Chats (start), Characters, Me — labels always shown
- Per-tab nested back stacks with `saveState` / `restoreState`
- Re-selecting the current tab emits a scroll-to-top event (hook only; lists arrive in 1.6)
- Bottom bar hidden on full-screen destinations (Connect, Chat, Advanced) and while the keyboard is open
- `Routes.kt` reorganized into `TabRoutes` and `FullScreenRoutes`; Settings route and Home removed
- Neutral palette with single amber accent; dynamic color off by default behind a `useDynamicColor` flag
- Inter (Regular, Medium, SemiBold) bundled and mapped into the Material 3 type scale
- Shapes: 12dp small, 16dp medium/large
- Bottom bar previews in light and dark with each tab selected
- Version bump to `0.1.1` / `versionCode 2`

## Notes

- Tab icons are vector drawables (Material Icons, Apache 2.0) because chat bubble and people are not in `material-icons-core` and `material-icons-extended` is too heavy
- Inter is bundled under the SIL Open Font License; license text in `licenses/`

## References

- @context/specs/phase1-1b-bottom-navigation-spec.md
- @context/ui-guidelines.md
- @context/coding-standards.md

## History

<!-- Completed work is added here, newest last -->

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