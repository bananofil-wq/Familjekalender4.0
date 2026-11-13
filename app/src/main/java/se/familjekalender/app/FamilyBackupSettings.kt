package se.familjekalender.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
internal fun FamilyBackupSettings(session: FamilySession) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<String?>(null) }

    val fileSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExport
        pendingExport = null
        if (uri == null || json == null) {
            busy = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri, "w")
                        ?: error("Kunde inte öppna filen.")
                    output.use { it.write(json.toByteArray(StandardCharsets.UTF_8)) }
                }
            }.onSuccess {
                status = "Säkerhetskopian har sparats på vald plats."
            }.onFailure { error ->
                status = "Kunde inte spara filen: ${error.message ?: "okänt fel"}"
            }
            busy = false
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error("Kunde inte läsa säkerhetskopian.")
                    val file = input.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                    require(file.length <= 15_000_000) { "Filen är för stor." }
                    val document = JSONObject(file)
                    require(document.optString("format") == "familjekalender-backup" &&
                        document.optInt("version") == 1) { "Okänt format på säkerhetskopian." }
                    require(document.optString("family_id") == session.id) {
                        "Säkerhetskopian tillhör en annan familj."
                    }
                    file
                }
            }.onSuccess { json ->
                pendingImport = json
            }.onFailure { error ->
                status = "Kunde inte läsa säkerhetskopian: ${error.message ?: "okänt fel"}"
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            "Spara familjens aktiviteter, familjemedlemmar, inköp och To-do i en fil. " +
                "Kopian innehåller personuppgifter i klartext, så förvara den säkert. " +
                "Familjekoden och enhetsuppgifter sparas inte.",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(10.dp))
        Button(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = !busy,
            onClick = {
                busy = true
                scope.launch {
                    runCatching { SupabaseSync.exportFamilyBackup(session) }
                        .onSuccess { json ->
                            pendingExport = json
                            fileSaver.launch("Familjekalendern-${LocalDate.now()}.json")
                        }
                        .onFailure { error ->
                            status = "Kunde inte skapa säkerhetskopian: ${error.message ?: "okänt fel"}"
                            busy = false
                        }
                }
            },
        ) {
            Text("Spara säkerhetskopia")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = !busy,
            onClick = { filePicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
        ) {
            Text("Återställ från säkerhetskopia")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Återställning lägger bara tillbaka saknade poster i samma familj. " +
                "Den tar inte bort eller skriver över nyare information.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        if (status.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(status, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (busy) {
            Spacer(Modifier.height(8.dp))
            Text("Arbetar med säkerhetskopian…")
        }
    }

    if (pendingImport != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Återställ säkerhetskopia?") },
            text = {
                Text("Saknade aktiviteter, familjemedlemmar, inköp och To-do läggs tillbaka. " +
                    "Befintliga poster lämnas orörda. Fortsätt bara om du känner igen filen.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val json = pendingImport ?: return@Button
                        pendingImport = null
                        busy = true
                        scope.launch {
                            runCatching { SupabaseSync.restoreFamilyBackup(session, json) }
                                .onSuccess { result ->
                                    status = "Återställningen är klar. ${result.total} poster " +
                                        "kontrollerades; redan befintliga poster hoppades över. " +
                                        "Öppna kalendern igen för att läsa in uppgifterna."
                                    FamilyCalendarWidget.enqueueRefresh(context)
                                }
                                .onFailure { error ->
                                    status = "Återställningen avbröts: ${error.message ?: "okänt fel"}. " +
                                        "En del saknade poster kan ha hunnit återställas."
                                }
                            busy = false
                        }
                    }
                ) { Text("Återställ") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Avbryt") }
            },
        )
    }
}
