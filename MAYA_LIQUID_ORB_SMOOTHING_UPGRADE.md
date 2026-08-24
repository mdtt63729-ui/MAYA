# MAYA Liquid Glass ORB — Motion + Tap Activation Upgrade

## Visual upgrade
- Replaced polygonal sphere edge sampling with a closed cubic Catmull-Rom-style interpolation path.
- Reduced per-frame path sample counts while increasing curve smoothness.
- Reworked breathing motion to use low-frequency layered sine waves, avoiding visible pulsing/jitter.
- Replaced per-frame tween chasing with a spring-based scale response.
- Added press compression with spring physics and haptic feedback.
- Kept the procedural liquid/glass material native to Compose; no reference videos are bundled or played.
- Frame delta is clamped to prevent large animation jumps after lifecycle stalls.

## Orb activation
- Tapping the in-app ORB still toggles the MAYA voice session.
- Tapping the floating ORB now opens the MAYA activity using an explicit `orb_click`/`assistant_entry` request.
- The activity can receive repeated orb taps through `onNewIntent()`.
- After the activity is opened, MAYA automatically requests microphone/notification permission when needed and starts the voice assistant when permission is granted.
- If MAYA is already active, the normal in-app ORB tap continues to stop the session instead of creating a duplicate session.

## Build
- Version: 1.2.3 (versionCode 6)
