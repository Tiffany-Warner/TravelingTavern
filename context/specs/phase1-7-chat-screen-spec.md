# Phase 1.7 — Chat Screen (Story Chat)

## Overview

Chat shows the character's SillyTavern **main chat**, the only conversation per character (decision in `project-schema.md` → One Conversation), in a clean layout made for a phone: message cards, a tap on a message for its actions, and a native input bar. Everything runs on SillyTavern's own features and code; the app only changes how they look and how you reach them.

1.7 also lays the groundwork the final phase (Phase 8, proactive messages and reminders) builds on: fresh chats when the app comes back, lifecycle events, opening a specific chat, and cleanup of the earlier plans (EchoText, the in-app mic).

This is the **story** style. A second, texting style comes in Phase 2 (`phase2-texting-style-spec.md`) and builds on this layout.

**The actions are SillyTavern's.** Every action, menu item and option maps to something SillyTavern already does. Don't add features SillyTavern doesn't have. SillyTavern's wand (extensions) menu and options (hamburger) menu are not in the app at all; they stay in Advanced, and features from them come back one at a time later.

## Requirements

### Investigation first (do this before writing code)

- Message elements and attributes: message block, text, name, timestamp, user vs character (e.g. `.mes[is_user="true"]`), the message index (e.g. `mesid`), the swipe arrows and counter, the inline editor and its confirm / cancel buttons **(verify)**
- How SillyTavern runs each message action from script, preferring whatever its own buttons call: copy, edit, delete (inside edit mode), create branch, hide / unhide from the prompt, regenerate, continue, impersonate, stop, start a new chat **(verify)**
- **Regenerate as a new swipe:** on a character message, the call that adds a new swipe (what SillyTavern's right swipe arrow runs on the last reply), and what SillyTavern does when that generation fails or is stopped: does the previous reply stay as the shown swipe? **(verify)**. On a user message, SillyTavern's Regenerate asks the character to reply **(verify)**
- **Swipes:** how to stop SillyTavern from generating on the last reply: its right-arrow handler and its swipe gesture (the `gestures` setting / its touch handler) **(verify)**
- How to tell a generation is running: SillyTavern's generation started / ended / stopped events, or a flag such as `is_send_press` **(verify)**. Whether its typing indicator (`#typing_indicator` setting) or a streaming placeholder already shows something before the first text **(verify)**
- **Sending:** putting text into `#send_textarea` and running SillyTavern's own send (the function its send button calls) while the input area is hidden; a typed `/command` is then handled by SillyTavern as if typed there **(verify)**
- **Impersonate:** how to detect that it finished and read its text from `#send_textarea` (e.g. an `IMPERSONATE_READY` event, or generation ended with the textarea changed), and what it does with text already in the textarea **(verify)**
- SillyTavern's whole input area and what extensions attach to it (`#send_form`, Quick Replies' bar), so clean mode can hide all of it **(verify)**
- How to re-read the open chat from script without reloading the page, keeping the view at the newest message: `reloadCurrentChat()` or equivalent **(verify)**
- The open chat's file name from `getContext()` (e.g. `chatId` / `getCurrentChatId()`), and how to open a given chat file (e.g. `openCharacterChat(fileName)`) **(verify)**
- Whether `/api/chats/recent` (used by 1.6) returns each row's chat file name **(verify)**
- Write the findings into Notes and add every name to `st-selectors.js`

### Cleanup from the earlier plans

