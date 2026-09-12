package com.allione.messages.data

import android.content.Context
import com.allione.messages.model.MessageSource
import com.allione.messages.model.UnifiedMessage
import org.json.JSONArray
import org.json.JSONObject

object MessageStore {
    private const val PREFS = "allione_messages"
    private const val KEY = "messages"
    private const val MAX_MESSAGES = 500

    @Synchronized
    fun save(context: Context, message: UnifiedMessage) {
        val current = load(context).toMutableList()
        val existing = current.indexOfFirst { it.id == message.id }
        if (existing >= 0) current[existing] = message else current.add(message)
        val trimmed = current.sortedByDescending { it.timestamp }.take(MAX_MESSAGES)
        persist(context, trimmed)
    }

    @Synchronized
    fun saveAll(context: Context, messages: List<UnifiedMessage>) {
        val merged = (load(context) + messages)
            .associateBy { it.id }
            .values
            .sortedByDescending { it.timestamp }
            .take(MAX_MESSAGES)
        persist(context, merged)
    }

    @Synchronized
    fun load(context: Context): List<UnifiedMessage> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        UnifiedMessage(
                            id = o.getString("id"),
                            sender = o.optString("sender", "Okänd"),
                            preview = o.optString("preview", ""),
                            timestamp = o.optLong("timestamp", 0L),
                            source = MessageSource.valueOf(o.optString("source", MessageSource.SMS.name)),
                            unread = o.optBoolean("unread", true),
                            packageName = o.optString("packageName").takeIf { it.isNotBlank() },
                            notificationKey = o.optString("notificationKey").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList()).sortedByDescending { it.timestamp }
    }

    private fun persist(context: Context, messages: Collection<UnifiedMessage>) {
        val array = JSONArray()
        messages.forEach { m ->
            array.put(JSONObject().apply {
                put("id", m.id)
                put("sender", m.sender)
                put("preview", m.preview)
                put("timestamp", m.timestamp)
                put("source", m.source.name)
                put("unread", m.unread)
                put("packageName", m.packageName ?: "")
                put("notificationKey", m.notificationKey ?: "")
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
