import React, { useEffect, useRef, useCallback } from 'react';
import { motion } from 'motion/react';
import { LiveSessionState } from '../../services/liveSession';
import { audioStreamer } from '../../services/audioStreamer';
import { Mic, MicOff, Volume2, Sparkles, Power, Radio, Brain, Search } from 'lucide-react';

interface JarvisParticleOrbProps {
  state: LiveSessionState;
  isMuted: boolean;
  onTogglePower: () => void;
}

/* ============================================================
 * PREMIUM ORB VISUAL ENGINE
 * Every session state has a fully distinct, buttery-smooth look:
 *   idle       → dimmed slow-breathing slate-blue dormant core
 *   connecting → amber→cyan boot sequence (rings materialize in)
 *   listening  → electric-cyan, mic-reactive surface ripples + radar sweep
 *   thinking   → violet liquid-metal vortex with neural energy bands
 *   speaking   → warm golden sun-core, output-audio harmonic waves + photon pulses
 *   searching  → emerald radar with a vertical scan-band sweeping the sphere
 * All state transitions are continuously interpolated (colors, speeds,
 * amplitudes) so the orb morphs fluidly instead of snapping.
 * ============================================================ */

type RGB = [number, number, number];

interface OrbParams {
  spin: number;          // baseline equatorial spin (rad/s)
  tilt: number;          // base vertical tilt (rad)
  colorA: RGB;            // primary particle / ring color
  colorB: RGB;            // secondary accent color
  coreAlpha: number;      // luminous core brightness (0..1)
  coreRadius: number;    // core radius in px
  rippleGain: number;    // audio → surface ripple multiplier
  breatheAmp: number;    // organic breathing scale
  breatheRate: number;  // breathing speed (rad/s)
  ringAlpha: number;     // gyroscope ring visibility
  filament: number;       // neural web intensity (0..1)
  vortex: number;         // liquid twist strength (thinking)
  band: number;           // latitude energy-band strength (thinking)
  scan: number;           // vertical scan-band strength (searching)
  particleAlpha: number; // base particle brightness
  haloAlpha: number;     // ambient halo behind the sphere
  sizeBoost: number;     // particle size multiplier
}

const PROFILES: Record<string, OrbParams> = {
  // Dormant — calm, slow, sleepy
  idle: {
    spin: 0.06, tilt: 0.30,
    colorA: [110, 150, 190], colorB: [56, 89, 122],
    coreAlpha: 0.30, coreRadius: 20,
    rippleGain: 0, breatheAmp: 0.020, breatheRate: 0.9,
    ringAlpha: 0.10, filament: 0, vortex: 0, band: 0, scan: 0,
    particleAlpha: 0.55, haloAlpha: 0.10, sizeBoost: 0.90,
  },
  // Boot sequence — warm amber igniting into cyan
  connecting: {
    spin: 0.45, tilt: 0.34,
    colorA: [56, 189, 248], colorB: [251, 191, 36],
    coreAlpha: 0.50, coreRadius: 24,
    rippleGain: 0, breatheAmp: 0.012, breatheRate: 3.2,
    ringAlpha: 0.55, filament: 0, vortex: 0, band: 0.3, scan: 0,
    particleAlpha: 0.72, haloAlpha: 0.20, sizeBoost: 1.0,
  },
  // Listening — vivid electric cyan, alive to the user's voice
  listening: {
    spin: 0.12, tilt: 0.32,
    colorA: [0, 212, 255], colorB: [56, 189, 248],
    coreAlpha: 0.42, coreRadius: 22,
    rippleGain: 1.0, breatheAmp: 0.008, breatheRate: 1.6,
    ringAlpha: 0.35, filament: 0.10, vortex: 0, band: 0, scan: 0,
    particleAlpha: 0.80, haloAlpha: 0.22, sizeBoost: 1.0,
  },
  // Thinking — violet liquid-metal vortex with neural bands
  thinking: {
    spin: 0.42, tilt: 0.36,
    colorA: [167, 139, 250], colorB: [236, 72, 153],
    coreAlpha: 0.58, coreRadius: 24,
    rippleGain: 0, breatheAmp: 0.018, breatheRate: 2.4,
    ringAlpha: 0.50, filament: 0.55, vortex: 1.0, band: 1.0, scan: 0,
    particleAlpha: 0.85, haloAlpha: 0.26, sizeBoost: 1.0,
  },
  // Speaking — golden sun, harmonic waves with her voice
  speaking: {
    spin: 0.20, tilt: 0.32,
    colorA: [255, 191, 80], colorB: [255, 244, 214],
    coreAlpha: 0.85, coreRadius: 27,
    rippleGain: 1.45, breatheAmp: 0.010, breatheRate: 2.0,
    ringAlpha: 0.60, filament: 1.0, vortex: 0, band: 0, scan: 0,
    particleAlpha: 0.92, haloAlpha: 0.32, sizeBoost: 1.12,
  },
  // Searching — emerald radar with a scanning latitude beam
  searching: {
    spin: 0.34, tilt: 0.33,
    colorA: [52, 211, 153], colorB: [45, 212, 191],
    coreAlpha: 0.50, coreRadius: 23,
    rippleGain: 0, breatheAmp: 0.010, breatheRate: 2.0,
    ringAlpha: 0.50, filament: 0.15, vortex: 0, band: 0, scan: 1.0,
    particleAlpha: 0.82, haloAlpha: 0.24, sizeBoost: 1.0,
  },
};

