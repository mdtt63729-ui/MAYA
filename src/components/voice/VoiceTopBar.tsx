import React from 'react';
import { Sparkles, Palette, ShieldAlert, Wifi, WifiOff } from 'lucide-react';
import { LiveSessionState } from '../../services/liveSession';

interface VoiceTopBarProps {
  state: LiveSessionState;
  themeColor: string;
  onSelectTheme: (theme: string) => void;
  interruptedAlert: boolean;
}

export const VoiceTopBar: React.FC<VoiceTopBarProps> = ({
  state,
  themeColor,
  onSelectTheme,
  interruptedAlert,
}) => {
  const [showThemePicker, setShowThemePicker] = React.useState(false);

  const themes = [
    { id: 'pink', name: 'Neon Rose', bg: 'bg-rose-500' },
    { id: 'cyan', name: 'Cyber Cyan', bg: 'bg-cyan-500' },
    { id: 'purple', name: 'Electric Purple', bg: 'bg-purple-500' },
    { id: 'emerald', name: 'Matrix Emerald', bg: 'bg-emerald-500' },
  ];

  return (
    <header className="w-full max-w-3xl mx-auto px-4 py-3 flex items-center justify-between z-30 select-none">
      {/* Brand & Persona Badge */}
      <div className="flex items-center space-x-2.5">
        <div className="relative">
          <div className="w-8 h-8 rounded-xl bg-gradient-to-tr from-pink-500 via-rose-500 to-purple-600 flex items-center justify-center shadow-lg shadow-pink-500/25">
            <Sparkles className="w-4 h-4 text-white" />
          </div>
          {state !== 'disconnected' && (
            <span className="absolute -top-0.5 -right-0.5 w-2.5 h-2.5 bg-emerald-400 border-2 border-[#05070c] rounded-full animate-ping" />
          )}
        </div>

        <div>
          <div className="flex items-center space-x-1.5">
            <h1 className="text-sm font-bold tracking-tight text-white font-mono">MJ</h1>
            <span className="text-[10px] px-1.5 py-0.2 rounded bg-pink-950/70 border border-pink-700/50 text-pink-300 font-semibold tracking-wide">
              LIVE
            </span>
          </div>
          <p className="text-[10px] text-slate-400 font-medium">
            Confident • Witty • Sassy
          </p>
        </div>
      </div>

      {/* Center Interruption Pill */}
      {interruptedAlert && (
        <div className="animate-in fade-in zoom-in duration-200 px-2.5 py-1 rounded-full bg-amber-950/80 border border-amber-600/70 text-amber-300 text-[10px] font-mono font-semibold flex items-center space-x-1 shadow-lg">
          <ShieldAlert className="w-3 h-3 text-amber-400" />
          <span>Interrupted</span>
        </div>
      )}

      {/* Right Controls: Theme Selector & Status */}
      <div className="flex items-center space-x-2">
        {/* Status Dot */}
        <div className="flex items-center space-x-1.5 px-2 py-1 rounded-full bg-slate-900/80 border border-slate-800 text-[10px] font-mono text-slate-300">
          {state === 'disconnected' ? (
            <WifiOff className="w-3 h-3 text-slate-500" />
          ) : (
            <Wifi className="w-3 h-3 text-emerald-400 animate-pulse" />
          )}
          <span className="capitalize">{state}</span>
        </div>

        {/* Theme Picker Button */}
        <div className="relative">
          <button
            onClick={() => setShowThemePicker(!showThemePicker)}
            className="p-1.5 rounded-lg bg-slate-900/80 hover:bg-slate-800 text-slate-400 hover:text-white border border-slate-800 transition-colors"
            title="Choose Aesthetic"
          >
            <Palette className="w-3.5 h-3.5" />
          </button>

          {showThemePicker && (
            <div className="absolute right-0 mt-2 p-2 rounded-xl bg-slate-900/95 border border-slate-800 shadow-2xl backdrop-blur-xl flex space-x-1.5 z-50">
              {themes.map((t) => (
                <button
                  key={t.id}
                  onClick={() => {
                    onSelectTheme(t.id);
                    setShowThemePicker(false);
                  }}
                  className={`w-6 h-6 rounded-full ${t.bg} transition-transform hover:scale-110 ${
                    themeColor === t.id ? 'ring-2 ring-white ring-offset-2 ring-offset-slate-900' : 'opacity-70 hover:opacity-100'
                  }`}
                  title={t.name}
                />
              ))}
            </div>
          )}
        </div>
      </div>
    </header>
  );
};
