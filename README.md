# TravelingTavern

An Android companion app for a self-hosted [SillyTavern](https://github.com/SillyTavern/SillyTavern). The phone is a clean, native shell around your desktop SillyTavern (reached over Tailscale), plus native features a browser can't do well: notifications, reminders, voice notes, selfies, and calls.

SillyTavern is the brain — this app never re-implements prompt building, never writes chat files, and never stores chats itself.

> **Status: under construction.** Phase 1 (foundation & clean shell) is in progress. See [`context/project-schema.md`](context/project-schema.md) for the full roadmap.

## Stack

- Kotlin + Jetpack Compose (Material 3)
- Hilt, Jetpack Navigation Compose
- AndroidX WebKit (WebView + JS bridge)
- DataStore, Room, OkHttp, Coil, Kotlin Serialization

Min SDK 31 (Android 12), Target SDK 36.

## Requirements

- A SillyTavern instance running on your desktop, reachable over [Tailscale](https://tailscale.com/)
- Android Studio

## SillyTavern Extensions

The app shows your own SillyTavern, so some features only work if these extensions are installed on the desktop. Without them the matching buttons are simply missing or do nothing — the app isn't broken.

| Extension | Type | Needed for | Status |
|---|---|---|---|
| Speech Recognition | SillyTavern extension (install from the Extensions panel) | The mic button in the message bar (voice input). The app asks for Android's microphone permission the first time it's used | Needed until Phase 1.7b, which adds the app's own on-device mic; after that it's optional and only shows in Advanced and on desktop |
| EchoText | SillyTavern extension | The Text tab on the Chat screen (Phase 1.7) | Needed from Phase 1.7 |
| SillyTavern-EchoText-Proactive (fork) | SillyTavern extension | Proactive message notifications (Phase 2) | Needed from Phase 2 |
| TravelingTavern-Bridge | SillyTavern server plugin | Push events, media, reminders, card writes (Phase 2+) | Needed from Phase 2 |

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