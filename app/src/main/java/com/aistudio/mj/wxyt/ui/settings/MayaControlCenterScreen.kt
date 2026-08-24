package com.aistudio.mj.wxyt.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.mj.wxyt.domain.settings.SettingsViewModel
import com.aistudio.mj.wxyt.domain.jarvis.MayaHealthMonitor
import com.aistudio.mj.wxyt.domain.jarvis.MayaPredictiveEngine
import com.aistudio.mj.wxyt.domain.jarvis.MayaRoutineRepository
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MayaControlCenterScreen(viewModel: SettingsViewModel, onBack: () -> Unit, onOwnerVoiceSetup: () -> Unit) {
    val s by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var assistantIsDefault by remember { mutableStateOf(DefaultAssistantController.isDefaultAssistant(context)) }
    val assistantLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        assistantIsDefault = DefaultAssistantController.isDefaultAssistant(context)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                assistantIsDefault = DefaultAssistantController.isDefaultAssistant(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val health = remember { MayaHealthMonitor(context) }.snapshot()
    val predictions = remember(s) { MayaPredictiveEngine(context).suggestions(s) }
    val routines = remember { MayaRoutineRepository(context) }.all()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MAYA Command Center") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { SectionTitle("🧠 INTELLIGENCE") }
            item { ChoiceRow("Reasoning Mode", s.reasoningMode, listOf("Fast","Balanced","Deep")) { viewModel.setReasoningMode(it) } }
            item { ChoiceRow("Planning Depth", s.planningDepth, listOf("Single-step","Multi-step","Autonomous")) { viewModel.setPlanningDepth(it) } }
            item { ChoiceRow("Answer Style", s.answerStyle, listOf("Short","Normal","Detailed")) { viewModel.setAnswerStyle(it) } }
            item { SliderRow("Action confidence", s.actionConfidenceThreshold, { viewModel.setFloatSetting("actionConfidenceThreshold", it) }) }
            item { SwitchRow("Web Intelligence", "Use live information when a task needs it", s.webIntelligence) { viewModel.setBooleanSetting("webIntelligence", it) } }
            item { SwitchRow("Fact Verification", "Cross-check important answers", s.factVerification) { viewModel.setBooleanSetting("factVerification", it) } }
            item { SwitchRow("Self-Correction", "Retry with an alternative when an action fails", s.selfCorrection) { viewModel.setBooleanSetting("selfCorrection", it) } }
            item { SwitchRow("Proactive Intelligence", "Surface useful context without being asked", s.proactiveIntelligence) { viewModel.updateSettings(s.copy(proactiveIntelligence = it)) } }

            item { SectionTitle("🎙 VOICE ENGINE") }
            item { SwitchRow("Voice engine", "Enable MAYA voice input/output", s.voiceEnabled) { viewModel.setVoiceEnabled(it) } }
            item { ChoiceRow("Voice", s.activeVoice, listOf("Natural Female", "Natural Male")) { viewModel.setActiveVoice(it) } }
            item { ChoiceRow("Response language", s.responseLanguage, listOf("Bengali", "English", "Hindi")) { viewModel.setResponseLanguage(it) } }
            item { SliderRow("Speaking speed", s.speakingSpeed.coerceIn(.5f,2f), { viewModel.setSpeakingSpeedValue(it) }, .5f, 2f) }
            item { SliderRow("Voice pitch", s.voicePitch, { viewModel.setFloatSetting("voicePitch", it) }) }
            item { SliderRow("Expressiveness", s.expressiveness, { viewModel.setFloatSetting("expressiveness", it) }) }
            item { SliderRow("Emotion intensity", s.emotionIntensity, { viewModel.setFloatSetting("emotionIntensity", it) }) }
            item { SliderRow("Response delay", s.responseDelayMs / 500f, { viewModel.setResponseDelayMs((it * 500f).toInt()) }) }
            item { SliderRow("Interruption sensitivity", s.interruptionSensitivity, { viewModel.setFloatSetting("interruptionSensitivity", it) }) }
            item { SliderRow("VAD sensitivity", s.vadSensitivity, { viewModel.setFloatSetting("vadSensitivity", it) }) }
            item { SliderRow("Microphone gain", (s.voiceInputGain - 0.25f) / 2.25f, { viewModel.setFloatSetting("voiceInputGain", 0.25f + it * 2.25f) }) }
            item { SliderRow("Voice output volume", s.voiceOutputVolume, { viewModel.setFloatSetting("voiceOutputVolume", it) }) }
            item { SwitchRow("Auto Barge-in", "MAYA can stop speaking when you start talking", s.autoBargeIn) { viewModel.setBooleanSetting("autoBargeIn", it) } }
            item { SwitchRow("Noise Cancellation", "Suppress background noise", s.noiseCancellation) { viewModel.setBooleanSetting("noiseCancellation", it) } }
            item { SwitchRow("Echo Cancellation", "Reduce speaker-to-microphone feedback", s.echoCancellation) { viewModel.setBooleanSetting("echoCancellation", it) } }
            item { SwitchRow("Auto Language Detection", "Detect the language you speak", s.autoLanguageDetection) { viewModel.setBooleanSetting("autoLanguageDetection", it) } }
            item { SwitchRow("Bengali + English Code-switching", "Allow natural mixed-language conversation", s.codeSwitching) { viewModel.setBooleanSetting("codeSwitching", it) } }

            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Default Assistant", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (assistantIsDefault)
                                "MAYA is the current Android assistant. System assistant requests can be routed to MAYA."
                            else
                                "Make MAYA the Android assistant. Android will ask you to confirm the change."
                        , fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = {
                                if (assistantIsDefault) {
                                    activity?.let { DefaultAssistantController.openSystemAssistantSettings(it) }
                                } else {
                                    val requestIntent = DefaultAssistantController.createRequestIntent(context)
                                    if (requestIntent != null) {
                                        assistantLauncher.launch(requestIntent)
                                    } else {
                                        activity?.let { DefaultAssistantController.openSystemAssistantSettings(it) }
                                    }
                                }
                            },
                            enabled = activity != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (assistantIsDefault) "Manage Default Assistant" else "Set MAYA as Default Assistant")
                        }
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            Text(
                                "This Android version does not expose the assistant role API; the system voice-input/default-app settings will open instead.",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { SectionTitle("🔐 OWNER VOICE / PARENT MODE") }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Parent Mode", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (s.parentMode) "Only the enrolled owner voice is allowed to activate MAYA."
                            else "Anyone can activate MAYA after the wake word.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                        )
                        Switch(
                            checked = s.parentMode,
                            onCheckedChange = { enabled ->
                                if (enabled && !s.ownerVoiceEnrolled) {
                                    onOwnerVoiceSetup()
                                } else {
                                    viewModel.setParentMode(enabled)
                                }
                            }
                        )
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text("Wake phrase: ${s.wakeWord}", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (s.ownerVoiceEnrolled) "Owner voice is enrolled on this device." else "Owner voice is not enrolled yet.",
                            fontSize = 12.sp,
                            color = if (s.ownerVoiceEnrolled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Button(
                            onClick = onOwnerVoiceSetup,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) {
                            Text(if (s.ownerVoiceEnrolled) "Re-enroll Owner Voice" else "Set Up Owner Voice")
                        }
                        if (s.parentMode) {
                            SliderRow("Match threshold", s.ownerVoiceThreshold, { viewModel.setOwnerVoiceThreshold(it) }, .55f, .95f)
                            SliderRow("Lock after attempts", (s.lockAfterUnauthorizedAttempts - 2) / 3f, {
                                val attempts = (2 + (it * 3).toInt()).coerceIn(2,5)
                                viewModel.setLockAfterUnauthorizedAttempts(attempts)
                            })
                            Text("${s.lockAfterUnauthorizedAttempts} unauthorized attempts", fontSize = 12.sp)
                            SwitchRow("Unauthorized warning", "Warn an unrecognized speaker", s.unauthorizedWarningEnabled) { viewModel.setBooleanSetting("unauthorizedWarningEnabled", it) }
                            SwitchRow("Lock on repeated unauthorized voice", "Uses the configured Accessibility lock action", s.lockOnUnauthorizedVoice) { viewModel.setBooleanSetting("lockOnUnauthorizedVoice", it) }
                        }
                    }
                }
            }

            item { SectionTitle("💕 PERSONALITY") }
            item { SliderRow("Warmth", s.warmth, { viewModel.setFloatSetting("warmth",it) }) }
            item { SliderRow("Humor", s.humor, { viewModel.setFloatSetting("humor",it) }) }
            item { SliderRow("Playfulness", s.playfulness, { viewModel.setFloatSetting("playfulness",it) }) }
            item { SliderRow("Affection", s.affection, { viewModel.setFloatSetting("affection",it) }) }
            item { SliderRow("Sarcasm", s.sarcasm, { viewModel.setFloatSetting("sarcasm",it) }) }
            item { SliderRow("Emotional expressiveness", s.emotionalExpressiveness, { viewModel.setFloatSetting("emotionalExpressiveness",it) }) }
            item { SliderRow("Formality", s.formality, { viewModel.setFloatSetting("formality",it) }) }
            item { SliderRow("Talkativeness", s.talkativeness, { viewModel.setFloatSetting("talkativeness",it) }) }
            item { SliderRow("Proactivity", s.proactivity, { viewModel.setFloatSetting("proactivity",it) }) }
            item { SwitchRow("Nickname behavior", "Use the configured owner nickname naturally", s.nicknameBehavior) { viewModel.setBooleanSetting("nicknameBehavior",it) } }
            item { SwitchRow("Time-aware greetings", "Morning/evening context-aware greetings", s.timeAwareGreetings) { viewModel.setBooleanSetting("timeAwareGreetings",it) } }
            item { SwitchRow("Greetings", "Allow MAYA to greet when a session starts", s.greetingBehavior) { viewModel.setBooleanSetting("greetingBehavior",it) } }

            item { SectionTitle("👁 ORB / EXPERIENCE") }
            item { ChoiceRow("Motion", s.motion, listOf("Standard","90 FPS","120 FPS","Battery Saver")) { viewModel.setMotion(it) } }
            item { SliderRow("Orb reactivity", s.orbReactivity, { viewModel.setFloatSetting("orbReactivity",it) }) }
            item { SliderRow("Glow intensity", s.glowIntensity, { viewModel.setFloatSetting("glowIntensity",it) }) }
            item { SliderRow("Particle density", s.particleDensity, { viewModel.setFloatSetting("particleDensity",it) }) }
            item { SliderRow("Orb size", ((s.orbSize - .72f) / .56f).coerceIn(0f,1f), { viewModel.setFloatSetting("orbSize", .72f + it * .56f) }) }
            item { SliderRow("Animation speed", ((s.animationSpeed - .25f) / 1.75f).coerceIn(0f,1f), { viewModel.setFloatSetting("animationSpeed", .25f + it * 1.75f) }) }
            item { SwitchRow("Emotion Orb", "Reflect assistant state and emotional tone", s.emotionOrb) { viewModel.setBooleanSetting("emotionOrb",it) } }
            item { SwitchRow("Audio-reactive Orb", "React to MAYA voice energy while speaking/listening", s.musicReactiveOrb) { viewModel.setBooleanSetting("musicReactiveOrb",it) } }
            item { SwitchRow("Idle breathing", "Keep the liquid orb gently breathing while idle", s.idleBreathing) { viewModel.setBooleanSetting("idleBreathing",it) } }
            item { SwitchRow("Battery-saver animation", "Reduce orb motion when Battery Saver performance is selected", s.batterySaverAnimation) { viewModel.setBooleanSetting("batterySaverAnimation",it) } }
            item { SwitchRow("Haptic feedback", "Tactile response for important state changes", s.hapticFeedback) { viewModel.setBooleanSetting("hapticFeedback",it) } }

            item { SectionTitle("✨ MAX-STYLE EDGE LIGHTING") }
            item { SwitchRow("Edge lighting", "Illuminate the full screen edge when MAYA activates or speaks", s.edgeLightingEnabled) { viewModel.setBooleanSetting("edgeLightingEnabled", it) } }
            item { SwitchRow("Voice-reactive edge", "Make edge brightness breathe with microphone and MAYA audio", s.edgeLightingReactive) { viewModel.setBooleanSetting("edgeLightingReactive", it) } }
            item { SliderRow("Edge intensity", s.edgeLightingIntensity / 1.5f, { viewModel.setFloatSetting("edgeLightingIntensity", it * 1.5f) }) }
            item { SliderRow("Edge animation speed", ((s.edgeLightingSpeed - .25f) / 1.75f).coerceIn(0f, 1f), { viewModel.setFloatSetting("edgeLightingSpeed", .25f + it * 1.75f) }) }
            item { SwitchRow("Idle edge glow", "Keep a very subtle edge aura while MAYA is idle", s.edgeLightingIdle) { viewModel.setBooleanSetting("edgeLightingIdle", it) } }

            item { SectionTitle("🤖 AUTOMATION") }
            item { SwitchRow("Safe auto-actions", "Execute low-risk actions without asking every time", s.autoExecuteSafeActions) { viewModel.setBooleanSetting("autoExecuteSafeActions",it) } }
            item { SwitchRow("Action verification", "Verify a tool result before reporting success", s.actionVerification) { viewModel.setBooleanSetting("actionVerification",it) } }
            item { SwitchRow("Multi-step routines", "Allow routines such as sleep/work modes", s.routinesEnabled) { viewModel.setBooleanSetting("routinesEnabled",it) } }
            item { SwitchRow("Scheduled actions", "Allow saved routines to be scheduled by the scheduler", s.scheduledActions) { viewModel.setBooleanSetting("scheduledActions",it) } }
            item { SwitchRow("Screen automation", "Allow accessibility-driven UI actions", s.screenAutomation) { viewModel.setBooleanSetting("screenAutomation",it) } }
            item { SwitchRow("Accessibility automation", "Allow MAYA to operate supported UI controls", s.allowAccessibilityAutomation) { viewModel.setBooleanSetting("allowAccessibilityAutomation",it) } }
            item { SwitchRow("Failed-action retry", "Retry recoverable failures", s.failedActionRetry) { viewModel.setBooleanSetting("failedActionRetry",it) } }
            item { SwitchRow("Require confirmation", "Ask before medium/high-risk device actions", s.requireConfirmation) { viewModel.setBooleanSetting("requireConfirmation",it) } }
            item { SliderRow("Maximum retry count", s.maximumRetryCount / 5f, { viewModel.setMaximumRetryCount((it * 5f).toInt()) }, 0f, 1f) }
            item { SwitchRow("Use device context", "Include battery, connectivity and device state in planning", s.useDeviceContext) { viewModel.setBooleanSetting("useDeviceContext",it) } }

            item { SectionTitle("🔔 NOTIFICATIONS") }
            item { SwitchRow("Notification reading", "Allow MAYA to read notifications", s.notificationReading) { viewModel.setBooleanSetting("notificationReading",it) } }
            item { SwitchRow("Important filter", "Prioritize important notifications", s.importantNotificationFilter) { viewModel.setBooleanSetting("importantNotificationFilter",it) } }
            item { SwitchRow("Notification summaries", "Keep notification handling concise", s.notificationSummaries) { viewModel.setBooleanSetting("notificationSummaries",it) } }
            item { SwitchRow("Read aloud", "Speak selected notifications through device TTS", s.readNotificationsAloud) { viewModel.setBooleanSetting("readNotificationsAloud",it) } }
            item { ChoiceRow("Notification privacy", s.notificationPrivacy, listOf("Private", "Standard")) { viewModel.updateSettings(s.copy(notificationPrivacy = it)) } }
            item {
                OutlinedTextField(
                    value = s.notificationWhitelist,
                    onValueChange = { viewModel.updateSettings(s.copy(notificationWhitelist = it)) },
                    label = { Text("Notification whitelist (package names)") },
                    placeholder = { Text("com.whatsapp, com.google.android.gm") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = s.notificationBlacklist,
                    onValueChange = { viewModel.updateSettings(s.copy(notificationBlacklist = it)) },
                    label = { Text("Notification blacklist (package names)") },
                    placeholder = { Text("com.example.privateapp") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item { SectionTitle("🧠 MEMORY / PRIVACY") }
            item { SwitchRow("Long-term memory", "Keep useful memories across sessions", s.longTermMemoryEnabled) { viewModel.setBooleanSetting("longTermMemoryEnabled",it) } }
            item { SwitchRow("Memory auto-learn", "Detect useful preferences from conversation", s.memoryAutoLearn) { viewModel.setBooleanSetting("memoryAutoLearn",it) } }
            item { SwitchRow("Memory approval", "Ask before saving inferred memories", s.memoryApproval) { viewModel.setBooleanSetting("memoryApproval",it) } }
            item { SwitchRow("Private Mode", "Reduce persistence and background behavior", s.privateMode) { viewModel.setBooleanSetting("privateMode",it) } }
            item { SwitchRow("Save chat history", "Persist conversations locally", s.saveHistory) { viewModel.setBooleanSetting("saveHistory",it) } }
            item { SwitchRow("Remember conversations", "Use previous conversations as context", s.rememberConversations) { viewModel.setBooleanSetting("rememberConversations",it) } }
            item { SliderRow("Memory depth", (s.memoryDepth - 1) / 49f, { viewModel.setMemoryDepth(1 + (it * 49f).toInt()) }) }
            item { SwitchRow("Biometric action confirmation", "Require biometric confirmation when a critical action is gated", s.biometricActionConfirmation) { viewModel.setBooleanSetting("biometricActionConfirmation",it) } }

            item { SectionTitle("🧬 JARVIS COGNITION / PERCEPTION") }
            item { SwitchRow("Multimodal Vision", "Allow visual/screenshot context when available", s.multimodalVision) { viewModel.setBooleanSetting("multimodalVision", it) } }
            item { SwitchRow("Screen Awareness", "Understand the current app/screen context", s.screenAwareness) { viewModel.setBooleanSetting("screenAwareness", it) } }
            item { SwitchRow("Screenshot context", "Allow the current screen snapshot/context to be included when permitted", s.screenshotContext) { viewModel.setBooleanSetting("screenshotContext", it) } }
            item { SwitchRow("Predictive Intelligence", "Suggest useful actions from current context", s.predictiveIntelligence) { viewModel.setBooleanSetting("predictiveIntelligence", it) } }
            item { SwitchRow("Routine Learning", "Detect repeated workflows and propose routines", s.routineLearning) { viewModel.setBooleanSetting("routineLearning", it) } }
            item { SwitchRow("Knowledge Graph", "Store local relationships between memories and projects", s.knowledgeGraph) { viewModel.setBooleanSetting("knowledgeGraph", it) } }
            item { SwitchRow("Emotion Context", "Adapt response style to conversational cues", s.emotionContext) { viewModel.setBooleanSetting("emotionContext", it) } }
            item { SwitchRow("Temporal Intelligence", "Understand time, duration, deadlines and routines", s.temporalIntelligence) { viewModel.setBooleanSetting("temporalIntelligence", it) } }
            item { SwitchRow("Goal Decomposition", "Break complex requests into executable steps", s.goalDecomposition) { viewModel.setBooleanSetting("goalDecomposition", it) } }
            item { SwitchRow("Multi-Agent Orchestration", "Coordinate specialized reasoning/tool agents", s.multiAgentOrchestration) { viewModel.setBooleanSetting("multiAgentOrchestration", it) } }
            item { SwitchRow("Adaptive Model Routing", "Choose a suitable model based on task complexity", s.adaptiveModelRouting) { viewModel.setBooleanSetting("adaptiveModelRouting", it) } }
            item { SwitchRow("Context Compression", "Keep long sessions responsive by compressing stale context", s.contextCompression) { viewModel.setBooleanSetting("contextCompression", it) } }

            item { SectionTitle("🛡️ AUTONOMY / SAFETY") }
            item { SwitchRow("Failure Recovery", "Try safe alternatives when a tool fails", s.failureRecovery) { viewModel.setBooleanSetting("failureRecovery", it) } }
            item { SwitchRow("Risk-Based Confirmation", "Automatically require confirmation for higher-risk actions", s.riskBasedConfirmation) { viewModel.setBooleanSetting("riskBasedConfirmation", it) } }
            item { SwitchRow("Simulation Before Risky Actions", "Preview the intended operation before execution", s.simulationBeforeRiskyAction) { viewModel.setBooleanSetting("simulationBeforeRiskyAction", it) } }
            item { SwitchRow("Dry Run Mode", "Plan and simulate actions without executing them", s.dryRunMode) { viewModel.setBooleanSetting("dryRunMode", it) } }
            item { SwitchRow("Audit Trail", "Record tool decisions and execution outcomes locally", s.auditTrail) { viewModel.setBooleanSetting("auditTrail", it) } }
            item { SwitchRow("High-Risk Biometric Confirmation", "Require biometric confirmation for critical actions", s.highRiskBiometricConfirmation) { viewModel.setBooleanSetting("highRiskBiometricConfirmation", it) } }
            item { SliderRow("Maximum agent steps", (s.maxAgentSteps - 1) / 19f, { viewModel.setAgentInt("maxAgentSteps", 1 + (it * 19).toInt()) }) }
            

            item { SectionTitle("🎙️ OWNER VOICE HARDENING") }
            item { StatusRow("Wake-word engine", "On-device wake detector + system fallback", s.wakeWordEnabled) }
            item { SwitchRow("Anti-Spoofing", "Require anti-replay checks in the speaker-verification layer", s.ownerVoiceAntiSpoof) { viewModel.setBooleanSetting("ownerVoiceAntiSpoof", it) } }
            item { SwitchRow("Enrollment Required", "Do not authorize Parent Mode without an enrolled owner profile", s.ownerVoiceEnrollmentRequired) { viewModel.setBooleanSetting("ownerVoiceEnrollmentRequired", it) } }
            item { SliderRow("Wake-word sensitivity", s.wakeWordSensitivity, { viewModel.setFloatSetting("wakeWordSensitivity", it) }) }

            item { SectionTitle("⚡ PERFORMANCE / DEVELOPER") }
            item { ChoiceRow("Performance", s.performanceMode, listOf("Battery Saver","Balanced","Performance","Ultra")) { viewModel.setPerformanceMode(it) } }
            item { SwitchRow("Ultra-low latency", "Prefer faster streaming and shorter waits", s.ultraLowLatencyMode) { viewModel.setBooleanSetting("ultraLowLatencyMode",it) } }
            item { SwitchRow("Network optimization", "Tune streaming/network behavior", s.networkOptimization) { viewModel.setBooleanSetting("networkOptimization",it) } }
            item { SwitchRow("Background processing", "Allow background assistant processing", s.backgroundProcessing) { viewModel.setBooleanSetting("backgroundProcessing",it) } }
            item { SwitchRow("Strict notification privacy", "Apply strict redaction to notification content", s.notificationPrivacyStrict) { viewModel.setBooleanSetting("notificationPrivacyStrict",it) } }
            item { SwitchRow("Live transcript", "Show realtime voice transcript in diagnostics", s.liveTranscript) { viewModel.setBooleanSetting("liveTranscript",it) } }
            item { SwitchRow("Latency metrics", "Expose voice/chat latency diagnostics", s.showLatency) { viewModel.setBooleanSetting("showLatency",it) } }
            item { SwitchRow("Token usage", "Show provider token usage when the provider reports it", s.showTokenUsage) { viewModel.setBooleanSetting("showTokenUsage",it) } }
            item { SwitchRow("Action logs", "Enable local action-log diagnostics", s.showActionLogs) { viewModel.setBooleanSetting("showActionLogs",it) } }

            item { SectionTitle("🩺 MAYA HEALTH") }
            if (s.selfDiagnostics || s.systemHealthMonitoring) {
                item {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("System status", fontWeight = FontWeight.Bold)
                            if (s.selfDiagnostics) {
                                Text("Voice: ${if (health.voiceReady) "READY" else "NEEDS GEMINI KEY"}")
                                Text("Chat: ${if (health.chatReady) "READY" else "NEEDS NON-GEMINI KEY"}")
                                Text("Memory: ${if (health.memoryReady) "READY" else "OFF"}")
                                Text("Accessibility: ${if (health.accessibilityReady) "CONNECTED" else "NOT CONNECTED"}")
                                Text("Microphone: ${if (health.microphoneReady) "AVAILABLE" else "UNAVAILABLE"}")
                            }
                            if (s.systemHealthMonitoring) Text("Battery: ${health.batteryPercent}% • Heap: ${health.heapMb} MB")
                            Text(health.androidVersion, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (predictions.isNotEmpty()) {
                item {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Predictive suggestions", fontWeight = FontWeight.Bold)
                            predictions.forEach { Text("• $it", fontSize = 13.sp) }
                        }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Routines", fontWeight = FontWeight.Bold)
                        Text(if (routines.isEmpty()) "No learned routines yet." else "${routines.size} local routine(s) saved.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp))
}


@Composable private fun StatusRow(title: String, subtitle: String, enabled: Boolean) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(onClick = {}, enabled = false, label = { Text(if (enabled) "Active" else "Off") })
            }
        }
    }
}

@Composable private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

@Composable private fun SliderRow(title: String, value: Float, onChange: (Float) -> Unit, min: Float = 0f, max: Float = 1f) {
    Card {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(String.format("%.2f", value), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            Slider(value = value.coerceIn(min,max), onValueChange = onChange, valueRange = min..max)
        }
    }
}

@Composable private fun ChoiceRow(title: String, current: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(current, modifier = Modifier.weight(1f))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = {
                            expanded = false
                            onSelected(option)
                        })
                    }
                }
            }
        }
    }
}
