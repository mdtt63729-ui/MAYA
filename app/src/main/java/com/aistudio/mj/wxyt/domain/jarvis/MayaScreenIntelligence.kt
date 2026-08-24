package com.aistudio.mj.wxyt.domain.jarvis

class MayaScreenIntelligence {
    fun buildContext(enabled: Boolean): String {
        if (!enabled) return ""
        val s = MayaScreenContext.snapshot() ?: return "SCREEN_CONTEXT: unavailable. Accessibility permission may be disabled."
        val age = System.currentTimeMillis() - s.timestamp
        if (age > 10_000) return "SCREEN_CONTEXT: stale (>10s)."
        return buildString {
            append("SCREEN_CONTEXT:\nApp: ").append(s.packageName).append('\n')
            if (s.title.isNotBlank()) append("Title: ").append(s.title).append('\n')
            append("Visible text:\n")
            s.visibleText.take(40).forEach { append("- ").append(it).append('\n') }
        }
    }
}
