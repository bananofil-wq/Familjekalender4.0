from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/se/familjekalender/app/MainActivity.kt"
RUNNING = ROOT / "app/src/main/java/se/familjekalender/app/RunningLifeDashboard.kt"
PERSONAL = ROOT / "app/src/main/java/se/familjekalender/app/PersonalLayoutScreen.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"Could not find patch target: {label}")
    return text.replace(old, new, 1)


main = MAIN.read_text()
main = replace_once(
    main,
    "internal val CardBg = LuxurySurface\ninternal val Purple = Color(0xFFB47CFF)\ninternal val SoftPurple = LuxurySurfaceHigh\ninternal val Muted = LuxuryTextMuted",
    "internal val CardBg = PremiumGlass\ninternal val Purple = PremiumPurpleBright\ninternal val SoftPurple = PremiumGlassRaised\ninternal val Muted = PremiumMuted",
    "shared premium palette",
)

personal_old = '''                        UiLayoutMode.PERSONAL -> PersonalCalendarScreen(
                            session = session,
                            prefs = appPrefs,
                            profile = personalProfile,
                            revision = personalLayoutRevision,
                            selectedDate = selectedDate,
                            onSelectDate = { selectedDate = it },
                            events = events,
                            members = members,
                            shopping = shopping,
                            palette = palette,
                            themeMode = themeMode,
                            onAdd = {
                                addEventInitialTitle = ""
                                showAddEvent = true
                            },
                            onRefresh = {
                                refresh()
                                FamilyCalendarWidget.enqueueRefresh(context)
                            }
                        )'''
personal_new = '''                        UiLayoutMode.PERSONAL -> PremiumModeBackground {
                            PersonalCalendarScreen(
                                session = session,
                                prefs = appPrefs,
                                profile = personalProfile,
                                revision = personalLayoutRevision,
                                selectedDate = selectedDate,
                                onSelectDate = { selectedDate = it },
                                events = events,
                                members = members,
                                shopping = shopping,
                                palette = palette,
                                themeMode = themeMode,
                                onAdd = {
                                    addEventInitialTitle = ""
                                    showAddEvent = true
                                },
                                onRefresh = {
                                    refresh()
                                    FamilyCalendarWidget.enqueueRefresh(context)
                                }
                            )
                        }'''
main = replace_once(main, personal_old, personal_new, "personal premium background")

full_old = '''                        UiLayoutMode.FULL -> Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            FamilyAssistantCard(session, events, members, shopping) { assistantAddRequest++ }
                            WeekOverviewCard(events, members)
                            FamilyAutopilotCard(events, members)
                            RecurringLifeCard(session = session, events = events) { scope.launch { refresh() } }
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
                        }'''
full_new = '''                        UiLayoutMode.FULL -> PremiumModeBackground {
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                PremiumModeHeader(
                                    title = "Familjekalender",
                                    subtitle = "Fullständig familjeöversikt",
                                    onSettings = { selectedTab = 4 }
                                )
                                Spacer(Modifier.height(12.dp))
                                FamilyAssistantCard(session, events, members, shopping) { assistantAddRequest++ }
                                Spacer(Modifier.height(10.dp))
                                WeekOverviewCard(events, members)
                                Spacer(Modifier.height(10.dp))
                                FamilyAutopilotCard(events, members)
                                Spacer(Modifier.height(10.dp))
                                RecurringLifeCard(session = session, events = events) { scope.launch { refresh() } }
                                Spacer(Modifier.height(10.dp))
                                PremiumGlassPanel(Modifier.fillMaxWidth()) {
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
                                Spacer(Modifier.height(16.dp))
                            }
                        }'''
main = replace_once(main, full_old, full_new, "full premium dashboard")

running_old = '''                        UiLayoutMode.RUNNING -> RunningLifeDashboard(
                            session = session,
                            selectedDate = selectedDate,
                            onSelectDate = { selectedDate = it },
                            events = events,
                            members = members,
                            onAdd = {
                                addEventInitialTitle = ""
                                showAddEvent = true
                            },
                            onOpenSettings = { selectedTab = 4 },
                            onRefresh = {
                                refresh()
                                FamilyCalendarWidget.enqueueRefresh(context)
                            }
                        )'''
