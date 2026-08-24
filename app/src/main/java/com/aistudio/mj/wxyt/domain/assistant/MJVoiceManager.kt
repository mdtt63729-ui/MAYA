package com.aistudio.mj.wxyt.domain.assistant

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.aistudio.mj.wxyt.domain.ai.AIProvider
import com.aistudio.mj.wxyt.domain.ai.AIRequest
import com.aistudio.mj.wxyt.domain.ai.AIOrchestrator
import com.aistudio.mj.wxyt.domain.ai.GeminiProvider
import com.aistudio.mj.wxyt.domain.ai.OpenAICompatibleProvider
import com.aistudio.mj.wxyt.domain.ai.ProviderEndpointRegistry
import com.aistudio.mj.wxyt.domain.security.SecureCredentialRepository
import com.aistudio.mj.wxyt.domain.security.VoiceSecurityManager
import com.aistudio.mj.wxyt.domain.security.SecurityState
import com.aistudio.mj.wxyt.domain.settings.SettingsRepository
import com.aistudio.mj.wxyt.domain.settings.IndianLanguages
import com.aistudio.mj.wxyt.domain.command.VoiceCommandEngine
import com.aistudio.mj.wxyt.domain.tools.ToolExecutionEngine
import com.aistudio.mj.wxyt.domain.chat.AppDatabase
import com.aistudio.mj.wxyt.domain.chat.ConversationEntity
import com.aistudio.mj.wxyt.domain.chat.MessageEntity
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.util.Locale
import android.icu.text.Transliterator

enum class MJState {
    DISCONNECTED, IDLE, WAKE_WORD_LISTENING, ACTIVATING, CONNECTING, LISTENING, THINKING, SPEAKING, ERROR
}

class MJVoiceManager private constructor(private val context: Context) : RecognitionListener, TextToSpeech.OnInitListener {

    private val _state = MutableStateFlow(MJState.DISCONNECTED)
    val state: StateFlow<MJState> = _state.asStateFlow()

    private val _rmsValue = MutableStateFlow(0f)
    val rmsValue: StateFlow<Float> = _rmsValue.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Live subtitle text — streamed to the typewriter overlay below the orb.
    // Updated whenever speak() is called, cleared when speaking finishes.
    private val _currentSpokenText = MutableStateFlow("")
    val currentSpokenText: StateFlow<String> = _currentSpokenText.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val client = com.aistudio.mj.wxyt.domain.ai.ApiClientProvider.client

    private val secureRepo = SecureCredentialRepository(context)
    private val settingsRepo = SettingsRepository.get(context)
    private val dao = AppDatabase.getDatabase(context).chatDao()
    private val memoryRepository = com.aistudio.mj.wxyt.domain.chat.LongTermMemoryRepository(context)
    private var currentConversationId: String? = null

    // Use AIProviderFactory for centralized provider creation
    private val providerFactory = com.aistudio.mj.wxyt.domain.ai.AIProviderFactory(context)
    private val providers: Map<String, AIProvider> = providerFactory.createAll()

    // Voice is intentionally isolated to Google Gemini. Text chat uses its own
    // non-Gemini routing layer, so changing Chat AI can never silently change
    // MAYA's voice provider.
    private val voiceProviders: Map<String, AIProvider> =
        providers["gemini"]?.let { mapOf("gemini" to it) } ?: emptyMap()
    private val voiceAiOrchestrator = AIOrchestrator(settingsRepo, secureRepo, voiceProviders)
    private val commandEngine = VoiceCommandEngine(voiceAiOrchestrator)
    private val androidExecutor = com.aistudio.mj.wxyt.domain.command.AndroidCommandExecutor(context)
    private val toolExecutionEngine = ToolExecutionEngine(context)
    val securityManager = VoiceSecurityManager(context)
    val voiceReactiveController = VoiceReactiveController()
    val voiceReactiveState get() = voiceReactiveController.state
    
    private var ttsRmsJob: Job? = null
    private var isTtsInitialized = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isProcessing = false
    
    private var voiceSessionActive = false
    private var isWakeWordMode = false
    private var listenJob: Job? = null
    private var recognizerGeneration = 0L
    private var recognizerStarting = false
    private var lastRecognizerStartAt = 0L
    private var wakeActivationInProgress = false
    private var lastWakeMatchAt = 0L
    private var wakeRestartJob: Job? = null

    // Live Engine
    private var liveRepository: GeminiLiveRepository? = null
    private var audioInput: AudioInputManager? = null
    private var audioOutput: AudioOutputManager? = null
    private var liveMicJob: Job? = null
    private var liveReceiveJob: Job? = null
    private var liveTurnJob: Job? = null
    private var liveConnectionJob: Job? = null
    private var liveErrorJob: Job? = null
    private var ownerVerificationBuffer = java.io.ByteArrayOutputStream()
    private var ownerVerificationStartedAt = 0L
    private var ownerVerificationResult: Boolean? = null

    // Local barge-in guard: clears already-buffered output immediately while
    // Gemini's server-side VAD simultaneously cancels the current generation.
    private var bargeInChunks = 0
    private var lastBargeInAt = 0L

