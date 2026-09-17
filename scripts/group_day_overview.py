from pathlib import Path

ROOT = Path('app/src/main/java/se/familjekalender/app')


def replace_composable(path: Path, name: str, replacement: str) -> bool:
    source = path.read_text()
    marker = f'@Composable\nprivate fun {name}('
    start = source.find(marker)
    if start == -1:
        raise SystemExit(f'{name} not found in {path}')
    end = source.find('\n@Composable\n', start + len(marker))
    if end == -1:
        end = len(source)
    old = source[start:end]
    if old.strip() == replacement.strip():
        return False
    path.write_text(source[:start] + replacement.rstrip() + '\n' + source[end:])
    return True


def patch_exact_calendar() -> bool:
    path = ROOT / 'ExactCalendarScreen.kt'
    source = path.read_text()
    marker = '@Composable\nprivate fun DayOverviewPopup('
    start = source.find(marker)
    if start == -1:
        raise SystemExit('DayOverviewPopup not found')
    end = source.find('\n@Composable\n', start + len(marker))
    if end == -1:
        raise SystemExit('Could not find end of DayOverviewPopup')
    block = source[start:end]

    changed = False
    old_expanded = 'val expanded = personEvents.size == 1 || groupKey in expandedGroupKeys'
    if old_expanded in block:
        block = block.replace(old_expanded, 'val expanded = groupKey in expandedGroupKeys', 1)
        changed = True

    conditional_open = '''                        if (personEvents.size > 1) {
                            Surface('''
    if conditional_open in block:
        block = block.replace(conditional_open, '''                        Surface(''', 1)
        conditional_close = '''                            }
                        }

                        if (expanded) {'''
        if conditional_close not in block:
            raise SystemExit('Grouped card close marker not found in DayOverviewPopup')
        block = block.replace(conditional_close, '''                            }

                        if (expanded) {''', 1)
        changed = True

    old_count = 'Text("${personEvents.size} aktiviteter", color = Color.White.copy(alpha = .62f), fontSize = 12.sp)'
    new_count = 'Text(if (personEvents.size == 1) "1 aktivitet" else "${personEvents.size} aktiviteter", color = Color.White.copy(alpha = .62f), fontSize = 12.sp)'
    if old_count in block:
        block = block.replace(old_count, new_count, 1)
        changed = True

    block = block.replace('if (expanded) "Dölj" else "Visa alla"', 'if (expanded) "Dölj" else "Visa"')

    if changed:
        path.write_text(source[:start] + block + source[end:])
    return changed


