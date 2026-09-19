import React from 'react';
import { motion } from 'motion/react';
import { Settings, ShieldAlert } from 'lucide-react';
import { LiveSessionState } from '../../services/liveSession';

interface IosTopBarProps {
  state: LiveSessionState;
  interruptedAlert: boolean;
  onOpenSettings: () => void;
}

export const IosTopBar: React.FC<IosTopBarProps> = ({
  state,
  interruptedAlert,
  onOpenSettings,
}) => {
  return (
    <header className="w-full max-w-2xl mx-auto px-5 py-4 flex items-center justify-between z-30 select-none">
      {/* Brand & Status */}
      <div className="flex items-center space-x-3">
        <div className="relative flex items-center justify-center">
          <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-cyan-500 to-blue-600 flex items-center justify-center shadow-[0_0_15px_rgba(56,189,248,0.4)]">
            <span className="text-[11px] font-mono font-black text-white tracking-tighter">
              MJ
            </span>
          </div>
          {state !== 'disconnected' && (
            <span className="absolute -top-0.5 -right-0.5 w-2.5 h-2.5 bg-cyan-400 border-2 border-[#04060a] rounded-full animate-ping" />
          )}
        </div>

        <div>
          <div className="flex items-center space-x-1.5">
            <span className="text-xs font-mono font-bold tracking-wider text-slate-100 uppercase">
              JARVIS • FRIDAY CORE
            </span>
          </div>
          <p className="text-[10px] font-mono text-cyan-400/80 tracking-wide">
            {state === 'disconnected' ? 'SYSTEM STANDBY' : `LIVE • ${state.toUpperCase()}`}
          </p>
        </div>
      </div>

      {/* Center Interruption Pill */}
      {interruptedAlert && (
        <motion.div
          initial={{ opacity: 0, scale: 0.9, y: -4 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.9, y: -4 }}
          transition={{ type: 'spring', stiffness: 400, damping: 25 }}
          className="px-2.5 py-1 rounded-full bg-amber-950/80 border border-amber-500/60 text-amber-300 text-[10px] font-mono font-semibold flex items-center space-x-1 shadow-lg"
        >
          <ShieldAlert className="w-3 h-3 text-amber-400" />
          <span>Interrupted</span>
        </motion.div>
      )}

      {/* Right Side: ONLY ONE clean native iOS Settings Button */}
      <motion.button
        id="ios-settings-trigger"
        onClick={onOpenSettings}
        whileHover={{ scale: 1.05 }}
        whileTap={{ scale: 0.92 }}
        transition={{ type: 'spring', stiffness: 400, damping: 25 }}
        className="w-10 h-10 rounded-full bg-white/[0.06] hover:bg-white/[0.12] border border-white/10 text-slate-300 hover:text-white flex items-center justify-center backdrop-blur-2xl shadow-md transition-colors"
        title="Settings & System Diagnostics"
        aria-label="Settings"
      >
        <Settings className="w-4 h-4 text-cyan-300" />
      </motion.button>
    </header>
  );
};
