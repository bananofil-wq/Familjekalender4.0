from pathlib import Path

p = Path('app/src/main/java/se/familjekalender/app/FamilyLocationScreen.kt')
s = p.read_text()

s = s.replace(
'''    var selectedMemberId by remember { mutableStateOf(prefs.getString("device_member_id", null)) }
    var selectedMapMember by remember { mutableStateOf<String?>(null) }
''',
'''    var selectedMemberId by remember { mutableStateOf(prefs.getString("device_member_id", null)) }
    var selectedMapMember by remember { mutableStateOf<String?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var useTopoMap by remember { mutableStateOf(false) }
''')

s = s.replace(
'''                FamilyMap(
                    locations = locations,
                    members = familyMembers,
                    selectedMemberId = selectedMapMember,
                    modifier = Modifier.fillMaxSize()
                )
''',
'''                FamilyMap(
                    locations = locations,
                    members = familyMembers,
                    selectedMemberId = selectedMapMember,
                    onMapReady = { mapView = it },
                    modifier = Modifier.fillMaxSize()
                )
''')

s = s.replace(
'''                    MapCircleButton(Icons.Default.MyLocation) {
                        selectedMapMember = selectedMemberId
                    }
                    MapCircleButton(Icons.Default.Layers) { }
''',
'''                    MapCircleButton(Icons.Default.MyLocation) {
                        selectedMapMember = selectedMemberId
                        val own = locations.firstOrNull { it.memberId == selectedMemberId }
                            ?: locations.firstOrNull { it.memberId == selectedMapMember }
                            ?: locations.firstOrNull()
                        own?.let {
                            mapView?.controller?.setZoom(17.0)
                            mapView?.controller?.animateTo(GeoPoint(it.latitude, it.longitude))
                        }
                    }
                    MapCircleButton(Icons.Default.Layers) {
                        useTopoMap = !useTopoMap
                        mapView?.setTileSource(if (useTopoMap) TileSourceFactory.USGS_TOPO else TileSourceFactory.MAPNIK)
                        mapView?.invalidate()
                    }
                    MapCircleButton(Icons.Default.Add) {
                        mapView?.controller?.zoomIn()
                    }
                    MapCircleButton(Icons.Default.Remove) {
                        mapView?.controller?.zoomOut()
                    }
''')

s = s.replace(
'''private fun FamilyMap(
    locations: List<SyncFamilyLocation>,
    members: List<SyncMember>,
    selectedMemberId: String?,
    modifier: Modifier = Modifier
) {
''',
'''private fun FamilyMap(
    locations: List<SyncFamilyLocation>,
    members: List<SyncMember>,
    selectedMemberId: String?,
    onMapReady: (MapView) -> Unit,
    modifier: Modifier = Modifier
) {
''')

s = s.replace(
'''            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                minZoomLevel = 3.0
                maxZoomLevel = 20.0
                controller.setZoom(14.0)
                controller.setCenter(GeoPoint(lat, lon))
            }
''',
'''            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setUseDataConnection(true)
                setMultiTouchControls(true)
                setBuiltInZoomControls(false)
                isTilesScaledToDpi = true
                minZoomLevel = 3.0
                maxZoomLevel = 20.0
                controller.setZoom(17.0)
                controller.setCenter(GeoPoint(lat, lon))
            }.also(onMapReady)
''')

p.write_text(s)
