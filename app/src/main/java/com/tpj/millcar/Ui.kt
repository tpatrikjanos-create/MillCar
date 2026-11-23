package com.tpj.millcar

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView

// Narancs neon paletta (a Corolla műszerfala alapján)
const val BG = 0xFF07070A.toInt()
const val CARD = 0xFF140C05.toInt()
const val NEON = 0xFFFF8A1E.toInt()
const val FG = 0xFFFFB054.toInt()
const val DIM = 0xFFB0743A.toInt()
const val ACC = 0x40FF8A1E

val NEON_FONT: Typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.neonBox(radiusDp: Int = 18, fill: Int = CARD, strokeDp: Int = 2): GradientDrawable =
    GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(fill)
        setStroke(dp(strokeDp), NEON)
    }

fun TextView.glow(r: Float = 14f) { setShadowLayer(r, 0f, 0f, NEON) }

fun Context.label(s: String, sp: Float = 16f, color: Int = FG): TextView =
    TextView(this).apply {
        text = s
        typeface = NEON_FONT
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        glow(if (sp >= 24f) 18f else 10f)
    }

fun Context.button(s: String, sp: Float = 18f, bg: Int = CARD, fg: Int = FG, onClick: () -> Unit): TextView =
    label(s, sp, fg).apply {
        gravity = Gravity.CENTER
        val p = dp(14)
        setPadding(p, p, p, p)
        background = neonBox(18, bg)
        isClickable = true
        setOnClickListener { onClick() }
    }
