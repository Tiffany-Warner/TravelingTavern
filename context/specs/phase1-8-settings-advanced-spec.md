# Phase 1.8 — Me & Advanced

## Overview

The native **Me** tab with the few things a phone user needs, and an Advanced entry that shows the full, unstyled SillyTavern UI for everything else (presets, lorebooks, extensions). Tuning stays on the desktop or behind Advanced.

## Requirements

### Me (`ui/me/MeScreen.kt`)

- **Personas**
    - "Personas" → the 1.9 Personas screen (the same list the chat's chip opens; the Chat screen isn't showing, so picking doesn't lock any chat) (`phase1-9-personas-spec.md`); no separate persona UI here
- **Server**
    - Shows the current server URL and whether a login is saved
    - "Change server" → Connect
- **Advanced**
    - "Open full SillyTavern" → Advanced screen
- **About**
    - App version
    - Open-source licenses → screen that starts with the app's own license (AGPL-3.0-only, no warranty, link to the source on GitHub), then each third-party notice, including the Aegis wrapper's ISC license in full

### Advanced (`ui/advanced/AdvancedScreen.kt`)

- Top app bar: back, title "Full SillyTavern"
- Body: the shared `SillyTavernWebView` with `tt-clean` **removed** (the 1.7 layout and, later, the Phase 2 `tt-style-*` classes only apply under it)
- On leaving: restore `tt-clean`
- A small info banner on first visit: "Changes here save to your SillyTavern, same as on desktop"

### ViewModels

- `MeViewModel` — observes `ServerConfig`; exposes `UiState(serverUrl, hasLogin, appVersion)`
- `AdvancedViewModel` — calls `setClean(false)` on enter and `setClean(true)` on exit

## Notes

- Keep Me short. Anything SillyTavern already has a setting for stays in SillyTavern (reachable through Advanced)
- Licenses screen can be a simple hand-maintained list for now
- AGPL section 5(d): a program with an interactive interface shows its license notice and that there is no warranty; the licenses screen covers it (decided 2026-11-22)
- **Advanced and the session lease (checked 2026-11-21):** Advanced shows the same shared WebView page, so it is under the lease like every other screen: while this phone holds the lease its changes save to SillyTavern, and while parked the scrim covers it and nothing saves. The first-visit banner ("Changes here save to your SillyTavern, same as on desktop") still fits and needs no lease wording; the scrim explains the parked case
- **1.0.0 scope (user decision, 2026-11-21):** no "Notify me — Coming soon" row: it returns with Phase 8, wired up. No Appearance section: it's a later nice-to-have (`project-schema.md` → Later). "Settings" is renamed **Me** everywhere, matching the tab (`ui/me/MeScreen.kt` already exists as the placeholder)

## References

- @context/coding-standards.md
- @context/specs/phase1-7-chat-screen-spec.md
