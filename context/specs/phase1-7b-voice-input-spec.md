# Phase 1.7b — Voice Input

## Overview

Talk instead of type. A mic button sits next to the send button in the chat screen's message input (SillyTavern's main chat). Tapping it starts Android's **on-device** speech recognizer, and the words appear in the chat input as you speak. Pauses of a few seconds don't end recording. You review the text and tap send yourself. No audio leaves the phone and no paid API is involved.

This replaces the Speech Recognition extension's mic inside the app. That extension uses the browser's recognizer, which sends audio to Google and stops after a very short pause.

The app doesn't type into SillyTavern directly. Recognized text goes to the page through the JS bridge (1.4), which fills SillyTavern's own input the same way typing would.

**Scope:** main chat only. 1.7 (Text is the chat, EchoText thread) is on hold until EchoText's thread storage is fixed. When 1.7 resumes, the EchoText input gets the same mic; the bridge's `target` field already allows for it (see Bridge additions).

## Requirements

### Investigation first (do this before writing code)

- Confirm the main chat's input and send button selectors **(verify)**
- Confirm which event(s) SillyTavern listens for so programmatic text is treated like typing (e.g. `input`), so the box resizes and send works **(verify)**
- Confirm the Speech Recognition extension's mic button selector **(verify)**
- Check whether the on-device recognizer honors `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` / `EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS` on the Pixel 8 **(verify)** — this decides whether the restart approach under "Pauses" is needed
- Write the findings into this spec's Notes section and add the names to `st-selectors.js`
- EchoText's input and send button selectors are already recorded in the 1.7 spec's Notes; they're not needed until 1.7 resumes

### Speech recognizer (`data/speech/`)

