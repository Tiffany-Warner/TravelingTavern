# Phase 1.7 — Chat Screen (EchoText Thread)

> **On hold (2026-11-18, user):** blocked on EchoText thread storage — see Notes. Until the user's EchoText fork stores threads per thread, Chat stays as it is from 1.6 (main chat, Story only).

## Overview

**Text is the chat.** Opening a character shows their EchoText texting thread full screen: one conversation per character, where you talk and where proactive messages land, like Kindroid. SillyTavern's main roleplay chat is no longer a tab. It stays one tap away in the app bar's menu (**Story view**) for the occasional swipe, edit or long scene, and in Advanced (1.8).

Switching between the thread and Story view never reloads the page and never loses your place in either.

## Requirements

### Investigation (done — see Notes)

The EchoText investigation is complete and recorded under Notes. Still open:

- Whether `visibility: hidden` keeps `#et-messages`' scroll position (untested: no EchoText thread existed yet) **(verify on device once a thread exists)**

### Screen (`ui/chat/ChatScreen.kt`)

- Full-screen destination (bottom bar hidden)
- Top app bar: back, character avatar + name, and a ⋮ overflow menu with one item:
    - **Story view** while the thread is showing, **Texting** while Story view is showing
    - Without EchoText the item is still **Texting**; it opens a sheet instead (see EchoText availability)
- **No tab row or segmented control.** The chrome is the same height as in 1.6, so `ChatChromeHeight` doesn't change and the WebView is never resized (see Notes)
- Body: the shared `SillyTavernWebView` (1.3), hosted by the app shell
- Keyboard pushes the visible input up correctly (IME insets), in both views
- **Back** always returns to the list Chat was opened from, whichever view is showing

### Route

- Chat route takes `characterId` and an optional `view` argument (`text` | `story`)
- `view` is for later deep links and tests. Nothing passes it in 1.7 except tests

### Which view opens

1. The `view` route argument, if given
2. **Text**, if EchoText is available
3. **Story** otherwise

No per-character memory: Chat always opens on the thread. Story view is a detour, not a mode you live in.

### Modes (CSS in `clean-theme.css`, toggled via `__ttBridge.setMode`)

- `tt-mode-text` (the default): EchoText panel fills the viewport, main chat hidden. In EchoText's header (2026-11-18, user):
    - **Hide:** avatar, name, character switcher (▾) and close (×) — the app bar replaces them
    - **Keep reachable:** the Tethered / Untethered toggle and EchoText's ⋮ menu
    - **Keep visible:** the emotion badge (it sits in the status row under the name, so the status row stays)
    - The Tethered / Untethered toggle switches between **two separate threads** per character (`chatHistory` / `untetheredHistory`, see Notes): one tap changes which conversation is showing. That's EchoText's own behaviour; the app leaves it as is
- `tt-mode-story`: main chat visible with clean theme, EchoText panel hidden
- Both keep `tt-clean` on. EchoText's panel sits outside `<body>`, so its rules use `body.tt-… ~ #et-portal …` (see Notes)
- Hide the floating button `#et-fab` in both modes: the app opens and closes the panel itself
- **Hiding keeps scroll position:** the hidden side uses `visibility: hidden` (verified for `#chat`), so returning to a view shows the same place you left
- The hidden side is not focusable and not read by accessibility services
- Leave the area beside each send button clear: 1.7b adds a mic button to the thread's input row (and Story view's)

### EchoText availability

- The bridge reports whether EchoText is available: `#et-fab` exists without `et-fab-disabled`. It waits for `#et-fab` after `APP_READY` before reporting, and reports again when EchoText's own on/off switch changes
- Without EchoText:
    - Chat opens on Story view. **No banner** (2026-11-18): a banner under the app bar would resize the WebView or cover the top of the chat
    - The ⋮ menu keeps **Texting**; tapping it opens a sheet: "Texting needs the EchoText extension in SillyTavern." with a "How to set it up" link to the README's extensions section
    - Story view works normally
