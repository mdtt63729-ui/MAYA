# MAYA Chat + Voice Routing Upgrade

## Fixed chat composer

The previous implementation applied `imePadding()` to the entire chat column. With edge-to-edge + the IME this reduced the available content height and visually moved the composer far above the keyboard.

The new implementation applies `navigationBarsPadding()` + `imePadding()` only to the composer. The chat viewport keeps its full height and the composer remains attached to the IME. `adjustResize` is also declared on `MainActivity`.

## Premium thinking state

The old `CircularProgressIndicator` has been replaced with a lightweight Compose-only animation: shimmer surface, breathing AI glyph, and staggered pulsing dots. No image assets or image generation are used.

## Separate AI pipelines

### Text Chat
- Gemini is excluded.
- Default provider: OpenRouter.
- Default OpenRouter model: `openrouter/auto`.
- Also supports OpenCode, NVIDIA NIM, and Custom OpenAI-compatible providers.
- Provider fallback stays within non-Gemini chat providers.
- Chat requests set `requireAudio = false`.

### Voice
- Gemini Live remains the realtime voice/audio engine.
- Non-live voice fallback is also forced through the Gemini provider.
- The voice command engine uses a Gemini-only provider map.
- Changing Chat AI settings cannot silently switch the voice provider.

## Settings

AI & Intelligence now exposes:
- Chat AI provider
- Chat model ID
- Voice AI: Google Gemini Live (fixed)

Existing API-key storage remains compatible. Configure the selected non-Gemini provider in API & Secrets before using Chat mode.

## CI/build safety

Version bumped to 1.1.0 / versionCode 2. The project remains compatible with the existing GitHub Actions build flow.
