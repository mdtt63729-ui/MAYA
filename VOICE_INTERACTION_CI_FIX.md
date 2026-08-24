# MAYA VoiceInteraction CI Fix — 1.2.4

## Root cause
GitHub Actions failed during `:app:processDebugResources` because `res/xml/voice_interaction_service.xml` used the non-existent Android resource attribute `android:supportsLaunchVoiceActivity`.

Android's `VoiceInteractionService` metadata supports `supportsAssist`, `supportsLaunchVoiceAssistFromKeyguard`, and `supportsLocalInteraction`; the former attribute was incorrect.

## Fixes
- Removed `android:supportsLaunchVoiceActivity`.
- Corrected the manifest metadata key to `android.voice_interaction`.
- Added `android:supportsLocalInteraction="true"`.
- Added a compatibility `MayaRecognitionService` and recognition-service metadata so the VoiceInteractionService declaration remains valid on older Android releases.
- Kept Gemini as MAYA's primary voice pipeline; the compatibility recognition endpoint fails closed instead of pretending to be a second speech engine.
- Bumped app version to `1.2.4` / versionCode `7`.

## Verification
- No remaining `supportsLaunchVoiceActivity` references.
- No remaining `android.voice_interaction_service` metadata key.
- XML resources parse successfully.
- ZIP archive integrity verified after packaging.

Android references: official `VoiceInteractionService` metadata uses the `android.voice_interaction` key and the supported metadata attributes documented by Android/AOSP.
