package com.tpj.millcar

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView

const val BG = 0xFF0E0F12.toInt()
const val CARD = 0xFF1B1D22.toInt()
const val FG = 0xFFECECEC.toInt()
const val DIM = 0xFF8A8F98.toInt()
const val ACC = 0xFF2F6FE0.toInt()

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.label(s: String, sp: Float = 16f, color: Int = FG): TextView =
    TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
    }

fun Context.button(s: String, sp: Float = 16f, bg: Int = CARD, fg: Int = FG, onClick: () -> Unit): TextView =
    label(s, sp, fg).apply {
        gravity = Gravity.CENTER
        val p = dp(12)
        setPadding(p, p, p, p)
        background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(bg) }
        isClickable = true
        setOnClickListener { onClick() }
    }
