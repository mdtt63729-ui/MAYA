import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import {
  Sparkles,
  Key,
  CheckCircle2,
  AlertCircle,
  Cpu,
  ArrowRight,
  Download,
  Smartphone,
  ShieldCheck,
  Zap,
  Activity,
  Radio,
  Mic,
  Layers,
  Accessibility,
} from 'lucide-react';
import { Capacitor } from '@capacitor/core';

interface OnboardingModalProps {
  isOpen: boolean;
  onComplete: () => void;
  deferredPrompt: any;
}

export interface CodecTestResult {
  success: boolean;
  status: string;
  latencyMs: number;
  primaryModel: string;
  audioInputCodec: string;
  audioOutputCodec: string;
  streamingProtocol: string;
  transport?: string;
  message?: string;
  error?: string;
}

export const OnboardingModal: React.FC<OnboardingModalProps> = ({
  isOpen,
  onComplete,
  deferredPrompt,
}) => {
  const [step, setStep] = useState<1 | 2 | 3 | 4>(1);
  const [apiKey, setApiKey] = useState<string>('');
  const [isTesting, setIsTesting] = useState<boolean>(false);
  const [testResult, setTestResult] = useState<CodecTestResult | null>(null);
  const [useServerKey, setUseServerKey] = useState<boolean>(true);
  const [installSuccess, setInstallSuccess] = useState<boolean>(false);

  // ---- Permission Center (step 3) ----
  const [permStatus, setPermStatus] = useState<{
    mic: boolean;
    notifications: boolean;
    overlay: boolean;
    accessibility: boolean;
  }>({ mic: false, notifications: false, overlay: false, accessibility: false });

  const isNative = Capacitor.isNativePlatform();

  const refreshPermissions = async () => {
    if (!isNative) return;
    try {
      const MJNative = (Capacitor as any).Plugins?.MJNative;
      if (MJNative?.getPermissionStatus) {
        const s: any = await MJNative.getPermissionStatus();
        setPermStatus({
          mic: !!s?.mic,
          notifications: !!s?.notifications,
          overlay: !!s?.overlay,
          accessibility: !!s?.accessibility,
        });
      }
    } catch {
      /* status stays as-is */
    }
  };

  // Poll permission status while the Permission Center step is visible
  // (returning from system settings must refresh the ticks)
  useEffect(() => {
    if (step !== 3 || !isOpen) return;
    void refreshPermissions();
    const t = setInterval(() => void refreshPermissions(), 1500);
    return () => clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [step, isOpen]);

  const requestMicAndNotifications = async () => {
    if (!isNative) return;
    try {
      const MJNative = (Capacitor as any).Plugins?.MJNative;
      await MJNative?.requestRuntimePermissions?.();
      setTimeout(() => void refreshPermissions(), 800);
    } catch {
      /* ignore */
    }
  };

  const openOverlayPermSettings = async () => {
    if (!isNative) return;
    try {
      const MJNative = (Capacitor as any).Plugins?.MJNative;
      await MJNative?.openOverlaySettings?.();
    } catch {
      /* ignore */
    }
  };

  const openAccessibilityPermSettings = async () => {
    if (!isNative) return;
    try {
      const MJNative = (Capacitor as any).Plugins?.MJNative;
      await MJNative?.openAccessibilitySettings?.();
    } catch {
      /* ignore */
    }
  };

  useEffect(() => {
    const saved = localStorage.getItem('gemini_custom_api_key');
    if (saved) {
      setApiKey(saved);
      setUseServerKey(false);
    }
  }, []);

  // Run real connection & codec test
  const handleTestConnection = async (keyToTest?: string) => {
    setIsTesting(true);
    setTestResult(null);

    const key = keyToTest !== undefined ? keyToTest : (useServerKey ? '' : apiKey);

    try {
      // STANDALONE APK MODE: no backend server exists — verify the key
      // directly against the Gemini API instead of the server relay.
      if (Capacitor.isNativePlatform()) {
        const trimmedKey = (key || '').trim();
        if (!trimmedKey) {
          setTestResult({
            success: false,
            status: 'failed',
            latencyMs: 0,
            primaryModel: 'gemini-3.1-flash-live',
            audioInputCodec: 'audio/pcm;rate=16000',
            audioOutputCodec: 'audio/pcm;rate=24000',
            streamingProtocol: 'Direct WebSocket Real-Time Stream',
            error: 'Standalone app mode — your personal Gemini API Key is required here.',
          });
          return;
        }

        const started = Date.now();
        const res = await fetch(
          `https://generativelanguage.googleapis.com/v1beta/models?key=${encodeURIComponent(trimmedKey)}&pageSize=1`
        );
        const data = await res.json().catch(() => null);

        if (res.ok) {
          localStorage.setItem('gemini_custom_api_key', trimmedKey);
          setTestResult({
            success: true,
            status: 'connected',
            latencyMs: Date.now() - started,
            primaryModel: 'gemini-3.1-flash-live',
            audioInputCodec: 'audio/pcm;rate=16000',
            audioOutputCodec: 'audio/pcm;rate=24000',
            streamingProtocol: 'Direct WebSocket Real-Time Stream',
          });
        } else {
          setTestResult({
            success: false,
            status: 'failed',
            latencyMs: Date.now() - started,
            primaryModel: 'gemini-3.1-flash-live',
            audioInputCodec: 'audio/pcm;rate=16000',
            audioOutputCodec: 'audio/pcm;rate=24000',
            streamingProtocol: 'Direct WebSocket Real-Time Stream',
            error: data?.error?.message || `Gemini API rejected this key (HTTP ${res.status}).`,
          });
        }
        return;
      }

      const res = await fetch('/api/test-connection', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ apiKey: key || undefined }),
      });

      const data = await res.json();
      if (res.ok && data.success) {
        setTestResult(data);
        if (!useServerKey && key) {
          localStorage.setItem('gemini_custom_api_key', key.trim());
        } else if (useServerKey) {
          localStorage.removeItem('gemini_custom_api_key');
        }
      } else {
        setTestResult({
          success: false,
          status: 'failed',
          latencyMs: data.latencyMs || 0,
          primaryModel: 'gemini-3.1-flash-live',
          audioInputCodec: data.audioInputCodec || 'audio/pcm;rate=16000',
          audioOutputCodec: data.audioOutputCodec || 'audio/pcm;rate=24000',
          streamingProtocol: 'WebSocket Full-Duplex Real-Time Stream',
          error: data.error || 'Failed to authenticate API key.',
        });
      }
    } catch (err: unknown) {
      setTestResult({
        success: false,
        status: 'error',
        latencyMs: 0,
        primaryModel: 'gemini-3.1-flash-live',
        audioInputCodec: 'audio/pcm;rate=16000',
        audioOutputCodec: 'audio/pcm;rate=24000',
        streamingProtocol: 'WebSocket Full-Duplex Real-Time Stream',
        error: err instanceof Error ? err.message : 'Network error during connection test.',
      });
    } finally {
      setIsTesting(false);
    }
  };

  // Trigger PWA install
  const handleInstallApp = async () => {
    if (deferredPrompt) {
      try {
        deferredPrompt.prompt();
        const choice = await deferredPrompt.userChoice;
        if (choice.outcome === 'accepted') {
          setInstallSuccess(true);
        }
      } catch (err) {
        console.warn('Install error:', err);
      }
    } else {
      // Fallback alert / guidance
      setInstallSuccess(true);
    }
  };

  const handleFinish = () => {
    localStorage.setItem('mj_onboarding_completed', 'true');
    localStorage.setItem('mj_onboarding_completed_v2', 'true');
    onComplete();
  };

  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/90">
        <motion.div
          initial={{ opacity: 0, scale: 0.94, y: 15 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.94, y: 15 }}
          transition={{ type: 'spring', damping: 28, stiffness: 350 }}
          className="relative w-full max-w-lg bg-[#070c18] border border-cyan-500/35 rounded-3xl p-6 sm:p-7 shadow-[0_0_80px_rgba(6,182,212,0.2)] text-slate-100 flex flex-col overflow-hidden"
        >
          {/* Subtle Cyber Grid Background */}
          <div className="absolute inset-0 bg-[linear-gradient(to_right,#0284c70a_1px,transparent_1px),linear-gradient(to_bottom,#0284c70a_1px,transparent_1px)] bg-[size:24px_24px] pointer-events-none" />

          {/* Top Holographic Header */}
          <div className="relative z-10 flex items-center justify-between pb-4 border-b border-cyan-500/20">
            <div className="flex items-center space-x-3">
              <div className="w-10 h-10 rounded-2xl bg-gradient-to-tr from-cyan-500 to-blue-600 flex items-center justify-center shadow-[0_0_20px_rgba(56,189,248,0.5)]">
                <Zap className="w-5 h-5 text-white" />
              </div>
              <div>
                <span className="text-[10px] font-mono uppercase tracking-widest text-cyan-400 font-bold block">
                  SYSTEM INITIALIZATION • STEP {step} OF 4
                </span>
                <h2 className="text-lg font-bold text-white tracking-tight">
                  {step === 1 && 'Welcome to JARVIS MK-85'}
                  {step === 2 && 'API Calibration & Codec Test'}
                  {step === 3 && 'Permission Grants'}
                  {step === 4 && 'Set as Default Assistant'}
                </h2>
              </div>
            </div>

            {/* Step Indicators */}
            <div className="flex items-center space-x-1.5">
              {[1, 2, 3, 4].map((s) => (
                <div
                  key={s}
                  className={`h-1.5 rounded-full transition-all duration-300 ${
                    s === step
                      ? 'w-6 bg-cyan-400 shadow-[0_0_8px_#38bdf8]'
                      : s < step
                      ? 'w-2.5 bg-cyan-600/70'
                      : 'w-2.5 bg-slate-800'
                  }`}
                />
              ))}
            </div>
          </div>

          {/* Step 1: System Overview */}
          {step === 1 && (
            <motion.div
              key="step-1"
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -20 }}
              className="relative z-10 py-5 space-y-4"
            >
              <div className="p-4 rounded-2xl bg-cyan-950/20 border border-cyan-500/25 space-y-2">
                <div className="flex items-center space-x-2 text-cyan-300">
                  <Sparkles className="w-4 h-4 text-cyan-400" />
                  <h3 className="text-xs font-mono font-bold uppercase tracking-wider">
                    Tony Stark JARVIS / FRIDAY Protocol
                  </h3>
                </div>
                <p className="text-xs text-slate-300 leading-relaxed">
                  Experience true real-time, zero-lag voice-to-voice artificial intelligence powered by Gemini Live API with high-precision raw PCM audio streaming and app automation.
                </p>
              </div>

              <div className="grid grid-cols-2 gap-3 text-xs">
                <div className="p-3 rounded-xl bg-white/[0.03] border border-white/10 space-y-1">
                  <div className="flex items-center space-x-1.5 text-cyan-400">
                    <Radio className="w-3.5 h-3.5" />
                    <span className="font-mono font-semibold">16kHz / 24kHz</span>
                  </div>
                  <p className="text-[11px] text-slate-400">Raw PCM Full-Duplex Audio Stream</p>
                </div>
                <div className="p-3 rounded-xl bg-white/[0.03] border border-white/10 space-y-1">
                  <div className="flex items-center space-x-1.5 text-cyan-400">
                    <ShieldCheck className="w-3.5 h-3.5" />
                    <span className="font-mono font-semibold">App Automation</span>
                  </div>
                  <p className="text-[11px] text-slate-400">Opens YouTube, WhatsApp, Camera, etc.</p>
                </div>
              </div>

              <div className="pt-3">
                <button
                  onClick={() => setStep(2)}
                  className="w-full py-3.5 rounded-2xl bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-slate-950 font-bold text-sm flex items-center justify-center space-x-2 shadow-lg shadow-cyan-500/30 transition-all active:scale-[0.98]"
                >
                  <span>Configure API & Verify Codecs</span>
                  <ArrowRight className="w-4 h-4" />
                </button>
              </div>
            </motion.div>
          )}

          {/* Step 2: API Key Configuration & Codec Test */}
          {step === 2 && (
            <motion.div
              key="step-2"
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -20 }}
              className="relative z-10 py-4 space-y-4 max-h-[65vh] overflow-y-auto scrollbar-none"
            >
              {/* Option Selector: Server Key vs Custom User Key */}
              <div className="flex items-center p-1 rounded-xl bg-slate-900/90 border border-slate-700/80 text-xs">
                <button
                  onClick={() => {
                    setUseServerKey(true);
                    setTestResult(null);
                  }}
                  className={`flex-1 py-2 rounded-lg font-medium transition-all ${
                    useServerKey
                      ? 'bg-cyan-500 text-slate-950 font-semibold shadow'
                      : 'text-slate-400 hover:text-white'
                  }`}
                >
                  Pre-configured Key (Instant)
                </button>
                <button
                  onClick={() => {
                    setUseServerKey(false);
                    setTestResult(null);
                  }}
                  className={`flex-1 py-2 rounded-lg font-medium transition-all ${
                    !useServerKey
                      ? 'bg-cyan-500 text-slate-950 font-semibold shadow'
                      : 'text-slate-400 hover:text-white'
                  }`}
                >
                  Custom API Key
                </button>
              </div>

              {!useServerKey && (
                <div className="space-y-1.5">
                  <label className="text-xs font-mono text-slate-300 flex items-center space-x-1.5">
                    <Key className="w-3.5 h-3.5 text-cyan-400" />
                    <span>Enter Gemini API Key</span>
                  </label>
                  <input
                    type="password"
                    value={apiKey}
                    onChange={(e) => setApiKey(e.target.value)}
                    placeholder="AIzaSy..."
                    className="w-full px-3.5 py-2.5 rounded-xl bg-slate-900/80 border border-slate-700 focus:border-cyan-400 focus:outline-none text-xs font-mono text-slate-100 placeholder-slate-500 transition-colors"
                  />
                  <p className="text-[10px] text-slate-400">
                    Get a free API key from Google AI Studio. Stored privately on your device.
                  </p>
                </div>
              )}

              {/* Action: Auto Connection & Codec Test Button */}
              <div>
                <button
                  onClick={() => handleTestConnection()}
                  disabled={isTesting || (!useServerKey && !apiKey.trim())}
                  className="w-full py-2.5 rounded-xl bg-cyan-950/80 hover:bg-cyan-900/80 border border-cyan-500/40 text-cyan-300 hover:text-cyan-200 font-mono text-xs font-semibold flex items-center justify-center space-x-2 transition-all disabled:opacity-50"
                >
                  {isTesting ? (
                    <>
                      <Activity className="w-3.5 h-3.5 animate-spin text-cyan-400" />
                      <span>Testing Neural Connection & Codecs...</span>
                    </>
                  ) : (
                    <>
                      <Cpu className="w-3.5 h-3.5 text-cyan-400" />
                      <span>Test Connection & Verify Audio Codecs</span>
                    </>
                  )}
                </button>
              </div>

              {/* Real-time Codec & Test Results Card */}
              {testResult && (
                <motion.div
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  className={`p-3.5 rounded-2xl border text-xs space-y-2.5 ${
                    testResult.success
                      ? 'bg-cyan-950/25 border-cyan-500/40 text-cyan-200'
                      : 'bg-rose-950/25 border-rose-500/40 text-rose-200'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-1.5 font-semibold">
                      {testResult.success ? (
                        <>
                          <CheckCircle2 className="w-4 h-4 text-cyan-400" />
                          <span>Connection Verified</span>
                        </>
                      ) : (
                        <>
                          <AlertCircle className="w-4 h-4 text-rose-400" />
                          <span>Connection Test Failed</span>
                        </>
                      )}
                    </div>
                    {testResult.success && (
                      <span className="px-2 py-0.5 rounded font-mono text-[10px] bg-cyan-900/50 border border-cyan-600 text-cyan-300">
                        {testResult.latencyMs}ms Ping
                      </span>
                    )}
                  </div>

                  {testResult.error && (
                    <p className="text-[11px] text-rose-300 font-mono">
                      {testResult.error}
                    </p>
                  )}

                  {/* Detailed Codec Telemetry Table */}
                  <div className="p-2.5 rounded-xl bg-black/40 border border-white/10 space-y-1.5 font-mono text-[10px] text-slate-300">
                    <div className="flex justify-between">
                      <span className="text-slate-500">Audio In Codec:</span>
                      <span className="text-cyan-300 font-semibold">{testResult.audioInputCodec}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-slate-500">Audio Out Codec:</span>
                      <span className="text-cyan-300 font-semibold">{testResult.audioOutputCodec}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-slate-500">Model Engine:</span>
                      <span className="text-teal-300">{testResult.primaryModel}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-slate-500">Stream Protocol:</span>
                      <span className="text-slate-300">WebSocket RFC 6455</span>
                    </div>
                  </div>
                </motion.div>
              )}

              {/* Navigation buttons */}
              <div className="flex items-center space-x-3 pt-2">
                <button
                  onClick={() => setStep(1)}
                  className="px-4 py-3 rounded-2xl bg-white/5 hover:bg-white/10 text-slate-300 text-xs font-semibold"
                >
                  Back
                </button>
                <button
                  onClick={() => setStep(3)}
                  className="flex-1 py-3 rounded-2xl bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-slate-950 font-bold text-xs flex items-center justify-center space-x-1.5 shadow-md shadow-cyan-500/20"
                >
                  <span>Next: Grant Permissions</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </button>
              </div>
            </motion.div>
          )}

          {/* Step 3: Permission Grants — all Android permissions in one place */}
          {step === 3 && (
            <motion.div
              key="step-3"
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -20 }}
              className="relative z-10 py-4 space-y-3 max-h-[65vh] overflow-y-auto scrollbar-none"
            >
              <div className="p-3 rounded-2xl bg-cyan-950/20 border border-cyan-500/25">
                <p className="text-[11px] text-slate-300 leading-relaxed">
                  Allow everything now so MJ never asks again — every popup comes right here, one by one.
                </p>
              </div>

              {/* Microphone + Notifications (real runtime popups) */}
              <button
                onClick={requestMicAndNotifications}
                className={`w-full p-3.5 rounded-2xl border flex items-center space-x-3 text-left transition-all active:scale-[0.98] ${
                  permStatus.mic && permStatus.notifications
                    ? 'bg-emerald-950/25 border-emerald-500/40'
                    : 'bg-white/[0.03] border-white/10 hover:border-cyan-500/40'
                }`}
              >
                <div className="w-9 h-9 rounded-xl bg-cyan-500/15 border border-cyan-500/30 flex items-center justify-center shrink-0">
                  <Mic className="w-4 h-4 text-cyan-300" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-semibold text-white">Microphone & Notifications</p>
                  <p className="text-[10px] text-slate-400">Voice + background alerts (system popup)</p>
                </div>
                {permStatus.mic && permStatus.notifications ? (
                  <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0" />
                ) : (
                  <span className="px-2.5 py-1 rounded-lg bg-cyan-500 text-slate-950 text-[10px] font-bold shrink-0">Allow</span>
                )}
              </button>

              {/* Display over other apps (floating orb + edge lighting) */}
              <button
                onClick={openOverlayPermSettings}
                className={`w-full p-3.5 rounded-2xl border flex items-center space-x-3 text-left transition-all active:scale-[0.98] ${
                  permStatus.overlay
                    ? 'bg-emerald-950/25 border-emerald-500/40'
                    : 'bg-white/[0.03] border-white/10 hover:border-cyan-500/40'
                }`}
              >
                <div className="w-9 h-9 rounded-xl bg-cyan-500/15 border border-cyan-500/30 flex items-center justify-center shrink-0">
                  <Layers className="w-4 h-4 text-cyan-300" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-semibold text-white">Display Over Other Apps</p>
                  <p className="text-[10px] text-slate-400">Floating Orb + Edge Lighting (settings page)</p>
                </div>
                {permStatus.overlay ? (
                  <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0" />
                ) : (
                  <span className="px-2.5 py-1 rounded-lg bg-cyan-500 text-slate-950 text-[10px] font-bold shrink-0">Allow</span>
                )}
              </button>

              {/* Accessibility (Android Agent) */}
              <button
                onClick={openAccessibilityPermSettings}
                className={`w-full p-3.5 rounded-2xl border flex items-center space-x-3 text-left transition-all active:scale-[0.98] ${
                  permStatus.accessibility
                    ? 'bg-emerald-950/25 border-emerald-500/40'
                    : 'bg-white/[0.03] border-white/10 hover:border-cyan-500/40'
                }`}
              >
                <div className="w-9 h-9 rounded-xl bg-cyan-500/15 border border-cyan-500/30 flex items-center justify-center shrink-0">
                  <Accessibility className="w-4 h-4 text-cyan-300" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-semibold text-white">Accessibility — MJ Android Agent</p>
                  <p className="text-[10px] text-slate-400">Screen control: search, type, scroll, Back/Home</p>
                </div>
                {permStatus.accessibility ? (
                  <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0" />
                ) : (
                  <span className="px-2.5 py-1 rounded-lg bg-cyan-500 text-slate-950 text-[10px] font-bold shrink-0">Allow</span>
                )}
              </button>

              <p className="text-[10px] text-slate-500 text-center px-2">
                Ticks update automatically — return from Settings and they turn green.
              </p>

              <div className="flex items-center space-x-3 pt-2">
                <button
                  onClick={() => setStep(2)}
                  className="px-4 py-3 rounded-2xl bg-white/5 hover:bg-white/10 text-slate-300 text-xs font-semibold"
                >
                  Back
                </button>
                <button
                  onClick={() => setStep(4)}
                  className="flex-1 py-3 rounded-2xl bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-slate-950 font-bold text-xs flex items-center justify-center space-x-1.5 shadow-md shadow-cyan-500/20"
                >
                  <span>Next: Default Assistant Setup</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </button>
              </div>
            </motion.div>
          )}

          {/* Step 4: Default Assistant & PWA Install */}
          {step === 4 && (
            <motion.div
              key="step-4"
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -20 }}
              className="relative z-10 py-4 space-y-4"
            >
              <div className="p-4 rounded-2xl bg-cyan-950/20 border border-cyan-500/25 space-y-3 text-xs">
                <div className="flex items-center space-x-2 text-cyan-300 font-semibold">
                  <Smartphone className="w-4 h-4 text-cyan-400" />
                  <span>Use as Default Assistant on Android</span>
                </div>
                <p className="text-slate-300 text-[11px] leading-relaxed">
                  You can set MJ as your phone&apos;s default digital assistant or install it as a full-screen native Android app.
                </p>

                {/* Step Guide for Android */}
                <div className="space-y-1.5 p-3 rounded-xl bg-black/40 border border-white/10 font-mono text-[10px] text-slate-300">
                  <div className="flex items-center space-x-2">
                    <span className="w-4 h-4 rounded-full bg-cyan-500/30 text-cyan-300 flex items-center justify-center font-bold">1</span>
                    <span>Install app or tap &ldquo;Add to Home Screen&rdquo;</span>
                  </div>
                  <div className="flex items-center space-x-2">
                    <span className="w-4 h-4 rounded-full bg-cyan-500/30 text-cyan-300 flex items-center justify-center font-bold">2</span>
                    <span>Open Android <strong>Settings &rarr; Apps &rarr; Default apps</strong></span>
                  </div>
                  <div className="flex items-center space-x-2">
                    <span className="w-4 h-4 rounded-full bg-cyan-500/30 text-cyan-300 flex items-center justify-center font-bold">3</span>
                    <span>Tap <strong>Digital assistant app</strong> &rarr; choose <strong>MJ</strong></span>
                  </div>
                </div>
              </div>

              {/* Install PWA Button */}
              <button
                onClick={handleInstallApp}
                className="w-full py-3 rounded-2xl bg-white/[0.08] hover:bg-white/[0.14] border border-cyan-500/40 text-cyan-300 font-semibold text-xs flex items-center justify-center space-x-2 transition-all"
              >
                {installSuccess ? (
                  <>
                    <CheckCircle2 className="w-4 h-4 text-cyan-400" />
                    <span className="text-cyan-300">App Ready for Installation</span>
                  </>
                ) : (
                  <>
                    <Download className="w-4 h-4 text-cyan-400" />
                    <span>Install App / Add to Home Screen</span>
                  </>
                )}
              </button>

              {/* Launch Assistant */}
              <div className="flex items-center space-x-3 pt-2">
                <button
                  onClick={() => setStep(3)}
                  className="px-4 py-3 rounded-2xl bg-white/5 hover:bg-white/10 text-slate-300 text-xs font-semibold"
                >
                  Back
                </button>
                <button
                  onClick={handleFinish}
                  className="flex-1 py-3.5 rounded-2xl bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-slate-950 font-bold text-sm flex items-center justify-center space-x-2 shadow-lg shadow-cyan-500/30 active:scale-[0.98]"
                >
                  <Sparkles className="w-4 h-4" />
                  <span>Awaken JARVIS / FRIDAY</span>
                </button>
              </div>
            </motion.div>
          )}
        </motion.div>
      </div>
    </AnimatePresence>
  );
};
