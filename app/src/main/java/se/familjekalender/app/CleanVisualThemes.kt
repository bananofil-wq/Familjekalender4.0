package se.familjekalender.app

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class CleanVisualTheme(
    val label: String,
    val description: String,
    val emoji: String,
) {
    CURRENT(
        "Nuvarande",
        "Din nuvarande Clean-design med glas, lila accent och vald bakgrund.",
        "💜",
    ),
    NORDIC_DAY_PLANNER(
        "Nordic Day Planner",
        "Ljus skandinavisk dagsvy med blå tidslinje, dagspår och snabbkort för familjen.",
        "▤",
    ),
    NORDIC_DAY_PLANNER_DARK(
        "Nordic Day Planner Dark",
        "Samma Nordic-layout i svart och antracit, med orange accent.",
        "▤",
    ),
    BRUTALIST(
        "Industrial Pro",
        "Mörk industriell design i stål, grafit och varningsgult med tydlig struktur.",
        "⚙",
    ),
    JAPANDI(
        "Japandi",
        "Ljust, lugnt och avskalat i elfenben, sand och salviagrönt.",
        "◌",
    ),
    LUXURY_GOLD(
        "Svart & guld",
        "Mörk premiumlook med champagneguld och elegant serif.",
        "✦",
    ),
    MEMPHIS(
        "Playful Blocks",
        "Lekfull familjedesign med klara blockfärger, stora former och tydlig struktur.",
        "▣",
    ),
    SWISS(
        "Swiss",
        "Strikt grid, vit yta och koboltblå accent med tydlig typografi.",
        "▦",
    ),
    ICE_GLASS(
        "Arctic Glass",
        "Isblå frostad glasdesign med mjuka vita ljuskanter.",
        "❄",
    ),
    BIOPHILIC(
        "Forest Flow",
        "Djup skogskänsla med trä, mossa, grönska och varm naturlig kontrast.",
        "🌲",
    ),
    RETRO_70S(
        "Vintage Diary",
        "Klassisk dagbokskänsla med papper, läder, bläck och varm tidlös typografi.",
        "📖",
    ),
    CLAY(
        "Clay",
        "Mjuk 3D-känsla med pastell, rundade knappar och taktil yta.",
        "◍",
    ),
    CYBERPUNK(
        "Galaxy View",
        "Djupt rymdtema med stjärnhimmel, planettoner och lysande kosmiska accenter.",
        "✧",
    ),
    PURE_CALENDAR(
        "Pure Calendar", "Minimalistisk vit kalender med tydliga blå detaljer.", "▦",
    ),
    MIDNIGHT_GOLD(
        "Midnight Gold", "Kolsvart design med champagneguld och eleganta detaljer.", "✦",
    ),
    PASTEL_FLOW(
        "Pastel Flow", "Lavendel, persika och mint med mjuka kort.", "◕",
    ),
    CRYSTAL_GLASS(
        "Crystal Glass", "Frostat glas med blåviolett djup och mjuka reflexer.", "◇",
    ),
    EDITORIAL_PLANNER(
        "Editorial Planner", "Varmvitt papper och luftig tidningsliknande typografi.", "▤",
    ),
    NEON_PULSE(
        "Neon Pulse", "Grafitsvart med lysande cyan och violett.", "⚡",
    ),
    EARTH_SAGE(
        "Earth & Sage", "Salviagrönt, sand och terrakotta.", "❧",
    ),
    RETRO_DIGITAL(
        "Retro Digital", "Marinblå digital retro med orange och turkos.", "▧",
    ),
    FOREST_PRO(
        "Forest Pro", "Antracit och skogsgrönt med rena ytor.", "♧",
    ),
    FAMILY_SPECTRUM(
        "Family Spectrum", "Ljus familjedesign med klara kategorifärger.", "◉",
    ),
    OAK_WOOD(
        "Trätema", "Fotografiskt premiumträ med mässingsdetaljer, ljus ekkalender och mörka träpaneler.", "🌿",
    ),
    COPPER("Koppar", "Borstad koppar med mörka bronsytor och varma metallglansdetaljer.", "◈"),
    CARBON_FIBER("Kolfiber", "Svart vävd kolfiber, blå ljuskanter och sportigt djup.", "◆"),
    TITANIUM("Titan", "Borstad titan i grafit och silver med precisa metallkanter.", "◇"),
    HIPPIE("Hippie", "Blommor, turkos, apelsin och varm retroinspirerad kalender.", "✿"),
    GOTHAM_NIGHT(
        "DC – Gotham Night", "Månbelyst gotisk stad, mörk sten och blå nattglöd.", "🌙",
    ),
}

/** The only twelve user-selectable Clean themes; legacy enum IDs remain for compatibility. */
internal val selectableCleanVisualThemes = listOf(
    CleanVisualTheme.CURRENT,
    CleanVisualTheme.NORDIC_DAY_PLANNER,
    CleanVisualTheme.PURE_CALENDAR,
    CleanVisualTheme.MIDNIGHT_GOLD,
    CleanVisualTheme.PASTEL_FLOW,
    CleanVisualTheme.CRYSTAL_GLASS,
    CleanVisualTheme.EDITORIAL_PLANNER,
    CleanVisualTheme.NEON_PULSE,
    CleanVisualTheme.EARTH_SAGE,
    CleanVisualTheme.RETRO_DIGITAL,
    CleanVisualTheme.FOREST_PRO,
    CleanVisualTheme.FAMILY_SPECTRUM,
    CleanVisualTheme.OAK_WOOD,
    CleanVisualTheme.GOTHAM_NIGHT,
    CleanVisualTheme.COPPER,
    CleanVisualTheme.CARBON_FIBER,
    CleanVisualTheme.TITANIUM,
    CleanVisualTheme.HIPPIE,
)

/** Migrate persisted old theme IDs to the new collection without affecting appointments. */
internal fun resolveCleanVisualTheme(saved: String?): CleanVisualTheme {
    val old = runCatching { CleanVisualTheme.valueOf(saved ?: "") }.getOrDefault(CleanVisualTheme.CURRENT)
    return when (old) {
        CleanVisualTheme.NORDIC_DAY_PLANNER_DARK -> CleanVisualTheme.NORDIC_DAY_PLANNER
        CleanVisualTheme.BRUTALIST -> CleanVisualTheme.RETRO_DIGITAL
        CleanVisualTheme.JAPANDI -> CleanVisualTheme.EARTH_SAGE
        CleanVisualTheme.LUXURY_GOLD -> CleanVisualTheme.MIDNIGHT_GOLD
        CleanVisualTheme.MEMPHIS -> CleanVisualTheme.FAMILY_SPECTRUM
        CleanVisualTheme.SWISS -> CleanVisualTheme.PURE_CALENDAR
        CleanVisualTheme.ICE_GLASS -> CleanVisualTheme.CRYSTAL_GLASS
        CleanVisualTheme.BIOPHILIC -> CleanVisualTheme.FOREST_PRO
        CleanVisualTheme.RETRO_70S -> CleanVisualTheme.RETRO_DIGITAL
        CleanVisualTheme.CLAY -> CleanVisualTheme.PASTEL_FLOW
        CleanVisualTheme.CYBERPUNK -> CleanVisualTheme.NEON_PULSE
        else -> old
    }
}

