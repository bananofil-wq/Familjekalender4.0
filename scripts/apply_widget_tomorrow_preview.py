from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/FamilyCalendarWidgetWorker.kt')
text = path.read_text(encoding='utf-8')

old_events = '''                val todaysEvents = allEvents
                    .filter { it.date == today }
                    .sortedBy { it.time }
'''
new_events = '''                val todaysEvents = allEvents
                    .filter { it.date == today }
                    .sortedBy { it.time }
                val tomorrowsEvents = allEvents
                    .filter { it.date == today.plusDays(1) }
                    .sortedBy { it.time }
'''
if old_events not in text:
    raise SystemExit('today events target not found')
text = text.replace(old_events, new_events, 1)

old_upcoming = '''                val upcoming = todaysEvents
                    .filter { event ->
                        runCatching { LocalTime.parse(event.time) }.getOrNull()?.let { !it.isBefore(now.minusMinutes(15)) } ?: true
                    }
                    .take(3)
'''
new_upcoming = '''                val upcomingToday = todaysEvents
                    .filter { event ->
                        runCatching { LocalTime.parse(event.time) }.getOrNull()?.let { !it.isBefore(now.minusMinutes(15)) } ?: true
                    }
                    .take(4)
                val tomorrowSlots = (4 - upcomingToday.size).coerceAtLeast(0)
                val upcomingTomorrow = tomorrowsEvents.take(tomorrowSlots)
'''
if old_upcoming not in text:
    raise SystemExit('upcoming target not found')
text = text.replace(old_upcoming, new_upcoming, 1)

old_rows = '''                val rowIds = intArrayOf(
                    R.id.widget_event_1,
                    R.id.widget_event_2,
                    R.id.widget_event_3
                )

                if (upcoming.isEmpty()) {
                    views.setViewVisibility(R.id.widget_event_1, View.VISIBLE)
                    views.setTextViewText(
                        R.id.widget_event_1,
                        if (todaysEvents.isEmpty()) "Dagen är fri från kalenderposter" else "Inget mer tidsatt idag"
                    )
                } else {
                    upcoming.forEachIndexed { index, event ->
                        val who = when (event.memberId) {
                            null, ALL_FAMILY_MEMBER_ID -> "Alla"
                            else -> members[event.memberId]?.name.orEmpty()
                        }
                        val title = event.title.removePrefix("🌈").trim()
                        val suffix = if (who.isBlank()) "" else " • $who"
                        views.setViewVisibility(rowIds[index], View.VISIBLE)
                        views.setTextViewText(rowIds[index], "${event.time}  $title$suffix")
                    }
                }
'''
new_rows = '''                val rowIds = intArrayOf(
                    R.id.widget_event_1,
                    R.id.widget_event_2,
                    R.id.widget_event_3,
                    R.id.widget_event_4
                )

                val widgetItems = buildList {
                    upcomingToday.forEach { add(false to it) }
                    upcomingTomorrow.forEach { add(true to it) }
                }

                if (widgetItems.isEmpty()) {
                    views.setViewVisibility(R.id.widget_event_1, View.VISIBLE)
                    views.setTextViewText(
                        R.id.widget_event_1,
                        if (todaysEvents.isEmpty() && tomorrowsEvents.isEmpty()) "Lugnt idag och imorgon" else "Inget mer tidsatt idag"
                    )
                } else {
                    widgetItems.forEachIndexed { index, (isTomorrow, event) ->
                        val who = when (event.memberId) {
                            null, ALL_FAMILY_MEMBER_ID -> "Alla"
                            else -> members[event.memberId]?.name.orEmpty()
                        }
                        val title = event.title.removePrefix("🌈").trim()
                        val suffix = if (who.isBlank()) "" else " • $who"
                        val prefix = if (isTomorrow) "Imorgon ${event.time}" else event.time
                        views.setViewVisibility(rowIds[index], View.VISIBLE)
                        views.setTextViewText(rowIds[index], "$prefix  $title$suffix")
                    }
                }
'''
if old_rows not in text:
    raise SystemExit('row rendering target not found')
text = text.replace(old_rows, new_rows, 1)

path.write_text(text, encoding='utf-8')
print('Widget tomorrow preview applied')
