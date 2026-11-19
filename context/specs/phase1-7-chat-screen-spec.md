# Phase 1.7 — Chat Screen (Story Chat)

## Overview

Chat shows the character's SillyTavern **main chat**, the only conversation per character (decision in `project-schema.md` → One Conversation), in a clean layout made for a phone: message cards, a tap on a message for its actions, and a simple input bar. Everything runs on SillyTavern's own features and code; the app only changes how they look and how you reach them.

1.7 also lays the groundwork the final phase (Phase 8, proactive messages and reminders) builds on: fresh chats when the app comes back, lifecycle events, opening a specific chat, and cleanup of the earlier EchoText plan.

This is the **story** style. A second, texting style comes in Phase 2 (`phase2-texting-style-spec.md`) and builds on this layout.

**The actions are SillyTavern's.** Every action, menu item and option maps to something SillyTavern already does. Don't add features SillyTavern doesn't have.

## Requirements

### Investigation first (do this before writing code)

- Message elements and attributes: message block, text, name, timestamp, user vs character (e.g. `.mes[is_user="true"]`), the message index (e.g. `mesid`), the swipe arrows and counter, the inline editor and its confirm / cancel buttons **(verify)**
- How SillyTavern runs each message action from script, preferring whatever its own buttons call: copy, edit, delete (inside edit mode), create branch, hide / unhide from the prompt, regenerate, continue, impersonate, start a new chat **(verify)**
- How to tell a generation is running: SillyTavern's generation started / ended / stopped events, or a flag such as `is_send_press` **(verify)**
- How to re-read the open chat from script without reloading the page, keeping the view at the newest message: `reloadCurrentChat()` or equivalent **(verify)**
- The open chat's file name from `getContext()` (e.g. `chatId` / `getCurrentChatId()`), and how to open a given chat file (e.g. `openCharacterChat(fileName)`) **(verify)**
- Whether `/api/chats/recent` (used by 1.6) returns each row's chat file name **(verify)**
- Personas: how to list them with their names and avatars, which one is active for this chat, and how SillyTavern switches persona (its own persona selection, or the `/persona` command) **(verify)**
- Attaching a file: what SillyTavern's own "Attach a file" control calls **(verify)**
- Write the findings into Notes and add every name to `st-selectors.js`

### Cleanup from the earlier plans

- **`bridge.js` runs without the app.** Today it ends with `if (!port) return;`, so in a desktop browser or a headless page it never hooks SillyTavern's events. Remove the early return: everything runs in any page, and only `post()` does nothing when the `TravelingTavern` port is missing
- **Delete `setMode` / `tt-mode-*`** (`bridge.js`, `BridgeScript`, `BridgeClient.setMode`; nothing calls them). Phase 2 adds `setChatStyle` / `tt-style-*`
- **Remove the mic's `"text"` target:** `ChatInputTarget.Text`, the `"text"` wire name in `BridgeMessageParser`, and the `bridge.js` comment "'text' (EchoText) comes with 1.7". A `micPressed` with `"text"` then parses as an unknown target (logged and ignored)

### Look (CSS in `clean-theme.css`, under `body.tt-clean`)

- **Theme colors reach the page.** The page can't read the app's Compose theme, so `BridgeInstaller` sends the current colors (surface, on-surface, secondary text, the user-message color, accent; light or dark) as CSS variables (`--tt-*`) after every page load and whenever the app's theme changes. The CSS uses only those variables
- **Background:** SillyTavern's own chat background stays (whatever is set in SillyTavern), under a dark scrim so text stays readable. No background set → the theme's background
- **Messages as cards:**
    - Character messages on the left, user messages on the right, each at most about 85% of the width, 20dp rounded corners, 16dp inner padding, 12dp between messages
    - Character cards use the theme's surface color; user cards use the user-message color (a distinct tonal color from the theme; the amber container is one option — decide in spec review, within `ui-guidelines.md`)
    - The sender's name sits under each card in small secondary text, left under the character's and right under the user's
    - Hidden: per-message avatars, the header timestamp, message numbers, token counts and timers. The time is in the message actions sheet
