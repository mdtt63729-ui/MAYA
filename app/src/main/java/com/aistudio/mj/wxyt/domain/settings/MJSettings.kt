package com.aistudio.mj.wxyt.domain.settings

/**
 * Single source of truth for MAYA's user-facing configuration.
 *
 * Fields are intentionally grouped by capability so the settings UI can grow
 * without creating a separate preference store for every feature.
 */
data class MJSettings(
    // AI / Intelligence
    val activeProvider: String = "gemini",
    val activeModel: String = "",
    // Text chat is deliberately independent from the Gemini voice engine.
    val chatProvider: String = "openrouter",
    val chatModel: String = "",
    val reasoningMode: String = "Balanced",
    val planningDepth: String = "Multi-step",
    val temperature: Float = 0.7f,
    val contextLength: Int = 4096,
    val webIntelligence: Boolean = true,
    val factVerification: Boolean = false,
    val selfCorrection: Boolean = true,
    val actionConfidenceThreshold: Float = 0.80f,
    val answerStyle: String = "Normal",
    val proactiveIntelligence: Boolean = false,
    val memoryAutoLearn: Boolean = true,
    val memoryApproval: Boolean = true,

    // Voice / Speech
    val voiceEnabled: Boolean = true,
    val activeVoice: String = "Natural Female",
    val responseLanguage: String = "Hindi",
    val speakingSpeed: Float = 1.0f,
    val voicePitch: Float = 1.0f,
    val expressiveness: Float = 0.70f,
    val emotionIntensity: Float = 0.60f,
    val responseDelayMs: Int = 80,
    val interruptionSensitivity: Float = 0.70f,
    val vadSensitivity: Float = 0.55f,
    val autoBargeIn: Boolean = true,
    val noiseCancellation: Boolean = true,
    val echoCancellation: Boolean = true,
    val voiceInputGain: Float = 1.0f,
    val voiceOutputVolume: Float = 1.0f,
    val autoLanguageDetection: Boolean = true,
    val codeSwitching: Boolean = true,

    // Wake word / owner voice
    val wakeWordEnabled: Boolean = false,
    val wakeWord: String = "Hey MAYA",
    val ownerVoiceEnabled: Boolean = false,
    val ownerVoiceEnrolled: Boolean = false,
    val ownerVoiceThreshold: Float = 0.78f,
    val parentMode: Boolean = false,
    val unauthorizedWarningEnabled: Boolean = true,
    val lockAfterUnauthorizedAttempts: Int = 2,
    val lockOnUnauthorizedVoice: Boolean = true,

    // Personality
    val warmth: Float = 0.75f,
    val humor: Float = 0.55f,
    val playfulness: Float = 0.55f,
    val sarcasm: Float = 0.20f,
    val affection: Float = 0.45f,
    val emotionalExpressiveness: Float = 0.70f,
    val formality: Float = 0.30f,
    val talkativeness: Float = 0.45f,
    val proactivity: Float = 0.65f,
    val nicknameBehavior: Boolean = true,
    val greetingBehavior: Boolean = true,
    val timeAwareGreetings: Boolean = true,

    // Orb / appearance / haptics
    val theme: String = "System",
    val blur: String = "High",
    val motion: String = "120 FPS",
    val orbReactivity: Float = 0.85f,
    val voiceVisualization: Float = 0.85f,
    val idleBreathing: Boolean = true,
    val emotionOrb: Boolean = true,
    val musicReactiveOrb: Boolean = true,
    val glowIntensity: Float = 0.75f,
    val particleDensity: Float = 0.50f,
    val orbSize: Float = 1.0f,
    val animationSpeed: Float = 1.0f,
    val hapticFeedback: Boolean = true,
    val batterySaverAnimation: Boolean = true,

    // Full-screen voice-reactive edge lighting (native, no video asset)
    val edgeLightingEnabled: Boolean = true,
    val edgeLightingReactive: Boolean = true,
    val edgeLightingIntensity: Float = 0.92f,
    val edgeLightingSpeed: Float = 1.0f,
    val edgeLightingIdle: Boolean = false,

    // Automation / device
    val backgroundEnabled: Boolean = false,
    val scheduledActions: Boolean = false,
    val routinesEnabled: Boolean = true,
    val notificationActions: Boolean = false,
    val clipboardActions: Boolean = false,
    val screenAutomation: Boolean = false,
    val allowAccessibilityAutomation: Boolean = false,
    val actionVerification: Boolean = true,
    val autoExecuteSafeActions: Boolean = true,
    val requireConfirmation: Boolean = true,
    val failedActionRetry: Boolean = true,
    val maximumRetryCount: Int = 2,
    val useDeviceContext: Boolean = true,

    // Notifications
    val notificationReading: Boolean = false,
    val importantNotificationFilter: Boolean = true,
    val notificationSummaries: Boolean = true,
    val readNotificationsAloud: Boolean = false,
    val notificationPrivacy: String = "Private",
    val notificationWhitelist: String = "",
    val notificationBlacklist: String = "",

    // Memory / privacy
    val saveHistory: Boolean = true,
    val rememberConversations: Boolean = true,
    val longTermMemoryEnabled: Boolean = true,
    val memoryDepth: Int = 10,
    val localOnlyMemory: Boolean = true,
    val encryptedMemory: Boolean = true,
    val cloudMemory: Boolean = false,
    val privateMode: Boolean = false,
    val biometricActionConfirmation: Boolean = false,
    val aiDataSharing: Boolean = false,

    // Performance / developer
    val ultraLowLatencyMode: Boolean = true,
    val networkOptimization: Boolean = true,
    val backgroundProcessing: Boolean = true,
    val batteryOptimization: Boolean = true,
    val performanceMode: String = "Balanced",
    val debugMode: Boolean = false,
    val liveTranscript: Boolean = false,
    val showLatency: Boolean = false,
    val showTokenUsage: Boolean = false,
    val showActionLogs: Boolean = false,

    // JARVIS cognition / perception / autonomy
    val multimodalVision: Boolean = true,
    val screenshotContext: Boolean = true,
    val cameraAwareness: Boolean = false,
    val screenAwareness: Boolean = true,
    val predictiveIntelligence: Boolean = true,
    val routineLearning: Boolean = true,
    val dryRunMode: Boolean = false,
    val multiAgentOrchestration: Boolean = true,
    val adaptiveModelRouting: Boolean = true,
    val streamingResponses: Boolean = true,
    val persistentRealtimeConnection: Boolean = true,
    val contextCompression: Boolean = true,
    val goalDecomposition: Boolean = true,
    val parallelToolExecution: Boolean = true,
    val failureRecovery: Boolean = true,
    val riskBasedConfirmation: Boolean = true,
    val simulationBeforeRiskyAction: Boolean = true,
    val auditTrail: Boolean = true,
    val selfDiagnostics: Boolean = true,
    val systemHealthMonitoring: Boolean = true,
    val emotionContext: Boolean = true,
    val temporalIntelligence: Boolean = true,
    val knowledgeGraph: Boolean = true,
    val ownerVoiceAntiSpoof: Boolean = true,
    val ownerVoiceEnrollmentRequired: Boolean = true,
    val wakeWordOffline: Boolean = true,
    val wakeWordSensitivity: Float = 0.65f,
    val maxAgentSteps: Int = 8,
    val maxParallelTools: Int = 3,
    val highRiskBiometricConfirmation: Boolean = true,
    val notificationPrivacyStrict: Boolean = true,

    // Existing compatibility fields
    val activeVoiceLegacy: String = "Natural Female"
)
