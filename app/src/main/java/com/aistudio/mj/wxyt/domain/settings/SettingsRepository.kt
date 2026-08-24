package com.aistudio.mj.wxyt.domain.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistent settings store. Every MAYA setting is persisted here so UI,
 * background services and the voice engine observe the same configuration.
 */
class SettingsRepository private constructor(context: Context) {
    companion object {
        @Volatile private var INSTANCE: SettingsRepository? = null
        fun get(context: Context): SettingsRepository = INSTANCE ?: synchronized(this) {
            INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mj_unified_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<MJSettings> = _settings.asStateFlow()

    private fun loadSettings() = MJSettings(
        activeProvider = prefs.getString("activeProvider", "gemini") ?: "gemini",
        activeModel = prefs.getString("activeModel", "") ?: "",
        chatProvider = prefs.getString("chatProvider", "openrouter") ?: "openrouter",
        chatModel = prefs.getString("chatModel", "") ?: "",
        reasoningMode = prefs.getString("reasoningMode", "Balanced") ?: "Balanced",
        planningDepth = prefs.getString("planningDepth", "Multi-step") ?: "Multi-step",
        temperature = prefs.getFloat("temperature", .7f),
        contextLength = prefs.getInt("contextLength", 4096),
        webIntelligence = prefs.getBoolean("webIntelligence", true),
        factVerification = prefs.getBoolean("factVerification", false),
        selfCorrection = prefs.getBoolean("selfCorrection", true),
        actionConfidenceThreshold = prefs.getFloat("actionConfidenceThreshold", .8f),
        answerStyle = prefs.getString("answerStyle", "Normal") ?: "Normal",
        proactiveIntelligence = prefs.getBoolean("proactiveIntelligence", false),
        memoryAutoLearn = prefs.getBoolean("memoryAutoLearn", true),
        memoryApproval = prefs.getBoolean("memoryApproval", true),
        voiceEnabled = prefs.getBoolean("voiceEnabled", true),
        activeVoice = prefs.getString("activeVoice", "Natural Female") ?: "Natural Female",
        responseLanguage = prefs.getString("responseLanguage", "Hindi") ?: "Hindi",
        speakingSpeed = prefs.getFloat("speakingSpeed", 1f),
        voicePitch = prefs.getFloat("voicePitch", 1f),
        expressiveness = prefs.getFloat("expressiveness", .7f),
        emotionIntensity = prefs.getFloat("emotionIntensity", .6f),
        responseDelayMs = prefs.getInt("responseDelayMs", 80),
        interruptionSensitivity = prefs.getFloat("interruptionSensitivity", .7f),
        vadSensitivity = prefs.getFloat("vadSensitivity", .55f),
        autoBargeIn = prefs.getBoolean("autoBargeIn", true),
        noiseCancellation = prefs.getBoolean("noiseCancellation", true),
        echoCancellation = prefs.getBoolean("echoCancellation", true),
        voiceInputGain = prefs.getFloat("voiceInputGain", 1f),
        voiceOutputVolume = prefs.getFloat("voiceOutputVolume", 1f),
        autoLanguageDetection = prefs.getBoolean("autoLanguageDetection", true),
        codeSwitching = prefs.getBoolean("codeSwitching", true),
        wakeWordEnabled = prefs.getBoolean("wakeWordEnabled", false),
        wakeWord = prefs.getString("wakeWord", "Hey MAYA") ?: "Hey MAYA",
        ownerVoiceEnabled = prefs.getBoolean("ownerVoiceEnabled", false),
        ownerVoiceEnrolled = prefs.getBoolean("ownerVoiceEnrolled", false),
        ownerVoiceThreshold = prefs.getFloat("ownerVoiceThreshold", .78f),
        parentMode = prefs.getBoolean("parentMode", false),
        unauthorizedWarningEnabled = prefs.getBoolean("unauthorizedWarningEnabled", true),
        lockAfterUnauthorizedAttempts = prefs.getInt("lockAfterUnauthorizedAttempts", 2),
        lockOnUnauthorizedVoice = prefs.getBoolean("lockOnUnauthorizedVoice", true),
        warmth = prefs.getFloat("warmth", .75f),
        humor = prefs.getFloat("humor", .55f),
        playfulness = prefs.getFloat("playfulness", .55f),
        sarcasm = prefs.getFloat("sarcasm", .2f),
        affection = prefs.getFloat("affection", .45f),
        emotionalExpressiveness = prefs.getFloat("emotionalExpressiveness", .7f),
        formality = prefs.getFloat("formality", .3f),
        talkativeness = prefs.getFloat("talkativeness", .45f),
        proactivity = prefs.getFloat("proactivity", .65f),
        nicknameBehavior = prefs.getBoolean("nicknameBehavior", true),
        greetingBehavior = prefs.getBoolean("greetingBehavior", true),
        timeAwareGreetings = prefs.getBoolean("timeAwareGreetings", true),
        theme = prefs.getString("theme", "System") ?: "System",
        blur = prefs.getString("blur", "High") ?: "High",
        motion = prefs.getString("motion", "120 FPS") ?: "120 FPS",
        orbReactivity = prefs.getFloat("orbReactivity", .85f),
        voiceVisualization = prefs.getFloat("voiceVisualization", .85f),
        idleBreathing = prefs.getBoolean("idleBreathing", true),
        emotionOrb = prefs.getBoolean("emotionOrb", true),
        musicReactiveOrb = prefs.getBoolean("musicReactiveOrb", true),
        glowIntensity = prefs.getFloat("glowIntensity", .75f),
        particleDensity = prefs.getFloat("particleDensity", .5f),
        orbSize = prefs.getFloat("orbSize", 1f),
        animationSpeed = prefs.getFloat("animationSpeed", 1f),
        hapticFeedback = prefs.getBoolean("hapticFeedback", true),
        batterySaverAnimation = prefs.getBoolean("batterySaverAnimation", true),
        edgeLightingEnabled = prefs.getBoolean("edgeLightingEnabled", true),
        edgeLightingReactive = prefs.getBoolean("edgeLightingReactive", true),
        edgeLightingIntensity = prefs.getFloat("edgeLightingIntensity", .92f),
        edgeLightingSpeed = prefs.getFloat("edgeLightingSpeed", 1f),
        edgeLightingIdle = prefs.getBoolean("edgeLightingIdle", false),
        backgroundEnabled = prefs.getBoolean("backgroundEnabled", false),
        scheduledActions = prefs.getBoolean("scheduledActions", false),
        routinesEnabled = prefs.getBoolean("routinesEnabled", true),
        notificationActions = prefs.getBoolean("notificationActions", false),
        clipboardActions = prefs.getBoolean("clipboardActions", false),
        screenAutomation = prefs.getBoolean("screenAutomation", false),
        allowAccessibilityAutomation = prefs.getBoolean("allowAccessibilityAutomation", false),
        actionVerification = prefs.getBoolean("actionVerification", true),
        autoExecuteSafeActions = prefs.getBoolean("autoExecuteSafeActions", true),
        requireConfirmation = prefs.getBoolean("requireConfirmation", true),
        failedActionRetry = prefs.getBoolean("failedActionRetry", true),
        maximumRetryCount = prefs.getInt("maximumRetryCount", 2),
        useDeviceContext = prefs.getBoolean("useDeviceContext", true),
        notificationReading = prefs.getBoolean("notificationReading", false),
        importantNotificationFilter = prefs.getBoolean("importantNotificationFilter", true),
        notificationSummaries = prefs.getBoolean("notificationSummaries", true),
        readNotificationsAloud = prefs.getBoolean("readNotificationsAloud", false),
        notificationPrivacy = prefs.getString("notificationPrivacy", "Private") ?: "Private",
        notificationWhitelist = prefs.getString("notificationWhitelist", "") ?: "",
        notificationBlacklist = prefs.getString("notificationBlacklist", "") ?: "",
        saveHistory = prefs.getBoolean("saveHistory", true),
        rememberConversations = prefs.getBoolean("rememberConversations", true),
        longTermMemoryEnabled = prefs.getBoolean("longTermMemoryEnabled", true),
        memoryDepth = prefs.getInt("memoryDepth", 10),
        localOnlyMemory = prefs.getBoolean("localOnlyMemory", true),
        encryptedMemory = prefs.getBoolean("encryptedMemory", true),
        cloudMemory = prefs.getBoolean("cloudMemory", false),
        privateMode = prefs.getBoolean("privateMode", false),
        biometricActionConfirmation = prefs.getBoolean("biometricActionConfirmation", false),
        aiDataSharing = prefs.getBoolean("aiDataSharing", false),
        ultraLowLatencyMode = prefs.getBoolean("ultraLowLatencyMode", true),
        networkOptimization = prefs.getBoolean("networkOptimization", true),
        backgroundProcessing = prefs.getBoolean("backgroundProcessing", true),
        batteryOptimization = prefs.getBoolean("batteryOptimization", true),
        performanceMode = prefs.getString("performanceMode", "Balanced") ?: "Balanced",
        debugMode = prefs.getBoolean("debugMode", false),
        liveTranscript = prefs.getBoolean("liveTranscript", false),
        showLatency = prefs.getBoolean("showLatency", false),
        showTokenUsage = prefs.getBoolean("showTokenUsage", false),
        showActionLogs = prefs.getBoolean("showActionLogs", false),
        multimodalVision = prefs.getBoolean("multimodalVision", true),
        screenshotContext = prefs.getBoolean("screenshotContext", true),
        cameraAwareness = prefs.getBoolean("cameraAwareness", false),
        screenAwareness = prefs.getBoolean("screenAwareness", true),
        predictiveIntelligence = prefs.getBoolean("predictiveIntelligence", true),
        routineLearning = prefs.getBoolean("routineLearning", true),
        dryRunMode = prefs.getBoolean("dryRunMode", false),
        multiAgentOrchestration = prefs.getBoolean("multiAgentOrchestration", true),
        adaptiveModelRouting = prefs.getBoolean("adaptiveModelRouting", true),
        streamingResponses = prefs.getBoolean("streamingResponses", true),
        persistentRealtimeConnection = prefs.getBoolean("persistentRealtimeConnection", true),
        contextCompression = prefs.getBoolean("contextCompression", true),
        goalDecomposition = prefs.getBoolean("goalDecomposition", true),
        parallelToolExecution = prefs.getBoolean("parallelToolExecution", true),
        failureRecovery = prefs.getBoolean("failureRecovery", true),
        riskBasedConfirmation = prefs.getBoolean("riskBasedConfirmation", true),
        simulationBeforeRiskyAction = prefs.getBoolean("simulationBeforeRiskyAction", true),
        auditTrail = prefs.getBoolean("auditTrail", true),
        selfDiagnostics = prefs.getBoolean("selfDiagnostics", true),
        systemHealthMonitoring = prefs.getBoolean("systemHealthMonitoring", true),
        emotionContext = prefs.getBoolean("emotionContext", true),
        temporalIntelligence = prefs.getBoolean("temporalIntelligence", true),
        knowledgeGraph = prefs.getBoolean("knowledgeGraph", true),
        ownerVoiceAntiSpoof = prefs.getBoolean("ownerVoiceAntiSpoof", true),
        ownerVoiceEnrollmentRequired = prefs.getBoolean("ownerVoiceEnrollmentRequired", true),
        wakeWordOffline = prefs.getBoolean("wakeWordOffline", true),
        wakeWordSensitivity = prefs.getFloat("wakeWordSensitivity", .65f),
        maxAgentSteps = prefs.getInt("maxAgentSteps", 8),
        maxParallelTools = prefs.getInt("maxParallelTools", 3),
        highRiskBiometricConfirmation = prefs.getBoolean("highRiskBiometricConfirmation", true),
        notificationPrivacyStrict = prefs.getBoolean("notificationPrivacyStrict", true),
        activeVoiceLegacy = prefs.getString("activeVoiceLegacy", "Natural Female") ?: "Natural Female"
    )

    fun updateSettings(s: MJSettings) {
        val normalized = s.copy(
            temperature = s.temperature.coerceIn(0f, 1f),
            contextLength = s.contextLength.coerceIn(256, 32768),
            actionConfidenceThreshold = s.actionConfidenceThreshold.coerceIn(0f, 1f),
            speakingSpeed = s.speakingSpeed.coerceIn(0.5f, 2f),
            voicePitch = s.voicePitch.coerceIn(0.5f, 1.5f),
            expressiveness = s.expressiveness.coerceIn(0f, 1f),
            emotionIntensity = s.emotionIntensity.coerceIn(0f, 1f),
            responseDelayMs = s.responseDelayMs.coerceIn(0, 500),
            interruptionSensitivity = s.interruptionSensitivity.coerceIn(0f, 1f),
            vadSensitivity = s.vadSensitivity.coerceIn(0f, 1f),
            voiceInputGain = s.voiceInputGain.coerceIn(0.25f, 2.5f),
            voiceOutputVolume = s.voiceOutputVolume.coerceIn(0f, 1f),
            ownerVoiceThreshold = s.ownerVoiceThreshold.coerceIn(0.55f, 0.95f),
            lockAfterUnauthorizedAttempts = s.lockAfterUnauthorizedAttempts.coerceIn(2, 5),
            memoryDepth = s.memoryDepth.coerceIn(1, 50),
            maximumRetryCount = s.maximumRetryCount.coerceIn(0, 5),
            wakeWordSensitivity = s.wakeWordSensitivity.coerceIn(0f, 1f),
            maxAgentSteps = s.maxAgentSteps.coerceIn(1, 20),
            maxParallelTools = s.maxParallelTools.coerceIn(1, 8),
            orbReactivity = s.orbReactivity.coerceIn(0f, 1f),
            voiceVisualization = s.voiceVisualization.coerceIn(0f, 1f),
            glowIntensity = s.glowIntensity.coerceIn(0f, 1.5f),
            particleDensity = s.particleDensity.coerceIn(0f, 1f),
            orbSize = s.orbSize.coerceIn(0.72f, 1.28f),
            animationSpeed = s.animationSpeed.coerceIn(0.25f, 2f),
            edgeLightingIntensity = s.edgeLightingIntensity.coerceIn(0f, 1.5f),
            edgeLightingSpeed = s.edgeLightingSpeed.coerceIn(0.25f, 2f)
        )
        prefs.edit().apply {
            putString("activeProvider", normalized.activeProvider)
            putString("activeModel", normalized.activeModel)
            putString("chatProvider", normalized.chatProvider)
            putString("chatModel", normalized.chatModel)
            putString("reasoningMode", normalized.reasoningMode)
            putString("planningDepth", normalized.planningDepth)
            putFloat("temperature", normalized.temperature)
            putInt("contextLength", normalized.contextLength)
            putBoolean("webIntelligence", normalized.webIntelligence)
            putBoolean("factVerification", normalized.factVerification)
            putBoolean("selfCorrection", normalized.selfCorrection)
            putFloat("actionConfidenceThreshold", normalized.actionConfidenceThreshold)
            putString("answerStyle", normalized.answerStyle)
            putBoolean("proactiveIntelligence", normalized.proactiveIntelligence)
            putBoolean("memoryAutoLearn", normalized.memoryAutoLearn)
            putBoolean("memoryApproval", normalized.memoryApproval)
            putBoolean("voiceEnabled", normalized.voiceEnabled)
            putString("activeVoice", normalized.activeVoice)
            putString("responseLanguage", normalized.responseLanguage)
            putFloat("speakingSpeed", normalized.speakingSpeed)
            putFloat("voicePitch", normalized.voicePitch)
            putFloat("expressiveness", normalized.expressiveness)
            putFloat("emotionIntensity", normalized.emotionIntensity)
            putInt("responseDelayMs", normalized.responseDelayMs.coerceIn(0, 500))
            putFloat("interruptionSensitivity", normalized.interruptionSensitivity)
            putFloat("vadSensitivity", normalized.vadSensitivity)
            putBoolean("autoBargeIn", normalized.autoBargeIn)
            putBoolean("noiseCancellation", normalized.noiseCancellation)
            putBoolean("echoCancellation", normalized.echoCancellation)
            putFloat("voiceInputGain", normalized.voiceInputGain)
            putFloat("voiceOutputVolume", normalized.voiceOutputVolume)
            putBoolean("autoLanguageDetection", normalized.autoLanguageDetection)
            putBoolean("codeSwitching", normalized.codeSwitching)
            putBoolean("wakeWordEnabled", normalized.wakeWordEnabled)
            putString("wakeWord", normalized.wakeWord)
            putBoolean("ownerVoiceEnabled", normalized.ownerVoiceEnabled)
            putBoolean("ownerVoiceEnrolled", normalized.ownerVoiceEnrolled)
            putFloat("ownerVoiceThreshold", normalized.ownerVoiceThreshold)
            putBoolean("parentMode", normalized.parentMode)
            putBoolean("unauthorizedWarningEnabled", normalized.unauthorizedWarningEnabled)
            putInt("lockAfterUnauthorizedAttempts", normalized.lockAfterUnauthorizedAttempts.coerceIn(2, 5))
            putBoolean("lockOnUnauthorizedVoice", normalized.lockOnUnauthorizedVoice)
            putFloat("warmth", normalized.warmth); putFloat("humor", normalized.humor)
            putFloat("playfulness", normalized.playfulness); putFloat("sarcasm", normalized.sarcasm)
            putFloat("affection", normalized.affection); putFloat("emotionalExpressiveness", normalized.emotionalExpressiveness)
            putFloat("formality", normalized.formality); putFloat("talkativeness", normalized.talkativeness)
            putFloat("proactivity", normalized.proactivity)
            putBoolean("nicknameBehavior", normalized.nicknameBehavior)
            putBoolean("greetingBehavior", normalized.greetingBehavior)
            putBoolean("timeAwareGreetings", normalized.timeAwareGreetings)
            putString("theme", normalized.theme); putString("blur", normalized.blur); putString("motion", normalized.motion)
            putFloat("orbReactivity", normalized.orbReactivity); putFloat("voiceVisualization", normalized.voiceVisualization)
            putBoolean("idleBreathing", normalized.idleBreathing); putBoolean("emotionOrb", normalized.emotionOrb)
            putBoolean("musicReactiveOrb", normalized.musicReactiveOrb); putFloat("glowIntensity", normalized.glowIntensity)
            putFloat("particleDensity", normalized.particleDensity); putFloat("orbSize", normalized.orbSize)
            putFloat("animationSpeed", normalized.animationSpeed); putBoolean("hapticFeedback", normalized.hapticFeedback)
            putBoolean("batterySaverAnimation", normalized.batterySaverAnimation)
            putBoolean("edgeLightingEnabled", normalized.edgeLightingEnabled)
            putBoolean("edgeLightingReactive", normalized.edgeLightingReactive)
            putFloat("edgeLightingIntensity", normalized.edgeLightingIntensity)
            putFloat("edgeLightingSpeed", normalized.edgeLightingSpeed)
            putBoolean("edgeLightingIdle", normalized.edgeLightingIdle)
            putBoolean("backgroundEnabled", normalized.backgroundEnabled); putBoolean("scheduledActions", normalized.scheduledActions)
            putBoolean("routinesEnabled", normalized.routinesEnabled); putBoolean("notificationActions", normalized.notificationActions)
            putBoolean("clipboardActions", normalized.clipboardActions); putBoolean("screenAutomation", normalized.screenAutomation)
            putBoolean("allowAccessibilityAutomation", normalized.allowAccessibilityAutomation)
            putBoolean("actionVerification", normalized.actionVerification); putBoolean("autoExecuteSafeActions", normalized.autoExecuteSafeActions)
            putBoolean("requireConfirmation", normalized.requireConfirmation); putBoolean("failedActionRetry", normalized.failedActionRetry)
            putInt("maximumRetryCount", normalized.maximumRetryCount.coerceIn(0, 5)); putBoolean("useDeviceContext", normalized.useDeviceContext)
            putBoolean("notificationReading", normalized.notificationReading); putBoolean("importantNotificationFilter", normalized.importantNotificationFilter)
            putBoolean("notificationSummaries", normalized.notificationSummaries); putBoolean("readNotificationsAloud", normalized.readNotificationsAloud)
            putString("notificationPrivacy", normalized.notificationPrivacy); putString("notificationWhitelist", normalized.notificationWhitelist)
            putString("notificationBlacklist", normalized.notificationBlacklist)
            putBoolean("saveHistory", normalized.saveHistory); putBoolean("rememberConversations", normalized.rememberConversations)
            putBoolean("longTermMemoryEnabled", normalized.longTermMemoryEnabled); putInt("memoryDepth", normalized.memoryDepth.coerceIn(1, 50))
            putBoolean("localOnlyMemory", normalized.localOnlyMemory); putBoolean("encryptedMemory", normalized.encryptedMemory)
            putBoolean("cloudMemory", normalized.cloudMemory); putBoolean("privateMode", normalized.privateMode)
            putBoolean("biometricActionConfirmation", normalized.biometricActionConfirmation); putBoolean("aiDataSharing", normalized.aiDataSharing)
            putBoolean("ultraLowLatencyMode", normalized.ultraLowLatencyMode); putBoolean("networkOptimization", normalized.networkOptimization)
            putBoolean("backgroundProcessing", normalized.backgroundProcessing); putBoolean("batteryOptimization", normalized.batteryOptimization)
            putString("performanceMode", normalized.performanceMode); putBoolean("debugMode", normalized.debugMode)
            putBoolean("liveTranscript", normalized.liveTranscript); putBoolean("showLatency", normalized.showLatency)
            putBoolean("showTokenUsage", normalized.showTokenUsage); putBoolean("showActionLogs", normalized.showActionLogs)
            putBoolean("multimodalVision", normalized.multimodalVision)
            putBoolean("screenshotContext", normalized.screenshotContext)
            putBoolean("cameraAwareness", normalized.cameraAwareness)
            putBoolean("screenAwareness", normalized.screenAwareness)
            putBoolean("predictiveIntelligence", normalized.predictiveIntelligence)
            putBoolean("routineLearning", normalized.routineLearning)
            putBoolean("dryRunMode", normalized.dryRunMode)
            putBoolean("multiAgentOrchestration", normalized.multiAgentOrchestration)
            putBoolean("adaptiveModelRouting", normalized.adaptiveModelRouting)
            putBoolean("streamingResponses", normalized.streamingResponses)
            putBoolean("persistentRealtimeConnection", normalized.persistentRealtimeConnection)
            putBoolean("contextCompression", normalized.contextCompression)
            putBoolean("goalDecomposition", normalized.goalDecomposition)
            putBoolean("parallelToolExecution", normalized.parallelToolExecution)
            putBoolean("failureRecovery", normalized.failureRecovery)
            putBoolean("riskBasedConfirmation", normalized.riskBasedConfirmation)
            putBoolean("simulationBeforeRiskyAction", normalized.simulationBeforeRiskyAction)
            putBoolean("auditTrail", normalized.auditTrail)
            putBoolean("selfDiagnostics", normalized.selfDiagnostics)
            putBoolean("systemHealthMonitoring", normalized.systemHealthMonitoring)
            putBoolean("emotionContext", normalized.emotionContext)
            putBoolean("temporalIntelligence", normalized.temporalIntelligence)
            putBoolean("knowledgeGraph", normalized.knowledgeGraph)
            putBoolean("ownerVoiceAntiSpoof", normalized.ownerVoiceAntiSpoof)
            putBoolean("ownerVoiceEnrollmentRequired", normalized.ownerVoiceEnrollmentRequired)
            putBoolean("wakeWordOffline", normalized.wakeWordOffline)
            putFloat("wakeWordSensitivity", normalized.wakeWordSensitivity)
            putInt("maxAgentSteps", normalized.maxAgentSteps.coerceIn(1, 20))
            putInt("maxParallelTools", normalized.maxParallelTools.coerceIn(1, 8))
            putBoolean("highRiskBiometricConfirmation", normalized.highRiskBiometricConfirmation)
            putBoolean("notificationPrivacyStrict", normalized.notificationPrivacyStrict)
            putString("activeVoiceLegacy", normalized.activeVoiceLegacy)
        }.apply()
        _settings.value = normalized
    }

    fun clearSettings() {
        prefs.edit().clear().apply()
        _settings.value = loadSettings()
    }
}
