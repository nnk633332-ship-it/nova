package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MemoryManager(context: Context) {
    private val prefs = context.getSharedPreferences("nova_v3_prefs", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale("bn", "IN"))
    private val dayOnlyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun saveMode(mode: String) {
        prefs.edit().putString("current_mode", mode).apply()
    }

    fun getMode(): String {
        return prefs.getString("current_mode", "normal") ?: "normal"
    }

    fun setAlwaysOn(enabled: Boolean) {
        prefs.edit().putBoolean("always_on", enabled).apply()
    }

    fun isAlwaysOn(): Boolean {
        return prefs.getBoolean("always_on", true)
    }

    fun setStandbyMode(standby: Boolean) {
        prefs.edit().putBoolean("standby_mode", standby).apply()
    }

    fun isStandbyMode(): Boolean {
        return prefs.getBoolean("standby_mode", false)
    }

    fun addMemory(userSpeech: String, novaReply: String, emotion: String) {
        val raw = prefs.getString("memory_logs", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val obj = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("dateText", dateFormat.format(Date()))
            put("dayKey", dayOnlyFormat.format(Date()))
            put("user", userSpeech)
            put("nova", novaReply)
            put("emotion", emotion)
        }
        arr.put(obj)
        val trimmed = JSONArray()
        val start = if (arr.length() > 800) arr.length() - 800 else 0
        for (i in start until arr.length()) {
            trimmed.put(arr.getJSONObject(i))
        }
        prefs.edit().putString("memory_logs", trimmed.toString()).apply()
    }

    fun recallByDaysAgo(daysAgo: Int): String {
        val targetMillis = System.currentTimeMillis() - (daysAgo * 24L * 60L * 60L * 1000L)
        val targetDay = dayOnlyFormat.format(Date(targetMillis))
        val raw = prefs.getString("memory_logs", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val matches = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            if (item.optString("dayKey") == targetDay) {
                matches.add("${item.optString("dateText")}: আপনি বলেছিলেন '${item.optString("user")}'")
            }
        }
        return if (matches.isEmpty()) {
            "$daysAgo দিন আগে ($targetDay) কোনো কথা সেভ করা নেই।"
        } else {
            "$daysAgo দিন আগে আপনি বলেছিলেন:
" + matches.takeLast(3).joinToString("
")
        }
    }

    fun searchMemory(keyword: String): String {
        val raw = prefs.getString("memory_logs", "[]") ?: "[]"
        val arr = JSONArray(raw)
        for (i in arr.length() - 1 downTo 0) {
            val item = arr.getJSONObject(i)
            val u = item.optString("user")
            if (keyword.isBlank() || u.contains(keyword, ignoreCase = true)) {
                return "${item.optString("dateText")}-এ আপনি বলেছিলেন: '$u'"
            }
        }
        return "এই বিষয়ে পুরনো কোনো কথা খুঁজে পাইনি।"
    }

    fun getRecentLogsJson(): String {
        return prefs.getString("memory_logs", "[]") ?: "[]"
    }
}
