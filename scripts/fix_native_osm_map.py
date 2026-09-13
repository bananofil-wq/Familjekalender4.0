from pathlib import Path

screen = Path('app/src/main/java/se/familjekalender/app/FamilyLocationScreen.kt')
gradle = Path('app/build.gradle.kts')

text = screen.read_text(encoding='utf-8')
text = text.replace('import android.webkit.WebView\nimport android.webkit.WebViewClient\n', '')
anchor = 'import androidx.core.content.ContextCompat\n'
imports = '''import androidx.core.content.ContextCompat\nimport org.osmdroid.config.Configuration\nimport org.osmdroid.tileprovider.tilesource.TileSourceFactory\nimport org.osmdroid.util.GeoPoint\nimport org.osmdroid.views.MapView\nimport org.osmdroid.views.overlay.Marker\n'''
if 'org.osmdroid.views.MapView' not in text:
    text = text.replace(anchor, imports)

old = '''@Composable
private fun FamilyMap(
    locations: List<SyncFamilyLocation>,
    members: List<SyncMember>,
    selectedMemberId: String?,
    modifier: Modifier = Modifier
) {
    val center = locations.firstOrNull { it.memberId == selectedMemberId } ?: locations.firstOrNull()
    val lat = center?.latitude ?: 55.7047
    val lon = center?.longitude ?: 13.1910
    val delta = 0.035
    val bbox = "${lon - delta},${lat - delta},${lon + delta},${lat + delta}"
    val marker = if (center != null) "&marker=$lat,$lon" else ""
    val mapUrl = "https://www.openstreetmap.org/export/embed.html?bbox=${Uri.encode(bbox, ",")}&layer=mapnik$marker"

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                setBackgroundColor(android.graphics.Color.rgb(13, 17, 28))
                loadUrl(mapUrl)
            }
        },
        update = { webView ->
            if (webView.url != mapUrl) webView.loadUrl(mapUrl)
        },
        modifier = modifier
    )
}
'''
new = '''@Composable
private fun FamilyMap(
    locations: List<SyncFamilyLocation>,
    members: List<SyncMember>,
    selectedMemberId: String?,
    modifier: Modifier = Modifier
) {
    val center = locations.firstOrNull { it.memberId == selectedMemberId } ?: locations.firstOrNull()
    val lat = center?.latitude ?: 55.7047
    val lon = center?.longitude ?: 13.1910

    AndroidView(
        factory = { ctx ->
            Configuration.getInstance().userAgentValue = ctx.packageName
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                minZoomLevel = 3.0
                maxZoomLevel = 20.0
                controller.setZoom(14.0)
                controller.setCenter(GeoPoint(lat, lon))
            }
        },
        update = { map ->
            map.overlays.clear()
            locations.forEach { loc ->
                val member = members.firstOrNull { it.id == loc.memberId }
                Marker(map).apply {
                    position = GeoPoint(loc.latitude, loc.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = member?.name ?: "Familjemedlem"
                    snippet = "Senast uppdaterad: ${formatUpdated(loc.updatedAt).removePrefix("Uppdaterad ")}"
                    map.overlays.add(this)
                }
            }
            val current = locations.firstOrNull { it.memberId == selectedMemberId } ?: locations.firstOrNull()
            if (current != null) {
                map.controller.setCenter(GeoPoint(current.latitude, current.longitude))
            } else {
                map.controller.setCenter(GeoPoint(lat, lon))
            }
            map.invalidate()
        },
        modifier = modifier
    )
}
'''
if old not in text:
    raise SystemExit('Expected FamilyMap block not found; refusing unsafe patch')
text = text.replace(old, new)
screen.write_text(text, encoding='utf-8')

g = gradle.read_text(encoding='utf-8')
dep = '    implementation("org.osmdroid:osmdroid-android:6.1.20")\n'
if dep not in g:
    marker = '    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")\n'
    if marker not in g:
        raise SystemExit('Dependency insertion point not found')
    g = g.replace(marker, marker + dep)
gradle.write_text(g, encoding='utf-8')
