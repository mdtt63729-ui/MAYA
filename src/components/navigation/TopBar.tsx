/**
 * Flagship Top Bar with Native Android Edge-to-Edge styling,
 * network indicators, Indian language selector, and quick triggers.
 */

import React from 'react';
import {
  Globe,
  Brain,
  Eye,
  Sparkles,
  Settings as SettingsIcon,
  Wifi,
  WifiOff,
  Minimize2,
  Maximize2,
} from 'lucide-react';
import { GeminiModelId, IndianLanguageCode } from '../../types';
import { INDIAN_LANGUAGES } from '../../services/indianLanguageService';

interface TopBarProps {
  currentLanguage: IndianLanguageCode;
  selectedModel: GeminiModelId;
  isOnline: boolean;
  isFloating: boolean;
  onToggleFloating: () => void;
  onOpenLanguage: () => void;
  onOpenMemory: () => void;
  onOpenVision: () => void;
  onOpenStudio: () => void;
  onOpenSettings: () => void;
}

export const TopBar: React.FC<TopBarProps> = ({
  currentLanguage,
  selectedModel,
  isOnline,
  isFloating,
  onToggleFloating,
  onOpenLanguage,
  onOpenMemory,
  onOpenVision,
  onOpenStudio,
  onOpenSettings,
}) => {
  const currentLangInfo = INDIAN_LANGUAGES.find((l) => l.code === currentLanguage) || INDIAN_LANGUAGES[0];

  const modelLabel = {
    'gemini-3.1-flash-lite': '3.1 Lite',
    'gemini-3.5-flash': '3.5 Flash',
    'gemini-3.1-pro-preview': '3.1 Pro (Think)',
  }[selectedModel];

  return (
    <header className="w-full pt-2 pb-2 px-4 flex items-center justify-between z-30 select-none bg-[#080a0f]/80 backdrop-blur-md border-b border-slate-800/60">
      {/* Brand & Network Indicator */}
      <div className="flex items-center space-x-2">
        <div className="flex items-center space-x-1.5">
          <div className="w-2 h-2 rounded-full bg-cyan-400 animate-pulse" />
          <span className="font-semibold tracking-wider text-sm text-slate-100 uppercase">
            MJ
          </span>
          <span className="text-[10px] text-cyan-400/80 px-1.5 py-0.5 rounded bg-cyan-950/60 border border-cyan-800/40">
            v5.1
          </span>
        </div>

        {/* Network status indicator */}
        <div
          className={`flex items-center space-x-1 px-1.5 py-0.5 rounded text-[11px] ${
            isOnline
              ? 'text-emerald-400 bg-emerald-950/40'
              : 'text-rose-400 bg-rose-950/40'
          }`}
          title={isOnline ? 'Online Engine Active' : 'Offline Local Mode Active'}
        >
          {isOnline ? <Wifi className="w-3 h-3" /> : <WifiOff className="w-3 h-3" />}
          <span className="text-[10px] font-medium hidden sm:inline">
            {isOnline ? 'ONLINE' : 'OFFLINE'}
          </span>
        </div>
      </div>

      {/* Model & Quick Actions */}
      <div className="flex items-center space-x-1.5">
        {/* Model Badge */}
        <button
          onClick={onOpenSettings}
          className="hidden sm:flex items-center space-x-1 text-[11px] px-2 py-1 rounded-md bg-slate-900/90 text-slate-300 border border-slate-700/60 hover:border-cyan-500/50 transition-colors"
        >
          <Sparkles className="w-3 h-3 text-cyan-400" />
          <span>{modelLabel}</span>
        </button>

        {/* Language Switcher */}
        <button
          onClick={onOpenLanguage}
          className="flex items-center space-x-1 text-[11px] px-2 py-1 rounded-md bg-slate-900/90 text-slate-200 border border-slate-700/60 hover:border-cyan-500/50 transition-colors"
          title="Change Assistant Language"
        >
          <Globe className="w-3.5 h-3.5 text-cyan-400" />
          <span className="font-medium">{currentLangInfo.nativeName}</span>
        </button>

        {/* Screen Vision Button */}
        <button
          onClick={onOpenVision}
          className="p-1.5 rounded-md text-slate-300 hover:text-cyan-300 hover:bg-slate-800/60 transition-colors"
          title="Screen Vision & Understanding"
        >
          <Eye className="w-4 h-4" />
        </button>

        {/* Memory Vault Button */}
        <button
          onClick={onOpenMemory}
          className="p-1.5 rounded-md text-slate-300 hover:text-cyan-300 hover:bg-slate-800/60 transition-colors"
          title="Long-Term Memory Vault"
        >
          <Brain className="w-4 h-4" />
        </button>

        {/* Gemini Multi-Modal Studio Button */}
        <button
          onClick={onOpenStudio}
          className="p-1.5 rounded-md text-slate-300 hover:text-cyan-300 hover:bg-slate-800/60 transition-colors"
          title="Gemini Multi-Modal Tools Studio"
        >
          <Sparkles className="w-4 h-4 text-cyan-400" />
        </button>

        {/* Floating Orb Toggle */}
        <button
          onClick={onToggleFloating}
          className="p-1.5 rounded-md text-slate-300 hover:text-cyan-300 hover:bg-slate-800/60 transition-colors"
          title={isFloating ? 'Restore Fullscreen' : 'Floating Orb Overlay'}
        >
          {isFloating ? <Maximize2 className="w-4 h-4" /> : <Minimize2 className="w-4 h-4" />}
        </button>

        {/* Settings Button */}
        <button
          onClick={onOpenSettings}
          className="p-1.5 rounded-md text-slate-300 hover:text-cyan-300 hover:bg-slate-800/60 transition-colors"
          title="Settings & Diagnostics"
        >
          <SettingsIcon className="w-4 h-4" />
        </button>
      </div>
    </header>
  );
};