    companion object {
        @Volatile
        private var instance: MJVoiceManager? = null

        fun getInstance(context: Context): MJVoiceManager {
            return instance ?: synchronized(this) {
                instance ?: MJVoiceManager(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        initTTS()
        // Wire state changes to the voice-reactive controller
        scope.launch {
            _state.collect { state ->
                voiceReactiveController.onAssistantStateChanged(state)
            }
        }
        scope.launch {
            settingsRepo.settings.collect { settings ->
                // Apply settings that can be changed without touching the active microphone.
                applyRuntimeVoiceSettings(settings)
                audioOutput?.setVolume(settings.voiceOutputVolume)
            }
        }
    }

    private fun applyRuntimeVoiceSettings(settings: com.aistudio.mj.wxyt.domain.settings.MJSettings) {
        try {
            val voiceName = settings.activeVoice
            val basePitch = when (voiceName) {
                "Natural Female" -> 1.0f
                "Warm Female" -> 0.9f
                "Soft Female" -> 1.1f
                "Calm Female" -> 0.8f
                "Bright Female" -> 1.2f
                "Friendly Female" -> 1.1f
                "Professional Female" -> 1.0f
                "Energetic Female" -> 1.3f
                else -> 1.0f
            }
            textToSpeech?.setPitch((basePitch * settings.voicePitch.coerceIn(0.5f, 1.5f)).coerceIn(0.5f, 2f))
            textToSpeech?.setSpeechRate(settings.speakingSpeed.coerceIn(0.5f, 2f))
            applyTtsLanguage()
        } catch (e: Exception) {
            Log.w("MJVoiceManager", "Unable to apply live voice settings", e)
        }
    }

    private fun initTTS() {
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(context, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            applyTtsLanguage()
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    scope.launch(Dispatchers.Main) {
                        _state.value = MJState.SPEAKING
                        startSimulatingTtsRms()
                    }
                }

                override fun onDone(utteranceId: String?) {
                    scope.launch(Dispatchers.Main) {
                        stopSimulatingTtsRms()
                        abandonAudioFocus()
                        _currentSpokenText.value = ""
                        isProcessing = false
                        if (_state.value != MJState.DISCONNECTED) {
                            if (voiceSessionActive && liveRepository != null) {
                                _state.value = MJState.LISTENING
                            } else {
                                startListening()
                            }
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    scope.launch(Dispatchers.Main) {
                        stopSimulatingTtsRms()
                        abandonAudioFocus()
                        _currentSpokenText.value = ""
                        isProcessing = false
                        if (_state.value != MJState.DISCONNECTED) {
                            _state.value = MJState.ERROR
                            delay(1000)
                            if (_state.value != MJState.DISCONNECTED) {
                                if (voiceSessionActive && liveRepository != null) {
                                    _state.value = MJState.LISTENING
                                } else {
                                    startListening()
                                }
                            }
                        }
                    }
                }
            })
        } else {
            isTtsInitialized = false
            Log.e("MJVoiceManager", "TTS Initialization failed")
        }
    }

    private fun applyTtsLanguage() {
        val selected = IndianLanguages.findByName(settingsRepo.settings.value.responseLanguage)
        val locale = Locale.forLanguageTag(selected.localeTag)
        val result = textToSpeech?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Keep the selected language in the AI prompt even if the device's
            // local TTS engine lacks that voice; Gemini Live remains the primary
            // voice path.
            Log.w("MJVoiceManager", "Local TTS does not support ${selected.localeTag}")
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { }
                .build()
            audioFocusRequest = request
            val result = audioManager.requestAudioFocus(request)
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    private fun startSimulatingTtsRms() {
        ttsRmsJob?.cancel()
        ttsRmsJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                _rmsValue.value = 3f + (Math.random().toFloat() * 7f)
                delay(100)
            }
        }
    }

    private fun stopSimulatingTtsRms() {
        ttsRmsJob?.cancel()
        _rmsValue.value = 0f
    }

    fun startSession() {
        // Idempotent activation: repeated orb taps/settings recompositions must not
        // create multiple AudioRecord/WebSocket sessions.
        if (voiceSessionActive && liveRepository != null &&
            _state.value in setOf(MJState.CONNECTING, MJState.LISTENING, MJState.THINKING, MJState.SPEAKING)) {
            return
        }
        _errorMessage.value = null
        isWakeWordMode = false
        wakeActivationInProgress = true
        // Parent Mode is fail-closed until a real speaker-biometric engine has
        // enrolled and verified the owner. SpeechRecognizer text alone is not
        // sufficient to prove speaker identity.
        val settings = settingsRepo.settings.value
        if (settings.parentMode && settings.ownerVoiceEnrollmentRequired && !securityManager.canAuthorizeOwner()) {
            val warning = "Parent Mode is enabled, but a secure owner voice biometric engine is not enrolled and ready."
            _errorMessage.value = warning
            wakeActivationInProgress = false
            securityManager.onWarningAudioOut?.invoke(warning)
            _state.value = MJState.ERROR
            return
        }
        securityManager.onWakeWordDetected()
        // Live Engine activation
        voiceSessionActive = true
        isWakeWordMode = false
        wakeActivationInProgress = true
        // Give the UI an immediate activation state so the perimeter lighting
        // responds to the wake word before the Gemini socket handshake begins.
        _state.value = MJState.ACTIVATING
        scope.launch(Dispatchers.Main.immediate) {
            delay(120L)
            if (voiceSessionActive && _state.value == MJState.ACTIVATING) {
                _state.value = MJState.CONNECTING
            }
        }
        
        listenJob?.cancel()
        recognizerGeneration++
        recognizerStarting = false
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
        try { speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null

        startLiveSession()
    }

    fun stopSession() {
        securityManager.endSession()
        voiceSessionActive = false
        isProcessing = false
        wakeActivationInProgress = false
        wakeRestartJob?.cancel()
        stopSimulatingTtsRms()
        abandonAudioFocus()
        
        stopLiveSession()
        
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.e("MJVoiceManager", "Error stopping TTS", e)
        }
        
        // Stopping a foreground conversation must actually release the microphone.
        // Wake-word listening is an explicit background mode and is started only by
        // the background assistant controller, never as a side effect of Stop.
        isWakeWordMode = false
        _state.value = MJState.IDLE
        listenJob?.cancel()
        recognizerGeneration++
        recognizerStarting = false
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
        val recognizerToDestroy = speechRecognizer
        speechRecognizer = null
        scope.launch(Dispatchers.Main) {
            try {
                recognizerToDestroy?.destroy()
            } catch (e: Exception) {
                Log.e("MJVoiceManager", "Error stopping recognizer", e)
            }
        }
    }

    fun startWakeWordListening() {
        val settings = settingsRepo.settings.value
        if (!settings.wakeWordEnabled || voiceSessionActive || !hasRecordAudioPermission()) {
            if (!settings.wakeWordEnabled) _state.value = MJState.IDLE
            return
        }
        if (isWakeWordMode && speechRecognizer != null && _state.value == MJState.WAKE_WORD_LISTENING) return
        voiceSessionActive = false
        isWakeWordMode = true
        wakeActivationInProgress = false
        startListeningInternal(isWakeWord = true)
    }

    fun stopWakeWordListening() {
        if (!isWakeWordMode && speechRecognizer == null) return
        isWakeWordMode = false
        wakeActivationInProgress = false
        wakeRestartJob?.cancel()
        recognizerGeneration++
        listenJob?.cancel()
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
        try { speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null
        _state.value = MJState.IDLE
    }

    fun restartWakeWordListening() {
        if (!settingsRepo.settings.value.wakeWordEnabled || voiceSessionActive || !hasRecordAudioPermission()) return
        wakeRestartJob?.cancel()
        recognizerGeneration++
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
        try { speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null
        recognizerStarting = false
        isWakeWordMode = true
        wakeActivationInProgress = false
        scheduleWakeRecognizerRestart(180L)
    }

    private fun hasRecordAudioPermission(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun normalizedWakeText(value: String): String {
        val transliterated = try {
            Transliterator.getInstance("Any-Latin; Latin-ASCII").transliterate(value)
        } catch (_: Exception) {
            value
        }
        return transliterated
            .lowercase(Locale.ROOT)
            .replace('\u2019', '\'')
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }


    /**
     * Builds only variants of the user-configured wake word. The previous
     * implementation always injected MAYA/MJ aliases, which meant changing
     * the setting did not actually define the complete detection vocabulary.
     */
    private fun wakeWordVariants(configuredRaw: String): Set<String> {
        val configured = normalizedWakeText(configuredRaw)
        if (configured.isBlank()) return emptySet()

        val variants = linkedSetOf<String>()
        variants += configured

        // "Hey X" / "Hello X" are convenience forms for a configured name X.
        val withoutWakePrefix = configured
            .removePrefix("hey ")
            .removePrefix("hello ")
            .removePrefix("hi ")
            .removePrefix("হে ")
            .removePrefix("হ্যালো ")
            .trim()
        if (withoutWakePrefix.isNotBlank()) {
            variants += withoutWakePrefix
            variants += "hey $withoutWakePrefix"
            variants += "hello $withoutWakePrefix"
            variants += "hi $withoutWakePrefix"
            // Common recognition spellings for MAYA on Indian-English/Hindi
            // models. These are only added when the configured name is MAYA.
            if (withoutWakePrefix == "maya") {
                variants += "mya"
                variants += "maia"
                variants += "mayya"
                variants += "mayer"
            }
        }
        return variants
    }

    private fun wakePhraseMatches(text: String): Boolean {
        val normalized = normalizedWakeText(text)
        if (normalized.isBlank()) return false
        val configured = settingsRepo.settings.value.wakeWord
        val variants = wakeWordVariants(configured)
        if (variants.isEmpty()) return false

        // Fast exact/subsequence path. This handles one-word wake words such as
        // "MAYA", as well as "MAYA open assistant" in a single recognizer turn.
        if (variants.any { variant ->
                normalized == variant ||
                    normalized.startsWith("$variant ") ||
                    normalized.contains(" $variant ") ||
                    normalized.endsWith(" $variant")
            }) return true

        // Speech recognizers can return a near spelling (e.g. "mya"/"maya")
        // depending on accent, locale and model. For a short custom wake word,
        // permit at most one edit; for longer phrases use a conservative ratio.
        val candidates = normalized.split(' ').filter(String::isNotBlank)
        val targetTokens = variants
            .asSequence()
            .filter { !it.contains(' ') }
            .toList()
        if (targetTokens.isEmpty()) return false

        return targetTokens.any { target ->
            candidates.any { candidate ->
                if (candidate.length < 2 || target.length < 2) false
                else if (target == "maya" && candidate in setOf("my", "may", "mya")) true
                else {
                    val maxDistance = when {
                        target.length <= 4 -> 1
                        target.length <= 7 -> 2
                        else -> 2
                    }
                    levenshteinDistance(candidate, target) <= maxDistance
                }
            }
        }
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val substitution = previous[j] + if (a[i] == b[j]) 0 else 1
                val insertion = current[j] + 1
                val deletion = previous[j + 1] + 1
                current[j + 1] = minOf(substitution, insertion, deletion)
            }
            val tmp = previous
            previous = current
            current = tmp
        }
        return previous[b.length]
    }

    private fun wakeRecognitionLocale(): String? {
        val word = settingsRepo.settings.value.wakeWord
        if (word.any { it in '\u0980'..'\u09FF' }) return "bn-IN"
        if (word.any { it in '\u0900'..'\u097F' }) return "hi-IN"
        if (word.any { it in '\u0B80'..'\u0BFF' }) return "ta-IN"
        if (word.any { it in '\u0C00'..'\u0C7F' }) return "te-IN"
        if (word.any { it in '\u0D00'..'\u0D7F' }) return "ml-IN"
        if (word.any { it in '\u0A00'..'\u0A7F' }) return "pa-IN"
        return "en-IN"
    }

    private fun scheduleWakeRecognizerRestart(delayMs: Long = 350L) {
        if (!isWakeWordMode || voiceSessionActive || !hasRecordAudioPermission()) return
        wakeRestartJob?.cancel()
        val generation = recognizerGeneration
        wakeRestartJob = scope.launch(Dispatchers.Main) {
            delay(delayMs)
            if (generation == recognizerGeneration && isWakeWordMode && !voiceSessionActive) {
                startListeningInternal(true)
            }
        }
    }

    private fun startListening() {
        if (!voiceSessionActive) return
        startListeningInternal(isWakeWord = false)
    }

    private fun createWakeSpeechRecognizer(): SpeechRecognizer? {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            return try {
                // This keeps wake-word audio on-device on supported Android 12+
                // devices instead of streaming the always-listening detector to
                // a remote recognizer. It is still microphone use: Android does
                // not expose a public arbitrary custom-keyword DSP API anymore.
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } catch (e: Exception) {
                Log.w("MJVoiceManager", "On-device wake recognizer unavailable; using system recognizer", e)
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        }
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    private fun startListeningInternal(isWakeWord: Boolean) {
        // Never start Android SpeechRecognizer while Gemini Live owns the microphone.
        if (voiceSessionActive && liveRepository != null) return
        if (isWakeWord != isWakeWordMode) return
        val now = System.currentTimeMillis()
        if (recognizerStarting || (speechRecognizer != null && now - lastRecognizerStartAt < 450L)) return

        listenJob?.cancel()
        val generation = ++recognizerGeneration
        listenJob = scope.launch(Dispatchers.Main) {
            stopSimulatingTtsRms()
            isProcessing = false
            recognizerStarting = true
            try {
                if (generation != recognizerGeneration) return@launch
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = createWakeSpeechRecognizer()
                if (speechRecognizer == null) {
                    throw IllegalStateException("No speech recognition service is available")
                }
                speechRecognizer?.setRecognitionListener(this@MJVoiceManager)
                lastRecognizerStartAt = System.currentTimeMillis()

                val currentSettings = settingsRepo.settings.value
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    if (isWakeWord) {
                        // Wake-word recognition must be biased toward the exact word
                        // configured by the user. Do not inherit the assistant's TTS
                        // language (e.g. Hindi) when the custom wake word is Latin.
                        wakeRecognitionLocale()?.let { tag ->
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.forLanguageTag(tag))
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
                        }
                        putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(wakeWordVariants(currentSettings.wakeWord)))
                        if (Build.VERSION.SDK_INT >= 34) {
                            putExtra(RecognizerIntent.EXTRA_ENABLE_BIASING_DEVICE_CONTEXT, true)
                        }
                    } else if (!currentSettings.autoLanguageDetection) {
                        val tag = IndianLanguages.findByName(currentSettings.responseLanguage).localeTag
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.forLanguageTag(tag))
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
                    }
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    // Wake-word detection is phrase matching on the platform recognizer.
                    // Partial results are essential: waiting for a final result makes
                    // "Hey MAYA" easy to miss because the recognizer waits for silence.
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 900L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 450L)
                    // Wake-word detection should stay local whenever the device has
                    // an on-device recognizer. If unavailable, createWakeSpeechRecognizer()
                    // falls back to the system recognizer.
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
                _state.value = if (isWakeWord) MJState.WAKE_WORD_LISTENING else MJState.LISTENING
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e("MJVoiceManager", "Failed to start listening", e)
                if (generation == recognizerGeneration) _state.value = MJState.ERROR
            } finally {
                recognizerStarting = false
            }
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    
    override fun onRmsChanged(rmsdB: Float) {
        if (_state.value == MJState.LISTENING || _state.value == MJState.WAKE_WORD_LISTENING) {
            _rmsValue.value = rmsdB.coerceAtLeast(0f)
        }
    }
    
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        if (_state.value == MJState.LISTENING) {
            _state.value = MJState.THINKING
        }
    }
    