running_new = '''                        UiLayoutMode.RUNNING -> PremiumModeBackground {
                            RunningLifeDashboard(
                                session = session,
                                selectedDate = selectedDate,
                                onSelectDate = { selectedDate = it },
                                events = events,
                                members = members,
                                onAdd = {
                                    addEventInitialTitle = ""
                                    showAddEvent = true
                                },
                                onOpenSettings = { selectedTab = 4 },
                                onRefresh = {
                                    refresh()
                                    FamilyCalendarWidget.enqueueRefresh(context)
                                }
                            )
                        }'''
main = replace_once(main, running_old, running_new, "sport premium background")

main = main.replace(
    "NavigationBar(containerColor = LuxuryBackground.copy(alpha = .985f), tonalElevation = 0.dp)",
    "NavigationBar(containerColor = PremiumGlassRaised.copy(alpha = .96f), tonalElevation = 0.dp)",
)
main = main.replace(
    "indicatorColor = LuxurySurfaceHigh",
    "indicatorColor = PremiumPurple.copy(alpha = .18f)",
)
MAIN.write_text(main)

running = RUNNING.read_text()
running = running.replace("private val LifeBg = Color(0xFF0B0B10)", "private val LifeBg = Color.Transparent")
running = running.replace("private val LifeSurface = Color(0xFF17171F)", "private val LifeSurface = PremiumGlass")
running = running.replace("private val LifeSurfaceRaised = Color(0xFF1D1D26)", "private val LifeSurfaceRaised = PremiumGlassRaised")
running = running.replace("private val LifePurple = Color(0xFFA66CFF)", "private val LifePurple = PremiumPurpleBright")
running = running.replace("private val LifeMuted = Color(0xFFAAA8B7)", "private val LifeMuted = PremiumMuted")
running = running.replace("private val LifeDivider = Color(0xFF292933)", "private val LifeDivider = PremiumBorder")

sport_header_old = '''        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text("Löpning & vardag", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (focus == LifeFocus.EVERYDAY) "Det viktigaste i veckan, utan brus."
                    else "Träning, utveckling och historik i fokus.",
                    color = LifeMuted,
                    fontSize = 13.sp
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Inställningar", tint = Color(0xFFD1CFDC))
            }
        }

        Spacer(Modifier.height(16.dp))'''
sport_header_new = '''        PremiumModeHeader(
            title = "Sportläge",
            subtitle = if (focus == LifeFocus.EVERYDAY) "Löpning och vardag i balans" else "Träning, utveckling och historik",
            onAdd = onAdd,
            onSettings = onOpenSettings
        )

        Spacer(Modifier.height(14.dp))'''
running = replace_once(running, sport_header_old, sport_header_new, "sport premium header")

sport_button_old = '''        Spacer(Modifier.height(14.dp))

        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LifePurple)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Lägg till aktivitet", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(12.dp))'''
sport_button_new = '''        Spacer(Modifier.height(18.dp))'''
running = replace_once(running, sport_button_old, sport_button_new, "remove duplicate sport add button")
RUNNING.write_text(running)

personal = PERSONAL.read_text()
personal_header_old = '''    Column(
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
            if (editMode) {'''
personal_header_new = '''    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp)
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            PremiumModeHeader(
                title = profileName.ifBlank { "Familjekalender" },
                subtitle = "Personligt läge",
                onAdd = onAdd
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (editMode) "Tryck på en widget för att ändra den" else "${modules.size} aktiva widgetar",
                    color = Muted,
                    fontSize = 11.sp
                )
            }
            if (editMode) {'''
personal = replace_once(personal, personal_header_old, personal_header_new, "personal premium header")
personal = personal.replace(
    "colors = CardDefaults.cardColors(containerColor = CardBg)",
    "colors = CardDefaults.cardColors(containerColor = PremiumGlass)",
)
personal = personal.replace(
    "color = Color.White.copy(alpha = .04f)",
    "color = PremiumGlassSoft",
)
PERSONAL.write_text(personal)

print("Applied premium design system to Fullständigt, Sportläge and Personligt.")
