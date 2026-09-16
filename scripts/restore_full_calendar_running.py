# Restoration helper is intentionally idempotent; this commit triggers a signed build after the source patch.
from pathlib import Path

MAIN = Path("app/src/main/java/se/familjekalender/app/MainActivity.kt")
PANEL = Path("app/src/main/java/se/familjekalender/app/RunRecorderPanel.kt")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"Could not find anchor for {label}")
    return text.replace(old, new, 1)


# --- Restore four independent calendar modes ---
text = MAIN.read_text(encoding="utf-8")
old_enum = '''enum class UiLayoutMode(val label: String, val description: String) {
    FULL("Löpning & vardag", "Veckan, dagens åtaganden och löpningen i en lugn personlig vy"),
    MINIMAL("Clean", "Ren månadskalender med en diskret markering per dag"),
    PERSONAL("Personligt", "Bygg din egen kalender med valbara delar och egen ordning")
}
'''
new_enum = '''enum class UiLayoutMode(val label: String, val description: String) {
    MINIMAL("Clean", "Ren månadskalender med en diskret markering per dag"),
    FULL("Fullständigt", "Alla översikter, familjeverktyg och den fulla kalendern"),
    RUNNING("Löpning & vardag", "Veckan, dagens åtaganden och löpningen i en lugn personlig vy"),
    PERSONAL("Personligt", "Bygg din egen kalender med valbara delar och egen ordning")
}
'''
text = replace_once(text, old_enum, new_enum, "four layout modes")

text = replace_once(
    text,
    "                        UiLayoutMode.FULL -> RunningLifeDashboard(\n",
    "                        UiLayoutMode.RUNNING -> RunningLifeDashboard(\n",
    "separate running dashboard mode",
)

full_branch = '''                        UiLayoutMode.FULL -> Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            FamilyAssistantCard(session, events, members, shopping) { assistantAddRequest++ }
                            WeekOverviewCard(events, members)
                            FamilyAutopilotCard(events, members)
                            RecurringLifeCard(session = session, events = events) { scope.launch { refresh() } }
                            RunningProgressCard(
                                session = session,
                                members = members.filter { it.id != ALL_FAMILY_MEMBER_ID },
                                events = events,
                                onChanged = {
                                    refresh()
                                    FamilyCalendarWidget.enqueueRefresh(context)
                                }
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(590.dp)
                            ) {
                                ExactCalendarScreen(
                                    selectedDate,
                                    { selectedDate = it },
                                    events,
                                    members,
                                    palette,
                                    themeMode,
                                    onAdd = {
                                        addEventInitialTitle = ""
                                        showAddEvent = true
                                    },
                                    onAddLaundry = {
                                        addEventInitialTitle = "🧺 Tvätt"
                                        showAddEvent = true
                                    },
                                    addMenuRequest = assistantAddRequest
                                )
                            }
                        }
'''
running_anchor = "                        UiLayoutMode.RUNNING -> RunningLifeDashboard(\n"
if full_branch not in text:
    if running_anchor not in text:
        raise SystemExit("Could not find running dashboard branch for Fullständigt insertion")
    text = text.replace(running_anchor, full_branch + running_anchor, 1)

old_copy = 'Text("Välj Clean, Löpning & vardag eller Personligt. I Personligt bestämmer du själv vilka delar kalendern ska visa och i vilken ordning. Valet sparas på den här telefonen.", color = Muted, fontSize = 12.sp)'
new_copy = 'Text("Välj Clean, Fullständigt, Löpning & vardag eller Personligt. I Personligt bestämmer du själv vilka delar kalendern ska visa och i vilken ordning. Valet sparas på den här telefonen.", color = Muted, fontSize = 12.sp)'
if old_copy in text:
    text = text.replace(old_copy, new_copy, 1)

if 'FULL("Fullständigt"' not in text or 'RUNNING("Löpning & vardag"' not in text:
    raise SystemExit("Layout modes were not restored correctly")
if "UiLayoutMode.FULL -> Column(" not in text or "UiLayoutMode.RUNNING -> RunningLifeDashboard(" not in text:
    raise SystemExit("Calendar mode branches were not restored correctly")

MAIN.write_text(text, encoding="utf-8")


# --- Make every locally recorded GPS route selectable for replay ---
p = PANEL.read_text(encoding="utf-8")
state_anchor = '    var errorText by remember { mutableStateOf<String?>(null) }\n'
state_line = '    var replayRunId by remember(memberId) { mutableStateOf<String?>(null) }\n'
if state_line not in p:
    if state_anchor not in p:
        raise SystemExit("Could not find run recorder state anchor")
    p = p.replace(state_anchor, state_anchor + state_line, 1)

old_history = '''            if (!isThisMemberRecording) {
                latestRun?.let { run ->
                    val km = runDistanceKm(run.points)
                    val seconds = run.durationMillis / 1000L
                    HorizontalDivider()
                    Text("Senaste inspelade rundan", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecorderStat("Distans", "%.2f km".format(Locale.US, km), Modifier.weight(1f))
                        RecorderStat("Tid", formatDuration(seconds), Modifier.weight(1f))
                        RecorderStat("Tempo", paceSecondsPerKm(km, seconds)?.let(::formatPace) ?: "–", Modifier.weight(1f))
                    }
                    if (run.points.isNotEmpty()) RunReplayMap(run, Modifier.fillMaxWidth())
                }
            }
'''
new_history = '''            if (!isThisMemberRecording) {
                val savedRuns = store.runsFor(memberId)
                if (savedRuns.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Inspelade rundor", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Öppna valfri GPS-runda och spela upp den på kartan.", color = Muted, fontSize = 11.sp)
                    savedRuns.take(12).forEach { run ->
                        val km = runDistanceKm(run.points)
                        val seconds = run.durationMillis / 1000L
                        val date = Instant.ofEpochMilli(run.startedAtMillis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        val expanded = replayRunId == run.id
                        Surface(
                            color = CardBg,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(date.toString(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(
                                            "%.2f km · %s · %s".format(
                                                Locale.US,
                                                km,
                                                formatDuration(seconds),
                                                paceSecondsPerKm(km, seconds)?.let(::formatPace) ?: "–"
                                            ),
                                            color = Muted,
                                            fontSize = 11.sp
                                        )
                                    }
                                    TextButton(onClick = { replayRunId = if (expanded) null else run.id }) {
                                        Text(if (expanded) "Stäng" else "Visa / spela upp")
                                    }
                                }
                                if (expanded && run.points.isNotEmpty()) {
                                    RunReplayMap(run, Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
'''
if old_history in p:
    p = p.replace(old_history, new_history, 1)
elif 'Text("Inspelade rundor"' not in p:
    raise SystemExit("Could not find recorded run history block")

if 'var replayRunId' not in p or 'Text("Inspelade rundor"' not in p:
    raise SystemExit("Recorded run history was not upgraded")

PANEL.write_text(p, encoding="utf-8")
print("Restored Fullständigt, kept Löpning & vardag separate, and exposed GPS replay history.")
