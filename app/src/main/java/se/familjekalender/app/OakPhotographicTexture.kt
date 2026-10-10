package se.familjekalender.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

/**
 * A real photographed wood-grain sample taken from the user's selected concept
 * image, NOT a grid of simulated boards. Repeats with a mirrored tile to avoid seams.
 * Kept inline so the theme works offline and without another image download.
 */
private const val OAK_PHOTO_SAMPLE = "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAUEBAQEAwUEBAQGBQUGCA0ICAcHCBALDAkNExAUExIQEhIUFx0ZFBYcFhISGiMaHB4fISEhFBkkJyQgJh0gISD/2wBDAQUGBggHCA8ICA8gFRIVICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICD/wAARCAAjAHYDAREAAhEBAxEB/8QAGQAAAwEBAQAAAAAAAAAAAAAAAQIDAAQG/8QANBAAAgECAwcBBQcFAAAAAAAAAQIRAAMSITEEEyJRYXGBQSMyM2KRFDRygoPB4UJSobHR/8QAFwEBAQEBAAAAAAAAAAAAAAAAAAEGAv/EABYRAQEBAAAAAAAAAAAAAAAAAAARAf/aAAwDAQACEQMRAD8A9ANlUiBbI7MDWKjXUzbHbI0dewFSLmmTYkjhNweBNItWFhQuHARHyUiUTauQDDR0UA0gU2cRj2k96QYbOFnO55g/7pBjZu6jEBzwikKY2Xw/GM9UmkSgtk5zfYnlgpFqotKBxMT4ipApW3MFWY9SasGFtJytLHWaB92gP3e3HODQo4cstnBHagHARkAJ10rpGYW1OiwOZqBlCF+EKORAmgo+8A4SwnkuVFZt5HuYupXKiAIAJa2kjP3TQANZJMoo7elApuITAUEco/igUvhGaII6/wDaDY50zPLI0DBiBmrj8IoqyXUORe6vRk1qBi9sghr6g/MIn/FApUFSZTwwg0RtwQMyPDUHGWskw27GWsa1VNbNkCLbIfy0FItkxvArDktEONcIzn1HDQPggzDD9QUBYroYnnINEEqwHCCO9AAXA9R1iilY3lMDMczVBVXYzKx4qCkEf0gnnhMUBxSPgr3BIoELEyN2Okkn9qBN2BPsxPb+Kitu0iGT6VAirsz5qLn5YFUV3Gz6h3X8TgUS6DWlJhSx/UopfsZbWyrDqZ/ekKU7M6E+xtgehAmkKbdXoENA/tjKqM1u6CCN3PVWqIUteWVK2zPrBqqGK+rZRERGGgJW8ZOIjoMqgIF6MJLnqKKYpdK5m6CPmoiZF0HiVz5mgDLdJlbbzzg0Clb4EjZ709IFQVS7caAzkiqOlUQgyinuKIc7Ls7xitDTtVCtsmzoMS24PQmkS6jcGEDCSPNFILtwNGM/WgrjcqSTJqCTXHxROXaqKozNALGKg6cC4ND9aokwCzAqKlJNyDpQGBi0oKADCaqFLGNfWoP/2Q=="

@Composable
internal fun OakPhotographicBackground(modifier: Modifier = Modifier) {
    val sample = remember {
        val bytes = Base64.decode(OAK_PHOTO_SAMPLE, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    Canvas(modifier) {
        drawIntoCanvas { canvas ->
            drawOakPhoto(
                canvas.nativeCanvas, sample,
                width = size.width, height = size.height, opacity = 1f,
            )
        }
        // The reference photo darkens towards the bottom; keep the real grain visible.
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0x19FFE4B9),
                    Color(0x08CF9A69),
                    Color(0x188B532A),
                    Color(0x622D170B),
                )
            )
        )
    }
}

@Composable
internal fun OakPhotographicSurface(
    modifier: Modifier = Modifier,
    opacity: Float = .20f,
) {
    val sample = remember {
        val bytes = Base64.decode(OAK_PHOTO_SAMPLE, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    Canvas(modifier) {
        drawIntoCanvas { canvas ->
            drawOakPhoto(
                canvas.nativeCanvas, sample,
                width = size.width, height = size.height,
                opacity = opacity.coerceIn(0f, 1f),
            )
        }
    }
}

private fun drawOakPhoto(
    canvas: android.graphics.Canvas,
    sample: Bitmap?,
    width: Float,
    height: Float,
    opacity: Float,
) {
    if (sample == null || width <= 0f || height <= 0f) return

    val shader = BitmapShader(sample, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
    val matrix = Matrix().apply {
        // One broad-grained sweep across the screen, repeated softly down the page.
        val xScale = width / sample.width.coerceAtLeast(1).toFloat()
        setScale(xScale, xScale * .60f)
    }
    shader.setLocalMatrix(matrix)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        this.shader = shader
        alpha = (opacity * 255).toInt().coerceIn(0, 255)
    }
    canvas.drawRect(0f, 0f, width, height, paint)
}
