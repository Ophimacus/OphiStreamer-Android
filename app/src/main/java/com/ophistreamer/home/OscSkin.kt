package com.ophistreamer.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import androidx.preference.PreferenceManager

/**
 * OphiStreamer's on-screen control look (the approved mockup): round face buttons with coloured letters,
 * pill shoulders and triggers, one-piece D-pad with chevrons, ring-and-thumb sticks, icon pills for
 * Select/Start. Artemis keeps positions, sizes and input; this only paints.
 */
object OscSkin {
    private class Style(
        val line: Int,
        val strokeDp: Float,
        val fill: Int,
        val label: Int,
        val press: Int,
        val colouredLetters: Boolean,
    )

    private val LETTER = mapOf("A" to 0xFF59BF40.toInt(), "B" to 0xFFE5534B.toInt(), "X" to 0xFF1A9FFF.toInt(), "Y" to 0xFFE6B450.toInt())
    private const val PRESSED_TEXT = 0xFF0B0F14.toInt()
    private const val THUMB_IDLE = 0x2EFFFFFF

    private fun styleFor(id: String) = when (id) {
        "glass" -> Style(0xBFFFFFFF.toInt(), 1.4f, 0x14FFFFFF, 0xFFFFFFFF.toInt(), 0xFF59BF40.toInt(), true)
        "bold" -> Style(0xFFFFFFFF.toInt(), 3f, 0x8C000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFE6B450.toInt(), true)
        "classic" -> Style(0xFF9AA0A6.toInt(), 1.6f, 0x80464646.toInt(), 0xFFE8EAED.toInt(), 0xFF7A7F86.toInt(), false)
        else -> Style(0xFF8B98A5.toInt(), 1.6f, 0x730E141B, 0xFFDCDEDF.toInt(), 0xFF1A9FFF.toInt(), true)
    }

    // Settings are read at most once a second, not on every frame.
    private var look = styleFor("steamos")
    private var opacity = 0.9f
    private var density = 3f
    private var readAt = 0L

    private fun refresh(context: Context) {
        val now = SystemClock.uptimeMillis()
        if (now - readAt < 1000) return
        readAt = now
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        look = styleFor(OscTheme.current(context))
        opacity = prefs.getInt("seekbar_osc_opacity", 90).coerceIn(10, 100) / 100f
        density = context.resources.displayMetrics.density
    }

    /** Always on: Artemis's own skins are replaced. Kept as a switch so Java can fall through cleanly. */
    @JvmStatic
    fun enabled() = true

