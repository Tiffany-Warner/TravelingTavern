# Phase 1.6b — Lorebooks Tab

> Builds on the JS bridge (1.4) and the list patterns from the Chats & Characters tabs (1.6).

## Overview

Lorebooks (SillyTavern calls them **World Info**) are a core SillyTavern feature: keyword-triggered entries that inject lore, places, people and rules into the prompt. On a phone they are buried in a drawer, so they get their own bottom tab. The tab lets the user browse, create and delete lorebooks, see which are active, and read, add, edit and delete entries, with all reads and writes going through SillyTavern's own code.

## Requirements

### Bottom navigation

- Bottom bar grows from three tabs to four: **Chats**, **Characters**, **Lorebooks**, **Me** (Lorebooks sits before Me so Me stays last)
- New `Routes.Tab.LOREBOOKS` nested graph and `TopLevelTab.LOREBOOKS` entry; outlined/filled book icon pair like the other tabs
- Same tab behavior as the others: state preserved when switching, reselect scrolls to top, bar hidden on full-screen destinations
- Update `context/ui-guidelines.md` (Navigation: four tabs; Screen Map: add Lorebooks row)
- Ship the tab only when the list is real — ui-guidelines forbid placeholder tabs

### Lorebooks list (`ui/lorebooks/LorebooksScreen.kt`)

