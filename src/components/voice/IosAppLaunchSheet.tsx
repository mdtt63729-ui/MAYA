import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { X, ExternalLink, Camera, Calculator, Zap, AppWindow, CheckCircle2 } from 'lucide-react';
import { AppActionPayload } from '../../services/liveSession';

interface IosAppLaunchSheetProps {
  appAction: AppActionPayload | null;
  onClose: () => void;
}

export const IosAppLaunchSheet: React.FC<IosAppLaunchSheetProps> = ({ appAction, onClose }) => {
  const [calcInput, setCalcInput] = useState('0');
  const [flashlightOn, setFlashlightOn] = useState(true);

  if (!appAction) return null;

  const isBuiltInCamera = appAction.appName.toLowerCase() === 'camera' || appAction.url === '#camera';
  const isBuiltInCalc = appAction.appName.toLowerCase() === 'calculator' || appAction.url === '#calculator';
  const isBuiltInTorch = appAction.appName.toLowerCase() === 'flashlight' || appAction.url === '#flashlight';

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-end justify-center">
        {/* Backdrop */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
          className="absolute inset-0 bg-black/75 backdrop-blur-md"
        />

        {/* iOS Native Modal Sheet */}
        <motion.div
          initial={{ y: '100%' }}
          animate={{ y: 0 }}
          exit={{ y: '100%' }}
          transition={{ type: 'spring', damping: 28, stiffness: 350 }}
          className="relative z-10 w-full max-w-md bg-[#0a0f1e]/95 border-t border-cyan-500/30 rounded-t-[32px] p-6 shadow-2xl backdrop-blur-3xl text-slate-100 flex flex-col"
        >
          {/* Drag Handle */}
          <div className="w-12 h-1.5 rounded-full bg-white/20 mx-auto mb-4 shrink-0" />

          {/* Header */}
          <div className="flex items-center justify-between pb-3 border-b border-white/10 shrink-0">
            <div className="flex items-center space-x-2.5">
              <div className="w-8 h-8 rounded-full bg-cyan-500/20 border border-cyan-400/50 flex items-center justify-center">
                <AppWindow className="w-4 h-4 text-cyan-300" />
              </div>
              <div>
                <span className="text-[10px] font-mono text-cyan-400 tracking-widest uppercase block font-semibold">
                  App Protocol
                </span>
                <h4 className="text-base font-semibold text-white capitalize">
                  {appAction.appName}
                </h4>
              </div>
            </div>

            <motion.button
              onClick={onClose}
              whileTap={{ scale: 0.9 }}
              className="w-8 h-8 rounded-full bg-white/10 hover:bg-white/15 flex items-center justify-center text-slate-300 hover:text-white"
            >
              <X className="w-4 h-4" />
            </motion.button>
          </div>

          {/* Body Content */}
          <div className="py-5">
            {isBuiltInCamera ? (
              <div className="flex flex-col items-center space-y-3">
                <div className="w-full h-48 rounded-2xl bg-black border border-cyan-500/40 relative overflow-hidden flex flex-col items-center justify-center">
                  <div className="absolute inset-4 border border-dashed border-cyan-400/40 rounded-xl pointer-events-none" />
                  <Camera className="w-10 h-10 text-cyan-400 animate-pulse" />
                  <span className="text-xs font-mono text-cyan-300 mt-2">
                    CAMERA HUD ACTIVE
                  </span>
                </div>
                <p className="text-xs text-slate-400 text-center">
                  Live camera viewport initialized by voice command.
                </p>
              </div>
            ) : isBuiltInCalc ? (
              <div className="p-4 rounded-2xl bg-black/40 border border-white/10 space-y-3">
                <div className="text-right text-2xl font-mono text-cyan-300 p-2 bg-slate-900/80 rounded-xl">
                  {calcInput}
                </div>
                <div className="grid grid-cols-4 gap-2 text-sm font-mono">
                  {['7', '8', '9', '/', '4', '5', '6', '*', '1', '2', '3', '-', 'C', '0', '=', '+'].map((btn) => (
                    <button
                      key={btn}
                      onClick={() => {
                        if (btn === 'C') setCalcInput('0');
                        else if (btn === '=') {
                          try {
                            // Simple safe math
                            const sanitized = calcInput.replace(/[^0-9+\-*/.]/g, '');
                            // eslint-disable-next-line no-eval
                            setCalcInput(String(Function(`'use strict'; return (${sanitized})`)()));
                          } catch {
                            setCalcInput('Error');
                          }
                        } else {
                          setCalcInput(calcInput === '0' ? btn : calcInput + btn);
                        }
                      }}
                      className="p-2.5 rounded-xl bg-white/5 hover:bg-white/10 active:scale-95 text-slate-200 transition-colors"
                    >
                      {btn}
                    </button>
                  ))}
                </div>
              </div>
            ) : isBuiltInTorch ? (
              <div className="flex flex-col items-center py-4 space-y-3">
                <div
                  className={`w-24 h-24 rounded-full flex items-center justify-center transition-all ${
                    flashlightOn
                      ? 'bg-amber-400 text-slate-950 shadow-[0_0_60px_#f59e0b]'
                      : 'bg-slate-800 text-slate-400'
                  }`}
                >
                  <Zap className="w-10 h-10" />
                </div>
                <button
                  onClick={() => setFlashlightOn(!flashlightOn)}
                  className="px-4 py-2 rounded-xl bg-white/10 text-xs font-mono text-slate-200"
                >
                  {flashlightOn ? 'Turn Flashlight Off' : 'Turn Flashlight On'}
                </button>
              </div>
            ) : (
              <div className="flex flex-col items-center space-y-4 text-center">
                <div className="w-16 h-16 rounded-2xl bg-cyan-500/20 border border-cyan-400/40 flex items-center justify-center shadow-lg shadow-cyan-500/20">
                  <CheckCircle2 className="w-8 h-8 text-cyan-400" />
                </div>

                <div className="space-y-1">
                  <h5 className="text-sm font-semibold text-white">
                    App Launch Triggered
                  </h5>
                  <p className="text-xs text-slate-400 max-w-xs">
                    MJ initiated launch for <strong className="text-cyan-300">{appAction.appName}</strong>. Tap below if not opened automatically.
                  </p>
                </div>

                {appAction.url && (
                  <motion.a
                    href={appAction.url.startsWith('http') ? appAction.url : `https://${appAction.url}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    whileTap={{ scale: 0.96 }}
                    className="w-full py-3.5 rounded-2xl bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-slate-950 font-bold text-sm flex items-center justify-center space-x-2 shadow-lg shadow-cyan-500/25 transition-all"
                  >
                    <span>Open {appAction.appName}</span>
                    <ExternalLink className="w-4 h-4 text-slate-950" />
                  </motion.a>
                )}
              </div>
            )}
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
};
