package se.familjekalender.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val LATEST_RELEASE_API =
    "https://api.github.com/repos/bananofil-wq/Familjekalender4.0/releases/latest"

private data class AvailableUpdate(
    val version: String,
    val downloadUrl: String
)

private fun versionParts(version: String): List<Int> =
    version.removePrefix("v")
        .substringBefore('-')
        .split('.')
        .map { it.toIntOrNull() ?: 0 }

private fun isNewerVersion(candidate: String, current: String): Boolean {
    val candidateParts = versionParts(candidate)
    val currentParts = versionParts(current)
    val count = maxOf(candidateParts.size, currentParts.size)
    for (index in 0 until count) {
        val candidatePart = candidateParts.getOrElse(index) { 0 }
        val currentPart = currentParts.getOrElse(index) { 0 }
        if (candidatePart != currentPart) return candidatePart > currentPart
    }
    return false
}

private suspend fun findAvailableUpdate(currentVersion: String): AvailableUpdate? = withContext(Dispatchers.IO) {
    val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "Familjekalender-Android/$currentVersion")
    }

    try {
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null
        if (responseCode !in 200..299) {
            error("GitHub svarade med HTTP $responseCode")
        }

        val json = connection.inputStream.bufferedReader().use { it.readText() }
        val release = JSONObject(json)
        val version = release.optString("tag_name").removePrefix("v")
        if (version.isBlank() || !isNewerVersion(version, currentVersion)) {
            return@withContext null
        }

        val assets = release.optJSONArray("assets") ?: return@withContext null
        var apkUrl: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                if (apkUrl != null) break
            }
        }

        apkUrl?.let { AvailableUpdate(version, it) }
    } finally {
        connection.disconnect()
    }
}

@Composable
internal fun AppUpdateSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersion = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "0.0.0"
    }
    var checking by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AvailableUpdate?>(null) }
    var statusText by remember { mutableStateOf("Tryck för att kontrollera om en ny version finns.") }
    var statusIsError by remember { mutableStateOf(false) }

    Text("Appuppdatering", fontWeight = FontWeight.Bold)
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Installerad version: $currentVersion", color = Muted)
            Spacer(Modifier.height(8.dp))
            Text(
                statusText,
                color = if (statusIsError) MaterialTheme.colorScheme.error else Color.White
            )
            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        checking = true
                        statusIsError = false
                        statusText = "Söker efter uppdatering…"
                        runCatching { findAvailableUpdate(currentVersion) }
                            .onSuccess { update ->
                                availableUpdate = update
                                statusText = if (update == null) {
                                    "Du har den senaste publicerade versionen."
                                } else {
                                    "Ny version ${update.version} finns tillgänglig."
                                }
                            }
                            .onFailure { error ->
                                availableUpdate = null
                                statusIsError = true
                                statusText = "Kunde inte kontrollera uppdateringar: ${error.message ?: "okänt fel"}"
                            }
                        checking = false
                    }
                },
                enabled = !checking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (checking) "Kontrollerar…" else "Sök efter uppdatering")
            }

            availableUpdate?.let { update ->
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Uppdatera nu")
                }
            }
        }
    }
}