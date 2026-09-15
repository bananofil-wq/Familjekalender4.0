package se.familjekalender.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Lightweight route replay used inside the running page. It draws the imported GPS stream
 * and animates a marker over the exact points in chronological order. A full map tile layer
 * can be placed behind this Canvas without changing the replay model.
 */
@Composable
fun RunRouteReplay(
    points: List<RoutePoint>,
    modifier: Modifier = Modifier
) {
    var playing by remember(points) { mutableStateOf(false) }
    var targetProgress by remember(points) { mutableFloatStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 12_000, easing = LinearEasing),
        label = "routeReplay"
    )

    LaunchedEffect(playing, points) {
        if (playing && points.size > 1) {
            targetProgress = 0f
            delay(80)
            targetProgress = 1f
            delay(12_050)
            playing = false
        }
    }

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (points.size < 2) return@Canvas
                val minLat = points.minOf { it.latitude }
                val maxLat = points.maxOf { it.latitude }
                val minLon = points.minOf { it.longitude }
                val maxLon = points.maxOf { it.longitude }
                val latSpan = (maxLat - minLat).takeIf { it > 0.000001 } ?: 0.000001
                val lonSpan = (maxLon - minLon).takeIf { it > 0.000001 } ?: 0.000001

                fun screenPoint(p: RoutePoint): Offset {
                    val x = ((p.longitude - minLon) / lonSpan).toFloat() * size.width
                    val y = size.height - ((p.latitude - minLat) / latSpan).toFloat() * size.height
                    return Offset(x, y)
                }

                val path = Path().apply {
                    val first = screenPoint(points.first())
                    moveTo(first.x, first.y)
                    points.drop(1).forEach {
                        val pos = screenPoint(it)
                        lineTo(pos.x, pos.y)
                    }
                }
                drawPath(
                    path = path,
                    color = Color(0xFF8B5CF6),
                    style = Stroke(width = 7f, cap = StrokeCap.Round)
                )

                val scaled = progress.coerceIn(0f, 1f) * (points.lastIndex)
                val index = scaled.toInt().coerceIn(0, points.lastIndex)
                val next = (index + 1).coerceAtMost(points.lastIndex)
                val fraction = scaled - index
                val a = screenPoint(points[index])
                val b = screenPoint(points[next])
                val marker = Offset(a.x + (b.x - a.x) * fraction, a.y + (b.y - a.y) * fraction)
                drawCircle(Color.White, radius = 12f, center = marker)
                drawCircle(Color(0xFF8B5CF6), radius = 8f, center = marker)
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { playing = true },
            enabled = points.size > 1 && !playing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (playing) "Visar rundan…" else "▶ Visa runda")
        }
    }
}
