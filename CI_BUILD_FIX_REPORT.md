# MAYA CI Build Fix — v1.2.1

## GitHub Actions failure diagnosed

The reported CI failure stopped at `:app:compileDebugKotlin` with three actual compilation problems.

### 1. `MayaAgentPlanner.kt` constructor mismatch

`VoiceCommandEngine` requires an `AIOrchestrator`, but `MayaAgentPlanner` was passing an Android `Context`.

Fixed by changing the planner to receive the already-configured non-Gemini `AIOrchestrator` and constructing:

`VoiceCommandEngine(aiOrchestrator)`

`ChatViewModel` now creates one non-Gemini orchestrator and shares it with both the command engine and agent planner. This avoids duplicate provider wiring and keeps Gemini isolated to the voice pipeline.

### 2. `AISettingsScreen.kt` missing `height` import

The screen used `Modifier.height(...)` three times without importing `androidx.compose.foundation.layout.height`. The import has been added.

### 3. Material 3 experimental API

`TopAppBar`/related Material 3 APIs are experimental in the selected dependency set. `AISettingsScreen` now explicitly opts in with `@OptIn(ExperimentalMaterial3Api::class)`. This is a warning-to-cleanup change, not a runtime workaround.

## Additional cleanup

- Version bumped to `1.2.1` / `versionCode 4`.
- Gemini remains excluded from the text-chat command orchestrator.
- Voice continues to use the separate Gemini-only pipeline.
- Existing GitHub Actions workflow is retained.

## Verification performed here

- Confirmed every `MayaAgentPlanner(...)` call uses the new constructor.
- Confirmed no remaining `VoiceCommandEngine(context)` construction exists in the planner path.
- Confirmed `AISettingsScreen.kt` imports `height`.
- Confirmed the experimental Material 3 opt-in is present.
- Confirmed ZIP/source tree consistency after patching.

The Android SDK/Gradle toolchain is not installed in this execution environment, so a local Gradle compile could not be executed here. The GitHub runner remains the authoritative build environment.
