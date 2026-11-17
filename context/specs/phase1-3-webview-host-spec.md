# Phase 1.3 — WebView Host

## Overview

One WebView, created once and kept alive for the whole app session, showing the user's SillyTavern. Screens attach and detach it — they never create their own and never reload it. Ports the file upload and permission handling from the Aegis wrapper.

## Requirements

### WebViewHolder (`data/webview/WebViewHolder.kt`, `@Singleton`)

- Creates the WebView lazily with a `MutableContextWrapper(applicationContext)`
- `attach(activity)` swaps the wrapper's base context to the Activity; `detach()` swaps back to the application context
- Loads `ServerConfig.baseUrl` on first creation; reloads only when the saved URL changes
- Exposes `pageState: StateFlow<PageState>` — `Loading`, `Ready`, `Error(type)` where type is `Unreachable`, `AuthFailed`, `HttpError(code)`
- WebView settings:
    - `javaScriptEnabled = true`, `domStorageEnabled = true`
    - `mediaPlaybackRequiresUserGesture = false` (voice notes later)
    - Cookies accepted and flushed on `onPause`
    - `setWebContentsDebuggingEnabled(BuildConfig.DEBUG)`

### WebViewClient

- `onReceivedHttpAuthRequest` → proceed once with stored credentials; if it repeats for the same request, cancel and set `Error(AuthFailed)`
- Main-frame `onReceivedError` → `Error(Unreachable)`; main-frame `onReceivedHttpError` → `Error(HttpError(code))`
- Links to other hosts open in the system browser, not in the WebView
- `onPageFinished` emits a `pageFinished` event (1.4 and 1.5 hook in here)

### WebChromeClient (ported from Aegis — keep its ISC notice at the top of ported files)

- `onShowFileChooser` → system file picker via an Activity Result launcher; returns the result to the `ValueCallback` (and `null` on cancel, so the page isn't stuck)
- `onPermissionRequest` → for audio capture, request `RECORD_AUDIO`; for video capture, request `CAMERA`; grant only what the user approved
- Add `RECORD_AUDIO` and `CAMERA` to the manifest (requested at runtime only when the page asks)

### Composable (`ui/components/SillyTavernWebView.kt`)

- `AndroidView` that attaches the holder's WebView on enter and removes it from its parent on dispose (without destroying it)
- Overlays for `Loading` (progress indicator) and `Error` (message + Retry + "Server settings" button that navigates to Connect)
- `BackHandler`: if the WebView can go back, go back in the WebView; otherwise let navigation handle it

## Notes

- Use this composable as a temporary full-screen view on Home to verify it works; 1.6 replaces Home with the real list
- Mic access in WebView requires HTTPS (secure origin). Over plain `http://` the mic prompt won't fire — that's expected until the user switches to a `tailscale serve` HTTPS address
- Verify on device: file upload (character import), back gesture, rotating the phone doesn't reload, backgrounding and returning doesn't reload

## References

- @context/coding-standards.md
- @context/specs/phase1-2-connect-screen-spec.md
- Aegis wrapper reference: https://github.com/aegis-plus/sillytavern-android (ISC)
