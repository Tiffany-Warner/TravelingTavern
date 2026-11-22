# Phase 1.9 — Personas

## Overview

A persona is who you are in a chat: a name, a picture and a description the character sees. Personas live in SillyTavern; the app shows them, switches them and edits them through SillyTavern's own code.

- In the chat's native input bar (1.7), a **chip** shows the persona you're chatting as
- Tapping it opens a **full-screen Personas list**; tapping a persona there switches to it
- From the same screen you can create, edit and delete personas and set the default. Picking a persona in a chat locks it to that chat

**Only SillyTavern's persona features.** Every field, badge and action maps to something SillyTavern already has (`public/scripts/personas.js`). SillyTavern personas have no age, gender or other structured fields, and the app doesn't add any.

## Requirements

### Investigation first (do this before writing code)

What SillyTavern 1.19.0 has (read from `personas.js`, 2026-11-19), to confirm on the live page **(verify)**:

- Personas: `power_user.personas` (avatar file → name) and `power_user.persona_descriptions[avatarId]` (`description`, `title`, `position` / `depth` / `role`, `lorebook`, `connections`); the active persona is `user_avatar`; the default is `power_user.default_persona`
- Switching: `setUserAvatar(avatarId)` (or `autoSelectPersona`), the same thing a click in SillyTavern's persona list does
- Locks: `setPersonaLockState(state, type)` with types `chat` (this chat's metadata), `character` (a connection to this character) and `default`; `isPersonaLocked(type)` reads them
- Create: `createPersona` / `initPersona(avatarId, name, description, title)`; picture upload: `uploadUserAvatar`
- Edit: `setPersonaDescription` (description), `renamePersona` (name), title and picture changes
- Delete: `deleteUserAvatar` and how SillyTavern confirms and cleans up (default, locks, connections)
- Persona pictures: the thumbnail address (e.g. `/thumbnail?type=persona&file=…`) and the full-size one, and whether a changed picture needs the same `tt_v` cache-busting as character avatars (1.6 fix, 0.1.15)
- Which of these are reachable from `bridge.js` (exported, on `getContext()`, or imported by absolute URL like the lorebook functions in 1.6b), and which events fire on a persona change (e.g. `PERSONA_CREATED`, settings updated) so the list stays fresh
- Write the findings into Notes and add every name to `st-selectors.js`

### Chip in the chat input bar

- A **native Compose chip** in the 1.7 native input bar, **below the message container** (user decision, 2026-11-19): the persona's small round picture, "Chatting as {name}" and a ▾. Long names are ellipsized. Nothing about it is in the page
- Tapping it opens the Personas screen
- It follows the active persona, including changes made on the desktop or by SillyTavern's own auto-selection (a locked persona is selected when its chat opens)

### Personas screen (`ui/personas/PersonasScreen.kt`)

- Full-screen destination (bottom bar hidden). Top bar: close (✕) and the title **Personas**
- One line of secondary text under the bar: "Who you are in your chats. Characters see your persona's description."
- **List of persona cards** (surface cards, 16dp corners, no borders; per `ui-guidelines.md`):
    - Left: the persona's picture, 3:4, rounded
    - Right: the name (`titleMedium`); the description, two lines, ellipsized (`bodyMedium`, secondary); then small badges from SillyTavern's data, only those that apply: **Active** (the one in use) and **Default** (user decision, 2026-11-19; **Locked to this chat** dropped 2026-11-20, user decision: picking in a chat always locks, so it only repeated Active, and chats without a lock use the Default)
    - A horizontal ⋯ button at the top right of each card (48dp target) for its actions (user decision 2026-11-20)
    - The active persona's card is marked with the accent (a thin amber outline or the selected container); one marked card only
- Sorted as SillyTavern sorts its own list (by name), the active persona first
- **Create** at the bottom: the screen's one filled button (per `ui-guidelines.md`)
- Empty or unreachable states as on other screens (banner for "Can't reach your server"; empty: one sentence + Create)

### Picking a persona (one behavior everywhere)

- **Tap a card to use that persona.** The same screen and the same behavior wherever it's opened from; ⋯ holds everything else
- **In a chat** (opened from the chat's chip): picking a persona switches to it **and locks it to this chat automatically** (user decision, 2026-11-19), using SillyTavern's own switch and chat lock. Every time that chat opens, SillyTavern selects that persona, until you pick another one, which moves the lock. No confirmation: picking is the choice. Then the screen closes back to the chat
- **Without a chat** (the Chat screen isn't showing, for example opened from the Me tab): picking makes it the active persona, as clicking it in SillyTavern's own list does. Nothing is locked, and picking **never changes the lock on the chat that's still open in the page** (SillyTavern keeps the last chat open behind the other screens). If SillyTavern's own auto-lock setting would lock the persona to that chat, the bridge must avoid or undo it **(verify against SillyTavern's auto-lock setting)**
- Chats that were never locked (for example old ones) show whatever SillyTavern selects for them (the current or default persona), until you pick one
- Prefer SillyTavern's own path if it has one (for example an auto-lock setting when choosing a persona); otherwise switch, then lock with `setPersonaLockState(true, 'chat')` **(verify)**

### Card actions (⋯ → dropdown menu)

A dropdown anchored under the card's ⋯, like the chat's ⋮ menu (changed from a bottom sheet, user decision 2026-11-20).

Only SillyTavern's own options, kept short:

| Action | What runs |
|---|---|
| Edit | Opens the persona editor |
| Set as default / Remove default | SillyTavern's default persona |
| Delete | The same alert dialog as deleting a chat (`ConfirmDeleteDialog`: red delete icon, "Delete {name}?", bold red "This also deletes the persona on your SillyTavern.", then "Chats keep their messages, but this persona is gone.", red Delete button; user decision 2026-11-20), then SillyTavern deletes it. Not offered for the active persona while it's in use, if SillyTavern doesn't allow that **(verify)** |

- Not offered (user decision, 2026-11-19): connecting a persona to a character, and a separate lock / unlock action (picking already locks). Both stay available in Advanced

### Persona editor (`ui/personas/PersonaEditorScreen.kt`)

- Full-screen, used for **Create** and **Edit**. Fields:
    - Picture (tap to pick from the phone's photos; uploaded through SillyTavern's own persona picture upload)
    - Name (required)
    - Title (optional short tagline, SillyTavern's `title`)
    - Description (multi-line, with a token count like the lorebook entry editor in 1.6b)
- **Save** at the bottom above the keyboard, enabled when the name isn't empty and something changed
- Fields the app doesn't edit (description position, depth, role, persona lorebook, connections) are kept exactly as they are, as the 1.6b lorebook editor does for entries
- Saving runs SillyTavern's own create / rename / description / title functions, so the desktop shows the same persona after a reload

### Bridge

- `personas` message: list of `{ avatarId, name, title, description (≤ 300 chars for previews), pictureUrl, isActive, isDefault }`, sent after `ready`, on persona changes and on request (`requestPersonas`)
- Commands, each answering with a request ID and success or error, like the lorebook commands: `selectPersona(avatarId, lockToChat)`, `requestPersona(avatarId)` (full description and fields), `savePersona(…)`, `createPersona(…)`, `deletePersona(avatarId)`, `countTokens` (already exists)
- Picture upload: the app picks the image; how it reaches SillyTavern's upload (through the page or the shared `OkHttpClient` with SillyTavern's request headers) is decided by the investigation; prefer SillyTavern's own upload path **(verify)**

### Me tab (1.8)

- 1.8's "persona" entry becomes a **Personas** row that opens this same screen (the Chat screen isn't showing, so picking doesn't lock). No separate persona UI in the Me tab

### Out of scope

- Persona lorebooks, description position / depth / role (stay in Advanced)
- Group-chat persona rules
- Fields SillyTavern doesn't have (age, gender, structured appearance)

## Tests

- Unit tests:
    - `BridgeMessageParser`: `personas` (valid, missing fields, malformed)
    - `PersonaRepositoryImpl`: list, `selectPersona(avatarId, lockToChat)` (with and without the lock), save, create, delete with request-ID matching and timeouts
    - `PersonasViewModel`: tap in a chat selects and locks, tap without a chat selects only; badges; active first; delete confirmation; errors
    - `PersonaEditorViewModel`: Save enabled rules; unchanged fields kept; create vs edit
    - `BridgeScript`: the new commands (argument encoding)
- Compose UI tests: `PersonasContent` (cards, the two badges, active marking, ⋯ menu with Edit / default / Delete, delete dialog, Create, empty and error states); `PersonaEditorContent` (Save enabled / disabled, token count shown); the chat's persona chip shows the name and calls back
- Version bump per `CLAUDE.md`

## Verify on Device

- [ ] The chip shows the active persona; tapping it opens the list; picking switches, returns to the chat, and the next reply addresses the new persona
- [ ] After picking, the persona is locked to the chat; closing and reopening the chat (in the app and on the desktop) keeps it; picking another moves the lock
- [ ] Badges match SillyTavern's own persona panel on the desktop (active, default)
- [ ] Create with a picture, edit name / title / description, delete: each shows the same on the desktop after a reload, and fields the app doesn't edit are unchanged
- [ ] From the Me tab, tapping a card makes it the active persona without locking any chat; ⋯ → Edit opens the editor
- [ ] A persona picture changed on the desktop shows in the app after a refresh
- [ ] A persona created on the desktop shows when Personas opens, and survives the app saving settings afterwards

## Notes

### Decision (2026-11-19, user)

Personas get their own sub-feature instead of being part of 1.7: a chip in the chat's input bar shows the active persona, and tapping it opens a full-screen list. The layout follows a screenshot the user shared; the fields and actions are SillyTavern's own.

Kept simple on purpose: one behavior everywhere (tap to use, ⋯ for Edit / default / Delete); picking in a chat locks the persona to that chat automatically; two badges only (Active, Default); no character connections.

A persona connected to a character on the desktop (in SillyTavern's own persona panel) still auto-selects when that character's chat opens. The chip follows it like any other change; the app shows no badge for connections.

### Investigation (2026-11-19, SillyTavern 1.19.0 source, `public/scripts/personas.js`)

Names are in `st-selectors.js` → `personas`. Still to confirm on the live page: marked **(verify)**.

- **Data:** `getContext().powerUserSettings` has `personas` (avatar id → name), `persona_descriptions[id]` (`description`, `title`, `position`, `depth`, `role`, `lorebook`, `connections`) and `default_persona`. The chat lock is `chatMetadata.persona` (avatar id). The active persona `user_avatar` is **not** on `getContext()`: the bridge imports `/scripts/personas.js` (same instance as SillyTavern's UI, like `world-info.js` in 1.6b) and reads the live binding
- **Sorting:** SillyTavern sorts by name (`localeCompare`, asc/desc setting); the app sorts by name ascending, active first
- **Switch:** `setUserAvatar(id)` (exported), the same as a click in SillyTavern's list; emits `PERSONA_CHANGED`
- **Auto-lock:** `power_user.persona_auto_lock`, when on, makes `setUserAvatar` lock the picked persona to the open chat (in `selectCurrentPersona`, which checks the setting synchronously before its first `await`). **In a chat:** `setUserAvatar(id)`, then `setPersonaLockState(true, 'chat')` (harmless if auto-lock already did it). **Without a chat:** the bridge sets `persona_auto_lock = false` around `setUserAvatar` and restores it in `finally`, so the open chat's lock never changes
- **Create / edit / delete:** the helpers (`renamePersona`, `deletePersona`, `uploadPersonaAvatar`) aren't exported and open popups. SillyTavern's own slash commands `/persona-create`, `/persona-update`, `/persona-delete` do the same without popups. The bridge calls their callbacks directly (`SlashCommandParser.commands[name].callback(args, '')`), not through the parser, so `{{user}}`, quotes and pipes in a description aren't evaluated. They take `persona=<avatar id>` (`findPersona` matches avatar ids); create returns the new avatar id; update only touches fields it's given (position, depth, role, lorebook, connections stay as they are); `avatarPromptResize=false` skips the crop popup; `select=false` on create; delete with `silent=true` does SillyTavern's own cleanup (clears default and the chat lock if they pointed at it, then picks a persona for the open chat). Deleting the active persona is allowed in SillyTavern, so Delete is offered for every persona. Verified: called this way, create, update and delete show no toast in the page. Locking to a chat (`setPersonaLockState`) does show SillyTavern's green "Persona Locked" toast over the chat
- **Default:** no exported function and no slash command sets a non-active persona as default (`/persona-lock type=default` only works for the active one; `toggleDefaultPersona` isn't exported). The bridge sets `powerUserSettings.default_persona` (or deletes it to remove it) and calls `saveSettingsDebounced()`, which is what `toggleDefaultPersona` does after its popup
- **Pictures:** thumbnail `getThumbnailUrl('persona', id)` = `/thumbnail?type=persona&file=…`; full size `/User Avatars/{id}`. The upload replaces the file under the same name, and SillyTavern stores no change time for personas (unlike `date_added` on cards), so `tt_v` can't come from SillyTavern: the bridge keeps a picture version that changes on every `requestPersonas(fromServer)` (opening the Personas screen, pull to refresh) and after a picture upload
- **Upload:** create / update take `avatar=<data URL>`; SillyTavern uploads it through `/api/avatars/upload`, which crops/resizes to its avatar size on the server. The app downscales the picked photo (≤ 1024 px long edge, JPEG) before passing it to the page, to keep the `evaluateJavascript` string small
- **Events:** `PERSONA_CHANGED`, `PERSONA_CREATED`, `PERSONA_UPDATED`, `PERSONA_RENAMED`, `PERSONA_DELETED`; plus `CHAT_CHANGED` (the chat lock and SillyTavern's auto-selection change with the chat)
- **Gotcha to watch (verify):** `loadPersonaForCurrentChat` returns early if the chat id hasn't changed since the last load. If a persona is picked from the Me tab and the same chat is then reopened, SillyTavern may keep the picked persona instead of the chat's locked one

## References

- @context/ui-guidelines.md
- @context/coding-standards.md
- @context/specs/phase1-6b-lorebooks-tab-spec.md (request-ID commands, editor that keeps unknown fields, token count)
- @context/specs/phase1-7-chat-screen-spec.md (input bar)
- @context/specs/phase1-8-settings-advanced-spec.md (Me tab)
- SillyTavern `public/scripts/personas.js`

### Stale page (added 2026-11-20, user decision)

- The app's page reads settings.json once, at load, and SillyTavern's `saveSettings` writes the whole file from the page's copy. A persona created or edited on the desktop after the page loaded is unknown to the page, and the page's next settings save erases it (name and description; its picture file stays). Found in testing: a persona made on the desktop was missing in the app, and was later erased from the server when the app's page saved settings
- The page saves settings often, not only on persona changes: opening a chat saves, and returning to the app saves about a second later (seen on the emulator)
- Fix (personas only): `syncPersonaSettings` copies `personas`, `persona_descriptions` and `default_persona` from `POST /api/settings/get` (`settings` is settings.json as a string, `power_user` inside it) into the page's `power_user`, in place. It runs when Personas opens (`requestPersonas(true)`), when the app returns to the foreground (`setAppState(true)` from background) and before every persona action. It is skipped for 5 s after the page's own persona change, so it can't undo a change whose debounced save (1 s, `debounce_timeout.relaxed`) hasn't reached the server yet
- Not covered: other settings changed on the desktop are still overwritten the same way, and a persona made on the desktop while the app is in the foreground is erased if a chat is opened before Personas is. A general fix (reload the page when the app returns) is a separate fix

### Re-read made conditional (session lease, `0.1.20`)

- The session lease (`session-lease-app-spec.md`) closes the stale copy for every page that runs it: each page that writes loaded while holding the lease, so its settings are the newest. The re-read above is then skipped
- **Skipped once the lease's `leaseInfo` reports the desktop extension** (`extensionLeaseVersion` not null, any version) for the current page load. Kotlin (`PersonaSyncSwitch`) tells the page with `setExtensionReported(true)` once the bridge is ready and that `leaseInfo` has arrived, again after every page load
- **Kept while no extension is reported**, and before the first `leaseInfo` of a page load (unknown counts as no extension: the page starts every load with the re-read on). Without the extension a desktop tab outside the lease can still change personas, so the gaps listed above stay as they were in that case
- Known limit: a desktop tab opened before the extension was installed isn't in the lease until it reloads, while the phone already skips the re-read. The app's install message asks the user to reload SillyTavern on the computer (session lease A8)
