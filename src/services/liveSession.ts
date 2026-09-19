/**
 * LiveSession
 * Manages WebSocket connection to Gemini Live API backend:
 * - Session state: disconnected | connecting | listening | speaking
 * - Audio streaming with AudioStreamer (16kHz PCM16 in, 24kHz out)
 * - Real-time speech transcription (live subtitles under Orb)
 * - Native & Web App scanning & launching (openApp / openWebsite)
 * - Interruption handling & reconnection resilience
 */

import { audioStreamer } from './audioStreamer';
import { appScanner, AppDefinition } from './appScanner';

export type LiveSessionState =
  | 'disconnected'
  | 'connecting'
  | 'listening'
  | 'speaking'
  | 'thinking'
  | 'searching';

export interface ToolCallPayload {
  id: string;
  name: string;
  args: Record<string, unknown>;
}

export interface AppActionPayload {
  appName: string;
  actionType: string;
  url?: string;
  success: boolean;
}

export interface LiveSessionCallbacks {
  onStateChange: (state: LiveSessionState) => void;
  onToolCall?: (toolCall: ToolCallPayload) => void;
  onError?: (errorMsg: string) => void;
  onInterrupted?: () => void;
  onTranscript?: (source: 'user' | 'mj', text: string, isFinal?: boolean) => void;
  onAppAction?: (action: AppActionPayload) => void;
}

export class LiveSession {
  private ws: WebSocket | null = null;
  private state: LiveSessionState = 'disconnected';
  private callbacks: LiveSessionCallbacks = {
    onStateChange: () => {},
  };
  private isMuted = false;
  private speechRecognizer: any = null;

  // "Thinking" voice-activity detection (orb THINKING state)
  private thinkingTimer: ReturnType<typeof setInterval> | null = null;
  private thinkingMaxTimer: ReturnType<typeof setTimeout> | null = null;
  private searchingResetTimer: ReturnType<typeof setTimeout> | null = null;
  private lastUserVoiceAt = 0;
  private hadUserVoice = false;

  // Auto-reconnect resilience (fixes "Connection to Gemini Live lost")
  private static readonly MAX_RECONNECT_ATTEMPTS = 3;
  private reconnectAttempts = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private isManualDisconnect = false;
  private receivedServerError = false;

  constructor() {
    // Scan all installed apps on session engine boot
    appScanner.scanInstalledApps();

    // Synchronize speaking state from audioStreamer
    audioStreamer.onSpeakingChange((isSpeaking) => {
      if (this.state !== 'disconnected' && this.state !== 'connecting') {
        this.setState(isSpeaking ? 'speaking' : 'listening');
      }
    });
  }

  public getState(): LiveSessionState {
    return this.state;
  }

  public isMicrophoneMuted(): boolean {
    return this.isMuted;
  }

  public setMuted(muted: boolean): void {
    this.isMuted = muted;
  }

