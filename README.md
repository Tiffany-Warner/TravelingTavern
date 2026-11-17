# TravelingTavern

An Android companion app for a self-hosted [SillyTavern](https://github.com/SillyTavern/SillyTavern). The phone is a clean, native shell around your desktop SillyTavern (reached over Tailscale), plus native features a browser can't do well: notifications, reminders, voice notes, selfies, and calls.

SillyTavern is the brain — this app never re-implements prompt building, never writes chat files, and never stores chats itself.

> **Status: under construction.** Phase 1 (foundation & clean shell) is in progress. See [`context/project-schema.md`](context/project-schema.md) for the full roadmap.

## Stack

- Kotlin + Jetpack Compose (Material 3)
- Hilt, Jetpack Navigation Compose
- AndroidX WebKit (WebView + JS bridge)
- DataStore, Room, OkHttp, Coil, Kotlin Serialization

Min SDK 26, Target SDK 36.

## Requirements

- A SillyTavern instance running on your desktop, reachable over [Tailscale](https://tailscale.com/)
- Android Studio

## Build

```bash
./gradlew build          # full project build
./gradlew assembleDebug  # build debug APK
./gradlew test           # run unit tests
./gradlew lint           # run lint checks
```

## Project Docs

- [`CLAUDE.md`](CLAUDE.md) — project overview and conventions
- [`context/project-schema.md`](context/project-schema.md) — roadmap and phases
- [`context/coding-standards.md`](context/coding-standards.md) — architecture and style rules