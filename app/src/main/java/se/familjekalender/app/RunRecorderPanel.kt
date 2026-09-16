package se.familjekalender.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@Composable
fun RunRecorderPanel(
    session: FamilySession,
    memberId: String,
    onChanged: suspend () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { RecordedRunStore(context) }
    var snapshot by remember(memberId) { mutableStateOf(RunRecordingService.snapshot(context)) }
    var latestRun by remember(memberId) { mutableStateOf(store.runsFor(memberId).firstOrNull()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var busyFinishing by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var replayRunId by remember(memberId) { mutableStateOf<String?>(null) }

    LaunchedEffect(memberId) {
        while (true) {
            snapshot = RunRecordingService.snapshot(context)
            now = System.currentTimeMillis()
            if (!snapshot.active) latestRun = store.runsFor(memberId).firstOrNull()
            delay(1_000L)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val locationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true || hasRunLocationPermission(context)
        if (locationGranted) {
            errorText = null
            RunRecordingService.start(context, memberId)
        } else {
            errorText = "Platsbehörighet krävs för att spela in rundan."
        }
    }

    val isThisMemberRecording = snapshot.active && snapshot.memberId == memberId
    val anotherMemberRecording = snapshot.active && snapshot.memberId != null && snapshot.memberId != memberId
    val distanceKm = if (isThisMemberRecording) runDistanceKm(snapshot.points) else 0.0
    val elapsedSeconds = if (isThisMemberRecording) ((now - snapshot.startedAtMillis).coerceAtLeast(0L) / 1000L) else 0L
    val averagePace = paceSecondsPerKm(distanceKm, elapsedSeconds)
    val currentPace = currentPaceSecondsPerKm(snapshot.points)

    Surface(
        color = SoftPurple,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("GPS-runda", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            when {
                isThisMemberRecording -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecorderStat("Distans", "%.2f km".format(Locale.US, distanceKm), Modifier.weight(1f))
                        RecorderStat("Tid", formatDuration(elapsedSeconds), Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecorderStat("Tempo nu", currentPace?.let(::formatPace) ?: "–", Modifier.weight(1f))
                        RecorderStat("Snittempo", averagePace?.let(::formatPace) ?: "–", Modifier.weight(1f))
                    }
                    if (snapshot.points.isNotEmpty()) {
                        LiveRunMap(snapshot.points, Modifier.fillMaxWidth().height(260.dp))
                    } else {
                        Text("Väntar på första GPS-positionen…", color = Muted, fontSize = 12.sp)
                    }
                    Button(
                        enabled = !busyFinishing,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            busyFinishing = true
                            errorText = null
                            val previousId = latestRun?.id
                            RunRecordingService.finish(context)
                            scope.launch {
                                var completed: RecordedRun? = null
                                repeat(20) {
                                    delay(250L)
                                    val candidate = store.runsFor(memberId).firstOrNull()
                                    if (candidate != null && candidate.id != previousId) {
                                        completed = candidate
                                        return@repeat
                                    }
                                }
                                completed?.let { run ->
                                    latestRun = run
                                    val km = runDistanceKm(run.points)
                                    val mins = maxOf(1, ((run.durationMillis + 30_000L) / 60_000L).toInt())
                                    runCatching {
                                        SupabaseSync.addEvent(
                                            session = session,
                                            title = "🏃 Löpning · ${"%.2f".format(Locale.US, km)} km · $mins min",
                                            date = Instant.ofEpochMilli(run.startedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate(),
                                            startTime = Instant.ofEpochMilli(run.startedAtMillis).atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0).toString(),
                                            endTime = null,
                                            memberId = memberId
                                        )
                                        onChanged()
                                        FamilyCalendarWidget.enqueueRefresh(context)
                                    }.onFailure { errorText = "Rundan sparades lokalt men kunde inte synkas till kalendern." }
                                } ?: run {
                                    errorText = "Rundan stoppades, men sparningen kunde inte bekräftas."
                                }
                                snapshot = RunRecordingService.snapshot(context)
                                busyFinishing = false
                            }
                        }
                    ) { Text(if (busyFinishing) "Sparar…" else "✓ Klar") }
                }

                anotherMemberRecording -> {
                    Text("En annan persons löprunda spelas redan in på den här enheten.", color = Muted, fontSize = 12.sp)
                }

                else -> {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            errorText = null
                            if (hasRunLocationPermission(context)) {
                                RunRecordingService.start(context, memberId)
                            } else {
                                val permissions = buildList {
                                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                                }.toTypedArray()
                                permissionLauncher.launch(permissions)
                            }
                        }
                    ) { Text("▶ Spela in rundan") }
                }
            }

            errorText?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }

            if (!isThisMemberRecording) {
                val savedRuns = store.runsFor(memberId)
                if (savedRuns.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Inspelade rundor", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Öppna valfri GPS-runda och spela upp den på kartan.", color = Muted, fontSize = 11.sp)
                    savedRuns.take(12).forEach { run ->
                        val km = runDistanceKm(run.points)
                        val seconds = run.durationMillis / 1000L
                        val date = Instant.ofEpochMilli(run.startedAtMillis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        val expanded = replayRunId == run.id
                        Surface(
                            color = CardBg,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(date.toString(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(
                                            "%.2f km · %s · %s".format(
                                                Locale.US,
                                                km,
                                                formatDuration(seconds),
                                                paceSecondsPerKm(km, seconds)?.let(::formatPace) ?: "–"
                                            ),
                                            color = Muted,
                                            fontSize = 11.sp
                                        )
                                    }
                                    TextButton(onClick = { replayRunId = if (expanded) null else run.id }) {
                                        Text(if (expanded) "Stäng" else "Visa / spela upp")
                                    }
                                }
                                if (expanded && run.points.isNotEmpty()) {
                                    RunReplayMap(run, Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()
                Text("Extern löpdata", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                HealthConnectSettingsCard(
                    session = session,
                    onSynced = onChanged
                )
            }
        }
    }
}

@Composable
private fun RecorderStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = CardBg) {
        Column(Modifier.padding(9.dp)) {
            Text(label, color = Muted, fontSize = 10.sp)
            Text(value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

private fun hasRunLocationPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun paceSecondsPerKm(distanceKm: Double, elapsedSeconds: Long): Int? =
    if (distanceKm >= 0.05 && elapsedSeconds > 0L) (elapsedSeconds / distanceKm).toInt().takeIf { it in 120..1800 } else null

private fun currentPaceSecondsPerKm(points: List<RecordedRoutePoint>): Int? {
    if (points.size < 3) return null
    val recent = points.takeLast(12)
    val km = runDistanceKm(recent)
    val seconds = ((recent.last().timestampMillis - recent.first().timestampMillis).coerceAtLeast(0L) / 1000L)
    return paceSecondsPerKm(km, seconds)
}

private fun formatPace(seconds: Int): String = "%d:%02d /km".format(seconds / 60, seconds % 60)

private fun formatDuration(seconds: Long): String = "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
