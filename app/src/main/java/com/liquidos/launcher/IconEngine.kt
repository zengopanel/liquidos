package com.liquidos.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * يولّد أيقونة "زجاج سائل" موحّدة لكل تطبيق تلقائياً من أيقونته الأصلية، فلا يبقى أي
 * تطبيق بلا أيقونة مطابقة للنمط، حتى المثبَّت حديثاً. مُلوَّنة بلون الخلفية المستخرَج،
 * ومبسَّطة تلقائياً على وضع الأداء PERFORMANCE لتوفير المعالجة على الأجهزة الضعيفة.
 * style: 0 = زجاج سائل ، 1 = كلاسيكي ، 2 = دائري
 */
object IconEngine {
    private const val SIZE = 176
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    fun get(ctx: Context, app: AppEntry, style: Int, cornerPct: Float, accent: Int?, perf: Int): ImageBitmap {
        val k = "${app.key}#$style#$cornerPct#$accent#$perf"
        cache[k]?.let { return it }
        val img = render(ctx.applicationContext, app, style, cornerPct, accent, perf).asImageBitmap()
        cache[k] = img
        return img
    }

    private fun render(ctx: Context, app: AppEntry, style: Int, cornerPct: Float, accent: Int?, perf: Int): Bitmap {
        val s = SIZE.toFloat()
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pm = ctx.packageManager
        val d: Drawable = try {
            pm.getActivityIcon(app.component)
        } catch (e: Exception) {
            pm.defaultActivityIcon
        }

        val r = if (style == 2) s / 2f else s * cornerPct
        val shape = Path()
        shape.addRoundRect(RectF(0f, 0f, s, s), r, r, Path.Direction.CW)

        c.save()
        c.clipPath(shape)

        if (d is AdaptiveIconDrawable) {
            layer(c, d.background)
            layer(c, d.foreground)
        } else {
            val base = avg(d)
            val tinted = if (accent != null) mix(base, accent, 0.22f) else base
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(
                0f, 0f, 0f, s,
                mix(tinted, Color.WHITE, 0.20f), mix(tinted, Color.BLACK, 0.26f),
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, s, s, p)
            val i = (s * 0.17f).toInt()
            d.setBounds(i, i, SIZE - i, SIZE - i)
            d.draw(c)
        }

        if (style == 0 && perf >= 1) {
            // لمعة زجاجية علوية (تُحسب فقط على BALANCED/ULTRA)
            val g = Paint(Paint.ANTI_ALIAS_FLAG)
            g.shader = LinearGradient(
                0f, 0f, 0f, s * 0.75f,
                Color.argb(95, 255, 255, 255), Color.argb(0, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, s, s, g)
            val b = Paint(Paint.ANTI_ALIAS_FLAG)
            b.shader = LinearGradient(
                0f, s * 0.45f, 0f, s,
                Color.argb(0, 0, 0, 0), Color.argb(70, 0, 0, 0),
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, s, s, b)
            val gl = Paint(Paint.ANTI_ALIAS_FLAG)
            gl.color = Color.argb(if (perf == 2) 55 else 40, 255, 255, 255)
            c.drawOval(RectF(s * 0.10f, s * 0.03f, s * 0.90f, s * 0.47f), gl)
        } else if (style == 0) {
            // نسخة مبسطة جداً على وضع الأداء: لمعة واحدة خفيفة بلا طبقات متعددة
            val gl = Paint(Paint.ANTI_ALIAS_FLAG)
            gl.color = Color.argb(35, 255, 255, 255)
            c.drawOval(RectF(s * 0.12f, s * 0.05f, s * 0.88f, s * 0.4f), gl)
        }
        c.restore()

        val w = s * 0.03f
        val a = if (style == 0) 190 else 70
        val rim = Paint(Paint.ANTI_ALIAS_FLAG)
        rim.style = Paint.Style.STROKE
        rim.strokeWidth = w
        if (perf >= 1) {
            rim.shader = LinearGradient(
                0f, 0f, s, s,
                intArrayOf(
                    Color.argb(a, 255, 255, 255),
                    Color.argb(20, 255, 255, 255),
                    Color.argb(a / 2, 255, 255, 255)
                ),
                null, Shader.TileMode.CLAMP
            )
        } else {
            rim.color = Color.argb(a / 2, 255, 255, 255)
        }
        c.drawRoundRect(RectF(w / 2, w / 2, s - w / 2, s - w / 2), r - w / 2, r - w / 2, rim)
        return bmp
    }

    private fun layer(c: Canvas, d: Drawable?) {
        if (d == null) return
        val o = (SIZE * 0.25f).toInt()
        d.setBounds(-o, -o, SIZE + o, SIZE + o)
        d.draw(c)
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        val r = (Color.red(a) * (1 - t) + Color.red(b) * t).toInt()
        val g = (Color.green(a) * (1 - t) + Color.green(b) * t).toInt()
        val bl = (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt()
        return Color.rgb(r, g, bl)
    }

    private fun avg(d: Drawable): Int {
        val fallback = Color.rgb(70, 82, 110)
        val n = 20
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        d.setBounds(0, 0, n, n)
        d.draw(cv)
        var r = 0L; var g = 0L; var bl = 0L; var cnt = 0
        for (y in 0 until n) {
            for (x in 0 until n) {
                val p = b.getPixel(x, y)
                if (Color.alpha(p) > 128) {
                    r += Color.red(p); g += Color.green(p); bl += Color.blue(p); cnt++
                }
            }
        }
        b.recycle()
        if (cnt == 0) return fallback
        val rr = (r / cnt).toInt(); val gg = (g / cnt).toInt(); val bb = (bl / cnt).toInt()
        val lum = (0.299 * rr + 0.587 * gg + 0.114 * bb) / 255.0
        return if (lum > 0.82) fallback else Color.rgb(rr, gg, bb)
    }
}