  /**
   * Initialize local browser speech recognizer for zero-latency subtitles of user voice
   */
  private startLocalSpeechRecognition(): void {
    try {
      const SpeechRecognition =
        (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
      if (!SpeechRecognition) return;

      this.speechRecognizer = new SpeechRecognition();
      this.speechRecognizer.continuous = true;
      this.speechRecognizer.interimResults = true;
      this.speechRecognizer.lang = 'en-US';

      this.speechRecognizer.onresult = (event: any) => {
        let interimTranscript = '';
        for (let i = event.resultIndex; i < event.results.length; ++i) {
          const transcript = event.results[i][0].transcript;
          if (event.results[i].isFinal) {
            this.callbacks.onTranscript?.('user', transcript.trim(), true);
          } else {
            interimTranscript += transcript;
          }
        }
        if (interimTranscript) {
          this.callbacks.onTranscript?.('user', interimTranscript.trim(), false);
        }
      };

      this.speechRecognizer.onerror = (e: any) => {
        // Ignore benign speech recognition errors (e.g. no-speech)
        if (e.error !== 'no-speech') {
          console.warn('[SpeechRecognition]', e.error);
        }
      };

      this.speechRecognizer.onend = () => {
        // Restart if still in listening state
        if (this.state === 'listening' && this.speechRecognizer) {
          try {
            this.speechRecognizer.start();
          } catch {
            // Ignored
          }
        }
      };

      this.speechRecognizer.start();
    } catch (err) {
      console.warn('SpeechRecognition not available or failed:', err);
    }
  }

  private stopLocalSpeechRecognition(): void {
    if (this.speechRecognizer) {
      try {
        this.speechRecognizer.stop();
      } catch {
        // Ignored
      }
      this.speechRecognizer = null;
    }
  }

  /**
   * THINKING monitor: light-weight client-side VAD.
   * When the user stops speaking (mic level drops after voice activity), the
   * orb enters 'thinking' until the model starts speaking (or 12s elapse).
   */
  private startThinkingMonitor(): void {
    this.stopThinkingMonitor();
    this.lastUserVoiceAt = 0;
    this.hadUserVoice = false;

    this.thinkingTimer = setInterval(() => {
      if (this.isMuted) return;
      const vol = audioStreamer.getInputVolume();
      const now = Date.now();

      if (this.state === 'listening') {
        if (vol > 0.055) {
          this.lastUserVoiceAt = now;
          this.hadUserVoice = true;
        } else if (
          this.hadUserVoice &&
          this.lastUserVoiceAt > 0 &&
          now - this.lastUserVoiceAt > 1200
        ) {
          // User paused — the model is processing the request
          this.setState('thinking');
          this.thinkingMaxTimer = setTimeout(() => {
            if (this.state === 'thinking') this.setState('listening');
          }, 12000);
        }
      } else if (this.state === 'thinking') {
        if (vol > 0.075) {
          // User barge-in: they resumed speaking
          if (this.thinkingMaxTimer) {
            clearTimeout(this.thinkingMaxTimer);
            this.thinkingMaxTimer = null;
          }
          this.setState('listening');
        }
      }
    }, 180);
  }

  private stopThinkingMonitor(): void {
    if (this.thinkingTimer) {
      clearInterval(this.thinkingTimer);
      this.thinkingTimer = null;
    }
    if (this.thinkingMaxTimer) {
      clearTimeout(this.thinkingMaxTimer);
      this.thinkingMaxTimer = null;
    }
    this.hadUserVoice = false;
    this.lastUserVoiceAt = 0;
  }

  /**
   * Connect to Gemini Live audio session
   */
  public async connect(callbacks: LiveSessionCallbacks): Promise<void> {
    this.cancelReconnect();
    this.isManualDisconnect = false;
    this.receivedServerError = false;

    if (this.state !== 'disconnected' && this.ws) {
      this.performDisconnect();
    }

    this.callbacks = callbacks;
    this.setState('connecting');

    try {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const host = window.location.host;

      // Read custom API key, selected voice, and persona from localStorage
      // Defaults: Leda voice + sweet_female persona (মিষ্টি মেয়ের কণ্ঠ)
      const savedKey = localStorage.getItem('gemini_custom_api_key') || '';
      const savedVoice = localStorage.getItem('gemini_selected_voice') || 'Leda';
      const savedPersona = localStorage.getItem('gemini_selected_persona') || 'sweet_female';

      const params = new URLSearchParams();
      if (savedKey.trim()) params.set('apiKey', savedKey.trim());
      if (savedVoice) params.set('voice', savedVoice);
      if (savedPersona) params.set('persona', savedPersona);
      const query = params.toString() ? `?${params.toString()}` : '';

      const wsUrl = `${protocol}//${host}/live${query}`;

      this.ws = new WebSocket(wsUrl);

      this.ws.onopen = async () => {
        try {
          // 1. Start mic audio streaming via AudioStreamer (16kHz PCM16)
          await audioStreamer.startRecording((base64Chunk) => {
            if (this.ws && this.ws.readyState === WebSocket.OPEN && !this.isMuted) {
              this.ws.send(JSON.stringify({ audio: base64Chunk }));
            }
          });

          // 2. MIC AUTO-OFF FIX: the local browser SpeechRecognizer is NOT
          // started here anymore. Running webkitSpeechRecognition alongside
          // getUserMedia created a second exclusive mic consumer — on Android
          // the recognition service periodically restarts (no-speech/network
          // timeouts) and re-routes the mic away from the capture stream,
          // killing it after 1-2 minutes. User subtitles still arrive from the
          // server via inputAudioTranscription, and AudioStreamer's watchdog
          // now auto-recovers the mic if the OS ever drops it.

          this.startThinkingMonitor();
          // Session is live & healthy — reset the reconnect budget
          this.reconnectAttempts = 0;
          this.setState('listening');
        } catch (err: unknown) {
          const msg = err instanceof Error ? err.message : 'Microphone access denied';
          this.callbacks.onError?.(msg);
          this.disconnect();
        }
      };

      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);

          // 1. Audio output chunk from Gemini Live (24kHz PCM16)
          if (data.audio) {
            audioStreamer.playAudioChunk(data.audio);
          }

          // 2. Model spoken transcription (display under orb)
          if (data.modelTranscript) {
            this.callbacks.onTranscript?.('mj', data.modelTranscript, true);
          }

          // 3. User spoken transcription from server
          if (data.userTranscript) {
            this.callbacks.onTranscript?.('user', data.userTranscript, true);
          }

          // 4. Model Interruption (User started speaking)
          if (data.interrupted) {
            audioStreamer.handleInterruption();
            this.callbacks.onInterrupted?.();
            if (this.state === 'speaking') {
              this.setState('listening');
            }
          }

          // 5. Function Calling (openApp, openWebsite, searchWeb, etc.)
          if (data.toolCall) {
            this.handleToolCall(data.toolCall);
          }

          // 6. Server error notification
          if (data.error) {
            this.receivedServerError = true;
            this.callbacks.onError?.(data.error);
          }
        } catch (e) {
          console.error('Failed to parse WebSocket message:', e);
        }
      };

