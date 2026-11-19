# Phase 1.9 — Personas

## Overview

A persona is who you are in a chat: a name, a picture and a description the character sees. Personas live in SillyTavern; the app shows them, switches them and edits them through SillyTavern's own code.

- In the chat's input bar (1.7), a **chip** shows the persona you're chatting as
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

- In the 1.7 input bar's control row, in the space 1.7 left for it: a filter-chip-style pill with the persona's small round picture, "Chatting as {name}" and a ▾. Long names are ellipsized
- Tapping it opens the Personas screen
- It follows the active persona, including changes made on the desktop or by SillyTavern's own auto-selection (a locked persona is selected when its chat opens)

### Personas screen (`ui/personas/PersonasScreen.kt`)

- Full-screen destination (bottom bar hidden). Top bar: close (✕) and the title **Personas**
- One line of secondary text under the bar: "Who you are in your chats. Characters see your persona's description."
- **List of persona cards** (surface cards, 16dp corners, no borders; per `ui-guidelines.md`):
    - Left: the persona's picture, 3:4, rounded
    - Right: the name (`titleMedium`); the description, two lines, ellipsized (`bodyMedium`, secondary); then small badges from SillyTavern's data, only those that apply: **Active** (the one in use), **Default**, **Locked to this chat** (user decision, 2026-11-19: only these three)
    - A ⋯ button at the top right of each card (48dp target) for its actions
    - The active persona's card is marked with the accent (a thin amber outline or the selected container); one marked card only
- Sorted as SillyTavern sorts its own list (by name), the active persona first
- **Create** at the bottom: the screen's one filled button (per `ui-guidelines.md`)
- Empty or unreachable states as on other screens (banner for "Can't reach your server"; empty: one sentence + Create)

### Picking a persona (one behavior everywhere)

- **Tap a card to use that persona.** The same screen and the same behavior wherever it's opened from; ⋯ holds everything else
- **In a chat** (opened from the chat's chip): picking a persona switches to it **and locks it to this chat automatically** (user decision, 2026-11-19), using SillyTavern's own switch and chat lock. Every time that chat opens, SillyTavern selects that persona, until you pick another one, which moves the lock. No confirmation: picking is the choice. Then the screen closes back to the chat
- **Without a chat** (opened from the Me tab): picking makes it the active persona, as clicking it in SillyTavern's own list does. Nothing is locked
- Chats that were never locked (for example old ones) show whatever SillyTavern selects for them (the current or default persona), until you pick one
- Prefer SillyTavern's own path if it has one (for example an auto-lock setting when choosing a persona); otherwise switch, then lock with `setPersonaLockState(true, 'chat')` **(verify)**

### Card actions (⋯ → bottom sheet)

Only SillyTavern's own options, kept short:

| Action | What runs |
|---|---|
| Edit | Opens the persona editor |
| Set as default / Remove default | SillyTavern's default persona |
| Delete | Destructive confirmation sheet ("Delete {name}? Chats keep their messages, but this persona is gone."), then SillyTavern deletes it. Not offered for the active persona while it's in use, if SillyTavern doesn't allow that **(verify)** |

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

- `personas` message: list of `{ avatarId, name, title, description (≤ 300 chars for previews), pictureUrl, isActive, isDefault, isLockedToChat }`, sent after `ready`, on persona changes and on request (`requestPersonas`)
- Commands, each answering with a request ID and success or error, like the lorebook commands: `selectPersona(avatarId, lockToChat)`, `requestPersona(avatarId)` (full description and fields), `savePersona(…)`, `createPersona(…)`, `deletePersona(avatarId)`, `countTokens` (already exists)
- Picture upload: the app picks the image; how it reaches SillyTavern's upload (through the page or the shared `OkHttpClient` with SillyTavern's request headers) is decided by the investigation; prefer SillyTavern's own upload path **(verify)**

### Me tab (1.8)

- 1.8's "persona" entry becomes a **Personas** row that opens this same screen (no chat open, so picking doesn't lock). No separate persona UI in the Me tab

### Out of scope

- Persona lorebooks, description position / depth / role (stay in Advanced)
- Group-chat persona rules
- Fields SillyTavern doesn't have (age, gender, structured appearance)

## Tests

- Unit tests:
    - `BridgeMessageParser`: `personas` (valid, missing fields, malformed)
    - `PersonaRepositoryImpl`: list, select, lock, save, create, delete with request-ID matching and timeouts
    - `PersonasViewModel`: tap in a chat selects and locks, tap without a chat selects only; badges; active first; delete confirmation; errors
    - `PersonaEditorViewModel`: Save enabled rules; unchanged fields kept; create vs edit
    - `BridgeScript`: the new commands (argument encoding)
- Compose UI tests: `PersonasContent` (cards, the three badges, active marking, ⋯ sheet with Edit / default / Delete, Create, empty and error states); `PersonaEditorContent` (Save enabled / disabled, token count shown); the chat's persona chip shows the name and calls back
- Version bump per `CLAUDE.md`

## Verify on Device

- [ ] The chip shows the active persona; tapping it opens the list; picking switches, returns to the chat, and the next reply addresses the new persona
- [ ] After picking, the chat shows "Locked to this chat" for that persona; closing and reopening the chat (in the app and on the desktop) keeps it; picking another moves the lock
- [ ] Badges match SillyTavern's own persona panel on the desktop (active, default, locked to this chat)
- [ ] Create with a picture, edit name / title / description, delete: each shows the same on the desktop after a reload, and fields the app doesn't edit are unchanged
- [ ] From the Me tab, tapping a card makes it the active persona without locking any chat; ⋯ → Edit opens the editor
- [ ] A persona picture changed on the desktop shows in the app after a refresh

## Notes

### Decision (2026-11-19, user)

Personas get their own sub-feature instead of being part of 1.7: a chip in the chat's input bar shows the active persona, and tapping it opens a full-screen list. The layout follows a screenshot the user shared; the fields and actions are SillyTavern's own.

Kept simple on purpose: one behavior everywhere (tap to use, ⋯ for Edit / default / Delete); picking in a chat locks the persona to that chat automatically; three badges only (Active, Default, Locked to this chat); no character connections.

## References

- @context/ui-guidelines.md
- @context/coding-standards.md
- @context/specs/phase1-6b-lorebooks-tab-spec.md (request-ID commands, editor that keeps unknown fields, token count)
- @context/specs/phase1-7-chat-screen-spec.md (input bar)
- @context/specs/phase1-8-settings-advanced-spec.md (Me tab)
- SillyTavern `public/scripts/personas.js`
