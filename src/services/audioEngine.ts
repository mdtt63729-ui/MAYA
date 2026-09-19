/**
 * MJ Audio & Speech Engine
 * Manages microphone capture, real-time waveform analysis for Orb,
 * audio playback with real amplitude tracking, VAD, interruption, and TTS.
 */

import { IndianLanguageCode } from '../types';
import { INDIAN_LANGUAGES } from './indianLanguageService';

type AudioListener = (amplitude: number, frequencies: number[]) => void;
type StateChangeListener = (isListening: boolean, isSpeaking: boolean) => void;

class AudioEngine {
  private audioContext: AudioContext | null = null;
  private micStream: MediaStream | null = null;
  private micAnalyser: AnalyserNode | null = null;
  private outAnalyser: AnalyserNode | null = null;
  private currentAudioSource: AudioBufferSourceNode | null = null;
  private currentAudioElement: HTMLAudioElement | null = null;
  
  private animationFrameId: number | null = null;
  private audioListeners: Set<AudioListener> = new Set();
  private stateListeners: Set<StateChangeListener> = new Set();

  private isListening: boolean = false;
  private isSpeaking: boolean = false;
  private vadThreshold: number = 0.08;
  private interruptionCallback: (() => void) | null = null;

  public initContext(): AudioContext {
    if (!this.audioContext || this.audioContext.state === 'closed') {
      const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      this.audioContext = new AudioContextClass();
    }
    if (this.audioContext.state === 'suspended') {
      this.audioContext.resume();
    }
    return this.audioContext;
  }

  public onAudioData(listener: AudioListener): () => void {
    this.audioListeners.add(listener);
    return () => this.audioListeners.delete(listener);
  }

  public onStateChange(listener: StateChangeListener): () => void {
    this.stateListeners.add(listener);
    return () => this.stateListeners.delete(listener);
  }

  public setInterruptionCallback(callback: () => void) {
    this.interruptionCallback = callback;
  }

  private notifyState() {
    this.stateListeners.forEach((fn) => fn(this.isListening, this.isSpeaking));
  }

  /**
   * Start microphone capture and analyzer
   */
  public async startMicrophone(): Promise<boolean> {
    try {
      this.initContext();
      if (this.isListening) return true;

      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      });
      this.micStream = stream;

      const ctx = this.audioContext!;
      const source = ctx.createMediaStreamSource(stream);
      const analyser = ctx.createAnalyser();
      analyser.fftSize = 64;
      analyser.smoothingTimeConstant = 0.6;
      source.connect(analyser);
      this.micAnalyser = analyser;

