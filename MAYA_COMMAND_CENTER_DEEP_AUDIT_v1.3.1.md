# MAYA Command Center Deep Audit — v1.3.1

Base: MAYA-CI-Fixed-v4-WakeWord-Fixed-v1.3.0

## Fixed
- Added missing functional Voice Engine controls: enable, voice, language.
- Added missing Personality controls: sarcasm, emotional expressiveness, formality, greeting behavior.
- Added ORB controls: animation speed, idle breathing, battery-saver animation, audio-reactive orb.
- Added Automation controls: scheduled-actions gate, confirmation policy, retry count, device-context policy.
- Added Memory controls: save history, remember conversations, memory depth, biometric action confirmation.
- Added perception control: screenshot context.
- Added Performance/Developer controls: background processing, live transcript, latency metrics, token usage, action logs, strict notification privacy.
- Removed fake/unwired Command Center rows for camera awareness, parallel execution, offline wake-word model, AI data sharing, and unsupported diagnostic placeholders. Unsupported capabilities are no longer presented as settings that appear actionable.
- Audio-reactive ORB setting now actually gates the reactive energy path.
- Direct chat command execution now respects the central ActionRiskEngine, action confidence threshold, confirmation policy, and biometric policy instead of bypassing them.

## Architecture principle
Every visible Command Center control is either connected to a runtime consumer or removed from this screen if the current Android build does not have a real implementation. No fake-success controls are presented.
