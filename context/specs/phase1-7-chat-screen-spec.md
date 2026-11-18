# Phase 1.7 — Chat Screen (Text and Story Tabs)

## Overview

The chat screen for one character, with two tabs over the same WebView: **Text** shows the EchoText texting panel full screen (the texting-style thread where proactive messages land), and **Story** shows SillyTavern's main roleplay chat. Switching tabs never reloads the page.

## Requirements

### Investigation first (do this before writing code)

- Find out how to open EchoText's panel for a specific character from script: a function, a click target, or an event **(verify)**
- Find EchoText's panel root element and how it sizes itself **(verify)**
- Write the findings into this spec's Notes section and add the names to `st-selectors.js`

### Screen (`ui/chat/ChatScreen.kt`)

- Top app bar: back, character avatar + name
- Tab row: **Text** | **Story** (Text is the default)
- Body: the shared `SillyTavernWebView` (1.3)
- Keyboard pushes the input up correctly (IME insets)

### Modes (CSS in `clean-theme.css`, toggled via `__ttBridge.setMode`)

- `tt-mode-text`: EchoText panel fills the viewport, main chat hidden
- `tt-mode-story`: main chat visible with clean theme, EchoText panel hidden
- Both keep `tt-clean` on

### ViewModel (`ui/chat/ChatViewModel.kt`)

- Takes `characterId` from the nav argument
- On enter: `selectCharacter(characterId)`, then open EchoText for that character, then apply the current tab's mode
- `selectTab(tab)` → `setMode(...)`
- `UiState`: `Loading`, `Ready(character, selectedTab)`, `Error(type)`
- Remembers the last tab per character for the session

## Notes

- **From 1.6:** `ChatScreen` / `ChatViewModel` already exist (top bar with back, avatar and name; Story mode only). The WebView is hosted by the app shell, not by this screen; Chat is transparent below its bar. `CharacterRepository.openChat()` returns once SillyTavern posts `chatOpened` (chat drawn and settled) and Chat covers the page until then. The shell keeps the WebView at a fixed size below `ChatChromeHeight` — a tab row added in 1.7 must be included in that height, or the WebView will be resized (and blank) on every tab switch
- If EchoText can't be opened for a specific character from script, fall back to: select the character in SillyTavern (EchoText follows the active character, if it does), and record that limitation here
- Verify on device: switching tabs is instant, sending a message works in both tabs, leaving and re-entering Chat doesn't reload

## References

- @context/coding-standards.md
- @context/specs/phase1-5-clean-theme-spec.md
- @context/specs/phase1-6-home-character-list-spec.md
- EchoText: https://github.com/mattjaybe/SillyTavern-EchoText
