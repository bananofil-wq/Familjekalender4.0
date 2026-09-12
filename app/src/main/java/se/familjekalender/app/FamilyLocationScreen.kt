package se.familjekalender.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PersonPinCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.*

@Composable
fun FamilyLocationScreen(session: FamilySession, members: List<SyncMember>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("family_calendar_location", Context.MODE_PRIVATE) }
    var selectedMemberId by remember { mutableStateOf(prefs.getString("device_member_id", null)) }
    var sharing by remember { mutableStateOf(prefs.getBoolean("sharing_enabled", false)) }
    var locations by remember { mutableStateOf(emptyList<SyncFamilyLocation>()) }
    var places by remember { mutableStateOf(emptyList<SyncFamilyPlace>()) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var placeName by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var memberMenu by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            status = "Platsbehörighet klar"
        } else {
            sharing = false
            prefs.edit().putBoolean("sharing_enabled", false).apply()
            status = "Platsbehörighet krävs"
        }
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun readLocation(): Location? {
        if (!hasLocationPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        return providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    fun batteryPercent(): Int? {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
    }

    suspend fun refresh() {
        runCatching {
            locations = FamilyLocationSync.loadLocations(session)
            places = FamilyLocationSync.loadPlaces(session)
        }.onFailure { status = it.message ?: "Kunde inte uppdatera platsdata" }
    }

    suspend fun publishNow() {
        val memberId = selectedMemberId ?: return
        val loc = readLocation()
        currentLocation = loc
        if (loc == null) {
            status = "Ingen plats tillgänglig ännu"
            return
        }
        FamilyLocationSync.publishLocation(
            session,
            memberId,
            loc.latitude,
            loc.longitude,
            loc.accuracy.takeIf { it > 0f },
            batteryPercent()
        )
        status = "Plats uppdaterad"
        refresh()
    }

    LaunchedEffect(session.id) {
        refresh()
        while (true) {
            if (sharing && selectedMemberId != null && hasLocationPermission()) {
                runCatching { publishNow() }
            } else {
                currentLocation = readLocation()
            }
            runCatching { refresh() }
            checkPlaceTransitions(context, session.id, locations, places, members)
            delay(30_000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Familjeplats", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Dela plats med familjen och få ankomst- och avresenotiser.", color = Muted)

        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Den här telefonen", fontWeight = FontWeight.Bold)
                Box {
                    OutlinedButton(onClick = { memberMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PersonPinCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text(members.firstOrNull { it.id == selectedMemberId }?.name ?: "Välj familjemedlem")
                    }
                    DropdownMenu(expanded = memberMenu, onDismissRequest = { memberMenu = false }) {
                        members.forEach { member ->
                            DropdownMenuItem(
                                text = { Text(member.name) },
                                onClick = {
                                    selectedMemberId = member.id
                                    prefs.edit().putString("device_member_id", member.id).apply()
                                    memberMenu = false
                                }
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Dela min plats", fontWeight = FontWeight.SemiBold)
                        Text(if (sharing) "Aktiv när appen körs" else "Avstängd", color = Muted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = sharing,
                        onCheckedChange = { enabled ->
                            if (enabled && !hasLocationPermission()) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            }
                            sharing = enabled
                            prefs.edit().putBoolean("sharing_enabled", enabled).apply()
                            if (enabled) scope.launch { runCatching { publishNow() } }
                            else selectedMemberId?.let { id -> scope.launch { runCatching { FamilyLocationSync.stopSharing(session, id) }; refresh() } }
                        },
                        enabled = selectedMemberId != null
                    )
                }

                OutlinedButton(
                    onClick = {
                        if (!hasLocationPermission()) permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        else scope.launch { publishNow() }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedMemberId != null
                ) {
                    Icon(Icons.Default.MyLocation, null)
                    Text(" Uppdatera plats nu")
                }
            }
        }

        Text("Familjen", fontWeight = FontWeight.Bold)
        if (locations.isEmpty()) {
            Text("Ingen delar plats ännu.", color = Muted)
        } else {
            locations.forEach { item ->
                val member = members.firstOrNull { it.id == item.memberId }
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${item.latitude},${item.longitude}?q=${item.latitude},${item.longitude}(${Uri.encode(member?.name ?: "Familj")})")))
                        }
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(member?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.LocationOn, null, tint = Color.Black.copy(alpha = .75f)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(member?.name ?: "Familjemedlem", fontWeight = FontWeight.Bold)
                            Text("${"%.5f".format(item.latitude)}, ${"%.5f".format(item.longitude)}", color = Muted, fontSize = 12.sp)
                            Text(formatUpdated(item.updatedAt), color = Muted, fontSize = 11.sp)
                        }
                        if (item.batteryPercent != null) Text("${item.batteryPercent}%", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.OpenInNew, null, tint = Muted)
                    }
                }
            }
        }

        Text("Platser", fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(placeName, { placeName = it }, label = { Text("Namn, t.ex. Hem eller Skola") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        val loc = readLocation()
                        if (loc == null) {
                            status = "Aktivera plats och försök igen"
                        } else {
                            scope.launch {
                                runCatching { FamilyLocationSync.addPlace(session, placeName.trim(), loc.latitude, loc.longitude) }
                                    .onSuccess { placeName = ""; status = "Plats sparad"; refresh() }
                                    .onFailure { status = it.message ?: "Kunde inte spara plats" }
                            }
                        }
                    },
                    enabled = placeName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddLocationAlt, null)
                    Text(" Spara min nuvarande plats")
                }
            }
        }

        places.forEach { place ->
            Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(place.name, fontWeight = FontWeight.Bold)
                            Text("Radie ${place.radiusM} m", color = Muted, fontSize = 12.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, null, tint = Muted)
                        Text(" Ankomst", modifier = Modifier.weight(1f))
                        Switch(
                            checked = place.arrivalAlerts,
                            onCheckedChange = { value -> scope.launch { FamilyLocationSync.updatePlaceAlerts(session, place, value, place.departureAlerts); refresh() } }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOff, null, tint = Muted)
                        Text(" Avresa", modifier = Modifier.weight(1f))
                        Switch(
                            checked = place.departureAlerts,
                            onCheckedChange = { value -> scope.launch { FamilyLocationSync.updatePlaceAlerts(session, place, place.arrivalAlerts, value); refresh() } }
                        )
                    }
                    TextButton(onClick = { scope.launch { FamilyLocationSync.deletePlace(session, place.id); refresh() } }) {
                        Text("Ta bort plats", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        Text("Platsdelning kan pausas när som helst. Den här första versionen uppdaterar plats när appen körs.", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))
    }
}

