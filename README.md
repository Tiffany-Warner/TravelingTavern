# TravelingTavern

Take your SillyTavern companions with you. TravelingTavern is a native Android app for your self-hosted [SillyTavern](https://github.com/SillyTavern/SillyTavern): a clean phone interface for your chats and characters, with companion features on the way — proactive messages, reminders, voice notes, selfies, and calls.

**SillyTavern keeps running on your own computer.** The app connects to it over your network. It never re-implements prompt building, never writes chat files, and never stores chats itself, so everything stays in sync with SillyTavern on your desktop.

> **Status: under construction.** Phase 1 (foundation and clean shell) is in progress. See [`context/project-schema.md`](context/project-schema.md) for the full roadmap.

## What It Does Today

- **Chats tab** — your recent conversations, newest first, with avatars and message previews
- **Characters tab** — your character library as a portrait grid, with search
- **Clean chat view** — SillyTavern's chat without the desktop menus, sized for a phone
- **Connect screen** — enter your server address and test it before saving

Coming next: a Lorebooks tab, the EchoText texting thread, on-device voice input, and the Me tab. After that, proactive message notifications (Phase 2) and the rest of the roadmap.

## How It Works

```
Your phone (TravelingTavern)  ──── network (e.g. Tailscale) ────  Your computer (SillyTavern)
  native screens + SillyTavern                                     characters, chats, prompts,
  in a built-in web view                                           extensions, your API keys
```

The app shows your real SillyTavern inside it and reads data like the character list straight from the page. Your characters, chats, settings and extensions all live in SillyTavern, exactly as on desktop.

## Before You Start

You need:

1. **SillyTavern running on your computer.** Tested with SillyTavern 1.19.0. The computer has to be on and SillyTavern running whenever you use the app.
2. **A way for your phone to reach SillyTavern by URL.** Any of these works — the app only needs an address:
    - **[Tailscale](https://tailscale.com/) (recommended)** — a free private network between your devices. Works at home and away, nothing exposed to the internet. → **[Tailscale setup guide](docs/tailscale-setup.md)**
    - **Same Wi-Fi only** — use your computer's local address (e.g. `http://192.168.1.50:8000`). Only works at home
    - **Your own setup** (reverse proxy, tunnel, etc.) — fine if you know how, but keep SillyTavern behind a login: it holds your API keys
3. **SillyTavern set to accept connections from your phone.** By default it only answers on the computer itself. Edit `config.yaml` in the SillyTavern folder (the [setup guide](docs/tailscale-setup.md#2-let-sillytavern-accept-connections) walks through it):
    - `listen: true`, plus a choice: **only your Tailscale devices** can connect (more locked down), or devices on the same Wi-Fi as your computer can too (simpler)
    - Add your phone to `whitelist` (and your computer too, if you open SillyTavern there through its Tailscale address), or turn the whitelist off and use SillyTavern's login instead
4. **An Android phone running Android 12 or newer.**

## Install

There are no prebuilt releases yet, so build it yourself:

1. Install [Android Studio](https://developer.android.com/studio)
2. Clone this repo and open it in Android Studio
3. Connect your phone (USB debugging on) and press **Run**, or build an APK with `./gradlew assembleDebug` (it lands in `app/build/outputs/apk/debug/`)

## First Launch

1. Open TravelingTavern. The **Connect** screen appears
2. Enter your SillyTavern address, e.g. `http://100.64.0.2:8000` (Tailscale) or `http://192.168.1.50:8000` (home Wi-Fi). A bare `host:port` works too
3. Tap **Test**. If it says "Connected to SillyTavern", tap **Save**
4. Your chats appear. Tap one to keep chatting

**Can't connect?** Check that SillyTavern is running, the computer is awake, and (if you use Tailscale) Tailscale is connected on the phone. More in the [setup guide's troubleshooting](docs/tailscale-setup.md#troubleshooting).

## SillyTavern Extensions

Install these in SillyTavern on your computer. The app shows your own SillyTavern, so it can only use what's installed there.

### Needed for App Features

Without these the matching parts of the app are simply missing — the app isn't broken.

| Extension | Type | Needed for | When |
|---|---|---|---|
| [Speech Recognition](https://github.com/SillyTavern/Extension-Speech-Recognition) | Extension (Extensions panel → Download Extensions & Assets) | The mic button in the message bar | Until Phase 1.7b adds the app's own on-device mic; optional after that |
| [EchoText](https://github.com/mattjaybe/SillyTavern-EchoText) | Extension | The Text tab on the Chat screen | From Phase 1.7 |
| [EchoText-Proactive](https://github.com/mattjaybe/SillyTavern-EchoText-Proactive) (TravelingTavern fork) | Server plugin | Proactive message notifications | From Phase 2 |
| TravelingTavern-Bridge | Server plugin | Push events, media, reminders, card writes | From Phase 2 |

### Recommended for the Best Experience

Not required by the app, but it makes phone chatting much better. Install it from SillyTavern's Extensions panel (**Install extension** → paste the link).

| Extension | What it does | Why it helps on the phone |
|---|---|---|
| [Memory Books](https://github.com/aikohanasaki/SillyTavern-MemoryBooks) | Turns stretches of a chat into memory entries in a lorebook, so characters recall past events | Long companion chats keep their history without huge context. The memories live in lorebooks, which the upcoming Lorebooks tab lets you browse and edit |

## Build

```bash
./gradlew build                      # full project build
./gradlew assembleDebug              # build debug APK
./gradlew test                       # run unit tests
./gradlew connectedDebugAndroidTest  # run Compose UI tests on a connected emulator/device
./gradlew lint                       # run lint checks
```

## Stack

- Kotlin + Jetpack Compose (Material 3)
- Hilt, Jetpack Navigation Compose
- AndroidX WebKit (WebView + JS bridge)
- DataStore, Room, OkHttp, Coil, Kotlin Serialization

Min SDK 31 (Android 12), Target SDK 36.

## Project Docs

- [`docs/tailscale-setup.md`](docs/tailscale-setup.md) — connecting your phone to SillyTavern with Tailscale
- [`CLAUDE.md`](CLAUDE.md) — project overview and conventions
- [`context/project-schema.md`](context/project-schema.md) — roadmap and phases
- [`context/coding-standards.md`](context/coding-standards.md) — architecture and style rules
