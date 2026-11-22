# Phase 1.10 — Create a Character (simple) — `0.1.22`

## Overview

The **+** on Characters opens one native screen: a picture (optional), a name (required), a description and a first message. Save creates the character in SillyTavern with SillyTavern's own create code, through the bridge, then opens the new character's chat. The app never writes a card itself.

Phase 3 grows this same screen into the full step-by-step creator. Import (a card file) is a later feature.

## Requirements

### Entry

- The **+** on Characters (the screen's one primary action, `ui-guidelines.md`) opens the Create a character screen directly. The Create / Import sheet (`AddCharacterSheet`, "Coming soon") is removed; it comes back when Import does
- Full-screen destination: no bottom bar (`ui-guidelines.md` → Navigation)

### Create a character screen (`ui/createcharacter/CreateCharacterScreen.kt`)

- Top app bar: back, title "New character"
- **Picture** (optional): a round 96 dp avatar placeholder with an icon; tap → the system photo picker. Shows the picked picture; tap again to change it, or remove it. Without a picture SillyTavern uses its default avatar
- **Name** (required), single line
- **Description**, multi-line
- **First message**, multi-line: what the character says when the chat opens
- **Save** at the bottom above the keyboard: the screen's one filled button, enabled when the name has a character SillyTavern keeps (Notes: `sanitize-filename`) and nothing is saving or generating
- While saving: Save shows progress, the fields are read-only
- Back with anything typed → a confirm dialog "Discard this character?" (Discard / Keep editing); with nothing typed, back just closes
- Edge-to-edge with IME insets; the fields scroll above the keyboard
- Errors: an inline message above Save, inputs kept ("Couldn't create the character. Try again."): a failed create, a timeout and an unreachable server all show it

### Saving

- Save sends one bridge command, `createCharacter(requestId, fields)`, answered by `characterResult { requestId, ok, avatar?, error? }`, with the 10 s action timeout lorebooks use (`ACTION_TIMEOUT_MS`). A timeout or an unreachable server is a failure (the inline error above)
- `bridge.js` calls SillyTavern's own `/char-create` callback directly, as 1.9 does for personas (`SlashCommandParser.commands['char-create'].callback(args, '')`, not through the parser, so `{{char}}`, quotes and pipes in a field are kept as typed): `name`, `description`, `firstMessage`, `avatar` (the picture as a `data:image/…` URL), `avatarPromptResize=false` (no crop popup), `select=false`. It returns the new avatar key (`<file name>.png`); an empty string means it failed
- **Picture:** shrunk and cropped on the phone before sending: center crop to 2:3 and scaled to 512 × 768 px (SillyTavern's own avatar size, `AVATAR_WIDTH` / `AVATAR_HEIGHT`), as a JPEG. SillyTavern stores an uploaded picture at its own size when no crop is given, so the phone does the crop. Reuses 1.9's `PictureEncoder`, with a new shape **(verify the card PNG looks right in SillyTavern's own character list and grid)**
- After success: Characters refreshes with `requestCharacters(fromServer = false)` (`/char-create` already called `getCharacters()`, so the page's list is current), then the app opens the new character's chat through the existing Chat route (`openChat(avatar, chatFile = null)`). SillyTavern starts the chat with the first message as the greeting, as it does for any character without a chat **(verify)**. Back from that chat goes to Characters, not to the create screen (the create screen is popped)
- **Not while a reply generates:** SillyTavern's own Create button refuses while generating ("Cannot create characters while generating"); `/char-create` doesn't check. Save is disabled while `generating` is true
- The command goes through `BridgeClient.run()`, so the session lease's pre-check applies (it writes)
- All `/char-create` names and the slash-command path go in `st-selectors.js`

### Out of scope

- Import (card file or URL): later
- Every other card field (personality, scenario, example messages, tags, lorebook, alternate greetings, creator notes, depth prompt), editing an existing card, crop UI, AI drafting: Phase 3
- Groups

## Tests

**Unit**

- ViewModel: Save enabled only with a usable name, not while saving or generating; success → refresh (not from the server), then navigate to the chat with the avatar key; failure, timeout or unreachable → the same inline error, inputs kept; discard dialog only when something is typed
- Name check: empty, spaces only, only characters `sanitize-filename` removes → not usable
- `characterResult` parsing (ok, failed, missing fields, unknown fields)

**Compose UI** (`CreateCharacterContent`): empty state, Save enabled / disabled, saving state, error message, discard dialog.

## Verify on Device

On the **test install** (throwaway characters):

- Create with and without a picture; the card appears in Characters and in SillyTavern's own list (desktop), picture shape correct
- The new chat opens with the first message; back goes to Characters
- A name that already exists: a second card is created with the same display name and a different file (Notes)
- A name with `?` or `:`: SillyTavern saves it without them (Notes); decide whether the app shows that before saving
- Save disabled while a reply generates; parked (session lease): refused with the scrim

## Notes

### Investigation (2026-11-21, SillyTavern 1.19.1 source on the test install)

- **SillyTavern's own Create** (`public/script.js` `createOrEditCharacter`, exported): reads the whole `#form_create` DOM form (every field, hidden defaults, the crop from its popup) and posts it as `multipart/form-data` to `/api/characters/create`. Refuses an empty name ("Name is required") and creating while generating. Driving it from the bridge would mean filling SillyTavern's form fields through DOM selectors
- **`/char-create`** (`public/scripts/slash-commands.js`, `createCharacterCallback`): SillyTavern's own create without the form. Builds the same fields the form sends (defaults: talkativeness `0.5`, depth prompt depth `4`, role `system`, empty tags and greetings, `extensions: '{}'`), posts them as JSON to `/api/characters/create`, then uploads the picture to `/api/characters/edit-avatar` (`avatar` file + `avatar_url` = the new key), refreshes the list (`getCharacters()`), selects the character unless `select=false`, and returns the avatar key. `avatar` takes a `data:image/…` URL (`resolveAvatarData` in `scripts/utils.js`), `prompt`, or a same-origin path; `avatarPromptResize=false` skips the crop popup (with it, SillyTavern's crop dialog would open in the hidden page). Shows a green toast "Character "{name}" created successfully" or a red one on failure **(verify whether it shows through the app's cover)**. No check for a running reply. **Chosen:** same approach as 1.9 personas, no DOM form
- **Server** (`src/endpoints/characters.js`, `POST /create`): `ch_name` is passed through `sanitize-filename` (the default replacement is empty, so `/ \ ? < > : * | "`, control characters, leading dots and Windows reserved names are **removed from the name itself**, not only from the file name). The card is written as a V2 card (`charaFormatData`), with SillyTavern's default avatar when no picture is uploaded. The chats folder `chats/<file name>` is created. Answers the avatar key as text
- **A taken name** (`getPngName`): no error and no overwrite. Display names may repeat; the file name is the sanitized name, and if `<name>.png` exists SillyTavern tries `<name>1.png`, `<name>2.png`, … (up to 10 000) and uses the first free one. So creating "Alice" twice gives two characters called Alice, files `Alice.png` and `Alice1.png`. The app needs no check of its own; the avatar key it gets back is the one to open
- **Picture size:** with no crop, `applyAvatarCropResize` keeps the uploaded size (`image.cover` to its own width and height). SillyTavern's own UI crops to 2:3 and, when asked to resize, to 512 × 768 (`AVATAR_WIDTH`, `AVATAR_HEIGHT` in `src/constants.js`), hence the phone-side crop above

## References

- @context/coding-standards.md
- @context/specs/phase1-9-personas-spec.md (same slash-command approach, picture encoding)
- @context/specs/phase1-6-home-character-list-spec.md (Characters tab, avatar cache keys)
- @context/specs/phase1-7-chat-screen-spec.md (opening a chat)
