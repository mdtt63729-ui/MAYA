# MAYA v1.3.2 — GitHub Actions Compile Fix Report

Base: MAYA-CI-Fixed-v4-CommandCenter-DeepFixed-v1.3.1

Fixed the exact Kotlin compiler failures reported by GitHub Actions:

1. `GeminiLiveRepository.kt`
   - Replaced unavailable `JsonPrimitive.contentOrNull` usage with `content` plus the existing fallback.

2. `MJVoiceManager.kt`
   - Fixed invalid Kotlin string escapes in wake-word normalization regexes by escaping regex backslashes correctly.

3. `LocalVoiceprintEngine.kt`
   - Converted `decode()` from an expression body to a block body so early `return null` is legal.

4. `MainHomeScreen.kt`
   - Added the missing Accompanist `isGranted` import used by `PermissionState.status.isGranted`.

5. `VoiceContent.kt`
   - Fixed illegal use of `LocalContext.current` inside a `remember {}` calculation lambda.
   - Context is now captured in composition and the repository flow is remembered using that context as the key.

6. `MayaControlCenterScreen.kt`
   - Added the missing `Alignment` import.

7. `ui/settings/Stubs.kt`
   - Fixed invalid `PaddingValues(vertical = ..., bottom = ...)` constructor calls.
   - Uses the supported `PaddingValues(top = ..., bottom = ...)` form.

The reported compile errors are therefore addressed without changing the intended runtime architecture.

Validation performed in this environment:
- XML/resource files remain intact.
- ZIP integrity verified.
- Kotlin parser/static pass did not report syntax/escape/expression-body errors after these changes; Android/Compose dependency resolution cannot be reproduced here because the Android SDK/toolchain is not installed.
