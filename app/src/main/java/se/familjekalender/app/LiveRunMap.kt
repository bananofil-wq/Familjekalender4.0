package se.familjekalender.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun LiveRunMap(points: List<RecordedRoutePoint>, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                setMultiTouchControls(true)
                controller.setZoom(17.0)
            }
        },
        update = { map ->
            map.overlays.clear()
            if (points.isNotEmpty()) {
                val geo = points.map { GeoPoint(it.latitude, it.longitude) }
                if (geo.size > 1) {
                    map.overlays.add(
                        Polyline().apply {
                            setPoints(geo)
                            outlinePaint.color = android.graphics.Color.rgb(180, 124, 255)
                            outlinePaint.strokeWidth = 10f
                        }
                    )
                }
                val current = geo.last()
                map.overlays.add(
                    Marker(map).apply {
                        position = current
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "Nu"
                    }
                )
                map.controller.setCenter(current)
            }
            map.invalidate()
        },
    )
}
