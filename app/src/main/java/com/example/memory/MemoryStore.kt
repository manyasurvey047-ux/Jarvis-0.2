package com.example.memory

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class MemoryItem(
    val id: String,
    val fact: String,
    val timestamp: Long = System.currentTimeMillis()
)

object MemoryStore {
    private const val PREFS_NAME = "zoya_persistent_memory"
    private const val KEY_MEMORIES = "stored_memories"

    private lateinit var prefs: SharedPreferences
    private val _memories = MutableStateFlow<List<MemoryItem>>(emptyList())
    val memories: StateFlow<List<MemoryItem>> = _memories.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadMemories()
    }

    private fun loadMemories() {
        val jsonStr = prefs.getString(KEY_MEMORIES, "[]") ?: "[]"
        val list = mutableListOf<MemoryItem>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    MemoryItem(
                        id = obj.optString("id", "${System.currentTimeMillis()}_$i"),
                        fact = obj.optString("fact", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _memories.value = list
    }

    private fun saveMemories() {
        try {
            val arr = JSONArray()
            for (item in _memories.value) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("fact", item.fact)
                    put("timestamp", item.timestamp)
                }
                arr.put(obj)
            }
            prefs.edit().putString(KEY_MEMORIES, arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun rememberFact(fact: String): String {
        if (fact.isBlank()) return "Fact cannot be empty."
        val newItem = MemoryItem(
            id = "mem_${System.currentTimeMillis()}",
            fact = fact.trim(),
            timestamp = System.currentTimeMillis()
        )
        val updated = _memories.value.toMutableList().apply { add(0, newItem) }
        _memories.value = updated
        saveMemories()
        return "Fact remembered: \"${fact.trim()}\""
    }

    fun recallMemory(query: String = ""): String {
        val list = _memories.value
        if (list.isEmpty()) {
            return "No persistent memories stored yet."
        }
        val filtered = if (query.isBlank()) {
            list
        } else {
            val q = query.lowercase()
            list.filter { it.fact.lowercase().contains(q) }
        }
        if (filtered.isEmpty()) {
            return "No stored memories found matching \"$query\". All stored facts: ${list.joinToString("; ") { it.fact }}"
        }
        return filtered.joinToString("\n") { "• ${it.fact}" }
    }

    fun clearAllMemories(): String {
        _memories.value = emptyList()
        prefs.edit().remove(KEY_MEMORIES).apply()
        return "All persistent memories cleared successfully."
    }

    fun deleteMemory(id: String) {
        _memories.value = _memories.value.filter { it.id != id }
        saveMemories()
    }
}
