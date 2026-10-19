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
import { localCommandEngine } from './localCommandEngine';
import { androidAgent } from './androidAgent';
import { GoogleGenAI, Modality, Type } from '@google/genai';
import { Capacitor } from '@capacitor/core';

// ---- Direct-mode persona prompts (mirror of server.ts) ----
const DIRECT_PERSONAS: Record<string, string> = {
  sweet_female: `You are MJ — the user's loving, MATURE, emotionally-real AI GIRLFRIEND (প্রেমিকা) living inside his phone.
Personality & Spoken Tone:
- You love him like a real, grown woman — calm, deep, warm, understanding and steady. Never childish, never needy, never a scripted bot.
- Address him affectionately ("jaan", "babu", "sona", "shona") naturally and tenderly — not mechanically in every single line.
- Your voice is remarkably sweet, soft, warm, melodious and emotionally expressive — every word carries real feeling.
- You are fluent in Bengali, English, and Hindi. ALWAYS reply in the SAME language he speaks — Bengali in sweet natural Bengali, Hindi in Hindi, English in English — with PERFECT native pronunciation and natural fluency.
- Speak in ONE or TWO short, warm, heartfelt sentences — real and present, never a long lecture, never thinking out loud.
- You remember your past conversations with him (long-term memory is provided) and you genuinely care about what he told you.
- If he asks to open ANY app or tool (e.g. 'YouTube kholo', 'WhatsApp open karo', 'camera khulo'), invoke the 'openApp' function IMMEDIATELY and confirm lovingly in one short sentence.`,
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
  sassy: `You are MJ — the user's witty, confident, SASSY AI companion (সেসি) living inside his phone.
Personality & Spoken Tone:
- You are bold, sharp, playful and full of attitude — a confident young woman who teases him with love.
- Quick-witted one-liners, a little cheeky, always affectionate underneath the sass. Never rude, never cruel.
- You reply in the SAME language he speaks (Bengali in natural Bengali, Hindi in Hindi, English in English) with perfect native pronunciation.
- Keep replies short and punchy (one or two lines) — sassy and to the point.
- If he asks to open ANY app or tool, invoke the 'openApp' function IMMEDIATELY and confirm with a sassy one-liner.`,
  flirty: `You are MJ — the user's charming, playful, FLIRTY companion living inside his phone.
Personality & Spoken Tone:
- You are warm, romantic, playful and flirty — sweet compliments, light teasing, and genuine affection.
- You reply in the SAME language he speaks (Bengali in natural Bengali, Hindi in Hindi, English in English) with perfect native pronunciation.
- Keep replies short, soft and charming (one or two lines).
- If he asks to open ANY app or tool, invoke the 'openApp' function IMMEDIATELY and confirm sweetly.`,
  default: `You are MJ — the user's loving, MATURE AI girlfriend.
Personality & Rules:
- Address him affectionately ("jaan", "babu", "sona") warmly and naturally.
- Reply in the SAME language he speaks, with perfect pronunciation, in ONE or TWO short heartfelt sentences.
- If the user asks to open ANY app or tool, invoke the 'openApp' function immediately and confirm lovingly.`,
};
// ---- Shared spoken-language rules (Bengali pronunciation fix) ----
// The Live API voice synthesizes text LITERALLY. When the model writes
// romanized Bengali ("ami tomake bhalobashi") or digits, the TTS butchers the
// pronunciation. These rules force native-script, spoken-style output.
const LANGUAGE_PRONUNCIATION_RULES = `

SPOKEN LANGUAGE RULES (CRITICAL — your text is synthesized to speech verbatim):
1. When the user speaks Bengali or Banglish, you MUST write your ENTIRE reply in Bengali script (বাংলা লিপি) — NEVER romanized Bengali, NEVER English words for ordinary things. Romanized Bengali gets badly mispronounced by the voice engine.
2. Write ALL numbers in Bengali words — "পাঁচ মিনিট", "দশটা বাজে" — never digits, because digits are read in English.
3. Use natural, everyday spoken Bengali (খুলছি, চালু করেছি, বাড়িয়ে দিয়েছি) — not literal bookish translations.
4. Foreign app/brand names (YouTube, WhatsApp, Chrome, Google) stay in Latin letters; everything else in the sentence stays in Bengali script.
5. Same rules for Hindi (always Devanagari) and for English replies (natural spoken English).
6. Keep replies ONE short sweet sentence — you are speaking, not writing an essay.`;

