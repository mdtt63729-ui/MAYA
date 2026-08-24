# MAYA — JARVIS Upgrade v2

This upgrade adds a permission-aware cognitive/control layer on top of the existing MAYA project.

## Added
- Goal decomposition and bounded agent plans
- Risk-based action gating
- Dry-run/simulation mode
- Failure recovery loop
- Multimodal/screen-awareness settings
- Predictive intelligence and routine-learning controls
- Local knowledge-graph storage
- Adaptive model-routing and streaming/realtime controls
- Audit/self-diagnostics settings
- Owner-voice anti-spoof policy controls
- Fail-closed speaker-verification interface
- Expanded JARVIS Control Center settings
- Default digital-assistant role eligibility remains enabled

## Important security behavior
Parent Mode no longer treats a fake/simulated match as a real biometric match. Android SpeechRecognizer alone is not a speaker biometric system. `SpeakerVerificationEngine` is the injection point for a genuine on-device speaker-embedding + anti-spoof model. The default implementation fails closed.

## Build
The source archive does not contain a Gradle wrapper in the provided project, so run it from Android Studio or add the project's normal `gradlew`/wrapper files before command-line CI builds.
