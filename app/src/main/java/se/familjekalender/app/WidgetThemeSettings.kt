package se.familjekalender.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The user explicitly chose to keep only the protected, original "Nuvarande"
 * home-screen widget. No theme picker or other widget designs are exposed here.
 * Calendar themes are managed elsewhere and are not modified.
 */
@Composable
internal fun WidgetThemeSettings(calendarTheme: CleanVisualTheme) {
    val context = LocalContext.current
    var detailsExpanded by remember { mutableStateOf(false) }
    var refreshStatus by remember { mutableStateOf<String?>(null) }

    fun restoreCurrentWidget() {
        WidgetThemeColors.saveChoice(context, WidgetThemeColors.ORIGINAL)
        refreshStatus = try {
            val count = FamilyCalendarWidget.refreshTheme(context.applicationContext)
            if (count == 0) {
                "Nuvarande är valt. Ingen widget finns på hemskärmen ännu."
            } else {
                "Nuvarande är valt. Uppdatering skickad till $count widget(ar)."
            }
        } catch (error: Exception) {
            android.util.Log.e("WidgetThemeSettings", "Could not refresh original widget", error)
            "Nuvarande är sparat, men Android kunde inte uppdatera widgeten."
        }
    }

    // Automatically restore the original widget when visiting Settings.
    // The provider also restores it on APK replacement and widget updates.
    LaunchedEffect(Unit) {
        restoreCurrentWidget()
    }

    val featureOptions = remember {
        listOf(
            WidgetFeatureSettings.WEEK_REMINDERS to "Veckans påminnelser",
            WidgetFeatureSettings.LIVE_STATUS to "Pågår / börjar snart",
            WidgetFeatureSettings.BIRTHDAYS to "Födelsedagar inom 7 dagar",
            WidgetFeatureSettings.QUICK_ADD to "Snabbknappen +",
            WidgetFeatureSettings.SYNC_WARNING to "Varning vid synkfel",
            WidgetFeatureSettings.WEEK_NUMBER to "Veckonummer",
        )
    }
    var featureValues by remember {
        mutableStateOf(featureOptions.associate { (key, _) ->
            key to WidgetFeatureSettings.enabled(context, key)
        })
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Widget",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Nuvarande",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            "Detta är den enda widgeten. Kalenderns egna teman påverkas inte.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        WidgetMaterialPreview(
            choice = WidgetThemeColors.ORIGINAL,
            modifier = Modifier.fillMaxWidth().height(218.dp)
                .clip(RoundedCornerShape(17.dp)),
        )
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { restoreCurrentWidget() },
        ) { Text("Uppdatera Nuvarande") }
        refreshStatus?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        OutlinedButton(
            onClick = {
                context.startActivity(
                    android.content.Intent(context, WidgetQuickAddActivity::class.java).apply {
                        putExtra(WidgetQuickAddActivity.EXTRA_PRIVATE_INBOX, true)
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Mina privata påminnelser") }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { detailsExpanded = !detailsExpanded },
        ) { Text(if (detailsExpanded) "Dölj widgetinnehåll" else "Anpassa widgetinnehåll") }

        if (detailsExpanded) {
            Text(
                "Välj vad som visas i Nuvarande. Detta påverkar inte kalenderns tema.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            featureOptions.forEach { (key, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(
                        checked = featureValues[key] ?: true,
                        onCheckedChange = { enabled ->
                            featureValues = featureValues + (key to enabled)
                            WidgetFeatureSettings.setEnabled(context, key, enabled)
                        },
                    )
                }
            }
        }
    }
}
