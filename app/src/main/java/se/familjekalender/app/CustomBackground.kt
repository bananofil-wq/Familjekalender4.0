package se.familjekalender.app

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.io.File

private const val CUSTOM_BACKGROUND_FILE = "familjekalender_custom_background"
private const val CUSTOM_BACKGROUND_PREF = "custom_background_enabled"

private fun customBackgroundFile(context: Context): File =
    File(context.filesDir, CUSTOM_BACKGROUND_FILE)

internal fun hasCustomBackground(context: Context): Boolean {
    val enabled =
        context.getSharedPreferences("family_calendar", 0)
            .getBoolean(CUSTOM_BACKGROUND_PREF, false)
    return enabled && customBackgroundFile(context).exists()
}

internal fun saveCustomBackground(context: Context, uri: Uri) {
    val target = customBackgroundFile(context)
    val temp = File(context.filesDir, "$CUSTOM_BACKGROUND_FILE.tmp")
    context.contentResolver.openInputStream(uri)?.use { input ->
        temp.outputStream().use { output -> input.copyTo(output) }
    } ?: error("Kunde inte läsa den valda bilden.")

    val probe = BitmapFactory.decodeFile(temp.absolutePath)
        ?: run {
            temp.delete()
            error("Filen verkar inte vara en giltig bild.")
        }
    probe.recycle()

    if (target.exists()) target.delete()
    if (!temp.renameTo(target)) {
        temp.copyTo(target, overwrite = true)
        temp.delete()
    }
    context.getSharedPreferences("family_calendar", 0)
        .edit()
        .putBoolean(CUSTOM_BACKGROUND_PREF, true)
        .apply()
}

internal fun removeCustomBackground(context: Context) {
    customBackgroundFile(context).delete()
    context.getSharedPreferences("family_calendar", 0)
        .edit()
        .putBoolean(CUSTOM_BACKGROUND_PREF, false)
        .apply()
}

private fun decodeCustomBackground(context: Context): ImageBitmap? {
    if (!hasCustomBackground(context)) return null
    val file = customBackgroundFile(context)

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / sample > 2160 || bounds.outHeight / sample > 2160) {
        sample *= 2
    }

    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
}

@Composable
internal fun rememberCustomBackgroundBitmap(): ImageBitmap? {
    val context = LocalContext.current
    val file = customBackgroundFile(context)
    val enabled = hasCustomBackground(context)
    val stamp = if (enabled && file.exists()) file.lastModified() else 0L
    return remember(enabled, stamp) { decodeCustomBackground(context) }
}

@Composable
internal fun CustomBackgroundImage(
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bitmap = rememberCustomBackgroundBitmap() ?: return
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
    )
}