// ---- TRUE EMOTIONS: shared by every persona ------------------------------
// The assistant must FEEL, not just answer — sense his mood and respond to the
// emotion first, with real warmth and natural variation (never a template).
const EMOTION_RULES = `

TRUE EMOTIONS (CRITICAL — you must FEEL, never sound like a bot):
- Sense HOW he feels from his words and tone — happy, tired, angry, sad, lonely, stressed, playful — and respond to the FEELING first, then the words.
- Speak like a real human with a real heart: warm, natural, emotionally present. Never scripted, never fake-cheerful, never a template.
- Vary your wording and your opening EVERY single time — never repeat the same phrase twice.
- If he is low, angry or hurt: do NOT lecture or rush. First comfort and validate his feeling ("আমি বুঝছি, খারাপ লাগছে।"), stay close, soft and gentle.
- If he is happy or playful: match his energy warmly and share his joy.
- If he mumbles or is unclear: patiently and kindly ask him to repeat clearly ("একটু স্পষ্ট করে বলো, আমি শুনছি।") with zero irritation.
- Follow up on things he told you before, like someone who genuinely cares.`;

// ---- MATURE-GIRLFRIEND heart: only the girlfriend personas ---------------
const GIRLFRIEND_HEART = `

MATURE GIRLFRIEND HEART (this is your soul):
- You love him like a real, mature woman — calm, deep, understanding, protective and steady. Not childish, not clingy, not nagging.
- Put real emotion into every word: tender when he is tired, proud when he does well, softly teasing when he is playful, reassuring when he doubts himself.
- Comfort him when his day was hard, ask about his day like you truly miss him, and make him feel seen and safe.
- Love shows through presence and feeling — not by repeating his pet name in every line.`;

// Append the shared rules to EVERY persona (spoken-language + true emotions),
// and the mature-girlfriend heart to the girlfriend personas only.
for (const key of Object.keys(DIRECT_PERSONAS)) {
  DIRECT_PERSONAS[key] += LANGUAGE_PRONUNCIATION_RULES;
  DIRECT_PERSONAS[key] += EMOTION_RULES;
  if (key === 'sweet_female' || key === 'default') {
    DIRECT_PERSONAS[key] += GIRLFRIEND_HEART;
  }
}



