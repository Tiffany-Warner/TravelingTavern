# Phase 1.7 — Chat Screen (Main Chat)

## Overview

Chat shows the character's SillyTavern **main chat**, the only conversation per character (decision in `project-schema.md` → One Conversation). 1.6 already built the screen. 1.7 makes it feel like texting and adds the groundwork the final phase (Phase 7, proactive messages and reminders) builds on:

- **Display style:** a texting look or the current story look, per character, for the same chat
- **Fresh chats on return:** when the app comes back to the foreground, the open chat and the Chats list are re-read, since another page (a desktop tab, later the Bridge) may have added messages
- **Lifecycle events** reach the page, and `bridge.js` works in any SillyTavern page
- **Open a specific chat:** the Chat route can name a chat file, so a list row (and later a notification) opens exactly that chat
- **Cleanup** of what the earlier EchoText plan left in the code

No EchoText. Switching styles never reloads the page.

## Requirements

### Investigation first (do this before writing code)

- How to re-read the open chat from script without reloading the page, keeping the view at the newest message: SillyTavern's `reloadCurrentChat()` or equivalent **(verify)**
- The open chat's file name from `getContext()` (e.g. `chatId` / `getCurrentChatId()`) **(verify)**
- How to open a given chat file for the selected character (e.g. `openCharacterChat(fileName)`) **(verify)**
- Whether `/api/chats/recent` (used by 1.6) returns each row's chat file name, so the Chats list can pass it **(verify)**
- SillyTavern's in-memory extension prompt API (`setExtensionPrompt` and its position / role constants) from `getContext()`, and confirm it is never saved to the chat file or settings **(verify)**
- How to tell a generation is running: SillyTavern's generation started / ended / stopped events, or a flag such as `is_send_press` **(verify)**
- The message elements and attributes the texting style needs: message block, text, name, timestamp, and how user vs character messages are marked (e.g. `.mes[is_user="true"]`) **(verify)**
- Write the findings into Notes and add every name to `st-selectors.js`

### Cleanup from the earlier plans

- **`bridge.js` runs without the app.** Today it ends with `if (!port) return;`, so in a desktop browser or a headless page it never hooks SillyTavern's events. Remove the early return: everything runs in any page, and only `post()` does nothing when the `TravelingTavern` port is missing
- **Delete `setMode` / `tt-mode-*`** (`bridge.js`, `BridgeScript`, `BridgeClient.setMode`; nothing calls them). `setChatStyle` / `tt-style-*` replaces them
- **Remove the mic's `"text"` target:** `ChatInputTarget.Text`, the `"text"` wire name in `BridgeMessageParser`, and the `bridge.js` comment "'text' (EchoText) comes with 1.7". A `micPressed` with `"text"` then parses as an unknown target (logged and ignored, as for any unknown value)

### Texting and story styles (a TravelingTavern feature)

SillyTavern has no texting or story mode. These styles exist only in the app (decision 2026-11-19, user, "option C"): SillyTavern on the desktop never changes how it works, and nothing is saved in SillyTavern for them. Managing them on the desktop would need a separate extension, which is not planned.

Each style changes **both** the look and how the character writes, for chats in the app:

| | Texting | Story |
|---|---|---|
| Look (`clean-theme.css`) | `tt-style-text`: bubbles, the user's on the right and the character's on the left, compact name and time, tighter spacing | `tt-style-story`: the current clean look from 1.5 |
| Replies | The texting instruction is added to the prompt (below), so replies come as short text messages | No instruction: normal roleplay replies |

- CSS only for the look, both only under `body.tt-clean`: message content, swipes, edit and SillyTavern's message buttons keep working in both styles
- **Per chat** (character avatar file + chat file name), stored in the app's DataStore (`ChatStylePreferences`). This is the one deliberate exception to "settings that affect generation live in SillyTavern" (see `project-schema.md`)
- **Default** for a chat with no saved choice: **story** (user decision, 2026-11-19). Existing chats look and reply as they do today until you switch them to texting
- Top app bar: a ⋮ overflow menu with one item, **Story style** while texting and **Texting style** while story. Same chrome height as 1.6, so `ChatChromeHeight` doesn't change and the WebView is never resized

