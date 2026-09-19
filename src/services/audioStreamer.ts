/**
 * AudioStreamer
 * Manages raw audio streaming:
 * - 16kHz PCM16 microphone recording via AudioContext & ScriptProcessor
 * - 24kHz Web Audio API playback with gapless scheduling
 * - AnalyserNodes for real-time waveform & frequency visualization
 * - Instant interruption handling (clearing queue & stopping active sources)
 */

export class AudioStreamer {
  private inputAudioCtx: AudioContext | null = null;
  private outputAudioCtx: AudioContext | null = null;
  private mediaStream: MediaStream | null = null;
  private scriptProcessor: ScriptProcessorNode | null = null;
  private micSource: MediaStreamAudioSourceNode | null = null;

  // Analysers for visualization
  private inputAnalyser: AnalyserNode | null = null;
  private outputAnalyser: AnalyserNode | null = null;

  // Output playback scheduling
  private nextStartTime = 0;
  private activeSources: AudioBufferSourceNode[] = [];
  private isSpeaking = false;
  private watchdogTimer: ReturnType<typeof setInterval> | null = null;

  // Mic health & auto-recovery (fixes mic dying after 1-2 minutes on Android)
  private micFailCount = 0;
  private isRestartingMic = false;
  private visibilityHandler: (() => void) | null = null;

  private onAudioDataCallback?: (base64Chunk: string) => void;
  private onSpeakingStateChange?: (speaking: boolean) => void;

  /**
   * Initialize audio contexts and input capture (16kHz PCM16)
   */
  public async startRecording(onAudioChunk: (base64Chunk: string) => void): Promise<void> {
    this.onAudioDataCallback = onAudioChunk;
    await this.initializeInput();

    // Setup Output Context at 24kHz
    if (!this.outputAudioCtx) {
      const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      this.outputAudioCtx = new AudioContextClass({ sampleRate: 24000 });
    }
    if (this.outputAudioCtx.state === 'suspended') {
      await this.outputAudioCtx.resume();
    }

    // Output Analyser
    this.outputAnalyser = this.outputAudioCtx.createAnalyser();
    this.outputAnalyser.fftSize = 256;
    this.outputAnalyser.smoothingTimeConstant = 0.8;
    this.outputAnalyser.connect(this.outputAudioCtx.destination);

    this.startWatchdog();
  }

  /**
   * (Re-)acquire the microphone and rebuild the capture graph.
   * Called on start and transparently whenever the OS silently kills the mic
   * track (the cause of the mic "auto off after 1-2 minutes" bug on Android,
   * where the WebView / OS re-routes audio and the MediaStream track goes
   * muted or ended). The WebSocket session is NOT touched — capture resumes
   * seamlessly through the same audio callback.
   */
  private async initializeInput(): Promise<void> {
    this.teardownInputNodes();

    // 1. Setup Input Context at 16kHz
    const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    this.inputAudioCtx = new AudioContextClass({ sampleRate: 16000 });
    if (this.inputAudioCtx.state === 'suspended') {
      await this.inputAudioCtx.resume();
    }

    // 2. Acquire mic media stream
    this.mediaStream = await navigator.mediaDevices.getUserMedia({
      audio: {
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
        channelCount: 1,
        sampleRate: 16000,
      },
    });

    // 3. Connect mic to analyser & processor
    this.micSource = this.inputAudioCtx.createMediaStreamSource(this.mediaStream);
    this.inputAnalyser = this.inputAudioCtx.createAnalyser();
    this.inputAnalyser.fftSize = 256;
    this.inputAnalyser.smoothingTimeConstant = 0.8;
    this.micSource.connect(this.inputAnalyser);

    // Buffer size 2048 or 4096 (approx 128-256ms at 16kHz)
    this.scriptProcessor = this.inputAudioCtx.createScriptProcessor(2048, 1, 1);
    this.inputAnalyser.connect(this.scriptProcessor);

    // Connect to destination to keep processor pumping
    const silentGain = this.inputAudioCtx.createGain();
    silentGain.gain.value = 0;
    this.scriptProcessor.connect(silentGain);
    silentGain.connect(this.inputAudioCtx.destination);

    this.scriptProcessor.onaudioprocess = (e) => {
      if (!this.onAudioDataCallback) return;
      const inputChannel = e.inputBuffer.getChannelData(0);
      const pcm16 = this.floatTo16BitPCM(inputChannel);
      const base64 = this.pcm16ToBase64(pcm16);
      this.onAudioDataCallback(base64);
    };
  }

