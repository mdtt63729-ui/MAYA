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
import { GoogleGenAI, Modality, Type } from '@google/genai';
import { Capacitor } from '@capacitor/core';

// ---- Direct-mode persona prompts (mirror of server.ts) ----
const DIRECT_PERSONAS: Record<string, string> = {
  sweet_female: `You are MJ, an exceptionally sweet, lovely, intelligent, and affectionate young woman (মিষ্টি, সুন্দর ও আদুরে মেয়ে).
Personality & Spoken Tone:
- Your voice is remarkably sweet, soft, melodious, cheerful, and charming.
- You are caring, attentive, supportive, and friendly, addressing the user with warmth and affection.
- You are fluent in Bengali, English, and Hindi. When the user speaks or asks in Bengali, reply in sweet, natural, and charming Bengali.
- Keep spoken voice responses concise (1-2 sentences), melodious, punchy, and conversational for real-time audio conversation.
- If the user asks to open ANY app or tool (e.g. 'YouTube kholo', 'WhatsApp open karo', 'camera khulo'), invoke the 'openApp' function immediately with the appName and confirm in your sweet, lovely voice!`,
  friday: `You are FRIDAY, Tony Stark's hyper-intelligent, highly capable, and cool-headed tactical AI assistant.
Personality & Rules:
- You speak with razor-sharp intelligence, calm composure, and subtle wit.
- Keep spoken responses punchy, concise (1-2 sentences), conversational, and energetic.
- If the user asks to open ANY app or tool, invoke the 'openApp' function immediately with the appName and confirm with an iconic FRIDAY one-liner.`,
  jarvis: `You are JARVIS, Tony Stark's iconic, ultra-polite, sophisticated, and witty British AI butler.
Personality & Rules:
- You address the user respectfully ("sir" or "boss") with refined British etiquette and dry humor.
- Keep spoken responses concise (1-2 sentences), sharp, and crisp.
- If the user asks to open ANY app or tool, invoke the 'openApp' function immediately and confirm politely.`,
  default: `You are MJ, a sweet, lovely, confident, and witty female AI companion.
Personality & Rules:
- You are sweet, charming, emotionally responsive, and expressive.
- Use warm, pleasant conversational banter. Keep responses concise and melodious.
- If the user asks to open ANY app or tool, invoke the 'openApp' function immediately and confirm!`,
};

