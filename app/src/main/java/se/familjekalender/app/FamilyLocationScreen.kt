package se.familjekalender.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

private const val LOCATION_PREFS = "family_calendar_location"
private const val LOCATION_CHANNEL = "family_location_alerts"

@Composable
fun FamilyLocationScreen(session: FamilySession, members: List<SyncMember>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE) }
    val familyMembers = remember(members) { members.filter { it.id != ALL_FAMILY_MEMBER_ID } }

    var selectedMemberId by remember { mutableStateOf(prefs.getString("device_member_id", null)) }
    var selectedMapMember by remember { mutableStateOf<String?>(null) }
    var sharing by remember { mutableStateOf(prefs.getBoolean("sharing_enabled", false)) }
    var batteryVisible by remember { mutableStateOf(prefs.getBoolean("battery_visible", true)) }
    var locations by remember { mutableStateOf(emptyList<SyncFamilyLocation>()) }
    var places by remember { mutableStateOf(emptyList<SyncFamilyPlace>()) }
    var status by remember { mutableStateOf("") }
    var memberMenu by remember { mutableStateOf(false) }
    var pendingEnableSharing by remember { mutableStateOf(false) }
    var showAddPlace by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showSecurity by remember { mutableStateOf(false) }
    var editingPlace by remember { mutableStateOf<SyncFamilyPlace?>(null) }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun readLocation(): Location? {
        if (!hasLocationPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.takeIf { last -> System.currentTimeMillis() - last.time <= 2 * 60_000L }
    }

    fun batteryPercent(): Int? =
        (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }

    suspend fun refresh() {
        runCatching {
            locations = FamilyLocationSync.loadLocations(session)
            places = FamilyLocationSync.loadPlaces(session)
            if (selectedMapMember == null) {
                selectedMapMember = selectedMemberId ?: locations.firstOrNull()?.memberId
            }
        }.onFailure {
            status = it.message ?: "Kunde inte uppdatera platsdata"
        }
    }

    suspend fun publishNow() {
        val memberId = selectedMemberId ?: run {
            status = "Välj vem den här telefonen tillhör"
            return
        }
        val location = readLocation() ?: run {
            status = "Ingen plats tillgänglig ännu"
            return
        }
        runCatching {
            FamilyLocationSync.publishLocation(
                session = session,
                memberId = memberId,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyM = location.accuracy.takeIf { it > 0 },
                batteryPercent = if (batteryVisible) batteryPercent() else null
            )
        }.onSuccess {
            status = "Platsen uppdaterades"
            refresh()
        }.onFailure {
            status = it.message ?: "Kunde inte dela plats"
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        status = if (granted) "Platsnotiser aktiverade" else "Notisbehörighet nekades"
    }

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            status = "Platsnotiser är aktiverade"
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            sharing = true
            prefs.edit().putBoolean("sharing_enabled", true).apply()
            status = "Platsdelning aktiverad"
            FamilyLocationService.start(context)
            scope.launch { publishNow() }
        } else {
            sharing = false
            prefs.edit().putBoolean("sharing_enabled", false).apply()
            status = "Platsbehörighet krävs"
        }
    }

    fun startSharing() {
        if (selectedMemberId == null) {
            pendingEnableSharing = true
            memberMenu = true
            status = "Välj vem den här telefonen tillhör"
            return
        }
        if (!hasLocationPermission()) {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            return
        }
        sharing = true
        prefs.edit().putBoolean("sharing_enabled", true).apply()
        status = "Platsdelning aktiverad"
        FamilyLocationService.start(context)
        scope.launch { publishNow() }
    }

    fun stopSharing() {
        pendingEnableSharing = false
        sharing = false
        prefs.edit().putBoolean("sharing_enabled", false).apply()
        status = "Platsdelning pausad"
        FamilyLocationService.stop(context)
        selectedMemberId?.let { id ->
            scope.launch {
                runCatching { FamilyLocationSync.stopSharing(session, id) }
                refresh()
            }
        }
    }

    suspend fun setArrivalAlerts(enabled: Boolean) {
        if (places.isEmpty()) {
            showAddPlace = true
            status = "Lägg till en destination först"
            return
        }
        runCatching {
            places.forEach { place ->
                FamilyLocationSync.updatePlaceAlerts(session, place, enabled, place.departureAlerts)
            }
        }.onSuccess {
            status = if (enabled) "Ankomstnotiser aktiverade" else "Ankomstnotiser avstängda"
            refresh()
        }.onFailure { status = it.message ?: "Kunde inte ändra ankomstnotiser" }
    }

    suspend fun setDepartureAlerts(enabled: Boolean) {
        if (places.isEmpty()) {
            showAddPlace = true
            status = "Lägg till en destination först"
            return
        }
        runCatching {
            places.forEach { place ->
                FamilyLocationSync.updatePlaceAlerts(session, place, place.arrivalAlerts, enabled)
            }
        }.onSuccess {
            status = if (enabled) "Avresenotiser aktiverade" else "Avresenotiser avstängda"
            refresh()
        }.onFailure { status = it.message ?: "Kunde inte ändra avresenotiser" }
    }

    LaunchedEffect(session.id, sharing, selectedMemberId) {
        if (sharing && selectedMemberId != null && hasLocationPermission()) {
            FamilyLocationService.start(context)
        }
        while (true) {
            refresh()
            checkLocationTransitions(context, session.id, locations, places, familyMembers)
            delay(15_000)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LocationMemberStrip(
            members = familyMembers,
            selectedMemberId = selectedMapMember,
            onSelect = { selectedMapMember = it }
        )

        LocationMapCard(
            locations = locations,
            members = familyMembers,
            selectedMemberId = selectedMapMember,
            batteryVisible = batteryVisible
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LocationQuickAction(Icons.Default.LocationOn, "Dela plats", Modifier.weight(1f)) {
                if (sharing) scope.launch { publishNow() } else startSharing()
            }
            LocationQuickAction(Icons.Default.Notifications, "Få notiser", Modifier.weight(1f)) {
                ensureNotificationPermission()
                if (places.isEmpty()) showAddPlace = true
            }
            LocationQuickAction(Icons.Default.Security, "Tryggt & säkert", Modifier.weight(1f)) {
                showSecurity = true
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Platsdelning", fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Switch(
                        checked = sharing,
                        onCheckedChange = { enabled -> if (enabled) startSharing() else stopSharing() },
                        enabled = familyMembers.isNotEmpty()
                    )
                }

                Box {
                    LocationSettingRow(
                        icon = Icons.Default.PersonPinCircle,
                        title = "Den här telefonen",
                        subtitle = familyMembers.firstOrNull { it.id == selectedMemberId }?.name ?: "Välj familjemedlem"
                    ) { memberMenu = true }

                    DropdownMenu(expanded = memberMenu, onDismissRequest = { memberMenu = false }) {
                        familyMembers.forEach { member ->
                            DropdownMenuItem(
                                text = { Text(member.name) },
                                onClick = {
                                    val shouldEnable = pendingEnableSharing
                                    selectedMemberId = member.id
                                    selectedMapMember = member.id
                                    prefs.edit().putString("device_member_id", member.id).apply()
                                    memberMenu = false
                                    pendingEnableSharing = false
                                    status = "Den här telefonen är kopplad till ${member.name}"
                                    if (shouldEnable) startSharing() else if (sharing) FamilyLocationService.start(context)
                                }
                            )
                        }
                    }
                }

                LocationSettingRow(
                    Icons.Default.LocationOn,
                    "Dela min plats",
                    if (sharing) "Aktiv även när appen ligger i bakgrunden" else "Avstängd"
                ) {
                    if (sharing) scope.launch { publishNow() } else startSharing()
                }

                val arrivalEnabled = places.isNotEmpty() && places.any { it.arrivalAlerts }
                LocationSettingRow(
                    Icons.Default.Notifications,
                    "Få ankomstnotiser",
                    when {
                        places.isEmpty() -> "Lägg till en destination först"
                        arrivalEnabled -> "Aktiverat"
                        else -> "Avstängt"
                    }
                ) {
                    ensureNotificationPermission()
                    scope.launch { setArrivalAlerts(!arrivalEnabled) }
                }

                val departureEnabled = places.isNotEmpty() && places.any { it.departureAlerts }
                LocationSettingRow(
                    Icons.Default.NotificationsActive,
                    "Få avresenotiser",
                    when {
                        places.isEmpty() -> "Lägg till en destination först"
                        departureEnabled -> "Aktiverat"
                        else -> "Avstängt"
                    }
                ) {
                    ensureNotificationPermission()
                    scope.launch { setDepartureAlerts(!departureEnabled) }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LocationIcon(Icons.Default.BatteryFull)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Batterinivå", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Visa batterinivå (valfritt)", color = Muted, fontSize = 13.sp)
                    }
                    Switch(
                        checked = batteryVisible,
                        onCheckedChange = {
                            batteryVisible = it
                            prefs.edit().putBoolean("battery_visible", it).apply()
                            status = if (it) "Batterinivå visas" else "Batterinivå dold"
                            if (sharing) scope.launch { publishNow() }
                        }
                    )
                }

                LocationSettingRow(Icons.Default.History, "Historik", "Se senaste platser") {
                    showHistory = true
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Destinationer", fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = { showAddPlace = true }, shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Lägg till destination")
            }
        }

        if (places.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { showAddPlace = true },
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Inga destinationer ännu", fontWeight = FontWeight.Bold)
                    Text("Lägg till till exempel Hemma, Skola eller Jobb för ankomst- och avresenotiser.", color = Muted, fontSize = 13.sp)
                }
            }
        } else {
            places.forEach { place ->
                DestinationCard(
                    place = place,
                    onToggleArrival = { enabled ->
                        ensureNotificationPermission()
                        scope.launch {
                            runCatching { FamilyLocationSync.updatePlaceAlerts(session, place, enabled, place.departureAlerts) }
                            refresh()
                        }
                    },
                    onToggleDeparture = { enabled ->
                        ensureNotificationPermission()
                        scope.launch {
                            runCatching { FamilyLocationSync.updatePlaceAlerts(session, place, place.arrivalAlerts, enabled) }
                            refresh()
                        }
                    },
                    onDelete = {
                        editingPlace = place
                    }
                )
            }
        }

        if (status.isNotBlank()) {
            Text(status, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
    }

    if (showAddPlace) {
        AddDestinationDialog(
            onDismiss = { showAddPlace = false },
            onSave = { name, address, radius ->
                scope.launch {
                    val coords = geocodeAddress(context, address)
                    if (coords == null) {
                        status = "Kunde inte hitta adressen"
                    } else {
                        runCatching {
                            FamilyLocationSync.addPlace(session, name, coords.first, coords.second, radius)
                        }.onSuccess {
                            status = "$name tillagd"
                            showAddPlace = false
                            ensureNotificationPermission()
                            refresh()
                        }.onFailure { status = it.message ?: "Kunde inte lägga till destination" }
                    }
                }
            }
        )
    }

    editingPlace?.let { place ->
        AlertDialog(
            onDismissRequest = { editingPlace = null },
            title = { Text("Ta bort ${place.name}?") },
            text = { Text("Destinationen och dess ankomst-/avresenotiser tas bort.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { FamilyLocationSync.deletePlace(session, place.id) }
                        editingPlace = null
                        refresh()
                    }
                }) { Text("Ta bort") }
            },
            dismissButton = { TextButton(onClick = { editingPlace = null }) { Text("Avbryt") } }
        )
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("Senaste platser") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (locations.isEmpty()) {
                        Text("Ingen platsdata finns ännu.", color = Muted)
                    } else {
                        locations.take(10).forEach { item ->
                            val member = familyMembers.firstOrNull { it.id == item.memberId }
                            Column {
                                Text(member?.name ?: "Familjemedlem", fontWeight = FontWeight.Bold)
                                Text(formatUpdated(item.updatedAt), color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Stäng") } }
        )
    }

    if (showSecurity) {
        AlertDialog(
            onDismissRequest = { showSecurity = false },
            title = { Text("Tryggt & säkert") },
            text = {
                Text("Du kan pausa platsdelningen när som helst. Ankomst- och avresenotiser styrs separat per destination, och batterivisning kan stängas av.")
            },
            confirmButton = { TextButton(onClick = { showSecurity = false }) { Text("Stäng") } }
        )
    }
}

@Composable
private fun LocationMemberStrip(
    members: List<SyncMember>,
    selectedMemberId: String?,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        members.forEach { member ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(60.dp).clickable { onSelect(member.id) }
            ) {
                Box(
                    modifier = Modifier.size(50.dp).clip(CircleShape).background(Color(member.colorArgb)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.size(if (selectedMemberId == member.id) 40.dp else 42.dp).clip(CircleShape).background(Color(0xFF262330)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(member.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(member.name, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LocationMapCard(
    locations: List<SyncFamilyLocation>,
    members: List<SyncMember>,
    selectedMemberId: String?,
    batteryVisible: Boolean
) {
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val selected = locations.firstOrNull { it.memberId == selectedMemberId } ?: locations.firstOrNull()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    Configuration.getInstance().userAgentValue = context.packageName
                    MapView(context).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(13.0)
                        mapView = this
                    }
                },
                update = { map ->
                    map.overlays.clear()
                    locations.forEach { item ->
                        val member = members.firstOrNull { it.id == item.memberId }
                        map.overlays.add(
                            Marker(map).apply {
                                position = GeoPoint(item.latitude, item.longitude)
                                title = member?.name ?: "Familjemedlem"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                        )
                    }
                    selected?.let {
                        map.controller.animateTo(GeoPoint(it.latitude, it.longitude))
                    }
                    map.invalidate()
                }
            )

            selected?.let { item ->
                val member = members.firstOrNull { it.id == item.memberId }
                Card(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xEE17151F)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(46.dp).clip(CircleShape).background(member?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(member?.name?.take(1)?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(member?.name ?: "Familjemedlem", fontWeight = FontWeight.Bold)
                            Text(formatUpdated(item.updatedAt), color = Muted, fontSize = 12.sp)
                            if (batteryVisible && item.batteryPercent != null) {
                                Text("Batteri ${item.batteryPercent}%", color = Color(0xFF7EE2A8), fontSize = 12.sp)
                            }
                        }
                        IconButton(onClick = {
                            mapView?.controller?.setZoom(17.0)
                            mapView?.controller?.animateTo(GeoPoint(item.latitude, item.longitude))
                        }) {
                            Icon(Icons.Default.MyLocation, contentDescription = "Centrera", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationQuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = text, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(5.dp))
        Text(text, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun LocationIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(shape = CircleShape, color = Color(0xFF292634)) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(9.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LocationSettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LocationIcon(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted)
    }
}

@Composable
private fun DestinationCard(
    place: SyncFamilyPlace,
    onToggleArrival: (Boolean) -> Unit,
    onToggleDeparture: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LocationIcon(Icons.Default.Place)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(place.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Radie ${place.radiusM} m", color = Muted, fontSize = 12.sp)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Ta bort", tint = Muted)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ankomstnotis", modifier = Modifier.weight(1f), fontSize = 13.sp)
                Switch(checked = place.arrivalAlerts, onCheckedChange = onToggleArrival)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Avresenotis", modifier = Modifier.weight(1f), fontSize = 13.sp)
                Switch(checked = place.departureAlerts, onCheckedChange = onToggleDeparture)
            }
        }
    }
}

@Composable
private fun AddDestinationDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var radiusText by remember { mutableStateOf("150") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lägg till destination") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Namn, t.ex. Hemma") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Adress") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = radiusText,
                    onValueChange = { radiusText = it.filter(Char::isDigit).take(4) },
                    label = { Text("Radie i meter") },
                    singleLine = true
                )
                Text("Ankomst- och avresenotiser aktiveras för nya destinationer och kan ändras efteråt.", color = Muted, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), address.trim(), radiusText.toIntOrNull()?.coerceIn(50, 2000) ?: 150) },
                enabled = name.isNotBlank() && address.isNotBlank()
            ) { Text("Lägg till") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } }
    )
}

