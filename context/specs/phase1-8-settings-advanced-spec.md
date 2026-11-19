# Phase 1.8 — Settings & Advanced

## Overview

A native Settings screen with the few things a phone user needs, and an Advanced entry that shows the full, unstyled SillyTavern UI for everything else (presets, lorebooks, extensions). Tuning stays on the desktop or behind Advanced.

## Requirements

### Settings (`ui/settings/SettingsScreen.kt`)

- **Personas**
    - "Personas" → the 1.9 Personas screen (the same list the chat's chip opens; with no chat open, picking doesn't lock) (`phase1-9-personas-spec.md`); no separate persona UI here. If 1.8 ships before 1.9, this row comes with 1.9
- **Server**
    - Shows the current server URL and whether a login is saved
    - "Change server" → Connect
- **Advanced**
    - "Open full SillyTavern" → Advanced screen
- **Notifications**
    - Notify me toggle, shown disabled with "Coming soon" (wired up in Phase 8, the final phase)
- **About**
    - App version
    - Open-source licenses → screen listing each third-party notice, including the Aegis wrapper's ISC license in full

### Advanced (`ui/advanced/AdvancedScreen.kt`)

- Top app bar: back, title "Full SillyTavern"
- Body: the shared `SillyTavernWebView` with `tt-clean` **removed** (the 1.7 layout and, later, the Phase 2 `tt-style-*` classes only apply under it)
- On leaving: restore `tt-clean`
- A small info banner on first visit: "Changes here save to your SillyTavern, same as on desktop"

### ViewModels

- `SettingsViewModel` — observes `ServerConfig`; exposes `UiState(serverUrl, hasLogin, appVersion)`
- `AdvancedViewModel` — calls `setClean(false)` on enter and `setClean(true)` on exit

## Notes

- Keep Settings short. Anything SillyTavern already has a setting for stays in SillyTavern (reachable through Advanced)
- Licenses screen can be a simple hand-maintained list for now

## References

- @context/coding-standards.md
- @context/specs/phase1-7-chat-screen-spec.md
