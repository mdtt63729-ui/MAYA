# MAYA — Ultimate JARVIS Upgrade

## Implemented in this build

### 1. Realtime conversation foundation
- Dedicated Gemini-only voice pipeline remains isolated from text chat.
- Non-Gemini chat pipeline supports OpenRouter, OpenCode, NVIDIA NIM and custom OpenAI-compatible providers.
- Chat streaming is enabled through provider SSE streams and updates the Room message progressively.
- Auto chat routing can select a non-Gemini provider by task class.
- Chat UI auto-scrolls on streamed content changes.

### 2. JARVIS runtime state
`MayaStateMachine` exposes:
- IDLE
- LISTENING
- UNDERSTANDING
- THINKING
- EXECUTING
- SPEAKING
- ERROR

This is the common state model for future Orb/HUD synchronization.

### 3. Skill system
`MayaSkillRegistry` provides bounded skills for:
- device/settings
- app launching
- web search
- time/date
- camera launch

The registry is intentionally permission-aware and can be extended without changing the core planner.

### 4. Offline brain
Basic commands such as greeting, time, battery and status can work without a cloud model.

### 5. Screen intelligence
Accessibility events now maintain a local screen snapshot containing:
- foreground package
- visible text
- content descriptions
- timestamp

Chat context includes this information only when screen awareness AND accessibility automation are enabled.

### 6. Predictive intelligence
A local predictive layer can surface battery and permission suggestions. It respects Private Mode and the proactive-intelligence setting.

### 7. Routine storage
A local routine repository is available for learned/reusable workflows.

### 8. Health center
The Command Center reports:
- Gemini voice readiness
- non-Gemini chat readiness
- memory state
- accessibility state
- microphone capability
- battery
- heap usage
- Android version

### 9. Audit trail
A bounded local audit log stores recent task classification, skill execution and chat lifecycle events.

### 10. Android default assistant architecture
MAYA now includes:
- `VoiceInteractionService`
- `VoiceInteractionSessionService`
- `VoiceInteractionSession`
- `voice_interaction_service.xml`

The existing RoleManager flow can request the Android Assistant role. System approval is still required.

### 11. Owner Voice safety
Parent Mode is fail-closed. Android SpeechRecognizer text is NOT treated as secure speaker identity. The speaker verification interface exposes an operational boundary for a real on-device speaker-embedding + anti-spoof model. Until such a model is installed and verified, Parent Mode cannot falsely authorize a user.

### 12. UI / keyboard fix
The chat composer owns IME and navigation-bar insets. The whole chat viewport is no longer padded by the keyboard, preventing the large vertical gap shown when the keyboard opens.

Thinking animation is Compose-native and uses a lightweight shimmer, breathing glyph and staggered dots.

### 13. Launch/permission hygiene
Overlay permission is no longer opened automatically on every app launch. It must be requested only by a user-facing floating-HUD action.

## Important platform boundaries

Some JARVIS capabilities cannot be safely or honestly implemented as fake logic:

- True owner voice biometrics require an actual speaker-embedding and anti-spoof model.
- Camera/image understanding requires a camera capture pipeline and a vision-capable model.
- Full screen capture requires Android MediaProjection user consent.
- Android system actions remain constrained by OS permissions, role APIs and Accessibility policy.
- Background microphone operation remains subject to Android foreground-service and while-in-use restrictions.

The project exposes the correct integration boundaries instead of claiming unsupported capabilities are already secure.