  /**
   * Release only the input (mic) side of the graph — output playback untouched.
   */
  private teardownInputNodes(): void {
    if (this.scriptProcessor) {
      this.scriptProcessor.disconnect();
      this.scriptProcessor.onaudioprocess = null;
      this.scriptProcessor = null;
    }

    if (this.micSource) {
      this.micSource.disconnect();
      this.micSource = null;
    }

    if (this.mediaStream) {
      this.mediaStream.getTracks().forEach((track) => track.stop());
      this.mediaStream = null;
    }

    if (this.inputAudioCtx && this.inputAudioCtx.state !== 'closed') {
      this.inputAudioCtx.close().catch(() => {});
    }
    this.inputAudioCtx = null;
  }

  private startWatchdog(): void {
    this.stopWatchdog();

    // Health monitor: resume suspended contexts, resurrect a dead mic track.
    this.watchdogTimer = setInterval(() => {
      if (this.inputAudioCtx && this.inputAudioCtx.state === 'suspended') {
        this.inputAudioCtx.resume().catch(() => {});
      }
      if (this.outputAudioCtx && this.outputAudioCtx.state === 'suspended') {
        this.outputAudioCtx.resume().catch(() => {});
      }
      this.checkMicHealth();
    }, 2500);

    // When the page/app becomes visible again, immediately unfreeze audio.
    this.visibilityHandler = () => {
      if (document.visibilityState !== 'visible') return;
      if (this.inputAudioCtx && this.inputAudioCtx.state === 'suspended') {
        this.inputAudioCtx.resume().catch(() => {});
      }
      if (this.outputAudioCtx && this.outputAudioCtx.state === 'suspended') {
        this.outputAudioCtx.resume().catch(() => {});
      }
      this.checkMicHealth();
    };
    document.addEventListener('visibilitychange', this.visibilityHandler);
  }

  /**
   * Detect a mic that the OS/WebView silently killed (track ended or muted)
   * and transparently re-acquire it. Two consecutive failures (~5s) are
   * required before restarting, to avoid reacting to transient blips.
   */
  private checkMicHealth(): void {
    if (!this.onAudioDataCallback || this.isRestartingMic) return; // not recording

    const track = this.mediaStream?.getTracks()[0];
    const micAlive = !!track && track.readyState === 'live' && !track.muted;

    if (micAlive) {
      this.micFailCount = 0;
      return;
    }

    this.micFailCount++;
    if (this.micFailCount >= 2) {
      this.micFailCount = 0;
      this.isRestartingMic = true;
      console.warn('[AudioStreamer] Microphone lost — auto re-acquiring capture...');
      this.initializeInput()
        .catch((e) => console.warn('[AudioStreamer] Mic auto-recovery failed:', e))
        .finally(() => {
          this.isRestartingMic = false;
        });
    }
  }

  private stopWatchdog(): void {
    if (this.watchdogTimer) {
      clearInterval(this.watchdogTimer);
      this.watchdogTimer = null;
    }
    if (this.visibilityHandler) {
      document.removeEventListener('visibilitychange', this.visibilityHandler);
      this.visibilityHandler = null;
    }
    this.micFailCount = 0;
    this.isRestartingMic = false;
  }

  /**
   * Schedule incoming 24kHz PCM16 audio chunk for playback
   */
  public playAudioChunk(base64Chunk: string): void {
    if (!this.outputAudioCtx || !this.outputAnalyser) {
      const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      this.outputAudioCtx = new AudioContextClass({ sampleRate: 24000 });
      this.outputAnalyser = this.outputAudioCtx.createAnalyser();
      this.outputAnalyser.fftSize = 256;
      this.outputAnalyser.connect(this.outputAudioCtx.destination);
    }

    if (this.outputAudioCtx.state === 'suspended') {
      this.outputAudioCtx.resume();
    }

    const float32Samples = this.base64ToFloat32(base64Chunk);
    if (float32Samples.length === 0) return;

    const audioBuffer = this.outputAudioCtx.createBuffer(1, float32Samples.length, 24000);
    audioBuffer.getChannelData(0).set(float32Samples);

    const source = this.outputAudioCtx.createBufferSource();
    source.buffer = audioBuffer;
    source.connect(this.outputAnalyser);

    // Gapless scheduling
    const currentTime = this.outputAudioCtx.currentTime;
    const startTime = Math.max(currentTime, this.nextStartTime);
    source.start(startTime);
    this.nextStartTime = startTime + audioBuffer.duration;

    this.activeSources.push(source);
    this.setSpeaking(true);

    source.onended = () => {
      const idx = this.activeSources.indexOf(source);
      if (idx !== -1) {
        this.activeSources.splice(idx, 1);
      }
      if (this.activeSources.length === 0 && this.outputAudioCtx && this.outputAudioCtx.currentTime >= this.nextStartTime - 0.05) {
        this.setSpeaking(false);
      }
    };
  }

