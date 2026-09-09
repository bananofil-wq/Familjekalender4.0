package se.familjekalender.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalTextStyle

@Composable
internal fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    val marker = text == "🌈" || text == "★"
    val rainbow = text == "🌈"
    val effectiveFontSize = if (rainbow && fontSize != TextUnit.Unspecified && fontSize.value < 15f) 15.sp else fontSize
    val markerLineHeight = when {
        effectiveFontSize == TextUnit.Unspecified -> 18.sp
        effectiveFontSize.value >= 18f -> 21.sp
        else -> 18.sp
    }

    androidx.compose.material3.Text(
        text = text,
        modifier = if (marker) {
            modifier
                .wrapContentSize(unbounded = true)
                .offset(y = (-2).dp)
        } else modifier,
        color = color,
        fontSize = effectiveFontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = if (marker) markerLineHeight else lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = if (marker) 1 else maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}
