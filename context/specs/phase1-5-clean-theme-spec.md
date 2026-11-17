# Phase 1.5 — Clean Theme

## Overview

Inject a stylesheet that hides SillyTavern's settings clutter and makes the chat feel like a phone app — inside TravelingTavern only. The desktop browser is never affected. Advanced (1.8) turns it off to show the full UI.

## Requirements

- `assets/web/clean-theme.css`, injected once on `pageFinished` as `<style id="tt-clean-theme">`
    - Pass the CSS to JS Base64-encoded (decoded with `atob` in the page) so quotes, backslashes and newlines in the stylesheet can't break the script
    - If `#tt-clean-theme` already exists (reload, re-injection), replace its contents instead of adding a second tag
- Every rule scoped under `body.tt-clean` so the stylesheet does nothing until the class is set
- `__ttBridge.setMode()` (from 1.4) toggles `tt-clean` on `<body>`
- When clean mode is on:
    - Top-bar drawers and settings panels are hidden (AI response config, API connections, extensions, user settings, world info, character management drawer)
    - Chat input and send button stay visible and comfortable to tap (min 48dp-equivalent touch targets)
    - Message text is readable on a phone without zooming
    - Respects the page's light/dark appearance — no hardcoded colors that clash with SillyTavern themes
- All selectors referenced by the CSS are listed (with a one-line description each) at the top of `clean-theme.css` and mirrored in `st-selectors.js` comments

## Notes

- Find selectors by inspecting the live page in `chrome://inspect` on a debug build. Don't guess **(verify every selector)**
- Prefer stable ids over deep class chains, so SillyTavern updates break as little as possible
- Expect iteration — get the big drawers hidden first, then polish spacing
- Verify on device: Story tab looks clean, Advanced shows everything, desktop browser unchanged after using the app

## References

- @context/coding-standards.md
- @context/specs/phase1-4-js-bridge-spec.md