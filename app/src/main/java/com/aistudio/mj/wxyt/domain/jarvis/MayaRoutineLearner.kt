package com.aistudio.mj.wxyt.domain.jarvis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Lightweight local repetition detector; it proposes routines rather than executing them automatically. */
class MayaRoutineLearner(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("maya_routine_learning", Context.MODE_PRIVATE)
    private val counts = HashMap<String, Int>()

    init {
        val array = JSONArray(prefs.getString("counts", "[]"))
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            counts[o.optString("key")] = o.optInt("count")
        }
    }

    fun observe(command: String, enabled: Boolean): String? {
        if (!enabled) return null
        val key = command.trim().lowercase().replace(Regex("\\s+"), " ").take(180)
        if (key.length < 8) return null
        val next = (counts[key] ?: 0) + 1
        counts[key] = next
        persist()
        return if (next == 3) "You have repeated this task three times. Want me to turn it into a routine?" else null
    }

    private fun persist() {
        val array = JSONArray()
        counts.entries.sortedByDescending { it.value }.take(100).forEach { (key, count) ->
            array.put(JSONObject().apply { put("key", key); put("count", count) })
        }
        prefs.edit().putString("counts", array.toString()).apply()
    }
}