/** Material backgrounds and dialogs must respect each light Clean palette. */
internal fun isLightCleanTheme(theme: CleanVisualTheme): Boolean =
    theme == CleanVisualTheme.NORDIC_DAY_PLANNER ||
        theme == CleanVisualTheme.PURE_CALENDAR ||
        theme == CleanVisualTheme.PASTEL_FLOW ||
        theme == CleanVisualTheme.EDITORIAL_PLANNER ||
        theme == CleanVisualTheme.EARTH_SAGE ||
        theme == CleanVisualTheme.FAMILY_SPECTRUM ||
        theme == CleanVisualTheme.OAK_WOOD ||
        theme == CleanVisualTheme.TITANIUM || theme == CleanVisualTheme.HIPPIE

internal fun isNordicDayPlannerTheme(theme: CleanVisualTheme): Boolean =
    theme == CleanVisualTheme.NORDIC_DAY_PLANNER ||
        theme == CleanVisualTheme.NORDIC_DAY_PLANNER_DARK

data class CleanThemeSpec(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val overlayTop: Color,
    val overlayBottom: Color,
    val panelTop: Color,
    val panelMid: Color,
    val panelBottom: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentStrong: Color,
    val secondary: Color,
    val info: Color,
    val warning: Color,
    val selectedTop: Color,
    val selectedBottom: Color,
    val selectedBorder: Color,
    val selectedText: Color,
    val dayTop: Color,
    val dayBottom: Color,
    val navSurface: Color,
    val navBorder: Color,
    val titleFont: FontFamily,
    val titleWeight: FontWeight,
    val cardRadius: Dp,
    val calendarRadius: Dp,
    val dayRadius: Dp,
    val buttonRadius: Dp,
    val shadow: Dp,
)

