package com.aistudio.mj.wxyt.domain.jarvis

import android.view.accessibility.AccessibilityNodeInfo
import com.aistudio.mj.wxyt.accessibility.ORBAccessibilityService

/** Current-screen context from Accessibility when the user explicitly enables it. */
data class MayaScreenSnapshot(val packageName: String, val title: String, val visibleText: List<String>, val timestamp: Long)

object MayaScreenContext {
    @Volatile private var latest: MayaScreenSnapshot? = null

    fun update(packageName: String, root: AccessibilityNodeInfo?) {
        val texts = ArrayList<String>(32)
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null || texts.size >= 80) return
            node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { texts += it.take(180) }
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { texts += it.take(180) }
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(root)
        latest = MayaScreenSnapshot(packageName, texts.firstOrNull().orEmpty(), texts.distinct(), System.currentTimeMillis())
    }

    fun snapshot(): MayaScreenSnapshot? = latest
    fun clear() { latest = null }
}
