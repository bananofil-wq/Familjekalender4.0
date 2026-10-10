package se.familjekalender.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** All ten designed layouts share readable, color-matched dialogs, never the old purple glass dialog. */
@Composable
internal fun DesignedCleanSummaryDialog(
    theme: CleanVisualTheme,
    heading: String,
    subtitle: String,
    kind: String,
    events: List<SyncEvent>,
    conflicts: List<CalendarConflict>,
    members: Map<String, SyncMember>,
    locale: Locale,
    onDismiss: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onEventClick: (SyncEvent) -> Unit,
) {
    val spec = cleanThemeSpec(theme)
    val square = theme == CleanVisualTheme.EDITORIAL_PLANNER ||
        theme == CleanVisualTheme.RETRO_DIGITAL || theme == CleanVisualTheme.NEON_PULSE
    val mono = theme == CleanVisualTheme.RETRO_DIGITAL || theme == CleanVisualTheme.NEON_PULSE
    val errorTint = if (isLightCleanTheme(theme)) Color(0xFFB64B58) else Color(0xFFFFA0A8)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = spec.panelTop,
        shape = RoundedCornerShape(if (square) 7.dp else spec.cardRadius),
        tonalElevation = 0.dp,
        title = {
            Column {
                Text(
                    heading,
                    color = spec.text,
                    fontFamily = if (mono) FontFamily.Monospace else spec.titleFont,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(subtitle, color = spec.muted, fontSize = 11.sp)
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 515.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (kind == "CONFLICTS") {
                    if (conflicts.isEmpty()) {
                        Text("Inga krockar den här veckan.", color = spec.muted, fontSize = 13.sp)
                    } else {
                        conflicts.forEach { conflict ->
                            Surface(
                                color = errorTint.copy(alpha = .08f),
                                shape = RoundedCornerShape(if (square) 3.dp else 13.dp),
                                border = BorderStroke(1.dp, errorTint.copy(alpha = .3f)),
                            ) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text(
                                        conflict.date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)),
                                        color = errorTint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(conflict.message, color = spec.text, fontSize = 13.sp)
                                    listOf(conflict.first, conflict.second).distinctBy { it.id }.forEach { event ->
                                        Text(
                                            "${event.time.ifBlank { "Hela dagen" }} · ${event.title}",
                                            modifier = Modifier.fillMaxWidth()
                                                .clickable { onEventClick(event) }
                                                .padding(vertical = 6.dp),
                                            color = spec.accentStrong, fontSize = 11.sp,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (events.isEmpty()) {
                        Text(when (kind) {
                            "REMINDERS" -> "Inga påminnelser den här veckan."
                            "WEEK" -> "Inga aktiviteter den här veckan."
                            else -> "Inga aktiviteter den här dagen."
                        }, color = spec.muted, fontSize = 13.sp)
                    }
                    events.groupBy { it.date }.toSortedMap().forEach { (date, dayEvents) ->
                        if (kind != "TODAY") {
                            Text(
                                date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
                                    .replaceFirstChar { it.uppercase(locale) },
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        onSelectDate(date)
                                        onDismiss()
                                    }.padding(top = 6.dp, bottom = 3.dp),
                                color = spec.accentStrong, fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        dayEvents.sortedWith(compareBy<SyncEvent> { it.time }.thenBy { it.title })
                            .forEach { event ->
                                val tint = members[event.memberId]?.let { Color(it.colorArgb.toInt()) }
                                    ?: spec.accent
                                val name = members[event.memberId]?.name ?: "Hela familjen"
                                Surface(
                                    color = spec.panelMid,
                                    shape = RoundedCornerShape(if (square) 3.dp else 13.dp),
                                    border = BorderStroke(1.dp, spec.border),
                                    modifier = Modifier.fillMaxWidth().clickable { onEventClick(event) },
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Spacer(Modifier.width(3.dp).height(34.dp)
                                            .background(tint, RoundedCornerShape(2.dp)))
                                        Spacer(Modifier.width(9.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(event.title, color = spec.text, fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(name, color = spec.muted, fontSize = 10.sp)
                                        }
                                        Spacer(Modifier.width(5.dp))
                                        Text(event.time.ifBlank { "Heldag" }, color = spec.accentStrong,
                                            fontSize = 10.sp)
                                    }
                                }
                            }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Stäng", color = spec.accentStrong, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
