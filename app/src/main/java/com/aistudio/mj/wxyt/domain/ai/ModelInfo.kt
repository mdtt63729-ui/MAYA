package com.aistudio.mj.wxyt.domain.ai

/**
 * ModelInfo — capability metadata for an AI model.
 *
 * Prevents sending the wrong operation to the wrong model.
 * Example: a text request goes to a text-capable model;
 *          a live voice request goes to a live-capable model.
 */
data class ModelInfo(
    val id: String,
    val displayName: String = id,
    val provider: String,
    val supportsText: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsAudioInput: Boolean = false,
    val supportsAudioOutput: Boolean = false,
    val supportsLive: Boolean = false,
    val supportsTools: Boolean = false,
    val supportsStreaming: Boolean = true
)

/**
 * Check if a model is compatible with the requested capability.
 */
fun ModelInfo.hasCapability(requestAudio: Boolean, requestLive: Boolean): Boolean {
    if (requestLive && !supportsLive) return false
    if (requestAudio && !supportsAudioOutput) return false
    return supportsText
}
