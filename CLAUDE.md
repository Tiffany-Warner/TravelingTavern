# TravelingTavern

An Android companion app for a self-hosted SillyTavern. The phone is a clean, native shell around the user's desktop SillyTavern (reached over Tailscale), plus native features a browser can't do well: notifications, reminders, voice notes, selfies, and calls.

SillyTavern is the brain. This app never re-implements prompt building, never writes chat files, and never stores chats itself.

## Context Files

Read the following at the start of every session:

- @context/ai-guidelines.md
- @context/coding-standards.md
- @context/current-feature.md
- @context/project-schema.md

## Commands

```bash
./gradlew build          # full project build — run before every commit
./gradlew assembleDebug  # build debug APK
./gradlew test           # run unit tests
./gradlew lint           # run lint checks
```

## Stack

- **Kotlin** with **Jetpack Compose** (Material 3)
- **Hilt** for dependency injection
- **Jetpack Navigation Compose** for navigation
- **AndroidX WebKit** (`androidx.webkit`) for the WebView and JS bridge
- **DataStore** (Preferences) for settings
- **Room** for app-owned data only (added when a feature needs it)
- **OkHttp** for direct HTTP calls; **Coil** for images
- **Kotlin Serialization** for JSON (bridge messages, settings)
- Min SDK 26, Target SDK 36

## Versioning

Semantic versioning, pre-1.0 while the app is under construction. Both values live in `app/build.gradle.kts`.

- `versionName` = `0.<phase>.<increment>` — `0.1.x` during Phase 1, `0.2.0` when Phase 2 lands, and so on. Bump the last number for each completed sub-feature or fix within a phase
- `versionCode` increments by 1 for every build that gets installed or shared; it never goes down
- `1.0.0` is reserved for when Phase 1 is complete and the app is usable day-to-day

## Architecture

MVVM + Clean Architecture. Features are organized into three layers:

- `ui/` — Composables and ViewModels, organized by feature
- `domain/` — Use cases, repository interfaces, and domain models (pure Kotlin)
- `data/` — Repository implementations, DataStore, WebView/bridge plumbing, network, mappers

All navigation routes are defined in `ui/navigation/Routes.kt`.

Injected web assets (JS bridge script, clean-theme CSS) live in `app/src/main/assets/web/`.

## Related Repos

- **TravelingTavern-Bridge** — SillyTavern server plugin on the desktop (push events, media, reminders, card writes)
- **SillyTavern-EchoText-Proactive** (fork) — adds a webhook when a proactive message is generated

Changes needed on the desktop side go in those repos, not here.