- **`bridge.js` runs without the app.** Today it ends with `if (!port) return;`, so in a desktop browser or a headless page it never hooks SillyTavern's events. Remove the early return: everything runs in any page, and only `post()` does nothing when the `TravelingTavern` port is missing
- **Delete `setMode` / `tt-mode-*`** (`bridge.js`, `BridgeScript`, `BridgeClient.setMode`; nothing calls them). Phase 2 adds `setChatStyle` / `tt-style-*`
- **1.5 input-row rules:** the 48 × 48 sizing of SillyTavern's input-row buttons becomes unused once the input area is hidden; remove it
- **Remove the 1.7b in-app mic; voice typing comes from the keyboard** (decision 2026-11-19, user). Android keyboards (for example Gboard) already have a voice-typing mic, and it works in the native input bar like typing does. In chat apps an in-app mic usually means sending a voice note, so the app's own speech-to-text button goes. Remove, with their tests:
    - **Page side:** `#tt-mic`, `micPressed` and `setVoiceInputAvailable` / `beginVoiceInput` / `updateVoiceInput` / `endVoiceInput` in `bridge.js`; the mic names in `st-selectors.js` (`micButtonClasses`, the `#microphone_button` and `#tt-mic` comments); in `clean-theme.css`, the `#tt-mic` and `.tt-mic-listening` rules **and** the rule hiding the Speech Recognition extension's `#microphone_button` (dead once SillyTavern's input area is hidden), with their header comments
    - **Bridge (Kotlin):** `BridgeClient.setVoiceInputAvailable` / `beginVoiceInput` / `updateVoiceInput` / `endVoiceInput`, `BridgeMessage.MicPressed` and its `"micPressed"` case in `BridgeMessageParser`, and in `BridgeInstaller` the mic availability push (`setVoiceInputAvailable` after each page load) and its `SpeechInputRepository` dependency
    - **Files:** `data/speech/OnDeviceSpeechInput.kt`, `data/speech/AndroidMicrophonePermission.kt`, `data/repository/ChatInputRepositoryImpl.kt`, `domain/model/Speech.kt` (`SpeechEvent`, `SpeechError`, `VoiceSessionEvent`, `ChatInputTarget`), `domain/repository/SpeechInputRepository.kt`, `domain/repository/ChatInputRepository.kt` (with `MicrophonePermission`), `domain/usecase/IsVoiceInputAvailableUseCase.kt`, `domain/usecase/ListenForSpeechUseCase.kt`
    - **Hilt:** in `di/DataModule.kt`, `bindSpeechInputRepository`, `bindMicrophonePermission` and `bindChatInputRepository`, with their imports
    - **Chat screen:** in `ChatViewModel`, `VoiceInputState`, `VoiceMessage`, the `voiceInput` / `voiceMessage` state, `permissionRequests`, `onMicPermissionResult`, `stopListening` and the `micPresses` collection; in `ChatScreen`, the `RECORD_AUDIO` launcher (`rememberLauncherForActivityResult`), the `ON_STOP` → `stopListening()` effect, `VoiceMessageEffect` and its Settings snackbar action (`appDetailsSettings`), and the `chat_voice_*` strings
    - **Tests:** `ListenForSpeechUseCaseTest`, `FakeSpeechInputRepository`, `FakeChatInputRepository`, the voice tests in `ChatViewModelTest`, the voice snackbar tests in `ChatContentTest`, and the mic cases in `BridgeMessageParserTest` and `BridgeScriptTest`
    - **Keep:** the `RECORD_AUDIO` permission in the manifest and the WebView's permission handling (SillyTavern's own extensions in Advanced and later features such as calls may ask for the mic)
    - Ask before deleting files, per `ai-guidelines.md`; list them in the history entry
    - A `micPressed` from an old page then parses as an unknown type (logged and ignored)

### Look (CSS in `clean-theme.css`, under `body.tt-clean`)

- **Theme colors reach the page.** The page can't read the app's Compose theme. The app shell writes the current colors (surface, on-surface, secondary text, primary container and on-primary-container for user cards, accent; light or dark) into a small store, `PageThemeStore` (like `PageModeStore`), whenever the theme changes. `BridgeInstaller` reads it and sends the colors as CSS variables (`--tt-*`) after every page load, and the store's changes are sent while the page is loaded. The CSS uses only those variables
- **Background:** SillyTavern's own chat background stays (whatever is set in SillyTavern), under a dark scrim so text stays readable. No background set → the theme's background
- **Messages as cards:**
    - Character messages on the left, user messages on the right, each at most about 85% of the width, 20dp rounded corners, 16dp inner padding, 12dp between messages
    - Character cards use the theme's surface color; user cards use the theme's amber container color (`primaryContainer`, with `onPrimaryContainer` text; user decision, 2026-11-19), so your messages stand apart in both light and dark
    - The sender's name sits under each card in small secondary text, left under the character's and right under the user's
    - Hidden: per-message avatars, the header timestamp, message numbers, token counts, timers and SillyTavern's per-message button row. The time is in the message actions sheet
