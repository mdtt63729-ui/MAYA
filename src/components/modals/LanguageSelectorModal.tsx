/**
 * Indian Multilingual Selector & Voice Preview Modal
 * PRD v5.1 Sections 33-77: First-class Indian language support with native scripts
 * and instant audio preview testing.
 */

import React from 'react';
import { X, Volume2, Check, Globe } from 'lucide-react';
import { IndianLanguageCode } from '../../types';
import { INDIAN_LANGUAGES } from '../../services/indianLanguageService';
import { audioEngine } from '../../services/audioEngine';

interface LanguageSelectorModalProps {
  isOpen: boolean;
  currentLanguage: IndianLanguageCode;
  onSelectLanguage: (lang: IndianLanguageCode) => void;
  onClose: () => void;
}

export const LanguageSelectorModal: React.FC<LanguageSelectorModalProps> = ({
  isOpen,
  currentLanguage,
  onSelectLanguage,
  onClose,
}) => {
  if (!isOpen) return null;

  const handlePreviewVoice = (langCode: IndianLanguageCode, greeting: string) => {
    audioEngine.speakLocalTTS(greeting, langCode);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md select-none animate-fade-in">
      <div className="w-full max-w-md rounded-3xl bg-[#0e131d] border border-slate-700/80 p-5 shadow-2xl space-y-4 max-h-[85vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-800 pb-3">
          <div className="flex items-center space-x-2.5">
            <div className="w-9 h-9 rounded-xl bg-cyan-950/70 border border-cyan-700/40 flex items-center justify-center text-cyan-400">
              <Globe className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-sm font-semibold text-slate-100">
                Indian Language Voice Intelligence
              </h3>
              <p className="text-[11px] text-slate-400">
                Native scripts, bilingual code-switching & authentic accents
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-slate-800"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Languages List */}
        <div className="flex-1 overflow-y-auto space-y-2 pr-1">
          {INDIAN_LANGUAGES.map((lang) => {
            const isSelected = currentLanguage === lang.code;

            return (
              <div
                key={lang.code}
                onClick={() => onSelectLanguage(lang.code)}
                className={`p-3 rounded-2xl border transition-all cursor-pointer flex items-center justify-between ${
                  isSelected
                    ? 'bg-cyan-950/50 border-cyan-500/60 text-slate-100 shadow-md shadow-cyan-950/40'
                    : 'bg-slate-900/60 border-slate-800/80 text-slate-300 hover:border-slate-700 hover:bg-slate-900'
                }`}
              >
                <div className="space-y-0.5">
                  <div className="flex items-center space-x-2">
                    <span className="text-sm font-semibold tracking-wide">
                      {lang.nativeName}
                    </span>
                    <span className="text-xs text-slate-400">({lang.name})</span>
                    {isSelected && (
                      <span className="p-0.5 rounded-full bg-cyan-400 text-slate-950">
                        <Check className="w-3 h-3 stroke-[3]" />
                      </span>
                    )}
                  </div>
                  <p className="text-[11px] text-slate-400 italic">
                    “{lang.bilingualExample}”
                  </p>
                </div>

                {/* Voice Preview Button */}
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    handlePreviewVoice(lang.code, lang.sampleGreeting);
                  }}
                  className="p-2 rounded-xl text-slate-400 hover:text-cyan-300 hover:bg-slate-800/80 transition-colors shrink-0 ml-2"
                  title={`Preview ${lang.name} voice`}
                >
                  <Volume2 className="w-4 h-4" />
                </button>
              </div>
            );
          })}
        </div>

        {/* Footer info */}
        <div className="pt-2 border-t border-slate-800 text-center">
          <p className="text-[11px] text-slate-400">
            Auto-detection is always active for natural code-switching in speech and text.
          </p>
        </div>
      </div>
    </div>
  );
};
