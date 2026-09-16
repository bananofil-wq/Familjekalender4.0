from pathlib import Path
import re

screen = Path('app/src/main/java/se/familjekalender/app/FamilyLocationScreen.kt')
s = screen.read_text()

# Normalize tile-source imports. Older patch revisions could add XYTileSource more than once.
s = re.sub(r'import org\.osmdroid\.tileprovider\.tilesource\.XYTileSource\n', '', s)
if 'import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase\n' not in s:
    s = s.replace(
        'import org.osmdroid.config.Configuration\n',
        'import org.osmdroid.config.Configuration\nimport org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase\n'
    )
if 'import org.osmdroid.util.MapTileIndex\n' not in s:
    s = s.replace(
        'import org.osmdroid.util.GeoPoint\n',
        'import org.osmdroid.util.MapTileIndex\nimport org.osmdroid.util.GeoPoint\n'
    )

# Replace every previous satellite definition with one Esri ArcGIS tile source.
# ArcGIS uses z/y/x ordering, so a custom OnlineTileSourceBase is required.
header_pattern = re.compile(
    r'private const val LOCATION_CHANNEL = "family_location_alerts"\n.*?\n@Composable\nfun FamilyLocationScreen',
    re.S,
)
header_replacement = '''private const val LOCATION_CHANNEL = "family_location_alerts"

private val SATELLITE_TILES = object : OnlineTileSourceBase(
    "EsriWorldImagery",
    0,
    19,
    256,
    "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "Tiles © Esri"
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        getBaseUrl() + MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
}

@Composable
fun FamilyLocationScreen'''
s, replaced = header_pattern.subn(header_replacement, s, count=1)
if replaced != 1:
    raise SystemExit('location header anchor not found')

# Keep exactly one live-view DisposableEffect after the screen state declarations.
state_pattern = re.compile(
    r'    var editingPlace by remember \{ mutableStateOf<SyncFamilyPlace\?>\(null\) \}\n\n.*?    fun hasLocationPermission\(\): Boolean =',
    re.S,
)
state_replacement = '''    var editingPlace by remember { mutableStateOf<SyncFamilyPlace?>(null) }

    // Use denser GPS updates only while the Plats view is actually open.
    DisposableEffect(session.id, sharing, selectedMemberId) {
        prefs.edit().putBoolean("live_view_active", true).apply()
        if (sharing && selectedMemberId != null) FamilyLocationService.start(context)
        onDispose {
            prefs.edit().putBoolean("live_view_active", false).apply()
            if (sharing && selectedMemberId != null) FamilyLocationService.start(context)
        }
    }

    fun hasLocationPermission(): Boolean ='''
s, replaced = state_pattern.subn(state_replacement, s, count=1)
if replaced != 1:
    raise SystemExit('screen state anchor not found')

s = s.replace('            delay(15_000)\n', '            delay(3_000)\n')
s = s.replace('                        setTileSource(TileSourceFactory.MAPNIK)\n', '                        setTileSource(SATELLITE_TILES)\n')
s = s.replace('                        setMaxZoomLevel(20.0)\n', '                        setMaxZoomLevel(19.0)\n')

# Collapse duplicate "Live" overlays created by older non-idempotent runs.
overlay_pattern = re.compile(
    r'(            \)\n\n)(?:            selected\?\.let \{ current ->.*?\n            \}\n\n)+(?=            Column\(\n                modifier = Modifier\.align\(Alignment\.TopEnd\)\.padding\(12\.dp\),)',
    re.S,
)
overlay_replacement = '''\\1            selected?.let { current ->
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xD917151F)
                ) {
                    Text(
                        "Live · ${formatUpdated(current.updatedAt)}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

'''
s, replaced = overlay_pattern.subn(overlay_replacement, s, count=1)
if replaced != 1:
    raise SystemExit('map overlay anchor not found')

screen.write_text(s)

service = Path('app/src/main/java/se/familjekalender/app/FamilyLocationService.kt')
t = service.read_text()

