package com.aistudio.mj.wxyt.domain.command

enum class CommandAction {
    OPEN_APP, SEARCH_WEB, SEARCH_APP, SEND_MESSAGE, CALL_CONTACT, PLAY_MEDIA, OPEN_SETTINGS, TYPE_TEXT, INSTALL_APP, OPEN_PLAY_STORE, CLICK_TEXT, GO_BACK,
    // PRD 1 §3.2: Media controls
    PAUSE_MEDIA, NEXT_TRACK, PREVIOUS_TRACK, VOLUME_UP, VOLUME_DOWN, SET_VOLUME,
    // PRD 1 §3.1: Deep-link media play
    PLAY_MEDIA_DEEP_LINK,
    UNKNOWN
}

data class VoiceCommand(
    val id: String = java.util.UUID.randomUUID().toString(),
    val rawText: String,
    val normalizedText: String,
    val action: CommandAction,
    val target: String? = null,
    val query: String? = null,
    val parameters: Map<String, String> = emptyMap(),
    val confidence: Float,
    val requiresClarification: Boolean = false,
    val clarificationPrompt: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