private fun formatUpdated(value: String): String = runCatching {
    val dt = OffsetDateTime.parse(value)
    "Uppdaterad ${dt.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}.getOrDefault("Senast uppdaterad")

private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dp = Math.toRadians(lat2 - lat1)
    val dl = Math.toRadians(lon2 - lon1)
    val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun checkPlaceTransitions(
    context: Context,
    familyId: String,
    locations: List<SyncFamilyLocation>,
    places: List<SyncFamilyPlace>,
    members: List<SyncMember>
) {
    if (locations.isEmpty() || places.isEmpty()) return
    val prefs = context.getSharedPreferences("family_location_geofence", Context.MODE_PRIVATE)
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (android.os.Build.VERSION.SDK_INT >= 26) {
        manager.createNotificationChannel(NotificationChannel("family_location", "Familjeplats", NotificationManager.IMPORTANCE_DEFAULT))
    }
    for (location in locations) {
        val memberName = members.firstOrNull { it.id == location.memberId }?.name ?: "En familjemedlem"
        for (place in places) {
            val inside = distanceMeters(location.latitude, location.longitude, place.latitude, place.longitude) <= place.radiusM
            val key = "${familyId}_${location.memberId}_${place.id}"
            if (!prefs.contains(key)) {
                prefs.edit().putBoolean(key, inside).apply()
                continue
            }
            val before = prefs.getBoolean(key, inside)
            if (inside != before) {
                prefs.edit().putBoolean(key, inside).apply()
                val shouldNotify = (inside && place.arrivalAlerts) || (!inside && place.departureAlerts)
                if (shouldNotify && (android.os.Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) {
                    val text = if (inside) "$memberName har kommit fram till ${place.name}" else "$memberName har lämnat ${place.name}"
                    val notification = NotificationCompat.Builder(context, "family_location")
                        .setSmallIcon(android.R.drawable.ic_dialog_map)
                        .setContentTitle("Familjekalendern")
                        .setContentText(text)
                        .setAutoCancel(true)
                        .build()
                    manager.notify(key.hashCode(), notification)
                }
            }
        }
    }
}
