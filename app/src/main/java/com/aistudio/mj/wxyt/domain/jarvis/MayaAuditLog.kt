package com.aistudio.mj.wxyt.domain.jarvis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import com.aistudio.mj.wxyt.domain.settings.SettingsRepository

class MayaAuditLog(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("maya_audit", Context.MODE_PRIVATE)
    fun record(event: String, detail: String = "") {
        val settings = SettingsRepository.get(appContext).settings.value
        if (!settings.auditTrail || settings.privateMode) return
        val old = JSONArray(prefs.getString("events", "[]"))
        old.put(JSONObject().apply { put("time", System.currentTimeMillis()); put("event", event); put("detail", detail.take(500)) })
        while (old.length() > 200) old.remove(0)
        prefs.edit().putString("events", old.toString()).apply()
    }
    fun clear() { prefs.edit().remove("events").apply() }
}
