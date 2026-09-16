from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


# 1) Clean mode: keep Family and expose Location as the fifth bottom-right destination.
main_path = ROOT / "app/src/main/java/se/familjekalender/app/MainActivity.kt"
main = main_path.read_text()
main = replace_once(
    main,
    '''    val mappedSelection = when (selected) {
        0 -> 0
        1 -> 1
        2 -> 2
        3, 5 -> 3
        else -> -1
    }''',
    '''    val mappedSelection = when (selected) {
        0 -> 0
        1 -> 1
        2 -> 2
        3 -> 3
        5 -> 4
        else -> -1
    }''',
    "clean nav selection mapping",
)
main = replace_once(
    main,
    '''            Triple(2, Icons.Default.CheckCircle, "To-do"),
            Triple(3, Icons.Default.People, "Familj")''',
    '''            Triple(2, Icons.Default.CheckCircle, "To-do"),
            Triple(3, Icons.Default.People, "Familj"),
            Triple(5, Icons.Default.LocationOn, "Plats")''',
    "clean nav location item",
)
main_path.write_text(main)


# 2) First run identity: a previously stored GPS identity must not silently skip
#    the explicit 'Vem är du?' confirmation. The new versioned flag intentionally
#    forces the picker once on existing installations and once per family.
identity_path = ROOT / "app/src/main/java/se/familjekalender/app/FirstRunIdentityGate.kt"
identity = identity_path.read_text()
identity = replace_once(
    identity,
    '''private const val DEVICE_MEMBER_KEY = "device_member_id"''',
    '''private const val DEVICE_MEMBER_KEY = "device_member_id"
private const val IDENTITY_CONFIRMATION_VERSION = "20260916_v2"

private fun identityConfirmationKey(sessionId: String) =
    "identity_prompt_confirmed_${IDENTITY_CONFIRMATION_VERSION}_$sessionId"''',
    "identity confirmation key",
)
identity = replace_once(
    identity,
    '''    val locationPrefs = remember { context.getSharedPreferences(LOCATION_PREFS_IDENTITY, Context.MODE_PRIVATE) }

    var members by remember(session.id) { mutableStateOf<List<SyncMember>>(emptyList()) }''',
    '''    val locationPrefs = remember { context.getSharedPreferences(LOCATION_PREFS_IDENTITY, Context.MODE_PRIVATE) }
    val confirmationKey = remember(session.id) { identityConfirmationKey(session.id) }

    var members by remember(session.id) { mutableStateOf<List<SyncMember>>(emptyList()) }''',
    "identity confirmation key state",
)
identity = replace_once(
    identity,
    '''    var selectedMemberId by remember(session.id) {
        mutableStateOf(
            mainPrefs.getString(DEVICE_MEMBER_KEY, null)
                ?: locationPrefs.getString(DEVICE_MEMBER_KEY, null)
        )
    }

    fun persistIdentity(memberId: String) {
        selectedMemberId = memberId
        mainPrefs.edit().putString(DEVICE_MEMBER_KEY, memberId).apply()
        locationPrefs.edit().putString(DEVICE_MEMBER_KEY, memberId).apply()
    }''',
    '''    var selectedMemberId by remember(session.id) {
        mutableStateOf(
            mainPrefs.getString(DEVICE_MEMBER_KEY, null)
                ?: locationPrefs.getString(DEVICE_MEMBER_KEY, null)
        )
    }
    var identityConfirmed by remember(session.id) {
        mutableStateOf(mainPrefs.getBoolean(confirmationKey, false))
    }

    fun persistIdentity(memberId: String, confirmedByUser: Boolean = false) {
        selectedMemberId = memberId
        mainPrefs.edit().putString(DEVICE_MEMBER_KEY, memberId).apply()
        locationPrefs.edit().putString(DEVICE_MEMBER_KEY, memberId).apply()
        if (confirmedByUser) {
            identityConfirmed = true
            mainPrefs.edit().putBoolean(confirmationKey, true).apply()
        }
    }''',
    "identity persistence",
)
identity = replace_once(
    identity,
    '''    val chosenIsValid = selectedMemberId != null && members.any { it.id == selectedMemberId }''',
    '''    val chosenIsValid = identityConfirmed && selectedMemberId != null && members.any { it.id == selectedMemberId }''',
    "identity gate validity",
)
identity = replace_once(
    identity,
    '''                                        .clickable { persistIdentity(member.id) },''',
    '''                                        .clickable { persistIdentity(member.id, confirmedByUser = true) },''',
    "identity member click",
)
identity = replace_once(
    identity,
    '''                                        persistIdentity(created.id)''',
    '''                                        persistIdentity(created.id, confirmedByUser = true)''',
    "identity created member",
)
identity_path.write_text(identity)


