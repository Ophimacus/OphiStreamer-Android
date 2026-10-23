package com.ophistreamer.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val ToggleOff = Color(0xFF3D4450)
private val TrackOff = Color(0xFF2E3742)

/** SteamOS-style toggle: grey pill when off, blue with a white knob when on. Visual only; the row handles taps. */
@Composable
fun OphiToggle(checked: Boolean) {
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, label = "toggle")
    Box(
        Modifier
            .size(width = 44.dp, height = 24.dp)
            .background(if (checked) OphiAccent else ToggleOff, RoundedCornerShape(12.dp))
            .padding(3.dp),
    ) {
        Box(Modifier.offset(x = x).size(18.dp).background(if (checked) Color.White else Color(0xFFB8BEC4), CircleShape))
    }
}

/**
 * SteamOS-style slider: thin track, round thumb, no tick marks. Drag or tap it; with a controller,
 * focus it and press left/right. [onChange] gets values snapped to [step].
 */
@Composable
fun OphiSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
) {
    val change by rememberUpdatedState(onChange)
    val current by rememberUpdatedState(value)
    var focused by remember { mutableStateOf(false) }
    val span = range.endInclusive - range.start
    fun snap(v: Float) = (range.start + ((v - range.start) / step).roundToInt() * step).coerceIn(range)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (it.key) {
                    Key.DirectionLeft -> { change(snap(current - step)); true }
                    Key.DirectionRight -> { change(snap(current + step)); true }
                    else -> false
                }
            }
            .focusable()
            .pointerInput(range) {
                detectTapGestures { change(snap(range.start + span * (it.x / size.width))) }
            }
            .pointerInput(range) {
                detectHorizontalDragGestures { c, _ -> change(snap(range.start + span * (c.position.x / size.width).coerceIn(0f, 1f))) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val frac = ((value - range.start) / span).coerceIn(0f, 1f)
        val thumb = 18.dp
        Box(Modifier.fillMaxWidth().height(4.dp).background(TrackOff, RoundedCornerShape(2.dp)))
        Box(Modifier.fillMaxWidth(frac).height(4.dp).background(OphiAccent, RoundedCornerShape(2.dp)))
        Box(
            Modifier
                .offset(x = (maxWidth - thumb) * frac)
                .size(thumb)
                .border(if (focused) 3.dp else 0.dp, if (focused) OphiAccent else Color.Transparent, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/** A titled group of rows on a card, like a SteamOS Quick Access section. */
@Composable
fun OphiSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        OphiCaption(title)
        Column(Modifier.fillMaxWidth().background(OphiCard, OphiCardShape), content = content)
    }
}

@Composable
fun OphiDividerLine() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(OphiDivider))
}

/** Label on the left, a toggle on the right; the whole row is the tap/focus target. */
@Composable
fun OphiToggleRow(label: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .focusRing(OphiCardShape)
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = OphiText, fontSize = 14.sp)
            if (sub != null) Text(sub, color = OphiMuted, fontSize = 11.sp)
        }
        OphiToggle(checked)
    }
}

/** Label with the current value on the right, and the control underneath. */
@Composable
fun OphiControlRow(label: String, value: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = OphiText, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (value != null) Text(value, color = OphiMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

/** A row that opens something: label, optional detail, and a chevron. */
@Composable
fun OphiLinkRow(label: String, detail: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .focusRing(OphiCardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = OphiText, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, color = OphiMuted, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
        Text("›", color = OphiMuted, fontSize = 20.sp)
    }
}

/** A choice in a list: a blue check on the selected one. */
@Composable
fun OphiChoiceRow(label: String, selected: Boolean, detail: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .focusRing(OphiCardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (selected) Color.White else OphiText,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (detail != null) Text(detail, color = OphiMuted, fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
        Box(
            Modifier
                .size(20.dp)
                .border(2.dp, if (selected) OphiAccent else ToggleOff, CircleShape)
                .padding(4.dp)
                .background(if (selected) OphiAccent else Color.Transparent, CircleShape),
        )
    }
}

/** Small flat text button for secondary actions inside a section. */
@Composable
fun OphiTextButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .height(36.dp)
            .focusRing()
            .background(OphiCardRaised, OphiButtonShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) OphiText else OphiMuted.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** SteamOS tab strip: white label and a blue underline on the selected tab. */
@Composable
fun OphiTabs(tabs: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(modifier.fillMaxWidth()) {
        tabs.forEachIndexed { i, t ->
            val on = i == selected
            Column(
                Modifier
                    .weight(1f)
                    .focusRing()
                    .clickable { onSelect(i) }
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    t,
                    color = if (on) Color.White else OphiMuted,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                )
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(if (on) OphiAccent else OphiDivider),
                )
            }
        }
    }
}
