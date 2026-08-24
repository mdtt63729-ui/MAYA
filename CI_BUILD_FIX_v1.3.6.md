# MAYA 1.3.6 CI Build Fix

## Failure
GitHub Actions failed during `:app:compileDebugKotlin` in `MJVoiceManager.kt:564` because `Intent.putExtra()` has no overload accepting the `List<String>` returned by `wakeWordVariants(...).toList()`.

## Fix
Replaced the invalid extra write with:

`putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(wakeWordVariants(currentSettings.wakeWord)))`

This uses the Android Bundle type expected by `EXTRA_BIASING_STRINGS` and preserves the configured wake-word variants for speech-recognition biasing.

## Version
- versionName: `1.3.6`
- versionCode: `18`

## Validation
The source-level failure is corrected. A local Gradle build could not be executed in this environment because the project intentionally relies on GitHub Actions to provision Gradle 9.3.1 and the container has neither a Gradle executable nor network access to download it. The provided CI workflow remains unchanged and will provision Gradle before running `testDebugUnitTest`.
