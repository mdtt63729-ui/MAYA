# MAYA Wake Word Deep Fix v1.3.4

## Problem
The custom wake word setting was persisted, but detection could still miss the configured name. Android SpeechRecognizer can return partial results and multiple recognition alternatives; the previous implementation only examined the first result and injected unrelated hard-coded MAYA/MJ aliases.

## Fixes
- Custom wake word is now the only primary detection vocabulary.
- Supports the configured word itself plus generated forms such as `hey <word>`, `hello <word>`, and Bengali equivalents.
- Checks all recognition alternatives, not only the first candidate.
- Uses partial-result detection while the user is speaking.
- Adds Android recognizer biasing strings for the configured wake word variants.
- Selects a wake-recognition locale from the script of the configured word (Bengali/Hindi/Tamil/Telugu/Malayalam/Punjabi/English).
- Adds conservative Levenshtein matching for short recognition variations such as `mya` vs `maya`.
- Restart/recreates the active SpeechRecognizer immediately when the user edits the wake word.
- Handles recognizer-busy/client errors with a longer controlled restart delay.
- Keeps Gemini Live microphone ownership isolated from wake-word recognition.

## Expected examples
If Settings -> Wake Word contains:
- `Maya` -> `Maya`, `Hey Maya`, `Hello Maya`
- `Jarvis` -> `Jarvis`, `Hey Jarvis`, `Hello Jarvis`
- `MIRA` -> `Mira`, `Hey Mira`, `Hello Mira`
- `মায়া` -> `মায়া`, `হে মায়া`, `হ্যালো মায়া`

Changing the field while Wake Word is enabled immediately restarts the recognizer with the new phrase.

## Important platform note
This implementation uses Android SpeechRecognizer as a phrase detector. It is not equivalent to a dedicated hardware low-power keyword-spotting engine used by some system assistants, so absolute screen-off/hardware wake-word reliability requires a dedicated on-device wake-word model. The current implementation is nevertheless designed to make the configured phrase functional with Android's available recognizer and partial results.
