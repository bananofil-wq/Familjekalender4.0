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
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
    val familyMembers = remember(members) { members.filter { it.id != ALL_FAMILY_MEMBER_ID } }

    var selectedMemberId by remember { mutableStateOf(prefs.getString("device_member_id", null)) }
    var selectedMapMember by remember { mutableStateOf<String?>(null) }
    var sharing by remember { mutableStateOf(prefs.getBoolean("sharing_enabled", false)) }
    var locations by remember { mutableStateOf(emptyList<SyncFamilyLocation>()) }
    var places by remember { mutableStateOf(emptyList<SyncFamilyPlace>()) }
    var placeName by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var memberMenu by remember { mutableStateOf(false) }
    var showAddPlace by remember { mutableStateOf(false) }
    var batteryVisible by remember { mutableStateOf(prefs.getBoolean("battery_visible", true)) }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun readLocation(): Location? {
        if (!hasLocationPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    fun batteryPercent(): Int? =
        (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }

    suspend fun refresh() {
        runCatching {
            locations = FamilyLocationSync.loadLocations(session)
            places = FamilyLocationSync.loadPlaces(session)
            if (selectedMapMember == null) selectedMapMember = locations.firstOrNull()?.memberId
        }.onFailure { status = it.message ?: "Kunde inte uppdatera platsdata" }
    }

    suspend fun publishNow() {
        val id = selectedMemberId ?: return
        val loc = readLocation() ?: run {
            status = "Ingen plats tillgänglig ännu"
            return
        }
        FamilyLocationSync.publishLocation(
            session = session,
            memberId = id,
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracyM = loc.accuracy.takeIf { it > 0 },
            batteryPercent = batteryPercent()
        )
        refresh()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!granted) {
            sharing = false
            prefs.edit().putBoolean("sharing_enabled", false).apply()
            status = "Platsbehörighet krävs"
        } else if (sharing) {
            scope.launch { publishNow() }
        }
    }

    LaunchedEffect(session.id, sharing, selectedMemberId) {
        refresh()
        while (true) {
            if (sharing && selectedMemberId != null && hasLocationPermission()) {
                runCatching { publishNow() }
            }
            runCatching { refresh() }
            checkPlaceTransitions(context, session.id, locations, places, familyMembers)
            delay(30_000)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Familjekalender",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            familyMembers.forEach { member ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(58.dp)
                        .clickable { selectedMapMember = member.id }
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(member.colorArgb)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF262330)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                member.name.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(member.name, fontSize = 10.sp, maxLines = 1)
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp)) {
                Surface(
                    modifier = Modifier.size(50.dp),
                    shape = CircleShape,
                    color = Color(0xFF24212F),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF5E5871))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = "Lägg till", tint = Muted)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Lägg till", fontSize = 10.sp, color = Muted)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
            ) {
                FamilyMap(
                    locations = locations,
                    members = familyMembers,
                    selectedMemberId = selectedMapMember,
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MapCircleButton(Icons.Default.MyLocation) {
                        selectedMapMember = selectedMemberId
                    }
                    MapCircleButton(Icons.Default.Layers) { }
                }

                val chosen = locations.firstOrNull { it.memberId == selectedMapMember }
                    ?: locations.firstOrNull()

                chosen?.let { item ->
                    val member = familyMembers.firstOrNull { it.id == item.memberId }
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xF217151F))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(member?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF292633)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        member?.name?.take(1)?.uppercase() ?: "?",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(Modifier.weight(1f)) {
                                Text(
                                    member?.name ?: "Familjemedlem",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                                Text(
                                    "Senast sedd · ${formatUpdated(item.updatedAt).removePrefix("Uppdaterad ")}",
                                    color = Muted,
                                    fontSize = 12.sp
                                )
                                if (batteryVisible && item.batteryPercent != null) {
                                    Text(
                                        "▰ ${item.batteryPercent}%",
                                        color = Color(0xFF7EE2A8),
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("geo:${item.latitude},${item.longitude}?q=${item.latitude},${item.longitude}(${Uri.encode(member?.name ?: "Familj")})")
                                        )
                                    )
                                }
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted)
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FeaturePill(Icons.Default.LocationOn, "Dela plats", Modifier.weight(1f))
            FeaturePill(Icons.Default.Notifications, "Få notiser", Modifier.weight(1f))
            FeaturePill(Icons.Default.Security, "Tryggt & säkert", Modifier.weight(1f))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Platsdelning", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Switch(
                        checked = sharing,
                        onCheckedChange = { enabled ->
                            if (enabled && !hasLocationPermission()) {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                            sharing = enabled
                            prefs.edit().putBoolean("sharing_enabled", enabled).apply()
                            if (enabled) {
                                scope.launch { publishNow() }
                            } else {
                                selectedMemberId?.let { id ->
                                    scope.launch {
                                        runCatching { FamilyLocationSync.stopSharing(session, id) }
                                        refresh()
                                    }
                                }
                            }
                        },
                        enabled = selectedMemberId != null
                    )
                }

                Box {
                    SettingRow(
                        icon = Icons.Default.PersonPinCircle,
                        title = "Den här telefonen",
                        subtitle = familyMembers.firstOrNull { it.id == selectedMemberId }?.name ?: "Välj familjemedlem"
                    ) { memberMenu = true }

                    DropdownMenu(expanded = memberMenu, onDismissRequest = { memberMenu = false }) {
                        familyMembers.forEach { member ->
                            DropdownMenuItem(
                                text = { Text(member.name) },
                                onClick = {
                                    selectedMemberId = member.id
                                    selectedMapMember = member.id
                                    prefs.edit().putString("device_member_id", member.id).apply()
                                    memberMenu = false
                                }
                            )
                        }
                    }
                }

                SettingRow(
                    Icons.Default.LocationOn,
                    "Dela min plats",
                    if (sharing) "Syns för familjens medlemmar" else "Avstängd"
                ) {
                    if (sharing) scope.launch { publishNow() }
                }

                SettingRow(
                    Icons.Default.Notifications,
                    "Få ankomstnotiser",
                    "När någon kommer fram"
                ) { }

                SettingRow(
                    Icons.Default.NotificationsActive,
                    "Få avresenotiser",
                    "När någon lämnar en plats"
                ) { }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = CircleShape, color = Color(0xFF292634)) {
                        Icon(
                            Icons.Default.BatteryFull,
                            contentDescription = null,
                            modifier = Modifier.padding(9.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Batterinivå", fontWeight = FontWeight.SemiBold)
                        Text("Visa batterinivå (valfritt)", color = Muted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = batteryVisible,
                        onCheckedChange = {
                            batteryVisible = it
                            prefs.edit().putBoolean("battery_visible", it).apply()
                        }
                    )
                }

                SettingRow(Icons.Default.History, "Historik", "Se senaste platser") { }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Platser", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = { showAddPlace = !showAddPlace }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(" Lägg till plats")
            }
        }

        if (showAddPlace) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = placeName,
                        onValueChange = { placeName = it },
                        label = { Text("Namn, t.ex. Hem eller Skola") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val loc = readLocation()
                            if (loc == null) {
                                status = "Aktivera plats och försök igen"
                            } else {
                                scope.launch {
                                    runCatching {
                                        FamilyLocationSync.addPlace(
                                            session,
                                            placeName.trim(),
                                            loc.latitude,
                                            loc.longitude
                                        )
                                    }.onSuccess {
                                        placeName = ""
                                        showAddPlace = false
                                        refresh()
                                    }.onFailure {
                                        status = it.message ?: "Kunde inte spara plats"
                                    }
                                }
                            }
                        },
                        enabled = placeName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AddLocationAlt, contentDescription = null)
                        Text(" Spara min nuvarande plats")
                    }
                }
            }
        }

        places.forEachIndexed { index, place ->
            PlaceRow(
                place = place,
                index = index,
                onArrival = { value ->
                    scope.launch {
                        FamilyLocationSync.updatePlaceAlerts(session, place, value, place.departureAlerts)
                        refresh()
                    }
                },
                onDeparture = { value ->
                    scope.launch {
                        FamilyLocationSync.updatePlaceAlerts(session, place, place.arrivalAlerts, value)
                        refresh()
                    }
                },
                onDelete = {
                    scope.launch {
                        FamilyLocationSync.deletePlace(session, place.id)
                        refresh()
                    }
                }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Säkerhet & integritet", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                SecurityLine("Du bestämmer vem som ser din plats")
                SecurityLine("Dela bara med familjen")
                SecurityLine("Platsdata delas skyddat")
                SecurityLine("Går att pausa när som helst")

                OutlinedButton(
                    onClick = {
                        sharing = false
                        prefs.edit().putBoolean("sharing_enabled", false).apply()
                        selectedMemberId?.let { id ->
                            scope.launch {
                                FamilyLocationSync.stopSharing(session, id)
                                refresh()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Default.Pause, contentDescription = null)
                    Text(" Pausa platsdelning")
                }
            }
        }

        if (status.isNotBlank()) {
            Text(status, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun MapCircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = Color(0xE617151F)) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
private fun FeaturePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(text, fontSize = 11.sp, color = Muted)
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = Color(0xFF292634)) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(9.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted)
    }
}

