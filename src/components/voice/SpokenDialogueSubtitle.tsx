import React from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Mic, Volume2, Sparkles, Radio } from 'lucide-react';
import { LiveSessionState } from '../../services/liveSession';

interface SpokenDialogueSubtitleProps {
  state: LiveSessionState;
  userSpeech: string;
  mjSpeech: string;
}

export const SpokenDialogueSubtitle: React.FC<SpokenDialogueSubtitleProps> = ({
  state,
  userSpeech,
  mjSpeech,
}) => {
  const hasDialogue = Boolean(userSpeech || mjSpeech);

  return (
    <div className="w-full max-w-lg mx-auto px-4 mt-2 flex flex-col items-center justify-center min-h-[90px] select-text">
      <AnimatePresence mode="wait">
        {state === 'disconnected' ? (
          <motion.div
            key="idle-hint"
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ type: 'spring', stiffness: 400, damping: 30 }}
            className="flex flex-col items-center text-center space-y-1 py-1"
          >
            <p className="text-xs text-cyan-400 font-mono tracking-wider flex items-center space-x-1.5">
              <Sparkles className="w-3.5 h-3.5 text-cyan-400 animate-pulse" />
              <span>JARVIS / FRIDAY LIVE QUANTUM CORE</span>
            </p>
            <p className="text-[13px] text-slate-300 font-medium">
              Tap the orb to start live voice interaction
            </p>
          </motion.div>
        ) : !hasDialogue ? (
          <motion.div
            key="listening-hint"
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ type: 'spring', stiffness: 400, damping: 30 }}
            className="px-4 py-2 rounded-2xl bg-cyan-950/30 border border-cyan-500/30 backdrop-blur-xl text-center shadow-[0_0_15px_rgba(0,212,255,0.15)]"
          >
            <p className="text-xs text-cyan-300 font-mono flex items-center justify-center space-x-2">
              <span className="w-2 h-2 rounded-full bg-cyan-400 animate-ping" />
              <span>Listening in real-time... Speak naturally or ask to open any app</span>
            </p>
          </motion.div>
        ) : (
          <motion.div
            key="active-dialogue"
            initial={{ opacity: 0, scale: 0.98, y: 6 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.98, y: -6 }}
            transition={{ type: 'spring', stiffness: 380, damping: 28 }}
            className="w-full space-y-2.5"
          >
            {/* User Spoken Text Box */}
            {userSpeech && (
              <motion.div
                initial={{ opacity: 0, x: -8 }}
                animate={{ opacity: 1, x: 0 }}
                className="flex items-start space-x-2.5 p-3.5 rounded-2xl bg-black/60 border border-cyan-500/30 backdrop-blur-2xl shadow-lg"
              >
                <div className="w-6 h-6 rounded-full bg-cyan-500/20 border border-cyan-400/40 flex items-center justify-center shrink-0 mt-0.5 shadow-[0_0_8px_rgba(0,212,255,0.3)]">
                  <Mic className="w-3.5 h-3.5 text-cyan-300" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between mb-0.5">
                    <span className="text-[10px] font-mono uppercase tracking-widest text-cyan-400 font-bold">
                      You (Spoken Input)
                    </span>
                    <span className="text-[9px] font-mono text-slate-500">16kHz PCM</span>
                  </div>
                  <p className="text-sm text-slate-100 font-medium leading-relaxed break-words">
                    {userSpeech}
                  </p>
                </div>
              </motion.div>
            )}

            {/* MJ AI Response Spoken Text Box */}
            {mjSpeech && (
              <motion.div
                initial={{ opacity: 0, x: 8 }}
                animate={{ opacity: 1, x: 0 }}
                className="flex items-start space-x-2.5 p-3.5 rounded-2xl bg-gradient-to-r from-cyan-950/40 via-blue-950/30 to-slate-900/50 border border-cyan-400/40 backdrop-blur-2xl shadow-lg"
              >
                <div className="w-6 h-6 rounded-full bg-gradient-to-tr from-cyan-500 to-blue-600 flex items-center justify-center shrink-0 mt-0.5 shadow-[0_0_10px_rgba(0,212,255,0.4)]">
                  <Volume2 className="w-3.5 h-3.5 text-slate-950" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between mb-0.5">
                    <span className="text-[10px] font-mono uppercase tracking-widest text-cyan-300 font-bold flex items-center space-x-1.5">
                      <span>JARVIS • MJ</span>
                      <Radio className="w-2.5 h-2.5 text-cyan-400 animate-pulse" />
                    </span>
                    <span className="text-[9px] font-mono text-cyan-400/70">24kHz Audio</span>
                  </div>
                  <p className="text-sm text-cyan-100 font-medium leading-relaxed break-words">
                    {mjSpeech}
                  </p>
                </div>
              </motion.div>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};
