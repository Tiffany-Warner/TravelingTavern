# Phase 1.6c — Chats Tab: Hide and Delete

## Overview

Long press a chat in the Chats tab to hide it from the list or delete it. A ⋮ menu in the Chats top bar brings hidden chats back.

- **Delete** removes the chat from SillyTavern (the same as deleting it in SillyTavern's chat list), after the same delete-chat alert dialog as Restart chat (1.7)
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

- Delete → `ConfirmDeleteChatDialog` (shared alert dialog with a red delete icon, also used by Restart chat; a dialog, not a sheet, so deleting a whole chat can't be mistaken for Start new chat — user decision 2026-11-19): "Delete this chat?", then in bold red "This also deletes the chat on your SillyTavern.", then "Every message in it is gone for good. This can't be undone.", with a red Delete button. No line about memories (user decision 2026-11-19): Memory Books' lorebook file stays, but in its default Automatic mode the lorebook is bound in the chat file's metadata, so after the delete no chat uses it; "memories stay" would mislead
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
- Compose UI (`ChatsContentTest`): long press opens the sheet, Hide / Delete call the right callbacks, the confirmation dialog's buttons, ⋮ shown only with hidden chats, Delete disabled while that chat generates

## Verify on Device

- [x] Long press opens the sheet; tap still opens the chat
- [x] Hide removes the row; Undo brings it back; it stays hidden after restarting the app
- [x] ⋮ appears only with hidden chats; Show hidden chats brings them all back and the ⋮ goes away
- [x] Delete asks first; Cancel keeps the chat; Delete removes it from the app and from SillyTavern on the desktop
- [x] Deleting the chat that's open in the page, then opening that character, works (no blank page, no stale messages)
- [ ] Deleting a character's only chat, then opening the character, works
- [ ] Server unreachable (Tailscale off): Delete shows the snackbar and the row stays

## Notes

### Investigation findings (SillyTavern 1.19.0 source, 2026-11-19)

- **Page-side delete:** `deleteCharacterChatByName(characterId, fileName)` in `script.js` (used by the welcome screen's recent chats). Exported but **not on `getContext()`**: import `/script.js` by absolute URL, like `doNewChat`. `characterId` is the index in `characters` (as a string); `fileName` has no `.jsonl`. It calls `unshallowCharacter` first (lazy-loaded characters)
- **Server call:** `POST /api/chats/delete` `{ chatfile: '<name>.jsonl', avatar_url }`. 400 if the file isn't there (already deleted elsewhere) or the path escapes the chats folder
- **No result:** the function returns nothing either way and only `console.error`s a failed delete. Success is the `CHAT_DELETED` event (`event_types.CHAT_DELETED`, argument = file name without `.jsonl`), emitted only after the server said OK. The bridge listens for it once, matched by file name, to answer the app; a missing character also gives no event → failure
- **Character's current chat deleted:** it re-reads the character's chats and points `character.chat` at the newest one left, or at a new name `<name> - <date>` if none left (saved via `/api/characters/merge-attributes`). Opening that character later loads that chat; a name with no file gives a fresh chat with the greeting (`getChat` → empty → greeting), so **deleting a character's only chat works** with no extra code. **User decision (2026-11-19):** that fresh greeting-only chat is fine and stays; the user can hide it if they don't want it in the list
- **Chat open in the page deleted:** `deleteCharacterChatByName` does **not** touch the open page: the deleted messages stay in memory, and a later save would write them under the character's *new* current chat name (overwriting the newest remaining chat). SillyTavern's own Manage chats path (`delChat`, not exported) handles this by calling `replaceCurrentChat()` (exported): `clearChat`, pick the newest remaining chat or a new one, `getChat`, which emits `CHAT_CHANGED`. So when the deleted chat is the one open (`this_chid` + `characters[this_chid].chat`), the bridge calls `replaceCurrentChat()` after the delete, matching what deleting in SillyTavern does. The bridge's existing `CHAT_CHANGED` hook then posts `chatChanged`
- **Events:** `CHAT_DELETED` fires on success (also used by itemized prompts). The app still re-asks `recentChats` afterwards, since the event doesn't carry the new list
- **Hidden entries for chats that no longer exist** are ignored rather than deleted from storage: they hide nothing and don't count towards the ⋮. The app only sees SillyTavern's 100 most recent chats (`recentChatsMax`), so "missing from the list" can't tell a deleted chat from an old one, and deleting the entry would unhide an old chat. A chat deleted in the app is removed from the hidden list
- **Delete answer:** reuses the `chatResult` message (`ok`, `error` = `busy` / `failed`); no new message type
- **(live, confirmed 2026-11-19)** Open-chat delete: `chat_deleted` → page moved to the newest chat left (`CHAT_CHANGED`) → `chatChanged` reached the app → `chatResult ok` → `recentChats`. The remaining chat's file was re-saved (newer time in the list, same content), as SillyTavern's own Manage chats delete does. Opening the character afterwards showed that chat, no blank page

- Wording "Show hidden chats" chosen over "Refresh all chats" (the user's suggestion, "or similar"): it says what the item does. Change if the user prefers

## References

- @context/specs/phase1-6-home-character-list-spec.md (Chats list, one row per chat)
- @context/specs/phase1-7-chat-screen-spec.md (Restart chat confirmation, `doNewChat` import pattern)
- @context/ui-guidelines.md (sheets over dialogs, lists)
- @context/project-schema.md (Built Toward the Final Phase: SillyTavern's IDs, no chat copies)