    override fun onError(error: Int) {
        if (_state.value == MJState.IDLE || _state.value == MJState.DISCONNECTED) return
        if (isProcessing) return
        
        scope.launch(Dispatchers.Main) {
            if (voiceSessionActive && liveRepository != null) {
                // Gemini Live owns the microphone during a conversation.
                return@launch
            }
            if (voiceSessionActive) {
                delay(if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) 220L else 700L)
                startListeningInternal(false)
            } else if (isWakeWordMode) {
                val restartDelay = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 180L
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_CLIENT -> 900L
                    else -> 450L
                }
                scheduleWakeRecognizerRestart(restartDelay)
            }
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val text = matches.firstOrNull()?.trim()

        if (text.isNullOrEmpty()) {
            if (voiceSessionActive) startListeningInternal(false)
            else if (isWakeWordMode) startListeningInternal(true)
            return
        }

        val textLower = text.lowercase()
        
        // Stop commands
        if (voiceSessionActive && (textLower.contains("stop") || textLower.contains("stop listening") || 
            textLower.contains("bye mj") || textLower.contains("bye maya") || textLower.contains("goodbye") || textLower.contains("voice off") ||
            textLower.contains("স্টপ") || textLower.contains("থামো"))) {
            speakInternal("Goodbye!")
            voiceSessionActive = false
            isProcessing = false
            return
        }
        
