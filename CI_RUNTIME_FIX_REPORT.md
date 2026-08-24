# MAYA CI / Runtime Fix — v1.2.5

Source: user-provided `MAYA-CI-Fixed-v4 (1).zip`.

## Root cause of the reported voice error

The previous voice pipeline was opening a Gemini Live WebSocket with an obsolete/incorrect default model:
`gemini-2.5-flash-native-audio-latest`.

The current Google Gemini Live documentation recommends `gemini-3.1-flash-live-preview` for low-latency realtime dialogue. The previous setup also enabled affective dialog, which is not supported by Gemini 3.1 Flash Live.

## Fixes

- Updated Gemini Live model to `gemini-3.1-flash-live-preview`.
- Removed unsupported `enableAffectiveDialog` from the Gemini 3.1 Live setup.
- Added a documented 2.5 native-audio fallback model constant for future compatibility.
- Converted Live connection status to StateFlow so fast connection/setup events cannot be lost.
- Subscribed to Live state/error flows before opening the WebSocket to eliminate a startup race.
- Added explicit Live protocol-error parsing.
- Added actionable error messages for missing/rejected API keys, unavailable models, rate limits and network failures.
- Added retry UX: after an error, tapping the Orb retries the connection.
- Removed random microphone RMS values; the Orb now receives actual PCM energy from the microphone.
- Removed misleading romantic-partner wording from the voice system prompt; MAYA is a personal AI assistant.
- Cancelled Live connection/error collectors during session shutdown to prevent stale collectors and duplicate state transitions.
- Bumped app version to 1.2.5 / versionCode 8.

## Validation

- All modified Kotlin files have balanced delimiters.
- All Android XML resources parse successfully.
- Archive integrity verified with `zip -T`.

A full Android/Gradle build must still be executed by GitHub Actions because this container does not contain the project's Android SDK/Gradle wrapper environment.
