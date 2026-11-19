# Phase 2 — Texting Style

> **Not started.** Written down so the decisions aren't lost (2026-11-19, user). Builds on the 1.7 story chat. Settle the open items at the end before writing code.

## Overview

A second style for a chat, next to the default story style from 1.7. In texting style a chat **looks** like texting and the character **writes** like they're texting: short, casual messages without narration.

SillyTavern has no texting or story mode. This is a TravelingTavern-only feature (decision 2026-11-19, user, "option C"): SillyTavern on the desktop never changes how it works, and nothing about the style is saved in SillyTavern. Managing it on the desktop would need a separate extension, which is not planned.

| | Story (1.7, default) | Texting (this phase) |
|---|---|---|
| Look | The 1.7 story layout | Compact bubbles, tighter spacing, short-message proportions (`tt-style-text`) |
| Replies | Normal roleplay replies | The texting instruction is added to the prompt, so replies come as short text messages, including swipes and regenerations |

## Requirements

### Style per chat

- **Per chat** (character avatar file + chat file name), stored in the app's DataStore (`ChatStylePreferences`). This is the one deliberate exception to "settings that affect generation live in SillyTavern" (see `project-schema.md`)
- **Default** for a chat with no saved choice: **story** (user decision, 2026-11-19). Existing chats look and reply as they do today until switched to texting
- Switched from the Chat screen's ⋮ overflow menu: **Texting style** while in story, **Story style** while texting
- CSS only for the look, under `body.tt-clean`, built on top of the 1.7 story layout. Message content, swipes and the 1.7 message actions keep working
- Bridge: `setChatStyle(style, instruction?)` swaps the `tt-style-*` class and sets or clears the extension prompt; rejects unknown style names

### The texting instruction

- One app-wide template, stored in the app's DataStore with a default, for example: *"{{char}} and {{user}} are texting on their phones. Reply as {{char}}'s next text message: short and casual, no narration or actions, emoji are fine."* SillyTavern macros (`{{char}}`, `{{user}}`) are left for SillyTavern to fill
- Editable from the Me tab (a Chat section added by this phase). Per-character templates are out of scope
- **How it reaches the prompt:** while a texting-style chat is open in the app's page, `bridge.js` sets it as an in-memory extension prompt through SillyTavern's own API (`setExtensionPrompt('tt_texting', text, IN_CHAT, depth 1, role system)` or equivalent) **(verify: name and arguments in `getContext()`, and that it is never written to the chat file or settings)**. It's part of every reply in that chat, including swipes and regenerations, and is never saved as a message
- **Cleared** when the page switches to another chat, when the chat is switched to story, and when the app leaves Chat. A page reload clears it too, and the installer sets it again (1.7 → After a page reload)
- Fallback if `setExtensionPrompt` isn't usable: `/inject id=tt_texting … ephemeral=true` before each generation. Its cost: it is written into the chat's metadata until the reply ends, so another page could briefly see it. Record which one was used in Notes
- Phase 8 (later): the app reports each chat's style with its heartbeat, and the desktop's headless page sets the same extension prompt before generating, so a proactive message in a texting chat comes out as a text

### Message actions

- Texting style uses the 1.7 message actions (a **tap** on a message opens the actions sheet). No long press, so Android's text selection on long press is untouched

## Open items (from Claude Code's review, 2026-11-19)

Settle these in this spec before building:

1. **Applying the style on a chat opened without a chat file** (from the Characters grid): the app only learns which chat is open when `chatOpened` arrives. Write the order down: `chatOpened` with `chatFile` → read the saved style → `setChatStyle` → lift the cover
2. **Bubble CSS vs SillyTavern's inline editor:** hiding parts of the message for compact bubbles must keep the inline editor's own confirm and cancel buttons visible
3. **A renamed chat file loses its style** (keyed by file name); a new chat (for example started from Advanced) starts as story. Probably acceptable; confirm and note it
4. **Me tab:** add the Chat section (texting template) to the 1.8 spec or to this phase's own UI work

## Tests

- Unit tests: `ChatStylePreferences` (per-chat read and write, story when nothing saved, the template's default and saved value); `ChatViewModel` (saved style applied after `chatOpened`, `toggleChatStyle`, instruction cleared on leaving); `BridgeScript` (`setChatStyle` with and without an instruction: quotes, newlines, macros kept as written)
- Compose UI tests (`ChatContent`): the ⋮ menu shows the right style item and calls back
- Version bump per `CLAUDE.md`

## Verify on Device

- [ ] Each chat remembers its own style across app restarts and after a page reload (look and instruction)
- [ ] In a texting chat, replies (including swipes and regenerations) come as short texts; in a story chat, normal roleplay replies
- [ ] The instruction never appears in the chat file, the chat's metadata or SillyTavern's settings, and the desktop's replies in the same chat stay normal roleplay
- [ ] Switching to story, to another chat, or leaving Chat removes the instruction (the next reply is normal)
- [ ] The 1.7 message actions and the inline editor work in texting style

## Notes

### Decision (2026-11-19, user): app-only (option C)

Texting changes how the character writes, not only the look. The instruction is added only by the app, in its own page, and never saved in SillyTavern, so SillyTavern on the desktop keeps working exactly as before. Considered and rejected: a saved `/inject` in the chat's metadata (option A), which would make desktop replies in that chat texting-style with nothing on the desktop showing why; and an `[OOC: …]` prefix on the user's message, which would be saved in the chat and summarized by Memory Books.

### Decision (2026-11-19, user): its own phase

First planned inside 1.7. Moved out so 1.7 delivers the clean story chat without the extra risk; story is the default anyway, so nothing in 1.7 depends on it.

## References

- @context/project-schema.md (Built Toward the Final Phase: the one exception)
- @context/specs/phase1-7-chat-screen-spec.md
