# Phase 1.7 — Chat Screen (Main Chat)

## Overview

Chat shows the character's SillyTavern **main chat**, the only conversation per character (decision in `project-schema.md` → One Conversation). 1.6 already built the screen. 1.7 makes it feel like texting and adds the groundwork the final phase (Phase 7, proactive messages and reminders) builds on:

- **Display style:** a texting look or the current story look, per character, for the same chat
- **Reload on return:** the open chat is re-read when the app comes back, since another page (a desktop tab, later the Bridge) may have added messages
- **Lifecycle events** reach the page
- **Open a specific chat:** the Chat route can name a chat file, so a list row (and later a notification) opens exactly that chat

No EchoText. Switching styles never reloads the page.

## Requirements

### Investigation first (do this before writing code)

- How to re-read the open chat from script without reloading the page, keeping the view at the newest message: SillyTavern's `reloadCurrentChat()` or equivalent **(verify)**
- The open chat's file name from `getContext()` (e.g. `chatId` / `getCurrentChatId()`) **(verify)**
- How to open a given chat file for the selected character (e.g. `openCharacterChat(fileName)`) **(verify)**
- Whether `/api/chats/recent` (used by 1.6) returns each row's chat file name, so the Chats list can pass it **(verify)**
- The message elements and attributes the texting style needs: message block, text, name, timestamp, and how user vs character messages are marked (e.g. `.mes[is_user="true"]`) **(verify)**
- Write the findings into Notes and add every name to `st-selectors.js`

### Display style (CSS in `clean-theme.css`)

- Two body classes, both only under `body.tt-clean`:
    - `tt-style-text`: messages as bubbles, the user's on the right and the character's on the left, compact name and time, tighter spacing between messages
    - `tt-style-story`: the current clean look from 1.5
- CSS only: message content, swipes, edit and SillyTavern's message buttons keep working in both styles
- Bridge: `setChatStyle(style)` swaps the `tt-style-*` class, like `setMode` swaps `tt-mode-*`, and rejects unknown names
- **Per character**, stored in DataStore (`ChatStylePreferences`, keyed by the character's avatar ID). It is a display preference only: nothing about generation is stored in the app
- **Default: texting** (user to confirm in spec review)
- Top app bar: a ⋮ overflow menu with one item, **Story style** while texting and **Texting style** while story. Same chrome height as 1.6, so `ChatChromeHeight` doesn't change and the WebView is never resized

### Reload on return

- When the app comes back to the foreground (`ON_START` after an `ON_STOP`) while Chat is showing, the bridge re-reads the open chat (`reloadChat()`), staying at the newest message
- Not on the first open (the chat was just loaded) and not on rotation
- No cover: the chat updates in place. If the reload fails, keep the chat as it is and log a warning (no message data)

### Lifecycle events

- Kotlin → JS: `setAppState(foreground)` on `ON_START` / `ON_STOP`, from the app shell (not Chat), so it's sent whatever screen is showing
- `bridge.js` keeps the latest state and the open chat (from `chatChanged`) in one place, `appState`. Nothing reads it in 1.7; Phase 7 sends it to the Bridge as the heartbeat
- These must work in any SillyTavern page: with no `TravelingTavern` port (a desktop browser, later the headless page), the commands still run and `post()` does nothing

### Opening a specific chat

- The Chat route takes `characterId` and an optional `chatFile`
- With `chatFile`: select the character, then open that chat. Without it: SillyTavern's current chat for the character, as in 1.6
- The Chats list passes each row's chat file (if the investigation confirms it's available)
- Same `chatOpened` wait and 3 s cap as 1.6
- A `chatFile` that doesn't exist for that character: open the current chat instead and log a warning
- Register an app deep link for the route (`travelingtavern://chat/{characterId}?chatFile={chatFile}`), so Phase 7's notification tap only has to build that URI

### ViewModel (`ui/chat/ChatViewModel.kt`)

- Takes `characterId` and optional `chatFile` from the nav arguments
- `UiState.Ready` gains `chatStyle`
- `toggleChatStyle()` → saves the preference, then `setChatStyle(...)`
- Applies the saved style on enter, before the cover lifts

### Out of scope

- Group chats
- Picking among a character's older chats in the app (SillyTavern's chat list stays in Advanced)
- Notifications, the heartbeat to the Bridge, anything that generates without the user (Phase 7)

## Tests

- Unit tests:
    - `ChatViewModel`: saved style applied on enter, default when nothing saved, `toggleChatStyle`, `chatFile` passed to the open
    - `ChatStylePreferences`: per-character read and write
    - `BridgeScript`: `setChatStyle`, `setAppState`, `reloadChat`, open with a chat file (argument encoding)
    - Navigation: the route and deep link parse `characterId` and an optional `chatFile` (including names with spaces)
- Compose UI tests (`ChatContent`): the ⋮ menu shows the right style item and calls back
- Version bump per `CLAUDE.md`

## Verify on Device

- [ ] Texting and story styles both render, switch instantly with no reload, and keep the scroll position
- [ ] Each character remembers its own style across app restarts
- [ ] Swipe, edit and the message buttons work in both styles; sending works with the keyboard pushing the input up
- [ ] A message added from a desktop tab while the app is in the background shows after returning to the app, with the view at the newest message
- [ ] Rotation doesn't reload the chat
- [ ] A Chats list row opens that exact chat, including for a character with several chats
- [ ] `adb shell am start -d "travelingtavern://chat/<id>?chatFile=<file>"` opens that chat
- [ ] In a desktop browser (no app), `__ttBridge.setChatStyle`, `setAppState` and `reloadChat` run without errors

## Notes

### Decision (2026-11-18, user): the main chat is the only conversation

The previous 1.7 (EchoText thread as the chat, main chat as Story view) is dropped, together with 1.7c (Chats list previews from EchoText threads). Its EchoText findings and the thread storage investigation are in Git history: this file before this change (`dev` at `9fd0aa0`). Why it was dropped and what replaces it: `project-schema.md` → One Conversation and Phase 7.

### From 1.6

`ChatScreen` / `ChatViewModel` already exist (top bar with back, avatar and name). The WebView is hosted by the app shell, not by this screen; Chat is transparent below its bar. `CharacterRepository.openChat()` returns once SillyTavern posts `chatOpened` (chat drawn and settled) and Chat covers the page until then. The shell keeps the WebView at a fixed size below `ChatChromeHeight`; 1.7 adds no chrome below the app bar, so that height is unchanged.

## References

- @context/coding-standards.md
- @context/ui-guidelines.md
- @context/project-schema.md (One Conversation; Built Toward the Final Phase)
- @context/specs/phase1-5-clean-theme-spec.md
- @context/specs/phase1-6-home-character-list-spec.md
