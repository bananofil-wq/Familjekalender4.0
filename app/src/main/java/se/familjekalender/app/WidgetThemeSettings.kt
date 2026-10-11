package se.familjekalender.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class WidgetThemeOption(val value: String, val title: String, val description: String)

@Composable
internal fun WidgetThemeSettings(calendarTheme: CleanVisualTheme) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(WidgetThemeColors.savedChoice(context)) }
    var pickerOpen by remember { mutableStateOf(false) }

    val options = remember {
        listOf(
            WidgetThemeOption(
                WidgetThemeColors.ORIGINAL, "Originalwidget",
                "Din nuvarande mörka glaswidget, helt oförändrad",
            ),
            WidgetThemeOption(
                WidgetThemeColors.FOLLOW_CALENDAR, "Samma som kalendern",
                "Widgeten byter automatiskt när du ändrar kalendertema",
            ),
        ) + selectableCleanVisualThemes.map {
            WidgetThemeOption(it.name, it.label, it.description)
        }
    }
    val chosen = options.firstOrNull { it.value == selected } ?: options.first()

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            "Widgettema",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Välj widgetens utseende oberoende av kalendern. Kalenderns tema ändras inte.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        WidgetThemePreview(selected, calendarTheme)
        OutlinedButton(
            onClick = { pickerOpen = true },
            modifier = Modifier.fillMaxWidth().height(46.dp),
        ) {
            Text("Widget: ${chosen.title}  ·  Byt tema", maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Text(
            "Vald kalender: ${calendarTheme.label}  •  Widget: ${chosen.title}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
    }

    if (pickerOpen) {
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = {
                Text("Välj widgettema", color = MaterialTheme.colorScheme.onSurface)
            },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    options.forEach { option ->
                        val active = selected == option.value
                        val themed = when (option.value) {
                            WidgetThemeColors.ORIGINAL -> null
                            WidgetThemeColors.FOLLOW_CALENDAR -> calendarTheme
                            else -> selectableCleanVisualThemes.firstOrNull {
                                it.name == option.value
                            }
                        }
                        val tint = themed?.let { cleanThemeSpec(it).accentStrong }
                            ?: MaterialTheme.colorScheme.primary
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    selected = option.value
                                    WidgetThemeColors.saveChoice(context, option.value)
                                    // Partial updates preserve existing widget text, click actions
                                    // and scrolling while refreshing themed collection rows.
                                    FamilyCalendarWidget.refreshTheme(context)
                                    pickerOpen = false
                                }
                                .padding(horizontal = 5.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(23.dp)
                                    .background(tint, RoundedCornerShape(7.dp)),
                            )
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    option.title,
                                    fontWeight = if (active) FontWeight.Bold
                                        else FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                )
                                if (option.value == WidgetThemeColors.ORIGINAL ||
                                    option.value == WidgetThemeColors.FOLLOW_CALENDAR) {
                                    Text(option.description,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp, maxLines = 2)
                                }
                            }
                            RadioButton(
                                selected = active,
                                onClick = {
                                    selected = option.value
                                    WidgetThemeColors.saveChoice(context, option.value)
                                    FamilyCalendarWidget.refreshTheme(context)
                                    pickerOpen = false
                                },
                            )
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

/**
 * Static SAMPLE showing the structure and contrast of a selected widget style.
 * Real activities and counts are supplied by the widget Worker, not this preview.
 */
@Composable
private fun WidgetThemePreview(choice: String, calendarTheme: CleanVisualTheme) {
    val context = LocalContext.current
    val palette = remember(choice, calendarTheme) { WidgetThemeColors.colors(context) }
    val original = choice == WidgetThemeColors.ORIGINAL
    val today = java.time.LocalDate.now()
    val dateLabel = today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale("sv", "SE")))
        .replaceFirstChar { it.uppercase() }
    val background = when (palette.skin) {
        WidgetThemeColors.Skin.WOOD -> Color(0xFF4C2B18)
        WidgetThemeColors.Skin.COPPER -> Color(0xFF412925)
        WidgetThemeColors.Skin.TITANIUM -> Color(0xFFE4E9EC)
        WidgetThemeColors.Skin.CARBON -> Color(0xFF101824)
        WidgetThemeColors.Skin.HIPPIE -> Color(0xFFFFE9A0)
        WidgetThemeColors.Skin.LIGHT -> Color(0xFFFCFDFF)
        WidgetThemeColors.Skin.DARK -> Color(0xFF1D202A)
        WidgetThemeColors.Skin.GLASS -> Color(0xFF476594)
        WidgetThemeColors.Skin.FOREST -> Color(0xFF193026)
        WidgetThemeColors.Skin.GOLD -> Color(0xFF1C1C1E)
        WidgetThemeColors.Skin.PASTEL -> Color(0xFFFFF3F2)
        WidgetThemeColors.Skin.GOTHAM -> Color(0xFF0B2138)
        WidgetThemeColors.Skin.RETRO -> Color(0xFF1B354D)
        WidgetThemeColors.Skin.SAGE -> Color(0xFFF0F3E8)
        WidgetThemeColors.Skin.PAPER -> Color(0xFFFAF2E8)
        WidgetThemeColors.Skin.CLASSIC -> Color(0xE62D2C34)
    }
    val contentColor = Color(palette.text)
    val rowColor = if (background.red + background.green + background.blue > 1.7f)
        Color.Black.copy(alpha = .045f) else Color.White.copy(alpha = .105f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = background,
        border = BorderStroke(1.dp,
            if (original) Color.White.copy(alpha = .35f)
            else Color(palette.accent).copy(alpha = .5f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFE75D68),
                ) {
                    Text(today.dayOfMonth.toString(), Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold, color = Color.White, fontSize = 17.sp)
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(dateLabel, color = contentColor, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold)
                    Text("3 aktiviteter kvar idag", color = Color(palette.muted),
                        fontSize = 11.sp)
                }
                Text("↻", color = contentColor, fontSize = 21.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF246D4A)) {
                    Text("✓  0 To-Do   ›",
                        color = Color.White, fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp))
                }
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF245D9B)) {
                    Text("🛒  2 inköp   ›", color = Color.White, fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp))
                }
            }
            listOf("Viggo    Bollskola               08:30–09:30",
                "Hugo     Träning                  09:00",
                "Alla        Tömma kattlåda").forEach { row ->
                Surface(shape = RoundedCornerShape(15.dp), color = rowColor,
                    border = BorderStroke(.6.dp, Color(palette.muted).copy(alpha = .4f))) {
                    Text(row, color = contentColor, fontSize = 10.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(8.dp))
                }
            }
            Text("Schematisk förhandsvisning – dina riktiga aktiviteter visas i widgeten",
                color = Color(palette.muted), fontSize = 9.sp, maxLines = 2)
        }
    }
}
