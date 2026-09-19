import React, { useEffect, useRef } from 'react';
import { audioStreamer } from '../../services/audioStreamer';
import { LiveSessionState } from '../../services/liveSession';

interface VoiceVisualizerProps {
  state: LiveSessionState;
  themeColor: string;
}

export const VoiceVisualizer: React.FC<VoiceVisualizerProps> = ({ state, themeColor }) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    let animId: number;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const render = () => {
      animId = requestAnimationFrame(render);
      const width = canvas.width;
      const height = canvas.height;
      ctx.clearRect(0, 0, width, height);

      if (state === 'disconnected') {
        // Subtle ambient idle wave
        const time = Date.now() * 0.002;
        ctx.beginPath();
        ctx.strokeStyle = 'rgba(100, 116, 139, 0.2)';
        ctx.lineWidth = 1.5;
        for (let x = 0; x < width; x += 4) {
          const y = height / 2 + Math.sin(x * 0.03 + time) * 6;
          if (x === 0) ctx.moveTo(x, y);
          else ctx.lineTo(x, y);
        }
        ctx.stroke();
        return;
      }

      if (state === 'connecting') {
        // Pulsing radar-like sine
        const time = Date.now() * 0.005;
        ctx.beginPath();
        ctx.strokeStyle = 'rgba(168, 85, 247, 0.4)';
        ctx.lineWidth = 2;
        for (let x = 0; x < width; x += 3) {
          const y = height / 2 + Math.sin(x * 0.05 + time) * Math.sin(time * 0.5) * 12;
          if (x === 0) ctx.moveTo(x, y);
          else ctx.lineTo(x, y);
        }
        ctx.stroke();
        return;
      }

      // Active states: Listening or Speaking
      const isSpeaking = state === 'speaking';
      const spectrum = audioStreamer.getSpectrum(isSpeaking ? 'output' : 'input');
      const barCount = 36;
      const step = Math.floor(spectrum.length / barCount);
      const barWidth = Math.max(3, (width - barCount * 3) / barCount);

      const colorMap: Record<string, { main: string; glow: string }> = {
        pink: { main: '#f43f5e', glow: 'rgba(244, 63, 94, 0.4)' },
        cyan: { main: '#06b6d4', glow: 'rgba(6, 182, 212, 0.4)' },
        purple: { main: '#a855f7', glow: 'rgba(168, 85, 247, 0.4)' },
        rose: { main: '#fb7185', glow: 'rgba(251, 113, 133, 0.4)' },
        emerald: { main: '#10b981', glow: 'rgba(16, 185, 129, 0.4)' },
      };

      const currentColors = colorMap[themeColor] || colorMap.pink;
      const center = height / 2;

      for (let i = 0; i < barCount; i++) {
        const val = spectrum[i * step] || 0;
        const norm = val / 255;
        const barHeight = Math.max(4, norm * (height * 0.75));

        const x = i * (barWidth + 3) + 6;
        const yTop = center - barHeight / 2;

        // PERFORMANCE: no shadowBlur (mobile GPU killer) — a soft faded
        // backing bar gives the glow look at a fraction of the cost
        ctx.fillStyle = currentColors.glow;
        ctx.fillRect(x - 1, yTop - 4, barWidth + 2, barHeight + 8);

        ctx.fillStyle = currentColors.main;

        // Rounded pill bars
        ctx.beginPath();
        ctx.roundRect(x, yTop, barWidth, barHeight, 2);
        ctx.fill();
      }
    };

    render();

    return () => {
      cancelAnimationFrame(animId);
    };
  }, [state, themeColor]);

  return (
    <div className="w-full max-w-sm px-4 py-2 flex items-center justify-center">
      <canvas
        ref={canvasRef}
        width={320}
        height={64}
        className="w-full h-16 pointer-events-none"
      />
    </div>
  );
};
