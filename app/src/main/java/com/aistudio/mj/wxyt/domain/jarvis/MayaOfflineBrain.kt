package com.aistudio.mj.wxyt.domain.jarvis

import android.content.Context
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Deterministic offline commands that do not require an AI provider. */
class MayaOfflineBrain(context: Context) {
    private val app = context.applicationContext
    fun answer(text: String): String? {
        val q = text.trim().lowercase()
        return when {
            q == "hi" || q == "hello" || q.contains("হাই") || q.contains("হ্যালো") -> "Hello. MAYA is ready."
            q.contains("what time") || q.contains("সময় কত") || q == "time" -> SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            q.contains("battery") || q.contains("ব্যাটারি") -> {
                val p = (app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                "Battery is at $p%."
            }
            q == "status" || q.contains("system status") || q.contains("স্ট্যাটাস") -> "MAYA offline core is online. Cloud AI is not required for this check."
            else -> null
        }
    }
}
