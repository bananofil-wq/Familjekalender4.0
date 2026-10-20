package se.familjekalender.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A real calendar-themed alert, not the default Android Material purple dialog.
 * The approved oak look reuses the exact photographed wood material atlas.
 */
@Composable
internal fun FamilyEventPopupDialog(
    theme: CleanVisualTheme,
    event: FamilyEventPopup.Event,
    onClose: () -> Unit,
    onShowEvent: () -> Unit,
) {
    val spec = cleanThemeSpec(theme)
    val oak = theme == CleanVisualTheme.OAK_WOOD
    val premium = isPremiumMaterialTheme(theme)
    val light = isLightCleanTheme(theme) && !oak
    val gold = if (oak) Color(0xFFE1B679) else spec.accentStrong
    val frame = if (oak) Color(0xFF2E190E) else spec.panelBottom
    val mainInk = if (oak) Color(0xFFFFE8C8) else spec.text
    val quietInk = if (oak) Color(0xFFDEC298) else spec.muted
    val innerInk = if (oak) Color(0xFF402718) else spec.text
    val innerBg = when {
        oak -> Color(0xFFF2DCB8)
        light -> Color.White.copy(alpha = .88f)
        else -> spec.panelMid
    }
    val eventName = event.message
        .replace(Regex("^(Ny aktivitet|Ny händelse)\\s*:\\s*", RegexOption.IGNORE_CASE), "")
        .trim().ifEmpty { event.message.ifBlank { "En ny aktivitet har lagts till." } }

    // No swipe, backdrop or system back dismissal: every queued item must be acknowledged.
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        CompositionLocalProvider(LocalPremiumMaterialTheme provides theme) {
            val shape = RoundedCornerShape(if (oak) 23.dp else spec.cardRadius)
            Surface(
                modifier = Modifier.fillMaxWidth(.91f).widthIn(max = 430.dp),
                color = frame,
                shape = shape,
                shadowElevation = 24.dp,
                border = BorderStroke(if (oak) 2.dp else 1.dp, if (oak) gold else spec.border),
                tonalElevation = 0.dp,
            ) {
                Box(Modifier.clip(shape)) {
                    if (premium) {
                        PremiumMaterialSurface(
                            Modifier.matchParentSize(),
                            opacity = 1f,
                            material = OakPhotoMaterial.DARK,
                        )
                    } else {
                        Box(
                            Modifier.matchParentSize().background(
                                Brush.verticalGradient(listOf(spec.panelTop, spec.panelBottom))
                            )
                        )
                    }
                    // A little vignetting keeps the photographed oak legible behind the text.
                    if (oak) {
                        Box(
                            Modifier.matchParentSize().background(
                                Brush.verticalGradient(
                                    listOf(Color(0x442C1408), Color(0x99200E07))
                                )
                            )
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = 20.dp,
                            vertical = 22.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                if (oak) Color(0xFF80502C) else spec.selectedTop,
                                                if (oak) Color(0xFF3A2011) else spec.selectedBottom,
                                            )
                                        )
                                    )
                                    .border(1.dp, gold.copy(alpha = .8f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = if (oak) Color(0xFFFFE3B3) else spec.selectedText,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                            Spacer(Modifier.width(13.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    "FAMILJEKALENDERN",
                                    fontSize = 10.sp,
                                    letterSpacing = 2.sp,
                                    color = gold,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Ny aktivitet",
                                    fontFamily = if (oak) FontFamily.Serif else spec.titleFont,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = mainInk,
                                )
                            }
                        }

                        Box(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(if (oak) 15.dp else 13.dp))
                                .background(innerBg)
                                .border(
                                    1.dp,
                                    if (oak) Color(0xFFB68C56) else spec.border.copy(alpha = .6f),
                                    RoundedCornerShape(if (oak) 15.dp else 13.dp),
                                )
                        ) {
                            if (oak) {
                                PremiumMaterialSurface(
                                    Modifier.matchParentSize(),
                                    opacity = .82f,
                                    material = OakPhotoMaterial.PALE,
                                )
                                Box(
                                    Modifier.matchParentSize().background(Color(0x6EFFE5B7))
                                )
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(19.dp),
                                verticalArrangement = Arrangement.spacedBy(9.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = if (oak) Color(0xFF84532A) else spec.accent,
                                        modifier = Modifier.size(17.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "NYTT I KALENDERN",
                                        fontSize = 10.sp,
                                        letterSpacing = 1.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (oak) Color(0xFF84532A) else spec.muted,
                                    )
                                }
                                Text(
                                    eventName,
                                    fontSize = 23.sp,
                                    lineHeight = 29.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = if (oak) FontFamily.Serif else spec.titleFont,
                                    color = innerInk,
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                Modifier.weight(1f).height(52.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(
                                        if (oak) Color(0xC6382113) else spec.panelMid
                                    )
                                    .border(
                                        1.dp, if (oak) gold.copy(alpha = .55f) else spec.border,
                                        RoundedCornerShape(13.dp),
                                    )
                                    .clickable(onClick = onClose),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "Stäng",
                                    color = mainInk,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                )
                            }

                            Box(
                                Modifier.weight(1.65f).height(52.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            if (oak) listOf(Color(0xFFFFD69B), Color(0xFFC28747))
                                            else listOf(spec.selectedTop, spec.selectedBottom),
                                        )
                                    )
                                    .border(
                                        1.dp,
                                        if (oak) Color(0xFFFFE3B5) else spec.selectedBorder,
                                        RoundedCornerShape(13.dp),
                                    )
                                    .clickable(onClick = onShowEvent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        "Visa händelse",
                                        color = if (oak) Color(0xFF382013) else spec.selectedText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = if (oak) Color(0xFF382013) else spec.selectedText,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            "Stäng eller öppna händelsen för att fortsätta.",
                            color = quietInk,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
