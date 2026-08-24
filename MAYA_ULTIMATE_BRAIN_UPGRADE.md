# MAYA Ultimate Brain Upgrade

This upgrade expands MAYA's settings architecture into a single persistent control plane.

## Included

- Intelligence: reasoning mode, planning depth, web intelligence, fact verification, self-correction, confidence threshold, proactive intelligence, memory learning/approval.
- Voice: pitch, speed, expressiveness, emotion, VAD, interruption sensitivity, barge-in, noise/echo cancellation, language detection, code switching, latency preference.
- Owner Voice / Parent Mode policy: wake phrase, enrollment state, threshold, warning policy, unauthorized-attempt threshold, lock policy.
- Personality: warmth, humor, playfulness, sarcasm, affection, emotional expressiveness, formality, talkativeness, proactivity, greetings and nickname behavior.
- Orb: reactivity, visualization, idle breathing, emotion orb, music reaction, glow, particles, size, animation speed, haptics and battery saver mode.
- Automation: safe auto-actions, verification, routines, schedules, accessibility/screen automation, retries and confirmation.
- Notifications: reading, filtering, summaries, read-aloud and privacy policy.
- Memory/privacy: long-term memory, auto-learn, approval, local-only, encrypted, cloud, private mode, biometric confirmation and data-sharing controls.
- Performance/developer: low latency, network/background processing, performance profile, transcript, latency, token and action diagnostics.
- Android default-assistant eligibility: MAYA's main activity now handles `ACTION_ASSIST`.

## Owner voice limitation

Android's standard `SpeechRecognizer` performs speech-to-text; it is not a secure speaker biometric. Therefore Parent Mode must not claim to authenticate a person from transcription alone.

The project now contains the policy/configuration and enforcement layer. A production owner-only implementation should connect a real on-device speaker-verification engine (speaker embeddings + anti-spoofing) to `VoiceSecurityManager.verifyVoice()` and persist only the minimum required biometric representation.

The existing accessibility lock action is retained for repeated unauthorized attempts when the user enables it.

## Important

A default assistant role is requested by Android's role system and still requires the user to explicitly grant the role in system UI. The app cannot silently become the default assistant.

No feature should bypass Android permission/role prompts.
