from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
text = path.read_text(encoding='utf-8')
old = '''                        3 -> EditableFamilyScreen(
                            members.filter { it.id != ALL_FAMILY_MEMBER_ID },
                            events,
                            { name, role ->
                                scope.launch {
                                    val realMemberCount = members.count { it.id != ALL_FAMILY_MEMBER_ID }
                                    SupabaseSync.addMember(session, name, role, MemberColors[realMemberCount % MemberColors.size])
                                    refresh()
                                }
                            },
                            { member, color ->
                                scope.launch {
                                    SupabaseSync.updateMemberColor(session, member.id, color)
                                    refresh()
                                }
                            },
                            { event, title, date, time ->
                                scope.launch {
                                    SupabaseSync.updateEvent(session, event.id, title, date, time, event.endTime, event.memberId)
                                    refresh()
                                    FamilyCalendarWidget.enqueueRefresh(context)
                                }
                            },
                            { event ->
                                scope.launch {
                                    deleteCalendarEventsDirect(session, listOf(event.id))
                                    refresh()
                                    FamilyCalendarWidget.enqueueRefresh(context)
                                }
                            }
                        )
'''
new = '''                        3 -> {
                            RunningProgressCard(
                                session = session,
                                members = members.filter { it.id != ALL_FAMILY_MEMBER_ID },
                                events = events,
                                onChanged = {
                                    refresh()
                                    FamilyCalendarWidget.enqueueRefresh(context)
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                            EditableFamilyScreen(
                                members.filter { it.id != ALL_FAMILY_MEMBER_ID },
                                events,
                                { name, role ->
                                    scope.launch {
                                        val realMemberCount = members.count { it.id != ALL_FAMILY_MEMBER_ID }
                                        SupabaseSync.addMember(session, name, role, MemberColors[realMemberCount % MemberColors.size])
                                        refresh()
                                    }
                                },
                                { member, color ->
                                    scope.launch {
                                        SupabaseSync.updateMemberColor(session, member.id, color)
                                        refresh()
                                    }
                                },
                                { event, title, date, time ->
                                    scope.launch {
                                        SupabaseSync.updateEvent(session, event.id, title, date, time, event.endTime, event.memberId)
                                        refresh()
                                        FamilyCalendarWidget.enqueueRefresh(context)
                                    }
                                },
                                { event ->
                                    scope.launch {
                                        deleteCalendarEventsDirect(session, listOf(event.id))
                                        refresh()
                                        FamilyCalendarWidget.enqueueRefresh(context)
                                    }
                                }
                            )
                        }
'''
if new in text:
    print('Running progress integration already applied')
elif old not in text:
    raise SystemExit('Target family block not found')
else:
    path.write_text(text.replace(old, new, 1), encoding='utf-8')
    print('Running progress integration applied')
