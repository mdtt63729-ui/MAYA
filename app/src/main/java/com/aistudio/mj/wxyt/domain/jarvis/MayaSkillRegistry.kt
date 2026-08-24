package com.aistudio.mj.wxyt.domain.jarvis

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.aistudio.mj.wxyt.domain.ai.AIOrchestrator
import com.aistudio.mj.wxyt.domain.command.AndroidCommandExecutor
import com.aistudio.mj.wxyt.domain.command.VoiceCommandEngine
import com.aistudio.mj.wxyt.domain.settings.MJSettings

/** Extensible skill registry. Skills are bounded and permission-aware. */
data class MayaSkill(val id: String, val description: String, val keywords: Set<String>)

class MayaSkillRegistry(
    private val context: Context,
    orchestrator: AIOrchestrator
) {
    private val app = context.applicationContext
    private val commandEngine = VoiceCommandEngine(orchestrator)
    private val executor = AndroidCommandExecutor(app)

    val skills: List<MayaSkill> = listOf(
        MayaSkill("device", "Device and system controls", setOf("wifi", "bluetooth", "brightness", "volume", "battery", "settings", "ওয়াইফাই", "ব্লুটুথ")),
        MayaSkill("apps", "Open installed applications", setOf("open", "launch", "খুলো", "খুলে", "অ্যাপ")),
        MayaSkill("web", "Web search", setOf("search", "google", "find", "search কর", "খুঁজে")),
        MayaSkill("time", "Time and date", setOf("time", "date", "সময়", "তারিখ")),
        MayaSkill("camera", "Camera launch", setOf("camera", "ক্যামেরা"))
    )

    fun match(text: String): MayaSkill? {
        val lower = text.lowercase()
        return skills.maxByOrNull { skill -> skill.keywords.count(lower::contains) }
            ?.takeIf { it.keywords.any(lower::contains) }
    }

    suspend fun executeIfSupported(text: String, settings: MJSettings): String? {
        if (!settings.autoExecuteSafeActions) return null
        val command = commandEngine.processCommand(text) ?: return null
        if (command.confidence < settings.actionConfidenceThreshold) return null
        if (settings.riskBasedConfirmation && command.action.name in setOf("SEND_MESSAGE", "CALL_CONTACT", "DELETE_FILE", "INSTALL_APP")) return null
        val result = executor.execute(command)
        return result.takeIf { it.success }?.userMessage
    }

    fun openAssistantSettings() {
        val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
    }

    fun openAppInfo() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
    }
}
