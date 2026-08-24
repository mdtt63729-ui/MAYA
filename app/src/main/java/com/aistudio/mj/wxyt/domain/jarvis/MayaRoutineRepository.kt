package com.aistudio.mj.wxyt.domain.jarvis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class MayaRoutine(val id: String, val name: String, val steps: List<String>, val createdAt: Long)

class MayaRoutineRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("maya_routines", Context.MODE_PRIVATE)

    fun all(): List<MayaRoutine> {
        val array = JSONArray(prefs.getString("items", "[]"))
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                add(MayaRoutine(o.optString("id"), o.optString("name"), o.optJSONArray("steps")?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList(), o.optLong("createdAt")))
            }
        }
    }

    fun clear() { prefs.edit().remove("items").apply() }

    fun save(routine: MayaRoutine) {
        val list = all().filterNot { it.id == routine.id } + routine
        val array = JSONArray()
        list.forEach { r ->
            array.put(JSONObject().apply {
                put("id", r.id); put("name", r.name); put("createdAt", r.createdAt)
                put("steps", JSONArray(r.steps))
            })
        }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