#### Editing and deleting messages (texting style)

Bubbles have no room for SillyTavern's row of message buttons, so in texting style a **long press on any bubble** (yours or the character's) opens a bottom sheet with **Edit** and **Delete**. Story style keeps SillyTavern's own message buttons.

- **Edit:** opens SillyTavern's own inline editor for that message, in the bubble, with its confirm and cancel. The keyboard pushes the input up as usual. Editing never regenerates the reply
- **Delete:** a confirmation sheet first (the shared `ConfirmDeleteSheet`, "Delete this message?"), then SillyTavern deletes it. Any message can be deleted, not only the last
- Both run through SillyTavern's own code, so the chat file, swipes and message numbering stay as SillyTavern keeps them, and the desktop sees the same result. Never edit the chat file or the DOM directly
- Bridge:
    - `bridge.js` detects the long press on a message in texting style (about 500 ms, cancelled by scrolling) and posts `messageLongPressed { messageId, isUser }`. A normal tap, text selection and swipe arrows keep working
    - `editMessage(messageId)` and `deleteMessage(messageId)`, each answering with a request ID and success or error, like the lorebook commands
- **While a reply is generating:** the sheet's actions are disabled (SillyTavern doesn't allow editing or deleting during a generation)
- A message that no longer exists when the action runs (for example, deleted on the desktop meanwhile): show "This message has changed. Try again." and re-read the chat
- Investigation **(verify)**:
    - How SillyTavern starts editing a message from script (e.g. clicking its `.mes_edit` button, or an exported function), and how it deletes one message by index (e.g. `/cut <id>` through the slash-command executor, or an exported function). Prefer whatever SillyTavern's own buttons call
    - The message index attribute on each message element (e.g. `mesid`)
    - Whether a long press conflicts with SillyTavern's own touch handling (text selection, swipe gestures)

#### The texting instruction

- One app-wide template, stored in the app's DataStore with a default, for example: *"{{char}} and {{user}} are texting on their phones. Reply as {{char}}'s next text message: short and casual, no narration or actions, emoji are fine."* SillyTavern macros (`{{char}}`, `{{user}}`) are left for SillyTavern to fill
- Editing the template comes with the Me tab (1.8, Me → Chat). Per-character templates are out of scope for 1.7
- **How it reaches the prompt:** while a texting-style chat is open in the app's page, `bridge.js` sets it as an in-memory extension prompt through SillyTavern's own API (`setExtensionPrompt('tt_texting', text, IN_CHAT, depth 1, role system)` or equivalent) **(verify: name and arguments in `getContext()`, and that it is never written to the chat file or settings)**. It's part of every reply in that chat, including swipes and regenerations, and is never saved as a message
- **Cleared** when the page switches to another chat, when the chat is switched to story, and when the app leaves Chat. A page reload clears it too, and the installer sets it again (After a page reload, below)
- Fallback if `setExtensionPrompt` isn't usable: `/inject id=tt_texting … ephemeral=true` before each generation. It has a cost: it is written into the chat's metadata until the reply ends, so another page could briefly see it. Record which one was used in Notes
- Bridge: `setChatStyle(style, instruction?)` swaps the `tt-style-*` class and sets or clears the extension prompt; rejects unknown style names
- Phase 7 (later): the app reports each chat's style with its heartbeat, and the desktop's headless page sets the same extension prompt before generating, so a proactive message in a texting chat comes out as a text

### Foreground and background

- Use **`ProcessLifecycleOwner`** (the whole app going to the background and back), not the Activity's `ON_START` / `ON_STOP`. The Activity is recreated on rotation, so its events would treat every rotation as leaving and coming back
- On each change, the app shell sends `setAppState(foreground)` to the page. `bridge.js` keeps it, together with the open character and chat file (from `chatChanged`), in one object, `appState`. Nothing reads it in 1.7; Phase 7 uses it as the heartbeat

