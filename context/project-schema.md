# TravelingTavern — Project Schema

> Take your SillyTavern companions with you. A clean Android shell around your desktop SillyTavern, with proactive messages, reminders, voice, and selfies.

---

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM + Clean Architecture
- **DI**: Hilt
- **Settings**: DataStore (Preferences) + encrypted credentials
- **Local DB**: Room (app-owned data only)
- **Navigation**: Jetpack Navigation Compose
- **WebView / bridge**: AndroidX WebKit (`addWebMessageListener`)
- **Network / images**: OkHttp, Coil
- **Serialization**: Kotlin Serialization

## How It Fits Together

- **Desktop:** SillyTavern + EchoText + EchoText-Proactive (fork) + TravelingTavern-Bridge (server plugin)
- **Network:** Tailscale between phone and PC
- **Phone (this repo):** native screens + one shared WebView showing SillyTavern + a JS bridge between them

---

## Phase 1 — Foundation & Clean Shell

> Goal: A clean, fast phone experience on top of the real SillyTavern. No companion features yet.

- [x] 1.1 Project setup & app shell (dependencies, theme, navigation, placeholder screens)
- [x] 1.1b Bottom navigation (Chats, Characters, Me) & quiet visual style
- [x] 1.2 Connect screen — server URL, optional login, connection test
- [x] 1.3 WebView host — single retained WebView, file upload, mic/camera permissions, back handling, error states
- [x] 1.4 JS bridge foundation — injected script, typed messages, Kotlin ↔ JS calls
- [ ] 1.5 Clean theme — injected CSS that hides SillyTavern clutter inside the app only
- [ ] 1.6 Chats & Characters tabs — recent-chats list and portrait grid
- [ ] 1.6b Lorebooks tab — browse, create and delete lorebooks; view, add, edit and delete entries
- [ ] 1.7 Chat screen — Text tab (EchoText) and Story tab (main chat), no reloads
- [ ] 1.7b Voice input — on-device speech-to-text mic in both chat tabs, tolerates pauses, never auto-sends
- [ ] 1.8 Me tab & Advanced — persona, appearance, server, full SillyTavern UI behind Advanced, open-source licenses

---

## Phase 2 — Proactive Message Notifications

> Goal: Characters reach your phone when the app is closed. One toggle turns it off.
> Depends on desktop milestone M0 (Bridge repo + EchoText-Proactive fork).

- [ ] UnifiedPush receiver (ntfy as distributor)
- [ ] Notify me toggle (global) + per-character on/off
- [ ] One notification channel per character, avatar as large icon, hide text on lock screen option
- [ ] Tap opens that character's Text tab
- [ ] Quiet hours
- [ ] Inline reply from notification (stretch)

---

## Phase 3 — Characters Who Live in Real Time

> Goal: Time awareness, time zones, and character-set reminders.

- [ ] Lives in real time toggle per character
- [ ] Time zone toggles: use persona's zone, use character's zone from backstory
- [ ] Detected zone display + manual override
- [ ] Reminders list per character (upcoming/past, delete, snooze)

---

## Phase 4 — Voice Notes & Selfies

> Goal: Some proactive messages arrive as a voice note (Eleven v4) or a selfie (ComfyUI).

- [ ] Voice note playback in thread + Play action on notification (MediaSession)
- [ ] Selfie preview in notification (BigPictureStyle)
- [ ] Per-character frequency, voice, and style settings

---

## Phase 5 — Character Creator

> Goal: Guided, step-by-step creator that writes a standard SillyTavern V2 card.

- [ ] Step-by-step creator (Basics → Who they are → Memories → How they talk → Greeting → Look & voice → Journals)
- [ ] Live token estimate per field
- [ ] Draft with AI
- [ ] Edit existing cards without touching unknown fields

---

## Phase 6 — Voice Calls

> Goal: Hands-free calls, under 2.5 s to first audio.

- [ ] Call screen + foreground service (microphone, mediaPlayback)
- [ ] VAD turn-taking, on-device STT, Eleven v4 Turbo playback, barge-in
- [ ] Monthly minutes counter

---

## Phase 7 — Avatar Generation

> Goal: Pick a style, get four avatar options from local ComfyUI.

- [ ] Style chips loaded from Bridge (`GET /styles`)
- [ ] 2×2 results grid, regenerate, vary

---

## Out of Scope (for now)

- Running SillyTavern on the phone
- Using the app while the PC is off
- iOS
- Multiple users / accounts
- Public internet exposure (Tailscale only)

---

## Context Files

| File | Purpose |
|---|---|
| `context/ai-guidelines.md` | How Claude Code should behave |
| `context/current-feature.md` | Active feature being worked on |
| `context/coding-standards.md` | Kotlin/Compose/architecture/WebView rules |
| `context/ui-guidelines.md` | Visual style, navigation, and common UI patterns |
| `context/project-schema.md` | This file — phases and roadmap |
| `context/specs/` | Feature specs, one per feature |
