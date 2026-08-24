# MAYA Upgrade Report

## Implemented in this revision

### Cognitive core
- bounded goal decomposition
- multi-step execution plan
- context signals (time, battery, screen state, airplane mode, Android version)
- knowledge-graph storage
- temporal context

### Agent safety
- central action risk classification
- risk-based confirmation
- optional biometric gate for critical actions
- dry-run mode
- failure recovery with bounded retries
- maximum agent step / parallel-tool limits

### Perception controls
- multimodal vision toggle
- screenshot context toggle
- screen awareness toggle
- camera awareness toggle
- predictive intelligence toggle
- emotion/temporal intelligence controls

### Voice security
- Parent Mode controls remain available
- offline wake-word policy control
- wake-word sensitivity
- anti-spoof policy
- owner enrollment requirement
- pluggable `SpeakerVerificationEngine`
- default speaker verifier now fails closed instead of pretending a match occurred

### Settings
- JARVIS Cognition / Perception section
- Autonomy / Safety section
- Owner Voice Hardening section
- existing Intelligence / Voice / Personality / Orb / Automation / Notifications / Memory / Performance controls retained

## Not faked
A true owner-only voice biometric requires an actual speaker-embedding + anti-spoof model. The project now has the correct injection boundary, but no model weights were fabricated. Until a real verifier is injected, Parent Mode rejects speaker verification rather than silently accepting everyone.
