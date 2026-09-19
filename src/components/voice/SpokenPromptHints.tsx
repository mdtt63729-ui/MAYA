import React from 'react';
import { Mic, Radio } from 'lucide-react';
import { LiveSessionState } from '../../services/liveSession';

interface SpokenPromptHintsProps {
  state: LiveSessionState;
  onPromptHintClick?: (hint: string) => void;
}

export const SpokenPromptHints: React.FC<SpokenPromptHintsProps> = ({ state }) => {
  const hints = [
    '“Hey MJ, what’s up?”',
    '“Open YouTube for me”',
    '“Are you always this sassy?”',
    '“Open Spotify”',
    '“Search latest tech news”',
    '“Change theme to cyan”',
  ];

  return (
    <div className="w-full max-w-md mx-auto px-4 py-3 flex flex-col items-center select-none text-center">
      <div className="flex items-center space-x-1.5 text-[11px] font-mono uppercase tracking-widest text-slate-500 mb-2">
        <Radio className="w-3 h-3 text-pink-500 animate-pulse" />
        <span>Try Saying Out Loud</span>
      </div>

      <div className="flex flex-wrap items-center justify-center gap-1.5">
        {hints.map((hint, idx) => (
          <span
            key={idx}
            className={`text-[11px] px-3 py-1 rounded-full border transition-all ${
              state === 'listening'
                ? 'bg-slate-900/60 border-slate-800 text-slate-300'
                : 'bg-slate-950/40 border-slate-900 text-slate-500'
            }`}
          >
            {hint}
          </span>
        ))}
      </div>

      {state === 'disconnected' && (
        <p className="mt-3 text-xs text-slate-400 font-medium flex items-center space-x-1">
          <Mic className="w-3.5 h-3.5 text-pink-400 inline shrink-0" />
          <span>Tap the center power orb to start the live voice call</span>
        </p>
      )}
    </div>
  );
};
