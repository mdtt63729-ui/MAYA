/**
 * Settings, Permissions & Diagnostics Modal
 * PRD Sections 93, 111, 113: Android permission center, model configurations,
 * and real-time engine diagnostics.
 */

import React, { useState } from 'react';
import {
  X,
  Settings as SettingsIcon,
  Mic,
  Camera,
  Layers,
  Bell,
  Cpu,
  Activity,
  CheckCircle,
  AlertCircle,
  Sparkles,
} from 'lucide-react';
import { GeminiModelId } from '../../types';

interface SettingsModalProps {
  isOpen: boolean;
  selectedModel: GeminiModelId;
  onSelectModel: (m: GeminiModelId) => void;
  onClose: () => void;
}

export const SettingsModal: React.FC<SettingsModalProps> = ({
  isOpen,
  selectedModel,
  onSelectModel,
  onClose,
}) => {
  const [activeSubTab, setActiveSubTab] = useState<'model' | 'permissions' | 'diagnostics'>('model');

  // Simulated Android permission states
  const [permissions, setPermissions] = useState({
    microphone: true,
    screenCapture: true,
    notifications: true,
    overlay: true,
    accessibility: true,
  });

  if (!isOpen) return null;

  const togglePermission = (key: keyof typeof permissions) => {
    setPermissions((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md select-none animate-fade-in">
      <div className="w-full max-w-md rounded-3xl bg-[#0e131d] border border-slate-700/80 p-5 shadow-2xl space-y-4 max-h-[85vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-800 pb-3">
          <div className="flex items-center space-x-2.5">
            <div className="w-9 h-9 rounded-xl bg-cyan-950/70 border border-cyan-700/40 flex items-center justify-center text-cyan-400">
              <SettingsIcon className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-sm font-semibold text-slate-100">
                Assistant Settings
              </h3>
              <p className="text-[11px] text-slate-400">
                Models, permissions, and system diagnostics
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

        {/* Sub-tab switcher */}
        <div className="flex items-center space-x-1 p-1 rounded-xl bg-slate-950 border border-slate-800 text-xs">
          {[
            { id: 'model', label: 'AI Model' },
            { id: 'permissions', label: 'Permissions' },
            { id: 'diagnostics', label: 'Diagnostics' },
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveSubTab(tab.id as 'model' | 'permissions' | 'diagnostics')}
              className={`flex-1 py-1.5 rounded-lg font-medium transition-all ${
                activeSubTab === tab.id
                  ? 'bg-cyan-500/20 text-cyan-300 shadow-xs'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* Tab Content */}
        <div className="flex-1 overflow-y-auto space-y-3 pr-1 text-xs">
          {/* MODEL TAB */}
          {activeSubTab === 'model' && (
            <div className="space-y-2.5">
              <span className="text-[11px] font-semibold text-slate-400">
                Gemini Reasoning Model Family:
              </span>

              {[
                {
                  id: 'gemini-3.1-flash-lite',
                  name: 'Gemini 3.1 Flash-Lite',
                  badge: 'Default / Fast',
                  desc: 'Lowest latency on-device feel. Optimized for real-time turn-taking and quick tools.',
                },
                {
                  id: 'gemini-3.5-flash',
                  name: 'Gemini 3.5 Flash',
                  badge: 'Search & Maps Grounding',
                  desc: 'Balanced intelligence with live Google Search and Google Maps grounding capabilities.',
                },
                {
                  id: 'gemini-3.1-pro-preview',
                  name: 'Gemini 3.1 Pro (Thinking)',
                  badge: 'Complex Reasoning',
                  desc: 'High-thinking mode (thinkingLevel: HIGH) for deep STEM, coding, and multi-step agent planning.',
                },
              ].map((m) => {
                const isSelected = selectedModel === m.id;
                return (
                  <div
                    key={m.id}
                    onClick={() => onSelectModel(m.id as GeminiModelId)}
                    className={`p-3 rounded-2xl border cursor-pointer transition-all ${
                      isSelected
                        ? 'bg-cyan-950/40 border-cyan-500/60 shadow-md shadow-cyan-950/30'
                        : 'bg-slate-900/60 border-slate-800 text-slate-300 hover:bg-slate-900'
                    }`}
                  >
                    <div className="flex items-center justify-between mb-1">
                      <span className="font-semibold text-slate-100">{m.name}</span>
                      <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-cyan-950/70 border border-cyan-800 text-cyan-300">
                        {m.badge}
                      </span>
                    </div>
                    <p className="text-[11px] text-slate-400 leading-relaxed">
                      {m.desc}
                    </p>
                  </div>
                );
              })}
            </div>
          )}

          {/* PERMISSIONS TAB */}
          {activeSubTab === 'permissions' && (
            <div className="space-y-2">
              <span className="text-[11px] font-semibold text-slate-400">
                Native Android Permissions:
              </span>

              {[
                { key: 'microphone' as const, label: 'Microphone & Audio', desc: 'Required for real-time voice & wake word', icon: Mic },
                { key: 'screenCapture' as const, label: 'Screen Vision (MediaProjection)', desc: 'Required for screen understanding & click assistance', icon: Camera },
                { key: 'overlay' as const, label: 'Floating Orb Overlay', desc: 'Allows MJ Orb to float above third-party apps', icon: Layers },
                { key: 'notifications' as const, label: 'Notification Integration', desc: 'Allows proactive reminders & event alerts', icon: Bell },
                { key: 'accessibility' as const, label: 'Accessibility Automation', desc: 'Permitted Android UI click & scroll gestures', icon: Cpu },
              ].map((perm) => {
                const isGranted = permissions[perm.key];
                const Icon = perm.icon;
                return (
                  <div
                    key={perm.key}
                    className="p-3 rounded-2xl bg-slate-900/60 border border-slate-800 flex items-center justify-between"
                  >
                    <div className="flex items-center space-x-2.5">
                      <div className="w-8 h-8 rounded-xl bg-slate-950 border border-slate-800 flex items-center justify-center text-slate-300">
                        <Icon className="w-4 h-4" />
                      </div>
                      <div>
                        <span className="font-semibold text-slate-200 block text-xs">
                          {perm.label}
                        </span>
                        <span className="text-[10px] text-slate-400 block">
                          {perm.desc}
                        </span>
                      </div>
                    </div>

                    <button
                      onClick={() => togglePermission(perm.key)}
                      className={`text-[10px] font-semibold px-2.5 py-1 rounded-lg border transition-all ${
                        isGranted
                          ? 'bg-emerald-950/60 border-emerald-700/60 text-emerald-300'
                          : 'bg-rose-950/60 border-rose-700/60 text-rose-300'
                      }`}
                    >
                      {isGranted ? 'GRANTED' : 'DENIED'}
                    </button>
                  </div>
                );
              })}
            </div>
          )}

          {/* DIAGNOSTICS TAB */}
          {activeSubTab === 'diagnostics' && (
            <div className="space-y-2 font-mono text-[11px]">
              <div className="p-3 rounded-2xl bg-slate-950 border border-slate-800 space-y-1.5 text-slate-300">
                <div className="flex items-center justify-between">
                  <span className="text-slate-500">App Core:</span>
                  <span className="text-cyan-300 font-semibold">MJ AI Assistant v5.1</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-slate-500">Capacitor Bridge:</span>
                  <span className="text-slate-200">v8.5.2 (Android Native)</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-slate-500">Audio Engine:</span>
                  <span className="text-emerald-400 flex items-center">
                    <Activity className="w-3 h-3 mr-1" /> Web Audio (60 FPS)
                  </span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-slate-500">Active Model:</span>
                  <span className="text-cyan-400">{selectedModel}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-slate-500">API Status:</span>
                  <span className="text-emerald-400 flex items-center">
                    <CheckCircle className="w-3 h-3 mr-1" /> Connected
                  </span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-slate-500">Offline Engine:</span>
                  <span className="text-slate-200">Local Command Router Active</span>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