MINIMAL_AGENDA = r'''@Composable
private fun PremiumAgenda(
    date: LocalDate,
    events: List<SyncEvent>,
    memberById: Map<String, SyncMember>,
    locale: Locale,
    motionEnabled: Boolean
) {
    val headerFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
    val header = date.format(headerFormatter).replaceFirstChar { it.uppercase(locale) }
    val groups = remember(events) {
        events.groupBy { it.memberId }
            .entries
            .sortedBy { group -> group.value.minOfOrNull { it.time } ?: "" }
    }
    var expandedGroups by remember(date) { mutableStateOf(emptySet<String>()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = LuxurySurfaceElevated),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, LuxuryOutlineSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(motionDuration(LuxuryMotion.Standard, motionEnabled)))
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(header, color = LuxuryText, style = MaterialTheme.typography.titleLarge)
                Surface(color = LuxurySurfaceHigh, shape = RoundedCornerShape(99.dp)) {
                    Text(
                        if (events.size == 1) "1 aktivitet" else "${events.size} aktiviteter",
                        color = LuxuryTextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            if (events.isEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("Inga aktiviteter den här dagen", color = LuxuryTextMuted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(2.dp))
                Text("En lugn dag i familjens kalender.", color = LuxuryTextMuted.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall)
            } else {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { group ->
                        val memberId = group.key
                        val personEvents = group.value.sortedWith(compareBy<SyncEvent> { it.time }.thenBy { it.title })
                        val member = memberId?.let { memberById[it] }
                        val memberName = when {
                            memberId == ALL_FAMILY_MEMBER_ID -> "Hela familjen"
                            member != null -> member.name
                            else -> "Familjen"
                        }
                        val accent = when {
                            memberId == ALL_FAMILY_MEMBER_ID -> Color(0xFFFFD75E)
                            member != null -> Color(member.colorArgb.toInt())
                            else -> MaterialTheme.colorScheme.primary
                        }
                        val groupKey = memberId ?: "__unassigned__"
                        val expanded = groupKey in expandedGroups

                        Surface(
                            color = LuxurySurface,
                            shape = RoundedCornerShape(17.dp),
                            border = BorderStroke(1.dp, accent.copy(alpha = .28f)),
                            modifier = Modifier.fillMaxWidth().clickable {
                                expandedGroups = if (expanded) expandedGroups - groupKey else expandedGroups + groupKey
                            }
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.width(3.dp).height(38.dp).clip(RoundedCornerShape(99.dp)).background(accent))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(memberName, color = LuxuryText, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (personEvents.size == 1) "1 aktivitet" else "${personEvents.size} aktiviteter",
                                        color = LuxuryTextMuted,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    if (expanded) "Dölj" else "Visa",
                                    color = accent,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (expanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                personEvents.forEach { event -> PremiumAgendaRow(event, member) }
                            }
                        }
                    }
                }
            }
        }
    }
}'''


PERSONAL_AGENDA = r'''@Composable
private fun PersonalTodayAgenda(
    selectedDate: LocalDate,
    events: List<SyncEvent>,
    members: List<SyncMember>
) {
    val locale = remember { Locale("sv", "SE") }
    val formatter = remember { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
    val dayEvents = remember(events, selectedDate) {
        events.filter { it.date == selectedDate }.sortedBy { it.time }
    }
    val memberMap = remember(members) { members.associateBy { it.id } }
    val groups = remember(dayEvents) {
        dayEvents.groupBy { it.memberId }
            .entries
            .sortedBy { group -> group.value.minOfOrNull { it.time } ?: "" }
    }
    var expandedGroups by remember(selectedDate) { mutableStateOf(emptySet<String>()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                selectedDate.format(formatter).replaceFirstChar { it.uppercase(locale) },
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                if (dayEvents.size == 1) "1 aktivitet" else "${dayEvents.size} aktiviteter",
                color = Muted,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            if (dayEvents.isEmpty()) {
                Text("Inga aktiviteter", color = Muted, fontSize = 12.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    groups.forEach { group ->
                        val memberId = group.key
                        val personEvents = group.value.sortedBy { it.time }
                        val member = memberId?.let { memberMap[it] }
                        val who = when {
                            memberId == ALL_FAMILY_MEMBER_ID -> "Hela familjen"
                            member != null -> member.name
                            else -> "Familjen"
                        }
                        val accent = when {
                            memberId == ALL_FAMILY_MEMBER_ID -> Color(0xFFFFD75E)
                            member != null -> Color(member.colorArgb.toInt())
                            else -> MaterialTheme.colorScheme.primary
                        }
                        val groupKey = memberId ?: "__unassigned__"
                        val expanded = groupKey in expandedGroups

                        Surface(
                            color = Color.White.copy(alpha = .04f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, accent.copy(alpha = .26f)),
                            modifier = Modifier.fillMaxWidth().clickable {
                                expandedGroups = if (expanded) expandedGroups - groupKey else expandedGroups + groupKey
                            }
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(9.dp).background(accent, RoundedCornerShape(99.dp)))
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(who, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (personEvents.size == 1) "1 aktivitet" else "${personEvents.size} aktiviteter",
                                        fontSize = 10.sp,
                                        color = Muted
                                    )
                                }
                                Text(if (expanded) "Dölj" else "Visa", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (expanded) {
                            personEvents.forEach { event ->
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        event.time.ifBlank { "Hela dagen" },
                                        modifier = Modifier.width(72.dp),
                                        color = accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(event.title, modifier = Modifier.weight(1f), fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}'''


