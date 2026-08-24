package com.aistudio.mj.wxyt.domain.assistant

import android.util.Base64
import android.util.Log
import com.aistudio.mj.wxyt.domain.tools.ToolExecutionEngine
import com.aistudio.mj.wxyt.domain.settings.MJSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * GeminiLiveRepository — upgraded with full tool/function calling support.
 *
 * Architecture:
 *   GeminiLiveRepository → LiveConversationManager → ORB Voice Manager → Foreground Service → ORB UI
 *
 * Features:
 *   - Gemini Live WebSocket connection
 *   - Real-time audio input/output
 *   - Streaming response
 *   - Interrupt handling (barge-in)
 *   - Session lifecycle management
 *   - Connection state tracking
 *   - Tool/function calling with 14 tools
 *   - Automatic reconnection/error handling
 *   - Session cleanup
 */
class GeminiLiveRepository(
    private val apiKey: String,
    private val toolEngine: ToolExecutionEngine? = null,
    private val responseLanguage: String = "Hindi",
    private val model: String = com.aistudio.mj.wxyt.domain.ai.ProviderEndpointRegistry.GEMINI_DEFAULT_LIVE_MODEL,
    private val settings: MJSettings = MJSettings(responseLanguage = responseLanguage)
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val json = Json { ignoreUnknownKeys = true }

    private val _incomingAudio = MutableSharedFlow<ByteArray>(extraBufferCapacity = 100)
    val incomingAudio: SharedFlow<ByteArray> = _incomingAudio

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _turnState = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val turnState: SharedFlow<String> = _turnState

    private val _liveState = MutableStateFlow(LiveState.IDLE)
    val liveState: StateFlow<LiveState> = _liveState.asStateFlow()

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages.asStateFlow()

    private var isSetupComplete = false

    // Tool/function declarations — all 14 tools
    private val toolsJson = buildJsonObject {
        putJsonArray("functionDeclarations") {
            add(buildJsonObject {
                put("name", "openApp")
                put("description", "Open an application package, like WhatsApp or YouTube")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("packageName") {
                            put("type", "STRING")
                            put("description", "A generic name of the app to launch (e.g. 'WhatsApp', 'YouTube', 'Settings', 'Calculator')")
                        }
                    }
                    putJsonArray("required") { add("packageName") }
                }
            })
            add(buildJsonObject {
                put("name", "searchAndCallContact")
                put("description", "Search for a contact name on the device and call them. Can optionally open dialer instead of calling immediately, or use a specific SIM card slot.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("contactName") {
                            put("type", "STRING")
                            put("description", "The EXACT name of the contact as spoken by the user. NEVER guess or invent numbers. If the user says a name, use exactly that name.")
                        }
                        putJsonObject("useDialer") {
                            put("type", "BOOLEAN")
                            put("description", "Set to true if user wants to open dial pad / keyboard so they can see the number before calling")
                        }
                        putJsonObject("simSlot") {
                            put("type", "INTEGER")
                            put("description", "1 for SIM 1, 2 for SIM 2 if user specified. Null if default.")
                        }
                    }
                    putJsonArray("required") { add("contactName") }
                }
            })
            add(buildJsonObject {
                put("name", "sendWhatsAppMessage")
                put("description", "Send a WhatsApp message to a specific contact with some text.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("contactName") {
                            put("type", "STRING")
                            put("description", "The EXACT name of the contact as spoken by the user. NEVER guess or invent numbers.")
                        }
                        putJsonObject("message") {
                            put("type", "STRING")
                        }
                    }
                    putJsonArray("required") { add("contactName"); add("message") }
                }
            })
            add(buildJsonObject {
                put("name", "sendGmail")
                put("description", "Draft or send an email.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("recipientEmail") { put("type", "STRING") }
                        putJsonObject("subject") { put("type", "STRING") }
                        putJsonObject("body") { put("type", "STRING") }
                    }
                    putJsonArray("required") { add("recipientEmail"); add("subject"); add("body") }
                }
            })
            add(buildJsonObject {
                put("name", "searchYouTube")
                put("description", "Search for a query on YouTube app.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("query") { put("type", "STRING") }
                    }
                    putJsonArray("required") { add("query") }
                }
            })
            add(buildJsonObject {
                put("name", "adjustVolume")
                put("description", "Adjust the device volume.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("direction") {
                            put("type", "STRING")
                            put("description", "Volume action: 'up', 'down', 'mute', 'unmute', or 'max'")
                        }
                    }
                    putJsonArray("required") { add("direction") }
                }
            })
            add(buildJsonObject {
                put("name", "setVolumePercent")
                put("description", "Set the device volume to a specific percentage (0 to 100).")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("percent") {
                            put("type", "INTEGER")
                            put("description", "Volume percentage (0-100)")
                        }
                    }
                    putJsonArray("required") { add("percent") }
                }
            })
            add(buildJsonObject {
                put("name", "getSimCardInfo")
                put("description", "Check how many active SIM cards the device has.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "openQuickSettings")
                put("description", "Pull down the quick settings / components panel (toggles for wifi, bluetooth, etc).")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "clickTextOnScreen")
                put("description", "Click on any text visible on the screen. Acts like a real human finger tap and shows tap effect visually.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("text") {
                            put("type", "STRING")
                            put("description", "The text to tap on the screen")
                        }
                    }
                    putJsonArray("required") { add("text") }
                }
            })
            add(buildJsonObject {
                put("name", "openNotificationPanel")
                put("description", "Pull down the notification bar / status bar to view notifications.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "toggleTorch")
                put("description", "Turn the flashlight/torch on or off.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("state") {
                            put("type", "STRING")
                            put("description", "'on' or 'off'")
                        }
                    }
                    putJsonArray("required") { add("state") }
                }
            })
            add(buildJsonObject {
                put("name", "setBrightness")
                put("description", "Set the screen brightness. Note: Requires write settings permission first.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("level") {
                            put("type", "INTEGER")
                            put("description", "Brightness level 0 to 100")
                        }
                    }
                    putJsonArray("required") { add("level") }
                }
            })
            add(buildJsonObject {
                put("name", "playMedia")
                put("description", "Play media (like a song, video, or movie) from another app by searching for it.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("query") {
                            put("type", "STRING")
                            put("description", "What to play (e.g. 'Despacito by Luis Fonsi' or 'latest tech news')")
                        }
                    }
                    putJsonArray("required") { add("query") }
                }
            })
        }
    }

    fun connect() {
        _error.value = null
        _connectionState.value = false
        if (apiKey.isBlank()) {
            _connectionState.value = false
            addMessage("Error: API Key is missing.")
            return
        }

        Log.i("GeminiLive", "Connecting to Gemini Live API with model=$model...")
        val url = com.aistudio.mj.wxyt.domain.ai.ProviderEndpointRegistry.geminiLiveUrl(apiKey)
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("GeminiLive", "WebSocket connection OPENED successfully.")
                addMessage("WebSocket Opened")
                isSetupComplete = false
                sendSetupMessage(webSocket)
                _liveState.value = LiveState.LISTENING
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                handleServerMessage(bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val errorBody = response?.body?.string().orEmpty()
                val code = response?.code
                val reason = response?.message.orEmpty()
                val detail = when {
                    code == 401 || code == 403 -> "Gemini API key was rejected. Check the Gemini API key in Settings."
                    code == 404 -> "Gemini Live model is unavailable for this API key. Please update the Gemini model or enable Live API access."
                    code == 429 -> "Gemini API rate limit reached. Please wait a moment and try again."
                    !t.message.isNullOrBlank() -> "Gemini connection failed: ${t.message}"
                    reason.isNotBlank() -> "Gemini connection failed: $reason"
                    else -> "Gemini Live connection failed. Please check your internet connection and Gemini API access."
                }
                Log.e("GeminiLive", "WebSocket ERROR: code=$code reason=$reason body=$errorBody", t)
                addMessage("Error: $detail")
                _error.value = detail
                _liveState.value = LiveState.IDLE
                _connectionState.value = false
                this@GeminiLiveRepository.webSocket = null
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i("GeminiLive", "WebSocket CLOSED. Code: $code, Reason: $reason")
                addMessage("WebSocket Closed: $reason")
                _liveState.value = LiveState.IDLE
                _connectionState.value = false
                this@GeminiLiveRepository.webSocket = null
            }
        })
    }

    private fun sendSetupMessage(ws: WebSocket) {
        val setupMsg = buildJsonObject {
            putJsonObject("setup") {
                put("model", "models/$model")
                putJsonObject("generationConfig") {
                    putJsonArray("responseModalities") { add("AUDIO") }
                    putJsonObject("speechConfig") {
                        putJsonObject("voiceConfig") {
                            putJsonObject("prebuiltVoiceConfig") {
                                put("voiceName", geminiVoiceName(settings.activeVoice))
                            }
                        }
                    }
                }
                // Maximum responsiveness: aggressive automatic VAD plus the
                // default START_OF_ACTIVITY_INTERRUPTS barge-in behavior.
                // This makes new speech cut off Maya's current response.
                putJsonObject("realtimeInputConfig") {
                    putJsonObject("automaticActivityDetection") {
                        put("disabled", false)
                        put("startOfSpeechSensitivity", if (settings.vadSensitivity >= 0.66f) "START_SENSITIVITY_HIGH" else if (settings.vadSensitivity <= 0.33f) "START_SENSITIVITY_LOW" else "START_SENSITIVITY_HIGH")
                        put("endOfSpeechSensitivity", if (settings.vadSensitivity >= 0.66f) "END_SENSITIVITY_HIGH" else if (settings.vadSensitivity <= 0.33f) "END_SENSITIVITY_LOW" else "END_SENSITIVITY_HIGH")
                        put("prefixPaddingMs", (80 - settings.vadSensitivity * 60f).toInt().coerceIn(20, 80))
                        put("silenceDurationMs", (220 - settings.vadSensitivity * 120f).toInt().coerceIn(80, 220))
                    }
                    put("activityHandling", if (settings.autoBargeIn) "START_OF_ACTIVITY_INTERRUPTS" else "NO_INTERRUPTION")
                    put("turnCoverage", "TURN_INCLUDES_ONLY_ACTIVITY")
                }
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        add(buildJsonObject {
                            put("text", """You are MAYA, the user's personal AI assistant and emotionally intelligent voice companion.

LANGUAGE: Reply and speak ONLY in $responseLanguage unless the user explicitly asks to change language. Auto-language detection is ${if (settings.autoLanguageDetection) "enabled" else "disabled"}. Code-switching between Bengali, Hindi and English is ${if (settings.codeSwitching) "allowed naturally" else "disabled unless explicitly requested"}. Understand accents and natural conversational speech. Be natural rather than translating word-for-word.

REAL-TIME CONVERSATION: Respond as soon as the user's intent is clear. Do not wait for a long pause. Keep most replies short and conversational. If the user starts speaking while you are speaking, STOP immediately, listen to the user, and respond to the new turn. Never continue an interrupted answer.

PERSONALITY: Be warm, playful, caring, confident, emotionally aware, and natural. Intensity controls: warmth=${settings.warmth}, humor=${settings.humor}, playfulness=${settings.playfulness}, sarcasm=${settings.sarcasm}, affection=${settings.affection}, emotional expressiveness=${settings.emotionalExpressiveness}, formality=${settings.formality}, talkativeness=${settings.talkativeness}, proactivity=${settings.proactivity}, expressiveness=${settings.expressiveness}, emotionIntensity=${settings.emotionIntensity}. Use these as behavioral guides, not as literal values in replies. Emotion-aware adaptation is ${if (settings.emotionContext) "enabled" else "disabled"}. React appropriately to excitement, sadness, teasing, frustration, affection, surprise, and silence. Do not claim to be a real human.

CONVERSATION: You may discuss emotions, relationships, and personal topics respectfully. Keep the interaction non-sexual and appropriate.

STYLE: Answer style=${settings.answerStyle}. Reasoning mode=${settings.reasoningMode}. Planning depth=${settings.planningDepth}. Never reveal hidden chain-of-thought. Never say you are thinking, processing, or planning. Do not use filler. Do not repeat yourself. Sound like a natural personal assistant having a live conversation, not a customer-support bot.

TOOLS: When a tool is needed, call it directly and silently. Never invent phone numbers. If the user asks to call someone by name, pass the EXACT contact name to the tool.

CALLING: Use getSimCardInfo first, then searchAndCallContact with useDialer=true. Ask for confirmation only when required. After confirmation, use useDialer=false with the selected SIM.

UI ACTIONS: Use clickTextOnScreen, openNotificationPanel, openQuickSettings, toggleTorch, setBrightness, setVolumePercent, or openApp when appropriate.""")
                        })
                    }
                }
                putJsonArray("tools") {
                    add(toolsJson)
                }
            }
        }
        ws.send(setupMsg.toString())
    }

    private fun geminiVoiceName(name: String): String = when (name) {
        "Warm Female" -> "Kore"
        "Soft Female" -> "Leda"
        "Calm Female" -> "Charon"
        "Bright Female" -> "Zephyr"
        "Friendly Female" -> "Aoede"
        "Professional Female" -> "Kore"
        "Energetic Female" -> "Puck"
        else -> "Aoede"
    }

    /**
     * Send an initial prompt after setup is complete to kick off the conversation.
     * This mirrors Maya's behavior where it sent "Hi" to start interacting.
     */
    private fun sendInitialPrompt(ws: WebSocket) {
        val msg = buildJsonObject {
            putJsonObject("clientContent") {
                putJsonArray("turns") {
                    add(buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            add(buildJsonObject {
                                put("text", "Hi ORB! Introduce yourself briefly.")
                            })
                        }
                    })
                }
                put("turnComplete", true)
            }
        }
        ws.send(msg.toString())
    }

    private fun addMessage(msg: String) {
        _messages.value = _messages.value + msg
    }

    fun sendAudioData(pcmData: ByteArray) {
        if (webSocket == null || !isSetupComplete || _liveState.value == LiveState.IDLE) return

        val base64Data = Base64.encodeToString(pcmData, Base64.NO_WRAP)
        // Gemini Live's current WebSocket schema uses realtimeInput.audio for
        // streaming audio. mediaChunks is deprecated and can be ignored by the
        // service, which results in a connected session that never responds.
        val inputMsg = buildJsonObject {
            putJsonObject("realtimeInput") {
                putJsonObject("audio") {
                    put("mimeType", "audio/pcm;rate=16000")
                    put("data", base64Data)
                }
            }
        }
        webSocket?.send(inputMsg.toString())
    }

    fun sendTextMessage(text: String) {
        if (webSocket == null || !isSetupComplete || _liveState.value == LiveState.IDLE) return
        addMessage("You: $text")
        val msg = buildJsonObject {
            putJsonObject("clientContent") {
                putJsonArray("turns") {
                    add(buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            add(buildJsonObject { put("text", text) })
                        }
                    })
                }
                put("turnComplete", true)
            }
        }
        webSocket?.send(msg.toString())
    }

    private fun handleServerMessage(text: String) {
        try {
            val jsonMsg = json.parseToJsonElement(text).jsonObject

            // Gemini can return a protocol-level error over the WebSocket even
            // after the socket itself has opened. Surface that error to the UI
            // instead of leaving MAYA stuck on CONNECTING.
            jsonMsg["error"]?.jsonObject?.let { errorObj ->
                val code = errorObj["code"]?.jsonPrimitive?.content
                val message = errorObj["message"]?.jsonPrimitive?.content
                    ?: "Gemini Live returned an unknown error."
                val detail = when (code) {
                    "401", "403" -> "Gemini API key was rejected. Check the Gemini API key in Settings."
                    "404" -> "Gemini Live model is unavailable for this API key."
                    "429" -> "Gemini API rate limit reached. Please wait and try again."
                    else -> "Gemini Live error: $message"
                }
                addMessage("Error: $detail")
                _error.value = detail
                _liveState.value = LiveState.IDLE
                _connectionState.value = false
                return
            }

            if (jsonMsg.containsKey("setupComplete")) {
                isSetupComplete = true
                _connectionState.value = true
                addMessage("Server says: Setup Complete")
                // Do not send an unsolicited greeting here. Waiting for the
                // user's first audio keeps startup latency and surprise speech low.
            }

            if (jsonMsg.containsKey("serverContent")) {
                val serverContent = jsonMsg["serverContent"]?.jsonObject
                val modelTurn = serverContent?.get("modelTurn")?.jsonObject

                // Interrupt handling
                if (serverContent?.get("interrupted")?.jsonPrimitive?.content == "true" ||
                    serverContent?.get("interrupted")?.jsonPrimitive?.booleanOrNull == true
                ) {
                    _turnState.tryEmit("interrupt")
                }

                modelTurn?.get("parts")?.jsonArray?.forEach { partElement ->
                    val part = partElement.jsonObject

                    if (part.containsKey("inlineData")) {
                        val dataBase64 = part["inlineData"]?.jsonObject?.get("data")?.jsonPrimitive?.content
                        if (dataBase64 != null) {
                            _liveState.value = LiveState.SPEAKING
                            val rawBytes = Base64.decode(dataBase64, Base64.NO_WRAP)
                            _incomingAudio.tryEmit(rawBytes)
                        }
                    }

                    if (part.containsKey("text")) {
                        val textContent = part["text"]?.jsonPrimitive?.content
                        if (!textContent.isNullOrBlank()) {
                            addMessage("ORB: $textContent")
                            _turnState.tryEmit("text:$textContent")
                        }
                    }
                }

                if (serverContent?.containsKey("turnComplete") == true &&
                    serverContent["turnComplete"]?.jsonPrimitive?.content == "true"
                ) {
                    _liveState.value = LiveState.LISTENING
                    _turnState.tryEmit("complete")
                }
            }

            // Tool/function calling
            if (jsonMsg.containsKey("toolCall")) {
                val toolCallObj = jsonMsg["toolCall"]?.jsonObject
                val functionCalls = toolCallObj?.get("functionCalls")?.jsonArray

                functionCalls?.forEach { callElement ->
                    val callObj = callElement.jsonObject
                    val id = callObj["id"]?.jsonPrimitive?.content ?: ""
                    val name = callObj["name"]?.jsonPrimitive?.content ?: ""
                    val args = callObj["args"]?.jsonObject ?: buildJsonObject {}

                    executeToolAndRespond(id, name, args)
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiLive", "Error parsing server message", e)
            addMessage("Parsing error: ${e.message}")
        }
    }

    private fun executeToolAndRespond(id: String, name: String, args: JsonObject) {
        _liveState.value = LiveState.THINKING
        scope.launch {
            val resultStr = toolEngine?.execute(name, args) ?: "Tool engine not available"

            val responseMsg = buildJsonObject {
                putJsonObject("toolResponse") {
                    putJsonArray("functionResponses") {
                        add(buildJsonObject {
                            put("id", id)
                            put("name", name)
                            putJsonObject("response") {
                                put("result", resultStr)
                            }
                        })
                    }
                }
            }
            webSocket?.send(responseMsg.toString())
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "User stopped")
        webSocket = null
        _liveState.value = LiveState.IDLE
        _connectionState.value = false
        addMessage("Session stopped.")
    }
}

enum class LiveState {
    IDLE, LISTENING, THINKING, SPEAKING
}
