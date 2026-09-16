from pathlib import Path

screen = Path('app/src/main/java/se/familjekalender/app/FamilyLocationScreen.kt')
s = screen.read_text()

s = s.replace('import org.osmdroid.tileprovider.tilesource.TileSourceFactory\n', 'import org.osmdroid.tileprovider.tilesource.TileSourceFactory\nimport org.osmdroid.tileprovider.tilesource.XYTileSource\n')
s = s.replace('private const val LOCATION_CHANNEL = "family_location_alerts"\n', '''private const val LOCATION_CHANNEL = "family_location_alerts"\n\nprivate val SATELLITE_TILES = XYTileSource(\n    "EsriWorldImagery", 0, 19, 256, ".jpg",\n    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")\n) { zoom, x, y ->\n    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$y/$x"\n}\n''')
needle = '    var editingPlace by remember { mutableStateOf<SyncFamilyPlace?>(null) }\n\n'
insert = '''    var editingPlace by remember { mutableStateOf<SyncFamilyPlace?>(null) }\n\n    DisposableEffect(session.id) {\n        prefs.edit().putBoolean("live_view_active", true).apply()\n        if (sharing && selectedMemberId != null) FamilyLocationService.start(context)\n        onDispose {\n            prefs.edit().putBoolean("live_view_active", false).apply()\n            if (sharing && selectedMemberId != null) FamilyLocationService.start(context)\n        }\n    }\n\n'''
if needle not in s: raise SystemExit('screen state anchor not found')
s = s.replace(needle, insert, 1)
s = s.replace('            delay(15_000)\n', '            delay(3_000)\n', 1)
s = s.replace('                        setTileSource(TileSourceFactory.MAPNIK)\n', '                        setTileSource(SATELLITE_TILES)\n', 1)
anchor = '            Column(\n                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),\n'
fresh = '''            selected?.let { current ->\n                Surface(\n                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),\n                    shape = RoundedCornerShape(14.dp),\n                    color = Color(0xD917151F)\n                ) {\n                    Text(\n                        "Live · ${formatUpdated(current.updatedAt)}",\n                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),\n                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold\n                    )\n                }\n            }\n\n            Column(\n                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),\n'''
if anchor not in s: raise SystemExit('map overlay anchor not found')
s = s.replace(anchor, fresh, 1)
screen.write_text(s)

service = Path('app/src/main/java/se/familjekalender/app/FamilyLocationService.kt')
t = service.read_text()
t = t.replace('        private const val MOVING_NETWORK_INTERVAL_MS = 45_000L\n', '        private const val LIVE_INTERVAL_MS = 3_000L\n        private const val LIVE_HEARTBEAT_MS = 5_000L\n        private const val LIVE_MIN_DISTANCE_M = 3f\n\n        private const val MOVING_NETWORK_INTERVAL_MS = 45_000L\n')
t = t.replace('    private enum class TrackingMode { MOVING, STILL, NEAR_PLACE }\n', '    private enum class TrackingMode { LIVE, MOVING, STILL, NEAR_PLACE }\n')
t = t.replace('        configureTracking(TrackingMode.MOVING, force = trackingMode == null)\n', '        configureTracking(if (liveViewActive()) TrackingMode.LIVE else TrackingMode.MOVING, force = true)\n')
sharing = '''    private fun sharingEnabled(): Boolean {\n        val prefs = getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE)\n        return prefs.getBoolean("sharing_enabled", false) &&\n            !prefs.getString("device_member_id", null).isNullOrBlank()\n    }\n\n'''
if sharing not in t: raise SystemExit('sharing anchor not found')
t = t.replace(sharing, sharing + '''    private fun liveViewActive(): Boolean =\n        getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE).getBoolean("live_view_active", false)\n\n''', 1)
t = t.replace('''        val requests = when (mode) {\n            TrackingMode.MOVING -> listOf(\n''', '''        val requests = when (mode) {\n            TrackingMode.LIVE -> listOf(\n                Triple(LocationManager.NETWORK_PROVIDER, LIVE_INTERVAL_MS, LIVE_MIN_DISTANCE_M),\n                Triple(LocationManager.GPS_PROVIDER, LIVE_INTERVAL_MS, LIVE_MIN_DISTANCE_M)\n            )\n            TrackingMode.MOVING -> listOf(\n''')
t = t.replace('        val desiredMode = desiredTrackingMode(location, now)\n', '        val desiredMode = if (liveViewActive()) TrackingMode.LIVE else desiredTrackingMode(location, now)\n')
t = t.replace('''        val movementThreshold = if (location.hasAccuracy()) {\n            maxOf(MIN_PUBLISH_DISTANCE_M, (location.accuracy * 0.75f).coerceAtMost(80f))\n        } else {\n            MIN_PUBLISH_DISTANCE_M\n        }\n''', '''        val movementThreshold = if (desiredMode == TrackingMode.LIVE) LIVE_MIN_DISTANCE_M else if (location.hasAccuracy()) {\n            maxOf(MIN_PUBLISH_DISTANCE_M, (location.accuracy * 0.75f).coerceAtMost(80f))\n        } else MIN_PUBLISH_DISTANCE_M\n''', 1)
t = t.replace('''    private fun heartbeatInterval(mode: TrackingMode): Long = when (mode) {\n        TrackingMode.MOVING -> MOVING_HEARTBEAT_MS\n''', '''    private fun heartbeatInterval(mode: TrackingMode): Long = when (mode) {\n        TrackingMode.LIVE -> LIVE_HEARTBEAT_MS\n        TrackingMode.MOVING -> MOVING_HEARTBEAT_MS\n''')
service.write_text(t)
print('Applied live satellite location mode')
