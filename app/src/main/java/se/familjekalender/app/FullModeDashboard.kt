package se.familjekalender.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private enum class FullSmartPanel {
    ASSISTANT,
    WEEK,
    AUTOPILOT,
    ROUTINES,
}

@Composable
internal fun FullModeDashboard(
    session: FamilySession,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    shopping: List<SyncShoppingItem>,
    palette: SeasonPalette,
    themeMode: ThemeMode,
    addMenuRequest: Int,
    onAssistantAdd: () -> Unit,
    onAdd: () -> Unit,
    onAddLaundry: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    var smartPanel by rememberSaveable { mutableStateOf(FullSmartPanel.ASSISTANT) }
    val today = LocalDate.now()
    val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val weekEnd = weekStart.plusDays(6)
    val todayCount = remember(events, today) { events.count { it.date == today } }
    val weekCount = remember(events, weekStart) {
        events.count { !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) }
    }
    val conflicts = remember(events, members) {
        analyzeCalendarConflicts(events, members)
            .count { !it.date.isBefore(today) && !it.date.isAfter(weekEnd) }
    }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PremiumModeHeader(
            title = "Familjekalender",
            subtitle = "Fullständig översikt utan röran",
            onAdd = onAdd,
            onSettings = onOpenSettings,
        )

        FullSummaryStrip(
            todayCount = todayCount,
            weekCount = weekCount,
            conflictCount = conflicts,
        )

        PremiumGlassPanel(Modifier.fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth()
                    .height(610.dp)
                    .padding(2.dp)
            ) {
                ExactCalendarScreen(
                    selectedDate = selectedDate,
                    onSelect = onSelectDate,
                    events = events,
                    members = members,
                    palette = palette,
                    themeMode = themeMode,
                    onAdd = onAdd,
                    onAddLaundry = onAddLaundry,
                    addMenuRequest = addMenuRequest,
                    showSeasonalBackground = false,
                )
            }
        }

        Text(
            "SMART CENTER",
            color = PremiumMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        )

        FullPanelSelector(
            selected = smartPanel,
            onSelected = { smartPanel = it },
        )

        when (smartPanel) {
            FullSmartPanel.ASSISTANT ->
                FamilyAssistantCard(session, events, members, shopping, onAssistantAdd)

            FullSmartPanel.WEEK ->
                WeekOverviewCard(events, members)

            FullSmartPanel.AUTOPILOT ->
                FamilyAutopilotCard(events, members)

            FullSmartPanel.ROUTINES ->
                RecurringLifeCard(
                    session = session,
                    events = events,
                    onChanged = onRefresh,
                )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun FullSummaryStrip(
    todayCount: Int,
    weekCount: Int,
    conflictCount: Int,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FullSummaryTile(
            label = "IDAG",
            value = todayCount.toString(),
            helper = if (todayCount == 1) "aktivitet" else "aktiviteter",
            modifier = Modifier.weight(1f),
        )
        FullSummaryTile(
            label = "VECKAN",
            value = weekCount.toString(),
            helper = if (weekCount == 1) "aktivitet" else "aktiviteter",
            modifier = Modifier.weight(1f),
        )
        FullSummaryTile(
            label = "KROCKAR",
            value = conflictCount.toString(),
            helper = if (conflictCount == 0) "lugnt" else "att se över",
            accent = conflictCount > 0,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FullSummaryTile(
    label: String,
    value: String,
    helper: String,
    accent: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = PremiumGlassRaised,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (accent) Color(0xFFFFA36A).copy(alpha = .34f) else PremiumBorder,
        ),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 12.dp)) {
            Text(
                label,
                color = PremiumMuted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .8.sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                value,
                color = if (accent) Color(0xFFFFB27D) else Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                helper,
                color = PremiumMuted,
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FullPanelSelector(
    selected: FullSmartPanel,
    onSelected: (FullSmartPanel) -> Unit,
) {
    Surface(
        color = PremiumGlass,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, PremiumBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            FullPanelButton("Assistent", FullSmartPanel.ASSISTANT, selected, onSelected, Modifier.weight(1f))
            FullPanelButton("Vecka", FullSmartPanel.WEEK, selected, onSelected, Modifier.weight(1f))
            FullPanelButton("Autopilot", FullSmartPanel.AUTOPILOT, selected, onSelected, Modifier.weight(1f))
            FullPanelButton("Rutiner", FullSmartPanel.ROUTINES, selected, onSelected, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FullPanelButton(
    label: String,
    panel: FullSmartPanel,
    selected: FullSmartPanel,
    onSelected: (FullSmartPanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = panel == selected
    Surface(
        color = if (isSelected) PremiumPurple.copy(alpha = .22f) else Color.Transparent,
        contentColor = if (isSelected) Color.White else PremiumMuted,
        shape = RoundedCornerShape(15.dp),
        modifier = modifier.clickable { onSelected(panel) },
    ) {
        Box(
            Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}