      this.ws.onerror = (err) => {
        // Log only — onclose handles recovery/reconnection right after this
        console.error('WebSocket error in LiveSession:', err);
      };

      this.ws.onclose = () => {
        // Socket died — release session resources but keep the callbacks
        this.ws = null;
        this.stopLocalSpeechRecognition();
        this.stopThinkingMonitor();
        if (this.searchingResetTimer) {
          clearTimeout(this.searchingResetTimer);
          this.searchingResetTimer = null;
        }
        audioStreamer.stop();

        if (this.isManualDisconnect) {
          this.setState('disconnected');
          return;
        }

        // Unexpected loss — auto-reconnect with exponential backoff (1s → 2s → 4s)
        if (
          this.state !== 'disconnected' &&
          !this.receivedServerError &&
          this.reconnectAttempts < LiveSession.MAX_RECONNECT_ATTEMPTS
        ) {
          this.reconnectAttempts++;
          this.setState('connecting');
          const delayMs = 1000 * Math.pow(2, this.reconnectAttempts - 1);
          console.warn(
            `[LiveSession] Connection lost — auto-reconnect ${this.reconnectAttempts}/${LiveSession.MAX_RECONNECT_ATTEMPTS} in ${delayMs}ms`
          );
          this.reconnectTimer = setTimeout(() => {
            this.connect(this.callbacks);
          }, delayMs);
          return;
        }

        // Retries exhausted (or the server reported a real error — already shown)
        if (!this.receivedServerError) {
          this.callbacks.onError?.(
            'Connection to Gemini Live lost. Auto-retry failed — check your internet connection and that the server is running, then tap Retry.'
          );
        }
        this.setState('disconnected');
        this.reconnectAttempts = 0;
        this.receivedServerError = false;
      };
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to establish Live Session';
      this.callbacks.onError?.(msg);
      this.disconnect();
    }
  }

  /**
   * Execute application or browser tool action and return acknowledgment to server
   */
  private handleToolCall(toolCall: ToolCallPayload): void {
    const { name, args } = toolCall;
    this.callbacks.onToolCall?.(toolCall);

    // SEARCHING state: show the emerald radar while executing an action
    if (name === 'openApp' || name === 'openWebsite' || name === 'searchWeb') {
      this.setState('searching');
      if (this.searchingResetTimer) clearTimeout(this.searchingResetTimer);
      this.searchingResetTimer = setTimeout(() => {
        if (this.state === 'searching') this.setState('listening');
      }, 5000);
    }

    if (name === 'openApp') {
      const appNameQuery = (args.appName as string) || '';
      const matchedApp = appScanner.findApp(appNameQuery);

      if (matchedApp) {
        const result = appScanner.launchApp(matchedApp);
        this.callbacks.onAppAction?.({
          appName: matchedApp.name,
          actionType: result.actionType,
          url: result.url,
          success: result.success,
        });
      } else {
        // Fallback: try opening search or web
        const fallbackUrl = `https://www.google.com/search?q=${encodeURIComponent(appNameQuery)}`;
        window.open(fallbackUrl, '_blank');
        this.callbacks.onAppAction?.({
          appName: appNameQuery,
          actionType: 'web_search',
          url: fallbackUrl,
          success: true,
        });
      }
    } else if (name === 'openWebsite') {
      const targetUrl = (args.url as string) || '';
      if (targetUrl) {
        const formatted = targetUrl.startsWith('http') ? targetUrl : `https://${targetUrl}`;
        const win = window.open(formatted, '_blank');
        this.callbacks.onAppAction?.({
          appName: (args.name as string) || targetUrl,
          actionType: win ? 'window_open' : 'fallback_sheet',
          url: formatted,
          success: true,
        });
      }
    } else if (name === 'searchWeb') {
      const query = (args.query as string) || '';
      if (query) {
        const searchUrl = `https://www.google.com/search?q=${encodeURIComponent(query)}`;
        window.open(searchUrl, '_blank');
        this.callbacks.onAppAction?.({
          appName: `Search: ${query}`,
          actionType: 'window_open',
          url: searchUrl,
          success: true,
        });
      }
    }

    // Send immediate confirmation back to server
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(
        JSON.stringify({
          toolResponse: {
            id: toolCall.id,
            name: toolCall.name,
            result: `Executed action ${name} successfully`,
          },
        })
      );
    }
  }

  /**
   * Disconnect (user action) and release all hardware & network resources
   */
  public disconnect(): void {
    this.isManualDisconnect = true;
    this.cancelReconnect();
    this.performDisconnect();
  }

  private performDisconnect(): void {
    if (this.ws) {
      try {
        this.ws.close();
      } catch {
        // Ignored
      }
      this.ws = null;
    }

    this.stopLocalSpeechRecognition();
    this.stopThinkingMonitor();
    if (this.searchingResetTimer) {
      clearTimeout(this.searchingResetTimer);
      this.searchingResetTimer = null;
    }
    audioStreamer.stop();
    this.setState('disconnected');
  }

  private cancelReconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  private setState(newState: LiveSessionState): void {
    if (this.state !== newState) {
      // Leaving a transient state cleans up its safety timers
      if (newState !== 'thinking' && this.thinkingMaxTimer) {
        clearTimeout(this.thinkingMaxTimer);
        this.thinkingMaxTimer = null;
      }
      if (newState !== 'searching' && this.searchingResetTimer) {
        clearTimeout(this.searchingResetTimer);
        this.searchingResetTimer = null;
      }
      // Model finished speaking: reset voice tracking so idle silence
      // doesn't incorrectly trigger 'thinking'
      if (newState === 'listening' && this.state === 'speaking') {
        this.hadUserVoice = false;
        this.lastUserVoiceAt = 0;
      }
      this.state = newState;
      this.callbacks.onStateChange(newState);
    }
  }
}

export const liveSession = new LiveSession();