private suspend fun geocodeAddress(context: Context, query: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    runCatching {
        @Suppress("DEPRECATION")
        Geocoder(context, Locale("sv", "SE")).getFromLocationName(query, 1)?.firstOrNull()?.let {
            it.latitude to it.longitude
        }
    }.getOrNull()
}

private fun formatUpdated(raw: String): String {
    return runCatching {
        val dt = OffsetDateTime.parse(raw)
        "Senast sedd · ${dt.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    }.getOrDefault("Senast sedd")
}

private fun distanceMeters(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val earth = 6_371_000.0
    val dLat = Math.toRadians(bLat - aLat)
    val dLon = Math.toRadians(bLon - aLon)
    val lat1 = Math.toRadians(aLat)
    val lat2 = Math.toRadians(bLat)
    val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    return 2 * earth * asin(sqrt(h))
}

internal fun checkLocationTransitions(
    context: Context,
    familyId: String,
    locations: List<SyncFamilyLocation>,
    places: List<SyncFamilyPlace>,
    members: List<SyncMember>
) {
    if (locations.isEmpty() || places.isEmpty()) return
    val prefs = context.getSharedPreferences("location_geofence_state_$familyId", Context.MODE_PRIVATE)

    locations.forEach { location ->
        places.forEach { place ->
            val inside = distanceMeters(location.latitude, location.longitude, place.latitude, place.longitude) <= place.radiusM
            val key = "${location.memberId}_${place.id}"
            val hadState = prefs.contains(key)
            val previousInside = prefs.getBoolean(key, inside)

            if (hadState && previousInside != inside) {
                val memberName = members.firstOrNull { it.id == location.memberId }?.name ?: "En familjemedlem"
                if (inside && place.arrivalAlerts) {
                    showLocationNotification(context, "$memberName har kommit fram", "$memberName har kommit till ${place.name}", key.hashCode())
                } else if (!inside && place.departureAlerts) {
                    showLocationNotification(context, "$memberName har lämnat", "$memberName har lämnat ${place.name}", key.hashCode())
                }
            }
            prefs.edit().putBoolean(key, inside).apply()
        }
    }
}

private fun showLocationNotification(context: Context, title: String, text: String, id: Int) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager.createNotificationChannel(
            NotificationChannel(LOCATION_CHANNEL, "Platsnotiser", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val notification = NotificationCompat.Builder(context, LOCATION_CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher_calendar)
        .setContentTitle(title)
        .setContentText(text)
        .setAutoCancel(true)
        .build()
    manager.notify(id, notification)
}
