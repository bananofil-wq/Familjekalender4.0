from pathlib import Path

path = Path("app/src/main/java/se/familjekalender/app/FamilyLocationScreen.kt")
s = path.read_text(encoding="utf-8")

# Local opt-in state. Notifications are off until explicitly enabled on each device.
anchor = '    var batteryVisible by remember { mutableStateOf(prefs.getBoolean("battery_visible", true)) }\n'
addition = anchor + '    var locationAlertsEnabled by remember { mutableStateOf(prefs.getBoolean("location_alerts_enabled", false)) }\n    var alertPreferenceRevision by remember { mutableIntStateOf(0) }\n'
if 'var locationAlertsEnabled by remember' not in s:
    if anchor not in s:
        raise SystemExit("Could not find location preference state anchor")
    s = s.replace(anchor, addition, 1)

# Insert master switch + per-person alert selection before arrival/departure settings.
ui_anchor = '''                val arrivalEnabled = places.isNotEmpty() && places.any { it.arrivalAlerts }\n'''
ui_block = '''                Row(\n                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),\n                    verticalAlignment = Alignment.CenterVertically\n                ) {\n                    LocationIcon(Icons.Default.Notifications)\n                    Spacer(Modifier.width(12.dp))\n                    Column(Modifier.weight(1f)) {\n                        Text("Platsnotiser", fontWeight = FontWeight.Bold, fontSize = 16.sp)\n                        Text(\n                            if (locationAlertsEnabled) "Aktiverat · välj personer nedan" else "Avstängt",\n                            color = Muted,\n                            fontSize = 13.sp\n                        )\n                    }\n                    Switch(\n                        checked = locationAlertsEnabled,\n                        onCheckedChange = { enabled ->\n                            locationAlertsEnabled = enabled\n                            prefs.edit().putBoolean("location_alerts_enabled", enabled).apply()\n                            status = if (enabled) "Platsnotiser aktiverade" else "Platsnotiser avstängda"\n                            if (enabled) ensureNotificationPermission()\n                        }\n                    )\n                }\n\n                if (locationAlertsEnabled) {\n                    Text(\n                        "Välj vilka personer du vill få ankomst- och avresenotiser om",\n                        color = Muted,\n                        fontSize = 12.sp,\n                        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)\n                    )\n                    familyMembers.forEach { member ->\n                        val memberAlerts = remember(member.id, alertPreferenceRevision) {\n                            prefs.getBoolean("location_alert_member_${member.id}", false)\n                        }\n                        Row(\n                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),\n                            verticalAlignment = Alignment.CenterVertically\n                        ) {\n                            Box(\n                                Modifier\n                                    .size(10.dp)\n                                    .clip(CircleShape)\n                                    .background(Color(member.colorArgb.toInt()))\n                            )\n                            Spacer(Modifier.width(12.dp))\n                            Column(Modifier.weight(1f)) {\n                                Text(member.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)\n                                Text("Meddela när ${member.name} kommer eller lämnar", color = Muted, fontSize = 12.sp)\n                            }\n                            Switch(\n                                checked = memberAlerts,\n                                onCheckedChange = { enabled ->\n                                    prefs.edit().putBoolean("location_alert_member_${member.id}", enabled).apply()\n                                    alertPreferenceRevision++\n                                    status = if (enabled) {\n                                        "Platsnotiser för ${member.name} aktiverade"\n                                    } else {\n                                        "Platsnotiser för ${member.name} avstängda"\n                                    }\n                                }\n                            )\n                        }\n                    }\n                }\n\n                val arrivalEnabled = places.isNotEmpty() && places.any { it.arrivalAlerts }\n'''
if '"Välj vilka personer du vill få ankomst- och avresenotiser om"' not in s:
    if ui_anchor not in s:
        raise SystemExit("Could not find location alert UI anchor")
    s = s.replace(ui_anchor, ui_block, 1)

# Enforce local master opt-in and per-person opt-in before showing any transition alert.
function_anchor = '''    if (locations.isEmpty() || places.isEmpty()) return\n    val prefs = context.getSharedPreferences("location_geofence_state_$familyId", Context.MODE_PRIVATE)\n\n    locations.forEach { location ->\n'''
function_replacement = '''    if (locations.isEmpty() || places.isEmpty()) return\n    val alertPrefs = context.getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE)\n    if (!alertPrefs.getBoolean("location_alerts_enabled", false)) return\n\n    val prefs = context.getSharedPreferences("location_geofence_state_$familyId", Context.MODE_PRIVATE)\n\n    locations.forEach { location ->\n        if (!alertPrefs.getBoolean("location_alert_member_${location.memberId}", false)) return@forEach\n'''
if 'if (!alertPrefs.getBoolean("location_alerts_enabled", false)) return' not in s:
    if function_anchor not in s:
        raise SystemExit("Could not find transition function anchor")
    s = s.replace(function_anchor, function_replacement, 1)

s = s.replace(
    'Text("Ankomst- och avresenotiser aktiveras för nya destinationer och kan ändras efteråt.", color = Muted, fontSize = 12.sp)',
    'Text("Platsnotiser är avstängda tills du själv aktiverar dem. Ankomst och avresa kan sedan styras per destination.", color = Muted, fontSize = 12.sp)'
)

path.write_text(s, encoding="utf-8")
print("Per-person location alert controls applied")
