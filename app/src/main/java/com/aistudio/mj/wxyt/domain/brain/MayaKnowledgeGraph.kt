package com.aistudio.mj.wxyt.domain.brain

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small local knowledge graph for relationships/preferences; intentionally local. */
class MayaKnowledgeGraph(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("maya_knowledge_graph", Context.MODE_PRIVATE)

    fun link(subject: String, relation: String, objectValue: String) {
        val key = "edges"
        val array = JSONArray(prefs.getString(key, "[]"))
        array.put(JSONObject().apply {
            put("subject", subject.trim())
            put("relation", relation.trim())
            put("object", objectValue.trim())
            put("timestamp", System.currentTimeMillis())
        })
        prefs.edit().putString(key, array.toString()).apply()
    }

    fun related(subject: String): List<Triple<String, String, String>> {
        val result = mutableListOf<Triple<String, String, String>>()
        val array = JSONArray(prefs.getString("edges", "[]"))
        for (i in 0 until array.length()) {
            val e = array.optJSONObject(i) ?: continue
            if (e.optString("subject").equals(subject, true)) {
                result += Triple(e.optString("subject"), e.optString("relation"), e.optString("object"))
            }
        }
        return result
    }

    fun clear() = prefs.edit().clear().apply()
}