- **Text:** narration (SillyTavern's `*asterisks*`, rendered as `<em>`) in italic secondary color; dialogue and plain text in the primary text color; message text at least 16px as in 1.5
- **Swipes** stay on the character's latest message as SillyTavern shows them (arrows and counter), with 48 × 48 touch targets, plus SillyTavern's own swipe gesture
- **While a reply is starting:** a small "…" card on the character's side until the first text arrives (with streaming on, the text then appears live)
- **Scroll to newest:** when scrolled up, a round button with a down chevron sits above the input bar, bottom right; tap scrolls to the newest message. Hidden at the bottom
- Light and dark both work; nothing outside `body.tt-clean` changes, so Advanced (1.8) and the desktop are unaffected

### Top app bar (native, from 1.6)

- Back, character avatar and name, as now
- ⋮ overflow menu with **Start new chat** (SillyTavern's own new chat for this character, after a confirmation sheet; the new chat opens). Phase 2 adds the style switch here

### Message actions

- A **tap** on a message opens its actions. Long press is left alone, so Android's text selection keeps working. Taps on links, images, swipe arrows and inside an open editor do what they normally do
- **Sheet (native, `ModalBottomSheet`):** the rest of the screen is dimmed and the tapped message is scrolled into view above the sheet. At the top: the message's date and time (from `send_date`) and "Sent by {name}". Below: a grid of actions, four per row, each an icon with a short label
- **Actions** (SillyTavern's own; only the ones that apply are shown):

| Action | Shown on | What runs |
|---|---|---|
| Regenerate | The character's latest message | SillyTavern's regenerate |
| Continue | The character's latest message | SillyTavern's continue |
| Copy | Every message | Copies the message text (as SillyTavern's copy button does) |
| Edit | Every message | SillyTavern's inline editor opens in the card, with its own confirm and cancel kept visible. Editing never regenerates |
| Branch | Every message | SillyTavern's "create branch": a new chat up to this message, which then opens |
| Hide from AI / Show to AI | Every message | SillyTavern's hide / unhide (excluded from the prompt, still shown, marked as hidden) |
| Delete | Every message | Confirmation first (the shared `ConfirmDeleteSheet`, "Delete this message?"), then SillyTavern deletes it. Any message, not only the last |

- Extension buttons (translate, narrate, generate image) are not in the sheet in 1.7; they stay in Advanced
- **While a reply is generating:** everything except Copy is disabled
- **Bridge:**
    - `bridge.js` posts `messageTapped { messageId, sendDate, isUser, isLatest, isHidden, name }`
    - `messageAction(messageId, sendDate, action)` runs an action and answers with a request ID and success or error, like the lorebook commands
    - **Stale check:** a message's index moves when anything earlier is deleted (for example on the desktop). Before acting, the bridge checks that the message at `messageId` still has that `sendDate`; if not, it answers `stale`, the app shows "This message has changed. Try again." and re-reads the chat
    - `generationState { running }` is posted when a generation starts and when it ends or stops, so Kotlin knows when to disable actions

### Input bar (SillyTavern's own input, restyled)

- A rounded panel at the bottom: the message field on top (SillyTavern's own `#send_textarea`, placeholder "Message"), and a row of controls below it:
    - **+** (left): SillyTavern's "Attach a file"
    - **Persona chip** ("Chatting as {persona} ▾"): shows the active persona; a tap opens a native sheet listing SillyTavern's personas (avatar and name); picking one switches persona the way SillyTavern does
    - **Impersonate** (wand icon): SillyTavern's impersonate, which writes a message for you into the field to edit or send
    - **Mic / Send** (right): the 1.7b mic while the field is empty, the send button once there's text; SillyTavern's stop button replaces both while a reply is generating
- SillyTavern's own left-side menu buttons in the input row are hidden in clean mode; everything they offered that the app needs is above or in the message actions
- The keyboard pushes the whole panel up (IME insets), as now
- The 1.7b mic keeps working exactly as before; only its position changes

### Foreground and background

- Use **`ProcessLifecycleOwner`** (the whole app going to the background and back), not the Activity's `ON_START` / `ON_STOP`. The Activity is recreated on rotation, so its events would treat every rotation as leaving and coming back
- On each change, the app shell sends `setAppState(foreground)` to the page. `bridge.js` keeps it, together with the open character and chat file (from `chatChanged`), in one object, `appState`. Nothing reads it in 1.7; Phase 8 uses it as the heartbeat

### Fresh chats on return

When the app comes back to the foreground:

- **Chat is showing:** re-read the open chat (`reloadChat()`), staying at the newest message. If a generation is running, wait until it ends, then re-read. No cover: the chat updates in place. If the reload fails, keep the chat as it is and log a warning (no message data)
- **Chat isn't showing:** mark the page's open chat **stale**. The next time Chat opens that same chat, it is re-read before the cover lifts, instead of being shown as it is
- **Always:** refresh the Chats list (`requestRecentChats`)
- Rotation, and switching screens or tabs inside the app, are not a return

### After a page reload

- `BridgeInstaller` already re-sends clean mode after every page load. It also re-sends the current app state and the theme colors (Look, above)
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

- Register a deep link for the Chat route: `travelingtavern://chat/{characterId}?chatFile={chatFile}`, so Phase 8's notification tap only has to build that URI
- `MainActivity` becomes `launchMode="singleTop"` and handles `onNewIntent` (passed to the NavController), so a link opened while the app is running reuses the one Activity and the one WebView instead of starting a second Activity
- **No server saved:** go to Connect and drop the link
- **Back** from a chat opened by link goes to the Chats tab, whatever the start destination was

### ViewModel (`ui/chat/ChatViewModel.kt`)

- Takes `characterId` and optional `chatFile` from the nav arguments
- Holds the message actions state: the tapped message, its actions, whether a generation is running, the delete confirmation, stale and error messages
- Persona sheet state: the list and the active persona

### Out of scope

- Texting style (Phase 2)
- Group chats
- Picking among a character's older chats in the app (SillyTavern's chat list stays in Advanced)
- Extension message buttons (translate, narrate, generate image) in the actions sheet
- Notifications, the heartbeat to the Bridge, anything that generates without the user (Phase 8)

## Tests

- Unit tests:
    - `ChatViewModel`: `chatFile` passed to the open; tap → actions for that message (latest character message vs others); actions disabled while generating; Delete asks first, then calls the bridge; a stale answer shows the message and re-reads the chat; persona list and switch
    - `BridgeMessageParser`: `messageTapped`, `generationState`, `chatOpened` with and without `chatFile` (valid, missing, malformed); `micPressed` with `"text"` is an unknown target
    - `BridgeScript`: `messageAction`, `setAppState`, `reloadChat`, the theme variables, `selectCharacter` with a chat file (argument encoding)
    - `CharacterRepositoryImpl.openChat()`: matches on character and chat file; another chat of the same character doesn't count
    - Return handling: reload when Chat is showing, wait for a running generation, stale mark when it isn't, Chats list refreshed; no reload for a configuration change
    - Navigation: the route and deep link parse `characterId` and an optional `chatFile` (including names with spaces)
- Compose UI tests (`ChatContent` and the sheets): the actions grid shows the right actions for the latest character message and for other messages; all but Copy disabled while generating; Delete asks for confirmation before calling back; the persona sheet lists personas and calls back; the ⋮ menu's Start new chat asks first
- Version bump per `CLAUDE.md`

## Verify on Device

- [ ] Character and user cards on the correct sides, names under them, narration in italic secondary color, no avatars or message numbers; light and dark
- [ ] SillyTavern's background shows behind the chat and text stays readable
- [ ] Tap on a message opens the sheet with its time and sender; the latest character message shows Regenerate and Continue, others don't; long press still selects text; links and swipe arrows still work
- [ ] Each action does what SillyTavern's own button does, and the desktop shows the same result after a reload: copy, edit (nothing regenerates), branch, hide / show, delete (including a message in the middle), regenerate, continue
- [ ] Actions are disabled while a reply is generating; a message changed on the desktop gives "This message has changed" instead of acting on the wrong one
- [ ] Input bar: + attaches a file; the persona chip lists personas and switches; impersonate fills the field; the mic shows when empty and send when there's text; stop shows while generating; the keyboard pushes the panel up
- [ ] "…" shows before a reply starts; the scroll-to-newest button appears when scrolled up and works
- [ ] Advanced and the desktop look unchanged
- [ ] A message added from a desktop tab while the app is in the background shows after returning to the app, with the view at the newest message
- [ ] Same, but leaving from the Chats tab and then opening that character: the new message is there, and the Chats list shows it too
- [ ] Returning while a reply is generating doesn't interrupt it; the chat is re-read after it finishes
- [ ] Rotation doesn't reload the chat or send `setAppState`
- [ ] A Chats list row opens that exact chat, including for a character with several chats while another of its chats is open
- [ ] `adb shell am start -d "travelingtavern://chat/<id>?chatFile=<file>"` opens that chat, both from a cold start and while the app is running (one Activity, no page reload); Back goes to Chats
- [ ] In a desktop browser (no app), the bridge hooks SillyTavern's events and `__ttBridge.setAppState`, `reloadChat` and `messageAction` run without errors

## Notes

### Decision (2026-11-18, user): the main chat is the only conversation

The previous 1.7 (EchoText thread as the chat, main chat as Story view) is dropped, together with 1.7c (Chats list previews from EchoText threads). Its EchoText findings and the thread storage investigation are in Git history: this file before this change (`dev` at `9fd0aa0`). Why it was dropped and what replaces it: `project-schema.md` → One Conversation and Phase 8.

### Decision (2026-11-19, user): story first, texting later

1.7 is the story chat only. Texting style moved to its own spec and phase (Phase 2) so 1.7 delivers a clean, reliable chat first. Message actions open with a tap, not a long press, which also leaves text selection alone.

### Review of this spec against the code (2026-11-19)

A review against `dev` found the early `if (!port) return;` in `bridge.js`, the unused `setMode` / `tt-mode-*`, the mic's leftover `"text"` target, the character-only "already showing" check and `chatOpened`, Activity-level lifecycle events firing on rotation, no stale handling when returning through the Chats list, state lost on a page reload, the deep link's launch mode, reloading during a generation, no generation state for Kotlin, and message indexes moving when the desktop deletes a message. All are written into the sections above.

### From 1.6

`ChatScreen` / `ChatViewModel` already exist (top bar with back, avatar and name). The WebView is hosted by the app shell, not by this screen; Chat is transparent below its bar. `CharacterRepository.openChat()` returns once SillyTavern posts `chatOpened` (chat drawn and settled) and Chat covers the page until then. The shell keeps the WebView at a fixed size below `ChatChromeHeight`; 1.7 adds no native chrome below the app bar (the input bar is in the page), so that height is unchanged.

## References

- @context/coding-standards.md
- @context/ui-guidelines.md
- @context/project-schema.md (One Conversation; Built Toward the Final Phase)
- @context/specs/phase1-5-clean-theme-spec.md
- @context/specs/phase1-6-home-character-list-spec.md
- @context/specs/phase1-7b-voice-input-spec.md
- @context/specs/phase2-texting-style-spec.md
