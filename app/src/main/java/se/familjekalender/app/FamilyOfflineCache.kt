package se.familjekalender.app

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import org.json.JSONArray
import org.json.JSONObject

/**
 * Last known good family data stored only in the app's no-backup directory.
 * Network failures never clear these snapshots. The explicit backup export feature
 * is separate and remains the only portable backup.
 */
internal object FamilyOfflineCache {
    private fun file(context: Context, familyId: String, kind: String): File {
        val safe = familyId.replace(Regex("[^A-Za-z0-9-]"), "_")
        return File(context.noBackupFilesDir, "family_${safe}_${kind}.json")
    }

    @Synchronized
    private fun write(context: Context, familyId: String, kind: String, rows: JSONArray) {
        val target = file(context, familyId, kind)
        val payload = JSONObject()
            .put("saved_at", OffsetDateTime.now().toString())
            .put("rows", rows)
            .toString()
        val temporary = File(target.absolutePath + ".tmp")
        temporary.writeText(payload, Charsets.UTF_8)
        if (!temporary.renameTo(target)) {
            target.writeText(payload, Charsets.UTF_8)
            temporary.delete()
        }
    }

    @Synchronized
    private fun read(context: Context, familyId: String, kind: String): JSONArray? {
        val source = file(context, familyId, kind)
        if (!source.exists() || source.length() > 12_000_000) return null
        return runCatching {
            JSONObject(source.readText(Charsets.UTF_8)).getJSONArray("rows")
        }.getOrNull()
    }

    private fun optionalString(row: JSONObject, key: String): String? =
        if (row.isNull(key)) null else row.optString(key).takeIf { it.isNotBlank() }

    fun saveMembers(context: Context, session: FamilySession, values: List<SyncMember>) {
        val rows = JSONArray()
        values.forEach {
            rows.put(JSONObject().put("id", it.id).put("name", it.name)
                .put("role", it.role).put("colorArgb", it.colorArgb))
        }
        write(context, session.id, "members", rows)
    }

    fun loadMembers(context: Context, session: FamilySession): List<SyncMember>? =
        read(context, session.id, "members")?.let { rows ->
            runCatching {
                List(rows.length()) { index ->
                    val row = rows.getJSONObject(index)
                    SyncMember(row.getString("id"), row.getString("name"),
                        row.optString("role"), row.getLong("colorArgb"))
                }
            }.getOrNull()
        }

    fun saveShopping(context: Context, session: FamilySession, values: List<SyncShoppingItem>) {
        val rows = JSONArray()
        values.forEach {
            rows.put(JSONObject().put("id", it.id).put("name", it.name)
                .put("checked", it.checked)
                .put("updatedAt", it.updatedAt ?: JSONObject.NULL))
        }
        write(context, session.id, "shopping", rows)
    }

    fun loadShopping(context: Context, session: FamilySession): List<SyncShoppingItem>? =
        read(context, session.id, "shopping")?.let { rows ->
            runCatching {
                List(rows.length()) { index ->
                    val row = rows.getJSONObject(index)
                    SyncShoppingItem(row.getString("id"), row.getString("name"),
                        row.getBoolean("checked"), optionalString(row, "updatedAt"))
                }
            }.getOrNull()
        }

    fun saveEvents(context: Context, session: FamilySession, values: List<SyncEvent>) {
        val rows = JSONArray()
        values.forEach {
            rows.put(JSONObject().put("id", it.id).put("title", it.title)
                .put("date", it.date.toString()).put("time", it.time)
                .put("memberId", it.memberId ?: JSONObject.NULL)
                .put("source", it.source).put("endDate", it.endDate?.toString() ?: JSONObject.NULL)
                .put("endTime", it.endTime ?: JSONObject.NULL)
                .put("seriesId", it.seriesId ?: JSONObject.NULL)
                .put("updatedAt", it.updatedAt ?: JSONObject.NULL))
        }
        write(context, session.id, "events", rows)
    }

    fun loadEvents(context: Context, session: FamilySession): List<SyncEvent>? =
        read(context, session.id, "events")?.let { rows ->
            runCatching {
                List(rows.length()) { index ->
                    val row = rows.getJSONObject(index)
                    SyncEvent(
                        id = row.getString("id"), title = row.getString("title"),
                        date = LocalDate.parse(row.getString("date")),
                        time = row.optString("time"),
                        memberId = optionalString(row, "memberId"),
                        source = row.optString("source", "manual"),
                        endDate = optionalString(row, "endDate")?.let(LocalDate::parse),
                        endTime = optionalString(row, "endTime"),
                        seriesId = optionalString(row, "seriesId"),
                        updatedAt = optionalString(row, "updatedAt"),
                    )
                }
            }.getOrNull()
        }

    fun saveTodo(context: Context, session: FamilySession, values: List<SyncTodoItem>) {
        val rows = JSONArray()
        values.forEach {
            rows.put(JSONObject().put("id", it.id).put("title", it.title)
                .put("checked", it.checked)
                .put("memberId", it.memberId ?: JSONObject.NULL)
                .put("updatedAt", it.updatedAt ?: JSONObject.NULL))
        }
        write(context, session.id, "todo", rows)
    }

    fun loadTodo(context: Context, session: FamilySession): List<SyncTodoItem>? =
        read(context, session.id, "todo")?.let { rows ->
            runCatching {
                List(rows.length()) { index ->
                    val row = rows.getJSONObject(index)
                    SyncTodoItem(row.getString("id"), row.getString("title"),
                        row.getBoolean("checked"), optionalString(row, "memberId"),
                        optionalString(row, "updatedAt"))
                }
            }.getOrNull()
        }

    fun lastSavedAt(context: Context, session: FamilySession): String? {
        val source = file(context, session.id, "events")
        if (!source.exists()) return null
        return runCatching {
            val timestamp = JSONObject(source.readText(Charsets.UTF_8)).getString("saved_at")
            OffsetDateTime.parse(timestamp).atZoneSameInstant(
                java.time.ZoneId.of("Europe/Stockholm")
            ).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy HH:mm",
                java.util.Locale("sv", "SE")))
        }.getOrNull()
    }

    fun clear(context: Context, session: FamilySession) {
        listOf("members", "shopping", "events", "todo").forEach { kind ->
            file(context, session.id, kind).delete()
        }
    }
}