- **Text:** narration (SillyTavern's `*asterisks*`, rendered as `<em>`) in italic secondary color; dialogue and plain text in the primary text color; message text at least 16px as in 1.5
- **SillyTavern's input area is hidden** (`#send_form` and what extensions add to it, such as Quick Replies' bar). The native input bar replaces it
- **While a reply is starting:** a small "…" card on the character's side until the first text arrives (with streaming on, the text then appears live). Shown only if SillyTavern isn't already showing its own typing indicator or placeholder **(verify)**, so there's never two
- **Scroll to newest:** when scrolled up, a round button with a down chevron sits at the bottom right of the page, just above the native input bar; tap scrolls to the newest message. Hidden at the bottom
- Light and dark both work; nothing outside `body.tt-clean` changes, so Advanced (1.8) and the desktop are unaffected

### Swipes

- Swipes stay on the latest character message as SillyTavern shows them (arrows and counter), with 48 × 48 touch targets, plus SillyTavern's own swipe gesture
- **In clean mode, swipes never generate.** They only move between replies that already exist. On the last existing reply, the right arrow is hidden or inert and the swipe gesture does nothing **(verify how, see Investigation)**
- **Regenerate** in the message sheet is the only way to get a new reply. Advanced keeps SillyTavern's normal swipe behavior

### Top app bar (native, from 1.6)

- Back, character avatar and name, as now
- ⋮ overflow menu with only two items (Phase 2 adds the style switch):
    - **Start new chat:** SillyTavern's own new chat for this character (its greeting starts it). The current chat is kept and stays in SillyTavern's chat list. A confirmation sheet first; then the new chat opens
    - **Restart chat:** the same, but SillyTavern also deletes the current chat, exactly like ticking "Delete the current chat" in SillyTavern's own "Start new chat" popup. A destructive confirmation sheet first ("Restart this chat? This deletes every message in it and can't be undone.", red Restart button). Memories already saved to lorebooks (for example by Memory Books) are not removed
    - Both run SillyTavern's `doNewChat({ deleteCurrentChat })` (`public/script.js`, found 2026-11-19), reached the way its own menu reaches it **(verify how to call it from the bridge)**
    - Both are **disabled while a reply is generating**

### Message actions

