package com.mramr.trilauncher

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable

/** Custom monochrome line-art icon family drawn on a 24x24 grid. */
class LineIcon(private val kind: Int, private val letter: String, private val tint: Int) : Drawable() {

    companion object {
        const val GENERIC = 0
        const val PHONE = 1
        const val MSG = 2
        const val CAMERA = 3
        const val GALLERY = 4
        const val GLOBE = 5
        const val MAIL = 6
        const val MUSIC = 7
        const val MAP = 8
        const val GEAR = 9
        const val CLOCK = 10
        const val CAL = 11
        const val CALC = 12
        const val NOTE = 13
        const val FILES = 14
        const val PLAY = 15
        const val BAG = 16
        const val CHAT = 17
        const val INSTA = 18
        const val FB = 19

        fun kindOf(pkg: String, label: String): Int {
            val s = (pkg + " " + label).lowercase()
            fun has(vararg t: String) = t.any { s.contains(it) }
            return when {
                has("whatsapp") -> CHAT
                has("instagram") -> INSTA
                has("facebook") -> FB
                has("youtube") -> PLAY
                has("dialer", "phone", "contacts") -> PHONE
                has("messag", "mms", "sms") -> MSG
                has("camera") -> CAMERA
                has("gallery", "photos") -> GALLERY
                has("chrome", "browser", "firefox", "opera", "edge") -> GLOBE
                has("gmail", "mail") -> MAIL
                has("music", "spotify") -> MUSIC
                has("maps") -> MAP
                has("settings") -> GEAR
                has("clock") -> CLOCK
                has("calendar") -> CAL
                has("calculator") -> CALC
                has("notes", "keep", "memo") -> NOTE
                has("files", "manager", "explorer") -> FILES
                has("vending", "store") -> BAG
                else -> GENERIC
            }
        }
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.4f
        color = tint
    }
    private val tp by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            textSize = 11f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
    }

    private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float) =
        c.drawRoundRect(l, t, r, b, rad, rad, p)

    private fun ln(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) = c.drawLine(x1, y1, x2, y2, p)

    private fun poly(c: Canvas, vararg v: Float) {
        val path = Path()
        path.moveTo(v[0], v[1])
        var i = 2
        while (i < v.size) {
            path.lineTo(v[i], v[i + 1])
            i += 2
        }
        c.drawPath(path, p)
    }

    override fun draw(c: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()) / 24f
        c.save()
        c.translate(b.left + (b.width() - 24f * s) / 2f, b.top + (b.height() - 24f * s) / 2f)
        c.scale(s, s)
        when (kind) {
            PHONE -> {
                val path = Path()
                path.moveTo(6.5f, 3.5f); path.lineTo(9.5f, 3.5f); path.lineTo(10.8f, 8f); path.lineTo(8.8f, 9.5f)
                path.cubicTo(10f, 12.2f, 11.8f, 14f, 14.5f, 15.2f)
                path.lineTo(16f, 13.2f); path.lineTo(20.5f, 14.5f); path.lineTo(20.5f, 17.5f)
                path.cubicTo(20.5f, 19f, 19.3f, 20.5f, 17.5f, 20.5f)
                path.cubicTo(10f, 20f, 4f, 14f, 3.5f, 6.5f)
                path.cubicTo(3.5f, 4.7f, 5f, 3.5f, 6.5f, 3.5f)
                path.close()
                c.drawPath(path, p)
            }
            MSG -> {
                rr(c, 3f, 4f, 21f, 16f, 3f)
                poly(c, 8f, 16f, 8f, 20f, 12f, 16f)
                ln(c, 8f, 9f, 16f, 9f)
                ln(c, 8f, 12f, 13f, 12f)
            }
            CAMERA -> {
                rr(c, 3f, 7f, 21f, 19f, 3f)
                c.drawCircle(12f, 13f, 3.3f, p)
                poly(c, 9f, 7f, 10f, 5f, 14f, 5f, 15f, 7f)
            }
            GALLERY -> {
                rr(c, 3f, 4f, 21f, 20f, 3f)
                c.drawCircle(9f, 10f, 1.7f, p)
                poly(c, 3.5f, 17f, 9f, 12.5f, 13f, 16f, 16f, 13.5f, 20.5f, 18f)
            }
            GLOBE -> {
                c.drawCircle(12f, 12f, 9f, p)
                c.drawOval(RectF(8f, 3f, 16f, 21f), p)
                ln(c, 3f, 12f, 21f, 12f)
            }
            MAIL -> {
                rr(c, 3f, 5f, 21f, 19f, 2.5f)
                poly(c, 3.6f, 6.6f, 12f, 13f, 20.4f, 6.6f)
            }
            MUSIC -> {
                poly(c, 9f, 17f, 9f, 6f, 19f, 4f, 19f, 15f)
                c.drawCircle(7f, 17f, 2.2f, p)
                c.drawCircle(17f, 15f, 2.2f, p)
            }
            MAP -> {
                val path = Path()
                path.moveTo(5f, 10f)
                path.arcTo(RectF(5f, 3f, 19f, 17f), 180f, 180f, false)
                path.cubicTo(19f, 14f, 15f, 18f, 12f, 21f)
                path.cubicTo(9f, 18f, 5f, 14f, 5f, 10f)
                path.close()
                c.drawPath(path, p)
                c.drawCircle(12f, 10f, 2.4f, p)
            }
            GEAR -> {
                c.drawCircle(12f, 12f, 3f, p)
                c.drawCircle(12f, 12f, 7f, p)
                for (k in 0 until 8) {
                    val a = k * Math.PI / 4
                    val ca = Math.cos(a).toFloat()
                    val sa = Math.sin(a).toFloat()
                    ln(c, 12f + 7f * ca, 12f + 7f * sa, 12f + 9.6f * ca, 12f + 9.6f * sa)
                }
            }
            CLOCK -> {
                c.drawCircle(12f, 12f, 9f, p)
                poly(c, 12f, 7f, 12f, 12f, 15.5f, 14f)
            }
            CAL -> {
                rr(c, 3f, 5f, 21f, 21f, 2.5f)
                ln(c, 3f, 10f, 21f, 10f)
                ln(c, 8f, 3f, 8f, 7f)
                ln(c, 16f, 3f, 16f, 7f)
            }
            CALC -> {
                rr(c, 5f, 3f, 19f, 21f, 2.5f)
                ln(c, 8f, 8f, 16f, 8f)
                ln(c, 9f, 13f, 9.2f, 13f); ln(c, 14.8f, 13f, 15f, 13f)
                ln(c, 9f, 17f, 9.2f, 17f); ln(c, 14.8f, 17f, 15f, 17f)
            }
            NOTE -> {
                rr(c, 5f, 3f, 19f, 21f, 2.5f)
                ln(c, 8f, 9f, 16f, 9f)
                ln(c, 8f, 13f, 16f, 13f)
                ln(c, 8f, 17f, 12f, 17f)
            }
            FILES -> {
                rr(c, 3f, 7f, 21f, 19f, 2.5f)
                poly(c, 3f, 9f, 3f, 6f, 9f, 6f, 11f, 8f)
            }
            PLAY -> {
                rr(c, 2.5f, 5.5f, 21.5f, 18.5f, 5f)
                val path = Path()
                path.moveTo(10f, 9f); path.lineTo(15.5f, 12f); path.lineTo(10f, 15f); path.close()
                c.drawPath(path, p)
            }
            BAG -> {
                rr(c, 4f, 8f, 20f, 21f, 2.5f)
                poly(c, 9f, 8f, 9f, 6f, 15f, 6f, 15f, 8f)
            }
            CHAT -> {
                c.drawCircle(12f, 11.5f, 8.5f, p)
                poly(c, 4.2f, 20.5f, 5.8f, 16.2f)
                poly(c, 8.6f, 8.2f, 9.8f, 8.2f, 10.6f, 10.6f, 9.8f, 11.4f, 11.8f, 13.8f, 12.8f, 13.2f, 15.2f, 14f, 15.2f, 15.2f)
            }
            INSTA -> {
                rr(c, 3f, 3f, 21f, 21f, 6f)
                c.drawCircle(12f, 12f, 4.2f, p)
                ln(c, 17.2f, 6.8f, 17.3f, 6.8f)
            }
            FB -> {
                rr(c, 3f, 3f, 21f, 21f, 5f)
                val path = Path()
                path.moveTo(13.5f, 21f); path.lineTo(13.5f, 10f); path.quadTo(13.5f, 7.5f, 16.5f, 7.5f)
                c.drawPath(path, p)
                ln(c, 11f, 12.5f, 16f, 12.5f)
            }
            else -> {
                rr(c, 4f, 4f, 20f, 20f, 6f)
                c.drawText(letter, 12f, 15.8f, tp)
            }
        }
        c.restore()
    }

    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { p.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
