# Phase 1.1 — Project Setup & App Shell

## Overview

Initialize the TravelingTavern Android project with all core dependencies, theming, and the navigation shell. No real features yet — just a solid foundation every future phase builds on.

## Requirements

- Create new Android project (Kotlin, Jetpack Compose, package `com.tiffles.travelingtavern`, min SDK 26, target SDK 36)
- Add core dependencies via the version catalog (`gradle/libs.versions.toml`):
  - Hilt (+ `hilt-navigation-compose`)
  - Navigation Compose
  - Material 3
  - AndroidX WebKit (`androidx.webkit`)
  - DataStore Preferences
  - OkHttp
  - Coil (Compose, OkHttp network)
  - Kotlin Serialization
  - Lifecycle runtime Compose (`collectAsStateWithLifecycle`)
  - Test: JUnit, mockk, kotlinx-coroutines-test
  - Room is **not** added yet — it comes with the first feature that needs app-owned data
- Configure Hilt — `@HiltAndroidApp` on `TravelingTavernApplication`, `@AndroidEntryPoint` on `MainActivity`
- Define the theme in `ui/theme/` (Color, Type, Shape, Theme)
  - Dynamic color on Android 12+, TravelingTavern brand palette as fallback (warm tavern tones: amber primary, deep wood-brown surfaces in dark mode)
  - Light and dark mode from the start
  - Edge-to-edge enabled in `MainActivity`
- Navigation shell with placeholder screens (centered `Text` label each):
  - `Connect`
  - `Home` (start destination for now)
  - `Chat` — takes `characterId: String` argument
  - `Settings`
  - `Advanced`
- No bottom navigation. Home is the root; Chat, Settings, and Advanced are pushed on top. Home has a top app bar with a Settings action
- Define all navigation routes in `ui/navigation/Routes.kt`
- Scaffold the package structure from `coding-standards.md` (empty packages are fine)
- Create empty asset placeholders: `assets/web/bridge.js`, `assets/web/st-selectors.js`, `assets/web/clean-theme.css`
- Add `res/xml/network_security_config.xml` allowing cleartext traffic, referenced from the manifest, with a comment explaining why (user's SillyTavern is usually `http://` on a private tailnet)
- Add `INTERNET` permission to the manifest

## Notes

- Character IDs in SillyTavern are avatar file names (e.g. `Seraphina.png`), so `characterId` is a `String`, URL-encoded in the route **(verify when 1.6 is built)**
- No WebView, DataStore, or network code yet — just dependencies

## References

- @context/project-schema.md
- @context/coding-standards.md
