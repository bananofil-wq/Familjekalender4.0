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
}

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