      this.isListening = true;
      this.notifyState();
      this.startLoop();
      return true;
    } catch (err) {
      console.warn('Microphone permission or hardware unavailable:', err);
      this.isListening = false;
      this.notifyState();
      return false;
    }
  }

  /**
   * Stop microphone
   */
  public stopMicrophone() {
    if (this.micStream) {
      this.micStream.getTracks().forEach((track) => track.stop());
      this.micStream = null;
    }
    this.micAnalyser = null;
    this.isListening = false;
    this.notifyState();
  }

  /**
   * Play base64 audio or URL
   */
  public async playAudio(audioUrlOrBase64: string): Promise<void> {
    this.interrupt(); // Interrupt any ongoing speech first
    const ctx = this.initContext();

    this.isSpeaking = true;
    this.notifyState();

    return new Promise(async (resolve, reject) => {
      try {
        const audio = new Audio();
        audio.crossOrigin = 'anonymous';

        if (audioUrlOrBase64.startsWith('data:audio') || audioUrlOrBase64.startsWith('blob:') || audioUrlOrBase64.startsWith('http')) {
          audio.src = audioUrlOrBase64;
        } else {
          // Assume raw base64 or wav
          audio.src = `data:audio/mp3;base64,${audioUrlOrBase64}`;
        }

        const source = ctx.createMediaElementSource(audio);
        const analyser = ctx.createAnalyser();
        analyser.fftSize = 64;
        analyser.smoothingTimeConstant = 0.5;
        source.connect(analyser);
        analyser.connect(ctx.destination);
        this.outAnalyser = analyser;
        this.currentAudioElement = audio;

        audio.onended = () => {
          this.isSpeaking = false;
          this.outAnalyser = null;
          this.currentAudioElement = null;
          this.notifyState();
          resolve();
        };

        audio.onerror = (e) => {
          console.warn('Audio playback error:', e);
          this.isSpeaking = false;
          this.outAnalyser = null;
          this.currentAudioElement = null;
          this.notifyState();
          resolve();
        };

        await audio.play();
        this.startLoop();
      } catch (err) {
        console.warn('Failed playing audio stream:', err);
        this.isSpeaking = false;
        this.notifyState();
        resolve();
      }
    });
  }

  /**
   * Speak text using Web Speech API (offline fallback with proper Indian accents)
   */
  public speakLocalTTS(text: string, langCode: IndianLanguageCode = 'en-IN'): Promise<void> {
    this.interrupt();

    return new Promise((resolve) => {
      if (!('speechSynthesis' in window)) {
        console.warn('SpeechSynthesis not available in this environment');
        resolve();
        return;
      }

      window.speechSynthesis.cancel();
      const utterance = new SpeechSynthesisUtterance(text);

      const langInfo = INDIAN_LANGUAGES.find((l) => l.code === langCode);
      utterance.lang = langInfo ? langInfo.speechLocale : 'en-IN';
      utterance.rate = 1.02;
      utterance.pitch = 1.0;

      // Select matching voice if available in browser
      const voices = window.speechSynthesis.getVoices();
      const matchedVoice = voices.find(
        (v) => v.lang.toLowerCase().replace('_', '-') === utterance.lang.toLowerCase() ||
               v.name.toLowerCase().includes(langCode)
      );
      if (matchedVoice) {
        utterance.voice = matchedVoice;
      }

      this.isSpeaking = true;
      this.notifyState();

      // Simulate output amplitude for Orb waveform during speech synthesis
      let ttsInterval: number | null = null;
      ttsInterval = window.setInterval(() => {
        if (!this.isSpeaking) {
          if (ttsInterval) clearInterval(ttsInterval);
          return;
        }
        const simAmp = 0.3 + Math.random() * 0.45;
        const simFreq = Array.from({ length: 16 }, () => Math.random() * 0.8);
        this.audioListeners.forEach((l) => l(simAmp, simFreq));
      }, 50);

      utterance.onend = () => {
        if (ttsInterval) clearInterval(ttsInterval);
        this.isSpeaking = false;
        this.notifyState();
        resolve();
      };

      utterance.onerror = () => {
        if (ttsInterval) clearInterval(ttsInterval);
        this.isSpeaking = false;
        this.notifyState();
        resolve();
      };

      window.speechSynthesis.speak(utterance);
    });
  }

  /**
   * User Interruption Handler:
   * Instantly stops any active audio/TTS, clears queue, triggers callback
   */
  public interrupt() {
    if (this.currentAudioElement) {
      this.currentAudioElement.pause();
      this.currentAudioElement.currentTime = 0;
      this.currentAudioElement = null;
    }
    if (this.currentAudioSource) {
      try {
        this.currentAudioSource.stop();
      } catch {}
      this.currentAudioSource = null;
    }
    if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel();
    }
    this.outAnalyser = null;
    if (this.isSpeaking) {
      this.isSpeaking = false;
      this.notifyState();
      if (this.interruptionCallback) {
        this.interruptionCallback();
      }
    }
  }

  /**
   * Main real-time analyzer loop (60 FPS)
   */
  private startLoop() {
    if (this.animationFrameId !== null) return;

    const tick = () => {
      let activeAnalyser = this.isSpeaking ? this.outAnalyser : (this.isListening ? this.micAnalyser : null);

      if (activeAnalyser) {
        const bufferLength = activeAnalyser.frequencyBinCount;
        const dataArray = new Uint8Array(bufferLength);
        activeAnalyser.getByteFrequencyData(dataArray);

        let sum = 0;
        const freqs: number[] = [];
        for (let i = 0; i < bufferLength; i++) {
          const val = dataArray[i] / 255;
          sum += val;
          if (i % 2 === 0 && freqs.length < 16) {
            freqs.push(val);
          }
        }
        const avg = bufferLength > 0 ? sum / bufferLength : 0;

        // VAD Interruption Check: If speaking and user starts talking into mic
        if (this.isSpeaking && this.micAnalyser) {
          const micData = new Uint8Array(this.micAnalyser.frequencyBinCount);
          this.micAnalyser.getByteFrequencyData(micData);
          let micSum = 0;
          for (let i = 0; i < micData.length; i++) micSum += micData[i] / 255;
          const micAvg = micData.length > 0 ? micSum / micData.length : 0;
          if (micAvg > this.vadThreshold * 1.5) {
            this.interrupt();
          }
        }

        this.audioListeners.forEach((l) => l(avg, freqs));
      }

      if (this.isListening || this.isSpeaking) {
        this.animationFrameId = requestAnimationFrame(tick);
      } else {
        this.animationFrameId = null;
      }
    };

    this.animationFrameId = requestAnimationFrame(tick);
  }
}

export const audioEngine = new AudioEngine();