### Fresh chats on return

When the app comes back to the foreground:

- **Chat is showing:** re-read the open chat (`reloadChat()`), staying at the newest message. If a generation is running, wait until it ends, then re-read. No cover: the chat updates in place. If the reload fails, keep the chat as it is and log a warning (no message data)
- **Chat isn't showing:** mark the page's open chat **stale**. The next time Chat opens that same chat, it is re-read before the cover lifts, instead of being shown as it is
- **Always:** refresh the Chats list (`requestRecentChats`)
- Rotation, and switching screens or tabs inside the app, are not a return

### After a page reload

- `BridgeInstaller` already re-sends clean mode after every page load. It also re-sends the current app state and, while Chat is showing, the chat style and (for texting) the instruction
- Anything sent before the bridge exists (before the first load finishes) is lost, so state is pushed by the installer after each load instead of relying on earlier calls

### Opening a specific chat

- The Chat route takes `characterId` and an optional `chatFile`
- `selectCharacter(id, chatFile?)`:
    - With `chatFile`: select the character, then open that chat. Without it: SillyTavern's current chat for the character, as in 1.6
    - The "already showing" shortcut compares the character **and** the chat file, so another chat of the same character is never mistaken for the open one
    - `chatOpened` carries `characterId` and `chatFile`, and `openChat()` matches both
- The Chats list passes each row's chat file (if the investigation confirms it's available)
- Same `chatOpened` wait and 3 s cap as 1.6
- A `chatFile` that doesn't exist for that character: open the current chat instead and log a warning

### Deep link

- Register a deep link for the Chat route: `travelingtavern://chat/{characterId}?chatFile={chatFile}`, so Phase 7's notification tap only has to build that URI
- `MainActivity` becomes `launchMode="singleTop"` and handles `onNewIntent` (passed to the NavController), so a link opened while the app is running reuses the one Activity and the one WebView instead of starting a second Activity
- **No server saved:** go to Connect and drop the link
- **Back** from a chat opened by link goes to the Chats tab, whatever the start destination was

### ViewModel (`ui/chat/ChatViewModel.kt`)

- Takes `characterId` and optional `chatFile` from the nav arguments
- `UiState.Ready` gains `chatStyle`
- `toggleChatStyle()` → saves the preference for this chat, then `setChatStyle(...)` with the instruction when texting
- Applies the saved style (and instruction) on enter, before the cover lifts; clears the instruction on leaving Chat

### Out of scope

- Group chats
- Picking among a character's older chats in the app (SillyTavern's chat list stays in Advanced)
- Notifications, the heartbeat to the Bridge, anything that generates without the user (Phase 7)

## Tests

- Unit tests:
    - `ChatViewModel`: saved style applied on enter, story when nothing saved, `toggleChatStyle`, `chatFile` passed to the open
    - `ChatStylePreferences`: per-chat read and write; the texting template's default and saved value
    - `BridgeScript`: `editMessage`, `deleteMessage`; `setChatStyle` with and without an instruction (quotes, newlines, macros kept as written), `setAppState`, `reloadChat`, `selectCharacter` with a chat file (argument encoding)
    - `BridgeMessageParser`: `messageLongPressed` (valid, missing or malformed fields); `chatOpened` with and without `chatFile`; `micPressed` with `"text"` is an unknown target
    - `CharacterRepositoryImpl.openChat()`: matches on character and chat file; another chat of the same character doesn't count
    - Return handling: reload when Chat is showing, wait for a running generation, stale mark when it isn't, Chats list refreshed; no reload for a configuration change
    - Navigation: the route and deep link parse `characterId` and an optional `chatFile` (including names with spaces)
- Compose UI tests (`ChatContent`): the ⋮ menu shows the right style item and calls back; the message sheet shows Edit and Delete, both disabled while generating; Delete asks for confirmation before calling back
- `ChatViewModel`: long press opens the sheet, Delete confirms then calls the bridge, a failed or stale action shows the message and re-reads the chat
- Version bump per `CLAUDE.md`

