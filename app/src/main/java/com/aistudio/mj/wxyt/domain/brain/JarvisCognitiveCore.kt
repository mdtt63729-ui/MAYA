package com.aistudio.mj.wxyt.domain.brain

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.aistudio.mj.wxyt.domain.settings.MJSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * JARVIS-style orchestration layer. It is deterministic and permission-aware;
 * an LLM may provide intent/plan data, but this layer decides what can execute.
 */
class JarvisCognitiveCore(context: Context) {
    private val app = context.applicationContext

    fun buildExecutionPlan(request: String, settings: MJSettings): AgentPlan {
        val normalized = request.trim()
        val steps = if (settings.goalDecomposition) {
            normalized.split(Regex("\\s+(?:then|and then|তারপর|এরপর|এবং তারপর)\\s+", RegexOption.IGNORE_CASE))
                .map(String::trim).filter(String::isNotBlank)
        } else listOf(normalized)
        return AgentPlan(
            goal = normalized,
            steps = steps.take(settings.maxAgentSteps),
            parallelizable = settings.parallelToolExecution && steps.size > 1,
            requiresVerification = settings.actionVerification
        )
    }

    fun buildContextSignals(settings: MJSettings): ContextSignals {
        val batteryManager = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val interactive = if (Build.VERSION.SDK_INT >= 20) power.isInteractive else true
        val airplane = try {
            Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        } catch (_: Exception) { false }
        val time = SimpleDateFormat("EEEE, dd MMMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        return ContextSignals(time, battery, interactive, airplane, Build.VERSION.RELEASE)
    }

    fun riskFor(actionName: String): ActionRisk = when (actionName.uppercase(Locale.US)) {
        "SEND_MESSAGE", "CALL_CONTACT" -> ActionRisk.MEDIUM
        "INSTALL_APP", "DELETE_FILE", "UNINSTALL_APP", "DEVICE_LOCK", "FACTORY_RESET" -> ActionRisk.CRITICAL
        "OPEN_APP", "OPEN_SETTINGS", "SEARCH_WEB", "PLAY_MEDIA" -> ActionRisk.LOW
        else -> ActionRisk.MEDIUM
    }
}

data class AgentPlan(val goal: String, val steps: List<String>, val parallelizable: Boolean, val requiresVerification: Boolean)
data class ContextSignals(val localTime: String, val batteryPercent: Int, val screenInteractive: Boolean, val airplaneMode: Boolean, val androidVersion: String)
enum class ActionRisk { LOW, MEDIUM, HIGH, CRITICAL }
