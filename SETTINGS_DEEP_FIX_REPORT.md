# MAYA Settings Deep Fix — v1.2.6

## Core fixes
- Unified SettingsRepository into a process-wide singleton so Settings UI, MainActivity, Orb, voice manager, AI engines, security and services observe the same live StateFlow.
- Removed duplicate voice/background/wake/appearance preference stores from SettingsViewModel.
- Centralized setting normalization/clamping before persistence.
- Theme selection now actually changes Material 3 light/dark/system theme.
- Clear All Data now deletes conversations, memories, routines, audit logs, secure API credentials and unified settings after confirmation.

## Voice settings now affect runtime
- Voice enabled gates voice/wake-word sessions.
- Custom wake phrase is actually matched.
- Active voice maps to Gemini Live prebuilt voices.
- VAD sensitivity affects Gemini Live activity detection.
- Auto barge-in and interruption sensitivity affect local/server interruption behavior.
- Noise cancellation and echo cancellation use Android audio effects when available.
- Microphone gain is applied to PCM input.
- Voice output volume is applied to AudioTrack.
- Response delay and pitch affect the local TTS fallback.
- Auto-language detection changes SpeechRecognizer language pinning.
- Bengali/English code switching, personality and response-style controls are injected into the live/chat system instructions.

## AI / memory / automation
- Adaptive model routing now respects its toggle.
- Multi-agent orchestration can be disabled.
- Context compression changes retained chat context depth.
- History persistence respects Save History, Remember Conversations and Private Mode.
- Memory auto-learn and approval now have distinct behavior.
- Failure recovery respects Self-Correction and Failed-action Retry.
- Risky-action simulation gate is enforced.
- Screen context respects multimodal/screenshot/screen-awareness settings.
- Accessibility and notification action tools are permission/settings gated.

## Notifications
- Added Android NotificationListenerService bridge.
- Notification Reading opens Android notification-access settings when enabled.
- Important notification filtering, privacy mode, whitelist/blacklist and read-aloud behavior are enforced.

## Background assistant
- Background Assistant toggle starts/stops the foreground assistant service and wake-word mode.

## Orb / appearance
- Orb size, reactivity, visualization, glow, particle density, motion profile, animation speed, idle breathing, haptic feedback and blur setting now affect the native liquid-glass Orb.
- Greeting behavior/time-aware greeting settings now affect the home voice UI.

## Validation
- AndroidManifest.xml and voice interaction XML parsed successfully.
- Static Kotlin syntax pass produced no parser/`expecting`/unexpected-token diagnostics; the local environment lacks Android SDK/Gradle dependencies, so a real Android assemble/test was not possible here.
- No reference MP4 is used by the Orb runtime.
