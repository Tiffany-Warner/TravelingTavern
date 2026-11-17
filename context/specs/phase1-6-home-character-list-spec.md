# Phase 1.6 — Home: Character List

## Overview

A native list of the user's SillyTavern characters, fed by the JS bridge. Tapping a character opens Chat for them. This replaces the temporary full-screen WebView on Home from 1.3.

## Requirements

### Screen (`ui/home/HomeScreen.kt`)

- Top app bar: "TravelingTavern" title, Settings action
- `LazyColumn` of character rows:
    - Circular avatar (Coil, shared OkHttp client so auth applies), placeholder initial if it fails
    - Name
    - Last chat time, relative ("2h ago", "Yesterday")
- Sorted by most recent chat first
- Pull to refresh → `BridgeClient.requestCharacters()`
- States:
    - Loading (bridge not ready yet)
    - Empty: "No characters yet. Create or import one in SillyTavern."
    - Unreachable / auth failed: same messages and actions as the WebView error overlay
- The WebView stays alive in the background (not visible) so the bridge keeps working — attach it in a zero-size or hidden host on this screen **(confirm the approach keeps JS running; a hidden view must not be detached from the window)**

### ViewModel (`ui/home/HomeViewModel.kt`)

- Observes `BridgeRepository.characters` and `isReady`, and `WebViewHolder.pageState`
- `UiState`: `Loading`, `Error(type)`, `Empty`, `Success(characters)`
- `refresh()`
- `onCharacterClick(id)` emits a one-shot navigation event to Chat with `characterId`

### Domain

- `GetCharactersUseCase` — sorts by `lastChatAt` desc, nulls last

## Notes

- Unread badges come in Phase 2 with notifications — not here
- Later, the list may come from a TravelingTavern-Bridge endpoint instead of the JS bridge. Keep the source behind `CharacterRepository` so swapping it doesn't touch the UI

## References

- @context/coding-standards.md
- @context/features/phase1-4-js-bridge-spec.md