const lerp = (a: number, b: number, t: number): number => a + (b - a) * t;
const lerpRGB = (a: RGB, b: RGB, t: number): RGB => [
  Math.round(lerp(a[0], b[0], t)),
  Math.round(lerp(a[1], b[1], t)),
  Math.round(lerp(a[2], b[2], t)),
];
const easeOutCubic = (t: number): number => 1 - Math.pow(1 - t, 3);

interface Particle {
  x: number; y: number; z: number;
  theta: number; phi: number;
  baseSize: number; brightness: number; phase: number;
}

interface Shockwave { born: number; color: RGB; }

export const JarvisParticleOrb: React.FC<JarvisParticleOrbProps> = ({
  state,
  isMuted,
  onTogglePower,
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);

  // Keep state & mute flags in refs to eliminate React re-renders in the 60fps canvas loop
  const stateRef = useRef<LiveSessionState>(state);
  const isMutedRef = useRef<boolean>(isMuted);

  useEffect(() => { stateRef.current = state; }, [state]);
  useEffect(() => { isMutedRef.current = isMuted; }, [isMuted]);

  // User interactive 3D drag & rotation control
  const rotationRef = useRef<{
    x: number; y: number;
    userVX: number;
    isDragging: boolean;
    lastX: number; lastY: number; lastT: number;
  }>({
    x: 0.15,
    y: 0,
    userVX: 0,
    isDragging: false,
    lastX: 0, lastY: 0, lastT: 0,
  });

  // Canvas 3D rendering engine (mounts ONCE, rock-solid 60 FPS, no stutters or resets)
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d', { alpha: true });
    if (!ctx) return;

    let animationFrameId: number;
    let width = 320;
    let height = 320;
    // Compact refined size
    const baseRadius = 118;

    const updateDimensions = () => {
      if (!canvas.parentElement) return;
      const rect = canvas.parentElement.getBoundingClientRect();
      const size = Math.min(340, Math.max(280, rect.width || 310));
      // Cap DPR at 2 — 3x screens gain nothing visually but triple the fill cost
      const dpr = Math.min(2, window.devicePixelRatio || 1);
      width = size;
      height = size;
      canvas.width = size * dpr;
      canvas.height = size * dpr;
      canvas.style.width = `${size}px`;
      canvas.style.height = `${size}px`;
      ctx.scale(dpr, dpr);
    };

    updateDimensions();
    window.addEventListener('resize', updateDimensions);

    // Generate particles on a mathematical Fibonacci Sphere.
    // PERFORMANCE: phones get fewer particles + skip halos/filaments so the
    // orb stays buttery-smooth at 60fps even on budget Android devices.
    const isTouchDevice = (() => {
      try { return window.matchMedia('(pointer: coarse)').matches; } catch { return false; }
    })();
    const numParticles = isTouchDevice ? 380 : 800;
    const drawParticleHalos = !isTouchDevice;
    const enableFilaments = !isTouchDevice;
    const numTicks = isTouchDevice ? 72 : 120;
    const particles: Particle[] = [];
    const goldenRatio = (1 + Math.sqrt(5)) / 2;
    const goldenAngle = 2 * Math.PI * (1 - 1 / goldenRatio);

    for (let i = 0; i < numParticles; i++) {
      const y = 1 - (i / (numParticles - 1)) * 2;
      const radiusAtY = Math.sqrt(Math.max(0, 1 - y * y));
      const theta = goldenAngle * i;
      particles.push({
        x: Math.cos(theta) * radiusAtY,
        y,
        z: Math.sin(theta) * radiusAtY,
        theta,
        phi: Math.asin(Math.max(-1, Math.min(1, y))),
        baseSize: 1.1 + Math.random() * 1.2,
        brightness: 0.65 + Math.random() * 0.35,
        phase: Math.random() * Math.PI * 2,
      });
    }

    // ------- Interpolated visual state machine (the secret to buttery morphs) -------
    let cur: OrbParams = { ...PROFILES.idle, colorA: [...PROFILES.idle.colorA] as RGB, colorB: [...PROFILES.idle.colorB] as RGB };
    let lastState: LiveSessionState = stateRef.current;
    let bootT = lastState === 'connecting' ? 0 : 1;
    const shockwaves: Shockwave[] = [];

    const startTime = performance.now();
    let lastFrame = startTime;
    let smoothedAmp = 0;

    const render = (now: number) => {
      animationFrameId = requestAnimationFrame(render);
      const dt = Math.min(0.05, (now - lastFrame) / 1000);
      lastFrame = now;
      const elapsed = (now - startTime) / 1000;

      const currentState = stateRef.current;
      const currentMuted = isMutedRef.current;

      // ---- State transition detection: emit a shockwave + boot handling ----
      if (currentState !== lastState) {
        const target = PROFILES[currentState] || PROFILES.idle;
        shockwaves.push({ born: elapsed, color: target.colorA });
        if (shockwaves.length > 4) shockwaves.shift();
        if (currentState === 'connecting') bootT = 0;
        lastState = currentState;
      }

      // ---- Continuously interpolate every parameter toward the state profile ----
      const target = PROFILES[currentState] || PROFILES.idle;
      const k = 1 - Math.exp(-dt * 5.2); // ~0.4s smooth morph
      cur.spin = lerp(cur.spin, target.spin, k);
      cur.tilt = lerp(cur.tilt, target.tilt, k);
      cur.colorA = lerpRGB(cur.colorA, target.colorA, k);
      cur.colorB = lerpRGB(cur.colorB, target.colorB, k);
      cur.coreAlpha = lerp(cur.coreAlpha, target.coreAlpha, k);
      cur.coreRadius = lerp(cur.coreRadius, target.coreRadius, k);
      cur.rippleGain = lerp(cur.rippleGain, target.rippleGain, k);
      cur.breatheAmp = lerp(cur.breatheAmp, target.breatheAmp, k);
      cur.breatheRate = lerp(cur.breatheRate, target.breatheRate, k);
      cur.ringAlpha = lerp(cur.ringAlpha, target.ringAlpha, k);
      cur.filament = lerp(cur.filament, target.filament, k);
      cur.vortex = lerp(cur.vortex, target.vortex, k);
      cur.band = lerp(cur.band, target.band, k);
      cur.scan = lerp(cur.scan, target.scan, k);
      cur.particleAlpha = lerp(cur.particleAlpha, target.particleAlpha, k);
      cur.haloAlpha = lerp(cur.haloAlpha, target.haloAlpha, k);
      cur.sizeBoost = lerp(cur.sizeBoost, target.sizeBoost, k);

      if (currentState === 'connecting') {
        bootT = Math.min(1, bootT + dt / 1.3);
      } else {
        bootT = Math.min(1, bootT + dt / 0.4);
      }
      const bootEase = easeOutCubic(bootT);

      const colA = `rgb(${cur.colorA[0]},${cur.colorA[1]},${cur.colorA[2]})`;
      const colB = `rgb(${cur.colorB[0]},${cur.colorB[1]},${cur.colorB[2]})`;

      // ---- Real-time volume sampling (fast attack, gentle decay) ----
      let rawAmp = 0;
      if (!currentMuted) {
        if (currentState === 'speaking') {
          rawAmp = audioStreamer.getOutputVolume();
        } else if (currentState === 'listening') {
          rawAmp = audioStreamer.getInputVolume();
        }
      }
      const targetAmp = Math.min(1.0, rawAmp * 2.2);
      if (targetAmp > smoothedAmp) {
        smoothedAmp += (targetAmp - smoothedAmp) * 0.28;
      } else {
        smoothedAmp += (targetAmp - smoothedAmp) * 0.12;
      }
      const amp = smoothedAmp;

      // ---- Rotation: steady spin + drag inertia ----
      if (!rotationRef.current.isDragging) {
        rotationRef.current.userVX *= Math.exp(-dt * 2.4);
        rotationRef.current.y += (cur.spin + rotationRef.current.userVX) * dt;
      }

      ctx.clearRect(0, 0, width, height);

      const centerX = width / 2;
      const centerY = height / 2;

      // Breathing scale (organic life)
      const breathe = 1 + Math.sin(elapsed * cur.breatheRate) * cur.breatheAmp;
      const sphereR = baseRadius * breathe * (0.55 + 0.45 * bootEase);

      const rotX = rotationRef.current.x + cur.tilt;
      const rotY = rotationRef.current.y;
      const cosX = Math.cos(rotX);
      const sinX = Math.sin(rotX);
      const cosY = Math.cos(rotY);
      const sinY = Math.sin(rotY);

      // ============ 0. AMBIENT HALO ============
      // (Rendered by the CSS glow div behind the canvas — a full-screen
      // gradient fill here was one of the biggest per-frame costs on mobile)

      // ============ 1. STATE-TRANSITION SHOCKWAVES ============
      for (let i = shockwaves.length - 1; i >= 0; i--) {
        const sw = shockwaves[i];
        const t = (elapsed - sw.born) / 0.9;
        if (t >= 1 || t < 0) { shockwaves.splice(i, 1); continue; }
        const fade = 1 - t;
        const swR = sphereR * (0.65 + t * 1.45);
        // Layered glow strokes — no shadowBlur (mobile GPU killer)
        ctx.strokeStyle = `rgba(${sw.color[0]},${sw.color[1]},${sw.color[2]},${fade * 0.16})`;
        ctx.lineWidth = 4 + fade * 12;
        ctx.beginPath();
        ctx.arc(centerX, centerY, swR, 0, Math.PI * 2);
        ctx.stroke();
        ctx.strokeStyle = `rgba(${sw.color[0]},${sw.color[1]},${sw.color[2]},${fade * 0.55})`;
        ctx.lineWidth = 1.5 + fade * 3;
        ctx.beginPath();
        ctx.arc(centerX, centerY, swR, 0, Math.PI * 2);
        ctx.stroke();
      }

      // ============ 2. LUMINOUS CORE ============
      {
        // Thinking: rhythmic neural pulse. Speaking: voice-driven swell.
        const pulse =
          cur.vortex > 0.1 ? 0.78 + 0.22 * Math.sin(elapsed * 8.0) : 1.0;
        const coreR = cur.coreRadius * (1 + amp * 0.45) * pulse * breathe;
        const coreAlpha = Math.min(1, cur.coreAlpha * pulse + amp * 0.45);
        const grad = ctx.createRadialGradient(centerX, centerY, 1, centerX, centerY, Math.max(4, coreR));
        grad.addColorStop(0, `rgba(255,255,255,${Math.min(1, coreAlpha * 0.75)})`);
        grad.addColorStop(0.32, `rgba(${cur.colorA[0]},${cur.colorA[1]},${cur.colorA[2]},${coreAlpha * 0.55})`);
        grad.addColorStop(0.72, `rgba(${cur.colorB[0]},${cur.colorB[1]},${cur.colorB[2]},${coreAlpha * 0.22})`);
        grad.addColorStop(1, 'rgba(0,0,0,0)');
        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.arc(centerX, centerY, Math.max(4, coreR), 0, Math.PI * 2);
        ctx.fill();
      }

      // ============ 3. DUAL COUNTER-ROTATING GYROSCOPE RINGS ============
      const gyroRadius = baseRadius + 14;
      const drawGyro = (tiltAngle: number, spinDir: number, accent: string) => {
        ctx.save();
        ctx.translate(centerX, centerY);
        ctx.rotate(tiltAngle + (spinDir > 0 ? elapsed * 0.35 : -elapsed * 0.28));
        ctx.scale(1, 0.42);
        ctx.strokeStyle = `rgba(${cur.colorA[0]},${cur.colorA[1]},${cur.colorA[2]},${cur.ringAlpha * (0.55 + amp * 0.5) * bootEase})`;
        ctx.lineWidth = 1.1 + amp * 0.6;
        ctx.beginPath();
        ctx.arc(0, 0, gyroRadius, 0, Math.PI * 2);
        ctx.stroke();

        // Traveling photon pulse (layered glow — no shadowBlur for mobile perf)
        const photonAlpha = cur.ringAlpha * (0.6 + amp) * bootEase;
        if (photonAlpha > 0.06) {
          const pa = elapsed * spinDir * (2.4 + amp * 2.5);
          const px = Math.cos(pa) * gyroRadius;
          const py = Math.sin(pa) * gyroRadius;
          ctx.fillStyle = `rgba(${cur.colorB[0]},${cur.colorB[1]},${cur.colorB[2]},${photonAlpha * 0.25})`;
          ctx.beginPath();
          ctx.arc(px, py, 7.5, 0, Math.PI * 2);
          ctx.fill();
          ctx.fillStyle = accent;
          ctx.globalAlpha = Math.min(1, photonAlpha);
          ctx.beginPath();
          ctx.arc(px, py, 3.0, 0, Math.PI * 2);
          ctx.fill();
          ctx.globalAlpha = 1;
        }
        ctx.restore();
      };
      drawGyro(0.48, 1, colA);
      drawGyro(-0.48, -1, colB);

      // ============ 4. 3D PARTICLE PROJECTION ============
      interface ProjectedPoint {
        sx: number; sy: number; sz: number;
        size: number; alpha: number; isPeak: boolean;
      }
      const projected: ProjectedPoint[] = [];

      const scanLat = Math.sin(elapsed * 1.35); // searching: vertical scan position

      for (let i = 0; i < numParticles; i++) {
        const p = particles[i];
        let r = sphereR;
        let isPeak = false;

        // (a) Audio harmonic surface ripples (listening / speaking)
        if (cur.rippleGain > 0.05) {
          const wave = Math.sin(p.theta * 4 + elapsed * 6) * Math.cos(p.phi * 3 + elapsed * 4);
          r += wave * (amp * 20) * cur.rippleGain;
          r += Math.sin(elapsed * 4 + p.phase) * (amp * 5) * cur.rippleGain;
          if (wave > 0.6 && amp > 0.15) isPeak = true;
        }

        // (b) Vortex twist (thinking) — liquid-metal swirl by latitude
        const az = p.theta + cur.vortex * Math.sin(elapsed * 1.25 + p.y * 2.1) * 1.05;
        const rAtY = Math.sqrt(Math.max(0, 1 - p.y * p.y));
        const curX = Math.cos(az) * rAtY * r;
        const curZ = Math.sin(az) * rAtY * r;
        const curY = p.y * r;

        // 3D Rotation Matrix
        const x1 = curX * cosY - curZ * sinY;
        const z1 = curX * sinY + curZ * cosY;
        const y2 = curY * cosX - z1 * sinX;
        const z2 = curY * sinX + z1 * cosX;

        // Perspective projection
        const fov = 360;
        const perspective = fov / (fov + z2);
        const screenX = centerX + x1 * perspective;
        const screenY = centerY + y2 * perspective;

        // Depth cue
        const depthNorm = (z2 + baseRadius) / (2 * baseRadius);

        // (c) Thinking: neural latitude energy bands sweeping pole-to-pole
        let bandBoost = 0;
        if (cur.band > 0.05) {
          bandBoost = cur.band * (0.5 + 0.5 * Math.sin(p.phi * 3 - elapsed * 2.6));
        }

        // (d) Searching: bright scan beam sweeping vertically
        let scanBoost = 0;
        if (cur.scan > 0.05) {
          const d = p.y - scanLat;
          scanBoost = cur.scan * Math.exp(-(d * d) / 0.02);
        }

        // (e) Idle: gentle shimmer (smoothly fades out as the orb wakes up)
        const shimmerMix = Math.max(0, Math.min(1, (0.65 - cur.particleAlpha) / 0.10));
        const shimmerWave = 0.85 + 0.15 * Math.sin(elapsed * 0.7 + p.phase * 3);
        const idleShimmer = 1 - shimmerMix * (1 - shimmerWave);

        const glowBoost = Math.max(bandBoost, scanBoost);
        const alpha = Math.min(
          1.0,
          Math.max(
            0.14,
            (0.20 + depthNorm * 0.72 * p.brightness + glowBoost * 0.65 + (isPeak ? 0.25 : 0))
            * cur.particleAlpha * idleShimmer
          )
        );
        const pointSize = Math.max(
          0.7,
          p.baseSize * perspective * (0.8 + depthNorm * 0.5 + glowBoost * 0.7 + (isPeak ? 0.6 : 0)) * cur.sizeBoost
        );

        projected.push({ sx: screenX, sy: screenY, sz: z2, size: pointSize, alpha, isPeak });
      }

      // Sort by Z for true 3D depth occlusion
      projected.sort((a, b) => a.sz - b.sz);

      // ============ 5. NEURAL HOLOGRAPHIC FILAMENTS (speaking / thinking — desktop only) ============
      if (enableFilaments && cur.filament > 0.25 && (amp > 0.08 || cur.vortex > 0.2)) {
        ctx.lineWidth = 0.8;
        const maxDistSq = 576;
        const foregroundStart = Math.max(0, projected.length - 55);
        for (let i = foregroundStart; i < projected.length; i++) {
          const p1 = projected[i];
          if (p1.alpha < 0.55) continue;
          for (let j = i + 1; j < projected.length; j++) {
            const p2 = projected[j];
            const dx = p1.sx - p2.sx;
            const dy = p1.sy - p2.sy;
            const distSq = dx * dx + dy * dy;
            if (distSq < maxDistSq) {
              const lineAlpha = (1 - distSq / maxDistSq) * 0.32 * cur.filament * (amp + (cur.vortex > 0.2 ? 0.55 : 0.25));
              ctx.strokeStyle = `rgba(${cur.colorA[0]},${cur.colorA[1]},${cur.colorA[2]},${lineAlpha})`;
              ctx.beginPath();
              ctx.moveTo(p1.sx, p1.sy);
              ctx.lineTo(p2.sx, p2.sy);
              ctx.stroke();
            }
          }
        }
      }

      // ============ 6. DRAW ALL 3D PARTICLES ============
      for (let i = 0; i < projected.length; i++) {
        const pt = projected[i];
        ctx.fillStyle = pt.isPeak
          ? `rgba(255,255,255,${Math.min(1.0, pt.alpha + 0.2)})`
          : `rgba(240,249,255,${pt.alpha})`;
        ctx.beginPath();
        ctx.arc(pt.sx, pt.sy, pt.size, 0, Math.PI * 2);
        ctx.fill();

        // Shimmering bloom halo for foreground points (desktop only — mobile perf)
        if (pt.size > 1.8 && drawParticleHalos) {
          ctx.fillStyle = pt.isPeak
            ? `rgba(${cur.colorA[0]},${cur.colorA[1]},${cur.colorA[2]},${pt.alpha * 0.65})`
            : `rgba(${cur.colorA[0]},${cur.colorA[1]},${cur.colorA[2]},${pt.alpha * 0.38})`;
          ctx.beginPath();
          ctx.arc(pt.sx, pt.sy, pt.size * (pt.isPeak ? 2.8 : 2.2), 0, Math.PI * 2);
          ctx.fill();
        }
      }

      // ============ 7. OUTER PRECISION NOTCHED PERIMETER RING ============
      const outerRingRadius = baseRadius + 2;

      ctx.save();
      // Layered glow ring — no shadowBlur (mobile GPU killer)
      ctx.strokeStyle = `rgba(${cur.colorA[0]},${cur.colorA[1]},${cur.colorA[2]},${0.10 + amp * 0.14})`;
      ctx.lineWidth = 6.0;
      ctx.beginPath();
      ctx.arc(centerX, centerY, outerRingRadius, 0, Math.PI * 2);
      ctx.stroke();

      ctx.strokeStyle = colA;
      ctx.lineWidth = 2.0;

      // Boot arc sweep while connecting
      if (bootEase < 1) {
        ctx.globalAlpha = bootEase;
        ctx.beginPath();
        ctx.arc(centerX, centerY, outerRingRadius, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * bootEase);
        ctx.stroke();
        ctx.globalAlpha = 1;
      } else {
        ctx.beginPath();
        ctx.arc(centerX, centerY, outerRingRadius, 0, Math.PI * 2);
        ctx.stroke();
      }

      // Precision etched radial ticks
      ctx.lineWidth = 1.4;
      for (let i = 0; i < numTicks; i++) {
        const angle = (i / numTicks) * Math.PI * 2;
        const isMajor = i % 4 === 0;
        let tickLength = isMajor ? 5.0 : 2.6;
        if (amp > 0.03 && isMajor && cur.rippleGain > 0.05) {
          tickLength += amp * 4.5;
        }
        const ca = Math.cos(angle);
        const sa = Math.sin(angle);
        const xOuter = centerX + ca * (outerRingRadius + tickLength);
        const yOuter = centerY + sa * (outerRingRadius + tickLength);
        const xInner = centerX + ca * outerRingRadius;
        const yInner = centerY + sa * outerRingRadius;
        ctx.beginPath();
        ctx.moveTo(xInner, yInner);
        ctx.lineTo(xOuter, yOuter);
        ctx.stroke();
      }

      // ---- LISTENING: radar sweep arc ----
      if (currentState === 'listening') {
        const sweep = (elapsed * 2.6) % (Math.PI * 2);
        ctx.strokeStyle = `rgba(${cur.colorA[0]},${cur.colorA[1]},${cur.colorA[2]},${0.30 + amp * 0.45})`;
        ctx.lineWidth = 1.6;
        ctx.beginPath();
        ctx.arc(centerX, centerY, outerRingRadius - 6, sweep, sweep + Math.PI * 0.5);
        ctx.stroke();
        ctx.strokeStyle = `rgba(${cur.colorA[0]},${cur.colorA[1]},${cur.colorA[2]},${0.12})`;
        ctx.beginPath();
        ctx.arc(centerX, centerY, outerRingRadius - 6, sweep - Math.PI * 0.4, sweep);
        ctx.stroke();
      }

      // ---- SEARCHING: comet progress arc ----
      if (currentState === 'searching' || cur.scan > 0.2) {
        const comet = (elapsed * 2.2) % (Math.PI * 2);
        ctx.strokeStyle = `rgba(${cur.colorB[0]},${cur.colorB[1]},${cur.colorB[2]},${0.55})`;
        ctx.lineWidth = 3.0;
        ctx.lineCap = 'round';
        ctx.beginPath();
        ctx.arc(centerX, centerY, outerRingRadius - 5, comet, comet + Math.PI * 0.75);
        ctx.stroke();
        // Comet head (layered glow — no shadowBlur)
        const hx = centerX + Math.cos(comet + Math.PI * 0.75) * (outerRingRadius - 5);
        const hy = centerY + Math.sin(comet + Math.PI * 0.75) * (outerRingRadius - 5);
        ctx.fillStyle = `rgba(${cur.colorB[0]},${cur.colorB[1]},${cur.colorB[2]},${0.25})`;
        ctx.beginPath();
        ctx.arc(hx, hy, 9.5, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillStyle = colB;
        ctx.beginPath();
        ctx.arc(hx, hy, 4.0, 0, Math.PI * 2);
        ctx.fill();
        ctx.lineCap = 'butt';
      }

      // ---- THINKING: rotating triangular markers ----
      if (currentState === 'thinking' || cur.vortex > 0.2) {
        for (let m = 0; m < 3; m++) {
          const ma = elapsed * 1.4 + (m * Math.PI * 2) / 3;
          const mx = centerX + Math.cos(ma) * (outerRingRadius + 10);
          const my = centerY + Math.sin(ma) * (outerRingRadius + 10);
          ctx.save();
          ctx.translate(mx, my);
          ctx.rotate(elapsed * 3 + m);
          ctx.strokeStyle = `rgba(${cur.colorB[0]},${cur.colorB[1]},${cur.colorB[2]},${0.6})`;
          ctx.lineWidth = 1.2;
          ctx.beginPath();
          ctx.moveTo(0, -3.2);
          ctx.lineTo(2.8, 2.2);
          ctx.lineTo(-2.8, 2.2);
          ctx.closePath();
          ctx.stroke();
          ctx.restore();
        }
      }

      ctx.restore();
    };

    animationFrameId = requestAnimationFrame(render);

    return () => {
      cancelAnimationFrame(animationFrameId);
      window.removeEventListener('resize', updateDimensions);
    };
  }, []); // Run once on mount — zero unmount/mount churn

  // Touch & Pointer Drag Handlers for 3D interactive rotation (with fling inertia)
  const handlePointerDown = useCallback((e: React.PointerEvent) => {
    rotationRef.current.isDragging = true;
    rotationRef.current.lastX = e.clientX;
    rotationRef.current.lastY = e.clientY;
    rotationRef.current.lastT = performance.now();
    rotationRef.current.userVX = 0;
  }, []);

  const handlePointerMove = useCallback((e: React.PointerEvent) => {
    if (!rotationRef.current.isDragging) return;
    const dx = e.clientX - rotationRef.current.lastX;
    const dy = e.clientY - rotationRef.current.lastY;
    const now = performance.now();
    const dtMs = Math.max(8, now - rotationRef.current.lastT);

    rotationRef.current.y += dx * 0.007;
    rotationRef.current.x = Math.max(-1.1, Math.min(1.1, rotationRef.current.x + dy * 0.007));
    rotationRef.current.userVX = (dx * 0.007) / (dtMs / 1000);

    rotationRef.current.lastX = e.clientX;
    rotationRef.current.lastY = e.clientY;
    rotationRef.current.lastT = now;
  }, []);

  const handlePointerUp = useCallback(() => {
    rotationRef.current.isDragging = false;
    // Fling inertia: clamp so a hard swipe doesn't teleport the sphere
    rotationRef.current.userVX = Math.max(-5, Math.min(5, rotationRef.current.userVX));
  }, []);

  // ---- JSX accent palette per state ----
  const UI_ACCENTS: Record<string, string> = {
    disconnected: '#64748b',
    connecting: '#fbbf24',
    listening: '#00d4ff',
    thinking: '#a78bfa',
    speaking: '#ffc14d',
    searching: '#34d399',
  };
  const accent = isMuted ? '#f43f5e' : UI_ACCENTS[state] || UI_ACCENTS.listening;

  const STATE_LABELS: Record<string, string> = {
    disconnected: 'STANDBY',
    connecting: 'CONNECTING',
    listening: 'LISTENING',
    thinking: 'THINKING',
    speaking: 'SPEAKING',
    searching: 'SEARCHING',
  };

  return (
    <div className="relative flex flex-col items-center justify-center select-none my-auto py-1">
      {/* Background Atmospheric Ambient Glow — radial gradient, NO blur filter
          (a large CSS blur behind a 60fps canvas forces the GPU to re-composite
          the blurred layer every single frame — the main source of phone lag) */}
      <div
        className={`absolute w-72 h-72 sm:w-80 sm:h-80 rounded-full pointer-events-none transition-transform duration-700 ${
          isMuted
            ? 'scale-95'
            : state === 'speaking'
            ? 'scale-110'
            : state === 'thinking'
            ? 'scale-105'
            : state === 'listening' || state === 'searching'
            ? 'scale-100'
            : 'scale-90'
        }`}
        style={{
          background: `radial-gradient(circle, ${accent}30 0%, ${accent}14 45%, transparent 70%)`,
        }}
      />

      {/* Main Interactive 3D Canvas Orb - Sized cleanly and proportionally */}
      <div
        ref={containerRef}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerLeave={handlePointerUp}
        className="relative w-72 h-72 sm:w-80 sm:h-80 flex items-center justify-center cursor-grab active:cursor-grabbing touch-none"
      >
        <canvas
          ref={canvasRef}
          className="relative z-10 w-full h-full block"
        />

        {/* Center Interactive Tap Overlay Button */}
        <motion.button
          onClick={onTogglePower}
          whileTap={{ scale: 0.92 }}
          className="absolute z-20 w-14 h-14 rounded-full bg-black/45 hover:bg-black/65 border backdrop-blur-md flex items-center justify-center text-white transition-all group"
          style={{
            borderColor: `${accent}66`,
            boxShadow: `0 0 18px ${accent}59`,
          }}
          title={state === 'disconnected' ? 'Tap to start voice assistant' : isMuted ? 'Muted' : 'Tap to stop'}
          aria-label="Toggle Assistant"
        >
          {isMuted ? (
            <MicOff className="w-5 h-5 text-rose-400 drop-shadow-[0_0_8px_#f43f5e]" />
          ) : state === 'disconnected' ? (
            <Power className="w-5 h-5 text-slate-300 group-hover:text-cyan-400 transition-colors" />
          ) : state === 'connecting' ? (
            <Sparkles className="w-5 h-5 text-amber-300 animate-spin drop-shadow-[0_0_8px_#fbbf24]" />
          ) : state === 'speaking' ? (
            <Volume2 className="w-5 h-5 animate-pulse drop-shadow-[0_0_8px_#ffc14d]" style={{ color: accent }} />
          ) : state === 'thinking' ? (
            <Brain className="w-5 h-5 animate-pulse drop-shadow-[0_0_8px_#a78bfa]" style={{ color: accent }} />
          ) : state === 'searching' ? (
            <Search className="w-5 h-5 animate-pulse drop-shadow-[0_0_8px_#34d399]" style={{ color: accent }} />
          ) : (
            <Mic className="w-5 h-5 animate-pulse drop-shadow-[0_0_8px_#00d4ff]" style={{ color: accent }} />
          )}
        </motion.button>
      </div>

      {/* Real-time Status Badge Under Orb */}
      <div className="mt-1 flex items-center space-x-3 text-[10px] font-mono text-slate-400 tracking-wider">
        <div className="flex items-center space-x-1.5">
          <div
            className={`w-2 h-2 rounded-full ${state !== 'disconnected' && !isMuted ? 'animate-ping' : ''}`}
            style={{ backgroundColor: accent, boxShadow: `0 0 8px ${accent}` }}
          />
          <span
            className="font-semibold uppercase"
            style={{ color: isMuted ? '#fb7185' : accent }}
          >
            {isMuted ? 'MUTED (IDLE ROTATION)' : STATE_LABELS[state] || state}
          </span>
        </div>
        <span className="text-slate-600">/</span>
        <div className="flex items-center space-x-1 text-slate-300">
          <Radio className="w-3 h-3" style={{ color: accent }} />
          <span>QUANTUM 3D CORE</span>
        </div>
        <span className="text-slate-600">/</span>
        <span className="text-slate-400">16kHz IN • 24kHz OUT</span>
      </div>
    </div>
  );
};
