# Phase 1.1b — Bottom Navigation & Visual Style

## Overview

Replace the single-root navigation from 1.1 with a three-tab bottom navigation bar and apply the quiet visual style from `ui-guidelines.md`. Tabs are still placeholders — real content arrives in 1.6 and 1.8.

## Requirements

### Navigation (`ui/navigation/`)

- Bottom navigation bar (Material 3 `NavigationBar`) with three tabs, labels always shown:
    - **Chats** (start tab) — icon: chat bubble
    - **Characters** — icon: people
    - **Me** — icon: person
- Each tab has its own nested back stack; switching tabs preserves state (`saveState` / `restoreState`)
- Re-selecting the current tab scrolls its list to the top (wire the hook now; lists arrive in 1.6)
- Full-screen destinations hide the bottom bar: `Connect`, `Chat`, `Advanced`
- The bottom bar also hides while the keyboard is open (`WindowInsets.isImeVisible`), e.g. when typing in the Characters search field (1.6), and comes back when the keyboard closes
- Update `Routes.kt`: `TabRoutes` (Chats, Characters, Me) and `FullScreenRoutes` (Connect, Chat, Advanced)
- Remove the Settings route from 1.1 — settings now live in the Me tab
- Each tab shows a placeholder: centered tab name

### Theme (`ui/theme/`)

- Replace the 1.1 palette with the neutral palette from `ui-guidelines.md`:
    - Dark (default look): near-black background, slightly lighter surfaces, white primary text, muted gray secondary text
    - Light: off-white background, white surfaces, near-black text
    - Single warm amber accent (primary + primaryContainer); everything else neutral
- Dynamic color **off by default**; read a `useDynamicColor` flag from settings (hard-code `false` until 1.8 adds the toggle)
- Bundle Inter as the app font (Regular, Medium, SemiBold) and map it into the Material 3 type scale
- Shapes: 16dp medium/large, 12dp small

### Preview

- Previews for the bottom bar in light and dark, with each tab selected

## Notes

- If 1.1 used dynamic color or a different palette, replace it rather than layering on top

## References

- @context/ui-guidelines.md
- @context/coding-standards.md
- @context/specs/phase1-1-project-setup-spec.md