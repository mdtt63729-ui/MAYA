package com.aistudio.mj.wxyt.domain.diagnostics

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Debug

class MayaDiagnostics(context: Context) {
    private val app = context.applicationContext
    fun snapshot(): DiagnosticsSnapshot {
        val battery = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return DiagnosticsSnapshot(
            batteryPercent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            memoryUsedMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024),
            android = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT
        )
    }
}

data class DiagnosticsSnapshot(val batteryPercent: Int, val memoryUsedMb: Long, val android: String, val sdk: Int)
