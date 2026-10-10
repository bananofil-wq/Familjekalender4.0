package se.familjekalender.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Real photographed surfaces sampled directly from the user's approved oak reference.
 * The single image atlas ships in drawable-nodpi; no network, repeated screenshot UI,
 * gradients pretending to be grain, or brittle inline base64 texture.
 */
internal enum class OakPhotoMaterial(val atlasRow: Int) {
    TOP(0), PALE(1), DARK(2), AMBER(3)
}

@Composable
private fun rememberOakMaterial(material: OakPhotoMaterial): Bitmap? {
    val context = LocalContext.current
    val atlas = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.oak_2026_atlas)
    }
    return remember(atlas, material) {
        if (atlas == null || atlas.width < 240 || atlas.height < 160) null
        else Bitmap.createBitmap(atlas, 0, material.atlasRow * 40, 240, 40)
    }
}

@Composable
internal fun OakPhotographicBackground(modifier: Modifier = Modifier) {
    val dark = rememberOakMaterial(OakPhotoMaterial.DARK)
    val light = rememberOakMaterial(OakPhotoMaterial.TOP)
    Canvas(modifier) {
        drawIntoCanvas { canvas ->
            drawOakPhoto(canvas.nativeCanvas, dark, size.width, size.height, 1f)
            val headerHeight = 82.dp.toPx().coerceAtMost(size.height)
            canvas.nativeCanvas.save()
            canvas.nativeCanvas.clipRect(0f, 0f, size.width, headerHeight)
            drawOakPhoto(canvas.nativeCanvas, light, size.width, headerHeight, 1f)
            canvas.nativeCanvas.restore()
        }
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0x16FFE2B6),
                    Color(0x3A683B20),
                    Color(0x502A160C),
                    Color(0x88220F06),
                )
            )
        )
    }
}

@Composable
internal fun OakPhotographicSurface(
    modifier: Modifier = Modifier,
    opacity: Float = .20f,
    material: OakPhotoMaterial = OakPhotoMaterial.DARK,
) {
    val sample = rememberOakMaterial(material)
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
    val scale = width / sample.width.toFloat()
    shader.setLocalMatrix(
        Matrix().apply { setScale(scale, scale * 1.15f) }
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        this.shader = shader
        alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
    }
    canvas.drawRect(0f, 0f, width, height, paint)
}

/** Original corner foliage and sunset shoreline taken from the approved reference. */
private enum class OakDecor(val top: Int, val width: Int, val height: Int) {
    LEAVES(0, 80, 170), SUNSET(175, 240, 78)
}

@Composable
private fun rememberOakDecor(which: OakDecor): Bitmap? {
    val context = LocalContext.current
    val atlas = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.oak_2026_decor)
    }
    return remember(atlas, which) {
        if (atlas == null || atlas.width < 240 || atlas.height < 253) null
        else Bitmap.createBitmap(atlas, 0, which.top, which.width, which.height)
    }
}

@Composable
internal fun OakLeafDecoration(modifier: Modifier = Modifier) {
    val bitmap = rememberOakDecor(OakDecor.LEAVES)
    if (bitmap != null) Image(
        bitmap = bitmap.asImageBitmap(), contentDescription = null,
        modifier = modifier, contentScale = ContentScale.FillBounds,
    )
}

@Composable
internal fun OakSunsetDecoration(modifier: Modifier = Modifier) {
    val bitmap = rememberOakDecor(OakDecor.SUNSET)
    if (bitmap != null) Image(
        bitmap = bitmap.asImageBitmap(), contentDescription = null,
        modifier = modifier, contentScale = ContentScale.Crop,
    )
}