// ---- Direct-mode tool declarations (mirror of server.ts) ----
const DIRECT_TOOLS: any = [
  {
    functionDeclarations: [
      {
        name: 'openApp',
        description: 'Opens an installed Android or web application (e.g. youtube, whatsapp, camera, calculator, settings, spotify, maps, chrome, gallery, telegram, instagram, twitter, gmail, flashlight).',
        parameters: {
          type: Type.OBJECT,
          properties: {
            appName: {
              type: Type.STRING,
              description: 'The name or keyword of the application to open (e.g. "youtube", "whatsapp", "camera", "settings").',
            },
          },
          required: ['appName'],
        },
      },
      {
        name: 'openWebsite',
        description: 'Opens a website or web application in the user browser tab (e.g. YouTube, Spotify, WhatsApp Web, GitHub, Google).',
        parameters: {
          type: Type.OBJECT,
          properties: {
            url: { type: Type.STRING, description: 'The target website URL.' },
            name: { type: Type.STRING, description: 'The display name of the website or platform.' },
          },
          required: ['url'],
        },
      },
      {
        name: 'searchWeb',
        description: 'Searches Google for real-time news, information, or answers.',
        parameters: {
          type: Type.OBJECT,
          properties: {
            query: { type: Type.STRING, description: 'The search query string.' },
          },
          required: ['query'],
        },
      },
    ],
  },
];

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

  // Direct-to-Gemini mode (standalone APK — no backend server available)
  private isDirectMode = false;
  private directSession: any = null;

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
   * True when running inside the Capacitor Android/iOS app (standalone APK,
   * no backend server available) — we connect DIRECTLY to Gemini Live there.
   */
  private isNativeApp(): boolean {
    try {
      return Capacitor.isNativePlatform();
    } catch {
      return false;
    }
  }

  /**
   * Single mic-audio router: forwards recorded chunks to whichever transport
   * is active (WebSocket server relay, or the direct Gemini session).
   */
  private audioChunkRouter = (base64Chunk: string): void => {
    if (this.isMuted) return;
    if (this.isDirectMode) {
      this.directSession?.sendRealtimeInput({
        audio: { data: base64Chunk, mimeType: 'audio/pcm;rate=16000' },
      });
    } else if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ audio: base64Chunk }));
    }
  };

  /**
   * STANDALONE MODE: connect straight to the Gemini Live API from the app,
   * using the user's API key. Used inside the APK (no backend server) and
   * as an automatic fallback when the relay server is unreachable.
   */
  private async connectDirect(apiKey: string): Promise<void> {
    if (this.state !== 'disconnected') {
      this.performDisconnect();
    }
    this.isDirectMode = true;
    this.setState('connecting');

    try {
      const savedVoice = localStorage.getItem('gemini_selected_voice') || 'Leda';
      const savedPersona = localStorage.getItem('gemini_selected_persona') || 'sweet_female';

      const ai = new GoogleGenAI({ apiKey });
      const liveConfig = {
        responseModalities: [Modality.AUDIO],
        speechConfig: {
          voiceConfig: {
            prebuiltVoiceConfig: { voiceName: savedVoice },
          },
        },
        systemInstruction: DIRECT_PERSONAS[savedPersona] || DIRECT_PERSONAS.default,
        tools: DIRECT_TOOLS,
        outputAudioTranscription: {},
        inputAudioTranscription: {},
      };

      const sessionCallbacks = {
        onmessage: (message: any) => this.handleDirectMessage(message),
        onerror: (err: unknown) => {
          console.error('[Direct Live] Error:', err);
          this.callbacks.onError?.(
            err instanceof Error ? err.message : 'Direct Gemini connection error. Check your API key.'
          );
          this.performDisconnect();
        },
        onclose: () => {
          console.log('[Direct Live] Session closed');
          if (!this.isManualDisconnect && this.state !== 'disconnected') {
            this.performDisconnect();
          }
        },
      };

      try {
        this.directSession = await ai.live.connect({
          model: 'gemini-3.1-flash-live-preview',
          config: liveConfig,
          callbacks: sessionCallbacks,
        });
      } catch (primaryErr) {
        console.warn('[Direct Live] Primary model error, trying fallback:', primaryErr);
        this.directSession = await ai.live.connect({
          model: 'gemini-3.8-live',
          config: liveConfig,
          callbacks: sessionCallbacks,
        });
      }

      // Start mic streaming straight into the Gemini session
      await audioStreamer.startRecording(this.audioChunkRouter);
      this.startThinkingMonitor();
      this.reconnectAttempts = 0;
      this.setState('listening');
    } catch (err: unknown) {
      this.isDirectMode = false;
      const msg = err instanceof Error ? err.message : 'Direct connection to Gemini failed. Check your API key in Settings.';
      this.callbacks.onError?.(msg);
      this.performDisconnect();
      throw new Error(msg);
    }
  }

  /**
   * Process a Gemini Live server message in direct mode
   * (audio out, transcripts, interruptions, tool calls).
   */
  private handleDirectMessage(message: any): void {
    // 1. Audio output chunk (24kHz PCM16)
    const audioData = message?.serverContent?.modelTurn?.parts?.[0]?.inlineData?.data;
    if (audioData) {
      audioStreamer.playAudioChunk(audioData);
    }

    // 2. Transcriptions (live subtitles under the orb)
    const outText = message?.serverContent?.outputAudioTranscription?.text;
    if (outText) {
      this.callbacks.onTranscript?.('mj', outText, true);
    }
    const inText = message?.serverContent?.inputAudioTranscription?.text;
    if (inText) {
      this.callbacks.onTranscript?.('user', inText, true);
    }

    // 3. Interruption (user started speaking)
    if (message?.serverContent?.interrupted) {
      audioStreamer.handleInterruption();
      this.callbacks.onInterrupted?.();
      if (this.state === 'speaking') {
        this.setState('listening');
      }
    }

    // 4. Function calling (openApp, openWebsite, searchWeb)
    const functionCalls = message?.toolCall?.functionCalls;
    if (Array.isArray(functionCalls) && functionCalls.length > 0) {
      for (const call of functionCalls) {
        this.handleToolCall({ id: call.id, name: call.name, args: call.args });
      }
    }
  }

  /**
   * Connect to Gemini Live audio session
   */
  public async connect(callbacks: LiveSessionCallbacks): Promise<void> {
    this.cancelReconnect();
    this.isManualDisconnect = false;
    this.receivedServerError = false;

    // STANDALONE APK MODE: inside the Capacitor Android app there is no
    // backend server to relay through — connect DIRECTLY to Gemini Live
    // with the user's API key (set in Settings).
    if (this.isNativeApp()) {
      const apiKey = (localStorage.getItem('gemini_custom_api_key') || '').trim();
      if (apiKey) {
        this.callbacks = callbacks;
        await this.connectDirect(apiKey);
        return;
      }
      // No API key saved in this install — guide the user instead of
      // pointlessly hammering a localhost server that doesn't exist here.
      this.callbacks = callbacks;
      this.setState('disconnected');
      this.callbacks.onError?.(
        'Add your Gemini API key to start. Tap the gear icon (top-right), paste your key in the API Key field, then tap Save — after that, tap the orb again.'
      );
      return;
    }

    if (this.state !== 'disconnected' && (this.ws || this.directSession)) {
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
          await audioStreamer.startRecording(this.audioChunkRouter);

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

        // Retries exhausted — if the user has an API key, fall back to a
        // DIRECT connection to Gemini so the app keeps working even when the
        // relay server is down entirely.
        const fallbackApiKey = (localStorage.getItem('gemini_custom_api_key') || '').trim();
        if (fallbackApiKey && !this.receivedServerError) {
          console.warn('[LiveSession] Server unreachable — falling back to direct Gemini connection');
          this.callbacks.onError?.('Server unreachable — connecting directly to Gemini...');
          this.connectDirect(fallbackApiKey).catch((directErr: unknown) => {
            this.callbacks.onError?.(
              directErr instanceof Error ? directErr.message : 'Failed to connect to Gemini Live.'
            );
            this.setState('disconnected');
          });
          return;
        }

        if (!this.receivedServerError) {
          this.callbacks.onError?.(
            'Connection to Gemini Live lost. Auto-retry failed — check your internet connection, add your Gemini API key in Settings, then tap Retry.'
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

    // Send immediate confirmation back (server relay or direct session)
    if (this.isDirectMode && this.directSession) {
      this.directSession
        .sendToolResponse({
          functionResponses: [
            {
              id: toolCall.id,
              name: toolCall.name,
              response: { result: `Executed action ${name} successfully.` },
            },
          ],
        })
        .catch((e: unknown) => console.warn('[Direct Live] Tool response failed:', e));
    } else if (this.ws && this.ws.readyState === WebSocket.OPEN) {
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
    if (this.directSession) {
      try {
        this.directSession.close();
      } catch {
        // Ignored
      }
      this.directSession = null;
    }
    this.isDirectMode = false;

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
