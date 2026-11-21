# Phase 1.2 — Connect Screen

## Overview

Let the user point the app at their SillyTavern server, optionally log in, and test the connection. Shown on first launch, and reachable from error states ("Server settings") and Me → Server → "Change server". A temporary connection drop never sends the user here automatically.

## Requirements

### Domain (`domain/`)

- `ServerConfig` model
    - `baseUrl: String` (normalized: scheme + host + optional port, no trailing slash)
    - `username: String?`
    - `hasPassword: Boolean`
- `ConnectionResult` sealed class: `Success`, `Unreachable`, `AuthRequired`, `AuthFailed`, `NotSillyTavern`, `InvalidUrl`
- `ServerConfigRepository` — `observe(): Flow<ServerConfig?>`, `save(config, password: String?)`, `clear()`
- `TestConnectionUseCase` — returns `ConnectionResult` for a candidate URL + credentials

### Data (`data/settings/`, `data/remote/`)

- Server URL saved in DataStore
- Username and password stored encrypted (Tink + Android Keystore); password never exposed back to the UI
- Connection test: `GET {baseUrl}/` with the shared OkHttp client, 10 s timeout
    - 200 with SillyTavern page → `Success` (detect by page title or a known marker **(verify)**)
    - 200 but not SillyTavern → `NotSillyTavern`
    - 401 without credentials → `AuthRequired`; 401 with credentials → `AuthFailed`
    - Connect/timeout/DNS error → `Unreachable`

### Screen (`ui/connect/ConnectScreen.kt`)

Updated 2026-11-19 (user request): one Connect button instead of Test & Save.

- App logo centered near the top (72dp: the launcher icon's layers in a rounded square), then title and subtitle ("Enter the address of your SillyTavern server." — no tailnet wording: any reachable http(s) server works, Tailscale is just the usual setup)
- One address field: IP, hostname or full URL, optional port; placeholder: a Tailscale example address (`connect_url_placeholder`, the user-facing exception in `ai-guidelines.md`). A bare host gets `http://`
- "Server requires authentication" toggle (off by default) revealing username and password fields
- One full-width **Connect** button (disabled while the address is empty). The keyboard's Done on the last field also connects
- Connecting: the button is replaced by a centered spinner with "Connecting…" directly below it; fields are disabled
- Success: the config is saved and the app opens the Chats tab, no confirmation (Connect cleared from the back stack)
- Failure: the Connect button comes back, an inline error shows above it, and every input is kept. Editing an input clears the error
- Error messages:
    - `Unreachable` → "Can't reach the server. Check your connection (and Tailscale, if you use it) and that SillyTavern is running."
    - `AuthRequired` → "This server needs a login" (and turns the authentication toggle on)
    - `AuthFailed` → "Username or password is wrong"
    - `NotSillyTavern` → "That address answered, but it doesn't look like SillyTavern"
    - `InvalidUrl` → "That doesn't look like a valid address"

### ViewModel (`ui/connect/ConnectViewModel.kt`)

- `UiState`: url, loginEnabled, username, password, `status` (`Idle`, `Connecting`, `Failed(ConnectionResult)`)
- `connect()`: test, then save and emit `Saved` on `Success`; otherwise `Failed`

### Start Destination

- On launch: no saved `ServerConfig` → Connect; otherwise → Chats tab

## Notes

- The shared `OkHttpClient` adds basic auth from stored credentials when present. Build that interceptor here — WebView (1.3) and Coil (1.6) reuse the credentials
- SillyTavern basic auth is configured in its `config.yaml` (`basicAuthMode`). Most users on Tailscale won't have it on, so the login toggle defaults off

## References

- @context/coding-standards.md
- @context/specs/phase1-1-project-setup-spec.md