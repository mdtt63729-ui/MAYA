package com.aistudio.mj.wxyt.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.mj.wxyt.domain.ai.AIRequest
import com.aistudio.mj.wxyt.domain.ai.AIProviderFactory
import com.aistudio.mj.wxyt.domain.ai.ChatAIEngine
import com.aistudio.mj.wxyt.domain.brain.MayaAgentPlanner
import com.aistudio.mj.wxyt.domain.jarvis.MayaAuditLog
import com.aistudio.mj.wxyt.domain.jarvis.MayaOfflineBrain
import com.aistudio.mj.wxyt.domain.jarvis.MayaScreenIntelligence
import com.aistudio.mj.wxyt.domain.jarvis.MayaStateMachine
import com.aistudio.mj.wxyt.domain.jarvis.MayaRuntimeState
import com.aistudio.mj.wxyt.domain.jarvis.MayaSkillRegistry
import com.aistudio.mj.wxyt.domain.jarvis.MayaTaskRouter
import com.aistudio.mj.wxyt.domain.jarvis.MayaRoutineLearner
import com.aistudio.mj.wxyt.domain.brain.MayaBrain
import com.aistudio.mj.wxyt.domain.brain.JarvisCognitiveCore
import com.aistudio.mj.wxyt.domain.security.ActionRiskEngine
import com.aistudio.mj.wxyt.domain.chat.AppDatabase
import com.aistudio.mj.wxyt.domain.chat.ConversationEntity
import com.aistudio.mj.wxyt.domain.chat.MessageEntity
import com.aistudio.mj.wxyt.domain.command.AndroidCommandExecutor
import com.aistudio.mj.wxyt.domain.command.VoiceCommandEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class ChatUIState {
    IDLE, SENDING, THINKING, ERROR
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val providerFactory = AIProviderFactory(application)
    private val secureRepo = providerFactory.getSecureRepo()
    private val settingsRepo = providerFactory.getSettingsRepo()
    private val providers = providerFactory.createAll()
    private val chatAI = ChatAIEngine(settingsRepo, secureRepo, providers)
    private val brain = MayaBrain(application)
    // Text-chat command parsing and planning deliberately share the same non-Gemini
    // orchestrator. Gemini remains reserved for the voice pipeline.
    private val nonGeminiOrchestrator = com.aistudio.mj.wxyt.domain.ai.AIOrchestrator(
        settingsRepo,
        secureRepo,
        providers.filterKeys { it != "gemini" }
    )
    private val commandEngine = VoiceCommandEngine(nonGeminiOrchestrator)
    private val commandExecutor = AndroidCommandExecutor(application)
    private val agentPlanner = MayaAgentPlanner(application, nonGeminiOrchestrator)
    private val stateMachine = MayaStateMachine()
    private val offlineBrain = MayaOfflineBrain(application)
    private val screenIntelligence = MayaScreenIntelligence()
    private val audit = MayaAuditLog(application)
    private val skills = MayaSkillRegistry(application, nonGeminiOrchestrator)
    private val taskRouter = MayaTaskRouter()
    private val routineLearner = MayaRoutineLearner(application)
    private val dao = AppDatabase.getDatabase(application).chatDao()

    private val _uiState = MutableStateFlow(ChatUIState.IDLE)
    val uiState = _uiState.asStateFlow()
    val runtimeState = stateMachine.state

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId = _currentConversationId.asStateFlow()

    val currentMessages = _currentConversationId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else dao.getMessagesForConversation(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (_uiState.value == ChatUIState.SENDING || _uiState.value == ChatUIState.THINKING) return

        viewModelScope.launch {
            _uiState.value = ChatUIState.SENDING
            _errorMessage.value = null
            val settingsAtStart = settingsRepo.settings.value
            val persistHistory = settingsAtStart.saveHistory && settingsAtStart.rememberConversations && !settingsAtStart.privateMode

            var convId = _currentConversationId.value
            val now = System.currentTimeMillis()
            if (convId == null) {
                convId = UUID.randomUUID().toString()
                _currentConversationId.value = convId
                if (persistHistory) {
                    dao.insertConversation(
                        ConversationEntity(
                            id = convId,
                            title = trimmed.take(30) + if (trimmed.length > 30) "..." else "",
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            } else if (persistHistory) {
                dao.getConversationById(convId)?.let { dao.updateConversation(it.copy(updatedAt = now)) }
            }

            if (persistHistory) {
                dao.insertMessage(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = convId,
                        role = "user",
                        content = trimmed,
                        timestamp = now
                    )
                )
            }

            _uiState.value = ChatUIState.THINKING
            stateMachine.transition(MayaRuntimeState.THINKING)
            audit.record("CHAT_REQUEST", trimmed)

            try {
                val settings = settingsRepo.settings.value
                offlineBrain.answer(trimmed)?.let {
                    saveAssistantMessage(convId, it)
                    audit.record("OFFLINE_RESPONSE", it)
                    stateMachine.reset()
                    _uiState.value = ChatUIState.IDLE
                    return@launch
                }
                val skillResult = skills.executeIfSupported(trimmed, settings)
                if (skillResult != null) {
                    saveAssistantMessage(convId, skillResult)
                    audit.record("SKILL_EXECUTION", skillResult)
                    stateMachine.reset()
                    _uiState.value = ChatUIState.IDLE
                    return@launch
                }
                val taskType = taskRouter.classify(trimmed)
                audit.record("TASK_CLASSIFIED", taskType.name)
                routineLearner.observe(trimmed, settings.routineLearning && settings.routinesEnabled)?.let { suggestion ->
                    audit.record("ROUTINE_SUGGESTION", suggestion)
                }
                brain.learnFromUserText(trimmed, settings)

                val plannedResult = if (settings.multiAgentOrchestration) agentPlanner.execute(trimmed, settings) else null
                if (plannedResult != null) {
                    saveAssistantMessage(convId, plannedResult)
                    _uiState.value = ChatUIState.IDLE
                    return@launch
                }

                val parsedCommand = commandEngine.processCommand(trimmed)
                if (parsedCommand != null && parsedCommand.confidence >= settings.actionConfidenceThreshold && settings.autoExecuteSafeActions) {
                    val riskEngine = ActionRiskEngine(JarvisCognitiveCore(getApplication<Application>()))
                    val riskNeedsConfirmation = riskEngine.requiresConfirmation(parsedCommand.action.name, settings)
                    val needsBiometric = riskEngine.requiresBiometric(parsedCommand.action.name, settings)
                    if (!riskNeedsConfirmation && !needsBiometric) {
                        val execution = commandExecutor.execute(parsedCommand)
                        if (execution.success) {
                            saveAssistantMessage(convId, execution.userMessage)
                            _uiState.value = ChatUIState.IDLE
                            return@launch
                        }
                    } else {
                        val message = if (needsBiometric) {
                            "এই critical action-এর জন্য biometric confirmation প্রয়োজন: ${parsedCommand.action.name}"
                        } else {
                            "এই action execute করার আগে confirmation প্রয়োজন: ${parsedCommand.action.name}"
                        }
                        saveAssistantMessage(convId, message)
                        _uiState.value = ChatUIState.IDLE
                        return@launch
                    }
                }

                val contextBuilder = StringBuilder()
                val historyLimit = if (settings.contextCompression) 12 else 40
                currentMessages.value.takeLast(historyLimit).forEach { msg ->
                    contextBuilder.append(if (msg.role == "user") "User: " else "Assistant: ")
                    contextBuilder.append(msg.content).append('\n')
                }

                val brainContext = brain.buildContext(trimmed, settings)
                if (brainContext.isNotBlank()) contextBuilder.append('\n').append(brainContext).append('\n')
                val screenContext = screenIntelligence.buildContext(settings.multimodalVision && settings.screenshotContext && settings.screenAwareness && settings.allowAccessibilityAutomation)
                if (screenContext.isNotBlank()) contextBuilder.append('\n').append(screenContext).append('\n')

                val system = """
                    You are MAYA, a natural, emotionally intelligent Android assistant.
                    The current text-chat provider is separate from MAYA's Gemini voice engine.
                    Think before acting. Never claim an action succeeded unless the executor reported success.
                    If a permission, confirmation, biometric, or unavailable capability is required, say so clearly.
                    Answer style: ${settings.answerStyle}.
                    Reasoning mode: ${settings.reasoningMode}; planning depth: ${settings.planningDepth}.
                    Personality intensity: warmth=${settings.warmth}, humor=${settings.humor}, playfulness=${settings.playfulness}, affection=${settings.affection}, formality=${settings.formality}, talkativeness=${settings.talkativeness}.
                    Web intelligence is ${if (settings.webIntelligence) "enabled" else "disabled"}; factual verification is ${if (settings.factVerification) "requested for important claims" else "not explicitly requested"}.
                    Self-correction is ${if (settings.selfCorrection) "enabled" else "disabled"}.
                    Prefer concise, natural answers. Do not reveal hidden reasoning.
                    Task class: $taskType. Use screen context only when supplied and permission-gated.
                """.trimIndent()

                val request = AIRequest(
                    prompt = trimmed,
                    context = contextBuilder.toString(),
                    temperature = settings.temperature,
                    maxTokens = settings.contextLength.coerceIn(256, 8192),
                    systemInstruction = system,
                    requireAudio = false
                )
                val streaming = settings.streamingResponses && !settings.privateMode
                if (streaming) {
                    val assistantId = UUID.randomUUID().toString()
                    if (persistHistory) {
                        dao.insertMessage(MessageEntity(assistantId, convId, "assistant", "", System.currentTimeMillis()))
                    }
                    val buffer = StringBuilder()
                    chatAI.stream(request).collect { chunk ->
                        buffer.append(chunk.text)
                        if (persistHistory) dao.updateMessageContent(assistantId, buffer.toString())
                    }
                    if (buffer.isBlank()) throw IllegalStateException("Chat provider returned an empty response.")
                } else {
                    val response = chatAI.generate(request)
                    saveAssistantMessage(convId, response.text)
                }
                audit.record("CHAT_COMPLETE", "provider=${settings.chatProvider}")
                stateMachine.reset()
                _uiState.value = ChatUIState.IDLE
            } catch (e: Exception) {
                stateMachine.transition(MayaRuntimeState.ERROR)
                audit.record("CHAT_ERROR", e.message.orEmpty())
                _uiState.value = ChatUIState.ERROR
                _errorMessage.value = e.message ?: "Unable to generate a response."
            }
        }
    }

    private suspend fun saveAssistantMessage(conversationId: String, text: String) {
        val settings = settingsRepo.settings.value
        if (!settings.saveHistory || !settings.rememberConversations || settings.privateMode) return
        dao.insertMessage(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = "assistant",
                content = text,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun clearError() {
        _errorMessage.value = null
        _uiState.value = ChatUIState.IDLE
    }

    fun loadConversation(id: String) { _currentConversationId.value = id }

    fun newConversation() {
        _currentConversationId.value = null
        _uiState.value = ChatUIState.IDLE
        _errorMessage.value = null
    }
}
