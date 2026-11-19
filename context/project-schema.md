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

- **Desktop:** SillyTavern + TravelingTavern-Bridge (server plugin, final phase). No EchoText: SillyTavern's main chat is the only conversation
- **Network:** Tailscale between phone and PC
- **Phone (this repo):** native screens + one shared WebView showing SillyTavern + a JS bridge between them

## One Conversation: the Main Chat

**Decision (2026-11-18, user):** SillyTavern's main chat is the only conversation per character. EchoText is no longer used, as the chat or as a Text tab.

- **Why:** SillyTavern's memory tools work on the main chat: Memory Books, keyword-triggered lorebooks (chat, global and persona), vector storage and summarizers. EchoText's threads are invisible to all of them, and EchoText only reads the character's own lorebook, filtered by entry Order. Using it as the chat would make Memory Books almost pointless
- **Proactive messages and reminders** are generated in the main chat by SillyTavern itself, on the desktop. That's the final phase (Phase 8): it is the most advanced feature and the rest of the app comes first
- EchoText and EchoText-Proactive should be uninstalled from the desktop, so their own scheduler can't send messages. The app still hides EchoText's UI in clean mode (0.1.12 fix) in case it's installed
- The earlier EchoText design and its findings (1.7 spec Notes, thread storage) are in Git history before this change

## Built Toward the Final Phase

Proactive messages and reminders come last, but every phase before them keeps to these rules, so Phase 8 adds to the app instead of undoing it. In Phase 8, a SillyTavern page on the desktop (a headless browser driven by TravelingTavern-Bridge) runs the same `bridge.js` as the app and generates the character's message in the main chat.

- **`bridge.js` never requires the app.** It runs in any SillyTavern page: it hooks SillyTavern's events and its commands work whether or not the `TravelingTavern` port exists, and only `post()` does nothing without the port. (Today it returns early without the port; 1.7 removes that.) Nothing in it assumes Android
- **SillyTavern's own IDs.** A chat is identified by the character's avatar file plus SillyTavern's chat file name, the same keys the Bridge will use. No app-side chat IDs
- **No chat copies.** The app never keeps its own copy of messages as a source of truth (Room or otherwise); caching for display is fine, SillyTavern's file always wins. A second writer is what Phase 8 has to avoid
- **Settings live in SillyTavern.** Anything that affects generation (model, preset, prompt) is saved in SillyTavern, never applied only in the app's WebView session, so a message generated on the desktop matches one generated in the app. **One deliberate exception:** the texting / story style (Phase 2) is a TravelingTavern-only feature, stored per chat in the app, so SillyTavern on the desktop never changes
- **Reload the open chat when the app comes back** (resume, and later a notification tap). SillyTavern doesn't sync open pages, so another page may have added messages
- **Lifecycle events reach the page:** the app going to the background and back (`ProcessLifecycleOwner`, not Activity events, which also fire on rotation) and chat switches go to `bridge.js`. In Phase 8 the same state becomes the heartbeat that tells the Bridge which chat the app has open
- **A deep link to a chat** (character + chat) exists before Phase 8, so a notification tap only has to use it
- **Times come from SillyTavern's `send_date`**, never from app-side tracking. The Bridge's scheduler reads the same field
- **Login stays simple:** whatever auth the Connect screen supports must also work for a desktop browser page, since the headless page signs in the same way

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
- [x] 1.7b Voice input — on-device speech-to-text mic in the main chat. **Replaced (2026-11-19):** voice typing comes from the keyboard's own mic; 1.7 removes the in-app mic
- [ ] 1.7 Chat screen — story chat: message cards, tap for SillyTavern's message actions, new input bar; reload on resume, lifecycle events, deep link to a chat
- [ ] 1.8 Me tab & Advanced — persona, appearance, server, full SillyTavern UI behind Advanced, open-source licenses
- [ ] 1.9 Personas — a chip in the chat's input bar shows the active persona; tapping it opens a full-screen persona list to switch, create, edit and delete personas, with SillyTavern's default and lock options

---

## Phase 2 — Texting Style

