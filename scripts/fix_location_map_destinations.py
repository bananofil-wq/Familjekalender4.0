from pathlib import Path
import re

p = Path('app/src/main/java/se/familjekalender/app/FamilyLocationScreen.kt')
s = p.read_text()

s = s.replace('import android.location.Location\n', 'import android.location.Location\nimport android.location.Geocoder\n')
s = s.replace('import kotlinx.coroutines.delay\n', 'import kotlinx.coroutines.delay\nimport kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.withContext\n')
s = s.replace('import java.time.format.DateTimeFormatter\n', 'import java.time.format.DateTimeFormatter\nimport java.util.Locale\n')

s = s.replace('    var placeName by remember { mutableStateOf("") }\n', '    var placeName by remember { mutableStateOf("") }\n    var placeAddress by remember { mutableStateOf("") }\n')

needle = '''    fun batteryPercent(): Int? =\n        (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)\n            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)\n            .takeIf { it in 0..100 }\n'''
insert = needle + '''\n    suspend fun geocodeAddress(query: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {\n        runCatching {\n            @Suppress("DEPRECATION")\n            val result = Geocoder(context, Locale("sv", "SE")).getFromLocationName(query, 1)\n            result?.firstOrNull()?.let { it.latitude to it.longitude }\n        }.getOrNull()\n    }\n'''
if needle not in s:
    raise SystemExit('batteryPercent block not found')
s = s.replace(needle, insert, 1)

s = s.replace('Text("Platser", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))', 'Text("Destinationer", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))')
s = s.replace('Text(" Lägg till plats")', 'Text(" Lägg till destination")')
s = s.replace('label = { Text("Namn, t.ex. Hem eller Skola") }', 'label = { Text("Namn, t.ex. Hem eller Skola") }')

name_field = '''                    OutlinedTextField(\n                        value = placeName,\n                        onValueChange = { placeName = it },\n                        label = { Text("Namn, t.ex. Hem eller Skola") },\n                        modifier = Modifier.fillMaxWidth()\n                    )\n'''
address_field = name_field + '''                    OutlinedTextField(\n                        value = placeAddress,\n                        onValueChange = { placeAddress = it },\n                        label = { Text("Adress, ort eller destination") },\n                        placeholder = { Text("Exempel: Skolgatan 1, Lund") },\n                        singleLine = true,\n                        modifier = Modifier.fillMaxWidth()\n                    )\n'''
if name_field not in s:
    raise SystemExit('placeName field not found')
s = s.replace(name_field, address_field, 1)

button_pattern = re.compile(r'''                    Button\(\n                        onClick = \{\n                            val loc = readLocation\(\)\n                            if \(loc == null\) \{\n                                status = "Aktivera plats och försök igen"\n                            \} else \{\n                                scope.launch \{\n                                    runCatching \{\n                                        FamilyLocationSync.addPlace\(\n                                            session,\n                                            placeName.trim\(\),\n                                            loc.latitude,\n                                            loc.longitude\n                                        \)\n                                    \}.onSuccess \{\n                                        placeName = ""\n                                        showAddPlace = false\n                                        refresh\(\)\n                                    \}.onFailure \{\n                                        status = it.message \?: "Kunde inte spara plats"\n                                    \}\n                                \}\n                            \}\n                        \},\n                        enabled = placeName.isNotBlank\(\),\n                        modifier = Modifier.fillMaxWidth\(\)\n                    \) \{\n                        Icon\(Icons.Default.AddLocationAlt, contentDescription = null\)\n                        Text\(" Spara min nuvarande plats"\)\n                    \}''')
replacement = '''                    Button(\n                        onClick = {\n                            scope.launch {\n                                val coords = geocodeAddress(placeAddress.trim())\n                                if (coords == null) {\n                                    status = "Kunde inte hitta destinationen. Kontrollera adressen."\n                                } else {\n                                    runCatching {\n                                        FamilyLocationSync.addPlace(\n                                            session,\n                                            placeName.trim(),\n                                            coords.first,\n                                            coords.second\n                                        )\n                                    }.onSuccess {\n                                        placeName = ""\n                                        placeAddress = ""\n                                        showAddPlace = false\n                                        status = "Destination sparad"\n                                        refresh()\n                                    }.onFailure {\n                                        status = it.message ?: "Kunde inte spara destination"\n                                    }\n                                }\n                            }\n                        },\n                        enabled = placeName.isNotBlank() && placeAddress.isNotBlank(),\n                        modifier = Modifier.fillMaxWidth()\n                    ) {\n                        Icon(Icons.Default.AddLocationAlt, contentDescription = null)\n                        Text(" Sök adress och spara destination")\n                    }'''
s, count = button_pattern.subn(replacement, s, count=1)
if count != 1:
    raise SystemExit(f'add place button block not replaced: {count}')

map_pattern = re.compile(r'''@Composable\nprivate fun FamilyMap\(.*?\n\}\n\nprivate fun formatUpdated''', re.S)
new_map = '''@Composable\nprivate fun FamilyMap(\n    locations: List<SyncFamilyLocation>,\n    members: List<SyncMember>,\n    selectedMemberId: String?,\n    modifier: Modifier = Modifier\n) {\n    val center = locations.firstOrNull { it.memberId == selectedMemberId } ?: locations.firstOrNull()\n    val lat = center?.latitude ?: 55.7047\n    val lon = center?.longitude ?: 13.1910\n    val delta = 0.035\n    val bbox = "${lon - delta},${lat - delta},${lon + delta},${lat + delta}"\n    val marker = if (center != null) "&marker=$lat,$lon" else ""\n    val mapUrl = "https://www.openstreetmap.org/export/embed.html?bbox=${Uri.encode(bbox, ",")}&layer=mapnik$marker"\n\n    AndroidView(\n        factory = { ctx ->\n            WebView(ctx).apply {\n                webViewClient = WebViewClient()\n                settings.javaScriptEnabled = true\n                settings.domStorageEnabled = true\n                settings.loadsImagesAutomatically = true\n                setBackgroundColor(android.graphics.Color.rgb(13, 17, 28))\n                loadUrl(mapUrl)\n            }\n        },\n        update = { webView ->\n            if (webView.url != mapUrl) webView.loadUrl(mapUrl)\n        },\n        modifier = modifier\n    )\n}\n\nprivate fun formatUpdated'''
s, count = map_pattern.subn(new_map, s, count=1)
if count != 1:
    raise SystemExit(f'FamilyMap block not replaced: {count}')

p.write_text(s)
print('Patched FamilyLocationScreen.kt')
