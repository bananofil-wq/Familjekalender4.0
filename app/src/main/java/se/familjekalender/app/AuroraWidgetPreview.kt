package se.familjekalender.app

import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.min

/**
 * Shows the real Aurora RemoteViews layout, rather than a misleading flat mock.
 * Its TextClock children stay live while the Settings preview is visible.
 */
@Composable
internal fun AuroraWidgetPreview(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            object : FrameLayout(context) {
                val preview = LayoutInflater.from(context).inflate(
                    R.layout.widget_family_calendar_aurora, this, false
                )
                private val designWidthPx = 390f * context.resources.displayMetrics.density
                private val designHeightPx = 844f * context.resources.displayMetrics.density
                private val visiblePreviewHeightPx = 510f * context.resources.displayMetrics.density

                init {
                    clipChildren = true
                    clipToPadding = true
                    addView(preview, LayoutParams(
                        designWidthPx.toInt(), designHeightPx.toInt()
                    ))
                }

                override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                    super.onSizeChanged(w, h, oldw, oldh)
                    if (w == 0 || h == 0) return
                    val scale = min(w / designWidthPx, h / visiblePreviewHeightPx)
                    preview.pivotX = 0f
                    preview.pivotY = 0f
                    preview.scaleX = scale
                    preview.scaleY = scale
                    preview.translationX = (w - designWidthPx * scale) / 2f
                    preview.translationY = 0f
                }
            }
        },
    )
}
