package com.ophistreamer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// OphiStreamer SteamOS-inspired palette (PLAN.md 2b). Same values as res/values/colors.xml (ophi_*).
val OphiBgTop = Color(0xFF1B2838)
val OphiBgBottom = Color(0xFF0B0F14)
val OphiCard = Color(0xFF151C25)
val OphiCardRaised = Color(0xFF1F2833)
val OphiBorder = Color(0xFF26313E)
val OphiDivider = Color(0xFF212B37)
val OphiText = Color(0xFFDCDEDF)
val OphiMuted = Color(0xFF8B98A5)
val OphiAccent = Color(0xFF1A9FFF)
val OphiAccentDeep = Color(0xFF0B6FD1)
val OphiGood = Color(0xFF59BF40)
val OphiWarn = Color(0xFFE6B450)
val OphiBad = Color(0xFFE5534B)
val OphiHeroTop = Color(0xFF2A475E)

val OphiCardShape = RoundedCornerShape(6.dp)
val OphiButtonShape = RoundedCornerShape(4.dp)

@Composable
fun OphiBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(OphiBgTop, OphiBgBottom))),
    ) { content() }
}

/** White ring around whatever a gamepad or keyboard has focused (SteamOS-style focus). */
@Composable
fun Modifier.focusRing(shape: Shape = OphiButtonShape): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
}

@Composable
fun OphiCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = OphiMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp,
        modifier = modifier.padding(start = 2.dp, bottom = 6.dp),
    )
}

@Composable
fun OphiCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .border(1.dp, OphiBorder, OphiCardShape)
            .background(OphiCard, OphiCardShape),
        content = content,
    )
}

@Composable
fun OphiRow(
    label: String,
    value: String,
    valueColor: Color = OphiText,
    sub: String? = null,
    last: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = OphiText, fontSize = 14.sp)
            if (sub != null) Text(sub, color = OphiMuted, fontSize = 11.sp)
        }
        if (value.isNotEmpty()) {
            Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
        }
    }
    if (!last) HorizontalDivider(color = OphiDivider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 14.dp))
}

@Composable
fun OphiPill(text: String, color: Color) {
    Row(
        Modifier
            .background(color.copy(alpha = 0.16f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(text.uppercase(), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
fun RowScope.OphiStat(value: String, label: String, color: Color = OphiText) {
    Column(
        Modifier
            .weight(1f)
            .background(Color.Black.copy(alpha = 0.28f), OphiCardShape)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = OphiMuted, fontSize = 9.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun OphiPrimaryButton(
    text: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    height: Dp = 52.dp,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.45f)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusRing()
            .background(Brush.horizontalGradient(listOf(OphiAccent, OphiAccentDeep)), OphiButtonShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    }
}

@Composable
fun OphiSecondaryButton(
    text: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    height: Dp = 42.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.45f)
            .focusRing()
            .border(1.dp, OphiBorder, OphiButtonShape)
            .background(OphiCardRaised, OphiButtonShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = OphiText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Segmented control (SteamOS-style pill group). */
@Composable
fun <T> OphiSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier
            .background(OphiBgBottom, OphiButtonShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (o in options) {
            val isSel = o == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(32.dp)
                    .focusRing()
                    .background(if (isSel) OphiAccent else Color.Transparent, OphiButtonShape)
                    .clickable { onSelect(o) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(o), color = if (isSel) Color.White else OphiMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
