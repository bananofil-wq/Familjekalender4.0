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
    BRUTALIST(
        "Brutalist",
        "Svart, vitt och rött med hårda former och stor typografi.",
        "◼",
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
        "Memphis",
        "Lekfull färg, geometriska former och energiska accenter.",
        "●",
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
        "Nature",
        "Organiska former i salvia, lera och varma naturtoner.",
        "⌁",
    ),
    RETRO_70S(
        "70-tal",
        "Bränd orange, senap och brun retro med mjuka former.",
        "☀",
    ),
    CLAY(
        "Clay",
        "Mjuk 3D-känsla med pastell, rundade knappar och taktil yta.",
        "◍",
    ),
    CYBERPUNK(
        "Cyberpunk",
        "Mörk HUD-look med cyan, magenta och holografiska kontraster.",
        "⌬",
    ),
}

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

        CleanVisualTheme.BRUTALIST ->
            CleanThemeSpec(
                backgroundTop = Color(0xFF080808),
                backgroundBottom = Color(0xFF000000),
                overlayTop = Color.Transparent,
                overlayBottom = Color.Transparent,
                panelTop = Color(0xFFF6F6F3),
                panelMid = Color(0xFFF6F6F3),
                panelBottom = Color(0xFFEDEDEA),
                border = Color(0xFF111111),
                text = Color(0xFF0A0A0A),
                muted = Color(0xFF4B4B4B),
                accent = Color(0xFFFF2A25),
                accentStrong = Color(0xFFFF2A25),
                secondary = Color(0xFF111111),
                info = Color(0xFFFF2A25),
                warning = Color(0xFFFF2A25),
                selectedTop = Color(0xFFFF2A25),
                selectedBottom = Color(0xFFE31512),
                selectedBorder = Color(0xFF0A0A0A),
                selectedText = Color(0xFF0A0A0A),
                dayTop = Color(0xFFF8F8F5),
                dayBottom = Color(0xFFEFEFEC),
                navSurface = Color(0xFFF7F7F4),
                navBorder = Color(0xFF111111),
                titleFont = FontFamily.SansSerif,
                titleWeight = FontWeight.Black,
                cardRadius = 2.dp,
                calendarRadius = 2.dp,
                dayRadius = 0.dp,
                buttonRadius = 0.dp,
                shadow = 0.dp,
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
                backgroundTop = Color(0xFFFFFBF3),
                backgroundBottom = Color(0xFFF4F0FF),
                overlayTop = Color.Transparent,
                overlayBottom = Color.Transparent,
                panelTop = Color(0xFFFFFDF8),
                panelMid = Color(0xFFFFFBF4),
                panelBottom = Color(0xFFF5F1FF),
                border = Color(0x221A2457),
                text = Color(0xFF15205A),
                muted = Color(0xFF596184),
                accent = Color(0xFF8247FF),
                accentStrong = Color(0xFF7540F6),
                secondary = Color(0xFF1E9C73),
                info = Color(0xFF2C8EEB),
                warning = Color(0xFFFF8B4D),
                selectedTop = Color(0xFF9457FF),
                selectedBottom = Color(0xFF6931E8),
                selectedBorder = Color(0xFFD9C9FF),
                selectedText = Color.White,
                dayTop = Color(0xFFFFFEFB),
                dayBottom = Color(0xFFF8F4EE),
                navSurface = Color(0xFFFDFBF8),
                navBorder = Color(0x221A2457),
                titleFont = FontFamily.SansSerif,
                titleWeight = FontWeight.ExtraBold,
                cardRadius = 25.dp,
                calendarRadius = 32.dp,
                dayRadius = 16.dp,
                buttonRadius = 50.dp,
                shadow = 7.dp,
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
                backgroundTop = Color(0xFFF2EDDF),
                backgroundBottom = Color(0xFFD8D4C2),
                overlayTop = Color.Transparent,
                overlayBottom = Color(0x166A765E),
                panelTop = Color(0xFFF7F0DF),
                panelMid = Color(0xFFECE4D2),
                panelBottom = Color(0xFFE2DAC6),
                border = Color(0x2A4C5D49),
                text = Color(0xFF263728),
                muted = Color(0xFF667261),
                accent = Color(0xFF71866E),
                accentStrong = Color(0xFF536B54),
                secondary = Color(0xFFA99772),
                info = Color(0xFF839C9A),
                warning = Color(0xFFC37C5E),
                selectedTop = Color(0xFF71866E),
                selectedBottom = Color(0xFF4B654E),
                selectedBorder = Color(0xFFD9E3D2),
                selectedText = Color.White,
                dayTop = Color(0xFFF9F5EA),
                dayBottom = Color(0xFFEAE2D0),
                navSurface = Color(0xFFF0E8D8),
                navBorder = Color(0x2A4C5D49),
                titleFont = FontFamily.Serif,
                titleWeight = FontWeight.SemiBold,
                cardRadius = 30.dp,
                calendarRadius = 38.dp,
                dayRadius = 22.dp,
                buttonRadius = 50.dp,
                shadow = 5.dp,
            )

        CleanVisualTheme.RETRO_70S ->
            CleanThemeSpec(
                backgroundTop = Color(0xFFD06C25),
                backgroundBottom = Color(0xFF5F321E),
                overlayTop = Color(0x14FFF0CA),
                overlayBottom = Color(0x335B2D18),
                panelTop = Color(0xFFFFE6AA),
                panelMid = Color(0xFFF1C77A),
                panelBottom = Color(0xFFE4A84D),
                border = Color(0x884A2418),
                text = Color(0xFF542315),
                muted = Color(0xFF76503C),
                accent = Color(0xFFE85F24),
                accentStrong = Color(0xFFD94A17),
                secondary = Color(0xFF789056),
                info = Color(0xFFB16B2F),
                warning = Color(0xFFA94822),
                selectedTop = Color(0xFFE96828),
                selectedBottom = Color(0xFFC84C18),
                selectedBorder = Color(0xFFFFD98D),
                selectedText = Color.White,
                dayTop = Color(0xFFFFEBC1),
                dayBottom = Color(0xFFF2D293),
                navSurface = Color(0xFFFFD98C),
                navBorder = Color(0x884A2418),
                titleFont = FontFamily.Serif,
                titleWeight = FontWeight.Black,
                cardRadius = 28.dp,
                calendarRadius = 30.dp,
                dayRadius = 18.dp,
                buttonRadius = 50.dp,
                shadow = 6.dp,
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
                backgroundTop = Color(0xFF060A19),
                backgroundBottom = Color(0xFF02030A),
                overlayTop = Color(0x2200E7FF),
                overlayBottom = Color(0x55000000),
                panelTop = Color(0x5520DFFF),
                panelMid = Color(0xCC071429),
                panelBottom = Color(0xEE030914),
                border = Color(0xCC13E7FF),
                text = Color(0xFFD8FBFF),
                muted = Color(0xFF86B8C8),
                accent = Color(0xFFFF37D4),
                accentStrong = Color(0xFFFF4BE1),
                secondary = Color(0xFF18E9FF),
                info = Color(0xFF18E9FF),
                warning = Color(0xFFFFB13D),
                selectedTop = Color(0xFFFF34DC),
                selectedBottom = Color(0xFF8B2BFF),
                selectedBorder = Color(0xFF4CFAFF),
                selectedText = Color.White,
                dayTop = Color(0x3318E9FF),
                dayBottom = Color(0xCC07101F),
                navSurface = Color(0xF2050A15),
                navBorder = Color(0xAA18E9FF),
                titleFont = FontFamily.Monospace,
                titleWeight = FontWeight.Bold,
                cardRadius = 8.dp,
                calendarRadius = 10.dp,
                dayRadius = 7.dp,
                buttonRadius = 8.dp,
                shadow = 4.dp,
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
