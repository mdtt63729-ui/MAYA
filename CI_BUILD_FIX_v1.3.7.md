# MAYA v1.3.7 — Wake Word + Native Edge Lighting Fix

## Changes

1. Replaced the previous rotating full-screen border implementation with a native perimeter-only lighting renderer.
   - No video/GIF/image asset.
   - Thin luminous screen rim.
   - Multi-pass soft bloom.
   - Travelling cyan/violet light heads around the perimeter.
   - Corner glints synchronized with the travelling light.
   - No diagonal lines crossing the app content.

2. Wake-word engine upgraded.
   - Android 12+ uses `SpeechRecognizer.createOnDeviceSpeechRecognizer()` when available.
   - Falls back to the system recognizer when an on-device recognizer is unavailable.
   - Wake recognition requests offline preference.
   - Wake phrase is transliterated before matching, improving Bengali/Hindi/other-script names.
   - Configured wake word remains the source of truth.
   - Added conservative MAYA recognition variants (`mya`, `may`, `maia`, `mayya`, `mayer`).
   - Partial-result detection remains enabled for low-latency activation.

3. Wake-word lifecycle fixed.
   - Enabling Wake Word starts the microphone foreground controller.
   - Wake mode therefore continues after leaving the Activity.
   - Foreground service uses `START_STICKY` and restores wake mode after service recreation when the setting is enabled.
   - Disabling Wake Word immediately stops the wake recognizer.

4. Default-assistant infrastructure fixed.
   - Declared `MayaVoiceInteractionSessionService` in the manifest because the voice-interaction XML references it.
   - Removed the unsupported `android:supportsLaunchVoiceActivity` attribute from the voice-interaction XML in the previous build.

5. Activation visual timing.
   - Wake/orb activation enters `MJState.ACTIVATING` immediately.
   - Transitions to `CONNECTING` after a short 120 ms hand-off, allowing the edge-light animation to respond instantly.

## Important Android limitation

Android 12+ removed the public `AlwaysOnHotwordDetector` API. A third-party app cannot expose an arbitrary custom wake phrase through the same privileged DSP hotword path used by Google Assistant/Gemini. The implementation therefore uses the best public path: an on-device speech recognizer when the device provides one, with a system recognizer fallback. The microphone is necessarily active during custom wake-word detection; the wake audio is kept on-device when the on-device recognizer is available.

## Verification performed

- XML parsing: AndroidManifest.xml — OK
- XML parsing: voice_interaction_service.xml — OK
- Kotlin structural brace/parenthesis sanity checks: OK for all modified Kotlin files
- Confirmed no remaining `supportsLaunchVoiceActivity` attribute
- Confirmed no remaining rotating `EdgeLighting` implementation
- Confirmed `EXTRA_BIASING_STRINGS` uses `putStringArrayListExtra`

A full Gradle 9.3.1 build could not be executed in the isolated environment because the Gradle distribution is not installed and external network/DNS access is unavailable. The repository's GitHub Actions workflow remains configured to provision Gradle 9.3.1 and Android SDK 36 before running tests/builds.