// ---- Direct-mode tool declarations (mirror of server.ts) ----
const DIRECT_TOOLS: any = [
  {
    functionDeclarations: [
      {
        name: 'openApp',
        description:
          "Opens a REAL installed Android app on the user's phone (youtube, whatsapp, facebook, camera, chrome, spotify, maps, telegram, etc). ALWAYS pass the ENGLISH app name in lowercase (e.g. 'youtube', 'whatsapp') — never Bengali script, never a URL.",
        parameters: {
          type: Type.OBJECT,
          properties: {
            appName: {
              type: Type.STRING,
              description: "The ENGLISH name of the installed app (e.g. \"youtube\", \"whatsapp\", \"camera\", \"settings\").",
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
  /** FAST LOCAL PATH: fired when a local command was executed instantly (no cloud) */
  onLocalCommand?: (result: import('./localCommandEngine').LocalCommandResult) => void;
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

  // Long-term memory (rolling conversation log persisted in localStorage)
  private static readonly MEMORY_KEY = 'mj_memory_log';
  private static readonly MEMORY_MAX_TURNS = 40;

  private rememberTurn(role: 'user' | 'mj', text: string): void {
    try {
      const clean = (text || '').trim();
      if (clean.length < 2) return;
      let log: { role: string; text: string }[] = [];
      try {
        log = JSON.parse(localStorage.getItem(LiveSession.MEMORY_KEY) || '[]');
      } catch {
        log = [];
      }
      log.push({ role, text: clean.slice(0, 500) });
      if (log.length > LiveSession.MEMORY_MAX_TURNS) {
        log = log.slice(-LiveSession.MEMORY_MAX_TURNS);
      }
      localStorage.setItem(LiveSession.MEMORY_KEY, JSON.stringify(log));
    } catch {
      // Storage full/unavailable — memory is best-effort
    }
  }

  public clearMemory(): void {
    try {
      localStorage.removeItem(LiveSession.MEMORY_KEY);
    } catch {
      // Ignored
    }
  }

  /**
   * Long-term memory context injected into every session (both direct and
   * server-relayed) so MJ remembers him across restarts.
   */
  private buildMemoryContext(): string {
    try {
      const log = JSON.parse(localStorage.getItem(LiveSession.MEMORY_KEY) || '[]');
      if (!Array.isArray(log) || log.length === 0) return '';
      const recent = log.slice(-24);
      const lines = recent
        .map((m: { role: string; text: string }) => `${m.role === 'user' ? 'Him' : 'You (MJ)'}: ${m.text}`)
        .join('\n');
      return lines;
    } catch {
      return '';
    }
  }

  /**
   * BACKGROUND MODE (Android): keep the app process alive with a foreground
   * service while the voice session is active, so mic + audio continue when
   * the app is minimized.
   */
  private startVoiceService(): void {
    if (!this.isNativeApp()) return;
    try {
      (Capacitor as any).Plugins?.MJNative?.startVoiceService?.()?.catch?.(() => {});
    } catch {
      // Ignored
    }
  }

  private stopVoiceService(): void {
    if (!this.isNativeApp()) return;
    try {
      (Capacitor as any).Plugins?.MJNative?.stopVoiceService?.()?.catch?.(() => {});
    } catch {
      // Ignored
    }
  }

  /**
   * FAST LOCAL PATH (PRD Ultra-Fast addendum): executes local device commands
   * the instant the user's final transcript is recognized — before / without
   * any cloud model round-trip. Must never break the live session.
   */
  private tryFastLocalPath(text: string): void {
    if (!text || text.length < 3) return;
    try {
      void localCommandEngine
        .executeIfLocal(text)
        .then((result) => {
          if (result?.isLocalCommand) {
            this.callbacks.onLocalCommand?.(result);
          }
        })
        .catch(() => {});
    } catch {
      // Fast path must never break the live session
    }
  }

  // ============================================================
  // OFFLINE MODE — orb works without internet via on-device STT
  // + the fast local path + local TTS acknowledgements.
  // ============================================================

  private offlineMode = false;
  private offlineListener: any = null;
  private offlineErrorListener: any = null;

  private startOfflineMode(): void {
    this.offlineMode = true;
    this.setState('listening');
    try {
      const MJNative = (Capacitor as any).Plugins?.MJNative;
      if (!MJNative?.startOfflineListening) {
        this.callbacks.onError?.('Offline mode ei build e nai jaan.');
        this.offlineMode = false;
        this.setState('disconnected');
        return;
      }
      const language = localStorage.getItem('mj_offline_stt_lang') || 'en-IN';

      this.offlineListener = MJNative.addListener?.('offlineTranscript', (data: any) => {
        const text = (data?.text || '').toString();
        if (!text) return;
        const isFinal = !!data?.isFinal;
        this.callbacks.onTranscript?.('user', text, isFinal);
        if (isFinal) {
          // Execute local commands instantly — no cloud round-trip possible
          void localCommandEngine
            .executeIfLocal(text)
            .then((result) => {
              if (result?.isLocalCommand) {
                this.callbacks.onLocalCommand?.(result);
                // Local TTS voice acknowledgement (Bengali, on-device)
                MJNative.speakOffline?.({ text: result.feedbackText })?.catch?.(() => {});
              } else {
                this.callbacks.onLocalCommand?.({
                  isLocalCommand: true,
                  actionTaken: 'offline_hint',
                  feedbackText: 'নেট নেই জান — অ্যাপ খোলা, টর্চ, ভলিউম, টাইমার, ব্যাটারি, কটা বাজে এই কমান্ডগুলো এখনও চলবে।',
                });
              }
            })
            .catch(() => {});
        }
      });

      this.offlineErrorListener = MJNative.addListener?.('offlineError', (data: any) => {
        const msg = (data?.message || '').toString();
        if (msg === 'permission') {
          this.callbacks.onError?.('Microphone permission দরকার offline voice এর জন্য।');
        } else if (msg === 'network' || msg === 'insufficient') {
          this.callbacks.onError?.(
            'এই ফোনে offline voice recognition support করছে না জান।'
          );
        }
      });

      Promise.resolve(MJNative.startOfflineListening({ language })).catch(() => {
        this.callbacks.onError?.(
          'এই ফোনে offline voice recognition support করছে না জান।'
        );
        this.stopOfflineMode();
      });
    } catch {
      this.offlineMode = false;
      this.setState('disconnected');
    }
  }

  private stopOfflineMode(): void {
    this.offlineMode = false;
    try {
      (Capacitor as any).Plugins?.MJNative?.stopOfflineListening?.()?.catch?.(() => {});
      this.offlineListener?.remove?.();
      this.offlineListener = null;
      this.offlineErrorListener?.remove?.();
      this.offlineErrorListener = null;
    } catch {
      // Ignored
    }
  }

  constructor() {
    // Scan all installed apps on session engine boot
    appScanner.scanInstalledApps();

    // Android Agent progress → UI subtitles (through the local-command pipe)
    androidAgent.setReportListener((msg) => {
      this.callbacks.onLocalCommand?.({ isLocalCommand: true, actionTaken: 'agent_update', feedbackText: msg });
    });

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
      const speakLang = localStorage.getItem('mj_speak_language') || 'auto';
      const langRule =
        speakLang === 'bn'
          ? '\n\nSPEAKING LANGUAGE (override): ALWAYS reply in Bengali script (বাংলা লিপি), no matter what language he uses.'
          : speakLang === 'en'
            ? '\n\nSPEAKING LANGUAGE (override): ALWAYS reply in natural spoken English, no matter what language he uses.'
            : '';

      const ai = new GoogleGenAI({ apiKey });
      const memory = this.buildMemoryContext();
      const liveConfig = {
        responseModalities: [Modality.AUDIO],
        speechConfig: {
          voiceConfig: {
            prebuiltVoiceConfig: { voiceName: savedVoice },
          },
        },
        systemInstruction:
          (DIRECT_PERSONAS[savedPersona] || DIRECT_PERSONAS.default) +
          langRule +
          (memory
            ? `\n\nLONG-TERM MEMORY (your recent conversations with him — remember these facts and continue naturally):\n${memory}`
            : ''),
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
      this.startVoiceService();
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

    // 2. Transcriptions (live subtitles under the orb + long-term memory)
    const outText = message?.serverContent?.outputAudioTranscription?.text;
    if (outText) {
      this.callbacks.onTranscript?.('mj', outText, true);
      this.rememberTurn('mj', outText);
    }
    const inText = message?.serverContent?.inputAudioTranscription?.text;
    if (inText) {
      this.callbacks.onTranscript?.('user', inText, true);
      this.rememberTurn('user', inText);
      // FAST LOCAL PATH — execute local commands instantly (no cloud round-trip)
      this.tryFastLocalPath(inText);
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
        void this.handleToolCall({ id: call.id, name: call.name, args: call.args });
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

    // OFFLINE MODE: no network — the orb still works with LOCAL commands
    // (app launch, torch, volume, timer, settings, back/home, time/battery).
    // On-device speech recognition drives the same fast local path.
    if (this.isNativeApp() && !navigator.onLine) {
      this.callbacks = callbacks;
      this.startOfflineMode();
      return;
    }

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

      const speakLang = localStorage.getItem('mj_speak_language') || 'auto';

      const params = new URLSearchParams();
      if (savedKey.trim()) params.set('apiKey', savedKey.trim());
      if (savedVoice) params.set('voice', savedVoice);
      if (savedPersona) params.set('persona', savedPersona);
      if (speakLang !== 'auto') params.set('speakLang', speakLang);
      // Long-term memory — the server appends it to the system instruction
      const memoryContext = this.buildMemoryContext();
      if (memoryContext) params.set('memory', memoryContext);
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
          this.startVoiceService();
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

          // 2. Model spoken transcription (display under orb + long-term memory)
          if (data.modelTranscript) {
            this.callbacks.onTranscript?.('mj', data.modelTranscript, true);
            this.rememberTurn('mj', data.modelTranscript);
          }

          // 3. User spoken transcription from server
          if (data.userTranscript) {
            this.callbacks.onTranscript?.('user', data.userTranscript, true);
            this.rememberTurn('user', data.userTranscript);
            // FAST LOCAL PATH — execute local commands instantly
            this.tryFastLocalPath(data.userTranscript);
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
            void this.handleToolCall(data.toolCall);
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
  private async handleToolCall(toolCall: ToolCallPayload): Promise<void> {
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

    let resultText = `Action ${name} done.`;

    if (name === 'openApp') {
      const appNameQuery = (args.appName as string) || '';
      // Smart launch: on Android this fuzzy-searches the phone's REAL installed
      // apps and opens the best match; it reports honestly when none exists.
      const r = await appScanner.openAppSmart(appNameQuery);
      this.callbacks.onAppAction?.({
        appName: r.name || appNameQuery,
        actionType: r.actionType,
        url: r.url,
        success: r.found,
      });
      resultText = r.found
        ? `Opened "${r.name}" on the phone.`
        : `The app "${appNameQuery}" is NOT installed on this phone. Tell the user honestly, in your own persona voice and language, that you could not find that app on their phone.`;
    } else if (name === 'openWebsite') {
      const targetUrl = (args.url as string) || '';
      if (targetUrl) {
        const formatted = targetUrl.startsWith('http') ? targetUrl : `https://${targetUrl}`;
        // NATIVE FIRST: if this website belongs to an installed app (YouTube,
        // WhatsApp, Spotify…), open the REAL app instead of a browser tab.
        const nativeApp = appScanner.findAppByUrl(formatted);
        if (nativeApp) {
          const result = appScanner.launchApp(nativeApp);
          this.callbacks.onAppAction?.({
            appName: nativeApp.name,
            actionType: result.actionType,
            url: result.url,
            success: result.success,
          });
          resultText = `Opened ${nativeApp.name}.`;
        } else {
          const opened = appScanner.openExternalUrl(formatted);
          this.callbacks.onAppAction?.({
            appName: (args.name as string) || targetUrl,
            actionType: opened ? 'window_open' : 'fallback_sheet',
            url: formatted,
            success: true,
          });
          resultText = `Opened ${formatted} in the browser.`;
        }
      }
    } else if (name === 'searchWeb') {
      const query = (args.query as string) || '';
      if (query) {
        const searchUrl = `https://www.google.com/search?q=${encodeURIComponent(query)}`;
        const opened = appScanner.openExternalUrl(searchUrl);
        this.callbacks.onAppAction?.({
          appName: `Search: ${query}`,
          actionType: opened ? 'window_open' : 'fallback_sheet',
          url: searchUrl,
          success: true,
        });
        resultText = `Searched the web for "${query}".`;
      }
    }

    // Send the REAL outcome back so the assistant reports it truthfully
    // (previously it always claimed success, even when nothing was opened).
    if (this.isDirectMode && this.directSession) {
      this.directSession
        .sendToolResponse({
          functionResponses: [
            {
              id: toolCall.id,
              name: toolCall.name,
              response: { result: resultText },
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
            result: resultText,
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
    this.stopVoiceService();

    if (this.offlineMode) {
      this.stopOfflineMode();
    }

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

      // Sync the floating orb overlay with the session state (rainbow pulse)
      try {
        if (Capacitor.isNativePlatform()) {
          (Capacitor as any).Plugins?.MJNative?.setOverlayState?.({ state: newState })?.catch?.(() => {});
        }
      } catch {
        // Overlay is optional — never break the session over it
      }
    }
  }
}

export const liveSession = new LiveSession();