# 3) 'Behöver din uppmärksamhet': make the whole card tappable and show every
#    detected issue in a dedicated detail dialog instead of only the first few.
assistant_path = ROOT / "app/src/main/java/se/familjekalender/app/FamilyAssistantScreen.kt"
assistant = assistant_path.read_text()
assistant = replace_once(
    assistant,
    '''    var selectedMember by remember { mutableStateOf<SyncMember?>(null) }
    var todayPlanExpanded by remember { mutableStateOf(false) }''',
    '''    var selectedMember by remember { mutableStateOf<SyncMember?>(null) }
    var todayPlanExpanded by remember { mutableStateOf(false) }
    var showAttentionDetails by remember { mutableStateOf(false) }''',
    "attention dialog state",
)
assistant = replace_once(
    assistant,
    '''                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(13.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Behöver din uppmärksamhet", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                            Text("$attentionCount", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }''',
    '''                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().clickable { showAttentionDetails = true }
                ) {
                    Column(Modifier.padding(13.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Behöver din uppmärksamhet", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                            Text("$attentionCount  ›", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }''',
    "attention card click",
)
anchor = '''    popup?.let { selected ->
        AssistantDetailPopup('''
if anchor not in assistant:
    raise RuntimeError("attention dialog insertion anchor missing")
attention_dialog = '''    if (showAttentionDetails) {
        val attentionItems = buildList {
            conflicts.forEach { add("Krock" to it) }
            planning.forEach { add("Planering" to it) }
            actions.forEach { add("Förslag" to it) }
        }
        AlertDialog(
            onDismissRequest = { showAttentionDetails = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF171A20),
            title = {
                Column {
                    Text("Behöver din uppmärksamhet", fontWeight = FontWeight.Bold)
                    Text("${attentionItems.size} saker att gå igenom", color = Muted, fontSize = 12.sp)
                }
            },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    attentionItems.forEachIndexed { index, (kind, detail) ->
                        Surface(
                            color = when (kind) {
                                "Krock" -> MaterialTheme.colorScheme.error.copy(alpha = .09f)
                                "Förslag" -> MaterialTheme.colorScheme.primary.copy(alpha = .09f)
                                else -> Color.White.copy(alpha = .045f)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("${index + 1}. $kind", color = if (kind == "Krock") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(3.dp))
                                Text(detail, color = Color.White, fontSize = 13.sp)
                                if (kind != "Förslag") {
                                    Spacer(Modifier.height(5.dp))
                                    Text("Åtgärd: ${weekActionSuggestion(detail)}", color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAttentionDetails = false }) { Text("Stäng") } }
        )
    }

'''
assistant = assistant.replace(anchor, attention_dialog + anchor, 1)
assistant_path.write_text(assistant)


# 4) Personal mode: direct on-screen widget editing. Widgets can be selected,
#    resized between half/full width, reordered, removed and added without going
#    back to Settings. Existing profile/module storage remains compatible.
personal_path = ROOT / "app/src/main/java/se/familjekalender/app/PersonalLayoutScreen.kt"
personal = personal_path.read_text()
personal = replace_once(
    personal,
    '''import androidx.compose.foundation.background''',
    '''import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background''',
    "personal border import",
)
personal = replace_once(
    personal,
    '''    private fun profileNameKey(profile: Int) = "personal_profile_${profile}_name"
''',
    '''    private fun profileNameKey(profile: Int) = "personal_profile_${profile}_name"
    private fun widgetWidthKey(profile: Int, module: PersonalCalendarModule) =
        "personal_profile_${profile}_widget_width_${module.name}"
''',
    "personal width key",
)
personal = replace_once(
    personal,
    '''    fun saveModules(prefs: SharedPreferences, profile: Int, modules: List<PersonalCalendarModule>) {
        prefs.edit().putString(profileKey(profile), modules.distinct().joinToString(",") { it.name }).apply()
    }
}''',
    '''    fun saveModules(prefs: SharedPreferences, profile: Int, modules: List<PersonalCalendarModule>) {
        prefs.edit().putString(profileKey(profile), modules.distinct().joinToString(",") { it.name }).apply()
    }

    fun widgetWidth(prefs: SharedPreferences, profile: Int, module: PersonalCalendarModule): Int =
        prefs.getInt(widgetWidthKey(profile.coerceIn(1, 3), module), 2).coerceIn(1, 2)

    fun saveWidgetWidth(prefs: SharedPreferences, profile: Int, module: PersonalCalendarModule, columns: Int) {
        prefs.edit().putInt(widgetWidthKey(profile.coerceIn(1, 3), module), columns.coerceIn(1, 2)).apply()
    }
}''',
    "personal width storage",
)
personal = personal.replace(
    'Text("Bygg kalendern som du vill ha den. Välj vilka delar som ska visas, ändra ordningen och spara upp till tre egna layouter.", color = Muted, fontSize = 12.sp)',
    'Text("Välj vilka delar som ska finnas i profilen. På själva Personligt-sidan kan du sedan trycka Redigera och ändra storlek, ordning, lägga till eller ta bort widgetar direkt.", color = Muted, fontSize = 12.sp)',
    1,
)

