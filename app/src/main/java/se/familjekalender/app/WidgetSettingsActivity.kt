package se.familjekalender.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class WidgetSettingsActivity : ComponentActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                WidgetSettingsScreen(appWidgetId) { showClock, showDate, showToday, showTomorrow, maxItems ->
                    val prefs = getSharedPreferences("family_calendar_widget", MODE_PRIVATE)
                    prefs.edit()
                        .putBoolean("show_clock_$appWidgetId", showClock)
                        .putBoolean("show_date_$appWidgetId", showDate)
                        .putBoolean("show_today_$appWidgetId", showToday)
                        .putBoolean("show_tomorrow_$appWidgetId", showTomorrow)
                        .putInt("max_items_$appWidgetId", maxItems)
                        .apply()

                    FamilyCalendarWidgetProvider.updateWidget(
                        this,
                        AppWidgetManager.getInstance(this),
                        appWidgetId
                    )
                    val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    setResult(Activity.RESULT_OK, result)
                    finish()
                }
            }
        }
    }
}

@Composable
private fun WidgetSettingsScreen(
    appWidgetId: Int,
    onSave: (Boolean, Boolean, Boolean, Boolean, Int) -> Unit
) {
    var showClock by remember { mutableStateOf(true) }
    var showDate by remember { mutableStateOf(true) }
    var showToday by remember { mutableStateOf(true) }
    var showTomorrow by remember { mutableStateOf(true) }
    var maxItems by remember { mutableIntStateOf(4) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Familjekalender-widget", style = MaterialTheme.typography.headlineSmall)
            Text("Välj vad som ska visas. Temat hämtas automatiskt från appen.")

            WidgetToggle("Visa klocka", showClock) { showClock = it }
            WidgetToggle("Visa datum", showDate) { showDate = it }
            WidgetToggle("Visa idag", showToday) { showToday = it }
            WidgetToggle("Visa imorgon", showTomorrow) { showTomorrow = it }

            Text("Aktiviteter per dag: $maxItems")
            Slider(
                value = maxItems.toFloat(),
                onValueChange = { maxItems = it.toInt().coerceIn(1, 8) },
                valueRange = 1f..8f,
                steps = 6
            )

            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onSave(showClock, showDate, showToday, showTomorrow, maxItems) },
                enabled = showClock || showDate || showToday || showTomorrow,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Lägg till widget") }
        }
    }
}

@Composable
private fun WidgetToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
