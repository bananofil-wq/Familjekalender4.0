package se.familjekalender.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

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

private fun canInstallPackages(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

private fun openUnknownSourcesSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Suppress("DEPRECATION")
private fun packageVersionCode(packageInfo: PackageInfo): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode
    else packageInfo.versionCode.toLong()

@Suppress("DEPRECATION")
private fun packageSignatures(packageInfo: PackageInfo): Array<Signature> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val signingInfo = packageInfo.signingInfo ?: return emptyArray()
        if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        }
    } else {
        packageInfo.signatures ?: emptyArray()
    }

private fun signingDigests(packageInfo: PackageInfo): Set<String> =
    packageSignatures(packageInfo).map { signature ->
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }.toSet()

@Suppress("DEPRECATION")
private fun validateDownloadedApk(context: Context, apkFile: File, expectedVersion: String) {
    val packageManager = context.packageManager
    val signatureFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }

    val archiveInfo = packageManager.getPackageArchiveInfo(apkFile.absolutePath, signatureFlags)
        ?: error("Filen är inte en giltig Android-app")
    val installedInfo = packageManager.getPackageInfo(context.packageName, signatureFlags)

    if (archiveInfo.packageName != context.packageName) {
        apkFile.delete()
        error("Uppdateringen tillhör fel app")
    }

    val downloadedVersion = archiveInfo.versionName.orEmpty()
    if (downloadedVersion.isBlank() || downloadedVersion != expectedVersion) {
        apkFile.delete()
        error("Versionsnumret i uppdateringen stämmer inte")
    }

    if (packageVersionCode(archiveInfo) <= packageVersionCode(installedInfo)) {
        apkFile.delete()
        error("Uppdateringen har inte ett högre versionsnummer än den installerade appen")
    }

    val installedSigners = signingDigests(installedInfo)
    val downloadedSigners = signingDigests(archiveInfo)
    if (installedSigners.isEmpty() || downloadedSigners.isEmpty() || installedSigners.intersect(downloadedSigners).isEmpty()) {
        apkFile.delete()
        error("Uppdateringen är inte signerad med samma appnyckel")
    }
}

private suspend fun downloadUpdateApk(context: Context, update: AvailableUpdate): Uri = withContext(Dispatchers.IO) {
    val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
    val apkFile = File(updateDir, "Familjekalender-${update.version}.apk")
    if (apkFile.exists()) apkFile.delete()

    val connection = (URL(update.downloadUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        requestMethod = "GET"
        setRequestProperty("Accept", "application/octet-stream")
        setRequestProperty("User-Agent", "Familjekalender-Android-Updater")
    }

    try {
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            error("Nedladdningen misslyckades med HTTP $responseCode")
        }

        connection.inputStream.use { input ->
            apkFile.outputStream().use { output -> input.copyTo(output) }
        }
        if (!apkFile.exists() || apkFile.length() == 0L) {
            error("Den nedladdade uppdateringen är tom")
        }
        validateDownloadedApk(context, apkFile, update.version)
    } finally {
        connection.disconnect()
    }

    FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile
    )
}

private fun launchInstaller(context: Context, apkUri: Uri) {
    context.startActivity(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
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
    var updating by remember { mutableStateOf(false) }
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
                enabled = !checking && !updating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (checking) "Kontrollerar…" else "Sök efter uppdatering")
            }

            availableUpdate?.let { update ->
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (!canInstallPackages(context)) {
                            statusIsError = false
                            statusText = "Tillåt installation från Familjekalendern och gå sedan tillbaka hit."
                            openUnknownSourcesSettings(context)
                        } else {
                            scope.launch {
                                updating = true
                                statusIsError = false
                                statusText = "Laddar ner version ${update.version}…"
                                runCatching { downloadUpdateApk(context, update) }
                                    .onSuccess { apkUri ->
                                        statusText = "Uppdateringen är nedladdad. Bekräfta installationen i Android."
                                        launchInstaller(context, apkUri)
                                    }
                                    .onFailure { error ->
                                        statusIsError = true
                                        statusText = "Kunde inte installera uppdateringen: ${error.message ?: "okänt fel"}"
                                    }
                                updating = false
                            }
                        }
                    },
                    enabled = !checking && !updating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (updating) "Laddar ner…" else "Uppdatera nu")
                }
            }
        }
    }
}
