# MAYA Settings + Default Assistant Deep Fix — v1.2.8

Base project: `MAYA-CI-Fixed-v4-Settings-Mic-Deep-Fixed.zip`

## Fixed

### Default Android Assistant
- Added a dedicated `DefaultAssistantController`.
- Android 10+ uses `RoleManager.ROLE_ASSISTANT` and `createRequestRoleIntent()`.
- The UI uses `rememberLauncherForActivityResult` instead of the deprecated direct result API.
- The screen continuously refreshes the role state on resume.
- When MAYA is already the default assistant, the button changes to `Manage Default Assistant`.
- If the role API is unavailable, the app opens the system default-app / voice-input settings instead of silently doing nothing.
- The VoiceInteractionService manifest entry remains the system entry point.
- VoiceInteractionSession now launches the assistant UI with `startAssistantActivity()`.

### Settings screens
- Replaced the previous minimal/stub settings pages for Wake Word, Background Assistant, Appearance, and Privacy & Security with functional settings screens.
- Wake Word requests microphone permission before enabling and exposes wake phrase + sensitivity.
- Background Assistant requests microphone permission before enabling and exposes background processing, background service, battery settings and overlay settings.
- Appearance now controls theme, blur, motion profile, ORB reactivity, visualization, glow, particles, size, animation speed, haptics and animation power saving.
- Privacy & Security exposes history, memory, private mode, encryption/local-only policy, confirmation and permission status.

### Mic lifecycle hardening
- Background microphone enable is fail-closed if RECORD_AUDIO is not granted.
- Wake-word enable is fail-closed if RECORD_AUDIO is not granted.
- Disabling background processing also stops the assistant foreground service.
- Enabling background assistant automatically enables background processing instead of silently refusing the request.
- Existing Gemini Live mic ownership remains exclusive of Android SpeechRecognizer.

### Validation
- All XML resources parse successfully.
- Invalid `supportsLaunchVoiceActivity` attribute is absent.
- Invalid `android.voice_interaction_service` metadata key is absent.
- Changed Kotlin files were parser-checked with the Kotlin compiler; no syntax/declaration parsing diagnostics were reported. Full Android compilation still requires the GitHub Actions Android SDK/Gradle environment.

## Version
- versionName: `1.2.8`
- versionCode: `11`
