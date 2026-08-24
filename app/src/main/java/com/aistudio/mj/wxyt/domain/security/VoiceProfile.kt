package com.aistudio.mj.wxyt.domain.security

data class VoiceProfile(
    val slotIndex: Int,
    val name: String,
    val isEnrolled: Boolean,
    val embeddingVector: FloatArray? = null
)