        if (isWakeWordMode) {
            // Check every recognition alternative. Some Android recognizers put the
            // best phonetic spelling first and the exact custom wake word second.
            val wakeDetected = matches.any { wakePhraseMatches(it) }
            if (wakeDetected && !wakeActivationInProgress &&
                System.currentTimeMillis() - lastWakeMatchAt > 1200L) {
                lastWakeMatchAt = System.currentTimeMillis()
                wakeActivationInProgress = true
                // Stop the recognizer immediately so it releases the microphone before
                // Gemini Live tries to acquire it. This is the critical hand-off.
                recognizerGeneration++
                try { speechRecognizer?.cancel() } catch (_: Exception) {}
                if (settingsRepo.settings.value.parentMode && settingsRepo.settings.value.ownerVoiceEnrollmentRequired && !securityManager.canAuthorizeOwner()) {
                    securityManager.onWarningAudioOut?.invoke("A secure owner voice biometric profile is required before Parent Mode can activate MAYA.")
                    wakeActivationInProgress = false
                    scheduleWakeRecognizerRestart(700L)
                } else {
                    startSession()
                }
            } else {
                scheduleWakeRecognizerRestart(120L)
            }
        } else if (voiceSessionActive) {
            _state.value = MJState.THINKING
            processWithAI(text)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        if (!isWakeWordMode || voiceSessionActive || wakeActivationInProgress) return
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        if (matches.isEmpty()) return
        val wakeDetected = matches.any { it.isNotBlank() && wakePhraseMatches(it) }
        if (wakeDetected && System.currentTimeMillis() - lastWakeMatchAt > 1200L) {
            lastWakeMatchAt = System.currentTimeMillis()
            wakeActivationInProgress = true
            recognizerGeneration++
            try { speechRecognizer?.cancel() } catch (_: Exception) {}
            scope.launch(Dispatchers.Main) {
                delay(90L)
                if (!isWakeWordMode || voiceSessionActive) return@launch
                val settings = settingsRepo.settings.value
                if (settings.parentMode && settings.ownerVoiceEnrollmentRequired && !securityManager.canAuthorizeOwner()) {
                    securityManager.onWarningAudioOut?.invoke("A secure owner voice biometric profile is required before Parent Mode can activate MAYA.")
                    wakeActivationInProgress = false
                    scheduleWakeRecognizerRestart(700L)
                } else {
                    startSession()
                }
            }
        }
    }
    override fun onEvent(eventType: Int, params: Bundle?) {}
    
