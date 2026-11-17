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

- Top app bar: "Lorebooks" title + search action (filters by name, client-side)
- One row per lorebook: name (`titleMedium`), entry count (`bodyMedium`, secondary), and a trailing marker when it is active
- "Active" means enabled globally or attached to the current character or chat **(verify where SillyTavern stores each kind of binding and how to read it through the bridge)**
- Sorted: active first, then alphabetical
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
- Unit tests for the use cases, ViewModels, and bridge message parsing for the new message types

## Open Questions

- How much of the binding model (global / character / chat / persona) to show on a phone without it needing explanation

## Notes

- The SillyTavern boundary rules apply: lorebooks are SillyTavern data. Read and change them only through SillyTavern's own JS, never by writing files
- Everything marked **(verify)** must be confirmed against the live SillyTavern (`chrome://inspect`) or its source before building on it, and the findings recorded here

## References

- @context/ui-guidelines.md
- @context/coding-standards.md
- @context/specs/phase1-1b-bottom-navigation-spec.md
- @context/specs/phase1-4-js-bridge-spec.md
- @context/specs/phase1-6-home-character-list-spec.md
