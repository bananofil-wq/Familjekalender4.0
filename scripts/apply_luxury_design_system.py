from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/se/familjekalender/app"
main = src / "MainActivity.kt"
text = main.read_text(encoding="utf-8")

replacements = {
    "internal val Bg = Color(0xFF0F0E13)": "internal val Bg = LuxuryBackground",
    "internal val CardBg = Color(0xFF1B191F)": "internal val CardBg = LuxurySurface",
    "internal val SoftPurple = Color(0xFF2B2038)": "internal val SoftPurple = LuxurySurfaceHigh",
    "internal val Muted = Color(0xFFAAA4B2)": "internal val Muted = LuxuryTextMuted",
    "        containerColor = Bg,": "        containerColor = MaterialTheme.colorScheme.background,",
    "fadeIn(tween(motionDuration(220, motionEnabled)))": "fadeIn(tween(motionDuration(LuxuryMotion.Standard, motionEnabled)))",
    "scaleIn(tween(motionDuration(260, motionEnabled)), initialScale = .985f)": "scaleIn(tween(motionDuration(LuxuryMotion.Standard, motionEnabled)), initialScale = .992f)",
    "fadeOut(tween(motionDuration(150, motionEnabled)))": "fadeOut(tween(motionDuration(LuxuryMotion.Fast, motionEnabled)))",
    "scaleOut(tween(motionDuration(180, motionEnabled)), targetScale = .99f)": "scaleOut(tween(motionDuration(LuxuryMotion.Fast, motionEnabled)), targetScale = .996f)",
    "fadeIn(tween(motionDuration(170, motionEnabled)))": "fadeIn(tween(motionDuration(LuxuryMotion.Standard, motionEnabled)))",
    "slideInVertically(tween(motionDuration(210, motionEnabled))) { it / 18 }": "slideInVertically(tween(motionDuration(LuxuryMotion.Standard, motionEnabled))) { it / 28 }",
    "fadeOut(tween(motionDuration(120, motionEnabled)))": "fadeOut(tween(motionDuration(LuxuryMotion.Fast, motionEnabled)))",
    "slideOutVertically(tween(motionDuration(160, motionEnabled))) { -it / 20 }": "slideOutVertically(tween(motionDuration(LuxuryMotion.Fast, motionEnabled))) { -it / 30 }",
}
for old, new in replacements.items():
    text = text.replace(old, new)

old_theme = '''    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = palette.accent,
            secondary = palette.accent,
            surfaceVariant = palette.soft,
            outline = palette.accent.copy(alpha = .55f),
            background = Bg,
            surface = CardBg,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
'''
new_theme = '''    FamiljekalenderLuxuryTheme(palette) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) {
'''
if old_theme not in text:
    raise SystemExit("Expected MaterialTheme block not found in MainActivity.kt")
text = text.replace(old_theme, new_theme, 1)
main.write_text(text, encoding="utf-8")

# Unify the legacy base colors anywhere they are still hard-coded in Compose screens.
color_replacements = {
    "Color(0xFF0F0E13)": "LuxuryBackground",
    "Color(0xFF1B191F)": "LuxurySurface",
    "Color(0xFFAAA4B2)": "LuxuryTextMuted",
    "Color(0xFF2B2038)": "LuxurySurfaceHigh",
}
for path in src.glob("*.kt"):
    if path.name == "LuxuryDesignSystem.kt":
        continue
    source = path.read_text(encoding="utf-8")
    updated = source
    for old, new in color_replacements.items():
        updated = updated.replace(old, new)
    if updated != source:
        path.write_text(updated, encoding="utf-8")

print("Luxury design system applied")
