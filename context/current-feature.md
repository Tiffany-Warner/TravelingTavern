# Current Feature

Phase 1.6c — Chats Tab: Hide and Delete

## Status

Complete (`feature/chats-hide-delete`). `./gradlew build` green. `0.1.17`.

- Done: source investigation (spec Notes): `deleteCharacterChatByName` imported from `/script.js`, success = `CHAT_DELETED`; open chat deleted → `replaceCurrentChat()` (as ST's Manage chats); only-chat delete needs nothing extra
- Done: implementation — `bridge.js` `deleteChat` (+ `scriptModule()` helper), `HiddenChatsStore` (settings DataStore), `GetRecentChatsUseCase` → `ChatList` (visible + hidden count), `CharacterRepository.deleteChat` / `generatingChat`, `ChatsViewModel` options state + snackbar events, `ChatOptionsSheet`, ⋮ menu; unit tests pass, Compose UI tests compile; `./gradlew build` green; `0.1.17`
- User decision: deleting a character's only chat → SillyTavern's fresh greeting chat stays (hide it if unwanted)
- User request (2026-11-19): deleting a whole chat (Delete here, Restart chat in 1.7) confirms in an alert dialog (`ConfirmDeleteChatDialog`, red icon + button) instead of a sheet, so it can't be mixed up with the Start new chat sheet; ui-guidelines exception added
- User request: ⋮ Restart chat renamed **Delete and restart** (title and button too); both delete-chat dialogs open with a bold red "This also deletes the chat on your SillyTavern."
- Verified on the emulator (API 35) vs SillyTavern 1.19.0 over Tailscale, throwaway Bob chats: long press sheet; Hide → row gone + snackbar + ⋮; Undo; hidden survives app restart; ⋮ Show hidden chats (⋮ disappears); Delete dialog, Cancel keeps; Delete removes the row and the file on the desktop; deleting the chat open in the page (page moved to the next chat, `chatChanged` + `chatResult ok`, Bob then opened fine); Delete and restart dialog (cancelled). Two throwaways deleted; one left (`…21h29m28s056ms`)
- Not verified: Tailscale off → "Couldn't delete" snackbar; Delete disabled while generating (unit + UI tests only); deleting a character's only chat through the app (user confirmed SillyTavern's behavior); haptic
- App icon committed separately (`feat: app icon`)
- Next: commit (ask) → merge into `dev` → push

## Goals

Per `context/specs/phase1-6c-chats-actions-spec.md`:

- **Investigate:** how ST deletes a chat that isn't open (page function, server call), what happens when the open chat or a character's only chat is deleted, whether an event fires
- **Long press** a Chats row → sheet (avatar, name, time; Hide, Delete); tap still opens the chat; Delete disabled while that chat generates
- **Delete:** `ConfirmDeleteSheet` → new `__ttBridge` command (avatar + chat file), selectors in `st-selectors.js`; re-read `recentChats`; failure snackbar; drop from hidden list
- **Hide:** app-only (DataStore string set of `avatar/chatFile`), snackbar with Undo; stale entries dropped on read
- **⋮ menu:** Show hidden chats, only while something is hidden
- Tests per spec; version bump

## References

- @context/specs/phase1-6c-chats-actions-spec.md
- @context/specs/phase1-6-home-character-list-spec.md
- @context/specs/phase1-7-chat-screen-spec.md (Restart confirmation, `doNewChat` import pattern)
- @context/project-schema.md (Built Toward the Final Phase)

## History

### 2026-11-19 — Phase 1.6c Chats Hide and Delete — `0.1.17`
- Long press a chat → sheet: Hide (app only, DataStore `avatar/chatFile` keys, snackbar Undo; ⋮ Show hidden chats while any is hidden) and Delete (SillyTavern's `deleteCharacterChatByName`, imported from `/script.js`; success = `CHAT_DELETED`, answered with `chatResult`)
- Deleting the chat open in the page calls `replaceCurrentChat()`, as SillyTavern's Manage chats does (otherwise the page keeps the deleted messages and a later save writes them into the next chat)
- User changes: deleting a whole chat confirms in an alert dialog with a bold red "This also deletes the chat on your SillyTavern." (Delete here, and 1.7's Restart, renamed **Delete and restart**); no "memories stay" line (Memory Books' lorebook is bound in the chat file)
- Hidden entries for missing chats are ignored, not pruned (the app only sees the 100 most recent chats). Gotcha: file-backed DataStore can't overwrite its file on a Windows JVM, so the store test uses an in-memory DataStore
- Verified on an emulator (API 35); not verified: Tailscale off, Delete disabled while generating, deleting a character's only chat

### 2026-11-19 — App icon
- Logo (`docs/brand/travelingtavern_logo.svg`) as the adaptive launcher icon: purple background, TT + door foreground inside the safe zone, monochrome layer for themed icons; legacy webp mipmaps removed (min SDK 31)

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