RUNNING_AGENDA = r'''@Composable
private fun LifeAgendaCard(
    date: LocalDate,
    events: List<SyncEvent>,
    memberById: Map<String, SyncMember>,
    locale: Locale,
    motionEnabled: Boolean
) {
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
    val header = date.format(formatter).replaceFirstChar { it.uppercase(locale) }
    val groups = remember(events) {
        events.groupBy { it.memberId }
            .entries
            .sortedBy { group -> group.value.minOfOrNull { it.time } ?: "" }
    }
    var expandedGroups by remember(date) { mutableStateOf(emptySet<String>()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = LifeSurfaceRaised),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(motionDuration(220, motionEnabled)))
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(header, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (events.isEmpty()) "Lugn dag" else if (events.size == 1) "1 aktivitet" else "${events.size} aktiviteter",
                        color = LifeMuted,
                        fontSize = 11.sp
                    )
                }
            }

            if (events.isEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Inget planerat. Kalendern håller sig ur vägen.", color = LifeMuted, fontSize = 13.sp)
            } else {
                Spacer(Modifier.height(9.dp))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    groups.forEach { group ->
                        val memberId = group.key
                        val personEvents = group.value.sortedWith(compareBy<SyncEvent> { it.time }.thenBy { it.title })
                        val member = memberId?.let { memberById[it] }
                        val accent = when {
                            memberId == ALL_FAMILY_MEMBER_ID -> Color(0xFFFFD75E)
                            member != null -> Color(member.colorArgb.toInt())
                            else -> LifePurple
                        }
                        val memberName = when {
                            memberId == ALL_FAMILY_MEMBER_ID -> "Hela familjen"
                            member != null -> member.name
                            else -> "Familjen"
                        }
                        val groupKey = memberId ?: "__unassigned__"
                        val expanded = groupKey in expandedGroups

                        Surface(
                            color = LifeSurface,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .28f)),
                            modifier = Modifier.fillMaxWidth().clickable {
                                expandedGroups = if (expanded) expandedGroups - groupKey else expandedGroups + groupKey
                            }
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.width(3.dp).height(36.dp).clip(RoundedCornerShape(99.dp)).background(accent))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(memberName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (personEvents.size == 1) "1 aktivitet" else "${personEvents.size} aktiviteter",
                                        color = LifeMuted,
                                        fontSize = 10.sp
                                    )
                                }
                                Text(if (expanded) "Dölj" else "Visa", color = accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (expanded) {
                            personEvents.forEachIndexed { index, event ->
                                val title = event.title.removePrefix("🌈").removePrefix("🧺").trim()
                                val timeText = event.endTime?.takeIf { it.isNotBlank() }?.let { "${event.time}–$it" }
                                    ?: event.time.ifBlank { "Hela dagen" }
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(timeText, color = LifeMuted, fontSize = 11.sp, modifier = Modifier.width(78.dp))
                                    Text(
                                        title,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (index < personEvents.lastIndex) HorizontalDivider(color = LifeDivider, thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}'''


changed_files = []
if patch_exact_calendar():
    changed_files.append('ExactCalendarScreen.kt')
if replace_composable(ROOT / 'MinimalCalendarScreen.kt', 'PremiumAgenda', MINIMAL_AGENDA):
    changed_files.append('MinimalCalendarScreen.kt')
if replace_composable(ROOT / 'PersonalLayoutScreen.kt', 'PersonalTodayAgenda', PERSONAL_AGENDA):
    changed_files.append('PersonalLayoutScreen.kt')
if replace_composable(ROOT / 'RunningLifeDashboard.kt', 'LifeAgendaCard', RUNNING_AGENDA):
    changed_files.append('RunningLifeDashboard.kt')

print('Grouped day overview applied to all layout modes:', ', '.join(changed_files) if changed_files else 'already current')