start_marker = '@Composable\nfun PersonalCalendarScreen('
end_marker = '@Composable\nprivate fun PersonalTodayAgenda('
start = personal.find(start_marker)
end = personal.find(end_marker, start)
if start < 0 or end < 0:
    raise RuntimeError("personal screen replacement markers missing")

new_personal_screen = r'''@Composable
fun PersonalCalendarScreen(
    session: FamilySession,
    prefs: SharedPreferences,
    profile: Int,
    revision: Int,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    shopping: List<SyncShoppingItem>,
    palette: SeasonPalette,
    themeMode: ThemeMode,
    onAdd: () -> Unit,
    onRefresh: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()
    var modules by remember(profile, revision) {
        mutableStateOf(PersonalLayoutStore.modules(prefs, profile))
    }
    val profileName = remember(profile, revision) { PersonalLayoutStore.profileName(prefs, profile) }
    var editMode by remember(profile) { mutableStateOf(false) }
    var selectedModule by remember(profile) { mutableStateOf<PersonalCalendarModule?>(null) }
    var showAddWidget by remember(profile) { mutableStateOf(false) }
    var widthRevision by remember(profile) { mutableIntStateOf(0) }

    fun persistModules(updated: List<PersonalCalendarModule>) {
        modules = updated.distinct()
        PersonalLayoutStore.saveModules(prefs, profile, modules)
        if (selectedModule !in modules) selectedModule = null
    }

    fun moveModule(module: PersonalCalendarModule, delta: Int) {
        val from = modules.indexOf(module)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, modules.lastIndex)
        if (from == to) return
        val updated = modules.toMutableList()
        updated.removeAt(from)
        updated.add(to, module)
        persistModules(updated)
    }

    val widths = remember(modules, profile, widthRevision) {
        modules.associateWith { module -> PersonalLayoutStore.widgetWidth(prefs, profile, module) }
    }
    val widgetRows = remember(modules, widths) {
        buildList<List<PersonalCalendarModule>> {
            var pendingHalf: PersonalCalendarModule? = null
            modules.forEach { module ->
                if (widths[module] == 2) {
                    pendingHalf?.let { add(listOf(it)); pendingHalf = null }
                    add(listOf(module))
                } else if (pendingHalf == null) {
                    pendingHalf = module
                } else {
                    add(listOf(pendingHalf!!, module))
                    pendingHalf = null
                }
            }
            pendingHalf?.let { add(listOf(it)) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                if (profileName.isNotBlank()) {
                    Text(profileName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                }
                Text(
                    if (editMode) "Tryck på en widget för att ändra den" else "${modules.size} aktiva widgetar",
                    color = Muted,
                    fontSize = 11.sp
                )
            }
            if (editMode) {
                TextButton(onClick = { editMode = false; selectedModule = null; showAddWidget = false }) {
                    Text("Klar", fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(onClick = { editMode = true }) { Text("Redigera") }
            }
        }

        if (editMode) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = .08f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Text(
                    "Välj en widget. Du kan göra den halv- eller fullbredd, flytta den, ta bort den eller lägga till nya widgetar.",
                    modifier = Modifier.padding(11.dp),
                    color = Muted,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        widgetRows.forEach { rowModules ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                rowModules.forEach { module ->
                    val width = widths[module] ?: 2
                    val itemModifier = if (rowModules.size == 2 || width == 1) {
                        Modifier.weight(1f)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                    PersonalEditableWidget(
                        module = module,
                        modifier = itemModifier,
                        selected = editMode && selectedModule == module,
                        editMode = editMode,
                        widthColumns = width,
                        canMoveUp = modules.indexOf(module) > 0,
                        canMoveDown = modules.indexOf(module) in 0 until modules.lastIndex,
                        onSelect = { selectedModule = module },
                        onToggleWidth = {
                            val next = if (width == 2) 1 else 2
                            PersonalLayoutStore.saveWidgetWidth(prefs, profile, module, next)
                            widthRevision++
                        },
                        onMoveUp = { moveModule(module, -1) },
                        onMoveDown = { moveModule(module, 1) },
                        onRemove = { persistModules(modules - module) }
                    ) {
                        when (module) {
                            PersonalCalendarModule.ASSISTANT ->
                                FamilyAssistantCard(session, events, members, shopping, onAdd)

                            PersonalCalendarModule.WEEK ->
                                WeekOverviewCard(events, members)

                            PersonalCalendarModule.AUTOPILOT ->
                                FamilyAutopilotCard(events, members)

                            PersonalCalendarModule.TODAY ->
                                PersonalTodayAgenda(selectedDate, events, members)

                            PersonalCalendarModule.CALENDAR ->
                                Box(Modifier.fillMaxWidth().height(590.dp)) {
                                    ExactCalendarScreen(
                                        selectedDate,
                                        onSelectDate,
                                        events,
                                        members,
                                        palette,
                                        themeMode,
                                        onAdd = onAdd,
                                        onAddLaundry = onAdd,
                                        addMenuRequest = 0
                                    )
                                }

                            PersonalCalendarModule.RECURRING ->
                                RecurringLifeCard(session = session, events = events) {
                                    scope.launch { onRefresh() }
                                }

                            PersonalCalendarModule.RUNNING ->
                                RunningProgressCard(
                                    session = session,
                                    members = members.filter { it.id != ALL_FAMILY_MEMBER_ID },
                                    events = events,
                                    onChanged = { onRefresh() }
                                )
                        }
                    }
                }
                if (rowModules.size == 1 && (widths[rowModules.first()] ?: 2) == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        if (editMode) {
            OutlinedButton(
                onClick = { showAddWidget = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Lägg till widget")
            }
        }

        if (modules.isEmpty() && !editMode) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Den här profilen är tom.", color = Muted)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { editMode = true; showAddWidget = true }) {
                        Text("Lägg till widget")
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }

    if (showAddWidget) {
        val available = PersonalCalendarModule.values().filter { it !in modules }
        AlertDialog(
            onDismissRequest = { showAddWidget = false },
            title = { Text("Lägg till widget") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (available.isEmpty()) {
                        Text("Alla widgetar är redan tillagda.", color = Muted)
                    } else {
                        available.forEach { module ->
                            Surface(
                                color = Color.White.copy(alpha = .04f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    persistModules(modules + module)
                                    selectedModule = module
                                    showAddWidget = false
                                }
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(module.label, fontWeight = FontWeight.SemiBold)
                                    Text(module.description, color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAddWidget = false }) { Text("Stäng") } }
        )
    }
}

@Composable
private fun PersonalEditableWidget(
    module: PersonalCalendarModule,
    modifier: Modifier,
    selected: Boolean,
    editMode: Boolean,
    widthColumns: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onToggleWidth: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else if (editMode) BorderStroke(1.dp, Color.White.copy(alpha = .12f)) else null,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (selected) {
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .10f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(module.label, modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Flytta upp", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Flytta ner", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Ta bort", modifier = Modifier.size(18.dp))
                            }
                        }
                        OutlinedButton(
                            onClick = onToggleWidth,
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(if (widthColumns == 2) "Storlek: full bredd · tryck för halv" else "Storlek: halv bredd · tryck för full", fontSize = 10.sp)
                        }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (editMode) Modifier.clickable(onClick = onSelect) else Modifier)
            ) {
                content()
            }
        }
    }
}

'''
personal = personal[:start] + new_personal_screen + personal[end:]
personal_path.write_text(personal)

print("Requested UI patch applied successfully")
