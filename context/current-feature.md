# Current Feature

Docs: the main chat is the only conversation; proactive messages and reminders become the final phase

## Status

Completed

## Goals

Update the docs for two decisions (2026-11-18, user):

- **The main chat is the only conversation.** EchoText is dropped, as the chat and as a Text tab: SillyTavern's memory tools (Memory Books, keyword lorebooks, vector storage) only work on the main chat
- **Proactive messages and reminders are the final phase.** They need a desktop-side generator and are the most advanced feature, so the rest of the app comes first, built so the final phase adds to it instead of undoing it

### Deliverables

- `project-schema.md`: How It Fits Together without EchoText; new sections One Conversation and Built Toward the Final Phase (the rules every phase keeps); "Blocked on EchoText Thread Storage" removed; 1.7c removed; phases reordered (2 creator, 3 voice notes and selfies in replies, 4 real-time awareness, 5 calls, 6 avatar generation, 7 proactive messages and reminders, final)
- 1.7 spec rewritten for the main chat: texting / story display styles, reload on return, lifecycle events, opening a specific chat with a deep link. The EchoText findings stay in Git history
- 1.7b spec, `README.md`, `CLAUDE.md`, `ai-guidelines.md`, `coding-standards.md` (three boundary rules), `ui-guidelines.md`, and the 1.6, 1.6b and 1.8 specs: EchoText and phase references updated
- Docs only, no version bump

## References

- @context/project-schema.md
- @context/specs/phase1-7-chat-screen-spec.md

## History

<!-- Completed work is added here, newest first -->

### 2026-11-18 — Docs: main chat only; proactive messages and reminders as the final phase (branch `docs/main-chat-final-phase`)

