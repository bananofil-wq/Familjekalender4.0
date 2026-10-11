package se.familjekalender.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class WidgetThemeOption(
    val value: String,
    val title: String,
    val theme: CleanVisualTheme?,
)

/** Widget style choice is saved independently of the calendar's selected design. */
@Composable
internal fun WidgetThemeSettings(calendarTheme: CleanVisualTheme) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(WidgetThemeColors.savedChoice(context)) }
    var pickerOpen by remember { mutableStateOf(false) }

    val options = remember {
        listOf(
            WidgetThemeOption(WidgetThemeColors.ORIGINAL, "Originalwidget", null),
            WidgetThemeOption(WidgetThemeColors.FOLLOW_CALENDAR, "Samma som kalendern", null),
        ) + selectableCleanVisualThemes.map { WidgetThemeOption(it.name, it.label, it) }
    }
    val selectedOption = options.firstOrNull { it.value == selected } ?: options.first()
    val selectedTheme = when (selected) {
        WidgetThemeColors.ORIGINAL -> null
        WidgetThemeColors.FOLLOW_CALENDAR -> calendarTheme
        else -> selectedOption.theme
    }

    fun choose(value: String) {
        selected = value
        WidgetThemeColors.saveChoice(context, value)
        FamilyCalendarWidget.refreshTheme(context)
        pickerOpen = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Widgettema",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Widgeten kan ha ett annat tema än kalendern.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        // Small thumbnail of currently selected skin. The previous full-size
        // static widget mockup took too much space in Settings.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.width(110.dp).height(84.dp)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                ThemeMiniature(
                    theme = selectedTheme,
                    widget = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    selectedOption.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Widgettema valt separat",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
                OutlinedButton(
                    onClick = { pickerOpen = true },
                    modifier = Modifier.fillMaxWidth().height(39.dp),
                ) {
                    Text("Byt tema", fontSize = 12.sp)
                }
            }
        }
        Text(
            "Kalender: ${calendarTheme.label}   •   Widget: ${selectedOption.title}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }

    if (pickerOpen) {
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text("Välj widgettema", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 470.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        "Små förhandsbilder av varje tema. Tryck för att välja.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                    options.chunked(2).forEach { pair ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            pair.forEach { option ->
                                val active = selected == option.value
                                val theme = when (option.value) {
                                    WidgetThemeColors.ORIGINAL -> null
                                    WidgetThemeColors.FOLLOW_CALENDAR -> calendarTheme
                                    else -> option.theme
                                }
                                Surface(
                                    modifier = Modifier.weight(1f)
                                        .clickable { choose(option.value) },
                                    shape = RoundedCornerShape(11.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        if (active) 2.dp else 1.dp,
                                        if (active) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                    ),
                                ) {
                                    Column(Modifier.padding(5.dp)) {
                                        Box(
                                            Modifier.fillMaxWidth().height(105.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                        ) {
                                            ThemeMiniature(
                                                theme = theme,
                                                widget = true,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                            if (active) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = "Vald",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.align(Alignment.TopEnd)
                                                        .padding(3.dp).size(18.dp),
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(5.dp))
                                        Text(
                                            option.title,
                                            fontSize = 11.sp,
                                            lineHeight = 13.sp,
                                            fontWeight = if (active) FontWeight.Bold
                                                else FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            minLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            repeat(2 - pair.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerOpen = false }) { Text("Stäng") }
            },
        )
    }
}
