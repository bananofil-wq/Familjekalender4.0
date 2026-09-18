package se.familjekalender.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime

private data class TodayRow(
    val event: SyncEvent,
    val member: SyncMember?,
    val startMinute: Int,
    val endMinute: Int?,
)

private fun todayMinute(value: String?): Int? {
    if (value.isNullOrBlank()) return null
    return runCatching { LocalTime.parse(value) }.getOrNull()?.let { it.hour * 60 + it.minute }
}

private fun todayRows(events: List<SyncEvent>, members: List<SyncMember>): List<TodayRow> {
    val today = LocalDate.now()
    val memberById = members.associateBy { it.id }
    return events
        .filter { it.date == today }
        .mapNotNull { event ->
            val start = todayMinute(event.time) ?: return@mapNotNull null
            TodayRow(event, memberById[event.memberId], start, todayMinute(event.endTime))
        }
        .sortedBy { it.startMinute }
}

private fun todayConflictText(rows: List<TodayRow>): String? {
    val byMember =
        rows
            .filter { it.event.memberId != null && it.event.memberId != ALL_FAMILY_MEMBER_ID }
            .groupBy { it.event.memberId }

    byMember.values.forEach { ownRows ->
        val sorted = ownRows.sortedBy { it.startMinute }
        for (i in 0 until sorted.lastIndex) {
            val first = sorted[i]
            val second = sorted[i + 1]
            val end = first.endMinute ?: (first.startMinute + 60)
            if (end > second.startMinute) {
                val name = first.member?.name ?: "En familjemedlem"
                return "$name har överlappande aktiviteter idag."
            }
        }
    }
    return null
}

private fun nextTodayRow(rows: List<TodayRow>): TodayRow? {
    val now = LocalTime.now().let { it.hour * 60 + it.minute }
    return rows.firstOrNull { (it.endMinute ?: it.startMinute + 60) >= now }
}

private fun formatMinute(minute: Int): String = "%02d:%02d".format((minute / 60) % 24, minute % 60)

@Composable
fun FamilyTodayCard(events: List<SyncEvent>, members: List<SyncMember>) {
    val rows = todayRows(events, members)
    val conflict = todayConflictText(rows)
    val next = nextTodayRow(rows)
    val realMembers = members.filter { it.id != ALL_FAMILY_MEMBER_ID }
    val eventsByMember = rows.groupBy { it.event.memberId }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Familjen idag", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text(
                        if (rows.isEmpty()) "Inget tidsatt idag"
                        else "${rows.size} tidsatta aktiviteter",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    if (conflict == null) "Ingen konflikt" else "Konflikt",
                    color =
                        if (conflict == null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            conflict?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

            next?.let { item ->
                val who = item.member?.name ?: "Hela familjen"
                val end = item.endMinute?.let { "–${formatMinute(it)}" }.orEmpty()
                Text(
                    "Nästa: ${formatMinute(item.startMinute)}$end · $who · ${item.event.title}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }

            realMembers.forEach { member ->
                val own = eventsByMember[member.id].orEmpty()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(10.dp)
                            .clip(CircleShape)
                            .background(Color(member.colorArgb.toInt()))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(member.name, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Text(
                        when {
                            own.isEmpty() -> "Ledig i kalendern"
                            own.size == 1 -> "1 aktivitet"
                            else -> "${own.size} aktiviteter"
                        },
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
            }

            val allFamily = eventsByMember[ALL_FAMILY_MEMBER_ID].orEmpty()
            if (allFamily.isNotEmpty()) {
                Text(
                    "★ ${allFamily.size} gemensamma ${if (allFamily.size == 1) "aktivitet" else "aktiviteter"}",
                    color = Color(0xFFFFD75E),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