- `OnDeviceSpeechInput` wraps `SpeechRecognizer.createOnDeviceSpeechRecognizer` (API 31, the app's min SDK)
    - Created, used, and destroyed on `Dispatchers.Main`
    - Request partial results (`EXTRA_PARTIAL_RESULTS`) and the device's default language
    - Exposes `listen(): Flow<SpeechEvent>`; cancelling the flow stops and releases the recognizer
- Never fall back to a cloud recognizer. If on-device recognition isn't available, voice input is unavailable

### Domain (`domain/`)

- `SpeechEvent` sealed class: `Listening`, `Partial(text)`, `Final(text)`, `Ended`, `Error(type)`
- `SpeechError` types: `Unavailable` (`isOnDeviceRecognitionAvailable()` false), `LanguagePackMissing`, `NoMatch`, `PermissionDenied`, `Busy`, `Other`
- `SpeechInputRepository` interface (implemented by `OnDeviceSpeechInput`)
- `IsVoiceInputAvailableUseCase`, `ListenForSpeechUseCase`

### Bridge additions (1.4)

- `bridge.js` adds a mic button (`#tt-mic`) next to the main chat's send button
    - Only while `tt-clean` is on (hidden in Advanced)
    - Not added at all if Kotlin reports voice input as unavailable
    - Min 48dp-equivalent touch target; uses the page's own icon color, accent color only while listening
- JS → Kotlin: `micPressed` — `{ target: "story" | "text" }`
    - Only `"story"` (the main chat) is sent in 1.7b. `"text"` is reserved for the EchoText input when 1.7 resumes, so the message format doesn't change later
- Kotlin → JS (through `BridgeClient`, arguments JSON-encoded):
    - `setVoiceInputAvailable(available)` — show or skip the mic button
    - `beginVoiceInput(target)` — remember the input's current text as the base; add `tt-mic-listening` to the button
    - `updateVoiceInput(text)` — set the input to base + space + text and fire the verified input event (used for partial and final results)
    - `endVoiceInput()` — remove `tt-mic-listening`, leave the text in place, focus the input with the cursor at the end
- Add `MicPressed` to the `BridgeMessage` sealed class
- In `clean-theme.css`, under `body.tt-clean`:
    - Hide the Speech Recognition extension's mic button, so the in-app mic is the only one. It stays visible in Advanced and on desktop
    - EchoText's floating button and window are already hidden by the 0.1.12 fix (`body.tt-clean ~ #et-portal > *`, all EchoText UI in the app until 1.7 resumes), so nothing covers the mic or send button. Nothing to add here

### Behavior (`ChatViewModel`, as it exists from 1.6)

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
- Stop listening when leaving the Chat screen or when the app goes to the background
- `UiState` gains `voiceInput: Idle | Listening | Unavailable`

### Errors

- Short messages through a `SnackbarHost` on the Chat screen; no dialogs:
    - `PermissionDenied` → "Microphone access is off. You can turn it on in Settings." + Settings action (app details page)
    - `LanguagePackMissing` → "Download the speech pack for your language to use voice input." (on API 33+, try `triggerModelDownload` first **(verify)**)
    - `NoMatch` → "Didn't catch that. Try again."
    - `Unavailable` → mic button isn't shown; nothing to report
- Every error returns the mic to idle and removes `tt-mic-listening`

### Tests

- `BridgeMessage` parsing for `micPressed` (valid `"story"`, valid `"text"`, unknown target, missing target)
- `BridgeClient` escaping for recognized text containing quotes, backslashes, newlines, and emoji
- `ChatViewModel` voice state: idle → listening → idle on stop, on error, after the pause tolerance runs out, and when leaving Chat
- Pause handling (with a fake `SpeechInputRepository` and a test clock): a 2 s pause keeps listening and joins both parts; a 5 s pause ends the session; `NoMatch` on a restart pass doesn't surface an error
- Compose UI test: the snackbar messages for `PermissionDenied` (with its Settings action), `LanguagePackMissing` and `NoMatch`
- Version bump per `CLAUDE.md`

## Notes

### Findings (verified 2026-11-18, SillyTavern 1.19.0 in the app's WebView, emulator)

- **Input row:** `#send_form` > `#nonQRFormItems` > `#leftSendForm` (`#options_button`, `#extensionsMenuButton`), `#send_textarea`, `#rightSendForm` (`#microphone_button`, the hidden `#stscript_*` / `#mes_stop` / `#mes_impersonate` / `#mes_continue`, then `#send_but`). The app's `#tt-mic` goes right before `#send_but`, borrowing the row's icon classes (`fa-solid fa-microphone interactable`); measured 48 × 48 CSS px next to the 48 × 48 send button
- **Typing event:** SillyTavern fills its own input by setting the value and dispatching `new Event('input', { bubbles: true })` (impersonate, restoring a draft); its `input` listener (`RossAscends-mods.js`) resizes the box and saves the draft, and send reads `$('#send_textarea').val()` directly. Checked live: 300 characters set this way grew the box 48 → 319 px and send would read them; clearing shrank it back. Nothing was sent
- **Speech Recognition extension's mic:** `#microphone_button` (`.speech-toggle`) in `#rightSendForm`; hidden under `body.tt-clean`, back with clean mode off. It is added after SillyTavern's `APP_READY` (extensions load later), so it may not exist right after a page load
- **Listening colour:** `var(--SmartThemeQuoteColor)` (the theme's quote colour; orange on this server), so the stylesheet still sets no colours of its own
- **Emulator (Medium_Phone_API_35):** `isOnDeviceRecognitionAvailable()` is true, but the English pack isn't installed: the recognizer fails with `ERROR_LANGUAGE_UNAVAILABLE` (13) → `LanguagePackMissing`, and `triggerModelDownload` (API 33+) brings up the system's own "Download English (US) update (63 MB)" dialog — so the **(verify)** on `triggerModelDownload` is answered: it works and the system shows its own prompt. Our snackbar runs underneath it and times out unseen, which is fine (the system dialog says the same)
- **Pauses on the Pixel 8 (user test, recognizer log):** the recognizer did **not** end a pass at a short pause — passes ran 16 s and 15 s through the user's pauses, then ended on their own, which looks like a length limit per pass rather than silence. So the restart approach is what makes longer dictation work. A restart takes about 0.75 s (pass end → next "start detection"); a word spoken exactly then could be missed — not noticed in the user's test
- **Cold start (Pixel 8):** the first pass after the speech model was unloaded took 2.4 s from `startListening` to listening. The pause tolerance originally ran from the tap, which left about 1.6 s to start talking; it now runs from the recognizer's first `onReadyForSpeech` (`Listening`), with a 10 s cap if it never gets ready
- **Spoken punctuation:** saying "period" or "exclamation mark" is transcribed as words, not symbols (user test) — the on-device recognizer doesn't convert spoken punctuation. Possible follow-up, not in this spec: `RecognizerIntent.EXTRA_ENABLE_FORMATTING` (API 33) for automatic punctuation and capitals
- **Errors not named above** (`Busy`, `Other`) show "Voice input stopped. Try again." so a failure is never silent
- **Theme:** snackbar actions use `inversePrimary`, which the palette didn't set, so the Settings action was Material's default purple. Both colour schemes now set `inversePrimary` / `inverseSurface` / `inverseOnSurface` from the other theme's amber and neutrals

- On-device recognition does not need HTTPS. The audio is captured natively, not by the WebView, so it works over plain `http://` on Tailscale (unlike the WebView mic in 1.3)
- Verify on device: put the phone in airplane mode with Tailscale off and confirm the transcription still appears in the input (proves it's on-device; the send itself will fail, which is expected)
- Verify on device: the mic shows next to the main chat's send button, the cursor ends up after the text, sending works normally afterwards, Advanced shows no in-app mic button (the extension's mic shows there instead)
- Verify on device: say a sentence, pause about 3 s, say another — both end up in the input. Then pause about 6 s — recording stops
- Verify on device: no EchoText UI over the chat or the mic in the app (0.1.12 fix), and EchoText still appears on desktop
- If restarts play a start/stop sound on each pass, or drop words at the seam, write that down here and raise it before working around it
- Auto-send and continuous (hands-free, always-on) listening are out of scope for this spec
- **When 1.7 resumes:** add `#tt-mic` to EchoText's input row (`.et-input-bar`, next to `#et-send-btn`) with `target: "text"`, and stop listening when switching between the thread and Story view

## References

- @context/coding-standards.md
- @context/ui-guidelines.md
- @context/specs/phase1-4-js-bridge-spec.md
- @context/specs/phase1-6-home-character-list-spec.md
- @context/specs/phase1-7-chat-screen-spec.md (EchoText findings, for later)