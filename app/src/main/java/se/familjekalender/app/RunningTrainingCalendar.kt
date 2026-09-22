package se.familjekalender.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

private data class RunPlanMonth(
    val month: Int,
    val label: String,
    val weeks: List<Int>,
    val targetMinKm: Double,
    val targetMaxKm: Double,
)

private val paperRunPlan2026 =
    listOf(
        RunPlanMonth(9, "September", (36..39).toList(), 35.0, 40.0),
        RunPlanMonth(10, "Oktober", (40..44).toList(), 40.0, 44.0),
        RunPlanMonth(11, "November", (45..48).toList(), 45.0, 45.0),
        RunPlanMonth(12, "December", (49..53).toList(), 45.0, 50.0),
    )

private fun planRunDistanceKm(event: SyncEvent): Double? {
    val title = event.title
    return when {
        title.startsWith("🏃 RUN|") ->
            title.removePrefix("🏃 RUN|").substringBefore('|').toDoubleOrNull()

        title.startsWith("🏃 Löpning · ") ->
            title
                .removePrefix("🏃 Löpning · ")
                .substringBefore(" · ")
                .removeSuffix(" km")
                .replace(',', '.')
                .toDoubleOrNull()

        else -> null
    }?.takeIf { it > 0.0 }
}

private fun weeklyRunKm(events: List<SyncEvent>, week: Int): Double {
    val wf = WeekFields.ISO
    return events
        .asSequence()
        .filter { it.date.get(wf.weekBasedYear()) == 2026 && it.date.get(wf.weekOfWeekBasedYear()) == week }
        .mapNotNull(::planRunDistanceKm)
        .sum()
}

@Composable
internal fun RunningTrainingCalendar(events: List<SyncEvent>) {
    val today = LocalDate.now()
    val initialMonth =
        remember(today) {
            if (today.year == 2026 && today.monthValue in 9..12) today.monthValue else 9
        }
    var selectedMonth by rememberSaveable { mutableIntStateOf(initialMonth) }
    val month = paperRunPlan2026.first { it.month == selectedMonth }
    val currentWeek =
        remember(today) {
            if (today.year == 2026) today.get(WeekFields.ISO.weekOfWeekBasedYear()) else -1
        }

    Card(
        colors = CardDefaults.cardColors(containerColor = PremiumGlass),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Löpkalender",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Plan från september–december 2026",
                        color = PremiumMuted,
                        fontSize = 11.sp,
                    )
                }
                Surface(
                    color = PremiumPurpleBright.copy(alpha = .16f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        "VECKOMÅL",
                        color = PremiumPurpleBright,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                paperRunPlan2026.forEach { item ->
                    val selected = item.month == selectedMonth
                    Surface(
                        color = if (selected) PremiumPurpleBright else PremiumGlassRaised,
                        contentColor = if (selected) Color.White else PremiumMuted,
                        shape = RoundedCornerShape(13.dp),
                        modifier =
                            Modifier.weight(1f)
                                .clickable { selectedMonth = item.month },
                    ) {
                        Box(
                            Modifier.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                item.label.take(3),
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            val targetText =
                if (month.targetMinKm == month.targetMaxKm) {
                    "${month.targetMinKm.roundToInt()} km/vecka"
                } else {
                    "${month.targetMinKm.roundToInt()}–${month.targetMaxKm.roundToInt()} km/vecka"
                }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    month.label,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    targetText,
                    color = PremiumPurpleBright,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            month.weeks.forEach { week ->
                val actualKm = weeklyRunKm(events, week)
                val target = month.targetMinKm
                val progress = (actualKm / target).coerceIn(0.0, 1.0).toFloat()
                val isCurrent = week == currentWeek
                val note =
                    when (week) {
                        37 -> "35,5 km antecknat · 18 + 10,5 + 8"
                        38 -> "32 km antecknat · 9,2 + 14 + 8,7"
                        39 -> "Förkylt/ont i halsen – se vad veckan ger"
                        else -> null
                    }

                Surface(
                    color =
                        if (isCurrent) PremiumPurpleBright.copy(alpha = .10f)
                        else Color.White.copy(alpha = .035f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isCurrent) {
                                Box(
                                    Modifier.size(7.dp)
                                        .clip(CircleShape)
                                        .background(PremiumPurpleBright)
                                )
                                Spacer(Modifier.width(7.dp))
                            }
                            Text(
                                "Vecka $week",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (actualKm > 0.0) {
                                    "${"%.1f".format(Locale.US, actualKm).replace('.', ',')} km"
                                } else {
                                    "0 km"
                                },
                                color = if (actualKm >= target) PremiumPurpleBright else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Box(
                            Modifier.fillMaxWidth()
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = .08f))
                        ) {
                            Box(
                                Modifier.fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(PremiumPurpleBright)
                            )
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                note ?: "Veckomål $targetText",
                                color = PremiumMuted,
                                fontSize = 10.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${(progress * 100).roundToInt()}%",
                                color = PremiumMuted,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }

            Text(
                "Registrerade löppass i Sportläget räknas automatiskt in i respektive vecka.",
                color = PremiumMuted,
                fontSize = 10.sp,
            )
        }
    }
}
