# Current Feature

Phase 1.1 — Project Setup & App Shell

## Status

Not Started

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
