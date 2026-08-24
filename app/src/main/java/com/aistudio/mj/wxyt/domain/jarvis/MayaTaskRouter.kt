package com.aistudio.mj.wxyt.domain.jarvis

/** Small deterministic router used before cloud inference. */
enum class MayaTaskType { SIMPLE, CODING, RESEARCH, VISION, DEVICE, CONVERSATION }

class MayaTaskRouter {
    fun classify(text: String): MayaTaskType {
        val q = text.lowercase()
        return when {
            listOf("code", "bug", "gradle", "kotlin", "java", "android", "api", "কোড", "এরর").any(q::contains) -> MayaTaskType.CODING
            listOf("search", "latest", "news", "research", "খুঁজে", "সাম্প্রতিক").any(q::contains) -> MayaTaskType.RESEARCH
            listOf("screen", "screenshot", "image", "ছবি", "স্ক্রিন").any(q::contains) -> MayaTaskType.VISION
            listOf("open", "wifi", "bluetooth", "volume", "brightness", "খুলো", "চালু", "বন্ধ").any(q::contains) -> MayaTaskType.DEVICE
            q.length < 30 -> MayaTaskType.SIMPLE
            else -> MayaTaskType.CONVERSATION
        }
    }
}