- Top app bar: "Lorebooks" title + search action. Search is client-side and matches lorebook names **and** the names of characters they're attached to (typing "Sera" finds Seraphina's lorebooks)
- Filter chips under the app bar, single-select: **All** (default) · **Global** · **Character ▾** · **Unused**
    - **All** — every lorebook
    - **Global** — turned on for every chat (SillyTavern's global "active worlds" list)
    - **Character ▾** — opens a bottom sheet listing characters (avatar + name) that have at least one lorebook attached. Picking one filters the grid to that character's lorebooks and the chip reads "<name> ✕"; tapping ✕ clears the filter back to All. Hidden when no character has a lorebook
    - **Unused** — not global, not attached to any character, not attached to any persona (useful for cleanup). A chat may still use it: chat links live inside each chat file and aren't read here (see "Lore in this chat" under Future)
    - Chips and search combine (e.g. Global + "magic")
    - Filter, selected character and search text are preserved when switching tabs (ui-guidelines)
- 2-column grid (`LazyVerticalGrid`) of lorebook cards, `surface` card with 16dp corners, no border:
    - **Cover** (4:3): the same quiet neutral tile with a book icon for every lorebook. No character art on covers and no per-lorebook colors — "characters are the color" is carried by the status line instead
    - **Name** (`titleSmall`, max 2 lines, ellipsized)
    - **Entry count** ("32 entries", `bodySmall`, secondary)
    - **Status line** (`bodySmall`):
        - Attached to one character → small round avatar (16dp) + character name, amber dot before it. Several characters → avatar of the first by name (alphabetical) + "Seraphina +2"
        - Global → amber dot + "Global" (if also attached to characters, Global wins the line; the detail screen shows the rest)
        - Attached only to a persona → amber dot + "Persona"
        - Nothing → "Not active" in muted text, no dot. Chat links are not shown (see Unused)
    - One tap target per card: tapping anywhere on it opens the lorebook. The status line is display only (the Character chip filters by character)
- Where each binding lives (global list, character's primary and extra lorebooks, persona) must be read through the bridge **(verified — see Notes)**
- Sorted alphabetically within the current filter
- Results count is not shown; an empty filter result shows one line ("No lorebooks match") with a "Clear filters" text button
- Pull to refresh (like Chats): lorebooks created, deleted or renamed on the desktop send no SillyTavern event
- FAB (`+`) → bottom sheet with a name field and Create. Names must be non-empty and not already taken; the new lorebook opens in its detail screen
- Empty state: "No lorebooks yet." + tonal button "Create lorebook" (same sheet as the FAB; tonal because the FAB is the screen's primary action)
- Error banner and states per ui-guidelines (loading, unreachable, auth failed, empty)

### Lorebook detail (full-screen, bottom bar hidden)

- Route takes the lorebook name only (primitive ID); the destination ViewModel loads the entries
- List of entries: title/comment, trigger keywords as chips, one-line content preview, enabled state
- Toggle a lorebook's global activation from here **(verified — see Notes)**
- FAB (`+`) adds a new entry and opens it in the editor
- Overflow menu → "Delete lorebook" → confirmation sheet ("Delete <name>? This can't be undone.") → delete through SillyTavern, then return to the list. If it is attached to characters or personas, say so in the sheet: name them, and add that they keep a link to this name (SillyTavern only clears the current character's and persona's), e.g. "Seraphina will keep a link to this name. Clear it in Advanced if you plan to reuse the name." **(verified — see Notes)**

### Entry editor (full-screen)

- Fields: title/comment, keywords, content, enabled; everything else stays as SillyTavern has it
- Unknown or advanced fields (position, depth, order, probability, recursion, groups, …) are preserved untouched and edited in SillyTavern via Advanced
- Saving goes through SillyTavern's own World Info save path via the bridge — the app never writes the lorebook JSON file itself
- Token count shown for the content field (`getTokenCountAsync`, verified cheap — see Notes)
- Overflow menu → "Delete entry" → confirmation sheet → delete through SillyTavern

### Architecture

- `LorebookRepository` (domain interface, data impl over `BridgeClient`), domain models `Lorebook` and `LorebookEntry`
- Use cases: `GetLorebooksUseCase`, `GetLorebookEntriesUseCase`, `CreateLorebookUseCase`, `DeleteLorebookUseCase`, `SaveLorebookEntryUseCase`, `DeleteLorebookEntryUseCase`, `SetLorebookActiveUseCase`
- Create and delete (lorebooks and entries) call SillyTavern's own functions through the bridge **(verified — see Notes)**
- `LorebooksViewModel`, `LorebookDetailViewModel`, `LorebookEntryViewModel`, each exposing a `StateFlow<UiState>`
- All World Info globals and selectors go in `assets/web/st-selectors.js` / `data/bridge/StSelectors.kt`
- Filtering and search live in a `FilterLorebooksUseCase` (chip + selected character + query → list), not in the composable
- Unit tests for the use cases (including every chip, the character filter, search by character name, and chip + search combined), ViewModels, and bridge message parsing for the new message types
- Compose UI tests for the list's stateless content: each status-line variant renders, chips select, the Character sheet applies and ✕ clears the filter, empty filter result shows "Clear filters"

## Open Questions

- How much of the binding model (global / character / chat / persona) to show on a phone without it needing explanation. Current answer: one status line per card plus the four filter chips; full detail only on the lorebook detail screen
- **Decided (2026-11-17):** chat links are not read for the list — SillyTavern keeps them inside each chat file, so showing them would mean opening every chat. No "Chat" status, Unused excludes only global/character/persona links, and chat links wait for "Lore in this chat". Tab order Chats · Characters · Lorebooks · Me. No tap shortcut on the status line: one tap target per card

## Future (not in 1.6b)

- **"Lore in this chat"** — an item in the Chat screen's menu (1.7 or later) listing every lorebook that's on for the current conversation (global + character + chat + persona), with toggles. This is where "active right now" makes sense; the Lorebooks tab has no current chat, so it has no "Active" filter

## Notes

- The SillyTavern boundary rules apply: lorebooks are SillyTavern data. Read and change them only through SillyTavern's own JS, never by writing files
- Everything marked **(verify)** must be confirmed against the live SillyTavern (`chrome://inspect`) or its source before building on it, and the findings recorded here
- Layout inspired by PocketTavern's lorebook browser (grid of simple cards, search, one filter control), restyled to `ui-guidelines.md`: neutral covers, one amber accent (the status dot and selected chip), no stars, authors, tags or result counts — those belong to a public library, not your own lorebooks
- **Verified against SillyTavern 1.19.0 (live page + `scripts/world-info.js`, `personas.js`, `utils.js`), 2026-11-17:**
  - **Module access:** `getContext()` only has `loadWorldInfo`, `saveWorldInfo`, `updateWorldInfoList`, `getWorldInfoNames`, `getTokenCountAsync`. Everything else comes from the module itself: `await import('/scripts/world-info.js')` (exports `world_names`, `selected_world_info`, `world_info`, `createNewWorldInfo`, `deleteWorldInfo`, `createWorldInfoEntry`, `deleteWorldInfoEntry`, `loadWorldInfo`, `saveWorldInfo`, `updateWorldInfoList`). `selected_world_info` and `world_names` are reassigned `let` exports — read them through the module namespace each time (live bindings), never cache them
  - **Lorebook names:** `world_names` (array of names, also the file names)
  - **Global:** `selected_world_info` (array of names; persisted as `world_info.globalSelect` in settings). SillyTavern's own control is the `#world_info` multi-select (option value = index into `world_names`); its `change` handler runs `onWorldInfoChange('__notSlashCommand__')`, which rebuilds `selected_world_info` from the selected options, saves settings and emits `WORLDINFO_SETTINGS_UPDATED`. **The bridge toggles global by setting that option's `selected` and triggering `change`** — exactly what the UI does (the slash-command path splits names on commas, so it can't handle every name)
  - **Character, primary:** `characters[i].data.extensions.world` (a name or `""`). This server: `default_Seraphina.png` → `Eldoria`
  - **Character, extra books:** `world_info.charLore` = `[{ name, extraBooks: [names] }]`, where `name` is the avatar file name without its extension (`getCharaFilename`). Absent on this server
  - **Persona:** `power_user.persona_descriptions[<persona avatar>].lorebook`; the current persona's is mirrored in `power_user.persona_description_lorebook`. None set on this server. Read `power_user` via `getContext().powerUserSettings`
  - **Chat:** `chat_metadata.world_info` — only for the open chat (not used by the tab; see Decided above)
  - **Entries:** `loadWorldInfo(name)` → `{ entries: { [uid]: entry } }` (cached in `worldInfoCache`, which hands out a **copy** on every read; one `POST /api/worldinfo/get` per lorebook the first time — the bridge drops the cached copy before opening a lorebook or on pull to refresh, so edits made on the desktop show up). An entry has 41 fields; the app reads/edits `uid`, `comment`, `key` (array), `content`, `disable` (enabled = `!disable`) and leaves the rest untouched by editing the loaded object in place
  - **Save:** `saveWorldInfo(name, data, true)` → `POST /api/worldinfo/edit`, updates the cache, emits `WORLDINFO_UPDATED(name, data)`
  - **Create lorebook:** `createNewWorldInfo(name, { interactive: false })` → `false` (with a toast) if a lorebook with that name exists, compared ignoring case and accents (`checkOverwriteExistingData`); the app checks the same way before calling. Side effect: selects it in the hidden World Info editor (harmless)
  - **Delete lorebook:** SillyTavern's own Delete button (`#world_popup_delete`) first removes the name from every `world_info.charLore` entry's `extraBooks` (+ `saveSettingsDebounced()`), then calls `deleteWorldInfo(name)`: `POST /api/worldinfo/delete`, removes it from the global list, clears the **current** character's primary and the **current** persona's lorebook only. Other characters' primary link (`extensions.world`) and other personas' links keep the dead name; SillyTavern ignores names not in `world_names`, so they're harmless but still show the old name in its character panel. The bridge does the same two steps (no `Popup`). The delete sheet names the characters it's attached to
  - **New entry:** `createWorldInfoEntry(name, data)` adds a template entry with a free `uid` to `data` (no save); then `saveWorldInfo`
  - **Delete entry:** `deleteWorldInfoEntry(data, uid, { silent: true })` (`silent` skips its confirm popup); then `saveWorldInfo`
  - **Bridge checked on the live page (emulator, throwaway lorebook "TT Bridge Test", deleted afterwards):** list with entry counts and Seraphina's link; Eldoria's 4 entries; create, and a duplicate name refused regardless of case; new entry; edit; fields the app doesn't edit (`order`, `depth`, `probability`, `position`) kept on the server after an app save; global on/off through `#world_info`; token count; delete entry; delete lorebook; "not found" afterwards; no bridge errors
  - **Token count:** `getContext().getTokenCountAsync(text)` — cheap (16 tokens for a short sentence, no visible delay). Shown for the content field
  - **Two devices at once (2026-11-18, user):** SillyTavern saves a lorebook as one whole file (`POST /api/worldinfo/edit` with every entry), so editing the same lorebook on two devices at the same moment can overwrite one side's change. Lower risk than EchoText threads (the earlier 1.7 spec's "Thread storage", now in Git history): nothing saves lorebooks in the background. So never keep edits only in memory, and save each change right away (the app does: every entry save and delete is saved at once, and the bridge drops its cached copy before opening a lorebook)
  - **Settings-file parts:** global on/off (`world_info.globalSelect`) and the delete's `charLore` cleanup are saved in SillyTavern's settings file, not the lorebook file, so they carry the same whole-file overwrite risk as EchoText threads: an older desktop tab that saves its settings can undo a global toggle made on the phone. Rare in practice (it needs a stale tab saving afterwards), and SillyTavern's own UI has the same behaviour
  - **Events for refreshing the list:** `WORLDINFO_UPDATED` (an entry saved), `WORLDINFO_SETTINGS_UPDATED` (global list changed), plus the character events the bridge already listens to (a character's primary link changes with the character). Creating, deleting or renaming a lorebook on the desktop emits no event; the bridge posts fresh lists after its own create/delete, and pull to refresh covers desktop changes

## References

- @context/ui-guidelines.md
- @context/coding-standards.md
- @context/specs/phase1-1b-bottom-navigation-spec.md
- @context/specs/phase1-4-js-bridge-spec.md
- @context/specs/phase1-6-home-character-list-spec.md
