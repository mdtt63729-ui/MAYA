/**
 * Wake Word Detector
 * Hands-free wake word engine using Web Speech Recognition.
 * Listens for "Hey MJ", "MJ", "Hey Jarvis", "Jarvis", "Friday", or Bengali equivalents.
 * Emits trigger event and plays a futuristic chime to start live session.
 */

type WakeWordCallback = (keyword: string, persona: string) => void;

interface IWindowWithSpeech extends Window {
  SpeechRecognition?: any;
  webkitSpeechRecognition?: any;
}

export class WakeWordDetector {
  private recognition: any = null;
  private isListening = false;
  private isEnabled = true;
  private isSessionPaused = false;
  private onWakeCallback?: WakeWordCallback;
  private restartTimer: ReturnType<typeof setTimeout> | null = null;

  private handleVisibilityChange = (): void => {
    // PERFORMANCE: release the mic & speech service while the app is
    // backgrounded/screen-off; resume when visible again.
    if (document.visibilityState === 'visible') {
      if (this.isEnabled && !this.isSessionPaused) this.start();
    } else {
      this.stop();
    }
  };

  // Recognized trigger phrases → each maps to an assistant persona, so
  // saying "Hey MJ", "Hey Jarvis", "Activate Friday" etc. activates the
  // assistant AND switches to that persona. Order matters: the more specific
  // personas are checked before the generic "mj".
  private readonly wakePhrases: Array<{ persona: string; words: string[] }> = [
    { persona: 'sassy', words: ['mj sassy', 'hey sassy', 'sassy', 'সেসি', 'স্যাসি'] },
    { persona: 'flirty', words: ['hey flirty', 'flirty', 'ফ্লার্টি'] },
    { persona: 'jarvis', words: ['hey jarvis', 'activate jarvis', 'jarvis on', 'jarvis', 'জার্ভিস', 'জারভিস'] },
    { persona: 'friday', words: ['hey friday', 'activate friday', 'friday on', 'friday', 'ফ্রাইডে'] },
    { persona: 'sweet_female', words: ['hey mj', 'activate mj', 'mj on', 'ok mj', 'listen mj', 'mj', 'এমজে'] },
  ];

  constructor() {
    const saved = localStorage.getItem('wake_word_enabled');
    // Wake-word listening is ON by default so "Hey MJ" / "Hey Jarvis" /
    // "Activate Friday" activate the assistant hands-free. Users who prefer
    // tap-only activation can switch it off in Settings.
    this.isEnabled = saved !== null ? saved === 'true' : true;
  }

  public init(callback: WakeWordCallback): boolean {
    this.onWakeCallback = callback;
    const windowWithSpeech = window as unknown as IWindowWithSpeech;
    const SpeechRecognition = windowWithSpeech.SpeechRecognition || windowWithSpeech.webkitSpeechRecognition;

    if (!SpeechRecognition) {
      console.warn('Wake Word: SpeechRecognition API not supported in this browser.');
      return false;
    }

    try {
      document.addEventListener('visibilitychange', this.handleVisibilityChange);

      this.recognition = new SpeechRecognition();
      this.recognition.continuous = true;
      this.recognition.interimResults = true;
      this.recognition.lang = 'en-US';

      this.recognition.onresult = (event: any) => {
        const results = event.results;
        for (let i = event.resultIndex; i < results.length; i++) {
          const transcript = results[i][0].transcript.trim().toLowerCase();

          for (const group of this.wakePhrases) {
            for (const word of group.words) {
              if (transcript.includes(word)) {
                console.log(`[Wake Word] Detected "${word}" → persona "${group.persona}" from "${transcript}"`);
                this.playWakeChime();
                this.onWakeCallback?.(word, group.persona);
                return;
              }
            }
          }
        }
      };

      this.recognition.onerror = (event: any) => {
        // 'no-speech' and 'aborted' are expected during silence or restart
        if (event.error !== 'no-speech' && event.error !== 'aborted') {
          console.warn('[Wake Word] Speech recognition error:', event.error);
        }
      };

      this.recognition.onend = () => {
        this.isListening = false;
        // Automatically restart wake word listening if still enabled and app visible
        if (this.isEnabled && !this.isSessionPaused && document.visibilityState !== 'hidden') {
          this.scheduleRestart(350);
        }
      };

      // MIC FIX: do NOT auto-start listening here. Only start when the user
      // explicitly enables wake word (or taps the orb to start a session).
      if (this.isEnabled) {
        this.start();
      }

      return true;
    } catch (e) {
      console.error('[Wake Word] Failed to initialize:', e);
      return false;
    }
  }

  public start(): void {
    if (!this.recognition || this.isListening || !this.isEnabled) return;
    if (document.hidden) return; // wait until the app is visible again
    try {
      this.recognition.start();
      this.isListening = true;
    } catch (e: any) {
      // If already started or restarting
      if (e.name !== 'InvalidStateError') {
        console.warn('[Wake Word] Start error:', e);
      }
    }
  }

  public stop(): void {
    if (this.restartTimer) {
      clearTimeout(this.restartTimer);
      this.restartTimer = null;
    }
    if (this.recognition && this.isListening) {
      try {
        this.recognition.stop();
      } catch {
        // Ignored
      }
      this.isListening = false;
    }
  }

  public pause(): void {
    // Called while a live session owns the mic — remember so the visibility
    // handler doesn't restart the detector behind the session's back.
    this.isSessionPaused = true;
    this.stop();
  }

  public resume(): void {
    this.isSessionPaused = false;
    if (this.isEnabled) {
      this.start();
    }
  }

  public toggle(enabled: boolean): void {
    this.isEnabled = enabled;
    localStorage.setItem('wake_word_enabled', enabled ? 'true' : 'false');
    if (enabled) {
      this.start();
    } else {
      this.stop();
    }
  }

  public getStatus(): { isEnabled: boolean; isListening: boolean } {
    return {
      isEnabled: this.isEnabled,
      isListening: this.isListening,
    };
  }

  /**
   * Synthesize a futuristic, sweet pleasant chime upon wake word detection
   */
  public playWakeChime(): void {
    try {
      const AudioCtxClass = window.AudioContext || (window as any).webkitAudioContext;
      if (!AudioCtxClass) return;
      const ctx = new AudioCtxClass();

      const now = ctx.currentTime;
      const osc1 = ctx.createOscillator();
      const osc2 = ctx.createOscillator();
      const gain = ctx.createGain();

      osc1.type = 'sine';
      osc2.type = 'triangle';

      // Harmonic dual-tone ascending chord (E5 -> B5 -> E6)
      osc1.frequency.setValueAtTime(659.25, now); // E5
      osc1.frequency.exponentialRampToValueAtTime(1318.51, now + 0.18); // E6

      osc2.frequency.setValueAtTime(987.77, now); // B5
      osc2.frequency.exponentialRampToValueAtTime(1975.53, now + 0.18); // B6

      gain.gain.setValueAtTime(0.12, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.35);

      osc1.connect(gain);
      osc2.connect(gain);
      gain.connect(ctx.destination);

      osc1.start(now);
      osc2.start(now);
      osc1.stop(now + 0.35);
      osc2.stop(now + 0.35);
    } catch {
      // Audio autoplay policy fallback
    }
  }

  private scheduleRestart(delayMs: number): void {
    if (this.restartTimer) {
      clearTimeout(this.restartTimer);
    }
    this.restartTimer = setTimeout(() => {
      if (this.isEnabled) {
        this.start();
      }
    }, delayMs);
  }
}

export const wakeWordDetector = new WakeWordDetector();
