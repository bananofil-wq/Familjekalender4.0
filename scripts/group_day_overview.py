# Applies the grouped per-person day overview before the verified Android build. Build trigger v2.
from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
source = path.read_text()

fn_start = source.index('@Composable\nprivate fun DayOverviewPopup(')
fn_end = source.find('\n@Composable\n', fn_start + 20)
if fn_end == -1:
    raise SystemExit('Could not find end of DayOverviewPopup')

block = source[fn_start:fn_end]
if 'val groupedEvents = events.groupBy { it.memberId }' in block:
    print('Grouped day overview already applied')
    raise SystemExit(0)

loop_start_marker = '                    events.forEach { event ->\n'
loop_start = block.find(loop_start_marker)
if loop_start == -1:
    raise SystemExit('DayOverviewPopup events loop not found')

loop_end_marker = '                    }\n                }\n            }\n        },'
loop_end_anchor = block.find(loop_end_marker, loop_start)
if loop_end_anchor == -1:
    raise SystemExit('DayOverviewPopup events loop end not found')

loop_end = loop_end_anchor + len('                    }')
loop_text = block[loop_start:loop_end]
body_start = loop_text.find('\n') + 1
body = loop_text[body_start:-len('                    }')]

replacement = '''                    var expandedGroupKeys by remember(date) { mutableStateOf(emptySet<String>()) }
                    val groupedEvents = events.groupBy { it.memberId }
                    groupedEvents.forEach { (memberId, personEvents) ->
                        val groupMember = members.find { it.id == memberId }
                        val groupName = groupMember?.name
                            ?: if (memberId == ALL_FAMILY_MEMBER_ID) "Hela familjen" else "Familjen"
                        val groupColor = if (memberId == ALL_FAMILY_MEMBER_ID) {
                            Color(0xFFFFD75E)
                        } else {
                            groupMember?.let { Color(it.colorArgb.toInt()) } ?: Color(0xFF8D95A5)
                        }
                        val groupKey = "member:${memberId ?: "unassigned"}"
                        val expanded = personEvents.size == 1 || groupKey in expandedGroupKeys

                        if (personEvents.size > 1) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedGroupKeys = if (groupKey in expandedGroupKeys) {
                                            expandedGroupKeys - groupKey
                                        } else {
                                            expandedGroupKeys + groupKey
                                        }
                                    },
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF20242B),
                                border = BorderStroke(1.dp, groupColor.copy(alpha = .34f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(13.dp)
                                            .clip(CircleShape)
                                            .background(groupColor)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            groupName,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            "${personEvents.size} aktiviteter",
                                            color = Color.White.copy(alpha = .62f),
                                            fontSize = 12.sp
                                        )
                                    }
                                    Text(
                                        if (expanded) "Dölj" else "Visa alla",
                                        color = groupColor,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        if (expanded) {
                            personEvents.sortedBy { it.time }.forEach { event ->
__EVENT_BODY__                            }
                        }
                    }'''.replace('__EVENT_BODY__', body)

new_block = block[:loop_start] + replacement + block[loop_end:]
new_source = source[:fn_start] + new_block + source[fn_end:]
path.write_text(new_source)
print('Grouped day overview applied')