- A **tap** on a message opens its actions. Long press is left alone, so Android's text selection keeps working
- A tap while text is selected only clears the selection; it doesn't open the sheet
- Taps on links, images, swipe arrows and inside an open editor do what they normally do
- **Sheet (native, `ModalBottomSheet`):** the rest of the screen is dimmed and the tapped message is scrolled into view above the sheet. At the top: the message's date and time (from `send_date`) and "Sent by {name}". Below: a grid of actions, four per row, each an icon with a short label
- **Actions** (SillyTavern's own; only the ones that apply are shown):

| Action | Shown on | What runs |
|---|---|---|
| Regenerate | The latest message, whoever sent it | On a character message: a new reply added as a new swipe (SillyTavern's swipe generation), so a failed or stopped generation leaves the previous reply in place **(verify, see Investigation)**. On a user message (for example after a failed first reply): SillyTavern's Regenerate, which asks the character to reply |
| Continue | The latest message, if it's the character's | SillyTavern's continue |
| Copy | Every message | Copies the message text (as SillyTavern's copy button does) |
| Edit | Every message | SillyTavern's inline editor opens in the card, with its own confirm and cancel kept visible. Editing never regenerates |
| Branch | Every message | SillyTavern's "create branch": a new chat up to this message, which then opens |
| Hide from AI / Show to AI | Every message | SillyTavern's hide / unhide (excluded from the prompt, still shown, marked as hidden) |
| Delete | Every message | Confirmation first (the shared `ConfirmDeleteSheet`, "Delete this message?"), then SillyTavern deletes it. Any message, not only the last |

- No multi-message delete mode. Deleting is per message (this sheet) or the whole chat (⋮ → Restart chat)
- Extension buttons (translate, narrate, generate image) are not in the sheet; they stay in Advanced
- **While a reply is generating:** everything except Copy is disabled
- **Bridge:**
    - `bridge.js` posts `messageTapped { messageId, sendDate, isUser, isLatest, isHidden, name }`
    - `messageAction(messageId, sendDate, action)` runs an action and answers with a request ID and success or error, like the lorebook commands
    - **Stale check:** a message's index moves when anything earlier is deleted (for example on the desktop). Before acting, the bridge checks that the message at `messageId` still has that `sendDate`; if not, it answers `stale`, the app shows "This message has changed. Try again." and re-reads the chat
    - `generationState { running }` is posted when a generation starts and when it ends or stops (impersonate included), so Kotlin knows when to disable actions and show Stop

### Input bar (native, `ui/chat/components/ChatInputBar.kt`)

A native Compose bar at the bottom of the Chat screen. SillyTavern's own input area is hidden in clean mode (Look, above).

- **Message field** on top (placeholder "Message"), growing with its text up to a few lines, then scrolling
- **Control row** below it:
    - **Left:** the 1.9 persona chip (a native Compose chip). 1.7 leaves the slot empty
    - **Right:** Write for me (`auto_fix_high` icon, content description "Write for me") and Send
    - **While a reply is generating** (impersonate included): Stop replaces Send and runs SillyTavern's own stop; Write for me is disabled
- **Send:** copies the field's text into SillyTavern's `#send_textarea` and runs SillyTavern's own send, then clears the field. A typed `/command` behaves as SillyTavern handles it **(verify)**. Disabled while the field is empty
- **Write for me:** runs SillyTavern's impersonate, which writes a message for you. When it finishes, its text is copied from `#send_textarea` into the native field, to edit or send **(verify how to detect it)**. This is the only sync from the page into the field
- **No in-app mic.** Voice typing is the keyboard's own mic (for example Gboard's), which types into the native field like the keyboard does. Nothing sends until you tap Send
- Not in the app (Advanced only): an options button, SillyTavern's wand (extensions) and options (hamburger) menus and everything in them, including Attach a file and Memory Books' menu (its automatic summaries keep running), slash-command autocomplete, and extension input tools such as Quick Replies
- **Layout:** the WebView shrinks to sit above the bar, so the page's bottom (the newest message, the scroll-to-newest button) is never under it. The keyboard pushes the bar up (IME insets) and the WebView shrinks with it. Resizing never reloads the page; when the chat was at the newest message it stays there **(verify)**

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

- `BridgeInstaller` already re-sends clean mode after every page load. It also re-sends the current app state and the theme colors (from `PageThemeStore`)
- Anything sent before the bridge exists (before the first load finishes) is lost, so state is pushed by the installer after each load instead of relying on earlier calls
- Kotlin treats a page start as "no generation running" until the page says otherwise

### Opening a specific chat

- The Chat route takes `characterId` and an optional `chatFile`
- `selectCharacter(id, chatFile?)`:
    - With `chatFile`: select the character, then open that chat. Without it: SillyTavern's current chat for the character, as in 1.6
    - The "already showing" shortcut compares the character **and** the chat file, so another chat of the same character is never mistaken for the open one
    - `chatOpened` and `chatChanged` carry `characterId` and `chatFile`, and `openChat()` matches both
- The Chats list passes each row's chat file (if the investigation confirms it's available)
- Same `chatOpened` wait and 3 s cap as 1.6
- A `chatFile` that doesn't exist for that character (for example deleted by Restart chat, or on the desktop): open the current chat instead and log a warning

### Following the open chat

Branch, Start new chat and Restart chat change the open chat while the Chat screen stays, so the route's `chatFile` goes out of date.

- The ViewModel opens the route's chat once, then **follows `chatChanged`** for this character
- The chat file it follows is kept in its `SavedStateHandle` and updated on every `chatChanged`, so a restore after process death reopens the chat that was showing, not the route's original one. If that chat no longer exists, the fallback above applies

### Deep link

- Register a deep link for the Chat route: `travelingtavern://chat/{characterId}?chatFile={chatFile}`, so Phase 8's notification tap only has to build that URI
- `MainActivity` becomes `launchMode="singleTop"` and handles `onNewIntent` (passed to the NavController), so a link opened while the app is running reuses the one Activity and the one WebView instead of starting a second Activity
- **No server saved:** go to Connect and drop the link
- **Back** from a chat opened by link goes to the Chats tab, whatever the start destination was

### ViewModel (`ui/chat/ChatViewModel.kt`)

- Takes `characterId` and optional `chatFile` from the nav arguments; keeps the followed chat file in `SavedStateHandle` (Following the open chat)
- Holds the input bar state: field text, whether Send is enabled, whether a generation is running (Stop, Write for me disabled)
- Holds the message actions state: the tapped message, its actions, the delete confirmation, stale and error messages
- Holds the ⋮ menu's confirmation state (start new / restart), disabled while generating

### Out of scope

- Texting style (Phase 2)
- Personas: the persona chip in the input bar and the persona screen come in 1.9 (`phase1-9-personas-spec.md`); 1.7 leaves the chip's slot on the left of the control row
- Anything from SillyTavern's wand (extensions) or options (hamburger) menus, including Attach a file; they stay in Advanced
- An in-app mic of any kind (voice typing is the keyboard's); a possible later voice-note button is not on the roadmap
- Multi-message delete
- Group chats
- Picking among a character's older chats in the app (SillyTavern's chat list stays in Advanced)
- Extension message buttons (translate, narrate, generate image) in the actions sheet
- Notifications, the heartbeat to the Bridge, anything that generates without the user (Phase 8)

## Tests

- Unit tests:
    - `ChatViewModel`:
        - `chatFile` passed to the open; the followed chat file updated on `chatChanged` and restored from `SavedStateHandle` (process death) instead of the route's
        - Tap → actions for that message: Regenerate on the latest message whoever sent it, Continue only on the latest character message; all but Copy disabled while generating; Delete asks first, then calls the bridge; a stale answer shows the message and re-reads the chat
        - Start new chat and Restart chat call SillyTavern with the right `deleteCurrentChat` only after confirming, and are disabled while generating
        - Input bar: Send disabled while the field is empty, sends the field's text and clears it; Write for me's text fills the field; Stop replaces Send and Write for me is disabled while generating
    - `BridgeMessageParser`: `messageTapped`, `generationState`, `chatOpened` and `chatChanged` with and without `chatFile` (valid, missing, malformed); `micPressed` is now an unknown type (logged and ignored)
    - `BridgeScript`: `messageAction`, `sendMessage`, `impersonate`, `stopGeneration`, `setAppState`, `reloadChat`, the theme variables, `selectCharacter` with a chat file (argument encoding: quotes, newlines, emoji, a `/command`)
    - `CharacterRepositoryImpl.openChat()`: matches on character and chat file; another chat of the same character doesn't count
    - Return handling: reload when Chat is showing, wait for a running generation, stale mark when it isn't, Chats list refreshed; no reload for a configuration change
    - Navigation: the route and deep link parse `characterId` and an optional `chatFile` (including names with spaces)
- Compose UI tests:
    - `ChatInputBar`: Send disabled when empty and enabled with text; Stop replaces Send and Write for me is disabled while generating; Write for me has its content description; no mic button
    - Actions sheet: the right actions for the latest character message, the latest user message (Regenerate, no Continue) and other messages; all but Copy disabled while generating; Delete asks for confirmation before calling back
    - ⋮ menu: Start new chat and Restart chat each ask first, Restart's sheet uses the destructive style, both disabled while generating
- Version bump per `CLAUDE.md`

## Verify on Device

- [ ] Character and user cards on the correct sides, names under them, narration in italic secondary color, no avatars, message numbers or per-message buttons; light and dark
- [ ] SillyTavern's background shows behind the chat and text stays readable; SillyTavern's own input area and Quick Replies' bar are hidden
- [ ] Tap on a message opens the sheet with its time and sender; long press still selects text, and a tap while text is selected only clears it; links and swipe arrows still work
- [ ] Each action does what SillyTavern's own button does, and the desktop shows the same result after a reload: copy, edit (nothing regenerates), branch, hide / show, delete (including a message in the middle), regenerate, continue
- [ ] Regenerate on the latest character message adds a new swipe and the previous reply is still reachable; a generation that fails or is stopped leaves the previous reply showing
- [ ] Regenerate after a failed first reply (your message is the latest): the character replies
- [ ] Swipes move between existing replies; on the last one, the right arrow and the swipe gesture don't generate anything. In Advanced, swiping right on the last reply generates as usual
- [ ] Actions and the ⋮ items are disabled while a reply is generating; a message changed on the desktop gives "This message has changed" instead of acting on the wrong one
- [ ] ⋮ Start new chat opens a new chat and the old one is still in SillyTavern's chat list; Restart chat deletes the current chat (gone from the list on the desktop too) and opens a fresh one; both ask first
- [ ] Native input bar: Send is disabled when empty; sending puts the message in the chat as SillyTavern's own send does, and a typed `/command` runs as in SillyTavern; Write for me fills the field; Stop replaces Send while generating and stops the reply
- [ ] Gboard's mic fills the native field, and nothing sends until you tap Send; no in-app mic is shown
- [ ] The keyboard pushes the bar up and the newest message stays visible above it; the WebView never reloads when it resizes
- [ ] "…" shows before a reply starts, and only once; the scroll-to-newest button appears when scrolled up and works
- [ ] Advanced and the desktop look unchanged
- [ ] A message added from a desktop tab while the app is in the background shows after returning to the app, with the view at the newest message
- [ ] Same, but leaving from the Chats tab and then opening that character: the new message is there, and the Chats list shows it too
- [ ] Returning while a reply is generating doesn't interrupt it; the chat is re-read after it finishes
- [ ] Rotation doesn't reload the chat or send `setAppState`
- [ ] After Branch (or Start new chat), killing the app in the background (`adb shell am kill`) and returning reopens the branch, not the original chat; after Restart chat, the same reopens the fresh chat
- [ ] A Chats list row opens that exact chat, including for a character with several chats while another of its chats is open
- [ ] `adb shell am start -d "travelingtavern://chat/<id>?chatFile=<file>"` opens that chat, both from a cold start and while the app is running (one Activity, no page reload); Back goes to Chats
- [ ] In a desktop browser (no app), the bridge hooks SillyTavern's events and `__ttBridge.setAppState`, `reloadChat` and `messageAction` run without errors

## Notes

### Decision (2026-11-18, user): the main chat is the only conversation

The previous 1.7 (EchoText thread as the chat, main chat as Story view) is dropped, together with 1.7c (Chats list previews from EchoText threads). Its EchoText findings and the thread storage investigation are in Git history: this file before this change (`dev` at `9fd0aa0`). Why it was dropped and what replaces it: `project-schema.md` → One Conversation and Phase 8.

### Decision (2026-11-19, user): story first, texting later

1.7 is the story chat only. Texting style moved to its own spec and phase (Phase 2) so 1.7 delivers a clean, reliable chat first. Message actions open with a tap, not a long press, which also leaves text selection alone.

### Decision (2026-11-19, user): native input bar, no SillyTavern menus, no in-app mic

The input bar is native Compose instead of SillyTavern's restyled input, so the persona chip (1.9) is a real Compose chip. SillyTavern's wand and options menus are left out entirely (Advanced only) and features from them come back one at a time. Voice typing is the keyboard's own mic; the 1.7b in-app mic is removed. Swipes never generate in clean mode; Regenerate (a new swipe) is the one way to a new reply, offered on the latest message whoever sent it so a failed first reply can be retried.

### Review of this spec against the code (2026-11-19)

A review against `dev` found the early `if (!port) return;` in `bridge.js`, the unused `setMode` / `tt-mode-*`, the mic's leftover `"text"` target (now moot: the whole in-app mic is removed), the character-only "already showing" check and `chatOpened`, Activity-level lifecycle events firing on rotation, no stale handling when returning through the Chats list, state lost on a page reload, the deep link's launch mode, reloading during a generation, no generation state for Kotlin, and message indexes moving when the desktop deletes a message. A second pass found no way to retry a failed first reply, the route's chat file going out of date after Branch / Start new chat / Restart chat, the ⋮ items during a generation, and mic-removal pieces missing from the cleanup list (Hilt bindings, the installer's push, the Chat screen's permission and lifecycle wiring, test fakes). All are written into the sections above.

### From 1.6

`ChatScreen` / `ChatViewModel` already exist (top bar with back, avatar and name). The WebView is hosted by the app shell, not by this screen; Chat is transparent below its bar. `CharacterRepository.openChat()` returns once SillyTavern posts `chatOpened` (chat drawn and settled) and Chat covers the page until then. In 1.6 the shell keeps the WebView at a fixed size below `ChatChromeHeight`. 1.7 adds native chrome at the bottom too (the input bar, whose height changes as the field grows and with the keyboard), so the WebView's bottom now follows the top of the input bar; it is resized, never reloaded.

## References

- @context/coding-standards.md
- @context/ui-guidelines.md
- @context/project-schema.md (One Conversation; Built Toward the Final Phase)
- @context/specs/phase1-5-clean-theme-spec.md
- @context/specs/phase1-6-home-character-list-spec.md
- @context/specs/phase1-7b-voice-input-spec.md (replaced; what the cleanup removes)
- @context/specs/phase1-9-personas-spec.md (persona chip)
- @context/specs/phase2-texting-style-spec.md
