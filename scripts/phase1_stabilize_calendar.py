from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
text = path.read_text(encoding='utf-8')

old_intro = '''        // Keep enough vertical room for the complete six-week month grid.\n        // The photo remains at its natural ratio and simply becomes smaller on\n        // short phones instead of squeezing the calendar cells to zero height.\n        val calendarMinHeight = 390.dp\n        // Keep some of the seasonal artwork visible above the calendar, but never let\n        // that hero area steal the height needed by the six week rows.\n        val heroHeight = (maxHeight - calendarMinHeight - 8.dp).coerceIn(72.dp, 135.dp)\n        SeasonalPhoto(mode, Modifier.matchParentSize())\n'''

new_intro = '''        // The month grid always owns a predictable amount of vertical space.\n        // Do not combine weight(), negative offsets and min-height here: that made\n        // six equal week rows collapse differently on different screen heights.\n        val calendarMinHeight = 390.dp\n        val outerVerticalPadding = 20.dp\n        val sectionSpacing = 8.dp\n        val seasonalHeroMax = 135.dp\n        val seasonalHeroMin = 72.dp\n        val hasSeasonalHero = mode != ThemeMode.CLASSIC\n        val availableForHero = maxHeight - calendarMinHeight - outerVerticalPadding - sectionSpacing\n        val heroHeight = if (hasSeasonalHero) {\n            availableForHero.coerceIn(0.dp, seasonalHeroMax).let {\n                if (it in 1.dp..<seasonalHeroMin) 0.dp else it\n            }\n        } else 0.dp\n        val calendarHeight = (maxHeight - outerVerticalPadding - heroHeight -\n            if (heroHeight > 0.dp) sectionSpacing else 0.dp).coerceAtLeast(calendarMinHeight)\n        SeasonalPhoto(mode, Modifier.matchParentSize())\n'''

old_container = '''            if (mode != ThemeMode.CLASSIC) {\n                Spacer(Modifier.height(heroHeight))\n            }\n            BoxWithConstraints(\n                modifier = Modifier\n                    .fillMaxWidth()\n                    .offset(y = (-130).dp)\n                    .weight(1f)\n                    .heightIn(min = calendarMinHeight)\n                    .clipToBounds()\n'''

new_container = '''            if (heroHeight > 0.dp) {\n                Spacer(Modifier.height(heroHeight))\n            }\n            BoxWithConstraints(\n                modifier = Modifier\n                    .fillMaxWidth()\n                    .height(calendarHeight)\n                    .clipToBounds()\n'''

if old_intro not in text:
    raise SystemExit('Expected calendar sizing intro block not found; refusing unsafe patch')
if old_container not in text:
    raise SystemExit('Expected compressed calendar container block not found; refusing unsafe patch')

text = text.replace(old_intro, new_intro, 1)
text = text.replace(old_container, new_container, 1)
path.write_text(text, encoding='utf-8')
print('Phase 1 calendar sizing stabilization applied')