- **Availability not known yet** (EchoText starts after SillyTavern, so Chat can open before the bridge has reported): wait for the report within the same 3 s cap as the open; if it hasn't come by then, open Story view. If EchoText reports in later, the ⋮ menu's Texting switches to the thread as usual
- The app does not change EchoText's settings (Tethered / Untethered, proactive messaging, activity level). It uses whatever the user set in SillyTavern

### Opening the thread

1. `selectCharacter(characterId)` (SillyTavern's active character)
2. If the panel isn't open, open it: click `#et-fab` **only when `#et-panel` is closed** (a click on an open panel closes it)
3. If EchoText shows another character, pick the right one through its own picker: click `#et-char-name`, then the `.et-char-picker-item[data-char-key="<characterId>"]`. Skip this when that item is already `.et-char-picker-item-active`, so EchoText's settings are written as rarely as possible
4. Wait for a new bridge message, `echoTextOpened` (`#et-char-name` shows the character and `#et-messages` has settled)
5. Chat stays covered until then, like 1.6's `chatOpened`, so a half-drawn thread is never shown. Same 3 s cap: reveal anyway after 3 s and log a warning (no character or message data)

Story view, when chosen from the ⋮ menu, uses 1.6's `chatOpened` wait the first time it's shown.

### ViewModel (`ui/chat/ChatViewModel.kt`)

- Takes `characterId` and optional `view` from the nav arguments
- On enter: open the thread (above) if EchoText is available, also select the character in SillyTavern, then apply the opening view's mode
- `toggleView()` → `setMode(...)`, with the first-time wait for the view being shown
- `UiState`: `Loading`, `Ready(character, view, isEchoTextAvailable)`, `Error(type)`
- Error types: page errors from 1.3 (unreachable, auth failed), `CharacterNotFound`. EchoText missing is not an error: it's `Ready` with `isEchoTextAvailable = false`

### Out of scope

- **Group chats.** Chat opens single characters only
- **Chats tab previews from EchoText.** The Chats list (1.6) still shows each character's main-chat last message and time. With Text as the chat, it should show the EchoText thread's instead. Planned as **1.7c**, after the thread storage question (Notes) is settled
- **A native thread UI.** Drawing the thread in Compose would need a public EchoText API (send, get thread, on message). If wanted later, offer that API upstream before considering a fork

## Tests

- Unit tests:
    - `ChatViewModel`: opening view (route argument, Text default, Story when EchoText is missing), `toggleView`, `CharacterNotFound`
    - `BridgeMessageParser`: `echoTextOpened` and the EchoText availability field, including missing and malformed
    - `openEchoText()` (or equivalent): returns on that character's `echoTextOpened`, another character's doesn't count, gives up after 3 s and logs
- Compose UI tests (`ChatContent`): the ⋮ menu shows the right item for each view and calls back; without EchoText, Texting opens the sheet with its link
- Version bump per `CLAUDE.md`

## Verify on Device

- [ ] Opening any character shows **that character's** thread, including after EchoText was pinned to another character on the desktop
- [ ] Thread ↔ Story view is instant and keeps your place in both (including `#et-messages` once a thread exists)
- [ ] Sending works in both views, with the keyboard pushing the right input up
- [ ] Leaving and re-entering Chat doesn't reload, and reopens on the thread
- [ ] Opening the thread never shows a half-drawn panel
- [ ] Back from either view returns to the list
- [ ] EchoText's floating button never shows in the app
- [ ] Turning EchoText off in SillyTavern (its own switch): Chat opens on Story view, and ⋮ → Texting opens the "needs EchoText" sheet
- [ ] Opening Chat with `view=story` / `view=text` (from a test or `adb`) opens that view

## Notes

### Decision (2026-11-18, user): Text is the chat

Option B: EchoText's thread is the one conversation per character, Kindroid-style. The main chat moved from a tab to the ⋮ menu's Story view. This removed the tab control, tab memory and the `ChatChromeHeight` change.

### Thread storage (verified 2026-11-18) — blocks 1.7

- **Where threads live:** in SillyTavern's settings file, not in chat files: `extension_settings.EchoText.chatHistory[<avatar file>]` (Tethered) and `untetheredHistory[<avatar file>]` (Untethered). EchoText saves a message by changing that object and calling `saveSettingsDebounced()`
- **How SillyTavern saves settings:** `saveSettings()` (`script.js`) POSTs the page's **whole** in-memory settings (`extension_settings` included) to `/api/settings/save`. Every open page holds its own copy, so the last page to save wins
- **Two-page test (2026-11-18), in the app's WebView:** page B (a hidden iframe loading SillyTavern again, its own JS and settings copy) loaded; page A then saved a throwaway marker in `extension_settings` → on the server; page B, which never saw it, saved without changing anything → **the marker was gone from the server**. A message saved by the phone is lost the same way when a desktop tab opened earlier saves anything (a setting, a character switch, its own EchoText message). Marker removed afterwards
- **EchoText-Proactive (server plugin, `mattjaybe/SillyTavern-EchoText-Proactive` 1.0.0, read from source):**
    - Keeps per-character state in server memory only (no files): pages `POST /register` and `/heartbeat` their state, the plugin's own timer generates messages (OpenAI-compatible / Ollama sources) or queues a `deferred_trigger` for the page to generate (default / profile sources), up to 20 queued per character. A server restart drops the queue
    - Pages fetch the queue with `GET /pending?key=<avatar>` every 60 s and when the tab becomes visible — only in Tethered mode and only for the character the page currently shows — append the messages to their own copy of the thread, `POST /ack` (removes them from the queue) and save the whole settings file
    - So server-generated messages land in the same settings file: only the first page to poll gets each one, and another page's older copy can erase it afterwards. The plugin writes nothing itself, so it can't overwrite threads directly
    - **This conclusion comes from reading the plugin's code, not from a test.** The two-page test ran without the plugin (it isn't installed and `enableServerPlugins` is false; `/api/plugins/echotext-proactive/status` → 404). Repeat the two-page test with the plugin installed before relying on any fix
