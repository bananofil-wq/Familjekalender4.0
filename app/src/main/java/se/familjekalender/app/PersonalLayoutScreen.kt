package se.familjekalender.app

import android.content.SharedPreferences
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
            Text("Bygg kalendern som du vill ha den. Välj vilka delar som ska visas, ändra ordningen och spara upp till tre egna layouter.", color = Muted, fontSize = 12.sp)
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
    val modules = remember(profile, revision) { PersonalLayoutStore.modules(prefs, profile) }
    val profileName = remember(profile, revision) { PersonalLayoutStore.profileName(prefs, profile) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                if (profileName.isNotBlank()) {
                    Text(profileName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                }
                Text("${modules.size} aktiva moduler", color = Muted, fontSize = 11.sp)
            }
            FilledIconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Lägg till aktivitet")
            }
        }

        modules.forEach { module ->
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

        if (modules.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text(
                    "Den här profilen har inga moduler ännu. Lägg till dem under Inställningar → Gränssnitt → Personlig.",
                    modifier = Modifier.padding(16.dp),
                    color = Muted
                )
            }
        }
        Spacer(Modifier.height(14.dp))
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
