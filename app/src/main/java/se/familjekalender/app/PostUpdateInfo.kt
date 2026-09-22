package se.familjekalender.app

import android.content.Context
import android.content.pm.PackageInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val UPDATE_INFO_PREFS = "familjekalender_update_info"
private const val LAST_SHOWN_VERSION = "last_shown_version"
private const val RELEASE_BY_TAG_API =
    "https://api.github.com/repos/bananofil-wq/Familjekalender4.0/releases/tags/v"

private data class PostUpdateNotes(
    val summary: String,
    val details: String,
)

private fun localFallbackNotes(version: String): PostUpdateNotes =
    when (version) {
        "4.3.20260922.554" ->
            PostUpdateNotes(
                summary = "Nytt i denna version:\n• ICA-recept visas direkt i appen\n• Ingen extern webbläsare öppnas\n• Receptvyn har förbättrats",
                details =
                    "• ICA Recept är nu integrerat i Familjekalenderns receptvy.\n\n" +
                            "• När du söker efter en maträtt, bakelse, dessert eller ingrediens visas ICA:s recept direkt inne i appen.\n\n" +
                            "• Familjekalendern öppnar inte längre telefonens externa webbläsare när du väljer ett recept.\n\n" +
                            "• Receptvyn har fått en tydligare intern navigering med möjlighet att stänga receptet och gå tillbaka till inköpsdelen.\n\n" +
                            "• Uppdateringsrutan visar nu konkreta nyheter direkt, medan Mer visar hela ändringslistan.",
            )

        "4.3.20260917.5" ->
            PostUpdateNotes(
                summary = "Nu får du en tydlig sammanfattning efter varje uppdatering.",
                details =
                    "• En liten informationsruta visas första gången du öppnar appen efter en uppdatering.\n\n" +
                            "• Rutan visar en kort sammanfattning av vad som är nytt.\n\n" +
                            "• Tryck på Mer för att läsa en mer ingående ändringslista.\n\n" +
                            "• Informationen visas bara en gång per installerad version.",
            )

        else ->
            PostUpdateNotes(
                summary = "Familjekalendern har uppdaterats med förbättringar och korrigeringar.",
                details =
                    "Den här versionen innehåller förbättringar, korrigeringar och mindre justeringar i appen.",
            )
    }

private suspend fun fetchPostUpdateNotes(version: String): PostUpdateNotes? =
    withContext(Dispatchers.IO) {
        val connection =
            (URL("$RELEASE_BY_TAG_API$version").openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Familjekalender-Android/$version")
            }

        try {
            if (connection.responseCode !in 200..299) return@withContext null
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val body = JSONObject(json).optString("body").trim()
            if (body.isBlank()) return@withContext null
            if (body.equals("Signerad uppdatering för Familjekalendern.", ignoreCase = true)) {
                return@withContext null
            }

            val summary =
                body
                    .lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                    ?.removePrefix("-")
                    ?.removePrefix("*")
                    ?.trim()
                    ?.take(180)
                    .orEmpty()

            PostUpdateNotes(
                summary = summary.ifBlank { "Familjekalendern har uppdaterats." },
                details = body,
            )
        } finally {
            connection.disconnect()
        }
    }

private fun packageInfo(context: Context): PackageInfo? = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0)
}.getOrNull()

@Composable
internal fun PostUpdateInfoNotice() {
    val context = LocalContext.current
    val infoPrefs = remember {
        context.getSharedPreferences(UPDATE_INFO_PREFS, Context.MODE_PRIVATE)
    }
    val installedPackage = remember(context) { packageInfo(context) }
    val currentVersion = installedPackage?.versionName?.takeIf { it.isNotBlank() } ?: return

    var visible by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var notes by remember(currentVersion) { mutableStateOf(localFallbackNotes(currentVersion)) }

    fun markSeen() {
        infoPrefs.edit().putString(LAST_SHOWN_VERSION, currentVersion).apply()
        showMore = false
        visible = false
    }

    LaunchedEffect(currentVersion) {
        val lastShownVersion = infoPrefs.getString(LAST_SHOWN_VERSION, null)
        if (lastShownVersion == currentVersion) return@LaunchedEffect

        val isUpdatedInstall =
            installedPackage.lastUpdateTime > installedPackage.firstInstallTime + 2_000L
        if (lastShownVersion == null && !isUpdatedInstall) {
            infoPrefs.edit().putString(LAST_SHOWN_VERSION, currentVersion).apply()
            return@LaunchedEffect
        }

        visible = true
        runCatching { fetchPostUpdateNotes(currentVersion) }.getOrNull()?.let { notes = it }
    }

    if (!visible) return

    if (showMore) {
        AlertDialog(
            onDismissRequest = { markSeen() },
            title = {
                Column {
                    Text("Vad är nytt?", fontWeight = FontWeight.SemiBold)
                    Text("Version $currentVersion")
                }
            },
            text = { Text(notes.details) },
            confirmButton = {
                TextButton(onClick = { markSeen() }) {
                    Text("Klart")
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = { markSeen() },
            title = { Text("Uppdateringen är klar", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text("Familjekalender $currentVersion")
                    Spacer(Modifier.height(8.dp))
                    Text(notes.summary)
                }
            },
            confirmButton = {
                TextButton(onClick = { showMore = true }) {
                    Text("Mer")
                }
            },
            dismissButton = {
                TextButton(onClick = { markSeen() }) {
                    Text("Stäng")
                }
            },
        )
    }
}