@Composable
private fun PlaceRow(
    place: SyncFamilyPlace,
    index: Int,
    onArrival: (Boolean) -> Unit,
    onDeparture: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Color(0xFF292634)) {
                    Icon(
                        when (index % 4) {
                            0 -> Icons.Default.Home
                            1 -> Icons.Default.School
                            2 -> Icons.Default.SportsHockey
                            else -> Icons.Default.Favorite
                        },
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(place.name, fontWeight = FontWeight.Bold)
                    Text("Radie ${place.radiusM} m", color = Muted, fontSize = 12.sp)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = Muted)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(" Ankomst", modifier = Modifier.weight(1f), color = Muted, fontSize = 12.sp)
                Switch(checked = place.arrivalAlerts, onCheckedChange = onArrival)
                Spacer(Modifier.width(10.dp))
                Text("Avresa", color = Muted, fontSize = 12.sp)
                Switch(checked = place.departureAlerts, onCheckedChange = onDeparture)
            }
        }
    }
}

@Composable
private fun SecurityLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = Color(0xFF58D07C), modifier = Modifier.size(22.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.width(9.dp))
        Text(text, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun FamilyMap(
    locations: List<SyncFamilyLocation>,
    members: List<SyncMember>,
    selectedMemberId: String?,
    modifier: Modifier = Modifier
) {
    val center = locations.firstOrNull { it.memberId == selectedMemberId } ?: locations.firstOrNull()
    val markers = locations.joinToString("\n") { loc ->
        val member = members.firstOrNull { it.id == loc.memberId }
        val color = member?.let {
            String.format("#%06X", 0xFFFFFF and Color(it.colorArgb).toArgb())
        } ?: "#8B5CF6"
        val label = (member?.name ?: "Familj").replace("'", "\\'")
        val selected = loc.memberId == selectedMemberId
        val radius = if (selected) 18 else 14
        val weight = if (selected) 6 else 4
        "L.circleMarker([${loc.latitude},${loc.longitude}],{radius:$radius,color:'$color',weight:$weight,fillColor:'$color',fillOpacity:.95}).addTo(map).bindTooltip('$label',{permanent:true,direction:'top',offset:[0,-18],className:'nameTag'});"
    }
    val lat = center?.latitude ?: 55.7047
    val lon = center?.longitude ?: 13.1910
    val html = """
        <!doctype html>
        <html>
        <head>
            <meta name='viewport' content='width=device-width,initial-scale=1'>
            <link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>
            <style>
                html,body,#map{height:100%;margin:0;background:#0d111c}
                .leaflet-tile{filter:brightness(.42) saturate(.72) hue-rotate(185deg) contrast(1.08)}
                .leaflet-control-attribution{display:none}
                .nameTag{background:#17151f;color:white;border:0;border-radius:12px;padding:5px 8px;font:600 12px sans-serif;box-shadow:0 2px 8px rgba(0,0,0,.35)}
            </style>
        </head>
        <body>
            <div id='map'></div>
            <script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>
            <script>
                var map=L.map('map',{zoomControl:false,attributionControl:false}).setView([$lat,$lon],13);
                L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19}).addTo(map);
                $markers
            </script>
        </body>
        </html>
    """.trimIndent()

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(android.graphics.Color.rgb(13, 17, 28))
                loadDataWithBaseURL("https://familjekalender.local/", html, "text/html", "UTF-8", null)
            }
        },
        update = {
            it.loadDataWithBaseURL("https://familjekalender.local/", html, "text/html", "UTF-8", null)
        },
        modifier = modifier
    )
}

private fun formatUpdated(value: String): String = runCatching {
    "Uppdaterad ${OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("HH:mm"))}"
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
        manager.createNotificationChannel(
            NotificationChannel("family_location", "Familjeplats", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    for (location in locations) {
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
                if (shouldNotify &&
                    (android.os.Build.VERSION.SDK_INT < 33 ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                ) {
                    val name = members.firstOrNull { it.id == location.memberId }?.name ?: "En familjemedlem"
                    val text = if (inside) {
                        "$name har kommit fram till ${place.name}"
                    } else {
                        "$name har lämnat ${place.name}"
                    }
                    manager.notify(
                        key.hashCode(),
                        NotificationCompat.Builder(context, "family_location")
                            .setSmallIcon(android.R.drawable.ic_dialog_map)
                            .setContentTitle("Familjekalendern")
                            .setContentText(text)
                            .setAutoCancel(true)
                            .build()
                    )
                }
            }
        }
    }
}