internal fun cleanThemeSpec(theme: CleanVisualTheme): CleanThemeSpec =
    when (theme) {
        CleanVisualTheme.CURRENT ->
            CleanThemeSpec(
                backgroundTop = Color(0xFF17121F),
                backgroundBottom = Color(0xFF0F0C15),
                overlayTop = Color(0x33120B16),
                overlayBottom = Color(0x77110D18),
                panelTop = Color.White.copy(alpha = .20f),
                panelMid = Color(0xD9292731),
                panelBottom = Color(0xE61A1821),
                border = Color.White.copy(alpha = .26f),
                text = Color.White,
                muted = Color.White.copy(alpha = .66f),
                accent = Color(0xFF8A5CF6),
                accentStrong = Color(0xFFAA72FF),
                secondary = Color(0xFF78AFFF),
                info = Color(0xFF8FC9FF),
                warning = Color(0xFFFFB35C),
                selectedTop = Color(0xFFD07BFF),
                selectedBottom = Color(0xFF7224E7),
                selectedBorder = Color(0xFFF0D5FF),
                selectedText = Color.White,
                dayTop = Color.White.copy(alpha = .18f),
                dayBottom = Color(0x16000000),
                navSurface = Color(0xEE1B1727),
                navBorder = Color.White.copy(alpha = .13f),
                titleFont = FontFamily.Cursive,
                titleWeight = FontWeight.SemiBold,
                cardRadius = 24.dp,
                calendarRadius = 32.dp,
                dayRadius = 16.dp,
                buttonRadius = 50.dp,
                shadow = 10.dp,
            )

        CleanVisualTheme.NORDIC_DAY_PLANNER ->
            CleanThemeSpec(
                backgroundTop = Color(0xFFF9F7F1),
                backgroundBottom = Color(0xFFF1EEE6),
                overlayTop = Color.Transparent,
                overlayBottom = Color.Transparent,
                panelTop = Color(0xFFFFFEFA),
                panelMid = Color(0xFFF8F5ED),
                panelBottom = Color(0xFFF1EDE3),
                border = Color(0x1F24425D),
                text = Color(0xFF18324A),
                muted = Color(0xFF6D7C88),
                accent = Color(0xFF1A8FC5),
                accentStrong = Color(0xFF0D7DB3),
                secondary = Color(0xFF46B5D8),
                info = Color(0xFF5AA4C8),
                warning = Color(0xFFD99B2B),
                selectedTop = Color.Transparent,
                selectedBottom = Color.Transparent,
                selectedBorder = Color(0xFF0D7DB3),
                selectedText = Color(0xFF0D7DB3),
                dayTop = Color(0xFF27A1D2),
                dayBottom = Color(0xFF0878AD),
                navSurface = Color(0xFFFFFEFA),
                navBorder = Color(0x1F24425D),
                titleFont = FontFamily.SansSerif,
                titleWeight = FontWeight.SemiBold,
                cardRadius = 14.dp,
                calendarRadius = 14.dp,
                dayRadius = 8.dp,
                buttonRadius = 12.dp,
                shadow = 2.dp,
            )

        CleanVisualTheme.NORDIC_DAY_PLANNER_DARK ->
            CleanThemeSpec(
                backgroundTop = Color(0xFF0B0B0B),
                backgroundBottom = Color(0xFF111111),
                overlayTop = Color.Transparent,
                overlayBottom = Color.Transparent,
                panelTop = Color(0xFF151515),
                panelMid = Color(0xFF121212),
                panelBottom = Color(0xFF0E0E0E),
                border = Color(0x2FFFFFFF),
                text = Color(0xFFF4F4F4),
                muted = Color(0xFF9CA3A8),
                accent = Color(0xFFFF8A00),
                accentStrong = Color(0xFFFF7600),
                secondary = Color(0xFFFFA23A),
                info = Color(0xFFFFA23A),
                warning = Color(0xFFFFB347),
                selectedTop = Color.Transparent,
                selectedBottom = Color.Transparent,
                selectedBorder = Color(0xFFFF8A00),
                selectedText = Color(0xFFFF8A00),
                dayTop = Color(0xFF151515),
                dayBottom = Color(0xFF0E0E0E),
                navSurface = Color(0xFF101010),
                navBorder = Color(0x2FFFFFFF),
                titleFont = FontFamily.SansSerif,
                titleWeight = FontWeight.SemiBold,
                cardRadius = 14.dp,
                calendarRadius = 14.dp,
                dayRadius = 8.dp,
                buttonRadius = 12.dp,
                shadow = 2.dp,
            )

        CleanVisualTheme.PURE_CALENDAR ->
            cleanThemeSpec(CleanVisualTheme.SWISS).copy(
                backgroundTop = Color(0xFFFFFFFF),
                backgroundBottom = Color(0xFFF7F9FC),
                panelTop = Color(0xFFFFFFFF),
                panelMid = Color(0xFFF9FAFC),
                panelBottom = Color(0xFFF3F6FA),
                text = Color(0xFF1E293B),
                muted = Color(0xFF68788A),
                accent = Color(0xFF3579E8),
                accentStrong = Color(0xFF1B62D5),
                secondary = Color(0xFF28A6A1),
                navSurface = Color(0xFFFFFFFF),
                border = Color(0x1F334155),
                navBorder = Color(0x1F334155),
                titleFont = FontFamily.SansSerif, titleWeight = FontWeight.SemiBold,
                cardRadius = 15.dp, calendarRadius = 19.dp,
                dayRadius = 9.dp, buttonRadius = 13.dp, shadow = 1.dp,
            )

        CleanVisualTheme.MIDNIGHT_GOLD ->
            cleanThemeSpec(CleanVisualTheme.LUXURY_GOLD).copy(
                backgroundTop = Color(0xFF0B0D11),
                backgroundBottom = Color(0xFF020305),
                panelTop = Color(0xFF242227),
                panelMid = Color(0xFF171719),
                panelBottom = Color(0xFF101113),
                navSurface = Color(0xFF101012),
                text = Color(0xFFF8F1DE),
                muted = Color(0xFFC3B9A6),
                accent = Color(0xFFD8B76C),
                accentStrong = Color(0xFFFFDC8E),
                secondary = Color(0xFFB6C5DB),
                titleFont = FontFamily.Serif, titleWeight = FontWeight.Medium,
                cardRadius = 18.dp, calendarRadius = 24.dp,
                dayRadius = 13.dp, buttonRadius = 50.dp, shadow = 5.dp,
            )

        CleanVisualTheme.PASTEL_FLOW ->
            cleanThemeSpec(CleanVisualTheme.CLAY).copy(
                backgroundTop = Color(0xFFFFF2ED),
                backgroundBottom = Color(0xFFECEBFF),
                panelTop = Color(0xFFFFF9F7),
                panelMid = Color(0xFFF8F3FC),
                panelBottom = Color(0xFFF0F7F1),
                navSurface = Color(0xFFFFFAFD),
                text = Color(0xFF4B4260),
                muted = Color(0xFF797087),
                accent = Color(0xFF9476CC),
                accentStrong = Color(0xFF805FBC),
                secondary = Color(0xFF61B6A4),
                info = Color(0xFF76A5DF),
                warning = Color(0xFFDE936F),
                titleFont = FontFamily.SansSerif, titleWeight = FontWeight.Bold,
                cardRadius = 25.dp, calendarRadius = 27.dp,
                dayRadius = 15.dp, buttonRadius = 50.dp, shadow = 3.dp,
            )

        CleanVisualTheme.CRYSTAL_GLASS ->
            cleanThemeSpec(CleanVisualTheme.ICE_GLASS).copy(
                backgroundTop = Color(0xFF3C468C),
                backgroundBottom = Color(0xFF161B43),
                overlayTop = Color(0x2228C6F7),
                overlayBottom = Color(0x66331766),
                panelTop = Color(0x667F8FCE),
                panelMid = Color(0x88415A9C),
                panelBottom = Color(0x99233469),
                navSurface = Color(0xCC29386E),
                text = Color(0xFFFFFFFF),
                muted = Color(0xFFE3EAFF),
                accent = Color(0xFF8EDBFA),
                accentStrong = Color(0xFFB4ECFF),
                secondary = Color(0xFFD3ADFF),
                border = Color(0x99DCE9FF),
                navBorder = Color(0x99DCE9FF),
                titleFont = FontFamily.SansSerif, titleWeight = FontWeight.SemiBold,
                cardRadius = 25.dp, calendarRadius = 30.dp,
                dayRadius = 17.dp, buttonRadius = 50.dp, shadow = 6.dp,
                selectedText = Color(0xFF182447),
            )

        CleanVisualTheme.EDITORIAL_PLANNER ->
            cleanThemeSpec(CleanVisualTheme.JAPANDI).copy(
                backgroundTop = Color(0xFFF8F4ED),
                backgroundBottom = Color(0xFFF0E9DF),
                panelTop = Color(0xFFFFFDF9),
                panelMid = Color(0xFFF8F4EC),
                panelBottom = Color(0xFFF1E9DC),
                navSurface = Color(0xFFFFFDF9),
                text = Color(0xFF282724),
                muted = Color(0xFF77746E),
                accent = Color(0xFF514A43),
                accentStrong = Color(0xFF282724),
                secondary = Color(0xFFB2785C),
                info = Color(0xFF627B79),
                titleFont = FontFamily.Serif, titleWeight = FontWeight.SemiBold,
                cardRadius = 5.dp, calendarRadius = 7.dp,
                dayRadius = 3.dp, buttonRadius = 5.dp, shadow = 1.dp,
            )

        CleanVisualTheme.NEON_PULSE ->
            cleanThemeSpec(CleanVisualTheme.CYBERPUNK).copy(
                backgroundTop = Color(0xFF13151C),
                backgroundBottom = Color(0xFF090A10),
                panelTop = Color(0xFF24283B),
                panelMid = Color(0xFF171B2B),
                panelBottom = Color(0xFF0F1220),
                navSurface = Color(0xFF101522),
                text = Color(0xFFF3FBFF),
                muted = Color(0xFFB2C2D2),
                accent = Color(0xFF3EE3EE),
                accentStrong = Color(0xFF82F6FC),
                secondary = Color(0xFFBD7AFF),
                border = Color(0x8850D9EF),
                navBorder = Color(0x8850D9EF),
                titleFont = FontFamily.SansSerif, titleWeight = FontWeight.Bold,
                cardRadius = 13.dp, calendarRadius = 16.dp,
                dayRadius = 8.dp, buttonRadius = 13.dp, shadow = 4.dp,
            )

        CleanVisualTheme.EARTH_SAGE ->
            cleanThemeSpec(CleanVisualTheme.JAPANDI).copy(
                backgroundTop = Color(0xFFF1E9DC),
                backgroundBottom = Color(0xFFE1E6D8),
                panelTop = Color(0xFFFCF8F0),
                panelMid = Color(0xFFF2F0E7),
                panelBottom = Color(0xFFE9E9DD),
                navSurface = Color(0xFFF7F4EA),
                text = Color(0xFF35463A),
                muted = Color(0xFF69766B),
                accent = Color(0xFF6F876A),
                accentStrong = Color(0xFF4F6E50),
                secondary = Color(0xFFBC7B62),
                info = Color(0xFF789D97),
                titleFont = FontFamily.Serif, titleWeight = FontWeight.Medium,
                cardRadius = 22.dp, calendarRadius = 24.dp,
                dayRadius = 12.dp, buttonRadius = 50.dp, shadow = 2.dp,
            )

        CleanVisualTheme.RETRO_DIGITAL ->
            cleanThemeSpec(CleanVisualTheme.RETRO_70S).copy(
                backgroundTop = Color(0xFF172C42),
                backgroundBottom = Color(0xFF0C1727),
                panelTop = Color(0xFF244057),
                panelMid = Color(0xFF1B3043),
                panelBottom = Color(0xFF142739),
                navSurface = Color(0xFF152A3F),
                text = Color(0xFFF5F0DF),
                muted = Color(0xFFB2C4CB),
                accent = Color(0xFFFFA34E),
                accentStrong = Color(0xFFFFC17B),
                secondary = Color(0xFF58C8BF),
                info = Color(0xFF8CC6EA),
                border = Color(0x88639D9B),
                navBorder = Color(0x88639D9B),
                titleFont = FontFamily.Monospace, titleWeight = FontWeight.Bold,
                cardRadius = 8.dp, calendarRadius = 11.dp,
                dayRadius = 5.dp, buttonRadius = 7.dp, shadow = 2.dp,
                selectedText = Color(0xFF152438),
            )

        CleanVisualTheme.FOREST_PRO ->
            cleanThemeSpec(CleanVisualTheme.BIOPHILIC).copy(
                backgroundTop = Color(0xFF1C2B26),
                backgroundBottom = Color(0xFF101B18),
                panelTop = Color(0xFF293A33),
                panelMid = Color(0xFF202E29),
                panelBottom = Color(0xFF192720),
                navSurface = Color(0xFF182721),
                text = Color(0xFFF0F3ED),
                muted = Color(0xFFB5C5B9),
                accent = Color(0xFF8EC69D),
                accentStrong = Color(0xFFB0E4BD),
                secondary = Color(0xFF80AEA0),
                info = Color(0xFF83B5C0),
                border = Color(0x775D957A),
                navBorder = Color(0x775D957A),
                titleFont = FontFamily.SansSerif, titleWeight = FontWeight.SemiBold,
                cardRadius = 14.dp, calendarRadius = 18.dp,
                dayRadius = 8.dp, buttonRadius = 12.dp, shadow = 3.dp,
                selectedText = Color(0xFF11281D),
            )

        CleanVisualTheme.FAMILY_SPECTRUM ->
            cleanThemeSpec(CleanVisualTheme.MEMPHIS).copy(
                backgroundTop = Color(0xFFF5F8FF),
                backgroundBottom = Color(0xFFEDF5F9),
                panelTop = Color(0xFFFFFFFF),
                panelMid = Color(0xFFF9FBFE),
                panelBottom = Color(0xFFF2F7FC),
                navSurface = Color(0xFFFFFFFF),
                text = Color(0xFF263A4A),
                muted = Color(0xFF6E8192),
                accent = Color(0xFF507BE6),
                accentStrong = Color(0xFF3565D1),
                secondary = Color(0xFF20AFA0),
                info = Color(0xFF9672E3),
                warning = Color(0xFFEAA35C),
                border = Color(0x24546880),
                navBorder = Color(0x24546880),
                titleFont = FontFamily.SansSerif, titleWeight = FontWeight.Bold,
                cardRadius = 21.dp, calendarRadius = 25.dp,
                dayRadius = 13.dp, buttonRadius = 20.dp, shadow = 2.dp,
                selectedText = Color.White,
            )


        // The oak identifier is preserved so saved preferences continue to work.
        // Its home screen is the bespoke 2026 reference layout; this palette skins auxiliary pages.
        CleanVisualTheme.OAK_WOOD ->
            cleanThemeSpec(CleanVisualTheme.CURRENT).copy(
                backgroundTop = Color(0xFFD7A873),
                backgroundBottom = Color(0xFF5B321C),
                overlayTop = Color(0x120C0804),
                overlayBottom = Color(0x5E241308),
                panelTop = Color(0xAD80502F),
                panelMid = Color(0xD457351E),
                panelBottom = Color(0xEB362115),
                border = Color(0x9ADDB783),
                text = Color(0xFFFCEBD4),
                muted = Color(0xFFDCC3A2),
                accent = Color(0xFFC48A50),
                accentStrong = Color(0xFFFFD399),
                secondary = Color(0xFFA9CCB5),
                info = Color(0xFFB6DBE6),
                warning = Color(0xFFFFC078),
                selectedTop = Color(0xFFB77A43),
                selectedBottom = Color(0xFF77401D),
                selectedBorder = Color(0xFFFFD59A),
                selectedText = Color(0xFFFFFFFF),
                dayTop = Color(0xB7F3DFBF),
                dayBottom = Color(0xC8E5C59A),
                navSurface = Color(0xED382317),
                navBorder = Color(0xA9D8AE7D),
                titleFont = FontFamily.Cursive,
                titleWeight = FontWeight.SemiBold,
                cardRadius = 24.dp,
                calendarRadius = 32.dp,
                dayRadius = 16.dp,
                buttonRadius = 50.dp,
                shadow = 10.dp,
            )

        // Device-density rendered material skins; no screenshot text/controls baked into a bitmap.
        CleanVisualTheme.COPPER ->
            cleanThemeSpec(CleanVisualTheme.OAK_WOOD).copy(
                backgroundTop = Color(0xFF4D2515), backgroundBottom = Color(0xFF150B08),
                panelTop = Color(0xFFAC5A32), panelMid = Color(0xFF653019),
                panelBottom = Color(0xFF29140E),
                border = Color(0xFFCD8752), text = Color(0xFFFFE0B4),
                muted = Color(0xFFD6A783), accent = Color(0xFFD9884C),
                accentStrong = Color(0xFFFFBC73), secondary = Color(0xFFFFA772),
                info = Color(0xFF8BD7E5), warning = Color(0xFFFFC66C),
                selectedTop = Color(0xFFBB6737), selectedBottom = Color(0xFF5A2410),
                selectedBorder = Color(0xFFFFDA87), selectedText = Color(0xFFFFF0D6),
                dayTop = Color(0xFF9F522D), dayBottom = Color(0xFF482213),
                navSurface = Color(0xFF28150F), navBorder = Color(0xFFE3A169),
                titleFont = FontFamily.Serif, cardRadius = 14.dp,
                calendarRadius = 18.dp, dayRadius = 9.dp, buttonRadius = 24.dp,
            )
        CleanVisualTheme.CARBON_FIBER ->
            cleanThemeSpec(CleanVisualTheme.NEON_PULSE).copy(
                backgroundTop = Color(0xFF121C26), backgroundBottom = Color(0xFF04070D),
                panelTop = Color(0xFF202A35), panelMid = Color(0xFF111A23),
                panelBottom = Color(0xFF080E15),
                border = Color(0xFF248ACA), text = Color(0xFFF5F9FF),
                muted = Color(0xFFACCCDD), accent = Color(0xFF118CFF),
                accentStrong = Color(0xFF63C7FF), secondary = Color(0xFF42B5FF),
                info = Color(0xFF91DAFF), warning = Color(0xFFFFBB65),
                selectedTop = Color(0xFF103D6C), selectedBottom = Color(0xFF051628),
                selectedBorder = Color(0xFF36B5FF), selectedText = Color.White,
                dayTop = Color(0xFF172531), dayBottom = Color(0xFF080F19),
                navSurface = Color(0xFF09121B), navBorder = Color(0xFF218BC9),
                titleFont = FontFamily.Serif, cardRadius = 12.dp,
                calendarRadius = 16.dp, dayRadius = 7.dp, buttonRadius = 24.dp,
            )
        CleanVisualTheme.TITANIUM ->
            cleanThemeSpec(CleanVisualTheme.PURE_CALENDAR).copy(
                backgroundTop = Color(0xFF727B83), backgroundBottom = Color(0xFF20272D),
                panelTop = Color(0xFFDAE0E3), panelMid = Color(0xFFB2BDC4),
                panelBottom = Color(0xFF77848D),
                border = Color(0xFF78838A), text = Color(0xFF152029),
                muted = Color(0xFF46555D), accent = Color(0xFF536D7A),
                accentStrong = Color(0xFF314C60), secondary = Color(0xFF739CB3),
                info = Color(0xFF477EAA), warning = Color(0xFFCA8B45),
                selectedTop = Color(0xFF56656E), selectedBottom = Color(0xFF293640),
                selectedBorder = Color(0xFFBDE8FF), selectedText = Color.White,
                dayTop = Color(0xFFE2E6E8), dayBottom = Color(0xFFB7C0C6),
                navSurface = Color(0xFFB9C3C8), navBorder = Color(0xFF5B6B75),
                titleFont = FontFamily.Serif, cardRadius = 12.dp,
                calendarRadius = 16.dp, dayRadius = 8.dp, buttonRadius = 24.dp,
            )
        CleanVisualTheme.HIPPIE ->
            cleanThemeSpec(CleanVisualTheme.FAMILY_SPECTRUM).copy(
                backgroundTop = Color(0xFF137973), backgroundBottom = Color(0xFF065458),
                panelTop = Color(0xFFFFBC68), panelMid = Color(0xFFFF8F46),
                panelBottom = Color(0xFFDF5A3C),
                border = Color(0xFF8F411D), text = Color(0xFF322010),
                muted = Color(0xFF70432A), accent = Color(0xFFEA642E),
                accentStrong = Color(0xFFE74726), secondary = Color(0xFF1BADA5),
                info = Color(0xFF1B8FBD), warning = Color(0xFFF1A320),
                selectedTop = Color(0xFFFF9D34), selectedBottom = Color(0xFFD95326),
                selectedBorder = Color(0xFFFFDC58), selectedText = Color(0xFF301909),
                dayTop = Color(0xFFFFE6B6), dayBottom = Color(0xFFF8C98B),
                navSurface = Color(0xFFEE9953), navBorder = Color(0xFF8F491D),
                titleFont = FontFamily.Serif, cardRadius = 17.dp,
                calendarRadius = 17.dp, dayRadius = 9.dp, buttonRadius = 24.dp,
            )

        CleanVisualTheme.GOTHAM_NIGHT ->
            cleanThemeSpec(CleanVisualTheme.CRYSTAL_GLASS).copy(
                backgroundTop = Color(0xFF081A30),
                backgroundBottom = Color(0xFF020914),
                overlayTop = Color.Transparent,
                overlayBottom = Color.Transparent,
                panelTop = Color(0xEB0C1727),
                panelMid = Color(0xED111E31),
                panelBottom = Color(0xE8091323),
                border = Color(0x774C6D91),
                text = Color(0xFFF2F3F6),
                muted = Color(0xFF9DAFC3),
                accent = Color(0xFF2D77BB),
                accentStrong = Color(0xFF83C7FC),
                secondary = Color(0xFF71A4D6),
                info = Color(0xFF81C8EC),
                warning = Color(0xFFE4AA6B),
                selectedTop = Color(0xFF254E75),
                selectedBottom = Color(0xFF102943),
                selectedBorder = Color(0xFF87CAFF),
                selectedText = Color(0xFFFFFFFF),
                dayTop = Color(0x990B1B2D),
                dayBottom = Color(0xB8071424),
                navSurface = Color(0xF004101F),
                navBorder = Color(0x77619BC6),
                titleFont = FontFamily.Serif,
                titleWeight = FontWeight.SemiBold,
                cardRadius = 9.dp,
                calendarRadius = 5.dp,
                dayRadius = 6.dp,
                buttonRadius = 8.dp,
                shadow = 4.dp,
            )

        CleanVisualTheme.BRUTALIST ->
            CleanThemeSpec(
                backgroundTop = Color(0xFF24272A), backgroundBottom = Color(0xFF0D0F10),
                overlayTop = Color(0x221B1D1F), overlayBottom = Color(0x66000000),
                panelTop = Color(0xFF34383B), panelMid = Color(0xFF272A2D), panelBottom = Color(0xFF1B1D1F),
                border = Color(0xFF6E7478), text = Color(0xFFF2F3F3), muted = Color(0xFFB0B5B8),
                accent = Color(0xFFF5B51B), accentStrong = Color(0xFFFFC62F), secondary = Color(0xFF8D969B),
                info = Color(0xFFB9C3C8), warning = Color(0xFFF5B51B),
                selectedTop = Color(0xFFF7C33A), selectedBottom = Color(0xFFD28D00), selectedBorder = Color(0xFFFFD96B), selectedText = Color(0xFF17191A),
                dayTop = Color(0xFF3A3E41), dayBottom = Color(0xFF25282A), navSurface = Color(0xF21A1C1E), navBorder = Color(0xFF5E6468),
                titleFont = FontFamily.SansSerif, titleWeight = FontWeight.Black,
                cardRadius = 6.dp, calendarRadius = 8.dp, dayRadius = 5.dp, buttonRadius = 6.dp, shadow = 5.dp,
            )

        CleanVisualTheme.JAPANDI ->
            CleanThemeSpec(
                backgroundTop = Color(0xFFF7F4EC),
                backgroundBottom = Color(0xFFECE8DE),
                overlayTop = Color.Transparent,
                overlayBottom = Color(0x10FFFFFF),
                panelTop = Color(0xFFFDFBF6),
                panelMid = Color(0xFFF7F3E9),
                panelBottom = Color(0xFFF1EDE3),
                border = Color(0x245A655B),
                text = Color(0xFF26352E),
                muted = Color(0xFF6E796F),
                accent = Color(0xFF7E927F),
                accentStrong = Color(0xFF667D69),
                secondary = Color(0xFF9DAD9F),
                info = Color(0xFF92A9B0),
                warning = Color(0xFFB97762),
                selectedTop = Color(0xFF879B89),
                selectedBottom = Color(0xFF6F8572),
                selectedBorder = Color(0xFFD9E2D8),
                selectedText = Color.White,
                dayTop = Color(0xFFFDFBF7),
                dayBottom = Color(0xFFF4F0E7),
                navSurface = Color(0xFFF9F6EE),
                navBorder = Color(0x285A655B),
                titleFont = FontFamily.Serif,
                titleWeight = FontWeight.Medium,
                cardRadius = 22.dp,
                calendarRadius = 28.dp,
                dayRadius = 15.dp,
                buttonRadius = 50.dp,
                shadow = 4.dp,
            )

        CleanVisualTheme.LUXURY_GOLD ->
            CleanThemeSpec(
                backgroundTop = Color(0xFF15120F),
                backgroundBottom = Color(0xFF050505),
                overlayTop = Color(0x22000000),
                overlayBottom = Color(0x77000000),
                panelTop = Color(0xFF2B2823),
                panelMid = Color(0xFF1B1916),
                panelBottom = Color(0xFF0E0D0C),
                border = Color(0x88D7B56D),
                text = Color(0xFFF4E5BD),
                muted = Color(0xFFBEB39C),
                accent = Color(0xFFD3AD5F),
                accentStrong = Color(0xFFFFD985),
                secondary = Color(0xFFB6C5DB),
                info = Color(0xFFE6C477),
                warning = Color(0xFFF0B85D),
                selectedTop = Color(0xFFE4BD66),
                selectedBottom = Color(0xFF8A6223),
                selectedBorder = Color(0xFFFFE7A6),
                selectedText = Color(0xFF17110A),
                dayTop = Color(0xFF26231F),
                dayBottom = Color(0xFF11100E),
                navSurface = Color(0xF20D0C0B),
                navBorder = Color(0x88D7B56D),
                titleFont = FontFamily.Serif,
                titleWeight = FontWeight.Medium,
                cardRadius = 20.dp,
                calendarRadius = 28.dp,
                dayRadius = 13.dp,
                buttonRadius = 50.dp,
                shadow = 10.dp,
            )

        CleanVisualTheme.MEMPHIS ->
            CleanThemeSpec(
                backgroundTop = Color(0xFF79D7FF), backgroundBottom = Color(0xFFEAFBFF),
                overlayTop = Color.Transparent, overlayBottom = Color(0x18FFFFFF),
                panelTop = Color(0xFFFFF8D7), panelMid = Color(0xFFF7FFF1), panelBottom = Color(0xFFE9F8FF),
                border = Color(0x334A5B7A), text = Color(0xFF123D72), muted = Color(0xFF55708D),
                accent = Color(0xFF26B96B), accentStrong = Color(0xFF159A55), secondary = Color(0xFF3CA8F5),
                info = Color(0xFF7657E8), warning = Color(0xFFFF9F1C),
                selectedTop = Color(0xFF59D87F), selectedBottom = Color(0xFF20AE60), selectedBorder = Color(0xFFDFFFF0), selectedText = Color(0xFF083C25),
                dayTop = Color.White, dayBottom = Color(0xFFEFFAFF), navSurface = Color(0xFFF7FDFF), navBorder = Color(0x334A5B7A),
                titleFont = FontFamily.SansSerif, titleWeight = FontWeight.ExtraBold,
                cardRadius = 22.dp, calendarRadius = 26.dp, dayRadius = 14.dp, buttonRadius = 22.dp, shadow = 7.dp,
            )

        CleanVisualTheme.SWISS ->
            CleanThemeSpec(
                backgroundTop = Color.White,
                backgroundBottom = Color(0xFFF2F3F5),
                overlayTop = Color.Transparent,
                overlayBottom = Color.Transparent,
                panelTop = Color(0xFFFFFFFF),
                panelMid = Color(0xFFF7F7F7),
                panelBottom = Color(0xFFF1F1F1),
                border = Color(0x1F111111),
                text = Color(0xFF111111),
                muted = Color(0xFF666A73),
                accent = Color(0xFF1757FF),
                accentStrong = Color(0xFF0D49F5),
                secondary = Color(0xFF1757FF),
                info = Color(0xFF1757FF),
                warning = Color(0xFF222222),
                selectedTop = Color(0xFF1757FF),
                selectedBottom = Color(0xFF0B45D8),
                selectedBorder = Color(0xFF1757FF),
                selectedText = Color.White,
                dayTop = Color.White,
                dayBottom = Color(0xFFF7F7F7),
                navSurface = Color.White,
                navBorder = Color(0x22111111),
                titleFont = FontFamily.SansSerif,
                titleWeight = FontWeight.Black,
                cardRadius = 0.dp,
                calendarRadius = 0.dp,
                dayRadius = 0.dp,
                buttonRadius = 0.dp,
                shadow = 0.dp,
            )

        CleanVisualTheme.ICE_GLASS ->
            CleanThemeSpec(
                backgroundTop = Color(0xFF1C5792),
                backgroundBottom = Color(0xFF0D274F),
                overlayTop = Color(0x224CB5FF),
                overlayBottom = Color(0x66112B51),
                panelTop = Color(0x66E7F5FF),
                panelMid = Color(0x555C8DB7),
                panelBottom = Color(0x66365F88),
                border = Color(0x99E6F6FF),
                text = Color(0xFFF6FCFF),
                muted = Color(0xFFCAE2F5),
                accent = Color(0xFF8ED4FF),
                accentStrong = Color(0xFFB6E6FF),
                secondary = Color(0xFFB49CFF),
                info = Color(0xFF9DDAFF),
                warning = Color(0xFFFFD083),
                selectedTop = Color(0xFF7DBDFF),
                selectedBottom = Color(0xFF4675F0),
                selectedBorder = Color(0xFFE8F7FF),
                selectedText = Color.White,
                dayTop = Color(0x44F1FAFF),
                dayBottom = Color(0x22376791),
                navSurface = Color(0x883A6B94),
                navBorder = Color(0x99E6F6FF),
                titleFont = FontFamily.SansSerif,
                titleWeight = FontWeight.SemiBold,
                cardRadius = 25.dp,
                calendarRadius = 30.dp,
                dayRadius = 18.dp,
                buttonRadius = 50.dp,
                shadow = 8.dp,
            )

        CleanVisualTheme.BIOPHILIC ->
            CleanThemeSpec(
                backgroundTop = Color(0xFF17372D), backgroundBottom = Color(0xFF071A14),
                overlayTop = Color(0x183D7A55), overlayBottom = Color(0x66030B08),
                panelTop = Color(0xDD314A3A), panelMid = Color(0xEE22382D), panelBottom = Color(0xF5162A21),
                border = Color(0x886F9677), text = Color(0xFFF3F0DF), muted = Color(0xFFC5C8B3),
                accent = Color(0xFF7FB06F), accentStrong = Color(0xFFA6D28E), secondary = Color(0xFFC19A64),
                info = Color(0xFF8CB7A0), warning = Color(0xFFE1A65E),
                selectedTop = Color(0xFF7FAE6E), selectedBottom = Color(0xFF4E7A4D), selectedBorder = Color(0xFFD2E8C5), selectedText = Color.White,
                dayTop = Color(0xFF355040), dayBottom = Color(0xFF1C3228), navSurface = Color(0xF212261D), navBorder = Color(0x776F9677),
                titleFont = FontFamily.Serif, titleWeight = FontWeight.SemiBold,
                cardRadius = 18.dp, calendarRadius = 22.dp, dayRadius = 11.dp, buttonRadius = 24.dp, shadow = 8.dp,
            )

        CleanVisualTheme.RETRO_70S ->
            CleanThemeSpec(
                backgroundTop = Color(0xFFB88B5B), backgroundBottom = Color(0xFF5B3A25),
                overlayTop = Color(0x22F7E7C2), overlayBottom = Color(0x55351F14),
                panelTop = Color(0xFFF4E5C5), panelMid = Color(0xFFE8D2A9), panelBottom = Color(0xFFD7BC8D),
                border = Color(0x886B482E), text = Color(0xFF3B281D), muted = Color(0xFF765E49),
                accent = Color(0xFF8D3F32), accentStrong = Color(0xFF713026), secondary = Color(0xFF5F7550),
                info = Color(0xFF6C6E86), warning = Color(0xFFA66B35),
                selectedTop = Color(0xFFB95A45), selectedBottom = Color(0xFF7D352C), selectedBorder = Color(0xFFF2D6A6), selectedText = Color.White,
                dayTop = Color(0xFFF6E8C9), dayBottom = Color(0xFFE3C99C), navSurface = Color(0xFFE9D3AB), navBorder = Color(0x886B482E),
                titleFont = FontFamily.Serif, titleWeight = FontWeight.Medium,
                cardRadius = 10.dp, calendarRadius = 12.dp, dayRadius = 6.dp, buttonRadius = 10.dp, shadow = 5.dp,
            )

        CleanVisualTheme.CLAY ->
            CleanThemeSpec(
                backgroundTop = Color(0xFFFFE2D6),
                backgroundBottom = Color(0xFFE6DEFF),
                overlayTop = Color.Transparent,
                overlayBottom = Color(0x12FFFFFF),
                panelTop = Color(0xFFFFF6F0),
                panelMid = Color(0xFFF9EBE4),
                panelBottom = Color(0xFFF0E4F4),
                border = Color(0x2A6E5B78),
                text = Color(0xFF3D2B68),
                muted = Color(0xFF746589),
                accent = Color(0xFF8B55F7),
                accentStrong = Color(0xFF9C67FF),
                secondary = Color(0xFF7E63D6),
                info = Color(0xFF5DAED7),
                warning = Color(0xFFFF7D8E),
                selectedTop = Color(0xFFAB78FF),
                selectedBottom = Color(0xFF7A43E3),
                selectedBorder = Color(0xFFE5D5FF),
                selectedText = Color.White,
                dayTop = Color(0xFFFFFAF5),
                dayBottom = Color(0xFFF1E6DF),
                navSurface = Color(0xFFFFF4EE),
                navBorder = Color(0x2A6E5B78),
                titleFont = FontFamily.SansSerif,
                titleWeight = FontWeight.Bold,
                cardRadius = 28.dp,
                calendarRadius = 34.dp,
                dayRadius = 20.dp,
                buttonRadius = 50.dp,
                shadow = 9.dp,
            )

        CleanVisualTheme.CYBERPUNK ->
            CleanThemeSpec(
                backgroundTop = Color(0xFF090B28), backgroundBottom = Color(0xFF01020C),
                overlayTop = Color(0x222A3CFF), overlayBottom = Color(0x77000010),
                panelTop = Color(0x553A4A91), panelMid = Color(0xCC10163B), panelBottom = Color(0xEE080B22),
                border = Color(0xAA6677FF), text = Color(0xFFF0F2FF), muted = Color(0xFFADB5E8),
                accent = Color(0xFF8B5CFF), accentStrong = Color(0xFFB77CFF), secondary = Color(0xFF38CFFF),
                info = Color(0xFF6FE5FF), warning = Color(0xFFFFC75A),
                selectedTop = Color(0xFFB14DFF), selectedBottom = Color(0xFF5032D8), selectedBorder = Color(0xFF80DFFF), selectedText = Color.White,
                dayTop = Color(0x443E56B8), dayBottom = Color(0xCC0D1230), navSurface = Color(0xF2070A21), navBorder = Color(0x996677FF),
                titleFont = FontFamily.SansSerif, titleWeight = FontWeight.Bold,
                cardRadius = 16.dp, calendarRadius = 20.dp, dayRadius = 12.dp, buttonRadius = 18.dp, shadow = 8.dp,
            )
    }