if 'private const val LIVE_INTERVAL_MS' not in t:
    t = t.replace(
        '        private const val MOVING_NETWORK_INTERVAL_MS = 45_000L\n',
        '        private const val LIVE_INTERVAL_MS = 3_000L\n'
        '        private const val LIVE_HEARTBEAT_MS = 5_000L\n'
        '        private const val LIVE_MIN_DISTANCE_M = 3f\n\n'
        '        private const val MOVING_NETWORK_INTERVAL_MS = 45_000L\n',
        1,
    )

if 'private enum class TrackingMode { LIVE,' not in t:
    t = t.replace(
        '    private enum class TrackingMode { MOVING, STILL, NEAR_PLACE }\n',
        '    private enum class TrackingMode { LIVE, MOVING, STILL, NEAR_PLACE }\n',
        1,
    )

t = t.replace(
    '        configureTracking(TrackingMode.MOVING, force = trackingMode == null)\n',
    '        configureTracking(if (liveViewActive()) TrackingMode.LIVE else TrackingMode.MOVING, force = true)\n',
    1,
)

if 'private fun liveViewActive()' not in t:
    sharing = '''    private fun sharingEnabled(): Boolean {
        val prefs = getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean("sharing_enabled", false) &&
            !prefs.getString("device_member_id", null).isNullOrBlank()
    }

'''
    if sharing not in t:
        raise SystemExit('sharing anchor not found')
    t = t.replace(
        sharing,
        sharing + '''    private fun liveViewActive(): Boolean =
        getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE)
            .getBoolean("live_view_active", false)

''',
        1,
    )

if 'TrackingMode.LIVE -> listOf(' not in t:
    t = t.replace(
        '''        val requests = when (mode) {
            TrackingMode.MOVING -> listOf(
''',
        '''        val requests = when (mode) {
            TrackingMode.LIVE -> listOf(
                Triple(LocationManager.NETWORK_PROVIDER, LIVE_INTERVAL_MS, LIVE_MIN_DISTANCE_M),
                Triple(LocationManager.GPS_PROVIDER, LIVE_INTERVAL_MS, LIVE_MIN_DISTANCE_M)
            )
            TrackingMode.MOVING -> listOf(
''',
        1,
    )

t = t.replace(
    '        val desiredMode = desiredTrackingMode(location, now)\n',
    '        val desiredMode = if (liveViewActive()) TrackingMode.LIVE else desiredTrackingMode(location, now)\n',
    1,
)

if 'if (desiredMode == TrackingMode.LIVE)' not in t:
    t = t.replace(
        '''        val movementThreshold = if (location.hasAccuracy()) {
            maxOf(MIN_PUBLISH_DISTANCE_M, (location.accuracy * 0.75f).coerceAtMost(80f))
        } else {
            MIN_PUBLISH_DISTANCE_M
        }
''',
        '''        val movementThreshold = if (desiredMode == TrackingMode.LIVE) {
            LIVE_MIN_DISTANCE_M
        } else if (location.hasAccuracy()) {
            maxOf(MIN_PUBLISH_DISTANCE_M, (location.accuracy * 0.75f).coerceAtMost(80f))
        } else {
            MIN_PUBLISH_DISTANCE_M
        }
''',
        1,
    )

if 'TrackingMode.LIVE -> LIVE_HEARTBEAT_MS' not in t:
    t = t.replace(
        '''    private fun heartbeatInterval(mode: TrackingMode): Long = when (mode) {
        TrackingMode.MOVING -> MOVING_HEARTBEAT_MS
''',
        '''    private fun heartbeatInterval(mode: TrackingMode): Long = when (mode) {
        TrackingMode.LIVE -> LIVE_HEARTBEAT_MS
        TrackingMode.MOVING -> MOVING_HEARTBEAT_MS
''',
        1,
    )

service.write_text(t)
print('Applied idempotent live satellite location mode')
