/**
 * MJ Cinematic Awakening & Welcome Setup Experience
 * PRD Sections 3 & 4: Smooth dark cinematic reveal with permissions & assistant initialization.
 */

import React, { useState } from 'react';
import { MJOrb } from '../orb/MJOrb';
import { Sparkles, Shield, Mic, CheckCircle, ArrowRight } from 'lucide-react';
import { IndianLanguageCode } from '../../types';
import { INDIAN_LANGUAGES } from '../../services/indianLanguageService';
import { audioEngine } from '../../services/audioEngine';

interface WelcomeExperienceProps {
  onComplete: (lang: IndianLanguageCode) => void;
}

export const WelcomeExperience: React.FC<WelcomeExperienceProps> = ({ onComplete }) => {
  const [step, setStep] = useState<number>(1);
  const [selectedLang, setSelectedLang] = useState<IndianLanguageCode>('en-IN');

  const handleNext = async () => {
    if (step === 1) {
      setStep(2);
    } else if (step === 2) {
      // Request mic permission during setup
      await audioEngine.startMicrophone();
      setStep(3);
    } else if (step === 3) {
      // Speak initial greeting: "Hi. I'm MJ. I'm ready."
      audioEngine.speakLocalTTS("Hi. I'm MJ. I'm ready.", selectedLang);
      onComplete(selectedLang);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex flex-col items-center justify-between p-6 bg-[#080a0f] text-slate-100 select-none animate-fade-in overflow-hidden">
      {/* Background Subtle Radial Lighting */}
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,_rgba(0,229,255,0.08)_0%,_transparent_70%)] pointer-events-none" />

      {/* Top Brand Marker */}
      <div className="pt-6 flex items-center space-x-2 z-10">
        <div className="w-2 h-2 rounded-full bg-cyan-400 animate-ping" />
        <span className="font-semibold tracking-widest text-xs uppercase text-slate-400">
          Personal AI Assistant v5.1
        </span>
      </div>

      {/* Center Awakening Orb & Step Visual */}
      <div className="flex flex-col items-center justify-center space-y-5 z-10 my-auto text-center max-w-sm">
        <MJOrb state={step === 1 ? 'READY' : (step === 2 ? 'LISTENING' : 'SPEAKING')} size="large" />

        {step === 1 && (
          <div className="space-y-2 animate-slide-up">
            <h1 className="text-2xl font-bold tracking-tight text-slate-100">
              Meet MJ
            </h1>
            <p className="text-xs leading-relaxed text-slate-400">
              An intelligent, persistent Android AI assistant engineered with real-time audio reactivity, screen vision, and Indian multilingual intelligence.
            </p>
          </div>
        )}

        {step === 2 && (
          <div className="space-y-3 animate-slide-up">
            <div className="inline-flex p-3 rounded-2xl bg-cyan-950/60 border border-cyan-700/50 text-cyan-400 mx-auto">
              <Mic className="w-6 h-6" />
            </div>
            <h2 className="text-lg font-bold text-slate-100">
              Microphone & Permissions
            </h2>
            <p className="text-xs text-slate-400 leading-relaxed">
              MJ uses live audio activity detection (VAD) to understand commands and provide audible, conversational responses with real-time interruption.
            </p>
          </div>
        )}

        {step === 3 && (
          <div className="space-y-3 animate-slide-up">
            <h2 className="text-lg font-bold text-slate-100">
              Select Primary Language
            </h2>
            <div className="grid grid-cols-2 gap-2 text-xs">
              {INDIAN_LANGUAGES.slice(0, 6).map((lang) => (
                <button
                  key={lang.code}
                  onClick={() => setSelectedLang(lang.code)}
                  className={`p-2.5 rounded-xl border text-left transition-all ${
                    selectedLang === lang.code
                      ? 'bg-cyan-950/60 border-cyan-500 text-cyan-200'
                      : 'bg-slate-900 border-slate-800 text-slate-400'
                  }`}
                >
                  <span className="font-semibold block text-xs">{lang.nativeName}</span>
                  <span className="text-[10px] text-slate-500">{lang.name}</span>
                </button>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Bottom Action Card */}
      <div className="w-full max-w-sm z-10 pb-4">
        <button
          onClick={handleNext}
          className="w-full py-3.5 px-6 rounded-2xl bg-cyan-400 hover:bg-cyan-300 active:scale-98 text-slate-950 font-semibold text-xs tracking-wider uppercase flex items-center justify-center space-x-2 shadow-xl shadow-cyan-950/60 transition-all"
        >
          <span>{step === 3 ? "Initialize MJ" : "Continue"}</span>
          <ArrowRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
};
