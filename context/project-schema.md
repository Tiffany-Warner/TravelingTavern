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

## Blocked on EchoText Thread Storage

EchoText keeps every thread in SillyTavern's settings file, which each open page saves whole, so a second open page (e.g. a desktop tab) can erase messages (confirmed 2026-11-18, see the 1.7 spec's Notes). The fix is the user's own fork of EchoText that stores threads per thread — a separate project, not in this repo.

- **Waits for the fork:** 1.7 (Chat screen with the EchoText thread), 1.7c (Chats list previews from threads), Phase 2 (proactive notifications), Phase 3 (reminders arrive as proactive messages), Phase 4 (voice notes and selfies arrive as proactive messages and play in the thread)
- **Can go ahead:** 1.6b, 1.7b (mic in the main chat for now; the EchoText input later), 1.8, Phase 5, Phase 6 (calls don't depend on EchoText), Phase 7
- Until then, Chat stays as it is from 1.6 (main chat only)

---

## Phase 1 — Foundation & Clean Shell

> Goal: A clean, fast phone experience on top of the real SillyTavern. No companion features yet.

- [x] 1.1 Project setup & app shell (dependencies, theme, navigation, placeholder screens)
- [x] 1.1b Bottom navigation (Chats, Characters, Me) & quiet visual style
- [x] 1.2 Connect screen — server URL, optional login, connection test
- [x] 1.3 WebView host — single retained WebView, file upload, mic/camera permissions, back handling, error states
- [x] 1.4 JS bridge foundation — injected script, typed messages, Kotlin ↔ JS calls
- [x] 1.5 Clean theme — injected CSS that hides SillyTavern clutter inside the app only
- [x] 1.6 Chats & Characters tabs — recent-chats list and portrait grid
- [x] 1.6b Lorebooks tab — browse, create and delete lorebooks; view, add, edit and delete entries
- [ ] 1.7 Chat screen — EchoText thread as the chat, main chat as Story view, no reloads (blocked on EchoText thread storage, see below)
- [ ] 1.7c Chats list previews from EchoText threads (blocked on EchoText thread storage)
- [x] 1.7b Voice input — on-device speech-to-text mic, tolerates pauses, never auto-sends (in the main chat for now; the EchoText thread once 1.7 lands)
- [ ] 1.8 Me tab & Advanced — persona, appearance, server, full SillyTavern UI behind Advanced, open-source licenses

---

## Phase 2 — Proactive Message Notifications

> Goal: Characters reach your phone when the app is closed. One toggle turns it off.
> Depends on desktop milestone M0 (Bridge repo + EchoText-Proactive fork).
>
> **Prerequisite:** EchoText thread storage (the user's EchoText fork) — see "Blocked on EchoText Thread Storage" above. Proactive messages are written into EchoText threads, so they can be erased until threads are stored per thread.

- [ ] UnifiedPush receiver (ntfy as distributor)
- [ ] Notify me toggle (global) + per-character on/off
- [ ] One notification channel per character, avatar as large icon, hide text on lock screen option
- [ ] Tap opens that character's Text tab
- [ ] Quiet hours
- [ ] Inline reply from notification (stretch)

**Notes**

- **Duplicate proactive messages (found 2026-11-18, to verify):** EchoText runs its own proactive scheduler in **every** open SillyTavern page (Tethered mode, `proactiveMessagingEnabled`), so the desktop tab and the app's WebView can each generate a proactive message for the same character. With the EchoText-Proactive plugin, each queued message goes to whichever page polls `/pending` first. Decide which side generates (e.g. only the server plugin, or only one page) before notifications depend on it. Thread storage and the two-page overwrite are in the 1.7 spec's Notes

---

## Phase 3 — Characters Who Live in Real Time

> Goal: Time awareness, time zones, and character-set reminders.
>
> **Prerequisite:** EchoText thread storage (the user's EchoText fork) — see "Blocked on EchoText Thread Storage" above. Reminders arrive as proactive messages in the EchoText thread.

- [ ] Lives in real time toggle per character
- [ ] Time zone toggles: use persona's zone, use character's zone from backstory
- [ ] Detected zone display + manual override
- [ ] Reminders list per character (upcoming/past, delete, snooze)

---

## Phase 4 — Voice Notes & Selfies

> Goal: Some proactive messages arrive as a voice note (Eleven v4) or a selfie (ComfyUI).
>
> **Prerequisite:** EchoText thread storage (the user's EchoText fork) — see "Blocked on EchoText Thread Storage" above. Deferred as a whole: voice notes and selfies are proactive messages, played and shown in the EchoText thread, and their notifications come from Phase 2. The per-character settings (frequency, voice, style) only matter once those messages exist.

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
>
> **No EchoText dependency:** can go ahead before the EchoText fork. If call turns are ever meant to appear in the EchoText thread, that part waits for the fork; decide in the Phase 6 spec.

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
