# MAYA v1.3.5 — Max Reference Feature Integration

## Reference reviewed
`426891.mp4` was reviewed frame-by-frame. The relevant visual behavior is native full-screen edge lighting around the device: cyan/blue/purple/magenta gradient, soft multi-pass bloom, rounded corners, travelling color motion, and brightness that responds to assistant activity. The reference was used only for behavior/visual design; no video/image asset is bundled or played.

## Integrated
- Full-screen edge-to-edge native Canvas lighting.
- Wake/activation -> strong edge bloom.
- Listening -> steady luminous edge with subtle movement.
- User speaking -> audio-reactive intensity.
- Thinking -> restrained lower-intensity edge motion.
- MAYA speaking -> stronger audio-reactive edge.
- Idle -> off by default; optional subtle idle glow setting.
- Cyan -> blue -> violet -> magenta sweep gradient.
- Multi-pass translucent bloom for soft glow without expensive full-screen blur.
- 120 FPS-friendly infinite animation with configurable speed.
- Settings controls persisted through the unified SettingsRepository.

## Settings added
- Edge lighting
- Voice-reactive edge
- Edge intensity
- Edge animation speed
- Idle edge glow

## Compatibility
The feature uses only existing Compose Canvas/graphics APIs and the existing edge-to-edge activity configuration. No new dependency or media asset is required.
