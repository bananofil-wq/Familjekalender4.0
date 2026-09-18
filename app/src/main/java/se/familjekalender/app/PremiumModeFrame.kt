package se.familjekalender.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val PremiumGlass = Color(0xB8171422)
internal val PremiumGlassSoft = Color(0xA61C1828)
internal val PremiumGlassRaised = Color(0xCC211A2D)
internal val PremiumBorder = Color.White.copy(alpha = .13f)
internal val PremiumMuted = Color.White.copy(alpha = .67f)
internal val PremiumPurple = Color(0xFF8A5CF6)
internal val PremiumPurpleBright = Color(0xFFAA72FF)

@Composable
internal fun PremiumModeBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.season_autumn),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x44120B16),
                            Color(0x70120C19),
                            Color(0xAA110D18),
                        )
                    )
                )
        )
        content()
    }
}

@Composable
internal fun PremiumModeHeader(
    title: String,
    subtitle: String,
    onAdd: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = FontFamily.Cursive,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "♡",
                    color = PremiumPurpleBright,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = subtitle.uppercase(),
                color = PremiumMuted,
                fontSize = 9.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onSettings != null) {
                Box(
                    Modifier.size(43.dp)
                        .clip(CircleShape)
                        .background(Color(0x661C1726))
                        .clickable(onClick = onSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Inställningar",
                        tint = Color.White,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            if (onAdd != null) {
                Box(
                    Modifier.size(50.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(PremiumPurpleBright, PremiumPurple))
                        )
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Lägg till aktivitet",
                        tint = Color.White,
                        modifier = Modifier.size(27.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun PremiumGlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = PremiumGlass,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, PremiumBorder),
        shadowElevation = 0.dp,
    ) {
        Column(content = content)
    }
}