- **Decision (2026-11-18, user):**
    - 1.7 stays on hold. EchoText-Proactive doesn't fix it (it writes no files; pages still save the whole settings file)
    - The fix is the user's own **fork of EchoText** (the extension, not EchoText-Proactive), storing threads per thread instead of in the settings file. A separate project for later, not part of this repo; no upstream PR. The user is checking EchoText's license first — don't fork or change EchoText until then
    - Until the fork is ready, Chat stays as it is from 1.6 (main chat, Story only)

### EchoText findings (verified against EchoText 1.2.6, upstream `mattjaybe/SillyTavern-EchoText`, in the app's WebView, 2026-11-18)

- **It's a floating window.** `#et-fab` (floating button) toggles `#et-panel`. Both live in `#et-portal`, a fixed full-viewport container appended to `<html>` **as a sibling of `<body>`** (not inside it) with `pointer-events: none` and, on mobile, z-index 2147483647. CSS that depends on a `body` class must reach it with `body.tt-… ~ #et-portal …`
- **Sizing:** on a phone (`isMobileDevice()`: user agent contains Android) EchoText loads `mobile-style.css`, which makes `.et-panel` fill the portal (`position: absolute`, top/left/right/bottom 0, no drag or resize). In the app it measured 411 × 802 CSS px at 0,0 — the whole WebView
- **Opening:** no public function. `openPanel()` / `closePanel()` are private; a click on `#et-fab` toggles the panel (so clicking it while open **closes** it — check `#et-panel` first). With `autoOpenOnReload` on and `panelWasOpen` true, EchoText reopens the panel itself after a page load. Opening sets `panelWasOpen = true` in EchoText's settings
- **Which character it shows:** EchoText follows SillyTavern's active character (`CHAT_CHANGED`) **until a character is picked in its own picker**, which pins it (`selectedCharacterKey`, in memory) and stops it following SillyTavern. With `autoLoadLastCharacter` on, it also pins `lastCharacterKey` after every page load. So `selectCharacter` alone isn't enough
- **Setting the character from script:** click `#et-char-name` → the picker `#et-char-picker` opens inside the panel with one `.et-char-picker-item[data-char-key="<avatar file>"]` per character; the current one has `.et-char-picker-item-active`. Clicking an item runs EchoText's own selection (pins it, saves `lastCharacterKey`, re-renders the panel) and closes the picker; `#et-char-picker-close` closes it without a change. Verified: SillyTavern on Seraphina, picker → Bob, EchoText showed Bob
- **Header** (`#et-panel-drag-handle`):
    - Avatar: `#et-char-avatar-wrap`; the "EchoText" title `.et-panel-echotext-title` only shows with no character
    - Name + switcher ▾: `#et-char-name` (caret `.et-char-name-caret`) in `.et-char-name-row`
    - Status row: `#et-panel-status-trigger` (tap → details) with `#et-panel-status-content`; the emotion badge is `.et-status-chip-emotion` in it. The name row and status row share `.et-header-info`, so only `.et-char-name-row` is hidden
    - Right side: tether toggle `#et-mode-toggle-btn`, ⋮ `#et-overflow-btn` (menu `#et-overflow-menu`), close `#et-close-btn`
