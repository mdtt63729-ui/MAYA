# MAYA Reference Orb — Native 3D Liquid Glass Implementation

The supplied reference videos were used only as visual design references. The videos are **not** bundled or played by the app.

## Visual states

- **Idle / Ready / Listening:** deep blue-violet glass sphere, slow breathing, low-energy caustics.
- **Thinking / Connecting / Executing:** brighter sphere with a restrained rotating halo arc.
- **User speaking / MAYA speaking:** cyan/indigo/violet liquid-glass sphere with stronger deformation, bloom, moving caustics and audio-reactive energy.
- **Error:** same material language with a restrained red-violet tint.

## Rendering approach

- Native Jetpack Compose `Canvas` only.
- Procedural 180-point liquid silhouette.
- Multiple harmonic deformation waves for organic movement.
- Radial gradients for spherical volume and Fresnel-like rim lighting.
- Clipped liquid lobes for the internal fluid look.
- Moving caustic ribbons.
- Specular highlight and reflected arc.
- Procedural bloom layers instead of video/image assets.
- Audio energy from the existing `VoiceReactiveController` drives active-state deformation and scale.
- A single frame clock keeps all animation layers phase-locked.

## Reference matching decisions

The active reference has a cool cyan/white upper-left specular region, a saturated blue body and violet lower-right energy. The idle reference is darker and more saturated blue/purple. The implementation intentionally keeps this palette across all states instead of switching to unrelated green/red state colors.

## Performance

The orb is drawn with a bounded number of Canvas paths and gradients. It does not allocate bitmaps, decode video frames, or perform per-frame image processing. The animation loop is frame-clock based and energy is already throttled by `VoiceReactiveController`.