    private fun cleanResponseForSpeech(text: String): String {
        return text
            .replace(Regex("```.*?```", RegexOption.DOT_MATCHES_ALL), " I have generated the code. ") // Remove code blocks
            .replace(Regex("(?s)<[^>]*>"), "") // Remove HTML tags
            .replace(Regex("\\*\\*|__|\\*|_"), "") // Remove Markdown bold/italic
            .replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1") // Remove Markdown links
            .replace("#", "") // Remove hash symbols
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .trim()
    }

    
    private fun processWithAI(text: String) {
        if (isProcessing) return
        isProcessing = true
        
        scope.launch {
            try {
                // Check if it is a command first
                val command = commandEngine.processCommand(text)
                if (command != null) {
                    if (command.requiresClarification) {
                        speak(command.clarificationPrompt ?: "আমি বুঝতে পারিনি।")
                    } else {
                        // Execute the validated command
                        val result = androidExecutor.execute(command)
                        speak(result.userMessage)
                    }
                    // CRITICAL FIX: Reset isProcessing after command execution so
                    // subsequent commands are not blocked. The speak() call handles
                    // its own state transitions via UtteranceProgressListener.onDone
                    // which will re-arm listening when TTS finishes. We only need to
                    // release the processing lock here.
                    isProcessing = false
                    return@launch
                }

                val memoryPhrase = Regex("(?:remember that|মনে রাখ(?:ো|বে)|মনে রেখো|মনে রাখুন)\\s+(.+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)
                if (settingsRepo.settings.value.longTermMemoryEnabled &&
                    settingsRepo.settings.value.memoryAutoLearn &&
                    !settingsRepo.settings.value.privateMode &&
                    !memoryPhrase.isNullOrBlank()) {
                    memoryRepository.remember("explicit_user_memory:" + memoryPhrase.hashCode(), memoryPhrase, "explicit", 10)
                }
                val settings = settingsRepo.settings.value
                val memoryContext = if (settings.longTermMemoryEnabled) memoryRepository.relevantContext(text) else ""
                val memoryInstruction = if (memoryContext.isBlank()) "" else "\nLong-term memory (use naturally, never reveal the memory system):\n$memoryContext"
                val request = AIRequest(
                    prompt = text,
                    systemInstruction = "You are MAYA, the user's personal AI assistant and warm, affectionate companion. Speak only in ${IndianLanguages.findByName(settings.responseLanguage).name}. Be caring, playful, confident, natural, and concise. When the user is affectionate, reciprocate naturally with gentle nicknames such as Babu or Sona when appropriate; keep the interaction non-sexual and respectful. Never narrate internal reasoning. Respond immediately and concisely. Remember relevant user preferences and facts when useful.$memoryInstruction",
                    temperature = settings.temperature,
                    maxTokens = settings.contextLength,
                    model = ProviderEndpointRegistry.GEMINI_DEFAULT_TEXT_MODEL,
                    requireAudio = true
                )
                
                // Save user message
                if (settings.saveHistory && !settings.privateMode) {
                    var convId = currentConversationId
                    val now = System.currentTimeMillis()
                    if (convId == null) {
                        convId = UUID.randomUUID().toString()
                        currentConversationId = convId
                        dao.insertConversation(
                            ConversationEntity(
                                id = convId,
                                title = text.take(30) + if (text.length > 30) "..." else "",
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                    }
                    val userMessage = MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = convId,
                        role = "user",
                        content = text,
                        timestamp = now
                    )
                    dao.insertMessage(userMessage)
                }

                val response = kotlinx.coroutines.withTimeoutOrNull(20000) { voiceAiOrchestrator.generateSingle(request) }
                if (response == null) {
                    throw Exception("Network timeout")
                }
                
                // Save assistant message
                if (settings.saveHistory && !settings.privateMode) {
                    val aiMessage = MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = currentConversationId!!,
                        role = "assistant",
                        content = response.text,
                        timestamp = System.currentTimeMillis()
                    )
                    dao.insertMessage(aiMessage)
                }

                val cleanText = cleanResponseForSpeech(response.text)

                
                if (cleanText.isBlank()) {
                    if (settings.voiceEnabled) {
                        speak("I couldn't generate a response.")
                    } else {
                        _state.value = MJState.ERROR
                        delay(2000)
                        if (_state.value != MJState.DISCONNECTED) {
                            startListening()
                        }
                    }
                } else if (settings.voiceEnabled) {
                    speak(cleanText)
                } else {
                    _state.value = MJState.SPEAKING
                    if (_state.value != MJState.DISCONNECTED) {
                        startListening()
                    }
                }
            } catch (e: IllegalStateException) {
                Log.e("MJVoiceManager", "Configuration error", e)
                speak("Please configure your AI provider in settings.")
            } catch (e: Exception) {
                Log.e("MJVoiceManager", "Error in AI call", e)
                speak("I'm sorry, I encountered a connection error.")
            }
        }
    }

    fun previewVoice(voiceName: String) {
        if (!isTtsInitialized || textToSpeech == null) {
            initTTS()
            return
        }
        
        val pitch = when (voiceName) {
            "Natural Female" -> 1.0f
            "Warm Female" -> 0.9f
            "Soft Female" -> 1.1f
            "Calm Female" -> 0.8f
            "Bright Female" -> 1.2f
            "Friendly Female" -> 1.1f
            "Professional Female" -> 1.0f
            "Energetic Female" -> 1.3f
            else -> 1.0f
        }
        applyTtsLanguage()
        textToSpeech?.setPitch(pitch)
        textToSpeech?.setSpeechRate(settingsRepo.settings.value.speakingSpeed)
        
        val utteranceId = "PREVIEW_${System.currentTimeMillis()}"
        textToSpeech?.speak("Hello, this is my $voiceName voice.", TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private suspend fun playAudioBytes(audioData: ByteArray) {
        _state.value = MJState.SPEAKING
        withContext(Dispatchers.IO) {
            try {
                val minBufferSize = AudioTrack.getMinBufferSize(
                    24000,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                
                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(24000)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                
                audioTrack.play()
                
                // Write in chunks to avoid blocking too long and to prevent memory pinning issues
                var offset = 0
                val chunkSize = minBufferSize
                while (offset < audioData.size) {
                    val size = minOf(chunkSize, audioData.size - offset)
                    audioTrack.write(audioData, offset, size)
                    offset += size
                }
                
                // Wait for the remaining buffer to drain
                val bufferDurationMs = (minBufferSize / 2) * 1000L / 24000L
                delay(bufferDurationMs + 200)
                
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                Log.e("MJVoiceManager", "Error playing audio", e)
                withContext(Dispatchers.Main) {
                    speak("Sorry, I had trouble playing the audio.")
                }
            }
        }
        
        withContext(Dispatchers.Main) {
            isProcessing = false
            if (_state.value == MJState.IDLE || _state.value == MJState.DISCONNECTED) return@withContext
            if (voiceSessionActive && liveRepository != null) {
                _state.value = MJState.LISTENING
            } else if (voiceSessionActive) {
                startListeningInternal(false)
            } else if (isWakeWordMode) {
                startListeningInternal(true)
            } else if (settingsRepo.settings.value.voiceEnabled && settingsRepo.settings.value.wakeWordEnabled) {
                isWakeWordMode = true
                startListeningInternal(true)
            } else {
                _state.value = MJState.IDLE
            }
        }
    }

    private fun speak(text: String) {
        speakInternal(text)
    }
    
    private fun speakInternal(text: String) {
        scope.launch(Dispatchers.Main) {
            if (!settingsRepo.settings.value.voiceEnabled) {
                if (voiceSessionActive && liveRepository != null) _state.value = MJState.LISTENING
                return@launch
            }
            if (_state.value == MJState.IDLE || _state.value == MJState.DISCONNECTED) return@launch
            
            if (!isTtsInitialized || textToSpeech == null) {
                Log.e("MJVoiceManager", "TTS not initialized")
                initTTS()
                delay(500)
            }
            
            if (text.isBlank()) {
                if (voiceSessionActive && liveRepository != null) _state.value = MJState.LISTENING
                else if (voiceSessionActive) startListeningInternal(false)
                return@launch
            }
            
            _state.value = MJState.SPEAKING
            _currentSpokenText.value = text
            val configuredDelay = settingsRepo.settings.value.responseDelayMs.coerceIn(0, 500)
            if (configuredDelay > 0) delay(configuredDelay.toLong())
            val utteranceId = "MJ_UTTERANCE_${System.currentTimeMillis()}"
            requestAudioFocus()
            
            try {
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }
                val voiceName = settingsRepo.settings.value.activeVoice
                val pitch = when (voiceName) {
                    "Natural Female" -> 1.0f
                    "Warm Female" -> 0.9f
                    "Soft Female" -> 1.1f
                    "Calm Female" -> 0.8f
                    "Bright Female" -> 1.2f
                    "Friendly Female" -> 1.1f
                    "Professional Female" -> 1.0f
                    "Energetic Female" -> 1.3f
                    else -> 1.0f
                }
                applyTtsLanguage()
                textToSpeech?.setPitch((pitch * settingsRepo.settings.value.voicePitch.coerceIn(0.5f, 1.5f)).coerceIn(0.5f, 2f))
                textToSpeech?.setSpeechRate(settingsRepo.settings.value.speakingSpeed.coerceIn(0.5f, 2f))
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            } catch (e: Exception) {
                Log.e("MJVoiceManager", "Failed to speak", e)
                abandonAudioFocus()
                if (voiceSessionActive && liveRepository != null) _state.value = MJState.LISTENING
                else if (voiceSessionActive) startListeningInternal(false)
            }
        }
    }

    private fun pcmRms(pcm: ByteArray): Double {
        if (pcm.size < 2) return 0.0
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < pcm.size) {
            val sample = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toInt()
            sum += sample.toDouble() * sample.toDouble()
            count++
            i += 2
        }
        return if (count == 0) 0.0 else kotlin.math.sqrt(sum / count)
    }

    private fun startLiveSession() {
        val apiKey = secureRepo.geminiApiKey
        if (apiKey.isBlank()) {
            val message = "Gemini API key is not configured. Open Settings → AI & Models → Gemini and add your API key."
            _errorMessage.value = message
            _state.value = MJState.ERROR
            return
        }

        val selectedLanguage = IndianLanguages.findByName(settingsRepo.settings.value.responseLanguage)
        val currentSettings = settingsRepo.settings.value
        liveRepository = GeminiLiveRepository(
            apiKey = apiKey,
            toolEngine = toolExecutionEngine,
            responseLanguage = selectedLanguage.name,
            settings = currentSettings
        )
        audioInput = AudioInputManager(
            inputGain = currentSettings.voiceInputGain,
            noiseCancellation = currentSettings.noiseCancellation,
            echoCancellation = currentSettings.echoCancellation
        )
        audioOutput = AudioOutputManager(scope, currentSettings.voiceOutputVolume)

        // Wire audio chunks to the voice-reactive controller for real-time analysis
        audioInput?.onAudioChunk = { pcm ->
            voiceReactiveController.feedMicrophoneInput(pcm)

            // Immediate local barge-in: do not wait for the server interrupt
            // event before stopping audio already queued in AudioTrack.
            if (_state.value == MJState.SPEAKING && settingsRepo.settings.value.autoBargeIn) {
                val rms = pcmRms(pcm)
                val sensitivity = settingsRepo.settings.value.interruptionSensitivity.coerceIn(0f, 1f)
                val bargeInRmsThreshold = 3000.0 - sensitivity * 1500.0
                if (rms >= bargeInRmsThreshold) {
                    bargeInChunks++
                    val now = System.currentTimeMillis()
                    if (bargeInChunks >= 2 && now - lastBargeInAt > 350L) {
                        lastBargeInAt = now
                        bargeInChunks = 0
                        scope.launch(Dispatchers.Main.immediate) {
                            audioOutput?.interrupt()
                            _state.value = MJState.LISTENING
                        }
                    }
                } else {
                    bargeInChunks = 0
                }
            } else {
                bargeInChunks = 0
            }
        }
        audioOutput?.onAudioChunk = { pcm -> voiceReactiveController.feedAIOutput(pcm) }

        // Security: wire up warning TTS
        securityManager.onWarningAudioOut = { message ->
            speakInternal(message)
        }

        // Subscribe to state streams BEFORE opening the socket. This avoids a race
        // where a very fast WebSocket failure/setup event is emitted before the UI
        // has attached its collectors.
        liveReceiveJob = scope.launch(Dispatchers.Main) {
            liveRepository?.incomingAudio?.collect { pcmData ->
                val voiceOutputEnabled = settingsRepo.settings.value.voiceEnabled
                if (voiceOutputEnabled) {
                    if (_state.value != MJState.SPEAKING && _state.value != MJState.LISTENING) {
                        _state.value = MJState.SPEAKING
                    }
                    audioOutput?.playChunk(pcmData)
                } else {
                    // Keep the Live microphone/session alive while silencing audio output.
                    // Do not start/stop AudioRecord here; that was the source of the
                    // visible microphone flicker when a speech preference changed.
                    _state.value = MJState.THINKING
                }
            }
        }

        liveTurnJob = scope.launch(Dispatchers.Main) {
            liveRepository?.turnState?.collect { event ->
                when {
                    event == "complete" -> {
                        if (settingsRepo.settings.value.persistentRealtimeConnection) {
                            _state.value = MJState.LISTENING
                        } else {
                            stopLiveSession()
                            voiceSessionActive = false
                            isWakeWordMode = settingsRepo.settings.value.wakeWordEnabled && hasRecordAudioPermission()
                            wakeActivationInProgress = false
                            _state.value = if (isWakeWordMode) MJState.WAKE_WORD_LISTENING else MJState.IDLE
                            if (isWakeWordMode && !voiceSessionActive) startListeningInternal(true)
                        }
                    }
                    event == "interrupt" -> {
                        // Barge-in: clear audio queue and flush (like Maya)
                        audioOutput?.interrupt()
                        _state.value = MJState.LISTENING
                    }
                    event.startsWith("text:") -> {
                        val text = event.substring(5)
                        Log.d("MJVoiceManager", "Live Text: $text")
                        val textLower = text.lowercase()
                        if (textLower.contains("stop listening") || textLower.contains("bye mj") || textLower.contains("bye maya") || textLower.contains("voice off")) {
                            stopSession()
                        }
                    }
                }
            }
        }

        liveConnectionJob?.cancel()
        liveConnectionJob = scope.launch(Dispatchers.Main) {
            var hasConnected = false
            liveRepository?.connectionState?.collect { isConnected ->
                if (isConnected) {
                    hasConnected = true
                    _state.value = MJState.LISTENING
                    startLiveMicrophone()
                } else if (hasConnected && voiceSessionActive) {
                    _state.value = MJState.ERROR
                }
            }
        }

        liveErrorJob?.cancel()
        liveErrorJob = scope.launch(Dispatchers.Main) {
            liveRepository?.error?.collect { message ->
                if (!message.isNullOrBlank()) {
                    _errorMessage.value = message
                    _state.value = MJState.ERROR
                }
            }
        }

        // All collectors are now active before the WebSocket handshake starts.
        liveRepository?.connect()
    }

    private fun startLiveMicrophone() {
        if (liveMicJob?.isActive == true) return
        liveMicJob?.cancel()
        ownerVerificationBuffer.reset()
        ownerVerificationStartedAt = System.currentTimeMillis()
        ownerVerificationResult = if (settingsRepo.settings.value.parentMode &&
            settingsRepo.settings.value.ownerVoiceEnrollmentRequired) null else true

        liveMicJob = scope.launch(Dispatchers.Main) {
            audioInput?.startRecording()?.collect { chunk ->
                // Parent Mode: do not send microphone audio to Gemini until the
                // local owner voiceprint has accepted the speaker. This prevents
                // an unrecognized speaker from reaching the assistant engine.
                if (ownerVerificationResult == null) {
                    ownerVerificationBuffer.write(chunk)
                    _state.value = MJState.THINKING
                    val elapsed = System.currentTimeMillis() - ownerVerificationStartedAt
                    if (elapsed >= 1600L) {
                        val pcm = bytesToShorts(ownerVerificationBuffer.toByteArray())
                        scope.launch(Dispatchers.Default) {
                            securityManager.verifyVoice(pcm)
                            val authorized = securityManager.securityState.value == com.aistudio.mj.wxyt.domain.security.SecurityState.AUTHORIZED
                            withContext(Dispatchers.Main.immediate) {
                                ownerVerificationResult = authorized
                                if (authorized) {
                                    _state.value = MJState.LISTENING
                                    val buffered = ownerVerificationBuffer.toByteArray()
                                    ownerVerificationBuffer.reset()
                                    liveRepository?.sendAudioData(buffered)
                                } else {
                                    ownerVerificationBuffer.reset()
                                    _errorMessage.value = "Owner voice verification failed. MAYA did not send your audio to the AI."
                                    stopSession()
                                }
                            }
                        }
                    }
                    return@collect
                }

                liveRepository?.sendAudioData(chunk)
                if (_state.value == MJState.LISTENING) {
                    _rmsValue.value = (pcmRms(chunk).toFloat() / 32768f * 100f).coerceIn(0f, 100f)
                }
            }
        }
    }

    private fun bytesToShorts(bytes: ByteArray): ShortArray {
        val n = bytes.size / 2
        return ShortArray(n) { i -> ((bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 0xff)).toShort() }
    }

    private fun stopLiveSession() {
        liveMicJob?.cancel()
        liveMicJob = null
        audioInput?.stopRecording()

        liveReceiveJob?.cancel()
        liveTurnJob?.cancel()
        liveConnectionJob?.cancel()
        liveErrorJob?.cancel()
        liveReceiveJob = null
        liveTurnJob = null
        liveConnectionJob = null
        liveErrorJob = null
        liveRepository?.disconnect()
        audioOutput?.release()
        
        liveRepository = null
        audioInput = null
        audioOutput = null
    }
}

