package com.mramr.trilauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View

/** Text drawn vertically (reads bottom to top), for editorial typography. */
class VText(
    ctx: Context,
    private val label: String,
    sizeSp: Float,
    col: Int,
    tf: Typeface,
    spacingEm: Float
) : View(ctx) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizeSp * ctx.resources.displayMetrics.density
        color = col
        typeface = tf
        letterSpacing = spacingEm
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fm = p.fontMetrics
        setMeasuredDimension((fm.descent - fm.ascent).toInt() + 4, p.measureText(label).toInt() + 4)
    }

    override fun onDraw(canvas: Canvas) {
        val fm = p.fontMetrics
        canvas.save()
        canvas.translate(0f, height.toFloat())
        canvas.rotate(-90f)
        canvas.drawText(label, 2f, -fm.ascent + 2f, p)
        canvas.restore()
    }
}
