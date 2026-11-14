package se.familjekalender.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Three independent appearances inside the EXISTING home-screen widget selector. */
@Composable
internal fun WidgetThemeSettings(calendarTheme: CleanVisualTheme) {
    val context = LocalContext.current
    var detailsExpanded by remember { mutableStateOf(false) }
    var refreshStatus by remember { mutableStateOf<String?>(null) }
    var activeChoice by remember { mutableStateOf(WidgetThemeColors.savedChoice(context)) }

    fun chooseWidget(choice: String) {
        if (!WidgetThemeColors.saveChoice(context, choice)) {
            refreshStatus = "Kunde inte spara widgetvalet. Försök igen."
            return
        }
        activeChoice = WidgetThemeColors.savedChoice(context)
        refreshStatus = try {
            val count = FamilyCalendarWidget.refreshTheme(context.applicationContext)
            if (count == 0) {
                "Valet är sparat. Lägg till Familjekalendern på hemskärmen."
            } else {
                "${widgetChoiceLabel(choice)} är vald. Uppdatering skickad till $count widget(ar)."
            }
        } catch (error: Exception) {
            android.util.Log.e("WidgetThemeSettings", "Could not update widget", error)
            "Valet är sparat, men Android kunde inte uppdatera widgeten."
        }
    }

    // Re-apply even when the stored selection changed during APK replacement.
    // This is a full RemoteViews replacement, not a theme-only partial update.
    LaunchedEffect(Unit) {
        activeChoice = WidgetThemeColors.savedChoice(context)
        if (refreshStatus == null) {
            refreshStatus = try {
                val count = FamilyCalendarWidget.refreshTheme(context.applicationContext)
                if (count == 0) "Ingen Familjekalender-widget hittades på hemskärmen."
                else "Aktiv: ${widgetChoiceLabel(activeChoice)}. Uppdatering skickad till $count widget(ar)."
            } catch (error: Exception) {
                android.util.Log.e("WidgetThemeSettings", "Could not reapply selected widget", error)
                "Det gick inte att skicka widgetuppdateringen."
            }
        }
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
        Text("Widget", style = MaterialTheme.typography.titleMedium)
        Text(
            "Välj widget här och se förhandsvisningen direkt. Kalenderns teman ändras inte.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Text(
            "Aktiv widget: " + widgetChoiceLabel(activeChoice),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.width(164.dp)) {
                Text("Nuvarande", style = MaterialTheme.typography.titleSmall)
                WidgetMaterialPreview(
                    choice = WidgetThemeColors.ORIGINAL,
                    modifier = Modifier.fillMaxWidth().height(190.dp)
                        .clip(RoundedCornerShape(15.dp)),
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { chooseWidget(WidgetThemeColors.ORIGINAL) },
                ) {
                    Text(if (activeChoice == WidgetThemeColors.ORIGINAL) "Vald: Nuvarande" else "Välj Nuvarande")
                }
            }
            Column(modifier = Modifier.width(164.dp)) {
                Text("Solnedgång", style = MaterialTheme.typography.titleSmall)
                Image(
                    painter = painterResource(R.drawable.widget_sunset_preview),
                    contentDescription = "Exakt bildreferens för Solnedgång-widgeten",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(190.dp)
                        .clip(RoundedCornerShape(15.dp)),
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { chooseWidget(WidgetThemeColors.SUNSET) },
                ) {
                    Text(if (activeChoice == WidgetThemeColors.SUNSET) "Vald: Solnedgång" else "Välj Solnedgång")
                }
            }
            Column(modifier = Modifier.width(164.dp)) {
                Text("Aurora Glass", style = MaterialTheme.typography.titleSmall)
                AuroraWidgetPreview(
                    modifier = Modifier.fillMaxWidth().height(190.dp)
                        .clip(RoundedCornerShape(15.dp)),
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { chooseWidget(WidgetThemeColors.AURORA) },
                ) {
                    Text(if (activeChoice == WidgetThemeColors.AURORA)
                        "Vald: Aurora" else "Välj Aurora")
                }
            }
        }
        refreshStatus?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
                "Välj vad som visas i den aktiva widgeten. Kalenderns tema påverkas inte.",
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
                            FamilyCalendarWidget.refreshTheme(context.applicationContext)
                        },
                    )
                }
            }
        }
    }
}

private fun widgetChoiceLabel(choice: String): String = when (choice) {
    WidgetThemeColors.AURORA -> "Aurora Glass"
    WidgetThemeColors.SUNSET -> "Solnedgång"
    else -> "Nuvarande"
}
