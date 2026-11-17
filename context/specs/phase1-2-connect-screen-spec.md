# Phase 1.2 — Connect Screen

## Overview

Let the user point the app at their SillyTavern server, optionally log in, and test the connection. Shown on first launch and whenever the server can't be reached.

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

- URL field with example placeholder `http://100.x.y.z:8000`
- Accepts bare host/IP and adds `http://` if no scheme is given
- Optional "Server requires login" toggle revealing username and password fields
- Test & Save button; Save only enabled after a `Success` result
- Result messages:
    - `Unreachable` → "Can't reach the server. Is Tailscale connected and SillyTavern running?"
    - `AuthRequired` → "This server needs a login" (and reveals the login fields)
    - `AuthFailed` → "Username or password is wrong"
    - `NotSillyTavern` → "That address answered, but it doesn't look like SillyTavern"
    - `InvalidUrl` → "That doesn't look like a valid address"
- On save, navigate to Home and clear Connect from the back stack

### ViewModel (`ui/connect/ConnectViewModel.kt`)

- `UiState`: url, loginEnabled, username, password, `testState` (`Idle`, `Testing`, `Result(ConnectionResult)`)
- `testConnection()`, `save()`

### Start Destination

- On launch: no saved `ServerConfig` → Connect; otherwise → Home

## Notes

- The shared `OkHttpClient` adds basic auth from stored credentials when present. Build that interceptor here — WebView (1.3) and Coil (1.6) reuse the credentials
- SillyTavern basic auth is configured in its `config.yaml` (`basicAuthMode`). Most users on Tailscale won't have it on, so the login toggle defaults off

## References

- @context/coding-standards.md
- @context/features/phase1-1-project-setup-spec.md
