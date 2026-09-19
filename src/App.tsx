/**
 * MJ AI Assistant - Iron Man JARVIS / FRIDAY Futuristic Voice Interface
 * Master Requirements:
 * - Full native iOS-grade fluid animations & transitions (spring curves, frosted glass, tactile tap compression)
 * - Iron Man / FRIDAY inspired Arc Reactor holographic UI
 * - Strictly NO chat screen on home page
 * - Single Settings button in the top bar (clean, minimal, no cluttered buttons)
 * - Live real-time dialogue subtitles directly underneath the Orb (User speech & MJ AI speech)
 * - Auto-app scanner on startup & reliable app launcher for YouTube, WhatsApp, Camera, etc.
 * - Ultra-premium multi-tiered Arc Reactor Orb with real-time audio wave reactivity
 */

import React, { useState, useEffect, useCallback, useRef } from 'react';
import { liveSession, LiveSessionState, AppActionPayload } from './services/liveSession';
import { appScanner } from './services/appScanner';
import { LocalCommandResult } from './services/localCommandEngine';
import { ActiveTask } from './types';
import { wakeWordDetector } from './services/wakeWordDetector';
import { IosTopBar } from './components/voice/IosTopBar';
import { JarvisArcOrb } from './components/voice/JarvisArcOrb';
import { SpokenDialogueSubtitle } from './components/voice/SpokenDialogueSubtitle';
import { IosSettingsModal } from './components/voice/IosSettingsModal';
import { IosAppLaunchSheet } from './components/voice/IosAppLaunchSheet';
import { VoiceVisualizer } from './components/voice/VoiceVisualizer';
import { OnboardingModal } from './components/onboarding/OnboardingModal';
import { ActionConfirmationModal } from './components/modals/ActionConfirmationModal';
import { AlertCircle, RefreshCw } from 'lucide-react';

