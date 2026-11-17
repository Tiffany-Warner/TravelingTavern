# Phase 1.7b — Voice Input

## Overview

Talk instead of type. A mic button sits next to the send button in both chat tabs (Text and Story). Tapping it starts Android's **on-device** speech recognizer, and the words appear in the chat input as you speak. Pauses of a few seconds don't end recording. You review the text and tap send yourself. No audio leaves the phone and no paid API is involved.

This replaces the Speech Recognition extension's mic inside the app. That extension uses the browser's recognizer, which sends audio to Google and stops after a very short pause.

The app doesn't type into SillyTavern directly. Recognized text goes to the page through the JS bridge (1.4), which fills SillyTavern's own input the same way typing would.

## Requirements

### Investigation first (do this before writing code)

- Confirm the Story tab's input and send button selectors (SillyTavern's main chat) **(verify)**
- Confirm EchoText's input and send button selectors (Text tab) **(verify — may already be found in 1.7)**
- Confirm which event(s) SillyTavern and EchoText listen for so programmatic text is treated like typing (e.g. `input`), so the box resizes and send works **(verify)**
- Confirm the Speech Recognition extension's mic button selector **(verify)**
- Check whether the on-device recognizer honors `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` / `EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS` on the Pixel 8 **(verify)** — this decides whether the restart approach under "Pauses" is needed
- Write the findings into this spec's Notes section and add the names to `st-selectors.js`

### Speech recognizer (`data/speech/`)

- `OnDeviceSpeechInput` wraps `SpeechRecognizer.createOnDeviceSpeechRecognizer` (API 31+)
    - Created, used, and destroyed on `Dispatchers.Main`
    - Request partial results (`EXTRA_PARTIAL_RESULTS`) and the device's default language
    - Exposes `listen(): Flow<SpeechEvent>`; cancelling the flow stops and releases the recognizer
- Never fall back to a cloud recognizer. If on-device recognition isn't available, voice input is unavailable

### Domain (`domain/`)

- `SpeechEvent` sealed class: `Listening`, `Partial(text)`, `Final(text)`, `Ended`, `Error(type)`
- `SpeechError` types: `Unavailable` (API < 31 or `isOnDeviceRecognitionAvailable()` false), `LanguagePackMissing`, `NoMatch`, `PermissionDenied`, `Busy`, `Other`
- `SpeechInputRepository` interface (implemented by `OnDeviceSpeechInput`)
- `IsVoiceInputAvailableUseCase`, `ListenForSpeechUseCase`

### Bridge additions (1.4)

- `bridge.js` adds a mic button (`#tt-mic`) next to the send button of the Story input and the EchoText input
    - Only while `tt-clean` is on (hidden in Advanced)
    - Not added at all if Kotlin reports voice input as unavailable
    - Min 48dp-equivalent touch target; uses the page's own icon color, accent color only while listening
- JS → Kotlin: `micPressed` — `{ target: "story" | "text" }`
- Kotlin → JS (through `BridgeClient`, arguments JSON-encoded):
    - `setVoiceInputAvailable(available)` — show or skip the mic button
    - `beginVoiceInput(target)` — remember the input's current text as the base; add `tt-mic-listening` to the button
    - `updateVoiceInput(text)` — set the input to base + space + text and fire the verified input event (used for partial and final results)
    - `endVoiceInput()` — remove `tt-mic-listening`, leave the text in place, focus the input with the cursor at the end
- Add `MicPressed` to the `BridgeMessage` sealed class
- In `clean-theme.css`, hide the Speech Recognition extension's mic button under `body.tt-clean`, so the in-app mic is the only one. It stays visible in Advanced and on desktop

### Behavior (`ChatViewModel` from 1.7)

- Tap mic while idle → check `RECORD_AUDIO` (already in the manifest from 1.3); request it if needed, then start listening
- Tap mic while listening → stop; keep what was recognized so far
- Partial results update the input live; the final result replaces the last partial
- Tapping again after recording has stopped adds more onto the end

### Pauses

Pausing for a few seconds mid-thought must not end recording.

- `PAUSE_TOLERANCE_MS = 4000` (one constant; not a user setting yet)
- First, request longer silence through the silence-length extras (set to `PAUSE_TOLERANCE_MS`). If the investigation shows the recognizer honors them, that's all that's needed
- If it doesn't (the recognizer still ends after a short pause), keep the session alive in the app:
    - When the recognizer ends on silence or `NoMatch`, and the user hasn't tapped stop, start a new recognizer pass right away
    - Keep the text recognized so far: committed text = each pass's final result joined with spaces; the input shows base + committed text + current partial
    - Track time since the last recognized word (partial or final). Once it passes `PAUSE_TOLERANCE_MS`, stop for real and return to idle
    - `NoMatch` during a restart pass is not an error; only show "Didn't catch that" if the whole session recognized nothing
- The mic stays in the listening state through restarts — no flicker between idle and listening
- This is not continuous listening: a session always ends after `PAUSE_TOLERANCE_MS` of silence or when the user taps stop
- Never auto-send. The user reviews the text and taps send
- Stop listening when leaving the Chat screen, switching between Text and Story, or the app goes to the background
- `UiState` gains `voiceInput: Idle | Listening | Unavailable`

### Errors

- Short messages through a `SnackbarHost` on the Chat screen; no dialogs:
    - `PermissionDenied` → "Microphone access is off. You can turn it on in Settings." + Settings action (app details page)
    - `LanguagePackMissing` → "Download the speech pack for your language to use voice input." (on API 33+, try `triggerModelDownload` first **(verify)**)
    - `NoMatch` → "Didn't catch that. Try again."
    - `Unavailable` → mic button isn't shown; nothing to report
- Every error returns the mic to idle and removes `tt-mic-listening`

### Tests

- `BridgeMessage` parsing for `micPressed` (valid, unknown target, missing target)
- `BridgeClient` escaping for recognized text containing quotes, backslashes, newlines, and emoji
- `ChatViewModel` voice state: idle → listening → idle on stop, on error, after the pause tolerance runs out, and when switching tabs
- Pause handling (with a fake `SpeechInputRepository` and a test clock): a 2 s pause keeps listening and joins both parts; a 5 s pause ends the session; `NoMatch` on a restart pass doesn't surface an error

## Notes

- On-device recognition does not need HTTPS. The audio is captured natively, not by the WebView, so it works over plain `http://` on Tailscale (unlike the WebView mic in 1.3)
- Verify on device: put the phone in airplane mode with Tailscale off and confirm the transcription still appears in the input (proves it's on-device; the send itself will fail, which is expected)
- Verify on device: works in both Text and Story tabs, cursor ends up after the text, sending works normally afterwards, Advanced shows no in-app mic button (the extension's mic shows there instead)
- Verify on device: say a sentence, pause about 3 s, say another — both end up in the input. Then pause about 6 s — recording stops
- If restarts play a start/stop sound on each pass, or drop words at the seam, write that down here and raise it before working around it
- Auto-send and continuous (hands-free, always-on) listening are out of scope for this spec

## References

- @context/coding-standards.md
- @context/ui-guidelines.md
- @context/specs/phase1-4-js-bridge-spec.md
- @context/specs/phase1-7-chat-screen-spec.md