    /** Re-read theme and opacity now (after the player changes them). */
    @JvmStatic
    fun invalidate() {
        readAt = 0
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val editPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val path = Path()

    private fun a(color: Int): Int {
        val alpha = ((color ushr 24) * opacity).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (color and 0xFFFFFF)
    }

    private fun stroke() = look.strokeDp * density

    private fun shape(canvas: Canvas, pressed: Boolean, draw: (Paint) -> Unit) {
        fillPaint.color = a(if (pressed) look.press else look.fill)
        draw(fillPaint)
        linePaint.color = a(if (pressed) look.press else look.line)
        linePaint.strokeWidth = stroke()
        draw(linePaint)
    }

    private fun text(canvas: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int) {
        textPaint.textSize = size
        textPaint.color = a(color)
        val m = textPaint.fontMetrics
        canvas.drawText(s, cx, cy - (m.ascent + m.descent) / 2, textPaint)
    }

    private fun editOutline(canvas: Canvas, w: Int, h: Int, editing: Boolean) {
        if (!editing) return
        editPaint.color = 0xCCFFFFFF.toInt()
        editPaint.strokeWidth = density
        editPaint.pathEffect = DashPathEffect(floatArrayOf(6 * density, 4 * density), 0f)
        canvas.drawRect(density, density, w - density, h - density, editPaint)
    }

    /** Face buttons, shoulders, triggers, Select/Start, L3/R3, Guide, Touchpad. */
    @JvmStatic
    fun drawButton(context: Context, canvas: Canvas, w: Int, h: Int, label: String, pressed: Boolean, editing: Boolean) {
        refresh(context)
        val inset = stroke() / 2 + density
        rect.set(inset, inset, w - inset, h - inset)
        val cx = w / 2f
        val cy = h / 2f
        val small = minOf(rect.width(), rect.height())
        when (label) {
            "A", "B", "X", "Y" -> {
                val r = small / 2 * (if (pressed) 0.94f else 1f)
                shape(canvas, pressed) { canvas.drawCircle(cx, cy, r, it) }
                val color = when {
                    pressed -> PRESSED_TEXT
                    look.colouredLetters -> LETTER.getValue(label)
                    else -> look.label
                }
                text(canvas, label, cx, cy, small * 0.48f, color)
            }
            "L3", "R3" -> {
                shape(canvas, pressed) { canvas.drawCircle(cx, cy, small / 2, it) }
                text(canvas, label, cx, cy, small * 0.38f, if (pressed) PRESSED_TEXT else look.label)
            }
            else -> {
                if (pressed) rect.inset(rect.width() * 0.03f, rect.height() * 0.03f)
                val radius = rect.height() / 2
                shape(canvas, pressed) { canvas.drawRoundRect(rect, radius, radius, it) }
                val (shown, scale) = when (label) {
                    "BACK" -> "⧉" to 0.62f
                    "START" -> "≡" to 0.78f
                    "GUIDE" -> "◎" to 0.6f
                    "Trackpad" -> "Touchpad" to 0.34f
                    else -> label to 0.46f
                }
                val size = minOf(rect.height() * scale, rect.width() / (shown.length * 0.62f + 0.6f))
                text(canvas, shown, cx, cy, size, if (pressed) PRESSED_TEXT else look.label)
            }
        }
        editOutline(canvas, w, h, editing)
    }

    /** One-piece D-pad; [direction] is Artemis's bit set (1 left, 2 up, 4 right, 8 down). */
    @JvmStatic
    fun drawPad(context: Context, canvas: Canvas, w: Int, h: Int, direction: Int, editing: Boolean) {
        refresh(context)
        val size = minOf(w, h) - stroke() - 2 * density
        val cx = w / 2f
        val cy = h / 2f
        val arm = size * 0.34f
        val half = size / 2
        val s = arm / 2
        path.reset()
        path.moveTo(cx - s, cy - half); path.lineTo(cx + s, cy - half); path.lineTo(cx + s, cy - s)
        path.lineTo(cx + half, cy - s); path.lineTo(cx + half, cy + s); path.lineTo(cx + s, cy + s)
        path.lineTo(cx + s, cy + half); path.lineTo(cx - s, cy + half); path.lineTo(cx - s, cy + s)
        path.lineTo(cx - half, cy + s); path.lineTo(cx - half, cy - s); path.lineTo(cx - s, cy - s)
        path.close()
        shape(canvas, false) { canvas.drawPath(path, it) }

        // Light up the pressed arm(s).
        fillPaint.color = a(look.press)
        val corner = arm * 0.2f
        if (direction and 2 != 0) { rect.set(cx - s, cy - half, cx + s, cy - s); canvas.drawRoundRect(rect, corner, corner, fillPaint) }
        if (direction and 8 != 0) { rect.set(cx - s, cy + s, cx + s, cy + half); canvas.drawRoundRect(rect, corner, corner, fillPaint) }
        if (direction and 1 != 0) { rect.set(cx - half, cy - s, cx - s, cy + s); canvas.drawRoundRect(rect, corner, corner, fillPaint) }
        if (direction and 4 != 0) { rect.set(cx + s, cy - s, cx + half, cy + s); canvas.drawRoundRect(rect, corner, corner, fillPaint) }

        // Chevrons pointing outwards.
        linePaint.strokeWidth = maxOf(2f * density, size * 0.025f)
        val c = arm * 0.22f
        val d = half - arm * 0.55f
        fun chevron(x: Float, y: Float, rot: Float, on: Boolean) {
            linePaint.color = a(if (on) PRESSED_TEXT else look.label)
            canvas.save()
            canvas.rotate(rot, x, y)
            path.reset()
            path.moveTo(x - c, y + c * 0.55f); path.lineTo(x, y - c * 0.55f); path.lineTo(x + c, y + c * 0.55f)
            canvas.drawPath(path, linePaint)
            canvas.restore()
        }
        chevron(cx, cy - d, 0f, direction and 2 != 0)
        chevron(cx + d, cy, 90f, direction and 4 != 0)
        chevron(cx, cy + d, 180f, direction and 8 != 0)
        chevron(cx - d, cy, 270f, direction and 1 != 0)
        editOutline(canvas, w, h, editing)
    }

    /** Stick: outer ring, and a thumb that follows the finger and lights up while in use. */
    @JvmStatic
    fun drawStick(
        context: Context, canvas: Canvas, w: Int, h: Int,
        outer: Float, thumbX: Float, thumbY: Float, thumbR: Float, active: Boolean, clicked: Boolean, editing: Boolean,
    ) {
        refresh(context)
        val cx = w / 2f
        val cy = h / 2f
        fillPaint.color = a(look.fill)
        canvas.drawCircle(cx, cy, outer, fillPaint)
        linePaint.color = a(if (active || clicked) look.press else look.line)
        linePaint.strokeWidth = stroke()
        canvas.drawCircle(cx, cy, outer, linePaint)

        val x = if (active) thumbX else cx
        val y = if (active) thumbY else cy
        val r = thumbR * 1.25f
        fillPaint.color = a(if (active || clicked) look.press else THUMB_IDLE)
        canvas.drawCircle(x, y, r, fillPaint)
        linePaint.color = a(if (active || clicked) look.press else look.line)
        canvas.drawCircle(x, y, r, linePaint)
        editOutline(canvas, w, h, editing)
    }
}