- **Decisions (user):** SillyTavern's main chat is the only conversation (EchoText dropped: its threads are invisible to Memory Books, keyword lorebooks and vector storage); proactive messages and reminders move to the final phase (Phase 7) and are generated on the desktop by SillyTavern itself, in a headless page driven by TravelingTavern-Bridge
- `project-schema.md`: One Conversation and Built Toward the Final Phase sections; roadmap reordered; Phase 7 holds the use cases (on-time reminders with the character's words in the notification; spontaneous messages), the desktop design and the app checklist
- 1.7 spec rewritten (main chat, display styles, reload on return, lifecycle events, `chatFile` route + deep link); 1.7c dropped
- Other docs: EchoText references removed or marked historical; phase numbers updated; `coding-standards.md` gains rules for `bridge.js` running in any page, SillyTavern's own chat keys and times, and generation settings living in SillyTavern
- No code changed, so no version bump

### 2026-11-18 — Fix: a character's new avatar doesn't show in the app (branch `fix/avatar-updates`)

- **Cause:** avatar addresses were built from the file name only, and Coil 3 caches by address (ignoring the server's `max-age=0`); and the page's character list was read from the server only when SillyTavern loaded
- `bridge.js`: both avatar addresses carry `tt_v=<date_added>` (the card file's `ctimeMs`); `requestCharacters(fromServer)` calls SillyTavern's `getCharacters()` first. New names `getCharacters` and `character.changedAt` in `st-selectors.js`. `BridgeClient.requestCharacters(fromServer)`; `CharacterRepositoryImpl.refresh()` asks the server
- Characters tab: pull to refresh (`CharactersViewModel.refresh` / `isRefreshing`, `PullToRefreshBox` around the grid)
- Unit tests: `CharactersViewModelTest` +1, `CharacterRepositoryImplTest` +1; `./gradlew build` green
- Verified on Medium_Phone_API_35 against SillyTavern 1.19.0 over Tailscale (DevTools): pull to refresh on Characters → one `/api/characters/all` request, SillyTavern's list objects replaced, the open chat untouched; the `characters` message carries `tt_v` on both addresses; the server serves both, including a name with a space encoded as `+`; the grid shows every picture
- Verified on the Pixel 8 by the user: an avatar changed on the desktop showed in the app after a pull to refresh
- Version `0.1.15` / `versionCode 16`

### 2026-11-18 — Fix: stuck on "Can't reach your server" after a reinstall (branch `fix/connect-after-restore`)

- **Cause:** Android Auto Backup restored the settings DataStore on install with a stale server address (the server's previous Tailscale IP), so Connect was skipped; and the "can't reach" banner offered only Retry, so the address couldn't be changed from the app
- `data_extraction_rules.xml` (cloud backup and device transfer) and `backup_rules.xml` exclude `datastore/settings.preferences_pb` as well as the keyset (user decision): a fresh install always starts at Connect
- `ConnectionBanner`: Unreachable shows Retry **and** Server settings; AuthFailed keeps Server settings, HttpError keeps Retry
- `ServerConfigRepositoryImpl.saves` fires on every save; `WebViewHolder` reloads on it while the page is in an error state (reads the stored address, so a changed address isn't retried at the old one first, and `load()` records it so it isn't loaded twice)
- Compose UI test: `ChatsContentTest.unreachableBannerRetriesOrOpensServerSettings`; `./gradlew build` green
- Verified on Medium_Phone_API_35 against SillyTavern 1.19.0 over Tailscale with the local backup transport: 0.1.13 backed up with a saved server → uninstall → 0.1.14 installed over that backup → no settings restored, Connect on six cold starts in a row; offline start → banner with Retry and Server settings on one line → Server settings opens Connect; stale address planted → banner → new address saved → list loads without Retry; offline start, network back, **same** address saved → list loads without Retry (before the reload-on-save change it stayed on the banner for 2 min)
- Verified on the Pixel 8 (0.1.14 installed as an update, stale address still saved): banner with Server settings → new address → Test "Connected to SillyTavern" → Save; the user needed one Retry afterwards (that build had no reload-on-save; a changed address did reload by itself on the emulator, so the Pixel's first load may simply have failed — not reproduced)
- Version `0.1.14` / `versionCode 15`

### 2026-11-18 — Phase 1.7b Voice Input (branch `feature/voice-input`)

- Speech: `OnDeviceSpeechInput` (`SpeechRecognizer.createOnDeviceSpeechRecognizer`, main thread, partial results, silence-length extras at 4 s, prefer offline, one recognizer per pass, released on cancel; `triggerModelDownload` when the speech pack is missing); `AndroidMicrophonePermission`. Never a cloud recognizer
- Domain: `SpeechEvent`, `SpeechError`, `VoiceSessionEvent`, `ChatInputTarget`; `SpeechInputRepository`, `ChatInputRepository`, `MicrophonePermission`; `IsVoiceInputAvailableUseCase`, `ListenForSpeechUseCase` (passes back to back, committed text + current partial, watchdog ends the session after `PAUSE_TOLERANCE_MS = 4000` without a new word — counted from when the recognizer is listening, 10 s start cap; `NoMatch` on a restart isn't an error; 250 ms back-off for passes that never start)
- Bridge: `#tt-mic` before `#send_but` (row's own icon classes, 48 × 48, added only when on-device recognition is available, re-added after every page load), `micPressed { target: "story" }` (`"text"` reserved for 1.7), `setVoiceInputAvailable` / `beginVoiceInput` / `updateVoiceInput` / `endVoiceInput` (value + bubbling `input` event, as SillyTavern does itself; cursor at the end); `BridgeMessage.MicPressed`; `ChatInputRepositoryImpl`. `clean-theme.css`: extension mic `#microphone_button` hidden in clean mode, `#tt-mic` hidden outside it, `--SmartThemeQuoteColor` while listening. New names in `st-selectors.js`
- Chat: `ChatViewModel` voice state (`Idle` / `Listening` / `Unavailable`), tap to start / stop, `RECORD_AUDIO` request, stop on leaving Chat and on `ON_STOP`, page mic always reset; snackbars for `PermissionDenied` (+ Settings → app details), `LanguagePackMissing`, `NoMatch`, and a generic failure. Theme: `inversePrimary` / `inverseSurface` / `inverseOnSurface` set (snackbar action amber, not Material purple)
- Unit tests: `ListenForSpeechUseCaseTest` (7: 2 s pause joins, 5 s pause ends, `NoMatch` on restart, nothing heard, other errors, back-off, slow start), `ChatViewModelTest` +7, `BridgeMessageParserTest` +2, `BridgeScriptTest` +1 (quotes, backslashes, newlines, emoji, U+2028/2029); `./gradlew build` green (150 unit tests). Compose UI tests: `ChatContentTest` (4); the user's connected run (Pixel 8 and emulator) passed 20 of 22 — the 2 failures were `LorebooksContentTest` (1.6b) matching both the Global chip and a card's Global status line, fixed in a separate commit; afterwards 22 / 22 on Medium_Phone_API_35. The run uninstalled the app from the Pixel (now noted in `ai-guidelines.md` → Releasing to main)
- Verified on Medium_Phone_API_35 against SillyTavern 1.19.0 over Tailscale: input-row selectors and the `input` event (box grows and shrinks, nothing sent); mic before send at 48 × 48, extension mic hidden; permission prompt → Don't allow → snackbar with an amber Settings action → app details page; second refusal goes straight to the snackbar; missing speech pack → listening, then idle, and the system's own "Download English (US) update" dialog (declined); one start-up ANR on the first launch after install (main thread in Compose's first composition during class verification on a memory-starved emulator; not reproduced on later starts or on the Pixel)
- Verified on the Pixel 8: mic present, extension mic hidden, permission granted ("While using the app"), silent session ends 4 s after the recognizer is listening; **user test: dictation works, pauses don't end it, "a lot better than the browser version"**; recognizer log: passes of 16 s and 15 s through pauses with a ~0.75 s restart gap; spoken punctuation ("period", "exclamation mark") comes out as words
- Found and fixed on the Pixel: the pause tolerance ran from the tap, so a 2.4 s cold start left ~1.6 s to start talking
- Advanced doesn't exist until 1.8; checked instead on the emulator by turning clean mode off through the bridge: `#tt-mic` hidden and the extension's mic back, and the reverse when it's turned on again
- Not verified: airplane mode with Tailscale off (on-device proof)
- Version `0.1.13` / `versionCode 14`

### 2026-11-18 — Fix: hide EchoText's UI in the app until 1.7 (branch `fix/hide-echotext-in-app`)

- **Cause:** with EchoText installed, its window — full screen on a phone, top z-index, reopened after every page load (`autoOpenOnReload` + `panelWasOpen`) — covered SillyTavern's main chat in the Chat screen: the visible list was EchoText's and a tap on the input landed in EchoText's input. Its floating button also floats over the chat
- `clean-theme.css`: `body.tt-clean ~ #et-portal > * { display: none !important }` — EchoText's portal is a sibling of `<body>` and carries an inline `display: block !important`, so its children are hidden. EchoText keeps running; its settings are untouched. Selector listed in the stylesheet header and `st-selectors.js`
- 1.7b spec: its `#et-fab` line now points to this fix
- `./gradlew build` green (no unit-testable code changed)
- Verified on Medium_Phone_API_35 against SillyTavern 1.19.0 with EchoText 1.2.6 over Tailscale (DevTools + screenshot): clean on → EchoText window and button `display: none`, the element at SillyTavern's input and send button is `#send_textarea` / `#send_but`; `setClean(false)` → EchoText's window back over the chat (Advanced and desktop unchanged); `setClean(true)` → hidden again; Bob's chat in the app shows the main chat and its input row with no EchoText UI
- Verified on the Pixel 8 (0.1.12, same server): same page check — clean on → EchoText window and button hidden, `#send_textarea` / `#send_but` on top; clean off → EchoText's window back; clean on → hidden again; Bob's chat in the app shows the main chat and its input row with no EchoText UI
- Version `0.1.12` / `versionCode 13`

### 2026-11-18 — Fix: crash on start after reinstalling with a restored backup (branch `fix/keystore-restore-crash`)

- **Cause:** Android Auto Backup restored the Tink keyset (`tt_credentials_keyset_prefs.xml`) on reinstall, but the Android Keystore master key that wraps it was deleted with the old install (Keystore keys are never backed up). `AndroidKeysetManager.build()` threw `InvalidKeyException` while Hilt created the first ViewModel, so the app crashed on every launch until its storage was cleared. Present since 1.2; found on the Pixel 8 after reinstalling 0.1.10
- `data_extraction_rules.xml` (cloud backup and device transfer) and `backup_rules.xml` exclude the keyset file; the rest (server address) is still backed up. Restored encrypted credentials read as empty
- `loadKeysetOrReset()` (`data/settings/KeysetRecovery.kt`): if the keyset can't be loaded (security, storage or Keystore error), `SecurityModule` logs a warning, deletes the keyset prefs and creates a new keyset; other errors and a second failure are thrown
- Unit tests: `KeysetRecoveryTest` (4); `./gradlew build` green
- Verified on Medium_Phone_API_35 with the local backup transport: 0.1.10 backup → uninstall → reinstall crashed with the Pixel's exact error (reproduced); 0.1.11 installed over that same backup → keyset not restored, app starts; keyset file kept with its Keystore key removed (`pm clear` + file put back) → warning logged, new keyset, Connect screen usable, second launch clean; 0.1.11 backup → uninstall → reinstall → no keyset restored, app starts
- Not verified: the Pixel 8 (the restore test would wipe its saved setup; nothing device-specific changed); a restored server address with a server that needs a login (this server has none)
- Version `0.1.11` / `versionCode 12`

### 2026-11-17 — Phase 1.6b Lorebooks Tab (branch `feature/lorebooks-tab`)

- Spec: decisions written in (no "Chat" status, Unused = not global / character / persona, chat links wait for "Lore in this chat", one tap target per card, Character chip hidden when unused, tonal empty-state button, delete sheet names attached characters and personas with the "keeps a link" note); every **(verify)** item checked against SillyTavern 1.19.0 and recorded in the spec's Notes. `ui-guidelines.md`: four tabs, item grids, filter chips, Screen Map row
- Bridge: `lorebooks` (name, entry count, global flag, attached character IDs and persona names) after `ready`, with the other lists, and on `WORLDINFO_UPDATED` / `WORLDINFO_SETTINGS_UPDATED`; `requestLorebooks(fromServer)` re-reads the list and drops cached copies (pull to refresh); `requestLorebook`, `createLorebook`, `deleteLorebook` (charLore cleanup + `deleteWorldInfo`, like SillyTavern's Delete button), `setLorebookGlobal` (through SillyTavern's `#world_info` control), `saveLorebookEntry` (title, keys, content, enabled only), `deleteLorebookEntry`, `countTokens` — each answers with a request ID. SillyTavern's `world-info.js` / `utils.js` imported by absolute URL. All names in `st-selectors.js`
- Data / domain: `BridgeMessage.Lorebooks` / `LorebookEntries` / `LorebookResult` / `TokenCount`; `LorebookRepository` + `LorebookRepositoryImpl` (request-ID matching, 10 s action timeout, 15 s refresh, `entriesChanged`); `Lorebook`, `LorebookEntry`, `LorebookCard` + `LorebookStatus`, `LorebookFilter`, `isSameLorebookName` (case and accents ignored); use cases `GetLorebooks`, `FilterLorebooks`, `CreateLorebook` (name check), `GetLorebookEntries`, `SetLorebookActive`, `SaveLorebookEntry`, `DeleteLorebook`, `DeleteLorebookEntry`
- UI: Lorebooks tab (book icon) with search (lorebook and character names), chips All / Global / Character ▾ / Unused, character sheet, 2-column item grid (neutral 4:3 cover, name, entry count, status line), pull to refresh, "No lorebooks match" + Clear filters, FAB → create sheet (Create disabled while empty or taken), empty state; full-screen lorebook detail (global switch, "Attached to …", entries with keyword pills and preview, FAB, overflow → delete sheet) and entry editor (title, keywords, content with token count, enabled, Save at the bottom above the keyboard, overflow → delete). Shared `AppBarSearchField` (moved from Characters) and `ConfirmDeleteSheet`
- Unit tests: `FilterLorebooksUseCaseTest` (8), `GetLorebooksUseCaseTest` (4), `CreateLorebookUseCaseTest` (5), `LorebookRepositoryImplTest` (6), `LorebooksViewModelTest` (7), `LorebookDetailViewModelTest` (6), `LorebookEntryViewModelTest` (6), `BridgeMessageParserTest` +4; `./gradlew build` green (all unit tests). Compose UI tests: `LorebooksContentTest` (6) — compiled; run before release per the testing rule
- Verified on Medium_Phone_API_35 against SillyTavern 1.19.0 over Tailscale: bridge functions on the live page (throwaway lorebooks, deleted afterwards), including fields the app doesn't edit (order, depth, probability, position) surviving an app save; through the app's UI: four tabs, grid with Seraphina's link and "Not active", search by character name, Unused / Global / Clear filters, character sheet and ✕, duplicate name refused, create "TT Test" (opened itself), new entry with token count, edit and switch off ("Off"), global on (Global chip lists it), delete entry, delete lorebook, Eldoria's delete sheet wording (cancelled); the user's lorebooks unchanged at the end (entry counts and global list checked on the server)
- Found and fixed during testing (see Notes): module import from injected scripts; create answering with a lorebook made on the desktop meanwhile — the UI test added one test entry to a real lorebook, removed right away
- Verified on the Pixel 8 by the user: the Lorebooks tab worked well (after clearing the app's storage to get past the crash fixed in 0.1.11)
- Not verified: pull to refresh by hand (covered by the ViewModel test and the bridge call); persona links (none on this server); a lorebook attached to several characters on device (unit and Compose UI tests)
- Version `0.1.10` / `versionCode 11`

### 2026-11-17 — Fix: 3 s cap on opening a chat (branch `fix/chat-open-timeout`)

- `CharacterRepositoryImpl.openChat()` reveals the chat after at most 3 s (was 10 s) from the request even without `chatOpened`, and logs `No chatOpened within 3000 ms; showing the chat anyway` at WARN (no character or message data); the page-load wait before the request doesn't count
- Verified SillyTavern's "# Msg. to Load (0 = All)" setting (`power_user.chat_truncation`, default 100, this server on the default) and how `printMessages()` / `scrollOnMediaLoad()` use it; noted in the 1.6 spec
- Unit tests: `CharacterRepositoryImplTest` (3, virtual time); `./gradlew build` green
- Verified on Medium_Phone_API_35 against SillyTavern 1.19.0 over Tailscale: a normal open (Bob) got `chatOpened` and logged nothing; opening Tide Pods with the page's JavaScript frozen for 4.5 s through DevTools logged the warning, revealed the chat at 3 s, and the late `chatOpened` 3.2 s later was ignored
- Not verified on the Pixel 8 (it was locked during testing); nothing device-specific changed
- Version `0.1.9` / `versionCode 10`

### 2026-11-17 — Phase 1.6 Chats & Characters Tabs (branch `feature/chats-characters-tabs`)

- Bridge: `requestRecentChats()` posts `recentChats` from SillyTavern's `POST /api/chats/recent` (`{ max: 100 }`, `getRequestHeaders()`): character ID, last message (≤ 200 chars, null for an empty chat), last-message time, message count; sent with `characters` after `ready` and on the same debounced events. `characters` now also carries `fullAvatarUrl` (`/characters/<encoded avatar>`). `selectCharacter` answers with `chatOpened` once the chat is drawn and settled (see Notes). New selectors (`getRequestHeaders`, `fullAvatarPath`, `api`, `recentChat`, `dom.chat`) in `st-selectors.js`
- Data / domain: `BridgeMessage.RecentChats` + `ChatOpened`; `BridgeRepository` exposes `chatActivity` and `characters` as nullable (null = not reported yet); `CharacterRepository` + `CharacterRepositoryImpl` (`characters`, `chatActivity`, `pageState`, `refresh()` waits for the answer ≤ 5 s, `retry()`, `openChat()` waits for `chatOpened` ≤ 10 s); `ChatActivity`, `RecentChat`; `CharacterSummary.portraitUrl`; `GetRecentChatsUseCase` (one row per character from its newest chat, newest first, undated last), `GetCharacterLibraryUseCase` (by name, case-insensitive, filtered by query). Coil uses the shared OkHttp client (`TravelingTavernApplication` is the `SingletonImageLoader.Factory`)
- WebView hosting moved to the app shell (`TravelingTavernNavHost`): always attached, fixed size, every destination but Chat wrapped in an opaque touch-consuming surface; while covered the WebView is unfocusable, gives up the keyboard and is hidden from accessibility (`importantForAccessibility = noHideDescendants`); `WebViewBackHandler` is registered by Chat so it wins over the NavHost
- UI: `ChatsScreen` / `ChatsViewModel` (list, pull to refresh, empty state → Characters, banner, re-select scrolls to top, times refresh every minute); `CharactersScreen` / `CharactersViewModel` (search field in the app bar, 2-column 3:4 grid with full-size portraits and name on a scrim, "no matches" text, FAB → Create / Import sheet "Coming soon", empty state → Advanced); `ChatScreen` / `ChatViewModel` (top bar, cover until `chatOpened`); shared `CharacterAvatar` / `CharacterImage` (initial until the image loads), `ConnectionBanner`, `EmptyState`, `RelativeTime`
- Unit tests: `GetRecentChatsUseCaseTest` (5), `GetCharacterLibraryUseCaseTest` (4), `ChatsViewModelTest` (6), `CharactersViewModelTest` (5), `ChatViewModelTest` (3), `RelativeTimeTest` (5), `BridgeMessageParserTest` +4; `./gradlew build` green. Compose UI tests: `ChatsContentTest` (5), `CharactersContentTest` (6) — compiled; run before release per the testing rule
- Verified on Medium_Phone_API_35 against SillyTavern 1.19.0 over Tailscale: Chats list with avatars, previews (emphasis markers stripped), empty-chat row without preview, relative times; Characters grid with full-size portraits, search filtering, add sheet; list → Chat → back for Bob, Seraphina and (from Characters) Oliver with the character panel closed and no reload
- Verified on the Pixel 8 (dark theme): list data 8 s after a cold start; covered WebView: 10 × 100 ms timers took ~1.01 s (no throttling), `visibilityState` visible, a SillyTavern event while covered produced fresh `characters` + `recentChats`, no WebView in the accessibility tree, a tap on the list where SillyTavern's input sits doesn't reach the page; Chat: WebView in the accessibility tree, tap on the input opens the keyboard (`mInputShown=true`) with the input row right above it; landscape in Chat and on the list (no page visible in the cutout strip); 13 list → Chat → back round trips across Bob, Tide Pods, Seraphina and Jasper with on-device frame capture: no blank or half-drawn page frame (cover, then the settled chat — Bob ~1 s, Tide Pods ~1.5–2 s, Seraphina with images ~2.5 s), reopening the already-open chat is immediate, and a JS marker survived the whole session (no reload); 0 crashes
- Not verified: pull to refresh by hand (covered by the ViewModel test; `refresh()` exercised through the bridge), the error banners on device (previews + Compose UI tests), a library with no characters, Advanced (still a placeholder)
- Version `0.1.8` / `versionCode 9`

### 2026-11-17 — Docs: Compose UI tests going forward (branch `docs/compose-ui-tests`)

- `coding-standards.md` → Testing: new and changed native Compose screens/components with real behavior get Compose UI tests in `androidTest` (stateless `…Content` composable, fixed `UiState`, fake callbacks; states render, taps call the right callback, enabled/disabled rules). Layout-only composables and the WebView host are exempt; WebView/bridge stays manual on device
- Unit tests (fast) still run before every commit and merge via `./gradlew build` — now stated explicitly in the workflow's Test step. Android tests (slow) run only before each release: `ai-guidelines.md` → Releasing to main has a new step 2 (run `./gradlew connectedDebugAndroidTest` on an emulator or device, note the result in the PR)
- `CLAUDE.md`: added `./gradlew connectedDebugAndroidTest`. Not in CI (would need an emulator runner); no tests backfilled for existing screens
- Dependencies were already present (`ui-test-junit4`, `ui-test-manifest`), so no build changes and no version bump

<!-- Completed work is added here, newest last -->

### 2026-11-17 — Phase 1.5 Clean Theme (branch `feature/clean-theme`)

- `clean-theme.css` (all rules under `body.tt-clean`, colors left to SillyTavern's theme): hides the top toolbar and all nine drawer panels, the TopInfoBar chat bar, and the in-chat `.drawer-opener` shortcut buttons (they would open hidden drawers); `#sheld` fills the viewport (`top: 0`, `100dvh`); message text and input at least 16px (`max(16px, var(--mainFontSize))`); input-row buttons at least 48×48 without touching their `display`, so `#mes_stop` stays hidden until a generation runs
- Bridge: `setClean(enabled)`, `installStyle(id, base64)` (UTF-8 via `TextDecoder`, replaces an existing tag); `selectCharacter` opens the chat without the character editor (`switchMenu: false`). Kotlin: `BridgeScript.installStyle`, `PageModeStore` (clean on by default), `BridgeInstaller` injects the stylesheet + current mode after the bridge on every page load, `BridgeClient.setCleanMode()`
- Unit tests: 2 new in `BridgeScriptTest` (Base64 round-trip with quotes, backslashes, newlines and unicode; only Base64-safe characters in the script); `./gradlew build` green
- Verified on Medium_Phone_API_35 against SillyTavern 1.19.0 over Tailscale (DevTools + screenshots): cold start → `tt-clean` on, toolbar and TopInfoBar hidden, `#sheld` at the top and full height, all four input buttons 48×48, messages 16px, one style tag; welcome screen without the drawer shortcuts; opening Bob through the bridge shows his chat directly (character panel stays closed); `setClean(false)` brings back the full UI, `setClean(true)` hides it again; `location.reload()` re-applies clean mode with still one style tag
- First pass bug fixed during verification: forcing `display: flex` on the input buttons made the hidden stop button appear
- Not verified on device: `BridgeClient.setCleanMode` from Kotlin (no caller until 1.8; the JS it calls was exercised), the Pixel 8, a light SillyTavern theme
- Version `0.1.7` / `versionCode 8`

### 2026-11-17 — Fix: WebView load timeout and status-bar strip (branch `fix/webview-load-timeout`)

- **Load timeout:** new `LoadWatchdog` (15 s, coroutine timer on the app scope). Started when the app loads the server and on every `onPageStarted`; cancelled by `onPageCommitVisible` (the server's response started rendering), so a slow but reachable server is never cut off. On timeout, if the page is still loading: `stopLoading()` + `PageError.Unreachable`
- **Status-bar strip — cause found:** a blank WebView paints white, and a Compose `AndroidView` doesn't clip, so the white page also covered the status-bar area (invisible light icons in dark theme). Fix: WebView background transparent (also removes the white flash before SillyTavern paints) and `clipToBounds()` on the `AndroidView`
- Unit tests: `LoadWatchdogTest` (4, virtual time); `./gradlew build` green
- Verified on Medium_Phone_API_35 (dark theme): error state with a dark status bar and visible icons (white before the fix); emulator `network delay 120000` (a connection that never answers) → Unreachable overlay ~20 s after launch, including app start-up and polling (was ~2 min on the Pixel); `network delay none` + Retry → page and bridge (`Ready`) back; `network delay 5000` (slow but reachable) → page loads, `Ready` after ~14 s, no false timeout
- Verified on Pixel 8 (0.1.6): Tailscale off → cold start → Unreachable overlay ~18 s after launch (was ~120 s), dark status bar with visible icons in both the loading and error states; Tailscale on → Retry → SillyTavern and the bridge (`Ready`, `characters (3)`) back
- Version `0.1.6` / `versionCode 7`

### 2026-11-17 — Phase 1.4 JS Bridge Foundation (branch `feature/js-bridge`)

- Assets: `st-selectors.js` holds every SillyTavern name (event keys, context members, character/message fields); `bridge.js` waits for `SillyTavern.getContext()`, listens to SillyTavern's `eventSource`, posts `{ v: 1, type, ... }` messages (`ready`, `characters` debounced 300 ms, `chatChanged`, `messageReceived` for character messages only, `error`) and exposes a frozen `window.__ttBridge` (`requestCharacters`, `selectCharacter` maps avatar ID → index → `selectCharacterById`, `setMode` swaps the `tt-mode-*` body class). Both scripts guard against running twice
- Data: `BridgeInstaller` registers the `TravelingTavern` listener for the saved origin only (re-registered if the server changes, main frame only) right before each load, and injects the scripts on every `onPageFinished`; `BridgeMessageParser` never throws (malformed / missing `v` or `type` / missing fields → dropped and logged, unknown types → `Unknown`); `BridgeRepository` exposes `messages`, `characters`, `isReady` (reset on each page start) and logs only the message type at DEBUG; `BridgeScript` + `BridgeClient` are the only place JS call strings are built, arguments JSON-encoded with U+2028/2029 escaped
- Domain: `CharacterSummary` (`lastChatAt: Instant?`)
- WebView without `WEB_MESSAGE_LISTENER` → `PageError.WebViewOutdated` overlay ("update it in the Play Store", no Retry / Server settings since neither helps)
- Unit tests: `BridgeMessageParserTest` (10), `BridgeScriptTest` (6); `./gradlew build` green, lint only version suggestions
- Verified on Medium_Phone_API_35 against SillyTavern 1.19.0 (DevTools + logcat): cold start delivers `characters (3)` then `Ready`; `selectCharacter("default_Seraphina.png")` opens Seraphina in SillyTavern and Kotlin gets `ChatChanged`; an unknown ID produces a bridge `Error`; `setMode` sets `tt-mode-story` and rejects invalid names; `location.reload()` re-injects cleanly with exactly one `Ready`
- Verified on Pixel 8 (Android 17), user and Claude (logcat + DevTools): cold start → `characters` + `Ready`, full-height page; `selectCharacter` for `default_Seraphina.png`, `Tide Pods.png` (space in ID) and `Bob.png` each opened the chat and produced one `ChatChanged`; replies the user generated in Bob's and Tide Pods' chats produced `MessageReceived`; page reload → exactly one `Ready`; Tailscale off → Unreachable overlay, Tailscale on → Retry recovers with the bridge back; drawer + JS marker survived backgrounding and two rotations (no reload); import file picker opens, cancel returns to a usable page twice; GitHub link opens the browser. Whole run: 0 crashes, 0 bridge errors or malformed messages
- Found on the Pixel, not fixed here: with Tailscale **off** the page sits on the loading spinner for ~2 min (the WebView's own network timeout) before the Unreachable overlay appears — airplane mode fails fast, a silently dropped connection doesn't. Also, during the cold-start loading and error states the status-bar strip is white with invisible icons
- Not verified on device: the Kotlin `BridgeClient` path (no screen calls it until 1.6 — covered by `BridgeScriptTest`, the JS it produces was exercised through DevTools), the character-list events from editing/duplicating a character (skipped to avoid changing the user's data), the origin restriction (relies on the WebView API; other-host pages open in the browser anyway), the outdated-WebView overlay (preview only)
- Version `0.1.5` / `versionCode 6`

### 2026-11-17 — Android CI (branch `chore/android-ci`)

- Added `.github/workflows/android-ci.yml`: on PRs into `main`, pushes to `main` and manual runs, builds (`assembleDebug`), runs unit tests and lint on `ubuntu-latest` with Temurin JDK 21 and `gradle/actions/setup-gradle` caching; uploads the debug APK and test/lint reports as artifacts (14 days)
- Marked `gradlew` executable in Git (`100644` → `100755`); it was committed from Windows without the flag, so `./gradlew` failed with "Permission denied" on Linux/macOS. No effect on Windows (`gradlew.bat`)
- Branching: `main` is now protected by a GitHub ruleset (PR + green CI required, no force pushes or deletion); new long-lived `dev` branch for day-to-day merges. `ai-guidelines.md` updated (branch from and merge into `dev`, release to `main` by PR with a merge commit); CI also runs on PRs into and pushes to `dev`
- No app code changed, so no version bump
- Verified by the PR's own CI run; nothing on device changed

### 2026-11-17 — Min SDK 31 (branch `chore/min-sdk-31`)

- `minSdk` 26 → 31 (Android 12) so 1.7b's on-device speech recognizer (`createOnDeviceSpeechRecognizer`, API 31) is always available as an API; updated `CLAUDE.md`, `README.md` and the 1.7b spec (`Unavailable` now only means on-device recognition isn't available)
- Removed the now-redundant API 31 check around dynamic color in `Theme.kt`
- `./gradlew build` green; installed on Pixel 8 (`minSdk=31`, `0.1.4`), launches and loads SillyTavern normally
- Version `0.1.4` / `versionCode 5`

### 2026-11-17 — Phase 1.3 WebView Host (branch `feature/webview-host`)

- Domain: `PageState` (`Loading` / `Ready` / `Error`) and `PageError` (`Unreachable`, `AuthFailed`, `HttpError(code)`)
- Data: `WebViewHolder` (`@Singleton`) creates the one WebView lazily on a `MutableContextWrapper`, swaps in the Activity on `attach` and back on `detach` (guarded so a late detach can't undo a new attach), loads the saved URL once and reloads only when it changes or a settings change happens while in an error state. `PageStateTracker` keeps a main-frame error alive past the `onPageFinished` that follows it. `TavernWebViewClient`: basic auth answered once and only for the saved host, main-frame errors → `PageState.Error`, other-host links → system browser. `TavernWebChromeClient` (Aegis port, ISC notice kept): file chooser via Activity Result, mic/camera only for the server origin and only what the user granted. `@ApplicationScope` coroutine scope added (`CoroutineModule`)
- UI: `SillyTavernWebView` + `SillyTavernWebViewModel` attach the shared WebView (removed from its parent, never destroyed, on dispose), flush cookies on pause, `BackHandler` while the WebView can go back; `PageStateOverlay` with loading and error states (Retry, Server settings) and previews. Chats tab hosts it temporarily (spec said Home, which no longer exists)
- Navigation: Server settings opens Connect; saving from there pops back instead of rebuilding Chats. Scaffold padding is now consumed so `imePadding()` doesn't double-count the system bars; `adjustResize` set on the Activity
- Manifest: `RECORD_AUDIO`, `CAMERA` (requested only when the page asks), camera/mic features optional; `buildConfig` enabled for `BuildConfig.DEBUG`
- Unit tests: `PageStateTrackerTest` (5), `ServerOriginTest` (5); `./gradlew build` green, lint only version suggestions
- Verified on Medium_Phone_API_35 against SillyTavern 1.19.0 (over Tailscale): page loads in the Chats tab; an open SillyTavern drawer survived tab switches, backgrounding and two rotations (no reload); airplane mode → Unreachable overlay → Retry recovers; Server settings opens Connect and back returns; character import opens the system file picker, cancel leaves the page usable (second open works); GitHub link opens Chrome, not the WebView
- Verified on Pixel 8 (Android 17) against the same server: clean install → Connect → Save loads a full-height page (before the fix: collapsed layout, `100dvh` = 0); docked keyboard pushes SillyTavern's input bar above it; GitHub link opens the default browser and back returns; backgrounding and returning keeps the page (a JS marker survived, so no reload)
- Mic: voice input via the SillyTavern **Speech Recognition** extension tested manually on the Pixel 8 by the user — works. Which STT provider it uses, and whether it goes through our `onPermissionRequest` path over plain `http://`, was not checked. Speech Recognition is now listed under "SillyTavern Extensions" in `README.md`
- Not verified on device: back navigation inside the WebView (SillyTavern adds no history entries in normal use), basic auth (server has it off), camera
- Version `0.1.3` / `versionCode 4`

### 2026-11-16 — Phase 1.2 Connect Screen (branch `feature/connect-screen`)

- Domain: `ServerConfig`, `ConnectionResult`, `normalizeServerUrl()` (pure `java.net.URI`, adds `http://`, strips path/trailing slash, lowercases host), `ServerConfigRepository`, `ConnectionTester`, `TestConnectionUseCase`
- Data: Preferences DataStore (`settings`) for the base URL; username and password stored as Tink AES256-GCM ciphertext wrapped by an Android Keystore master key (`SecurityModule`, `CredentialCipher`); `ServerConfigRepositoryImpl` exposes decrypted credentials only to the data layer
- Network: one `@Singleton` `OkHttpClient` with `BasicAuthInterceptor` — adds basic auth only when scheme/host/port match the saved server and no `Authorization` header is already set. `OkHttpConnectionTester` does `GET {baseUrl}/` with a 10 s call timeout and classifies by status code and `<title>SillyTavern</title>` (first 64 KB of body)
- UI: `ConnectScreen` (stateless `ConnectContent` + previews for empty/testing/error/success), `ConnectViewModel` — any input edit resets the test result so Save is only enabled for tested inputs; `AuthRequired` auto-reveals the login fields
- Navigation: `StartDestinationViewModel` reads the saved config once at launch → Connect or Chats; saving navigates to Chats with Connect popped; tab switching now pops to the Chats graph instead of the root start destination (which may be Connect)
- Tink `1.24.0` added; `FilledTonalButton` used for Test (the only filled button is Save, per ui-guidelines)
- Unit tests: `ServerUrlTest` (6), `TestConnectionUseCaseTest` (3), `ConnectViewModelTest` (6) — all passing; `./gradlew build` green, lint only version suggestions
- Verified on Medium_Phone_API_35 against the real SillyTavern 1.19.0 (over Tailscale): fresh install opens Connect; bare `host:port` → "Connected to SillyTavern" → Save → Chats; back exits (Connect popped); cold relaunch opens Chats directly. Error cases shown correctly: invalid address, `example.com` → not SillyTavern, unreachable IP → timeout message after 10 s, login toggle reveals fields. `AuthRequired`/`AuthFailed` not exercised on device (server has basic auth off) — covered by unit tests
- Version `0.1.2` / `versionCode 3`

### 2026-11-16 — Phase 1.1b Bottom Navigation & Visual Style (branch `feature/bottom-navigation`)

- `Routes.kt` split into `Routes.Tab` (Chats, Characters, Me — nested graphs) and `Routes.FullScreen` (Connect, Chat, Advanced). Home and Settings screens removed
- `TravelingTavernNavHost` is now the shell: one `Scaffold`, `TravelingTavernBottomBar` shown only when the current destination is inside a tab graph and the IME is hidden; tab switches use `popUpTo(start) { saveState }` + `restoreState`; re-selecting the current tab emits on `TabReselectEvents` (via `LocalTabReselectEvents`) for 1.6 lists to scroll to top
- Tab icons are vector drawables (Material Icons, outlined/filled pairs) — `?attr/colorControlNormal` tint had to be dropped since the app has no AppCompat; Compose `Icon` tints from `LocalContentColor`
- Theme: neutral light/dark palette, amber on primary **and** secondary so the `NavigationBar` indicator uses the accent; `useDynamicColor` parameter defaults to `false`; Inter Regular/Medium/SemiBold bundled in `res/font/` (OFL text in `licenses/Inter-OFL.txt`) and applied to the whole M3 type scale; shapes 12/16/16dp
- Version `0.1.1` / `versionCode 2`
- `./gradlew build` passes; lint only reports newer-version suggestions
- Verified on Medium_Phone_API_35: all three tabs in dark and light, selected indicator and labels, back from Me returns to Chats, back again exits. Not device-verifiable yet: bottom bar hiding on full-screen routes and under the keyboard (no navigation to those routes or text fields exist until 1.2/1.6) — covered by code path only

### 2026-11-16 — Phase 1.1 Project Setup & App Shell (branch `feature/project-setup`)

- Added all spec dependencies to the version catalog; Hilt (`TravelingTavernApplication`, `MainActivity`), Compose navigation, brand theme (Color/Shape/Theme), `Routes.kt`, `TravelingTavernNavHost`, placeholder screens, `assets/web/` placeholders, `network_security_config.xml`, `INTERNET` permission, package scaffold
- **Toolchain notes (AGP 9.0.1 uses built-in Kotlin):**
  - Android Studio's template pulled `core-ktx 1.19.0` / `lifecycle 2.11.0`, which require compileSdk 37 + AGP 9.1. Only SDK 36 is installed and the spec targets 36, so pinned `core-ktx 1.17.0`, `lifecycle 2.9.4`, `activity-compose 1.11.0`, Compose BOM `2025.09.01`
  - Kotlin plugins (compose, serialization) set to `2.2.10` to match AGP 9.0's bundled Kotlin
  - Hilt `2.60.1` — 2.57.x fails on AGP 9 ("Android BaseExtension not found")
  - KSP `2.3.4` — KSP1 (`2.0.21-1.0.x`) and `2.2.10-2.0.2` both fail on AGP 9 (`kotlin.sourceSets` DSL not allowed with built-in Kotlin)
  - `material-icons-core` added explicitly (not transitive from Material 3 1.4)
  - Gradle needs `JAVA_HOME` pointed at Android Studio's JBR (`C:\Program Files\Android\Android Studio1\jbr`)
- `./gradlew build` passes (lint + unit tests). Remaining lint warnings are newer-version suggestions and unused template colors in `res/values/colors.xml`
- Verified on Medium_Phone_API_35 emulator: launches to Home, top bar Settings action navigates to Settings, back returns, light and dark both render, no crashes. Dynamic color was active (API 35), so the brand fallback palette is covered by previews only