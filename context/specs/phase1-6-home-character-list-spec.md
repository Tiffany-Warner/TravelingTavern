# Phase 1.6 — Chats & Characters Tabs

## Overview

Fill the Chats and Characters tabs with real data from the JS bridge. **Chats** is a messaging-style list of conversations, most recent first. **Characters** is a portrait grid of the whole library. Tapping a character in either opens Chat. This replaces the temporary full-screen WebView from 1.3.

## Requirements

### Shared

- Data comes from `BridgeRepository.characters` (1.4) through a `CharacterRepository`
- Avatars load with Coil using the shared OkHttp client so auth applies; fallback is a round tile with the character's initial
- The WebView stays alive while these tabs are showing so the bridge keeps working — host it hidden behind the tab content **(confirm a hidden-but-attached WebView keeps running JS; it must stay attached to the window)**
- Disconnected / auth failed: inline banner at the top of the tab ("Can't reach your server — Retry", "Login failed — Server settings"). Nothing is shown when connected

### Chats tab (`ui/chats/ChatsScreen.kt`)

- Top app bar: plain "Chats" title
- `LazyColumn`, one row per chat (**changed 2026-11-19, user:** was one row per character; a character with two chats now has two rows, and a tap opens that row's chat file):
    - 48dp round avatar
    - Name (`titleMedium`)
    - Last message preview, one line, ellipsized (`bodyMedium`, secondary color) **(verify how to get the last message per character; if not available cheaply, show nothing in Phase 1 and note it here)**
    - Trailing relative time ("2h", "Yesterday", "Mar 3")
- Sorted by last chat time, newest first
- Pull to refresh → `requestCharacters()`
- Empty state: "No chats yet." + button "Browse characters" → Characters tab
- Unread dots come in Phase 8 (proactive messages)

### Characters tab (`ui/characters/CharactersScreen.kt`)

- Top app bar: "Characters" title + search action that expands to a search field (filters by name, client-side)
- `LazyVerticalGrid`, 2 columns, portrait cards (3:4), avatar image filling the card (`ContentScale.Crop`), name over a subtle bottom scrim
- Cards load the **full-size** avatar, not the thumbnail from 1.4. SillyTavern's thumbnails are small and look blurry at card size. Add a `fullAvatarUrl` (or derive it in `CharacterRepository`) **(verify the full-size avatar URL format, e.g. `/characters/<encoded avatar>`)**. The Chats list's 48dp avatars keep using the thumbnail
- Crossfade images in; keep a placeholder surface at the card's 3:4 size so the grid doesn't jump while images load
- Sorted alphabetically
- FAB (`+`) opens a bottom sheet with two options, both "Coming soon" in Phase 1:
    - Create character (Phase 3 creator)
    - Import card
- Empty state: "No characters yet. Create or import one in SillyTavern." + button "Open full SillyTavern" → Advanced

### ViewModels

- `ChatsViewModel` — `UiState`: `Loading`, `Error(type)`, `Empty`, `Success(chats)`; `refresh()`; one-shot nav event to Chat
- `CharactersViewModel` — `UiState`: `Loading`, `Error(type)`, `Empty`, `Success(characters, query)`; `setQuery()`; one-shot nav event to Chat

### Domain

- `GetRecentChatsUseCase` — characters with a last chat, sorted by `lastChatAt` desc
- `GetCharacterLibraryUseCase` — all characters, sorted by name, filtered by query

## Notes

- **Verified (SillyTavern 1.19.0):** full-size avatar is `/characters/<URL-encoded avatar>`; the last message per chat comes from `POST /api/chats/recent` (`mes`, `last_mes`, `avatar`, `chat_items`; body `{ max }`), read through the bridge; character IDs are avatar file names. Details in the 1.6 entry of `current-feature.md`
- **Verified (SillyTavern 1.19.0): "# Msg. to Load (0 = All)"** — User Settings, `power_user.chat_truncation`, input `#chat_truncation` (0–1000, step 5). **Default 100** (`power-user.js`); this server is on the default. `printMessages()` (`script.js`) draws only the last `chat_truncation` messages (0 = all) with a "Show more messages" button above them, scrolls to the bottom, then 200 ms later (`debounce_timeout.short`) runs `scrollOnMediaLoad()`, which scrolls again once the chat's images/videos have loaded — but only if they load within 1 s. So opening a chat draws at most 100 messages by default and keeps moving for up to ~1.2 s afterwards; a user who sets 0 (all) or a high value makes long chats slower to draw. `chatOpened` waits for that movement to stop (bridge cap 2 s) and the app reveals the chat after at most 3 s regardless
- **Decision:** the WebView is hosted once at the app shell (attached all session, covered by opaque tabs, hidden from accessibility, back handling only when revealed), not per screen
- **Decision:** Chat in 1.6 is the start of 1.7's `ChatScreen` / `ChatViewModel` — Story mode only, top bar with back + avatar + name — so 1.7 extends it instead of replacing a placeholder
- Later, data may come from a TravelingTavern-Bridge endpoint instead of the JS bridge. Keep the source behind `CharacterRepository` so swapping it doesn't touch the UI
- SillyTavern character IDs are avatar file names **(verify)**

## References

- @context/ui-guidelines.md
- @context/coding-standards.md
- @context/specs/phase1-4-js-bridge-spec.md
- @context/specs/phase1-1b-bottom-navigation-spec.md