package se.familjekalender.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val MinimalBg = Color(0xFF0B0B10)
private val MinimalSurface = Color(0xFF17171F)
private val MinimalSurfaceRaised = Color(0xFF1D1D26)
private val MinimalPurple = Color(0xFFA66CFF)
private val MinimalMuted = Color(0xFFAAA8B7)
private val MinimalDivider = Color(0xFF292933)

@Composable
internal fun MinimalCalendarScreen(
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    onAdd: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val locale = remember { Locale("sv", "SE") }
    var month by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    val memberById = remember(members) { members.associateBy { it.id } }
    val selectedEvents = remember(events, selectedDate) {
        events.filter { it.date == selectedDate }.sortedWith(compareBy<SyncEvent> { it.time }.thenBy { it.title })
    }

    LaunchedEffect(selectedDate) {
        val selectedMonth = YearMonth.from(selectedDate)
        if (selectedMonth != month) month = selectedMonth
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MinimalBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text("Familjeappen", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mer tid tillsammans", color = MinimalMuted, fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("♥", color = MinimalPurple, fontSize = 15.sp)
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Inställningar", tint = Color(0xFFD1CFDC))
            }
        }

        Spacer(Modifier.height(22.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                val next = month.minusMonths(1)
                month = next
                onSelect(next.atDay(1))
            }) {
                Text("‹", color = MinimalMuted, fontSize = 34.sp, fontWeight = FontWeight.Light)
            }
            Text(
                month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase(locale) } + " ${month.year}",
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = {
                val next = month.plusMonths(1)
                month = next
                onSelect(next.atDay(1))
            }) {
                Text("›", color = MinimalMuted, fontSize = 34.sp, fontWeight = FontWeight.Light)
            }
        }

        MinimalMonthGrid(
            month = month,
            selectedDate = selectedDate,
            events = events,
            memberById = memberById,
            onSelect = { date ->
                month = YearMonth.from(date)
                onSelect(date)
            }
        )

        Spacer(Modifier.height(18.dp))

        MinimalAgenda(
            date = selectedDate,
            events = selectedEvents,
            memberById = memberById,
            locale = locale
        )

        Spacer(Modifier.height(20.dp))

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            FilledIconButton(
                onClick = onAdd,
                modifier = Modifier.size(68.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MinimalPurple)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Lägg till aktivitet", tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(7.dp))
            Text("Lägg till aktivitet", color = MinimalMuted, fontSize = 13.sp)
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun MinimalMonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    events: List<SyncEvent>,
    memberById: Map<String, SyncMember>,
    onSelect: (LocalDate) -> Unit
) {
    val weekdays = listOf("MÅN", "TIS", "ONS", "TOR", "FRE", "LÖR", "SÖN")
    Row(Modifier.fillMaxWidth()) {
        weekdays.forEach { day ->
            Text(
                day,
                color = MinimalMuted,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    val first = month.atDay(1)
    val gridStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val eventsByDate = remember(events) { events.groupBy { it.date } }

    repeat(6) { row ->
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { column ->
                val date = gridStart.plusDays((row * 7 + column).toLong())
                val inMonth = YearMonth.from(date) == month
                val isSelected = date == selectedDate
                val dayEvents = eventsByDate[date].orEmpty()
                val birthday = dayEvents.any { it.title.trim().startsWith("🌈") }

                Box(
                    Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clickable { onSelect(date) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(MinimalPurple))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            date.dayOfMonth.toString(),
                            color = when {
                                isSelected -> Color.White
                                inMonth -> Color(0xFFF1F0F6)
                                else -> Color(0xFF5F5E69)
                            },
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Spacer(Modifier.height(3.dp))
                        if (birthday) {
                            BirthdayRainbowIcon(Modifier.size(width = 24.dp, height = 14.dp))
                        } else if (dayEvents.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                dayEvents
                                    .mapNotNull { event -> memberById[event.memberId]?.colorArgb }
                                    .distinct()
                                    .take(3)
                                    .forEach { colorArgb ->
                                        Box(
                                            Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(Color(colorArgb.toInt()))
                                        )
                                    }
                            }
                        } else {
                            Spacer(Modifier.height(9.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalAgenda(
    date: LocalDate,
    events: List<SyncEvent>,
    memberById: Map<String, SyncMember>,
    locale: Locale
) {
    val headerFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
    val header = date.format(headerFormatter).replaceFirstChar { it.uppercase(locale) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MinimalSurface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(header, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (events.size == 1) "1 aktivitet" else "${events.size} aktiviteter",
                    color = MinimalMuted,
                    fontSize = 12.sp
                )
            }

            if (events.isEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("Inga aktiviteter den här dagen", color = MinimalMuted, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
            } else {
                Spacer(Modifier.height(10.dp))
                events.forEachIndexed { index, event ->
                    MinimalAgendaRow(event, memberById[event.memberId])
                    if (index != events.lastIndex) {
                        HorizontalDivider(color = MinimalDivider, thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalAgendaRow(event: SyncEvent, member: SyncMember?) {
    val accent = member?.let { Color(it.colorArgb.toInt()) } ?: MinimalPurple
    val memberName = when {
        event.memberId == ALL_FAMILY_MEMBER_ID -> "Familjen"
        member != null -> member.name
        else -> "Familjen"
    }
    val title = event.title.removePrefix("🌈").removePrefix("🧺").trim()
    val timeText = event.endTime?.takeIf { it.isNotBlank() }?.let { "${event.time}–$it" } ?: event.time

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(50.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(accent)
        )
        Spacer(Modifier.width(11.dp))
        Text(timeText, color = MinimalMuted, fontSize = 12.sp, modifier = Modifier.width(78.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(5.dp))
                Text(memberName, color = MinimalMuted, fontSize = 11.sp)
            }
        }
    }
}
