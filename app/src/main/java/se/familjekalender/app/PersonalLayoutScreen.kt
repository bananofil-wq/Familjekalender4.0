package se.familjekalender.app

import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class PersonalCalendarModule(val label: String, val description: String) {
    ASSISTANT("Assistent", "Dagens plan, krockar, inköp och att göra"),
    WEEK("Veckoöversikt", "Belastning och planeringspunkter för veckan"),
    AUTOPILOT("Familjeautopilot", "Förslag baserade på familjens kalender"),
    TODAY("Dagens agenda", "Kompakt lista med dagens aktiviteter"),
    CALENDAR("Månadskalender", "Den fullständiga månadskalendern"),
    RECURRING("Scheman", "Återkommande arbets-, skol- och löpscheman"),
    RUNNING("Löpning", "Löpprogression, planering och historik")
}

object PersonalLayoutStore {
    private const val ACTIVE_PROFILE = "personal_active_profile"
    private fun profileKey(profile: Int) = "personal_profile_${profile}_modules"
    private fun profileNameKey(profile: Int) = "personal_profile_${profile}_name"
    private fun widgetWidthKey(profile: Int, module: PersonalCalendarModule) =
        "personal_profile_${profile}_widget_width_${module.name}"

    private val defaults = mapOf(
        1 to listOf(PersonalCalendarModule.ASSISTANT, PersonalCalendarModule.TODAY, PersonalCalendarModule.CALENDAR),
        2 to listOf(PersonalCalendarModule.WEEK, PersonalCalendarModule.CALENDAR, PersonalCalendarModule.AUTOPILOT),
        3 to listOf(PersonalCalendarModule.CALENDAR, PersonalCalendarModule.RUNNING, PersonalCalendarModule.RECURRING)
    )

    fun activeProfile(prefs: SharedPreferences): Int = prefs.getInt(ACTIVE_PROFILE, 1).coerceIn(1, 3)

    fun setActiveProfile(prefs: SharedPreferences, profile: Int) {
        prefs.edit().putInt(ACTIVE_PROFILE, profile.coerceIn(1, 3)).apply()
    }

    fun profileName(prefs: SharedPreferences, profile: Int): String =
        prefs.getString(profileNameKey(profile.coerceIn(1, 3)), "").orEmpty()

    fun saveProfileName(prefs: SharedPreferences, profile: Int, name: String) {
        prefs.edit().putString(profileNameKey(profile.coerceIn(1, 3)), name).apply()
    }

    fun modules(prefs: SharedPreferences, profile: Int): List<PersonalCalendarModule> {
        val raw = prefs.getString(profileKey(profile), null)
        if (raw.isNullOrBlank()) return defaults[profile].orEmpty()
        return raw.split(',')
            .mapNotNull { value -> runCatching { PersonalCalendarModule.valueOf(value) }.getOrNull() }
            .distinct()
            .ifEmpty { defaults[profile].orEmpty() }
    }

    fun saveModules(prefs: SharedPreferences, profile: Int, modules: List<PersonalCalendarModule>) {
        prefs.edit().putString(profileKey(profile), modules.distinct().joinToString(",") { it.name }).apply()
    }

    fun widgetWidth(prefs: SharedPreferences, profile: Int, module: PersonalCalendarModule): Int =
        prefs.getInt(widgetWidthKey(profile.coerceIn(1, 3), module), 2).coerceIn(1, 2)

    fun saveWidgetWidth(prefs: SharedPreferences, profile: Int, module: PersonalCalendarModule, columns: Int) {
        prefs.edit().putInt(widgetWidthKey(profile.coerceIn(1, 3), module), columns.coerceIn(1, 2)).apply()
    }
}

@Composable
fun PersonalLayoutEditor(
    prefs: SharedPreferences,
    activeProfile: Int,
    revision: Int,
    onActiveProfileChanged: (Int) -> Unit,
    onChanged: () -> Unit
) {
    var modules by remember(activeProfile, revision) {
        mutableStateOf(PersonalLayoutStore.modules(prefs, activeProfile))
    }
    var profileName by remember(activeProfile, revision) {
        mutableStateOf(PersonalLayoutStore.profileName(prefs, activeProfile))
    }

    fun persist(updated: List<PersonalCalendarModule>) {
        modules = updated
        PersonalLayoutStore.saveModules(prefs, activeProfile, updated)
        onChanged()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Personligt läge", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Välj vilka delar som ska finnas i profilen. På själva Personligt-sidan kan du sedan trycka Redigera och ändra storlek, ordning, lägga till eller ta bort widgetar direkt.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..3).forEach { profile ->
                    val savedName = PersonalLayoutStore.profileName(prefs, profile)
                    val selectorLabel = savedName.ifBlank { profile.toString() }
                    if (profile == activeProfile) {
                        Button(
                            onClick = { onActiveProfileChanged(profile) },
                            modifier = Modifier.weight(1f)
                        ) { Text(selectorLabel, maxLines = 1) }
                    } else {
                        OutlinedButton(
                            onClick = { onActiveProfileChanged(profile) },
                            modifier = Modifier.weight(1f)
                        ) { Text(selectorLabel, maxLines = 1) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = profileName,
                onValueChange = { value ->
                    profileName = value
                    PersonalLayoutStore.saveProfileName(prefs, activeProfile, value)
                    onChanged()
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Namn på gränssnittet (valfritt)") },
                placeholder = { Text("Kan lämnas tomt") }
            )
            Text(
                "Om fältet lämnas tomt visas ingen rubrik i det personliga gränssnittet.",
                color = Muted,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )

            Spacer(Modifier.height(14.dp))
            Text("Aktiva moduler · i denna ordning", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))

            modules.forEachIndexed { index, module ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .04f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(module.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Position ${index + 1}", color = Muted, fontSize = 10.sp)
                        }
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val updated = modules.toMutableList()
                                    val item = updated.removeAt(index)
                                    updated.add(index - 1, item)
                                    persist(updated)
                                }
                            },
                            enabled = index > 0
                        ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Flytta upp") }
                        IconButton(
                            onClick = {
                                if (index < modules.lastIndex) {
                                    val updated = modules.toMutableList()
                                    val item = updated.removeAt(index)
                                    updated.add(index + 1, item)
                                    persist(updated)
                                }
                            },
                            enabled = index < modules.lastIndex
                        ) { Icon(Icons.Default.ArrowDownward, contentDescription = "Flytta ner") }
                        IconButton(onClick = { persist(modules - module) }) {
                            Icon(Icons.Default.Close, contentDescription = "Ta bort")
                        }
                    }
                }
            }

            val available = PersonalCalendarModule.values().filter { it !in modules }
            if (available.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Lägg till modul", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                available.forEach { module ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { persist(modules + module) }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledIconButton(
                            onClick = { persist(modules + module) },
                            modifier = Modifier.size(34.dp)
                        ) { Icon(Icons.Default.Add, contentDescription = "Lägg till") }
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(module.label, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Text(module.description, color = Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
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

@Composable
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
                dayEvents.forEach { event ->
                    val who = when {
                        event.memberId == ALL_FAMILY_MEMBER_ID -> "Familjen"
                        else -> memberMap[event.memberId]?.name ?: "Familjen"
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(event.time.ifBlank { "Hela dagen" }, modifier = Modifier.width(72.dp), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Column(Modifier.weight(1f)) {
                            Text(event.title, fontSize = 12.sp, color = Color.White)
                            Text(who, fontSize = 10.sp, color = Muted)
                        }
                    }
                }
            }
        }
    }
}
