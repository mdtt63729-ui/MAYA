package com.aistudio.mj.wxyt.domain.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.aistudio.mj.wxyt.domain.ai.AIProviderFactory
import com.aistudio.mj.wxyt.domain.ai.ApiClientProvider
import com.aistudio.mj.wxyt.domain.ai.ProviderEndpointRegistry
import com.aistudio.mj.wxyt.domain.security.SecureCredentialRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.core.content.ContextCompat
import com.aistudio.mj.wxyt.service.AssistantForegroundService
import com.aistudio.mj.wxyt.domain.chat.AppDatabase
import com.aistudio.mj.wxyt.domain.chat.LongTermMemoryRepository
import com.aistudio.mj.wxyt.domain.jarvis.MayaAuditLog
import com.aistudio.mj.wxyt.domain.jarvis.MayaRoutineRepository

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    val secureRepo = SecureCredentialRepository(application)
    val settingsRepo = SettingsRepository.get(application)
    val settings: StateFlow<MJSettings> = settingsRepo.settings

    // Shared factory — uses shared OkHttpClient
    private val providerFactory = AIProviderFactory(application)

    private val _voiceEnabled = MutableStateFlow(settingsRepo.settings.value.voiceEnabled)
    val voiceEnabled = _voiceEnabled.asStateFlow()

    private val _activeVoice = MutableStateFlow(settingsRepo.settings.value.activeVoice)
    val activeVoice = _activeVoice.asStateFlow()

    private val _responseLanguage = MutableStateFlow(settingsRepo.settings.value.responseLanguage)
    val responseLanguage = _responseLanguage.asStateFlow()

    private val _speakingSpeed = MutableStateFlow(settingsRepo.settings.value.speakingSpeed)
    val speakingSpeed = _speakingSpeed.asStateFlow()

    private val _geminiApiKey = MutableStateFlow(secureRepo.geminiApiKey)
    val geminiApiKey = _geminiApiKey.asStateFlow()

    private val _openRouterApiKey = MutableStateFlow(secureRepo.openRouterApiKey)
    val openRouterApiKey = _openRouterApiKey.asStateFlow()

    private val _openCodeApiKey = MutableStateFlow(secureRepo.openCodeApiKey)
    val openCodeApiKey = _openCodeApiKey.asStateFlow()

    private val _nvidiaApiKey = MutableStateFlow(secureRepo.nvidiaApiKey)
    val nvidiaApiKey = _nvidiaApiKey.asStateFlow()

    private val _customProviderApiKey = MutableStateFlow(secureRepo.customProviderApiKey)
    val customProviderApiKey = _customProviderApiKey.asStateFlow()

    private val _customBaseUrl = MutableStateFlow(secureRepo.customBaseUrl)
    val customBaseUrl = _customBaseUrl.asStateFlow()

    private val _customModelId = MutableStateFlow(secureRepo.customModelId)
    val customModelId = _customModelId.asStateFlow()

    private val _isBackgroundEnabled = MutableStateFlow(settingsRepo.settings.value.backgroundEnabled)
    val isBackgroundEnabled = _isBackgroundEnabled.asStateFlow()

    private val _isWakeWordEnabled = MutableStateFlow(settingsRepo.settings.value.wakeWordEnabled)
    val isWakeWordEnabled = _isWakeWordEnabled.asStateFlow()

    private val _wakeWord = MutableStateFlow(settingsRepo.settings.value.wakeWord)
    val wakeWord = _wakeWord.asStateFlow()

    private val _theme = MutableStateFlow(settingsRepo.settings.value.theme)
    val theme = _theme.asStateFlow()

    enum class ConnectionState {
        NOT_TESTED, TESTING, SUCCESS, ERROR
    }

    private val _geminiConnectionState = MutableStateFlow(ConnectionState.NOT_TESTED)
    val geminiConnectionState = _geminiConnectionState.asStateFlow()
    private val _geminiErrorMessage = MutableStateFlow<String?>(null)
    val geminiErrorMessage = _geminiErrorMessage.asStateFlow()
    private val _geminiLatency = MutableStateFlow<Long?>(null)
    val geminiLatency = _geminiLatency.asStateFlow()

    private val _openRouterConnectionState = MutableStateFlow(ConnectionState.NOT_TESTED)
    val openRouterConnectionState = _openRouterConnectionState.asStateFlow()
    private val _openRouterErrorMessage = MutableStateFlow<String?>(null)
    val openRouterErrorMessage = _openRouterErrorMessage.asStateFlow()

    private val _openCodeConnectionState = MutableStateFlow(ConnectionState.NOT_TESTED)
    val openCodeConnectionState = _openCodeConnectionState.asStateFlow()
    private val _openCodeErrorMessage = MutableStateFlow<String?>(null)
    val openCodeErrorMessage = _openCodeErrorMessage.asStateFlow()

    private val _nvidiaConnectionState = MutableStateFlow(ConnectionState.NOT_TESTED)
    val nvidiaConnectionState = _nvidiaConnectionState.asStateFlow()
    private val _nvidiaErrorMessage = MutableStateFlow<String?>(null)
    val nvidiaErrorMessage = _nvidiaErrorMessage.asStateFlow()

    private val _customProviderConnectionState = MutableStateFlow(ConnectionState.NOT_TESTED)
    val customProviderConnectionState = _customProviderConnectionState.asStateFlow()
    private val _customProviderErrorMessage = MutableStateFlow<String?>(null)
    val customProviderErrorMessage = _customProviderErrorMessage.asStateFlow()

    fun updateSettings(newSettings: MJSettings) {
        settingsRepo.updateSettings(newSettings)
        _voiceEnabled.value = newSettings.voiceEnabled
        _activeVoice.value = newSettings.activeVoice
        _responseLanguage.value = newSettings.responseLanguage
        _speakingSpeed.value = newSettings.speakingSpeed
        _isBackgroundEnabled.value = newSettings.backgroundEnabled
        _isWakeWordEnabled.value = newSettings.wakeWordEnabled
        _wakeWord.value = newSettings.wakeWord
        _theme.value = newSettings.theme
        _geminiApiKey.value = secureRepo.geminiApiKey
        _openRouterApiKey.value = secureRepo.openRouterApiKey
        _openCodeApiKey.value = secureRepo.openCodeApiKey
        _nvidiaApiKey.value = secureRepo.nvidiaApiKey
        _customProviderApiKey.value = secureRepo.customProviderApiKey
        _customBaseUrl.value = secureRepo.customBaseUrl
        _customModelId.value = secureRepo.customModelId
    }

    fun setVoiceEnabled(enabled: Boolean) {
        // "Voice Feedback" controls spoken output. It must never implicitly
        // acquire the microphone or start a background recognizer. Microphone
        // ownership is controlled only by the active session / Wake Word /
        // Background Assistant settings. This removes the on/off microphone
        // loop caused by toggling a speech-output preference.
        _voiceEnabled.value = enabled
        updateSettings(settings.value.copy(voiceEnabled = enabled))
    }

    fun setActiveVoice(voice: String) {
        _activeVoice.value = voice
        updateSettings(settings.value.copy(activeVoice = voice))
    }

    fun setResponseLanguage(language: String) {
        val selected = IndianLanguages.findByName(language).name
        _responseLanguage.value = selected
        updateSettings(settings.value.copy(responseLanguage = selected))
    }

    fun setSpeakingSpeed(speed: Float) {
        _speakingSpeed.value = speed
        updateSettings(settings.value.copy(speakingSpeed = speed))
    }

    fun setOpenRouterApiKey(key: String): Boolean {
        val trimmed = key.trim()
        return try {
            secureRepo.openRouterApiKey = trimmed
            _openRouterApiKey.value = trimmed
            _openRouterConnectionState.value = ConnectionState.NOT_TESTED
            true
        } catch (e: Exception) { false }
    }

    fun setOpenCodeApiKey(key: String): Boolean {
        val trimmed = key.trim()
        return try {
            secureRepo.openCodeApiKey = trimmed
            _openCodeApiKey.value = trimmed
            _openCodeConnectionState.value = ConnectionState.NOT_TESTED
            true
        } catch (e: Exception) { false }
    }

    fun setNvidiaApiKey(key: String): Boolean {
        val trimmed = key.trim()
        return try {
            secureRepo.nvidiaApiKey = trimmed
            _nvidiaApiKey.value = trimmed
            _nvidiaConnectionState.value = ConnectionState.NOT_TESTED
            true
        } catch (e: Exception) { false }
    }

    fun setCustomProviderApiKey(key: String): Boolean {
        val trimmed = key.trim()
        return try {
            secureRepo.customProviderApiKey = trimmed
            _customProviderApiKey.value = trimmed
            _customProviderConnectionState.value = ConnectionState.NOT_TESTED
            true
        } catch (e: Exception) { false }
    }

    fun setCustomBaseUrl(url: String) {
        secureRepo.customBaseUrl = url.trim()
        _customBaseUrl.value = url.trim()
        _customProviderConnectionState.value = ConnectionState.NOT_TESTED
    }

    fun setCustomModelId(modelId: String) {
        secureRepo.customModelId = modelId.trim()
        _customModelId.value = modelId.trim()
        _customProviderConnectionState.value = ConnectionState.NOT_TESTED
    }

    fun setGeminiApiKey(key: String): Boolean {
        val trimmed = key.trim()
        return try {
            // Single source of truth: SecureCredentialRepository only
            secureRepo.geminiApiKey = trimmed
            _geminiApiKey.value = trimmed
            _geminiConnectionState.value = ConnectionState.NOT_TESTED
            _geminiLatency.value = null
            true
        } catch (e: Exception) { false }
    }

    fun setBackgroundEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        val microphoneGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        // Background microphone capture cannot be started safely without the
        // runtime microphone permission. The Settings UI requests that permission
        // before calling this method, but keep the ViewModel fail-closed as well.
        if (enabled && !microphoneGranted) {
            _isBackgroundEnabled.value = false
            updateSettings(settings.value.copy(backgroundEnabled = false))
            return
        }

        val effective = enabled
        val next = settings.value.copy(
            backgroundEnabled = effective,
            backgroundProcessing = if (effective) true else settings.value.backgroundProcessing
        )
        _isBackgroundEnabled.value = effective
        updateSettings(next)

        if (effective) {
            val intent = Intent(context, AssistantForegroundService::class.java)
                .setAction(AssistantForegroundService.ACTION_START_BACKGROUND)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                _isBackgroundEnabled.value = false
                updateSettings(settings.value.copy(backgroundEnabled = false))
            }
        } else {
            try {
                context.startService(
                    Intent(context, AssistantForegroundService::class.java)
                        .setAction(AssistantForegroundService.ACTION_STOP)
                )
            } catch (_: Exception) { }
        }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        val microphoneGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (enabled && !microphoneGranted) {
            _isWakeWordEnabled.value = false
            updateSettings(settings.value.copy(wakeWordEnabled = false))
            return
        }

        _isWakeWordEnabled.value = enabled
        // A wake word is inherently a background capability. Keep the foreground
        // assistant controller alive so the detector survives leaving/killing the
        // UI instead of depending on the Activity process.
        val next = settings.value.copy(
            wakeWordEnabled = enabled,
            backgroundEnabled = if (enabled) true else settings.value.backgroundEnabled,
            backgroundProcessing = if (enabled) true else settings.value.backgroundProcessing
        )
        updateSettings(next)

        if (enabled) {
            val intent = Intent(context, AssistantForegroundService::class.java)
                .setAction(AssistantForegroundService.ACTION_START_BACKGROUND)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                _isWakeWordEnabled.value = false
                updateSettings(settings.value.copy(wakeWordEnabled = false))
            }
        } else {
            // Stopping wake word must release the recognizer immediately. The
            // service itself may remain available if the user enabled other
            // background features.
            try {
                com.aistudio.mj.wxyt.domain.assistant.MJVoiceManager
                    .getInstance(context)
                    .stopWakeWordListening()
            } catch (_: Exception) { }
        }
    }

    fun setWakeWord(word: String) {
        val normalized = word.trim().replace(Regex("\\s+"), " ")
        _wakeWord.value = normalized
        updateSettings(settings.value.copy(wakeWord = normalized))

        // Recreate the recognizer immediately so the new custom wake word is
        // applied to Android's biasing hints and recognition locale. Without
        // this, an already-running recognizer can continue using the old phrase
        // until the next service restart.
        if (normalized.isNotBlank() && settings.value.wakeWordEnabled) {
            val context = getApplication<Application>()
            val manager = com.aistudio.mj.wxyt.domain.assistant.MJVoiceManager.getInstance(context)
            manager.restartWakeWordListening()
        }
    }

    fun setTheme(newTheme: String) {
        _theme.value = newTheme
        updateSettings(settings.value.copy(theme = newTheme))
    }


    fun clearAllUserData() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val database = AppDatabase.getDatabase(context)
            database.chatDao().clearAllMessages()
            database.chatDao().clearAllConversations()
            LongTermMemoryRepository(context).clear()
            MayaAuditLog(context).clear()
            MayaRoutineRepository(context).clear()
            secureRepo.clearAllCredentials()
            settingsRepo.clearSettings()
            _geminiApiKey.value = ""
            _openRouterApiKey.value = ""
            _openCodeApiKey.value = ""
            _nvidiaApiKey.value = ""
            _customProviderApiKey.value = ""
            _customBaseUrl.value = ""
            _customModelId.value = ""
        }
    }

    fun previewVoice(voiceName: String) {
        com.aistudio.mj.wxyt.domain.assistant.MJVoiceManager.getInstance(getApplication()).previewVoice(voiceName)
    }


    /** Generic, typed setting mutators used by the advanced Control Center. */
    fun setReasoningMode(value: String) = updateSettings(settings.value.copy(reasoningMode = value))
    fun setPlanningDepth(value: String) = updateSettings(settings.value.copy(planningDepth = value))
    fun setAnswerStyle(value: String) = updateSettings(settings.value.copy(answerStyle = value))
    fun setPerformanceMode(value: String) = updateSettings(settings.value.copy(performanceMode = value))
    fun setThemeValue(value: String) = setTheme(value)
    fun setBlur(value: String) = updateSettings(settings.value.copy(blur = value))
    fun setMotion(value: String) = updateSettings(settings.value.copy(motion = value))
    fun setParentMode(enabled: Boolean) = updateSettings(settings.value.copy(parentMode = enabled, ownerVoiceEnabled = enabled))
    fun setOwnerVoiceEnabled(enabled: Boolean) = updateSettings(settings.value.copy(ownerVoiceEnabled = enabled))
    fun setOwnerVoiceEnrolled(enrolled: Boolean) = updateSettings(settings.value.copy(ownerVoiceEnrolled = enrolled))
    fun setOwnerVoiceThreshold(value: Float) = updateSettings(settings.value.copy(ownerVoiceThreshold = value.coerceIn(.55f, .95f)))
    fun setLockAfterUnauthorizedAttempts(value: Int) =
        updateSettings(settings.value.copy(lockAfterUnauthorizedAttempts = value.coerceIn(2, 5)))
    fun setResponseDelayMs(value: Int) = updateSettings(settings.value.copy(responseDelayMs = value.coerceIn(0, 500)))
    fun setMaximumRetryCount(value: Int) = updateSettings(settings.value.copy(maximumRetryCount = value.coerceIn(0, 5)))
    fun setMemoryDepth(value: Int) = updateSettings(settings.value.copy(memoryDepth = value.coerceIn(1, 50)))
    fun setSpeakingSpeedValue(value: Float) = setSpeakingSpeed(value.coerceIn(.5f, 2f))
    fun setFloatSetting(name: String, value: Float) {
        val s = settings.value
        updateSettings(when (name) {
            "voicePitch" -> s.copy(voicePitch = value)
            "expressiveness" -> s.copy(expressiveness = value)
            "emotionIntensity" -> s.copy(emotionIntensity = value)
            "interruptionSensitivity" -> s.copy(interruptionSensitivity = value)
            "vadSensitivity" -> s.copy(vadSensitivity = value)
            "voiceInputGain" -> s.copy(voiceInputGain = value)
            "voiceOutputVolume" -> s.copy(voiceOutputVolume = value)
            "actionConfidenceThreshold" -> s.copy(actionConfidenceThreshold = value)
            "orbReactivity" -> s.copy(orbReactivity = value)
            "voiceVisualization" -> s.copy(voiceVisualization = value)
            "glowIntensity" -> s.copy(glowIntensity = value)
            "particleDensity" -> s.copy(particleDensity = value)
            "orbSize" -> s.copy(orbSize = value)
            "animationSpeed" -> s.copy(animationSpeed = value)
            "edgeLightingIntensity" -> s.copy(edgeLightingIntensity = value)
            "edgeLightingSpeed" -> s.copy(edgeLightingSpeed = value)
            "warmth" -> s.copy(warmth = value)
            "humor" -> s.copy(humor = value)
            "playfulness" -> s.copy(playfulness = value)
            "sarcasm" -> s.copy(sarcasm = value)
            "affection" -> s.copy(affection = value)
            "emotionalExpressiveness" -> s.copy(emotionalExpressiveness = value)
            "formality" -> s.copy(formality = value)
            "talkativeness" -> s.copy(talkativeness = value)
            "proactivity" -> s.copy(proactivity = value)
            "temperature" -> s.copy(temperature = value)
            "wakeWordSensitivity" -> s.copy(wakeWordSensitivity = value.coerceIn(0f, 1f))
            else -> s
        })
    }
    fun setAgentInt(name: String, value: Int) {
        val s = settings.value
        updateSettings(when (name) {
            "maxAgentSteps" -> s.copy(maxAgentSteps = value.coerceIn(1, 20))
            "maxParallelTools" -> s.copy(maxParallelTools = value.coerceIn(1, 8))
            else -> s
        })
    }

    fun setBooleanSetting(name: String, enabled: Boolean) {
        val s = settings.value
        updateSettings(when (name) {
            "multimodalVision" -> s.copy(multimodalVision = enabled)
            "screenshotContext" -> s.copy(screenshotContext = enabled)
            "cameraAwareness" -> s.copy(cameraAwareness = enabled)
            "screenAwareness" -> s.copy(screenAwareness = enabled)
            "predictiveIntelligence" -> s.copy(predictiveIntelligence = enabled)
            "routineLearning" -> s.copy(routineLearning = enabled)
            "dryRunMode" -> s.copy(dryRunMode = enabled)
            "multiAgentOrchestration" -> s.copy(multiAgentOrchestration = enabled)
            "adaptiveModelRouting" -> s.copy(adaptiveModelRouting = enabled)
            "streamingResponses" -> s.copy(streamingResponses = enabled)
            "persistentRealtimeConnection" -> s.copy(persistentRealtimeConnection = enabled)
            "contextCompression" -> s.copy(contextCompression = enabled)
            "goalDecomposition" -> s.copy(goalDecomposition = enabled)
            "parallelToolExecution" -> s.copy(parallelToolExecution = enabled)
            "failureRecovery" -> s.copy(failureRecovery = enabled)
            "riskBasedConfirmation" -> s.copy(riskBasedConfirmation = enabled)
            "simulationBeforeRiskyAction" -> s.copy(simulationBeforeRiskyAction = enabled)
            "auditTrail" -> s.copy(auditTrail = enabled)
            "selfDiagnostics" -> s.copy(selfDiagnostics = enabled)
            "systemHealthMonitoring" -> s.copy(systemHealthMonitoring = enabled)
            "emotionContext" -> s.copy(emotionContext = enabled)
            "temporalIntelligence" -> s.copy(temporalIntelligence = enabled)
            "knowledgeGraph" -> s.copy(knowledgeGraph = enabled)
            "ownerVoiceAntiSpoof" -> s.copy(ownerVoiceAntiSpoof = enabled)
            "ownerVoiceEnrollmentRequired" -> s.copy(ownerVoiceEnrollmentRequired = enabled)
            "wakeWordOffline" -> s.copy(wakeWordOffline = enabled)
            "highRiskBiometricConfirmation" -> s.copy(highRiskBiometricConfirmation = enabled)
            "notificationPrivacyStrict" -> s.copy(notificationPrivacyStrict = enabled)
            "webIntelligence" -> s.copy(webIntelligence = enabled)
            "factVerification" -> s.copy(factVerification = enabled)
            "selfCorrection" -> s.copy(selfCorrection = enabled)
            "memoryAutoLearn" -> s.copy(memoryAutoLearn = enabled)
            "memoryApproval" -> s.copy(memoryApproval = enabled)
            "autoBargeIn" -> s.copy(autoBargeIn = enabled)
            "noiseCancellation" -> s.copy(noiseCancellation = enabled)
            "echoCancellation" -> s.copy(echoCancellation = enabled)
            "autoLanguageDetection" -> s.copy(autoLanguageDetection = enabled)
            "codeSwitching" -> s.copy(codeSwitching = enabled)
            "unauthorizedWarningEnabled" -> s.copy(unauthorizedWarningEnabled = enabled)
            "lockOnUnauthorizedVoice" -> s.copy(lockOnUnauthorizedVoice = enabled)
            "nicknameBehavior" -> s.copy(nicknameBehavior = enabled)
            "greetingBehavior" -> s.copy(greetingBehavior = enabled)
            "timeAwareGreetings" -> s.copy(timeAwareGreetings = enabled)
            "idleBreathing" -> s.copy(idleBreathing = enabled)
            "emotionOrb" -> s.copy(emotionOrb = enabled)
            "musicReactiveOrb" -> s.copy(musicReactiveOrb = enabled)
            "hapticFeedback" -> s.copy(hapticFeedback = enabled)
            "batterySaverAnimation" -> s.copy(batterySaverAnimation = enabled)
            "edgeLightingEnabled" -> s.copy(edgeLightingEnabled = enabled)
            "edgeLightingReactive" -> s.copy(edgeLightingReactive = enabled)
            "edgeLightingIdle" -> s.copy(edgeLightingIdle = enabled)
            "scheduledActions" -> s.copy(scheduledActions = enabled)
            "routinesEnabled" -> s.copy(routinesEnabled = enabled)
            "notificationActions" -> s.copy(notificationActions = enabled)
            "clipboardActions" -> s.copy(clipboardActions = enabled)
            "screenAutomation" -> s.copy(screenAutomation = enabled)
            "allowAccessibilityAutomation" -> s.copy(allowAccessibilityAutomation = enabled)
            "actionVerification" -> s.copy(actionVerification = enabled)
            "autoExecuteSafeActions" -> s.copy(autoExecuteSafeActions = enabled)
            "requireConfirmation" -> s.copy(requireConfirmation = enabled)
            "failedActionRetry" -> s.copy(failedActionRetry = enabled)
            "useDeviceContext" -> s.copy(useDeviceContext = enabled)
            "notificationReading" -> s.copy(notificationReading = enabled)
            "importantNotificationFilter" -> s.copy(importantNotificationFilter = enabled)
            "notificationSummaries" -> s.copy(notificationSummaries = enabled)
            "readNotificationsAloud" -> s.copy(readNotificationsAloud = enabled)
            "saveHistory" -> s.copy(saveHistory = enabled)
            "rememberConversations" -> s.copy(rememberConversations = enabled)
            "longTermMemoryEnabled" -> s.copy(longTermMemoryEnabled = enabled)
            "localOnlyMemory" -> s.copy(localOnlyMemory = enabled)
            "encryptedMemory" -> s.copy(encryptedMemory = enabled)
            "cloudMemory" -> s.copy(cloudMemory = enabled)
            "privateMode" -> s.copy(privateMode = enabled)
            "biometricActionConfirmation" -> s.copy(biometricActionConfirmation = enabled)
            "aiDataSharing" -> s.copy(aiDataSharing = enabled)
            "ultraLowLatencyMode" -> s.copy(ultraLowLatencyMode = enabled)
            "networkOptimization" -> s.copy(networkOptimization = enabled)
            "backgroundProcessing" -> s.copy(
                backgroundProcessing = enabled,
                backgroundEnabled = if (!enabled) false else s.backgroundEnabled
            )
            "batteryOptimization" -> s.copy(batteryOptimization = enabled)
            "debugMode" -> s.copy(debugMode = enabled)
            "liveTranscript" -> s.copy(liveTranscript = enabled)
            "showLatency" -> s.copy(showLatency = enabled)
            "showTokenUsage" -> s.copy(showTokenUsage = enabled)
            "showActionLogs" -> s.copy(showActionLogs = enabled)
            else -> s
        })
        if (name == "backgroundProcessing" && !enabled) {
            try {
                getApplication<Application>().startService(
                    Intent(getApplication<Application>(), AssistantForegroundService::class.java)
                        .setAction(AssistantForegroundService.ACTION_STOP)
                )
            } catch (_: Exception) { }
        }

        if (name == "notificationReading" && enabled) {
            try {
                getApplication<Application>().startActivity(
                    Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) { }
        }
        if ((name == "allowAccessibilityAutomation" || name == "screenAutomation") && enabled &&
            com.aistudio.mj.wxyt.accessibility.ORBAccessibilityService.instance == null) {
            try {
                getApplication<Application>().startActivity(
                    Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) { }
        }
    }

    // ---- Connection tests using shared client and factory ----

    fun testGeminiConnection() {
        if (secureRepo.geminiApiKey.isEmpty()) {
            _geminiConnectionState.value = ConnectionState.ERROR
            _geminiErrorMessage.value = "API key is not configured"
            return
        }
        _geminiConnectionState.value = ConnectionState.TESTING
        _geminiErrorMessage.value = null
        viewModelScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                val provider = providerFactory.createGemini()
                val result = provider.testConnection()
                val latency = System.currentTimeMillis() - startTime
                when (result) {
                    is com.aistudio.mj.wxyt.domain.ai.ProviderTestResult.Success -> {
                        _geminiConnectionState.value = ConnectionState.SUCCESS
                        _geminiLatency.value = latency
                    }
                    is com.aistudio.mj.wxyt.domain.ai.ProviderTestResult.Failure -> {
                        _geminiConnectionState.value = ConnectionState.ERROR
                        _geminiErrorMessage.value = result.message
                    }
                }
            } catch (e: Exception) {
                _geminiConnectionState.value = ConnectionState.ERROR
                _geminiErrorMessage.value = e.message
            }
        }
    }

    fun testOpenRouterConnection() {
        if (secureRepo.openRouterApiKey.isEmpty()) {
            _openRouterConnectionState.value = ConnectionState.ERROR
            _openRouterErrorMessage.value = "API key is not configured"
            return
        }
        _openRouterConnectionState.value = ConnectionState.TESTING
        _openRouterErrorMessage.value = null
        viewModelScope.launch {
            try {
                val provider = providerFactory.createOpenRouter()
                val result = provider.testConnection()
                when (result) {
                    is com.aistudio.mj.wxyt.domain.ai.ProviderTestResult.Success -> {
                        _openRouterConnectionState.value = ConnectionState.SUCCESS
                    }
                    is com.aistudio.mj.wxyt.domain.ai.ProviderTestResult.Failure -> {
                        _openRouterConnectionState.value = ConnectionState.ERROR
                        _openRouterErrorMessage.value = result.message
                    }
                }
            } catch (e: Exception) {
                _openRouterConnectionState.value = ConnectionState.ERROR
                _openRouterErrorMessage.value = e.message
            }
        }
    }

    fun testOpenCodeConnection() {
        if (secureRepo.openCodeApiKey.isEmpty()) {
            _openCodeConnectionState.value = ConnectionState.ERROR
            _openCodeErrorMessage.value = "API key is not configured"
            return
        }
        _openCodeConnectionState.value = ConnectionState.TESTING
        _openCodeErrorMessage.value = null
        viewModelScope.launch {
            try {
                val provider = providerFactory.createOpenCode()
                val result = provider.testConnection()
                when (result) {
                    is com.aistudio.mj.wxyt.domain.ai.ProviderTestResult.Success -> {
                        _openCodeConnectionState.value = ConnectionState.SUCCESS
                    }
                    is com.aistudio.mj.wxyt.domain.ai.ProviderTestResult.Failure -> {
                        _openCodeConnectionState.value = ConnectionState.ERROR
                        _openCodeErrorMessage.value = result.message
                    }
                }
            } catch (e: Exception) {
                _openCodeConnectionState.value = ConnectionState.ERROR
                _openCodeErrorMessage.value = e.message
            }
        }
    }

    fun testNvidiaConnection() {
        if (secureRepo.nvidiaApiKey.isEmpty()) {
            _nvidiaConnectionState.value = ConnectionState.ERROR
            _nvidiaErrorMessage.value = "API key is not configured"
            return
        }
        _nvidiaConnectionState.value = ConnectionState.TESTING
        _nvidiaErrorMessage.value = null
        viewModelScope.launch {
            try {
                val provider = providerFactory.createNvidia()
                val result = provider.testConnection()
                when (result) {
                    is com.aistudio.mj.wxyt.domain.ai.ProviderTestResult.Success -> {
                        _nvidiaConnectionState.value = ConnectionState.SUCCESS
                    }
                    is com.aistudio.mj.wxyt.domain.ai.ProviderTestResult.Failure -> {
                        _nvidiaConnectionState.value = ConnectionState.ERROR
                        _nvidiaErrorMessage.value = result.message
                    }
                }
            } catch (e: Exception) {
                _nvidiaConnectionState.value = ConnectionState.ERROR
                _nvidiaErrorMessage.value = e.message
            }
        }
    }

    fun testCustomProviderConnection() {
        if (secureRepo.customProviderApiKey.isEmpty()) {
            _customProviderConnectionState.value = ConnectionState.ERROR
            _customProviderErrorMessage.value = "API key is not configured"
            return
        }
        _customProviderConnectionState.value = ConnectionState.TESTING
        _customProviderErrorMessage.value = null
        viewModelScope.launch {
            try {
                val provider = providerFactory.createCustom()
                val result = provider.testConnection()
                when (result) {
                    is com.aistudio.mj.wxyt.domain.ai.ProviderTestResult.Success -> {
                        _customProviderConnectionState.value = ConnectionState.SUCCESS
                    }
                    is com.aistudio.mj.wxyt.domain.ai.ProviderTestResult.Failure -> {
                        _customProviderConnectionState.value = ConnectionState.ERROR
                        _customProviderErrorMessage.value = result.message
                    }
                }
            } catch (e: Exception) {
                _customProviderConnectionState.value = ConnectionState.ERROR
                _customProviderErrorMessage.value = e.message
            }
        }
    }
}
