# MAYA Parent Mode Voice Enrollment Fix — v1.2.9

Implemented on top of the supplied v1.2.8 project.

## Added
- Dedicated Owner Voice enrollment screen.
- Animated microphone/orb recording UI.
- Three guided phrases, similar to assistant voice-training UX.
- 2.2 second PCM recording per phrase.
- Microphone permission flow.
- Local voiceprint template stored in app-private SharedPreferences.
- Re-enrollment support.
- Parent Mode setup button opens enrollment instead of silently enabling an unenrolled mode.

## Runtime
- Added LocalVoiceprintEngine implementing the existing SpeakerVerificationEngine boundary.
- Parent Mode now uses the enrolled local voiceprint to gate the first live audio segment.
- Audio is buffered locally during verification and is not sent to Gemini until authorization succeeds.
- Unauthorized verification stops the session and does not forward the buffered segment to Gemini.

## Security note
This is a lightweight dependency-free acoustic matcher, not a neural speaker-embedding/anti-spoof system. It is useful for local functionality but should not be described as equivalent to hardware-backed biometrics or a dedicated anti-spoof model. The anti-spoof flag remains a policy boundary and currently cannot detect replay attacks reliably.
