# Current Feature

Phase 1.7 — Chat Screen (Story Chat)

## Status

Complete (`feature/story-chat`, awaiting commit). `./gradlew build` green. `0.1.16`.

- Done: investigation (spec Notes); mic removed (files deleted with approval, all refs gone); `bridge.js` / `st-selectors.js` / `clean-theme.css`; bridge messages, `ChatSessionRepository`, `CharacterRepository` chat file + stale/return handling, `AppForegroundTracker` (`lifecycle-process`), `PageThemeStore`; `ChatViewModel`, `ChatScreen`, `ChatInputBar`, `MessageActionsSheet`; `chatFile` route + deep link + `singleTop`; unit tests pass, Compose UI tests compile
- Verified on Medium_Phone_API_35 (now 4 GB RAM; at 2 GB the whole emulator stalled) against SillyTavern 1.19.0 over Tailscale, Bob's chat: cards, background, names, sheet, Regenerate (new swipe), swipes never generate, Send, "…" card, Stop shown, Write for me, Copy, Edit, Hide / Show, Delete + confirmation, ⋮ menu + Restart confirmation (cancelled), return from background (chat re-read, no page reload). Test messages deleted afterwards; one extra swipe left on Bob's last reply
- Fixed during verification: names / swipe counter unreadable on the scrim (light theme); input field dropping letters (text now Compose state in `ChatViewModel`); Chats preview one message behind after a reply (re-asked when Chat closes); inline editor dark on light cards; tapped message scrolled above the sheet (`revealMessage` / `endReveal`). Composer redesigned (user): field and buttons in one container, icons bottom-right
- Also verified (user OK'd the server writes): Continue (on the extra swipe; original reply untouched), Branch (app follows the branch file), Restart in the branch (branch deleted, fresh chat), Start new chat, Stop while thinking (empty reply kept, SillyTavern's own behavior), untouched greeting's arrow generates nothing, deep link while running and from a cold start, Back to Chats, scroll-to-newest. Two throwaway Bob chats left for the user to delete (`…19h40m37s779ms`, `…19h41m54s935ms`)
- Fixed in this round: a running-app deep link recreated the Activity (`FLAG_ACTIVITY_NEW_TASK` → `handleDeepLink` restarts the task) and the old Activity's `onRelease` then pulled the WebView out of the new one (blank page) — link now handled without task flags, `onRelease` only removes the WebView from its own Activity; system Back from a linked chat closed the app — Chat's `BackHandler` now uses `leaveChat`; scroll-to-newest sat under the messages (z-index 10 < chat's 30), so a tap opened a message's sheet — z-index 1000
- User requests (2026-11-19): Chats list shows one row per chat instead of per character (1.6 spec updated; rows keyed by character + chat file; verified: two Oliver rows, three Bob rows, each opens its own chat); hidden-from-AI messages keep a solid card (was 60% opacity, which made Seraphina's image messages see-through) — text in the secondary color, label unchanged
- Reasoning bar ("Thought for …", user requests): message-field colors (`--tt-surface-container-highest` added to the page theme, secondary text, 12dp); arrow, actions and reasoning text hidden, CSS only, and the block ignores taps so a tap on the bar opens the message's sheet (the tap handler skips `details`). Verified in light and dark; reasoning stays in ST (Advanced). Dark theme verified on the emulator (switched live, no page reload)
- Background picture stays still while the page height changes (user request): bridge computes `cover` at the tallest page height per width from the image's natural size and passes `--tt-bg-size` / `--tt-bg-y`; #bg1 keeps the page's size (a taller #bg1 made the document scrollable by code). Verified: page 730 → 658 px, picture unchanged, no document scroll
- Not yet verified: on-screen keyboard (the emulator types with the PC keyboard) — best on the Pixel 8
- Next: commit (ask) → merge into `dev` → push

## Goals

Per `context/specs/phase1-7-chat-screen-spec.md`:

- **Investigate:** confirm every **(verify)** vs SillyTavern 1.19.0 (source + live page), record in spec Notes
- **Cleanup:** drop `bridge.js` early return, `setMode` / `tt-mode-*`, all 1.7b mic files/bindings/tests (delete only after user confirms)
- **Look:** message cards, ST background under scrim, theme colors via `PageThemeStore`, ST input area hidden, "…" card, scroll to newest
- **Swipes** never generate in clean mode; **actions sheet** (Regenerate, Continue, Copy, Edit, Branch, Hide/Show, Delete) with `send_date` stale check and `generationState`
- **⋮ menu:** Start new chat, Restart chat
- **Native input bar:** field, persona chip slot, Write for me, Send/Stop; WebView above it
- **Groundwork:** `ProcessLifecycleOwner` + `setAppState`, fresh chats on return, state re-sent after reload, `chatFile` route + chat following (`SavedStateHandle`), `singleTop` deep link
- Tests per spec; version bump

## References

- @context/specs/phase1-7-chat-screen-spec.md
- @context/specs/phase1-7b-voice-input-spec.md (what cleanup removes)
- @context/specs/phase1-9-personas-spec.md (chip slot)
- @context/project-schema.md (Built Toward the Final Phase)

## History

<!-- Newest first. Verified against SillyTavern 1.19.0 over Tailscale on the emulator and Pixel 8 unless noted. -->

### 2026-11-19 — Phase 1.7 Chat Screen (Story Chat) — `0.1.16`
- Story chat: message cards over ST's background, tap → actions sheet (Regenerate, Continue, Copy, Edit, Branch, Hide/Show, Delete), ⋮ Start new / Restart chat, native input bar (Write for me, Send/Stop), "…" card, scroll to newest; swipes never generate; 1.7b mic removed
- Groundwork: `ProcessLifecycleOwner` → `setAppState`, chat re-read on return, `chatFile` route + chat following, `singleTop` deep link (handled without task flags: `FLAG_ACTIVITY_NEW_TASK` recreated the Activity and blanked the WebView)
- User changes: Chats list one row per chat; composer icons inside the field; reasoning bar recolored, can't open (CSS only); background picture stays still as the page height changes; hidden messages stay solid
- Gotchas: input text must be Compose state (StateFlow round trip dropped letters); ST stacks `#chat` at z-index 30; tap handler skips `details`
- Verified on an emulator (API 35); on-screen keyboard and Pixel 8 not verified

### 2026-11-19 — Docs: 1.7 story chat spec; texting style → Phase 2
- 1.7 spec: story chat design (cards, tap → actions sheet, ⋮ menu, native input bar, `PageThemeStore`), cleanup list, lifecycle/reload/deep-link rules. ST input area hidden; Regenerate = new swipe on latest message; swipes never generate; ⋮ disabled while generating
- Texting style: own spec, Phase 2, app-only, per chat. Later phases renumbered (final = Phase 8)
- New 1.9 personas spec (chip, full-screen list, editor; ST features only; picking in a chat locks it)
- 1.7b in-app mic replaced by keyboard voice typing (`RECORD_AUDIO` stays)
- No code, no bump

### 2026-11-18 — Docs: main chat only; proactive messages → final phase
- ST's main chat is the only conversation (EchoText dropped: invisible to Memory Books, lorebooks, vectors)
- Proactive messages/reminders generated on desktop by ST in a headless page via TravelingTavern-Bridge
- No code, no bump

### 2026-11-18 — Fix: new avatar not showing — `0.1.15`
- Cause: Coil caches by URL; ST list only read at load. Avatar URLs carry `tt_v=<date_added>`; `requestCharacters(fromServer)` calls `getCharacters()`; pull to refresh on Characters

### 2026-11-18 — Fix: stuck on "Can't reach" after reinstall — `0.1.14`
- Cause: Auto Backup restored stale server address. Settings DataStore excluded from backup (fresh install → Connect)
- Unreachable banner: Retry + Server settings; `WebViewHolder` reloads on settings save while in error

### 2026-11-18 — Phase 1.7b Voice Input — `0.1.13` (since removed in 1.7)
- On-device `SpeechRecognizer` mic in ST's input row; 4 s pause tolerance
- First connected Compose run uninstalled the app from the Pixel (now in release rules)

### 2026-11-18 — Fix: hide EchoText UI in clean mode — `0.1.12`
- `body.tt-clean ~ #et-portal > * { display: none !important }` (portal is a `<body>` sibling)

### 2026-11-18 — Fix: crash after reinstall with restored backup — `0.1.11`
- Cause: Tink keyset restored but its Keystore key wasn't. Keyset excluded from backup; `loadKeysetOrReset()` recreates it on failure
- Not verified on Pixel

### 2026-11-17 — Phase 1.6b Lorebooks Tab — `0.1.10`
- Lorebooks tab (search, filter chips, grid, create/delete), detail (global switch, entries) and entry editor (title, keys, content, enabled; other fields preserved)
- Gotcha: ST modules must be imported by absolute URL from injected scripts
- Not verified: persona links (none on server), manual pull to refresh

### 2026-11-17 — Fix: 3 s cap on opening a chat — `0.1.9`
- `openChat()` reveals chat after 3 s without `chatOpened` (WARN log)

### 2026-11-17 — Phase 1.6 Chats & Characters Tabs — `0.1.8`
- `recentChats` via `POST /api/chats/recent`; `chatOpened` after `selectCharacter`; Coil uses shared OkHttp
- WebView hosted in app shell, always attached; covered by opaque surface on non-Chat screens (unfocusable, hidden from accessibility)
- Chats list, Characters grid (search, add sheet "Coming soon"), Chat screen with cover until `chatOpened`
- No reload across 13 list↔Chat round trips

### 2026-11-17 — Docs: Compose UI tests going forward
- UI tests for new/changed native screens; run only before release

### 2026-11-17 — Phase 1.5 Clean Theme — `0.1.7`
- `clean-theme.css` under `body.tt-clean`: hides toolbar, drawers, TopInfoBar; full-height `#sheld`; ≥16px text, ≥48dp buttons
- `setClean`, `installStyle`; `selectCharacter` without editor
- Gotcha: forcing `display` on input buttons showed hidden `#mes_stop`

### 2026-11-17 — Fix: WebView load timeout + status-bar strip — `0.1.6`
- `LoadWatchdog` 15 s (reset by `onPageCommitVisible`); Tailscale-off error now ~18 s (was ~2 min)
- Strip: transparent WebView background + `clipToBounds()`

### 2026-11-17 — Phase 1.4 JS Bridge Foundation — `0.1.5`
- `bridge.js` + `st-selectors.js`; `{ v: 1, type }` messages; frozen `window.__ttBridge`; double-run guards
- Listener for saved origin only, main frame; scripts injected on every `onPageFinished`; parser never throws
- `PageError.WebViewOutdated` when `WEB_MESSAGE_LISTENER` unsupported

### 2026-11-17 — Android CI
- GitHub Actions: build, unit tests, lint on PRs/pushes to `dev`/`main`. `gradlew` made executable. `main` protected; `dev` created

### 2026-11-17 — Min SDK 31 — `0.1.4`

### 2026-11-17 — Phase 1.3 WebView Host — `0.1.3`
- `WebViewHolder` singleton on `MutableContextWrapper`; basic auth for saved host only; other hosts → browser; Aegis file chooser/permissions; `adjustResize`
- Not verified: basic auth, camera

### 2026-11-16 — Phase 1.2 Connect Screen — `0.1.2`
- URL normalization; DataStore + Tink/Keystore credentials; shared OkHttp with host-scoped basic auth; test = `GET /` checking `<title>SillyTavern</title>`; Save only after a passing test
- Auth paths covered by unit tests only (server has auth off)

### 2026-11-16 — Phase 1.1b Bottom Navigation & Style — `0.1.1`
- Tabs Chats/Characters/Me + full-screen routes; amber accent, Inter font, dynamic color off by default

### 2026-11-16 — Phase 1.1 Project Setup — `0.1.0`
- Toolchain (AGP 9.0.1, built-in Kotlin): SDK 36 pins `core-ktx 1.17.0`, `lifecycle 2.9.4`, `activity-compose 1.11.0`, Compose BOM `2025.09.01`; Kotlin plugins `2.2.10`; Hilt `2.60.1`; KSP `2.3.4`; `material-icons-core` explicit; `JAVA_HOME` → Android Studio JBR