internal val LocalCleanVisualTheme = staticCompositionLocalOf { CleanVisualTheme.CURRENT }
internal val LocalCleanThemeSpec = staticCompositionLocalOf { cleanThemeSpec(CleanVisualTheme.CURRENT) }

@Composable
internal fun CleanThemeProvider(
    theme: CleanVisualTheme,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalCleanVisualTheme provides theme,
        LocalCleanThemeSpec provides cleanThemeSpec(theme),
        content = content,
    )
}

@Composable
internal fun CleanThemeBackdrop(
    theme: CleanVisualTheme,
    modifier: Modifier = Modifier,
) {
    val spec = cleanThemeSpec(theme)
    Canvas(modifier) {
        when (theme) {
            CleanVisualTheme.CURRENT -> Unit

            CleanVisualTheme.NORDIC_DAY_PLANNER -> {
                drawCircle(
                    color = Color.White.copy(alpha = .18f),
                    radius = size.width * .34f,
                    center = Offset(size.width * .86f, size.height * .10f),
                )
            }

            CleanVisualTheme.NORDIC_DAY_PLANNER_DARK -> {
                // Keep the Nordic layout unchanged. Dark mode is color-only.
            }


            CleanVisualTheme.PURE_CALENDAR, CleanVisualTheme.EDITORIAL_PLANNER -> Unit

            CleanVisualTheme.MIDNIGHT_GOLD, CleanVisualTheme.CRYSTAL_GLASS,
            CleanVisualTheme.PASTEL_FLOW, CleanVisualTheme.NEON_PULSE,
            CleanVisualTheme.EARTH_SAGE, CleanVisualTheme.FOREST_PRO,
            CleanVisualTheme.FAMILY_SPECTRUM -> {
                drawCircle(spec.accent.copy(alpha = .12f), size.width * .38f,
                    Offset(size.width * .93f, size.height * .08f))
                drawCircle(spec.secondary.copy(alpha = .09f), size.width * .33f,
                    Offset(size.width * .04f, size.height * .92f))
            }

            CleanVisualTheme.RETRO_DIGITAL -> {
                drawRect(spec.secondary.copy(alpha = .22f),
                    topLeft = Offset(0f, size.height * .06f),
                    size = androidx.compose.ui.geometry.Size(size.width * .028f, size.height * .18f))
            }

            CleanVisualTheme.OAK_WOOD, CleanVisualTheme.GOTHAM_NIGHT,
            CleanVisualTheme.COPPER, CleanVisualTheme.CARBON_FIBER,
            CleanVisualTheme.TITANIUM, CleanVisualTheme.HIPPIE -> Unit

            CleanVisualTheme.BRUTALIST -> {
                drawRect(
                    color = spec.accent.copy(alpha = .95f),
                    topLeft = Offset(size.width * .80f, 0f),
                    size = androidx.compose.ui.geometry.Size(size.width * .20f, size.height * .14f),
                )
                drawRect(
                    color = Color.White.copy(alpha = .06f),
                    topLeft = Offset(0f, size.height * .72f),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height * .025f),
                )
            }

            CleanVisualTheme.JAPANDI -> {
                drawCircle(
                    color = spec.accent.copy(alpha = .10f),
                    radius = size.width * .38f,
                    center = Offset(size.width * .92f, size.height * .08f),
                )
                drawCircle(
                    color = Color(0xFFD8C7A8).copy(alpha = .16f),
                    radius = size.width * .26f,
                    center = Offset(size.width * .08f, size.height * .92f),
                )
            }

            CleanVisualTheme.LUXURY_GOLD -> {
                drawCircle(
                    color = spec.accent.copy(alpha = .12f),
                    radius = size.width * .52f,
                    center = Offset(size.width * .75f, size.height * .12f),
                )
                drawCircle(
                    color = spec.accent.copy(alpha = .06f),
                    radius = size.width * .62f,
                    center = Offset(size.width * .1f, size.height * .85f),
                )
            }

            CleanVisualTheme.MEMPHIS -> {
                drawCircle(Color(0xFFFFD62E).copy(alpha = .85f), size.width * .18f, Offset(size.width * .94f, size.height * .06f))
                drawCircle(Color(0xFFFF6EB5).copy(alpha = .60f), size.width * .13f, Offset(size.width * .14f, size.height * .20f))
                drawRect(
                    color = Color(0xFF2F91F3).copy(alpha = .35f),
                    topLeft = Offset(-size.width * .05f, size.height * .60f),
                    size = androidx.compose.ui.geometry.Size(size.width * .20f, size.height * .20f),
                )
                drawCircle(Color(0xFF55C99A).copy(alpha = .45f), size.width * .16f, Offset(size.width * .92f, size.height * .86f))
            }

            CleanVisualTheme.SWISS -> {
                drawRect(
                    color = spec.accent,
                    topLeft = Offset(0f, size.height * .015f),
                    size = androidx.compose.ui.geometry.Size(size.width * .055f, size.height * .17f),
                )
                drawRect(
                    color = Color.Black.copy(alpha = .06f),
                    topLeft = Offset(size.width * .72f, size.height * .73f),
                    size = androidx.compose.ui.geometry.Size(size.width * .28f, size.height * .03f),
                )
            }

            CleanVisualTheme.ICE_GLASS -> {
                drawCircle(Color.White.copy(alpha = .12f), size.width * .34f, Offset(size.width * .86f, size.height * .10f))
                drawCircle(Color(0xFFBBDFFF).copy(alpha = .10f), size.width * .50f, Offset(size.width * .1f, size.height * .72f))
            }

            CleanVisualTheme.BIOPHILIC -> {
                drawCircle(spec.accent.copy(alpha = .13f), size.width * .26f, Offset(size.width * .88f, size.height * .10f))
                drawCircle(Color(0xFFC47F61).copy(alpha = .10f), size.width * .18f, Offset(size.width * .12f, size.height * .70f))
                drawCircle(spec.secondary.copy(alpha = .10f), size.width * .30f, Offset(size.width * .82f, size.height * .90f))
            }

            CleanVisualTheme.RETRO_70S -> {
                drawCircle(Color(0xFFFFC43A).copy(alpha = .85f), size.width * .42f, Offset(size.width * .82f, size.height * .04f))
                drawCircle(Color(0xFFE76F2E).copy(alpha = .85f), size.width * .30f, Offset(size.width * .82f, size.height * .04f))
                drawCircle(Color(0xFF7A472A).copy(alpha = .75f), size.width * .19f, Offset(size.width * .82f, size.height * .04f))
            }

            CleanVisualTheme.CLAY -> {
                drawCircle(Color(0xFFFFB9B0).copy(alpha = .35f), size.width * .32f, Offset(size.width * .90f, size.height * .08f))
                drawCircle(Color(0xFFC3B3FF).copy(alpha = .28f), size.width * .34f, Offset(size.width * .08f, size.height * .78f))
                drawCircle(Color(0xFFA8DEC3).copy(alpha = .22f), size.width * .24f, Offset(size.width * .88f, size.height * .90f))
            }

            CleanVisualTheme.CYBERPUNK -> {
                val grid = size.width / 8f
                var x = 0f
                while (x <= size.width) {
                    drawLine(
                        color = spec.secondary.copy(alpha = .09f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f,
                    )
                    x += grid
                }
                var y = 0f
                while (y <= size.height) {
                    drawLine(
                        color = spec.accent.copy(alpha = .07f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                    y += grid
                }
                drawCircle(spec.accent.copy(alpha = .16f), size.width * .35f, Offset(size.width * .90f, size.height * .08f))
            }
        }
    }
}
