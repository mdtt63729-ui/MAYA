# MAYA Settings + Microphone Deep Fix

Version: 1.2.7 / versionCode 10

## Microphone lifecycle fixes
- Gemini Live is now the sole microphone owner during an active voice conversation.
- Android SpeechRecognizer is never started while Gemini Live owns the mic.
- `startSession()` is idempotent; repeated ORB taps/settings recompositions cannot create duplicate AudioRecord/WebSocket sessions.
- `startLiveMicrophone()` refuses to start a second AudioRecord while one is active.
- `stopSession()` now actually releases the microphone and does not silently restart wake-word listening.
- Wake-word listening has an explicit `startWakeWordListening()` / `stopWakeWordListening()` lifecycle.
- SpeechRecognizer recreation is generation-guarded and rate-limited to reduce rapid mic churn in wake-word mode.
- TTS completion/error no longer starts SpeechRecognizer when a Gemini Live session is active.
- Voice Feedback setting no longer starts/stops the microphone. It only controls whether Live audio is played.
- Notification permission is no longer required to activate the core voice assistant; only RECORD_AUDIO gates voice activation.
- Live microphone is kept alive while voice output is muted instead of stopping/restarting AudioRecord.

## Settings fixes
- Runtime voice settings are observed centrally and applied to local TTS/output without restarting the microphone.
- Voice output volume can now be changed on an active AudioTrack.
- Theme setting now controls the app Surface background instead of MainActivity forcing AMOLED black.
- Memory Approval now has real behavior: with approval enabled only explicit "remember" statements are persisted; Private Mode prevents learning.
- Private Mode prevents voice/chat history persistence and audit logging.
- Unauthorized speaker warnings now respect the Unauthorized Warning setting.
- Emotion Context / expressiveness / emotion intensity are now included in the Gemini Live behavioral configuration.
- Background Assistant is blocked when Background Processing is disabled.
- Settings that do not have a real runtime implementation in this build are no longer presented as fake functional switches; they are shown as unavailable capability cards instead (camera awareness, offline wake-word model, scheduled actions, external media-reactive ORB, detailed token/latency/transcript HUD, etc.).
- Default Assistant and Android permission flows remain user-controlled and OS-gated.

## Validation
- All XML resources parse successfully.
- Invalid VoiceInteraction attributes are absent.
- ZIP integrity verified.
- Kotlin syntax diagnostics were checked with the local compiler; Android dependency resolution is intentionally left to GitHub Actions.
