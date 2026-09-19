import React, { useEffect, useState } from 'react';
import { Mic, MicOff, Power, Sparkles, Volume2 } from 'lucide-react';
import { LiveSessionState } from '../../services/liveSession';
import { audioStreamer } from '../../services/audioStreamer';

interface CentralMicOrbProps {
  state: LiveSessionState;
  isMuted: boolean;
  themeColor: string;
  onTogglePower: () => void;
  onToggleMute: () => void;
}

export const CentralMicOrb: React.FC<CentralMicOrbProps> = ({
  state,
  isMuted,
  themeColor,
  onTogglePower,
  onToggleMute,
}) => {
  const [volumeLevel, setVolumeLevel] = useState<number>(0);

  useEffect(() => {
    let animId: number;
    const updateVolume = () => {
      animId = requestAnimationFrame(updateVolume);
      if (state === 'speaking') {
        setVolumeLevel(audioStreamer.getOutputVolume());
      } else if (state === 'listening' && !isMuted) {
        setVolumeLevel(audioStreamer.getInputVolume());
      } else {
        setVolumeLevel(0);
      }
    };
    updateVolume();
    return () => cancelAnimationFrame(animId);
  }, [state, isMuted]);

  // Color accents
  const themeClasses: Record<string, { ring: string; glow: string; core: string; text: string }> = {
    pink: {
      ring: 'border-rose-500/50',
      glow: 'shadow-[0_0_80px_rgba(244,63,94,0.35)]',
      core: 'from-rose-500 to-pink-600',
      text: 'text-rose-400',
    },
    cyan: {
      ring: 'border-cyan-500/50',
      glow: 'shadow-[0_0_80px_rgba(6,182,212,0.35)]',
      core: 'from-cyan-500 to-blue-600',
      text: 'text-cyan-400',
    },
    purple: {
      ring: 'border-purple-500/50',
      glow: 'shadow-[0_0_80px_rgba(168,85,247,0.35)]',
      core: 'from-purple-500 to-indigo-600',
      text: 'text-purple-400',
    },
    rose: {
      ring: 'border-rose-400/50',
      glow: 'shadow-[0_0_80px_rgba(251,113,133,0.35)]',
      core: 'from-rose-400 to-red-500',
      text: 'text-rose-300',
    },
    emerald: {
      ring: 'border-emerald-500/50',
      glow: 'shadow-[0_0_80px_rgba(16,185,129,0.35)]',
      core: 'from-emerald-400 to-teal-600',
      text: 'text-emerald-400',
    },
  };

  const currentTheme = themeClasses[themeColor] || themeClasses.pink;
  const pulseScale = 1 + Math.min(0.25, volumeLevel * 0.8);

  return (
    <div className="relative flex flex-col items-center justify-center select-none my-auto">
      {/* Outer ambient glow ripple */}
      <div
        className={`absolute w-72 h-72 sm:w-88 sm:h-88 rounded-full blur-3xl opacity-30 transition-all duration-700 pointer-events-none ${
          state === 'speaking'
            ? 'bg-gradient-to-tr from-pink-600 via-rose-500 to-purple-600 scale-110'
            : state === 'listening'
            ? 'bg-gradient-to-tr from-cyan-600 via-sky-500 to-indigo-600'
            : state === 'connecting'
            ? 'bg-purple-800 animate-pulse'
            : 'bg-slate-800/40'
        }`}
      />

      {/* Orbiting ring 1 */}
      <div
        className={`absolute w-60 h-60 sm:w-72 sm:h-72 rounded-full border border-dashed transition-all duration-1000 pointer-events-none ${
          state !== 'disconnected'
            ? `${currentTheme.ring} ${state === 'speaking' ? 'animate-spin' : 'animate-[spin_12s_linear_infinite]'}`
            : 'border-slate-800/60'
        }`}
        style={{ animationDuration: state === 'speaking' ? '4s' : '16s' }}
      />

      {/* Orbiting ring 2 */}
      <div
        className={`absolute w-52 h-52 sm:w-64 sm:h-64 rounded-full border transition-all duration-700 pointer-events-none ${
          state !== 'disconnected'
            ? `border-white/10 ${state === 'listening' ? 'scale-105 opacity-80' : 'opacity-40'}`
            : 'border-slate-800/30'
        }`}
        style={{
          transform: `scale(${pulseScale})`,
          transition: 'transform 80ms ease-out',
        }}
      />

      {/* Main Central Orb Button */}
      <button
        onClick={onTogglePower}
        id="central-mic-power-btn"
        className={`relative z-10 w-40 h-40 sm:w-48 sm:h-48 rounded-full flex flex-col items-center justify-center cursor-pointer transition-all duration-300 transform active:scale-95 focus:outline-none ${
          state === 'disconnected'
            ? 'bg-slate-900/90 border border-slate-700/60 text-slate-400 hover:border-slate-500 hover:text-slate-200 shadow-2xl'
            : state === 'connecting'
            ? 'bg-gradient-to-br from-purple-900/80 via-indigo-950 to-slate-950 border border-purple-500/70 text-purple-200 shadow-[0_0_60px_rgba(168,85,247,0.3)] animate-pulse'
            : state === 'speaking'
            ? `bg-gradient-to-br ${currentTheme.core} text-white ${currentTheme.glow} border-2 border-white/40`
            : `bg-gradient-to-br from-slate-900 via-slate-950 to-cyan-950/80 border-2 ${currentTheme.ring} text-cyan-300 shadow-[0_0_50px_rgba(6,182,212,0.25)]`
        }`}
        style={{
          transform: state !== 'disconnected' ? `scale(${pulseScale})` : undefined,
          transition: 'transform 80ms ease-out, box-shadow 300ms ease',
        }}
        title={state === 'disconnected' ? 'Start Voice Session with MJ' : 'End Voice Session'}
      >
        {/* Icon & Label */}
        {state === 'disconnected' ? (
          <div className="flex flex-col items-center space-y-1.5">
            <Power className="w-10 h-10 text-slate-400 transition-colors group-hover:text-white" />
            <span className="text-[11px] font-semibold tracking-wider uppercase text-slate-400">
              Tap To Talk
            </span>
          </div>
        ) : state === 'connecting' ? (
          <div className="flex flex-col items-center space-y-2">
            <Sparkles className="w-9 h-9 text-purple-300 animate-spin" />
            <span className="text-[11px] font-semibold tracking-wider uppercase text-purple-300">
              Connecting...
            </span>
          </div>
        ) : state === 'speaking' ? (
          <div className="flex flex-col items-center space-y-1.5">
            <Volume2 className="w-11 h-11 text-white animate-pulse" />
            <span className="text-[11px] font-bold tracking-wider uppercase text-white drop-shadow">
              MJ Speaking
            </span>
          </div>
        ) : (
          <div className="flex flex-col items-center space-y-1.5">
            <Mic className="w-11 h-11 text-cyan-300 animate-pulse" />
            <span className="text-[11px] font-bold tracking-wider uppercase text-cyan-300 drop-shadow">
              Listening
            </span>
          </div>
        )}

        {/* Ambient micro-glow badge */}
        <div className="absolute -bottom-3 px-3 py-0.5 rounded-full bg-slate-950/90 border border-slate-800 text-[9px] uppercase tracking-widest font-mono text-slate-300 backdrop-blur-md">
          {state === 'disconnected' ? 'OFFLINE' : state}
        </div>
      </button>

      {/* Mute toggle when active */}
      {state !== 'disconnected' && (
        <button
          onClick={(e) => {
            e.stopPropagation();
            onToggleMute();
          }}
          className={`mt-6 px-4 py-1.5 rounded-full border text-xs font-medium flex items-center space-x-1.5 transition-colors backdrop-blur-md z-20 ${
            isMuted
              ? 'bg-rose-950/70 border-rose-600/70 text-rose-300'
              : 'bg-slate-900/70 border-slate-800 text-slate-400 hover:text-slate-200'
          }`}
        >
          {isMuted ? (
            <>
              <MicOff className="w-3.5 h-3.5 text-rose-400" />
              <span>Mic Muted</span>
            </>
          ) : (
            <>
              <Mic className="w-3.5 h-3.5 text-emerald-400" />
              <span>Mic Active</span>
            </>
          )}
        </button>
      )}
    </div>
  );
};