export default function App() {
  const [sessionState, setSessionState] = useState<LiveSessionState>('disconnected');
  const [isMuted, setIsMuted] = useState<boolean>(false);
  const [userSpeech, setUserSpeech] = useState<string>('');
  const [mjSpeech, setMjSpeech] = useState<string>('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [interruptedAlert, setInterruptedAlert] = useState<boolean>(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState<boolean>(false);
  const [isOnboardingOpen, setIsOnboardingOpen] = useState<boolean>(false);
  const [activeAppAction, setActiveAppAction] = useState<AppActionPayload | null>(null);
  const [deferredPrompt, setDeferredPrompt] = useState<any>(null);
  const [pendingConfirmTask, setPendingConfirmTask] = useState<ActiveTask | null>(null);

  // Check onboarding on initial mount
  useEffect(() => {
    // One-time onboarding per app version — fresh installs AND users who
    // upgraded (their old "completed" flag doesn't carry over the version bump)
    const isCompleted = localStorage.getItem('mj_onboarding_completed_v2');
    if (!isCompleted) {
      setIsOnboardingOpen(true);
    }

    // One-time upgrade: default MJ to the sweet girl voice (Leda)
    // and the sweet_female persona, even for installs that previously
    // saved a different voice/persona in localStorage.
    if (localStorage.getItem('mj_voice_pref_v2') !== 'done') {
      localStorage.setItem('mj_voice_pref_v2', 'done');
      localStorage.setItem('gemini_selected_voice', 'Leda');
      localStorage.setItem('gemini_selected_persona', 'sweet_female');
    }

    // Capture PWA beforeinstallprompt event
    const handleBeforeInstall = (e: Event) => {
      e.preventDefault();
      setDeferredPrompt(e);
    };

    window.addEventListener('beforeinstallprompt', handleBeforeInstall);
    return () => {
      window.removeEventListener('beforeinstallprompt', handleBeforeInstall);
    };
  }, []);

  // ---- WAKE WORD DETECTION ("Hey MJ" / "Jarvis" / "Friday" / "এমজে") ----
  // Hands-free: say the wake word while on standby to start the voice session.
  // While a live session is active the detector pauses (the session mic owns
  // the input), and it resumes automatically when the session ends.
  const toggleSessionRef = useRef<() => void>(() => {});

  useEffect(() => {
    wakeWordDetector.init(() => {
      if (liveSession.getState() === 'disconnected') {
        toggleSessionRef.current();
      }
    });
    return () => wakeWordDetector.stop();
  }, []);

  // Scan and index apps on initial boot
  useEffect(() => {
    appScanner.scanInstalledApps();
  }, []);

  // Handle live transcription (subtitles under Orb)
  const handleTranscript = useCallback((source: 'user' | 'mj', text: string) => {
    if (source === 'user') {
      setUserSpeech(text);
    } else {
      setMjSpeech(text);
    }
  }, []);

  // Interruption handling
  const handleInterrupted = useCallback(() => {
    setInterruptedAlert(true);
    const t = setTimeout(() => setInterruptedAlert(false), 2000);
    return () => clearTimeout(t);
  }, []);

  // Handle app action execution
  const handleAppAction = useCallback((action: AppActionPayload) => {
    setActiveAppAction(action);
  }, []);

  // FAST LOCAL PATH results — instant feedback + confirmation flow for
  // consequential actions (WhatsApp message / phone call).
  const handleLocalCommand = useCallback((result: LocalCommandResult) => {
    // Show the instant local acknowledgement under the orb
    setMjSpeech(result.feedbackText);
    // Surface consequential actions for explicit user confirmation
    if (result.requiresConfirmation && result.confirmationTask) {
      setPendingConfirmTask(result.confirmationTask);
    }
  }, []);

  // Connect or disconnect voice session
  const toggleSession = async () => {

    if (sessionState !== 'disconnected') {
      liveSession.disconnect();
      setSessionState('disconnected');
      return;
    }

    setErrorMessage(null);

    try {
      await liveSession.connect({
        onStateChange: (newState) => {
          setSessionState(newState);
          // Wake word sleeps during a live session and wakes on standby
          if (newState === 'disconnected') {
            wakeWordDetector.resume();
          } else {
            wakeWordDetector.pause();
          }
        },
        onTranscript: handleTranscript,
        onAppAction: handleAppAction,
        onLocalCommand: handleLocalCommand,
        onInterrupted: handleInterrupted,
        onError: (err) => {
          setErrorMessage(err);
          // Key missing (standalone APK) — open Settings straight on the API key tab
          if (err.toLowerCase().includes('add your gemini api key')) {
            setIsSettingsOpen(true);
          }
        },
      });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Microphone access failed';
      setErrorMessage(msg);
      setSessionState('disconnected');
    }
  };

  // Keep the latest toggleSession for the wake-word listener (no stale closure)
  toggleSessionRef.current = toggleSession;

  // Cleanup
  useEffect(() => {
    return () => {
      liveSession.disconnect();
    };
  }, []);

  return (
    <div className="relative w-screen h-screen min-h-[100dvh] bg-[#03060c] text-slate-100 flex flex-col justify-between overflow-hidden select-none font-sans antialiased">
      {/* Iron Man / Holographic HUD Ambient Background */}
      <div className="absolute inset-0 pointer-events-none overflow-hidden">
        {/* Holographic Ambient Glow (radial gradient — NO blur filter: mobile perf) */}
        <div
          className="absolute top-1/3 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[550px] h-[550px] rounded-full opacity-40"
          style={{
            background:
              'radial-gradient(circle, rgba(6,182,212,0.32) 0%, rgba(2,132,199,0.13) 45%, transparent 70%)',
          }}
        />
        {/* Cyber Grid Lines */}
        <div className="absolute inset-0 bg-[linear-gradient(to_right,#0284c70a_1px,transparent_1px),linear-gradient(to_bottom,#0284c70a_1px,transparent_1px)] bg-[size:36px_36px]" />
      </div>

      {/* Top Bar: Clean, Minimal with ONLY ONE native iOS Settings button */}
      <IosTopBar
        state={sessionState}
        interruptedAlert={interruptedAlert}
        onOpenSettings={() => setIsSettingsOpen(true)}
      />

      {/* Error Alert Bar */}
      {errorMessage && (
        <div className="fixed top-20 left-1/2 -translate-x-1/2 z-50 w-11/12 max-w-md animate-in fade-in duration-200">
          <div className="p-3.5 rounded-2xl bg-rose-950/95 border border-rose-600/60 shadow-xl flex items-center justify-between text-xs text-rose-200">
            <div className="flex items-center space-x-2 break-words text-left flex-1 min-w-0">
              <AlertCircle className="w-4 h-4 text-rose-400 shrink-0" />
              <span className="break-words">{errorMessage}</span>
            </div>
            <button
              onClick={toggleSession}
              className="px-3 py-1.5 ml-2 rounded-xl bg-rose-900/80 hover:bg-rose-800 text-white font-medium shrink-0 flex items-center space-x-1 transition-colors active:scale-95"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              <span>Retry</span>
            </button>
          </div>
        </div>
      )}

      {/* Centerpiece: Iron Man / FRIDAY Arc Reactor Orb */}
      <main className="flex-1 flex flex-col items-center justify-center relative z-20 px-4 w-full my-auto">
        <JarvisArcOrb
          state={sessionState}
          isMuted={isMuted}
          onTogglePower={toggleSession}
        />

        {/* Live Audio Reactive Spectrum Visualizer */}
        <div className="mt-2 w-full flex justify-center">
          <VoiceVisualizer state={sessionState} themeColor="cyan" />
        </div>

        {/* Live Spoken Dialogue Subtitles Underneath the Orb */}
        <SpokenDialogueSubtitle
          state={sessionState}
          userSpeech={userSpeech}
          mjSpeech={mjSpeech}
        />
      </main>

      {/* Bottom Subtle Voice Prompts Guide */}
      <footer className="w-full relative z-20 pb-5 px-4 text-center">
        <div className="flex items-center justify-center space-x-2 text-[11px] font-mono text-cyan-400/60 tracking-wider">
          <span className="w-1.5 h-1.5 rounded-full bg-cyan-400 animate-pulse" />
          <span>SAY: &ldquo;OPEN YOUTUBE&rdquo; • &ldquo;WHATSAPP KHOLO&rdquo; • &ldquo;OPEN CAMERA&rdquo; • &ldquo;SETTINGS&rdquo;</span>
        </div>
      </footer>

      {/* iOS Settings Sheet (Triggered by single Top Right Settings Button) */}
      <IosSettingsModal
        isOpen={isSettingsOpen}
        onClose={() => setIsSettingsOpen(false)}
        onOpenOnboarding={() => {
          setIsSettingsOpen(false);
          setIsOnboardingOpen(true);
        }}
        deferredPrompt={deferredPrompt}
      />

      {/* Onboarding Screen & API Setup Wizard */}
      <OnboardingModal
        isOpen={isOnboardingOpen}
        onComplete={() => setIsOnboardingOpen(false)}
        deferredPrompt={deferredPrompt}
      />

      {/* iOS App Launch Feedback Sheet */}
      <IosAppLaunchSheet
        appAction={activeAppAction}
        onClose={() => setActiveAppAction(null)}
      />

      {/* Consequential Action Confirmation (WhatsApp / Call) */}
      <ActionConfirmationModal
        task={pendingConfirmTask}
        onConfirm={() => {
          const task = pendingConfirmTask;
          setPendingConfirmTask(null);
          if (task?.onConfirm) {
            void task.onConfirm();
          }
        }}
        onCancel={() => setPendingConfirmTask(null)}
      />
    </div>
  );
}
