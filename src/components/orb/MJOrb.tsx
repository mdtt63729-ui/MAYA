/**
 * MJ Orb 2.0 - Flagship Real-Time Audio-Reactive Visual System
 * 8-Layer GPU Canvas renderer with spring physics, waveform reactivity, and state transitions
 * PRD v5.0 Sections 10-13 & v5.1 Sections 14-20 compliant.
 */

import React, { useEffect, useRef } from 'react';
import { MJState } from '../../types';
import { audioEngine } from '../../services/audioEngine';

interface MJOrbProps {
  state: MJState;
  size?: 'large' | 'medium' | 'mini';
  interactive?: boolean;
  onClick?: () => void;
  className?: string;
}

export const MJOrb: React.FC<MJOrbProps> = ({
  state,
  size = 'large',
  interactive = true,
  onClick,
  className = '',
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const amplitudeRef = useRef<number>(0);
  const targetAmpRef = useRef<number>(0);
  const frequenciesRef = useRef<number[]>([]);
  const rotationRef = useRef<number>(0);
  const pulseRef = useRef<number>(0);
  const particlesRef = useRef<Array<{ angle: number; radius: number; speed: number; size: number; alpha: number }>>([]);

  const dimensions = {
    large: 280,
    medium: 180,
    mini: 76,
  }[size];

  // Initialize orbital particles
  useEffect(() => {
    const pCount = size === 'mini' ? 12 : 24;
    particlesRef.current = Array.from({ length: pCount }, (_, i) => ({
      angle: (i / pCount) * Math.PI * 2,
      radius: dimensions * 0.38 + (Math.random() - 0.5) * 20,
      speed: 0.008 + Math.random() * 0.015,
      size: 1.5 + Math.random() * 2,
      alpha: 0.3 + Math.random() * 0.5,
    }));
  }, [size, dimensions]);

  // Subscribe to real audio engine amplitude
  useEffect(() => {
    const unsub = audioEngine.onAudioData((amp, freqs) => {
      targetAmpRef.current = amp;
      frequenciesRef.current = freqs;
    });
    return () => unsub();
  }, []);

  // Main Canvas Render Loop (60 FPS)
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animId: number;
    const dpr = window.devicePixelRatio || 1;
    canvas.width = dimensions * dpr;
    canvas.height = dimensions * dpr;
    ctx.scale(dpr, dpr);

    const center = dimensions / 2;

    const render = () => {
      // Smooth amplitude dampening (Spring physics)
      amplitudeRef.current += (targetAmpRef.current - amplitudeRef.current) * 0.22;
      const amp = amplitudeRef.current;

      pulseRef.current += state === 'THINKING' ? 0.05 : 0.025;
      const breathing = Math.sin(pulseRef.current) * 0.06;

      ctx.clearRect(0, 0, dimensions, dimensions);

      // Determine palette based on MJ State
      let primaryColor = 'rgba(0, 229, 255, '; // Electric Cyan
      let secondaryColor = 'rgba(56, 189, 248, '; // Ice Blue
      let coreColor = 'rgba(255, 255, 255, ';

      if (state === 'ERROR') {
        primaryColor = 'rgba(239, 68, 68, ';
        secondaryColor = 'rgba(248, 113, 113, ';
      } else if (state === 'LISTENING') {
        primaryColor = 'rgba(6, 182, 212, ';
        secondaryColor = 'rgba(14, 165, 233, ';
      } else if (state === 'THINKING') {
        primaryColor = 'rgba(129, 140, 248, ';
        secondaryColor = 'rgba(99, 102, 241, ';
      } else if (state === 'SPEAKING') {
        primaryColor = 'rgba(34, 211, 238, ';
        secondaryColor = 'rgba(147, 197, 253, ';
      }

      const baseRadius = (dimensions * 0.28) * (1 + breathing + (state === 'SPEAKING' ? amp * 0.35 : (state === 'LISTENING' ? amp * 0.2 : 0)));

      // LAYER 1: Subtle Ambient Background Glow
      const ambientGrad = ctx.createRadialGradient(center, center, baseRadius * 0.2, center, center, dimensions * 0.48);
      ambientGrad.addColorStop(0, primaryColor + (0.15 + amp * 0.25) + ')');
      ambientGrad.addColorStop(0.6, secondaryColor + (0.05 + amp * 0.1) + ')');
      ambientGrad.addColorStop(1, 'rgba(8, 10, 15, 0)');
      ctx.fillStyle = ambientGrad;
      ctx.beginPath();
      ctx.arc(center, center, dimensions * 0.48, 0, Math.PI * 2);
      ctx.fill();

      // LAYER 2: Outer Atmospheric Halo
      ctx.save();
      ctx.strokeStyle = primaryColor + (0.2 + amp * 0.3) + ')';
      ctx.lineWidth = 1.2;
      ctx.setLineDash([4, 8]);
      ctx.beginPath();
      ctx.arc(center, center, baseRadius * 1.35, 0, Math.PI * 2);
      ctx.stroke();
      ctx.restore();

      // LAYER 3 & 4: Primary & Secondary Orbital Rings with rotation
      const rotSpeed = state === 'THINKING' ? 0.04 : 0.008;
      rotationRef.current += rotSpeed;
      const rot = rotationRef.current;

      // Primary Ring
      ctx.save();
      ctx.translate(center, center);
      ctx.rotate(rot);
      ctx.strokeStyle = primaryColor + (0.6 + amp * 0.4) + ')';
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.ellipse(0, 0, baseRadius * 1.18, baseRadius * 1.05, Math.PI / 4, 0, Math.PI * 2);
      ctx.stroke();
      ctx.restore();

      // Secondary Ring (Counter-rotating)
      ctx.save();
      ctx.translate(center, center);
      ctx.rotate(-rot * 0.85);
      ctx.strokeStyle = secondaryColor + (0.45 + amp * 0.3) + ')';
      ctx.lineWidth = 1.4;
      ctx.beginPath();
      ctx.ellipse(0, 0, baseRadius * 1.15, baseRadius * 0.98, -Math.PI / 3, 0, Math.PI * 2);
      ctx.stroke();
      ctx.restore();

      // LAYER 5: Tiny Orbital Particles
      particlesRef.current.forEach((p) => {
        p.angle += p.speed * (state === 'THINKING' ? 2.5 : 1);
        const px = center + Math.cos(p.angle) * p.radius * (1 + breathing * 0.5);
        const py = center + Math.sin(p.angle) * p.radius * (1 + breathing * 0.5);

        ctx.fillStyle = primaryColor + p.alpha + ')';
        ctx.beginPath();
        ctx.arc(px, py, p.size, 0, Math.PI * 2);
        ctx.fill();
      });

      // LAYER 6: Dynamic Audio Waveform (Undulating Ring)
      const segments = 32;
      ctx.save();
      ctx.strokeStyle = primaryColor + (0.75 + amp * 0.25) + ')';
      ctx.lineWidth = 2.5;
      ctx.beginPath();
      for (let i = 0; i <= segments; i++) {
        const theta = (i / segments) * Math.PI * 2;
        const freqIdx = i % (frequenciesRef.current.length || 1);
        const fVal = frequenciesRef.current[freqIdx] || 0;
        const waveOffset = (state === 'SPEAKING' || state === 'LISTENING')
          ? Math.sin(theta * 6 + rot * 4) * (amp * 18 + fVal * 12)
          : Math.sin(theta * 4 + rot * 2) * 2;

        const r = baseRadius * 0.92 + waveOffset;
        const wx = center + Math.cos(theta) * r;
        const wy = center + Math.sin(theta) * r;
        if (i === 0) ctx.moveTo(wx, wy);
        else ctx.lineTo(wx, wy);
      }
      ctx.closePath();
      ctx.stroke();
      ctx.restore();

      // LAYER 7: Inner Crystalline Light Core
      const coreGrad = ctx.createRadialGradient(center, center, 0, center, center, baseRadius * 0.72);
      coreGrad.addColorStop(0, coreColor + '0.95)');
      coreGrad.addColorStop(0.35, primaryColor + '0.75)');
      coreGrad.addColorStop(0.8, secondaryColor + '0.35)');
      coreGrad.addColorStop(1, 'rgba(8, 10, 15, 0)');

      ctx.fillStyle = coreGrad;
      ctx.beginPath();
      ctx.arc(center, center, baseRadius * 0.72, 0, Math.PI * 2);
      ctx.fill();

      // LAYER 8: MJ Monogram / Symbol in center
      if (size !== 'mini') {
        ctx.save();
        ctx.font = `600 ${dimensions * 0.1}px system-ui, -apple-system, sans-serif`;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillStyle = 'rgba(255, 255, 255, 0.92)';
        ctx.shadowColor = primaryColor + '0.8)';
        ctx.shadowBlur = 10;
        ctx.fillText('MJ', center, center);
        ctx.restore();
      }

      animId = requestAnimationFrame(render);
    };

    animId = requestAnimationFrame(render);
    return () => cancelAnimationFrame(animId);
  }, [dimensions, size, state]);

  return (
    <div
      id="mj-orb-container"
      className={`relative flex items-center justify-center select-none transition-transform duration-200 active:scale-95 ${
        interactive ? 'cursor-pointer' : ''
      } ${className}`}
      onClick={onClick}
      style={{ width: dimensions, height: dimensions }}
    >
      <canvas
        ref={canvasRef}
        style={{ width: dimensions, height: dimensions }}
        className="pointer-events-none drop-shadow-2xl"
      />
    </div>
  );
};
