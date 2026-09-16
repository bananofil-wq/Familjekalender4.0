from pathlib import Path

p = Path('app/src/main/java/se/familjekalender/app/MinimalCalendarScreen.kt')
s = p.read_text()
old = '''                        if (isSelected) {
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .graphicsLayer { scaleX = scale; scaleY = scale }
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = .18f))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .62f), CircleShape)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                date.dayOfMonth.toString(),
                                color = when {
                                    isSelected -> LuxuryText
                                    inMonth -> LuxuryText.copy(alpha = .96f)
                                    else -> LuxuryTextMuted.copy(alpha = .42f)
                                },
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Spacer(Modifier.height(5.dp))
                            when {
                                birthday -> Box(Modifier.size(17.dp), contentAlignment = Alignment.Center) {
                                    BirthdayRainbowIcon(Modifier.size(17.dp))
                                }
                                dayEvents.isNotEmpty() -> Row(
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    dayEvents.take(3).forEach { event ->
                                        val dotColor = members[event.memberId]?.let { Color(it.colorArgb.toInt()) }
                                            ?: MaterialTheme.colorScheme.primary
                                        Box(Modifier.size(5.dp).clip(CircleShape).background(dotColor))
                                    }
                                }
                                else -> Spacer(Modifier.height(5.dp))
                            }
                        }'''
new = '''                        if (isSelected) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .graphicsLayer { scaleX = scale; scaleY = scale }
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = .18f))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .62f), CircleShape)
                            )
                        }
                        Column(
                            modifier = if (isSelected) Modifier.size(48.dp) else Modifier,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = if (isSelected) Arrangement.Center else Arrangement.Top
                        ) {
                            Text(
                                date.dayOfMonth.toString(),
                                color = when {
                                    isSelected -> LuxuryText
                                    inMonth -> LuxuryText.copy(alpha = .96f)
                                    else -> LuxuryTextMuted.copy(alpha = .42f)
                                },
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Spacer(Modifier.height(if (isSelected) 2.dp else 5.dp))
                            when {
                                birthday -> Box(
                                    Modifier.size(if (isSelected) 13.dp else 17.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BirthdayRainbowIcon(Modifier.size(if (isSelected) 13.dp else 17.dp))
                                }
                                dayEvents.isNotEmpty() -> Row(
                                    horizontalArrangement = Arrangement.spacedBy(if (isSelected) 2.dp else 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    dayEvents.take(3).forEach { event ->
                                        val dotColor = members[event.memberId]?.let { Color(it.colorArgb.toInt()) }
                                            ?: MaterialTheme.colorScheme.primary
                                        Box(Modifier.size(if (isSelected) 4.dp else 5.dp).clip(CircleShape).background(dotColor))
                                    }
                                }
                                else -> Spacer(Modifier.height(if (isSelected) 4.dp else 5.dp))
                            }
                        }'''
if old not in s:
    raise SystemExit('Target block not found')
p.write_text(s.replace(old, new, 1))