> Goal: A second style for a chat. Texting looks like texting and the character writes like they're texting; story (1.7) stays the default. A TravelingTavern-only feature: SillyTavern on the desktop never changes. See `context/specs/phase2-texting-style-spec.md`.

- [ ] Style per chat (story by default), switched from the Chat screen's ⋮ menu
- [ ] Texting instruction added in the app's page only (in-memory extension prompt), never saved in SillyTavern
- [ ] Editable texting template (Me tab)

---

## Phase 3 — Character Creator

> Goal: Guided, step-by-step creator that writes a standard SillyTavern V2 card.

- [ ] Step-by-step creator (Basics → Who they are → Memories → How they talk → Greeting → Look & voice → Journals)
- [ ] Live token estimate per field
- [ ] Draft with AI
- [ ] Edit existing cards without touching unknown fields

---

## Phase 4 — Voice Notes & Selfies in Replies

> Goal: A character's reply can come as a voice note (Eleven v4) or a selfie (ComfyUI), shown and played in the main chat.
>
> Replies only: you're in the chat when they arrive. Voice notes and selfies that arrive on their own, with notifications, are Phase 8.

- [ ] Voice note playback in the chat (MediaSession)
- [ ] Selfies shown in the chat
- [ ] Per-character voice and style settings

---

## Phase 5 — Characters Who Live in Real Time

> Goal: Time awareness and time zones. The current time reaches the prompt through SillyTavern (a macro or an injected prompt), not through the app.

- [ ] Lives in real time toggle per character
- [ ] Time zone toggles: use persona's zone, use character's zone from backstory
- [ ] Detected zone display + manual override

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

## Phase 8 — Proactive Messages & Reminders (final phase)

> Goal: Characters reach you when the app is closed: spontaneous messages, and reminders you asked for, delivered on time with the character's own words in the notification. One toggle turns it off.
>
> Depends on desktop milestone M0: the TravelingTavern-Bridge server plugin with its scheduler, headless SillyTavern page and ntfy push.

**Use cases**

1. **Reminder:** you ask a character to remind you at 2:00 PM. At 2:00 PM their in-character reminder is in the main chat and a notification shows it. No tap needed
2. **Spontaneous message:** a morning greeting, a check-in after a quiet day, a late-night text

**Desktop (TravelingTavern-Bridge repo)**

- Server plugin: 60 s scheduler, trigger logic, reminder store (a file, survives restarts), app heartbeat, ntfy push
- Generation in a SillyTavern page on the PC (headless Chromium via Playwright) running `bridge.js`: open the chat, add a one-time hidden instruction (`/inject … ephemeral=true`), then `/trigger`. SillyTavern builds the full prompt (lorebooks, Memory Books) and saves only the character's reply
- Never generates into a chat the app has open in the foreground: the app generates it there instead, using the same command
- Reminder delivery: generate a minute or two early, push at the due time; if generation fails, push the plain reminder text on time
- In the headless page `post()` has no port: Playwright supplies a `TravelingTavern` stand-in, or the Bridge reads `__ttBridge.appState` and command results directly. Decide in the Phase 8 spec
- When-to-text logic ported from EchoText's `lib/proactive-messaging.js` (MIT, keep its copyright notice): trigger types and templates, daily jitter, day patterns, cooldowns

**App**

- [ ] UnifiedPush receiver (ntfy as distributor)
- [ ] Notify me toggle (global) + per-character on/off
- [ ] One notification channel per character, avatar as large icon, hide text on lock screen option
- [ ] Tap opens that character's chat, reloaded
- [ ] Heartbeat to the Bridge (from the lifecycle events built in 1.7), including each chat's texting / story style, so the headless page adds the texting instruction for texting chats
- [ ] Reminder chip under the message that set it; reminders list per character (upcoming / past, delete, snooze)
- [ ] Backup alarm on the phone for a confirmed reminder, cancelled when it's delivered (covers the PC being asleep)
- [ ] Proactive voice notes and selfies, with Play action (MediaSession) and selfie preview (BigPictureStyle) on the notification
- [ ] Quiet hours
- [ ] Inline reply from notification (stretch)

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
