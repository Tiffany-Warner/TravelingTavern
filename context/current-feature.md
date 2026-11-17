# Current Feature

Phase 1.1 — Project Setup & App Shell

## Status

Completed

## Goals

Create the TravelingTavern Android project with all core dependencies, theming, and the navigation shell. Every screen is a placeholder for now.

### Deliverables

- New Android project (`com.tiffles.travelingtavern`), Kotlin, Compose, min SDK 26, target SDK 36
- Core dependencies in the version catalog and `build.gradle.kts`
- Hilt wired up (`TravelingTavernApplication`, `MainActivity`)
- Material 3 theme with dynamic color + brand fallback, light and dark
- Navigation shell with placeholder screens: Connect, Home, Chat, Settings, Advanced
- Routes defined in `ui/navigation/Routes.kt`
- Project structure scaffolded to match `coding-standards.md`

## Notes

- No WebView yet — that's 1.3
- Start destination is Home for now; 1.2 adds the "no server configured → Connect" logic

## References

- @context/specs/phase1-1-project-setup-spec.md
- @context/coding-standards.md

## History

<!-- Completed work is added here, newest last -->

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
