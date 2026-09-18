package se.familjekalender.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun RunReplayMap(run: RecordedRun, modifier: Modifier = Modifier) {
    val points = run.points
    var index by remember(run.id) { mutableIntStateOf(0) }
    var playing by remember(run.id) { mutableStateOf(false) }
    var followRunner by remember(run.id) { mutableStateOf(true) }

    LaunchedEffect(playing, run.id) {
        while (playing && index < points.lastIndex) {
            val current = points[index]
            val next = points[index + 1]
            val realGap = (next.timestampMillis - current.timestampMillis).coerceIn(100L, 5000L)
            delay((realGap / 5L).coerceIn(40L, 700L))
            index++
        }
        if (index >= points.lastIndex) playing = false
    }

    Column(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(320.dp),
            factory = { context -> MapView(context).apply { setMultiTouchControls(true) } },
            update = { map ->
                map.overlays.clear()
                if (points.isNotEmpty()) {
                    val geo = points.map { GeoPoint(it.latitude, it.longitude) }
                    map.overlays.add(
                        Polyline().apply {
                            setPoints(geo)
                            outlinePaint.color = android.graphics.Color.rgb(180, 124, 255)
                            outlinePaint.strokeWidth = 10f
                        }
                    )

                    map.overlays.add(
                        Marker(map).apply {
                            position = geo.first()
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "Start"
                        }
                    )
                    if (geo.size > 1) {
                        map.overlays.add(
                            Marker(map).apply {
                                position = geo.last()
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = "Mål"
                            }
                        )
                    }

                    val markerPoint = geo[index.coerceIn(0, geo.lastIndex)]
                    map.overlays.add(
                        Marker(map).apply {
                            position = markerPoint
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            title = "Löpare"
                        }
                    )

                    if (playing && followRunner) {
                        if (map.zoomLevelDouble < 16.0) map.controller.setZoom(16.5)
                        map.controller.animateTo(markerPoint)
                    } else if (geo.size > 1) {
                        val north = geo.maxOf { it.latitude }
                        val south = geo.minOf { it.latitude }
                        val east = geo.maxOf { it.longitude }
                        val west = geo.minOf { it.longitude }
                        runCatching {
                            map.zoomToBoundingBox(BoundingBox(north, east, south, west), true, 64)
                        }
                    } else {
                        map.controller.setZoom(17.0)
                        map.controller.setCenter(markerPoint)
                    }
                }
                map.invalidate()
            },
        )
        if (points.size > 1) {
            Slider(
                value = index.toFloat(),
                onValueChange = {
                    index = it.toInt()
                    playing = false
                },
                valueRange = 0f..points.lastIndex.toFloat(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        index = 0
                        playing = false
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Från start")
                }
                Button(
                    onClick = {
                        if (index >= points.lastIndex) index = 0
                        playing = !playing
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (playing) "Pausa" else "▶ Spela upp rundan")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FilterChip(
                    selected = followRunner,
                    onClick = { followRunner = !followRunner },
                    label = { Text("Följ löparen") },
                )
            }
        }
    }
}