## Verify on Device

- [ ] Texting and story styles both render, switch instantly with no reload, and keep the scroll position
- [ ] Each chat remembers its own style across app restarts, and after a page reload (look and instruction)
- [ ] In a texting chat, replies (including swipes and regenerations) come as short texts; in a story chat, normal roleplay replies
- [ ] The instruction never appears in the chat file, the chat's metadata or SillyTavern's settings, and the desktop's replies in the same chat stay normal roleplay
- [ ] Switching to story, to another chat, or leaving Chat removes the instruction (the next reply is normal)
- [ ] Texting style: long press on a user and on a character bubble → Edit changes the text in place (nothing regenerates); Delete asks first, then removes the message, including one in the middle of the chat; both show the same on the desktop after a reload
- [ ] Long press doesn't fire while scrolling, and text selection and swipe arrows still work; the actions are disabled while a reply is generating
- [ ] Swipe, edit and the message buttons work in both styles; sending works with the keyboard pushing the input up
- [ ] A message added from a desktop tab while the app is in the background shows after returning to the app, with the view at the newest message
- [ ] Same, but leaving from the Chats tab and then opening that character: the new message is there, and the Chats list shows it too
- [ ] Returning while a reply is generating doesn't interrupt it; the chat is re-read after it finishes
- [ ] Rotation doesn't reload the chat or send `setAppState`
- [ ] A Chats list row opens that exact chat, including for a character with several chats while another of its chats is open
- [ ] `adb shell am start -d "travelingtavern://chat/<id>?chatFile=<file>"` opens that chat, both from a cold start and while the app is running (one Activity, no page reload); Back goes to Chats
- [ ] In a desktop browser (no app), the bridge hooks SillyTavern's events and `__ttBridge.setChatStyle`, `setAppState` and `reloadChat` run without errors

## Notes

### Decision (2026-11-18, user): the main chat is the only conversation

The previous 1.7 (EchoText thread as the chat, main chat as Story view) is dropped, together with 1.7c (Chats list previews from EchoText threads). Its EchoText findings and the thread storage investigation are in Git history: this file before this change (`dev` at `9fd0aa0`). Why it was dropped and what replaces it: `project-schema.md` → One Conversation and Phase 7.

### Decision (2026-11-19, user): texting and story are app-only (option C)

Texting changes how the character writes, not only the look. The instruction is added only by the app, in its own page, and never saved in SillyTavern, so SillyTavern on the desktop keeps working exactly as before. Considered and rejected: a saved `/inject` in the chat's metadata (option A), which would make desktop replies in that chat texting-style with nothing on the desktop showing why; and an `[OOC: …]` prefix on the user's message, which would be saved in the chat and summarized by Memory Books.

### Review of this spec against the code (2026-11-19)

A review against `dev` found the early `if (!port) return;` in `bridge.js`, the unused `setMode` / `tt-mode-*`, the mic's leftover `"text"` target, the character-only "already showing" check and `chatOpened`, Activity-level lifecycle events firing on rotation, no stale handling when returning through the Chats list, state lost on a page reload, the deep link's launch mode, and reloading during a generation. All are written into the sections above.

### From 1.6

`ChatScreen` / `ChatViewModel` already exist (top bar with back, avatar and name). The WebView is hosted by the app shell, not by this screen; Chat is transparent below its bar. `CharacterRepository.openChat()` returns once SillyTavern posts `chatOpened` (chat drawn and settled) and Chat covers the page until then. The shell keeps the WebView at a fixed size below `ChatChromeHeight`; 1.7 adds no chrome below the app bar, so that height is unchanged.

## References

- @context/coding-standards.md
- @context/ui-guidelines.md
- @context/project-schema.md (One Conversation; Built Toward the Final Phase)
- @context/specs/phase1-5-clean-theme-spec.md
- @context/specs/phase1-6-home-character-list-spec.md
- @context/specs/phase1-7b-voice-input-spec.md
