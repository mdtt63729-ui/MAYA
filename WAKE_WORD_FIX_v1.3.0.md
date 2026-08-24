# MAYA Wake Word Fix v1.3.0

## Problem
Wake-word listening was using Android SpeechRecognizer with `EXTRA_PARTIAL_RESULTS=false`, so MAYA waited for a final utterance/silence before checking `Hey MAYA`. That is not suitable for phrase-style wake detection.

## Fix
- Enable partial recognition results.
- Detect the configured wake phrase from partial results, not only final results.
- Normalize case, punctuation and whitespace before matching.
- Support configured phrase plus common MAYA aliases.
- Cancel the recognizer immediately on a match so Gemini Live can acquire the microphone cleanly.
- Add a generation/activation guard to prevent duplicate activations.
- Add controlled recognizer restart/backoff after timeout/no-match/errors.
- Add Android 11+ package visibility query for `android.speech.RecognitionService`.
- Do not force offline recognition when no offline wake-word model is bundled.
- Require microphone permission before starting wake listening.
- Never run SpeechRecognizer while a Gemini Live session owns the microphone.

## Limitation
This is a platform speech-recognition wake phrase implementation, not a dedicated always-on neural keyword spotter. For true low-power, offline, hardware-style wake-word detection comparable to Google Assistant/Siri, a dedicated on-device keyword spotting model should replace the platform recognizer in a future version.
