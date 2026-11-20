# Phase 1.6c — Chats Tab: Hide and Delete

## Overview

Long press a chat in the Chats tab to hide it from the list or delete it. A ⋮ menu in the Chats top bar brings hidden chats back.

- **Delete** removes the chat from SillyTavern (the same as deleting it in SillyTavern's chat list), after the same destructive confirmation sheet as Restart chat (1.7)
- **Hide** only removes the row from the app's Chats list. Nothing changes in SillyTavern
- Requested by the user, 2026-11-19

## Requirements

### Investigation first (do this before writing code)

Confirm each **(verify)** against SillyTavern 1.19.0 (source + live page via `chrome://inspect`) and record findings in Notes.

- **(verify)** How SillyTavern deletes a chat that isn't open: the page-side function its chat list (Manage chats) uses (for example `deleteCharacterChatByName` or similar in `script.js`), whether it's on `getContext()` or must be imported by absolute URL like `doNewChat` (1.7), and the server call under it (`POST /api/chats/delete` with the chat file and avatar?)
- **(verify)** What SillyTavern does when the deleted chat is the one open in the page (the WebView keeps the last chat open while the Chats tab shows): does it open another chat or a new one, and does it fire `CHAT_CHANGED`?
- **(verify)** What happens when a character's only chat is deleted: does SillyTavern make a fresh chat when the character is next opened?
- **(verify)** Whether deleting a chat also emits an event the bridge can use, or whether the app must re-ask `recentChats` afterwards

### Long press menu

- Long press a row → a bottom sheet (sheets over dialogs, per the UI guidelines) with the chat's avatar, character name and last message time at the top, then two actions: **Hide** and **Delete** (Delete in the error color)
- A tap still opens the chat. Long press gives haptic feedback; the row's `onLongClickLabel` says "Chat options" for accessibility
- Delete is disabled for a chat that is generating a reply right now (the bridge's `generationState` plus the open chat)

### Delete

- Delete → `ConfirmDeleteSheet` (shared component, destructive style, as Restart chat): "Delete this chat? This deletes every message in it and can't be undone." with a red Delete button. Memories already saved to lorebooks (for example by Memory Books) are not removed
- On confirm, the app asks SillyTavern to delete the chat **through the JS bridge** (new `__ttBridge` command, selectors/globals in `st-selectors.js`), keyed by the character's avatar file + chat file name. The app never deletes chat files itself
- The row disappears once SillyTavern confirms; the list is then re-read from SillyTavern (`recentChats`)
- If the deleted chat was open in the page, the page follows whatever SillyTavern does (see investigation); opening that character later shows SillyTavern's current chat
- Failure (server unreachable, chat already gone): the row stays and a snackbar says "Couldn't delete the chat". A chat already deleted elsewhere just drops out on the next refresh
- A deleted chat that was hidden is also removed from the hidden list

### Hide

- Hide removes the row right away. No confirmation (it's undoable): a snackbar "Chat hidden" with **Undo**
- Hidden chats are stored in the app (DataStore), keyed by the character's avatar file + chat file name (SillyTavern's own IDs, per `project-schema.md`). Display only: nothing that affects generation, and no copy of the chat
- A hidden chat stays hidden until shown again from the ⋮ menu. It can still be opened from the Characters tab (that opens SillyTavern's current chat, as now)
- Entries for chats that no longer exist in SillyTavern are dropped when the list is read

### ⋮ menu (Chats top bar)

- A ⋮ at the top right of the Chats tab with one item: **Show hidden chats**, which clears the hidden list so every chat shows again
- Shown only while at least one chat is hidden (no menu with nothing useful in it)

### Architecture

- `ChatsViewModel`: long-pressed chat, confirmation state, one-shot snackbar events (`SharedFlow`), `onHide`, `onUndoHide`, `onDelete`, `onConfirmDelete`, `onShowHiddenChats`
- Hidden list: a small store in `data/settings/` (DataStore Preferences, string set of `avatar/chatFile` keys) behind a domain repository interface; `GetRecentChatsUseCase` filters it out
- Delete: `CharacterRepository` (or the chat-session repository, whichever owns `recentChats`) → `BridgeClient` → `bridge.js`
- Strings in `strings.xml`; no JS strings in ViewModels

### Out of scope

- Renaming chats, exporting, multi-select
- Hiding a whole character
- Showing hidden chats one by one (the ⋮ item shows them all)

## Tests

- Unit: `GetRecentChatsUseCase` filters hidden chats and drops entries for missing chats; `ChatsViewModel` long press → sheet, Hide + Undo, Delete asks first and only deletes on confirm, failure → snackbar, Show hidden chats clears the list, ⋮ only while something is hidden; hidden-chats store round trip
- Bridge: the new command's script is built correctly (`BridgeScriptTest`); its result message parses, including malformed
- Compose UI (`ChatsContentTest`): long press opens the sheet, Hide / Delete call the right callbacks, the confirmation sheet's buttons, ⋮ shown only with hidden chats, Delete disabled while that chat generates

## Verify on Device

- [ ] Long press opens the sheet; tap still opens the chat
- [ ] Hide removes the row; Undo brings it back; it stays hidden after restarting the app
- [ ] ⋮ appears only with hidden chats; Show hidden chats brings them all back and the ⋮ goes away
- [ ] Delete asks first; Cancel keeps the chat; Delete removes it from the app and from SillyTavern on the desktop
- [ ] Deleting the chat that's open in the page, then opening that character, works (no blank page, no stale messages)
- [ ] Deleting a character's only chat, then opening the character, works
- [ ] Server unreachable (Tailscale off): Delete shows the snackbar and the row stays

## Notes

- Wording "Show hidden chats" chosen over "Refresh all chats" (the user's suggestion, "or similar"): it says what the item does. Change if the user prefers

## References

- @context/specs/phase1-6-home-character-list-spec.md (Chats list, one row per chat)
- @context/specs/phase1-7-chat-screen-spec.md (Restart chat confirmation, `doNewChat` import pattern)
- @context/ui-guidelines.md (sheets over dialogs, lists)
- @context/project-schema.md (Built Toward the Final Phase: SillyTavern's IDs, no chat copies)
