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
    var showSettings by remember { mutableStateOf(false) }

    fun hasLocationPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun readLocation(): Location? {
        if (!hasLocationPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }
    fun batteryPercent(): Int? = (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
    suspend fun refresh() { runCatching { locations = FamilyLocationSync.loadLocations(session); places = FamilyLocationSync.loadPlaces(session) }.onFailure { status = it.message ?: "Kunde inte uppdatera platsdata" } }
    suspend fun publishNow() {
        val id = selectedMemberId ?: return
        val loc = readLocation() ?: run { status = "Ingen plats tillgänglig ännu"; return }
        FamilyLocationSync.publishLocation(session, id, loc.latitude, loc.longitude, loc.accuracy.takeIf { it > 0 }, batteryPercent())
        status = "Plats uppdaterad"; refresh()
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] != true && result[Manifest.permission.ACCESS_COARSE_LOCATION] != true) {
            sharing = false; prefs.edit().putBoolean("sharing_enabled", false).apply(); status = "Platsbehörighet krävs"
        } else if (sharing) scope.launch { publishNow() }
    }

    LaunchedEffect(session.id, sharing, selectedMemberId) {
        refresh()
        while (true) {
            if (sharing && selectedMemberId != null && hasLocationPermission()) runCatching { publishNow() }
            runCatching { refresh() }
            checkPlaceTransitions(context, session.id, locations, places, familyMembers)
            delay(30_000)
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Familjeplats", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Se var familjen är och få notiser när de kommer fram eller åker.", color = Muted)

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            familyMembers.forEach { member ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { selectedMapMember = member.id }) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(Color(member.colorArgb)), contentAlignment = Alignment.Center) {
                        Text(member.name.take(1).uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(member.name, fontSize = 11.sp, maxLines = 1)
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(360.dp)) {
                FamilyMap(locations, familyMembers, Modifier.fillMaxSize())
                Surface(Modifier.align(Alignment.TopEnd).padding(12.dp), shape = CircleShape, color = Color(0xDD17151F)) {
                    IconButton(onClick = { readLocation()?.let { loc -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}"))) } }) { Icon(Icons.Default.MyLocation, "Min plats") }
                }
                val chosen = locations.firstOrNull { it.memberId == selectedMapMember } ?: locations.firstOrNull()
                chosen?.let { item ->
                    val member = familyMembers.firstOrNull { it.id == item.memberId }
                    Card(Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xEE17151F)), shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(46.dp).clip(CircleShape).background(member?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) { Text(member?.name?.take(1)?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(member?.name ?: "Familjemedlem", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text(formatUpdated(item.updatedAt), color = Muted, fontSize = 12.sp)
                                item.batteryPercent?.let { Text("▰ $it%", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
                            }
                            IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${item.latitude},${item.longitude}?q=${item.latitude},${item.longitude}(${Uri.encode(member?.name ?: "Familj")})"))) }) { Icon(Icons.Default.ChevronRight, null) }
                        }
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Platsdelning", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Switch(checked = sharing, onCheckedChange = { enabled ->
                        if (enabled && !hasLocationPermission()) permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        sharing = enabled; prefs.edit().putBoolean("sharing_enabled", enabled).apply()
                        if (enabled) scope.launch { publishNow() } else selectedMemberId?.let { id -> scope.launch { runCatching { FamilyLocationSync.stopSharing(session, id) }; refresh() } }
                    }, enabled = selectedMemberId != null)
                }
                Box {
                    SettingRow(Icons.Default.PersonPinCircle, "Den här telefonen", familyMembers.firstOrNull { it.id == selectedMemberId }?.name ?: "Välj familjemedlem") { memberMenu = true }
                    DropdownMenu(expanded = memberMenu, onDismissRequest = { memberMenu = false }) {
                        familyMembers.forEach { member -> DropdownMenuItem(text = { Text(member.name) }, onClick = { selectedMemberId = member.id; prefs.edit().putString("device_member_id", member.id).apply(); memberMenu = false }) }
                    }
                }
                SettingRow(Icons.Default.LocationOn, "Dela min plats", if (sharing) "Syns för familjens medlemmar" else "Avstängd") { if (sharing) scope.launch { publishNow() } }
                SettingRow(Icons.Default.Notifications, "Ankomst- och avresenotiser", "När någon kommer fram eller lämnar") { showSettings = !showSettings }
                SettingRow(Icons.Default.BatteryFull, "Batterinivå", "Visas på familjemedlemmens kort") { }
                SettingRow(Icons.Default.History, "Historik", "Senaste uppdaterade plats") { }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Platser", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = { showSettings = !showSettings }) { Icon(Icons.Default.Add, null); Text(" Lägg till plats") }
        }

        if (showSettings) {
            Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(placeName, { placeName = it }, label = { Text("Namn, t.ex. Hem eller Skola") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        val loc = readLocation()
                        if (loc == null) status = "Aktivera plats och försök igen" else scope.launch { runCatching { FamilyLocationSync.addPlace(session, placeName.trim(), loc.latitude, loc.longitude) }.onSuccess { placeName = ""; status = "Plats sparad"; refresh() }.onFailure { status = it.message ?: "Kunde inte spara plats" } }
                    }, enabled = placeName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.AddLocationAlt, null); Text(" Spara min nuvarande plats") }
                }
            }
        }

        places.forEach { place ->
            Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Color(0xFF292634)) { Icon(Icons.Default.Home, null, modifier = Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary) }
                        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(place.name, fontWeight = FontWeight.Bold); Text("Radie ${place.radiusM} m", color = Muted, fontSize = 12.sp) }
                        IconButton(onClick = { scope.launch { FamilyLocationSync.deletePlace(session, place.id); refresh() } }) { Icon(Icons.Default.MoreHoriz, null) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ankomst", modifier = Modifier.weight(1f), color = Muted); Switch(place.arrivalAlerts, { v -> scope.launch { FamilyLocationSync.updatePlaceAlerts(session, place, v, place.departureAlerts); refresh() } })
                        Spacer(Modifier.width(14.dp)); Text("Avresa", color = Muted); Switch(place.departureAlerts, { v -> scope.launch { FamilyLocationSync.updatePlaceAlerts(session, place, place.arrivalAlerts, v); refresh() } })
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text("Säkerhet & integritet", fontWeight = FontWeight.Bold) }
                Text("✓ Du bestämmer vem som ser din plats", color = Muted)
                Text("✓ Delas bara med familjen", color = Muted)
                Text("✓ Går att pausa när som helst", color = Muted)
                OutlinedButton(onClick = { sharing = false; prefs.edit().putBoolean("sharing_enabled", false).apply(); selectedMemberId?.let { id -> scope.launch { FamilyLocationSync.stopSharing(session, id); refresh() } } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Pause, null); Text(" Pausa platsdelning") }
            }
        }
        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = Color(0xFF292634)) { Icon(icon, null, modifier = Modifier.padding(9.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Muted, fontSize = 12.sp) }; Icon(Icons.Default.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun FamilyMap(locations: List<SyncFamilyLocation>, members: List<SyncMember>, modifier: Modifier = Modifier) {
    val center = locations.firstOrNull()
    val markers = locations.joinToString("\n") { loc ->
        val m = members.firstOrNull { it.id == loc.memberId }
        val color = m?.let { String.format("#%06X", 0xFFFFFF and Color(it.colorArgb).toArgb()) } ?: "#8B5CF6"
        val label = (m?.name ?: "Familj").replace("'", "\\'")
        "L.circleMarker([${loc.latitude},${loc.longitude}],{radius:13,color:'$color',weight:5,fillColor:'$color',fillOpacity:.9}).addTo(map).bindTooltip('$label',{permanent:true,direction:'top',offset:[0,-14]});"
    }
    val lat = center?.latitude ?: 55.7047
    val lon = center?.longitude ?: 13.1910
    val html = """<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/><style>html,body,#map{height:100%;margin:0;background:#11121a}.leaflet-tile{filter:brightness(.48) saturate(.7) hue-rotate(180deg)}.leaflet-control-attribution{display:none}.leaflet-tooltip{background:#17151f;color:white;border:0;border-radius:10px;font:600 12px sans-serif;box-shadow:none}</style></head><body><div id='map'></div><script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script><script>var map=L.map('map',{zoomControl:false}).setView([$lat,$lon],13);L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19}).addTo(map);$markers</script></body></html>"""
    AndroidView(factory = { ctx -> WebView(ctx).apply { webViewClient = WebViewClient(); settings.javaScriptEnabled = true; settings.domStorageEnabled = true; setBackgroundColor(android.graphics.Color.rgb(17,18,26)); loadDataWithBaseURL("https://familjekalender.local/", html, "text/html", "UTF-8", null) } }, update = { it.loadDataWithBaseURL("https://familjekalender.local/", html, "text/html", "UTF-8", null) }, modifier = modifier)
}

private fun formatUpdated(value: String): String = runCatching { "Uppdaterad ${OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("HH:mm"))}" }.getOrDefault("Senast uppdaterad")
private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double { val r=6371000.0; val p1=Math.toRadians(lat1); val p2=Math.toRadians(lat2); val dp=Math.toRadians(lat2-lat1); val dl=Math.toRadians(lon2-lon1); val a=sin(dp/2).pow(2)+cos(p1)*cos(p2)*sin(dl/2).pow(2); return r*2*atan2(sqrt(a),sqrt(1-a)) }
private fun checkPlaceTransitions(context: Context, familyId: String, locations: List<SyncFamilyLocation>, places: List<SyncFamilyPlace>, members: List<SyncMember>) {
    if (locations.isEmpty() || places.isEmpty()) return
    val prefs=context.getSharedPreferences("family_location_geofence",Context.MODE_PRIVATE); val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (android.os.Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(NotificationChannel("family_location","Familjeplats",NotificationManager.IMPORTANCE_DEFAULT))
    for(location in locations) for(place in places){ val inside=distanceMeters(location.latitude,location.longitude,place.latitude,place.longitude)<=place.radiusM; val key="${familyId}_${location.memberId}_${place.id}"; if(!prefs.contains(key)){prefs.edit().putBoolean(key,inside).apply();continue}; val before=prefs.getBoolean(key,inside); if(inside!=before){prefs.edit().putBoolean(key,inside).apply(); val notify=(inside&&place.arrivalAlerts)||(!inside&&place.departureAlerts); if(notify&&(android.os.Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)){val name=members.firstOrNull{it.id==location.memberId}?.name?:"En familjemedlem"; val text=if(inside)"$name har kommit fram till ${place.name}" else "$name har lämnat ${place.name}"; manager.notify(key.hashCode(),NotificationCompat.Builder(context,"family_location").setSmallIcon(android.R.drawable.ic_dialog_map).setContentTitle("Familjekalendern").setContentText(text).setAutoCancel(true).build())}}}
}
