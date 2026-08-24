package com.aistudio.mj.wxyt.domain.jarvis

import android.content.Context
import android.os.BatteryManager
import com.aistudio.mj.wxyt.domain.settings.MJSettings

class MayaPredictiveEngine(context: Context) {
    private val app = context.applicationContext
    fun suggestions(settings: MJSettings): List<String> {
        if (!settings.predictiveIntelligence || settings.privateMode) return emptyList()
        val battery = (app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return buildList {
            if (battery in 1..15) add("Battery is ${battery}%. Battery Saver চালু করতে পারি।")
            if (!settings.allowAccessibilityAutomation) add("Accessibility automation is disabled; app-control actions will stay permission-gated.")
        }
    }
}
