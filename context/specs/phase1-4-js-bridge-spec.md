# Phase 1.4 — JS Bridge Foundation

## Overview

A typed, two-way channel between Kotlin and the SillyTavern page. JS listens to SillyTavern's own events and posts messages to Kotlin; Kotlin calls into SillyTavern through one wrapper. Everything later (character list, notifications deep links, calls) builds on this.

## Requirements

### Injection (`data/bridge/BridgeInstaller.kt`)

- Register `WebViewCompat.addWebMessageListener` with name `TravelingTavern` and allowed origin = the configured server origin only
- If `WEB_MESSAGE_LISTENER` isn't supported, set an error state ("Your Android System WebView is too old — update it in the Play Store")
- On `pageFinished`, inject `assets/web/st-selectors.js` then `assets/web/bridge.js` via `evaluateJavascript`
- Re-inject safely on reload — `bridge.js` guards with `if (window.__ttBridge) return;`

### bridge.js (`assets/web/bridge.js`)

- Waits until `SillyTavern.getContext()` is available and the app has finished loading **(verify which event or flag marks "ready")**
- Posts messages as JSON strings: `{ "v": 1, "type": "...", ... }`
- Messages JS → Kotlin:
    - `ready` — SillyTavern finished loading
    - `characters` — `{ items: [{ id, name, avatarUrl, lastChatAt }] }` (sent on ready and when the list changes)
    - `chatChanged` — `{ characterId }` when the open chat changes
    - `messageReceived` — `{ characterId, messageId }` when a character message arrives in the main chat
    - `error` — `{ message }` for bridge-side failures
- Exposes `window.__ttBridge` with functions Kotlin can call:
    - `requestCharacters()`
    - `selectCharacter(id)`
    - `setMode(mode)` — adds/removes body classes (used by 1.5 and 1.7)
- Uses SillyTavern's `eventSource` and `event_types` for events **(verify exact event names: e.g. `CHAT_CHANGED`, `MESSAGE_RECEIVED`)**
- Uses only names defined in `st-selectors.js` for anything SillyTavern-specific

### Kotlin side (`data/bridge/`)

- `BridgeMessage` sealed class (Kotlin Serialization, `type` as class discriminator) with a fallback `Unknown` for unrecognized types
- `BridgeRepository` exposing `messages: SharedFlow<BridgeMessage>`, `characters: StateFlow<List<CharacterSummary>>`, `isReady: StateFlow<Boolean>`
- `BridgeClient` — the only place that builds JS call strings; all arguments JSON-encoded (never string-concatenated)
    - `requestCharacters()`, `selectCharacter(id)`, `setMode(mode)`

### Domain

- `CharacterSummary` — `id: String`, `name: String`, `avatarUrl: String`, `lastChatAt: Instant?`

### Tests

- `BridgeMessage` parsing: each type, unknown type, malformed JSON, missing fields
- `BridgeClient` escaping: character IDs with quotes, spaces, and unicode produce valid JS

## Notes

- Candidate APIs **(verify against the installed SillyTavern version before use)**: `SillyTavern.getContext()`, `context.characters`, `context.characterId`, `context.eventSource`, `context.event_types`, `context.selectCharacterById`
- Avatar URL candidate: `/thumbnail?type=avatar&file=<encoded avatar>` **(verify)**
- Write the verified names into this Notes section once confirmed

## References

- @context/coding-standards.md
- @context/features/phase1-3-webview-host-spec.md
