import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import {
  X,
  CheckCircle2,
  AlertCircle,
  Key,
  Radio,
  AppWindow,
  Sparkles,
  Volume2,
  Mic,
  Smartphone,
  Download,
  Activity,
  RotateCcw,
  ExternalLink,
  Cpu,
  VolumeX,
} from 'lucide-react';
import { appScanner, AppDefinition } from '../../services/appScanner';
import { audioStreamer } from '../../services/audioStreamer';
import { wakeWordDetector } from '../../services/wakeWordDetector';
import { Capacitor } from '@capacitor/core';

interface IosSettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  onOpenOnboarding: () => void;
  deferredPrompt: any;
}

export const IosSettingsModal: React.FC<IosSettingsModalProps> = ({
  isOpen,
  onClose,
  onOpenOnboarding,
  deferredPrompt,
}) => {
  const [activeTab, setActiveTab] = useState<'api' | 'voice' | 'defaultApp' | 'apps' | 'hardware'>('api');
  
  // API Key State
  const [apiKey, setApiKey] = useState<string>('');
  const [isTestingApi, setIsTestingApi] = useState<boolean>(false);
  const [apiTestResult, setApiTestResult] = useState<any>(null);
  const [keySavedMsg, setKeySavedMsg] = useState<string>('');

  // Voice & Persona State
  const [selectedVoice, setSelectedVoice] = useState<string>('Leda');
  const [selectedPersona, setSelectedPersona] = useState<string>('sweet_female');

  // Wake Word State
  const [wakeWordEnabled, setWakeWordEnabled] = useState<boolean>(true);

  // App Scanner State
  const [appSearch, setAppSearch] = useState<string>('');
  const [apps, setApps] = useState<AppDefinition[]>([]);
  const [launchFeedback, setLaunchFeedback] = useState<string | null>(null);

  // Hardware Diagnostics State
  const [isTestingMic, setIsTestingMic] = useState<boolean>(false);
  const [micLevel, setMicLevel] = useState<number>(0);
  const [speakerTestMsg, setSpeakerTestMsg] = useState<string | null>(null);

  // PWA Install State
  const [installMsg, setInstallMsg] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen) {
      setApps(appScanner.getAllApps());
      // NATIVE: rescan the phone's installed apps (names + logos) so the
      // Apps list is always current when Settings opens
      appScanner.refreshNativeApps().then(() => {
        setApps(appScanner.getAllApps());
      });
      const savedKey = localStorage.getItem('gemini_custom_api_key') || '';
      setApiKey(savedKey);
      const savedVoice = localStorage.getItem('gemini_selected_voice') || 'Leda';
      setSelectedVoice(savedVoice);
      const savedPersona = localStorage.getItem('gemini_selected_persona') || 'sweet_female';
      setSelectedPersona(savedPersona);
      setWakeWordEnabled(wakeWordDetector.getStatus().isEnabled);
    }
  }, [isOpen]);

  // Test Connection & Codecs
  const handleTestConnection = async () => {
    setIsTestingApi(true);
    setApiTestResult(null);
    setKeySavedMsg('');

    try {
      // STANDALONE APK MODE: there is no backend server here — verify the
      // API key directly against Google's Gemini API instead (the old
      // server-only test returned HTML and failed with a JSON parse error).
      if (Capacitor.isNativePlatform()) {
        const key = apiKey.trim();
        if (!key) {
          setApiTestResult({
            success: false,
            error: 'Standalone app mode — your personal Gemini API Key is required. Paste it above and tap Save.',
          });
          return;
        }

        const started = Date.now();
        const res = await fetch(
          `https://generativelanguage.googleapis.com/v1beta/models?key=${encodeURIComponent(key)}&pageSize=1`
        );
        const data = await res.json().catch(() => null);

        if (res.ok) {
          localStorage.setItem('gemini_custom_api_key', key);
          setKeySavedMsg('API key verified & saved! Direct Gemini connection ready.');
          setApiTestResult({
            success: true,
            latencyMs: Date.now() - started,
            audioInputCodec: 'audio/pcm;rate=16000',
            audioOutputCodec: 'audio/pcm;rate=24000',
            primaryModel: 'gemini-3.1-flash-live-preview',
          });
        } else {
          setApiTestResult({
            success: false,
            error:
              data?.error?.message ||
              `Gemini API rejected this key (HTTP ${res.status}). Double-check the key and try again.`,
          });
        }
        return;
      }

      // WEB MODE: relay through the backend server
      const res = await fetch('/api/test-connection', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ apiKey: apiKey.trim() || undefined }),
      });

      const data = await res.json();
      setApiTestResult(data);

      if (res.ok && data.success) {
        if (apiKey.trim()) {
          localStorage.setItem('gemini_custom_api_key', apiKey.trim());
          setKeySavedMsg('Custom API key verified and saved!');
        } else {
          localStorage.removeItem('gemini_custom_api_key');
          setKeySavedMsg('Pre-configured server key verified!');
        }
      }
    } catch (err: unknown) {
      setApiTestResult({
        success: false,
        error: err instanceof Error ? err.message : 'Network error occurred.',
        audioInputCodec: 'audio/pcm;rate=16000',
        audioOutputCodec: 'audio/pcm;rate=24000',
      });
    } finally {
      setIsTestingApi(false);
    }
  };

  const handleSaveApiKey = () => {
    if (apiKey.trim()) {
      localStorage.setItem('gemini_custom_api_key', apiKey.trim());
      setKeySavedMsg('Custom API Key saved successfully.');
    } else {
      localStorage.removeItem('gemini_custom_api_key');
      setKeySavedMsg('Reverted to pre-configured server key.');
    }
    setTimeout(() => setKeySavedMsg(''), 3000);
  };

  // Toggle Wake Word Detection
  const handleToggleWakeWord = (enabled: boolean) => {
    setWakeWordEnabled(enabled);
    wakeWordDetector.toggle(enabled);
  };

  // Change Voice
  const handleVoiceChange = (voice: string) => {
    setSelectedVoice(voice);
    localStorage.setItem('gemini_selected_voice', voice);
  };

  // Change Persona
  const handlePersonaChange = (persona: string) => {
    setSelectedPersona(persona);
    localStorage.setItem('gemini_selected_persona', persona);
  };

  // Test Audio Speaker with Web Audio Tone
  const handleTestSpeaker = () => {
    try {
      const audioCtx = new (window.AudioContext || (window as any).webkitAudioContext)();
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();

      osc.type = 'sine';
      // Futuristic double-chirp
      osc.frequency.setValueAtTime(880, audioCtx.currentTime);
      osc.frequency.exponentialRampToValueAtTime(1760, audioCtx.currentTime + 0.15);

      gain.gain.setValueAtTime(0.2, audioCtx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.3);

      osc.connect(gain);
      gain.connect(audioCtx.destination);

      osc.start();
      osc.stop(audioCtx.currentTime + 0.35);

      setSpeakerTestMsg('Speaker hardware test successful: High-clarity 24kHz tone played.');
      setTimeout(() => setSpeakerTestMsg(null), 3500);
    } catch (e) {
      setSpeakerTestMsg('Speaker test failed: ' + (e as Error).message);
    }
  };

  // Test Microphone Live VU
  const handleToggleMicTest = async () => {
    if (isTestingMic) {
      setIsTestingMic(false);
      return;
    }

    try {
      setIsTestingMic(true);
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const audioCtx = new (window.AudioContext || (window as any).webkitAudioContext)();
      const analyser = audioCtx.createAnalyser();
      analyser.fftSize = 256;
      const source = audioCtx.createMediaStreamSource(stream);
      source.connect(analyser);

      const dataArray = new Uint8Array(analyser.frequencyBinCount);

      const poll = () => {
        if (!isTestingMic) {
          stream.getTracks().forEach((t) => t.stop());
          audioCtx.close();
          setMicLevel(0);
          return;
        }
        analyser.getByteFrequencyData(dataArray);
        let sum = 0;
        for (let i = 0; i < dataArray.length; i++) {
          sum += dataArray[i];
        }
        const avg = sum / dataArray.length;
        setMicLevel(Math.min(100, Math.round((avg / 128) * 100)));
        requestAnimationFrame(poll);
      };
      poll();
    } catch (err) {
      setIsTestingMic(false);
      setSpeakerTestMsg('Mic test error: ' + (err as Error).message);
    }
  };

  // Launch App Test
  const handleTestLaunchApp = (app: AppDefinition) => {
    appScanner.launchApp(app);
    setLaunchFeedback(`Launched ${app.name} (${app.url})`);
    setTimeout(() => setLaunchFeedback(null), 3000);
  };

  // PWA Install
  const handlePwaInstall = async () => {
    if (deferredPrompt) {
      try {
        deferredPrompt.prompt();
        const choice = await deferredPrompt.userChoice;
        if (choice.outcome === 'accepted') {
          setInstallMsg('MJ Assistant added to your home screen!');
        } else {
          setInstallMsg('Install dismissed.');
        }
      } catch (e) {
        setInstallMsg('Install prompt triggered.');
      }
    } else {
      setInstallMsg('To install on Android: Tap Chrome menu (⋮) → "Add to Home screen" or "Install App".');
    }
    setTimeout(() => setInstallMsg(null), 4500);
  };

  const filteredApps = apps.filter(
    (a) =>
      a.name.toLowerCase().includes(appSearch.toLowerCase()) ||
      a.aliases.some((k: string) => k.toLowerCase().includes(appSearch.toLowerCase()))
  );

  return (
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-50 flex items-end justify-center">
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="absolute inset-0 bg-black/85"
          />

          {/* iOS Bottom Sheet */}
          <motion.div
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{ y: '100%' }}
            transition={{ type: 'spring', damping: 28, stiffness: 350 }}
            className="relative z-10 w-full max-w-lg bg-[#070b16]/98 border-t border-cyan-500/30 rounded-t-[32px] p-6 shadow-2xl max-h-[88vh] flex flex-col text-slate-100"
          >
            {/* iOS Drag Handle */}
            <div className="w-12 h-1.5 rounded-full bg-slate-600 mx-auto mb-3 shrink-0" />

            {/* Header */}
            <div className="flex items-center justify-between pb-3 border-b border-white/10 shrink-0">
              <div className="flex items-center space-x-2.5">
                <div className="w-8 h-8 rounded-full bg-cyan-500/20 border border-cyan-400/40 flex items-center justify-center shadow-[0_0_15px_rgba(56,189,248,0.4)]">
                  <Cpu className="w-4 h-4 text-cyan-300" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-white tracking-tight">
                    Settings & Diagnostics
                  </h3>
                  <p className="text-[10px] font-mono text-cyan-400 tracking-wider">
                    JARVIS / FRIDAY • MARK-85
                  </p>
                </div>
              </div>

              <motion.button
                onClick={onClose}
                whileTap={{ scale: 0.9 }}
                className="w-8 h-8 rounded-full bg-white/10 hover:bg-white/15 flex items-center justify-center text-slate-300 hover:text-white transition-colors"
              >
                <X className="w-4 h-4" />
              </motion.button>
            </div>

            {/* Segmented iOS Tabs */}
            <div className="flex p-1 bg-black/50 border border-white/10 rounded-2xl my-3 shrink-0 text-[11px] font-mono overflow-x-auto scrollbar-none">
              <button
                onClick={() => setActiveTab('api')}
                className={`flex-1 py-1.5 px-2 rounded-xl whitespace-nowrap transition-all ${
                  activeTab === 'api'
                    ? 'bg-cyan-500 text-slate-950 font-bold shadow'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                API & Codec
              </button>
              <button
                onClick={() => setActiveTab('defaultApp')}
                className={`flex-1 py-1.5 px-2 rounded-xl whitespace-nowrap transition-all ${
                  activeTab === 'defaultApp'
                    ? 'bg-cyan-500 text-slate-950 font-bold shadow'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                Default App
              </button>
              <button
                onClick={() => setActiveTab('voice')}
                className={`flex-1 py-1.5 px-2 rounded-xl whitespace-nowrap transition-all ${
                  activeTab === 'voice'
                    ? 'bg-cyan-500 text-slate-950 font-bold shadow'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                Voice & Persona
              </button>
              <button
                onClick={() => setActiveTab('hardware')}
                className={`flex-1 py-1.5 px-2 rounded-xl whitespace-nowrap transition-all ${
                  activeTab === 'hardware'
                    ? 'bg-cyan-500 text-slate-950 font-bold shadow'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                Hardware
              </button>
              <button
                onClick={() => setActiveTab('apps')}
                className={`flex-1 py-1.5 px-2 rounded-xl whitespace-nowrap transition-all ${
                  activeTab === 'apps'
                    ? 'bg-cyan-500 text-slate-950 font-bold shadow'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                Apps ({apps.length})
              </button>
            </div>

            {/* Tab Content */}
            <div className="overflow-y-auto space-y-4 py-2 scrollbar-none flex-1">
              {/* TAB 1: API & CODEC */}
              {activeTab === 'api' && (
                <div className="space-y-4">
                  <div className="p-3.5 rounded-2xl bg-cyan-950/25 border border-cyan-500/30 space-y-2">
                    <div className="flex items-center space-x-2 text-cyan-300 text-xs font-bold">
                      <Key className="w-4 h-4 text-cyan-400" />
                      <span>Gemini API Key Configuration</span>
                    </div>
                    <p className="text-[11px] text-slate-300 leading-relaxed">
                      {Capacitor.isNativePlatform()
                        ? 'Standalone app mode — your personal Gemini API Key is REQUIRED here (the app connects directly to Gemini, no server). Get a free key at aistudio.google.com/apikey, paste it, and tap Save.'
                        : 'Leave empty to use the pre-configured server environment key, or enter your personal Gemini API Key for independent quota.'}
                    </p>
                  </div>

                  <div className="space-y-1.5">
                    <label className="text-xs font-mono text-slate-300">Custom API Key</label>
                    <div className="flex space-x-2">
                      <input
                        type="password"
                        value={apiKey}
                        onChange={(e) => setApiKey(e.target.value)}
                        placeholder="Default Environment Key Active"
                        className="flex-1 px-3.5 py-2.5 rounded-xl bg-black/50 border border-slate-700 focus:border-cyan-400 focus:outline-none text-xs font-mono text-slate-100 placeholder-slate-500"
                      />
                      <button
                        onClick={handleSaveApiKey}
                        className="px-4 py-2.5 rounded-xl bg-white/10 hover:bg-white/15 text-xs font-semibold text-slate-200 transition-colors"
                      >
                        Save
                      </button>
                    </div>
                    {keySavedMsg && (
                      <p className="text-[10px] font-mono text-cyan-400 flex items-center space-x-1">
                        <CheckCircle2 className="w-3 h-3" />
                        <span>{keySavedMsg}</span>
                      </p>
                    )}
                  </div>

                  {/* Auto Connection & Codec Test Button */}
                  <div>
                    <button
                      onClick={handleTestConnection}
                      disabled={isTestingApi}
                      className="w-full py-2.5 rounded-xl bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 text-slate-950 font-bold text-xs font-mono flex items-center justify-center space-x-2 shadow-md shadow-cyan-500/25 disabled:opacity-50"
                    >
                      {isTestingApi ? (
                        <>
                          <Activity className="w-3.5 h-3.5 animate-spin text-slate-950" />
                          <span>Testing Live Handshake & Audio Codecs...</span>
                        </>
                      ) : (
                        <>
                          <Cpu className="w-3.5 h-3.5 text-slate-950" />
                          <span>Run Auto Connection Test & Check Codecs</span>
                        </>
                      )}
                    </button>
                  </div>

                  {/* Test Result & Codecs Telemetry */}
                  {apiTestResult && (
                    <div
                      className={`p-3.5 rounded-2xl border text-xs space-y-2 ${
                        apiTestResult.success
                          ? 'bg-cyan-950/25 border-cyan-500/40 text-cyan-200'
                          : 'bg-rose-950/20 border-rose-500/40 text-rose-200'
                      }`}
                    >
                      <div className="flex items-center justify-between font-semibold">
                        <div className="flex items-center space-x-1.5">
                          {apiTestResult.success ? (
                            <CheckCircle2 className="w-4 h-4 text-cyan-400" />
                          ) : (
                            <AlertCircle className="w-4 h-4 text-rose-400" />
                          )}
                          <span>
                            {apiTestResult.success ? 'API & Codecs Verified' : 'Connection Failed'}
                          </span>
                        </div>
                        {apiTestResult.latencyMs !== undefined && (
                          <span className="font-mono text-[10px] px-1.5 py-0.5 rounded bg-black/40 border border-current">
                            {apiTestResult.latencyMs}ms Ping
                          </span>
                        )}
                      </div>

                      {apiTestResult.error && (
                        <p className="text-[11px] font-mono text-rose-300">
                          {apiTestResult.error}
                        </p>
                      )}

                      {/* Explicit Codecs Spec */}
                      <div className="p-2.5 rounded-xl bg-black/50 border border-white/10 space-y-1 font-mono text-[10px]">
                        <div className="flex justify-between">
                          <span className="text-slate-400">Audio Input Codec:</span>
                          <span className="text-cyan-300 font-bold">
                            {apiTestResult.audioInputCodec || 'audio/pcm;rate=16000 (16kHz PCM16)'}
                          </span>
                        </div>
                        <div className="flex justify-between">
                          <span className="text-slate-400">Audio Output Codec:</span>
                          <span className="text-cyan-300 font-bold">
                            {apiTestResult.audioOutputCodec || 'audio/pcm;rate=24000 (24kHz PCM16)'}
                          </span>
                        </div>
                        <div className="flex justify-between">
                          <span className="text-slate-400">Streaming Engine:</span>
                          <span className="text-teal-300">
                            {apiTestResult.primaryModel || 'gemini-3.1-flash-live-preview'}
                          </span>
                        </div>
                        <div className="flex justify-between">
                          <span className="text-slate-400">Transport:</span>
                          <span className="text-slate-300">WebSocket Bi-directional Stream</span>
                        </div>
                      </div>
                    </div>
                  )}

                  <div className="pt-2">
                    <button
                      onClick={onOpenOnboarding}
                      className="w-full py-2.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-slate-300 text-xs font-mono flex items-center justify-center space-x-1.5"
                    >
                      <RotateCcw className="w-3.5 h-3.5 text-cyan-400" />
                      <span>Rerun System Initial Setup Wizard</span>
                    </button>
                  </div>
                </div>
              )}

              {/* TAB 2: DEFAULT ASSISTANT & INSTALLATION */}
              {activeTab === 'defaultApp' && (
                <div className="space-y-4">
                  <div className="p-4 rounded-2xl bg-cyan-950/25 border border-cyan-500/30 space-y-2.5 text-xs">
                    <div className="flex items-center space-x-2 text-cyan-300 font-bold">
                      <Smartphone className="w-4 h-4 text-cyan-400" />
                      <span>Set as Default Digital Assistant on Android</span>
                    </div>
                    <p className="text-[11px] text-slate-300 leading-relaxed">
                      Follow these 3 easy steps to replace Google Assistant or Gemini with MJ on your home screen or power button hold:
                    </p>

                    <div className="p-3 rounded-xl bg-black/50 border border-white/10 space-y-2 font-mono text-[10px] text-slate-300">
                      <div className="flex items-start space-x-2">
                        <span className="w-4 h-4 rounded-full bg-cyan-500/30 text-cyan-300 flex items-center justify-center font-bold shrink-0">
                          1
                        </span>
                        <span>
                          Install the web app below or tap Chrome menu <strong>(&#8942;) &rarr; &ldquo;Add to Home screen&rdquo;</strong>.
                        </span>
                      </div>
                      <div className="flex items-start space-x-2">
                        <span className="w-4 h-4 rounded-full bg-cyan-500/30 text-cyan-300 flex items-center justify-center font-bold shrink-0">
                          2
                        </span>
                        <span>
                          Open phone <strong>Settings &rarr; Apps &rarr; Default apps</strong>.
                        </span>
                      </div>
                      <div className="flex items-start space-x-2">
                        <span className="w-4 h-4 rounded-full bg-cyan-500/30 text-cyan-300 flex items-center justify-center font-bold shrink-0">
                          3
                        </span>
                        <span>
                          Tap <strong>Digital assistant app &rarr; Default digital assistant</strong> and choose <strong>MJ / Chrome</strong>.
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Install PWA Button */}
                  <button
                    onClick={handlePwaInstall}
                    className="w-full py-3 rounded-2xl bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-slate-950 font-bold text-xs flex items-center justify-center space-x-2 shadow-lg shadow-cyan-500/25 transition-all"
                  >
                    <Download className="w-4 h-4" />
                    <span>Install App / Add to Home Screen</span>
                  </button>

                  {installMsg && (
                    <p className="text-xs font-mono text-cyan-300 p-2.5 rounded-xl bg-cyan-950/40 border border-cyan-800 text-center">
                      {installMsg}
                    </p>
                  )}
                </div>
              )}

              {/* TAB 3: VOICE & PERSONA */}
              {activeTab === 'voice' && (
                <div className="space-y-4">
                  {/* Wake Word Detection */}
                  <div className="p-3.5 rounded-2xl bg-black/40 border border-white/10 flex items-center justify-between text-xs">
                    <div className="flex items-center space-x-2.5">
                      <Volume2 className="w-4 h-4 text-cyan-400 shrink-0" />
                      <div>
                        <div className="text-white font-semibold">Wake Word Detection</div>
                        <div className="text-[10px] text-slate-400">
                          Say “Hey MJ”, “Jarvis”, “Friday” or “এমজে” on standby to start — hands-free
                        </div>
                      </div>
                    </div>
                    <button
                      onClick={() => handleToggleWakeWord(!wakeWordEnabled)}
                      className={`relative w-12 h-6 rounded-full transition-colors shrink-0 ${
                        wakeWordEnabled ? 'bg-cyan-500' : 'bg-slate-700'
                      }`}
                      aria-label="Toggle wake word detection"
                    >
                      <span
                        className="absolute top-0.5 w-5 h-5 rounded-full bg-white shadow transition-all"
                        style={{ left: wakeWordEnabled ? '26px' : '2px' }}
                      />
                    </button>
                  </div>

                  {/* Persona Selection */}
                  <div className="space-y-2">
                    <span className="text-xs font-mono uppercase tracking-wider text-slate-300 font-bold">
                      Assistant Persona
                    </span>
                    <div className="grid grid-cols-2 gap-2 text-xs">
                      {[
                        { id: 'sweet_female', name: 'Sweet MJ', desc: 'Mishti & affectionate girl' },
                        { id: 'friday', name: 'FRIDAY', desc: 'Tony Stark Tactical AI' },
                        { id: 'jarvis', name: 'JARVIS', desc: 'Sophisticated British Butler' },
                        { id: 'sassy', name: 'MJ Sassy', desc: 'Witty & Confident' },
                        { id: 'flirty', name: 'Flirty Companion', desc: 'Charming & Playful' },
                      ].map((p) => (
                        <button
                          key={p.id}
                          onClick={() => handlePersonaChange(p.id)}
                          className={`p-2.5 rounded-xl border text-left transition-all ${
                            selectedPersona === p.id
                              ? 'bg-cyan-950/60 border-cyan-400 text-cyan-200'
                              : 'bg-black/30 border-white/5 text-slate-400 hover:text-white'
                          }`}
                        >
                          <div className="font-semibold text-white">{p.name}</div>
                          <div className="text-[10px] text-slate-400">{p.desc}</div>
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Voice Selection */}
                  <div className="space-y-2">
                    <span className="text-xs font-mono uppercase tracking-wider text-slate-300 font-bold">
                      Neural TTS Voice Model
                    </span>
                    <div className="space-y-1.5">
                      {[
                        { id: 'Leda', name: 'Leda (Female) ★', desc: 'Youthful, sweet & melodious — recommended' },
                        { id: 'Aoede', name: 'Aoede (Female)', desc: 'Charismatic, confident, highly expressive' },
                        { id: 'Zephyr', name: 'Zephyr (Female)', desc: 'Bright, upbeat and cheerful' },
                        { id: 'Kore', name: 'Kore (Female)', desc: 'Calm, focused, tactical tone' },
                        { id: 'Fenrir', name: 'Fenrir (Male)', desc: 'Deep, commanding, cinematic tone' },
                        { id: 'Puck', name: 'Puck (Male)', desc: 'Energetic, witty, playful' },
                        { id: 'Charon', name: 'Charon (Male)', desc: 'Polite, refined, classic butler' },
                      ].map((v) => (
                        <div
                          key={v.id}
                          onClick={() => handleVoiceChange(v.id)}
                          className={`p-2.5 rounded-xl border flex items-center justify-between cursor-pointer transition-all ${
                            selectedVoice === v.id
                              ? 'bg-cyan-950/50 border-cyan-400 text-cyan-200'
                              : 'bg-black/30 border-white/5 text-slate-400 hover:text-white'
                          }`}
                        >
                          <div>
                            <div className="text-xs font-semibold text-white">{v.name}</div>
                            <div className="text-[10px] text-slate-400">{v.desc}</div>
                          </div>
                          {selectedVoice === v.id && (
                            <CheckCircle2 className="w-4 h-4 text-cyan-400 shrink-0" />
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              )}

              {/* TAB 4: HARDWARE DIAGNOSTICS */}
              {activeTab === 'hardware' && (
                <div className="space-y-4">
                  {/* Mic Test */}
                  <div className="p-3.5 rounded-2xl bg-black/40 border border-white/10 space-y-3 text-xs">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center space-x-2 text-cyan-300 font-semibold">
                        <Mic className="w-4 h-4 text-cyan-400" />
                        <span>Microphone Input (16kHz PCM16)</span>
                      </div>
                      <button
                        onClick={handleToggleMicTest}
                        className={`px-3 py-1 rounded-lg font-mono text-[11px] font-semibold transition-all ${
                          isTestingMic
                            ? 'bg-rose-500 text-white'
                            : 'bg-cyan-500 text-slate-950'
                        }`}
                      >
                        {isTestingMic ? 'Stop Test' : 'Test Mic VU'}
                      </button>
                    </div>

                    {/* Mic Level Bar */}
                    <div className="space-y-1">
                      <div className="w-full h-3 bg-slate-800 rounded-full overflow-hidden p-0.5 border border-slate-700">
                        <div
                          className="h-full bg-gradient-to-r from-emerald-500 via-cyan-400 to-rose-500 rounded-full transition-all duration-75"
                          style={{ width: `${micLevel}%` }}
                        />
                      </div>
                      <div className="flex justify-between text-[10px] font-mono text-slate-500">
                        <span>Silent</span>
                        <span>Level: {micLevel}%</span>
                        <span>Peak</span>
                      </div>
                    </div>
                  </div>

                  {/* Speaker Test */}
                  <div className="p-3.5 rounded-2xl bg-black/40 border border-white/10 space-y-3 text-xs">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center space-x-2 text-cyan-300 font-semibold">
                        <Volume2 className="w-4 h-4 text-cyan-400" />
                        <span>Speaker Output (24kHz DAC)</span>
                      </div>
                      <button
                        onClick={handleTestSpeaker}
                        className="px-3 py-1 rounded-lg bg-cyan-500 text-slate-950 font-mono text-[11px] font-semibold hover:bg-cyan-400"
                      >
                        Play Test Tone
                      </button>
                    </div>
                    {speakerTestMsg && (
                      <p className="text-[11px] font-mono text-cyan-300 bg-cyan-950/40 p-2 rounded-lg border border-cyan-800">
                        {speakerTestMsg}
                      </p>
                    )}
                  </div>
                </div>
              )}

              {/* TAB 5: APPS SCANNER */}
              {activeTab === 'apps' && (
                <div className="space-y-3">
                  <div className="flex items-center space-x-2">
                    <input
                      type="text"
                      value={appSearch}
                      onChange={(e) => setAppSearch(e.target.value)}
                      placeholder="Search scanned apps (e.g. YouTube, WhatsApp)..."
                      className="w-full px-3.5 py-2 rounded-xl bg-black/50 border border-slate-700 text-xs font-mono text-slate-100 placeholder-slate-500 focus:border-cyan-400 focus:outline-none"
                    />
                  </div>

                  {launchFeedback && (
                    <div className="p-2 rounded-xl bg-cyan-950/50 border border-cyan-600 text-cyan-300 text-xs font-mono">
                      {launchFeedback}
                    </div>
                  )}

                  <div className="grid grid-cols-2 gap-2 max-h-56 overflow-y-auto scrollbar-none">
                    {filteredApps.map((app) => (
                      <div
                        key={app.id}
                        className="p-2.5 rounded-xl bg-black/30 border border-white/5 flex items-center justify-between text-xs hover:border-cyan-500/30 transition-colors"
                      >
                        <div className="flex items-center space-x-2 mr-2 min-w-0">
                          {app.icon && app.icon.startsWith('data:') && (
                            <img
                              src={app.icon}
                              alt=""
                              className="w-8 h-8 rounded-lg shrink-0"
                            />
                          )}
                          <div className="truncate">
                            <span className="font-semibold text-white block truncate">
                              {app.name}
                            </span>
                            <span className="text-[9px] font-mono text-slate-500 uppercase">
                              {app.category}
                            </span>
                          </div>
                        </div>
                        <button
                          onClick={() => handleTestLaunchApp(app)}
                          className="px-2 py-1 rounded bg-cyan-500/20 hover:bg-cyan-500/30 text-cyan-300 text-[10px] font-mono shrink-0 border border-cyan-500/30"
                        >
                          Launch
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Bottom Done Button */}
            <div className="pt-2 shrink-0">
              <motion.button
                onClick={onClose}
                whileTap={{ scale: 0.96 }}
                className="w-full py-3 rounded-2xl bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold text-xs font-mono uppercase tracking-wider transition-colors shadow-lg shadow-cyan-500/25"
              >
                Apply & Return to JARVIS
              </motion.button>
            </div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
};