- **Thread and input:** messages scroll in `#et-messages` (inner `#et-messages-inner`, one `.et-message` per bubble); input row `.et-input-bar` with `#et-input` and `#et-send-btn`
- **Drawing:** selecting a character re-renders the header and messages synchronously (`applySelectedCharacterToPanel` → `renderMessages`); images load afterwards. "Opened" = `#et-char-name` shows the character and `#et-messages` has settled, like `chatOpened`
- **Installed and enabled:** loaded as `third-party/SillyTavern-EchoText` (in `extensions.js` `extensionNames`, not in `extensionSettings.disabledExtensions`); settings in `extensionSettings.EchoText`. Its own master switch (`enabled`) doesn't reload the page: it adds `et-fab-disabled` to `#et-fab` and closes the panel. Available = `#et-fab` exists without `et-fab-disabled`. EchoText initialises after SillyTavern's `APP_READY`, so the bridge waits for `#et-fab` before reporting
- **Hiding keeps scroll:** `visibility: hidden` on `#sheld` (Text) or `#et-panel` (Story) kept `#chat`'s `scrollTop` (2801 of 8405) through Text → Story → Text, and a hidden `#et-input` can't take focus. Not yet checked on `#et-messages` (no EchoText thread on this server yet)
- **Side effects on EchoText's settings:** opening the panel saves `panelWasOpen = true` and picking a character saves `lastCharacterKey`. Both are EchoText's own bookkeeping (not Tethered / proactive / activity settings), but they are shared with the desktop: with `autoOpenOnReload` / `autoLoadLastCharacter` on, the desktop's next reload opens the panel / pins the last character picked on the phone. The bridge only picks when the active character is wrong, to keep writes rare
- **Fragility:** opening the panel and picking the character drive EchoText's own buttons, since it has no public API. Every selector above lives in `st-selectors.js` only, so an EchoText update that renames them is a one-file fix. Re-check this section after updating EchoText

### From 1.6

`ChatScreen` / `ChatViewModel` already exist (top bar with back, avatar and name; Story mode only). The WebView is hosted by the app shell, not by this screen; Chat is transparent below its bar. `CharacterRepository.openChat()` returns once SillyTavern posts `chatOpened` (chat drawn and settled) and Chat covers the page until then. The shell keeps the WebView at a fixed size below `ChatChromeHeight`; 1.7 adds no chrome below the app bar, so that height is unchanged.

## References

- @context/coding-standards.md
- @context/ui-guidelines.md
- @context/specs/phase1-5-clean-theme-spec.md
- @context/specs/phase1-6-home-character-list-spec.md
- EchoText: https://github.com/mattjaybe/SillyTavern-EchoText