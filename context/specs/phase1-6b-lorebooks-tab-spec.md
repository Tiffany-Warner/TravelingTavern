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
    - **Character ▾** — opens a bottom sheet listing characters (avatar + name) that have at least one lorebook attached. Picking one filters the grid to that character's lorebooks and the chip reads "<name> ✕"; tapping ✕ clears the filter back to All
    - **Unused** — not global and not attached to any character, chat or persona (useful for cleanup)
    - Chips and search combine (e.g. Global + "magic")
- 2-column grid (`LazyVerticalGrid`) of lorebook cards, `surface` card with 16dp corners, no border:
    - **Cover** (4:3): the same quiet neutral tile with a book icon for every lorebook. No character art on covers and no per-lorebook colors — "characters are the color" is carried by the status line instead
    - **Name** (`titleSmall`, max 2 lines, ellipsized)
    - **Entry count** ("32 entries", `bodySmall`, secondary)
    - **Status line** (`bodySmall`):
        - Attached to one character → small round avatar (16dp) + character name, amber dot before it. Several characters → first avatar + "Seraphina +2"
        - Global → amber dot + "Global" (if also attached to characters, Global wins the line; the detail screen shows the rest)
        - Attached only to a chat or persona → amber dot + "Chat" / "Persona" **(verify these bindings exist and how to read them)**
        - Nothing → "Not active" in muted text, no dot
    - Tapping the avatar/name on the status line applies that character's filter (same as picking them from the Character sheet); tapping anywhere else on the card opens the lorebook
- Where each binding lives (global list, character's primary and extra lorebooks, chat, persona) must be read through the bridge **(verify where SillyTavern stores each kind of binding and how to read it)**
- Sorted alphabetically within the current filter
- Results count is not shown; an empty filter result shows one line ("No lorebooks match") with a "Clear filters" text button
- FAB (`+`) → bottom sheet with a name field and Create. Names must be non-empty and not already taken; the new lorebook opens in its detail screen
- Empty state: "No lorebooks yet." + button "Create lorebook" (same sheet as the FAB)
- Error banner and states per ui-guidelines (loading, unreachable, auth failed, empty)

### Lorebook detail (full-screen, bottom bar hidden)

- Route takes the lorebook name only (primitive ID); the destination ViewModel loads the entries
- List of entries: title/comment, trigger keywords as chips, one-line content preview, enabled state
- Toggle a lorebook's global activation from here **(verify the SillyTavern function that does this so its own code runs)**
- FAB (`+`) adds a new entry and opens it in the editor
- Overflow menu → "Delete lorebook" → confirmation sheet ("Delete <name>? This can't be undone.") → delete through SillyTavern, then return to the list. If it is attached to characters or chats, say so in the sheet **(verify how SillyTavern handles bindings to a deleted lorebook)**

### Entry editor (full-screen)

- Fields: title/comment, keywords, content, enabled; everything else stays as SillyTavern has it
- Unknown or advanced fields (position, depth, order, probability, recursion, groups, …) are preserved untouched and edited in SillyTavern via Advanced
- Saving goes through SillyTavern's own World Info save path via the bridge — the app never writes the lorebook JSON file itself
- Token count shown for the content field if SillyTavern exposes a tokenizer call cheaply **(verify)**
- Overflow menu → "Delete entry" → confirmation sheet → delete through SillyTavern

### Architecture

- `LorebookRepository` (domain interface, data impl over `BridgeClient`), domain models `Lorebook` and `LorebookEntry`
- Use cases: `GetLorebooksUseCase`, `GetLorebookEntriesUseCase`, `CreateLorebookUseCase`, `DeleteLorebookUseCase`, `SaveLorebookEntryUseCase`, `DeleteLorebookEntryUseCase`, `SetLorebookActiveUseCase`
- Create and delete (lorebooks and entries) call SillyTavern's own functions through the bridge **(verify which ones — the same ones its World Info panel uses)**
- `LorebooksViewModel`, `LorebookDetailViewModel`, `LorebookEntryViewModel`, each exposing a `StateFlow<UiState>`
- All World Info globals and selectors go in `assets/web/st-selectors.js` / `data/bridge/StSelectors.kt`
- Filtering and search live in a `FilterLorebooksUseCase` (chip + selected character + query → list), not in the composable
- Unit tests for the use cases (including every chip, the character filter, search by character name, and chip + search combined), ViewModels, and bridge message parsing for the new message types
- Compose UI tests for the list's stateless content: each status-line variant renders, chips select, the Character sheet applies and ✕ clears the filter, empty filter result shows "Clear filters"

## Open Questions

- How much of the binding model (global / character / chat / persona) to show on a phone without it needing explanation. Current answer: one status line per card plus the four filter chips; full detail only on the lorebook detail screen

## Future (not in 1.6b)

- **"Lore in this chat"** — an item in the Chat screen's menu (1.7 or later) listing every lorebook that's on for the current conversation (global + character + chat + persona), with toggles. This is where "active right now" makes sense; the Lorebooks tab has no current chat, so it has no "Active" filter

## Notes

- The SillyTavern boundary rules apply: lorebooks are SillyTavern data. Read and change them only through SillyTavern's own JS, never by writing files
- Everything marked **(verify)** must be confirmed against the live SillyTavern (`chrome://inspect`) or its source before building on it, and the findings recorded here
- Layout inspired by PocketTavern's lorebook browser (grid of simple cards, search, one filter control), restyled to `ui-guidelines.md`: neutral covers, one amber accent (the status dot and selected chip), no stars, authors, tags or result counts — those belong to a public library, not your own lorebooks

## References

- @context/ui-guidelines.md
- @context/coding-standards.md
- @context/specs/phase1-1b-bottom-navigation-spec.md
- @context/specs/phase1-4-js-bridge-spec.md
- @context/specs/phase1-6-home-character-list-spec.md