  /**
   * Handle model interruption: stop all playing nodes immediately and reset timeline
   */
  public handleInterruption(): void {
    for (const src of this.activeSources) {
      try {
        src.stop();
        src.disconnect();
      } catch {
        // Ignored if already stopped
      }
    }
    this.activeSources = [];
    if (this.outputAudioCtx) {
      this.nextStartTime = this.outputAudioCtx.currentTime;
    } else {
      this.nextStartTime = 0;
    }
    this.setSpeaking(false);
  }

  /**
   * Subscribe to model speaking state change
   */
  public onSpeakingChange(cb: (speaking: boolean) => void): () => void {
    this.onSpeakingStateChange = cb;
    return () => {
      if (this.onSpeakingStateChange === cb) {
        this.onSpeakingStateChange = undefined;
      }
    };
  }

  private setSpeaking(speaking: boolean): void {
    if (this.isSpeaking !== speaking) {
      this.isSpeaking = speaking;
      this.onSpeakingStateChange?.(speaking);
    }
  }

  /**
   * Retrieve input frequency/amplitude data for UI waveforms
   */
  public getInputVolume(): number {
    if (!this.inputAnalyser) return 0;
    const data = new Uint8Array(this.inputAnalyser.frequencyBinCount);
    this.inputAnalyser.getByteFrequencyData(data);
    let sum = 0;
    for (let i = 0; i < data.length; i++) {
      sum += data[i];
    }
    return sum / (data.length * 255);
  }

  /**
   * Retrieve output frequency/amplitude data for UI waveforms
   */
  public getOutputVolume(): number {
    if (!this.outputAnalyser) return 0;
    const data = new Uint8Array(this.outputAnalyser.frequencyBinCount);
    this.outputAnalyser.getByteFrequencyData(data);
    let sum = 0;
    for (let i = 0; i < data.length; i++) {
      sum += data[i];
    }
    return sum / (data.length * 255);
  }

  /**
   * Get raw byte frequency array for detailed waveform rendering
   */
  public getSpectrum(type: 'input' | 'output'): Uint8Array {
    const analyser = type === 'input' ? this.inputAnalyser : this.outputAnalyser;
    if (!analyser) return new Uint8Array(32);
    const data = new Uint8Array(analyser.frequencyBinCount);
    analyser.getByteFrequencyData(data);
    return data;
  }

  /**
   * Cleanly stop recording and playback
   */
  public stop(): void {
    this.handleInterruption();
    this.teardownInputNodes();
    this.stopWatchdog();
    this.onAudioDataCallback = undefined;
    this.setSpeaking(false);
  }

  // --- Binary Conversion Helpers ---

  private floatTo16BitPCM(input: Float32Array): Int16Array {
    const output = new Int16Array(input.length);
    for (let i = 0; i < input.length; i++) {
      const s = Math.max(-1, Math.min(1, input[i]));
      output[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
    }
    return output;
  }

  private pcm16ToBase64(pcm16: Int16Array): string {
    const bytes = new Uint8Array(pcm16.buffer);
    let binary = '';
    const len = bytes.byteLength;
    for (let i = 0; i < len; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
  }

  private base64ToFloat32(base64: string): Float32Array {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    const int16 = new Int16Array(bytes.buffer);
    const float32 = new Float32Array(int16.length);
    for (let i = 0; i < int16.length; i++) {
      float32[i] = int16[i] / 32768.0;
    }
    return float32;
  }
}

export const audioStreamer = new AudioStreamer();
