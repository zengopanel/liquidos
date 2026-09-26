package com.liquidos.launcher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color

/**
 * يستخرج لوناً مميزاً من الخلفية الحالية مرة واحدة فقط (وليس باستمرار، توفيراً للمعالجة
 * على الأجهزة الضعيفة)، ويُعاد الاستخراج فقط عند تغيير الخلفية فعلياً.
 */
object WallpaperTint {

    fun extract(ctx: Context): Color? {
        return try {
            val wm = WallpaperManager.getInstance(ctx)
            val d = wm.drawable as? BitmapDrawable ?: return null
            val src = d.bitmap ?: return null
            val small = Bitmap.createScaledBitmap(src, 32, 32, true)

            var r = 0L; var g = 0L; var b = 0L; var n = 0L
            var bestSat = -1.0
            var bestColor = AColor.rgb(90, 110, 160)

            for (y in 0 until small.height) {
                for (x in 0 until small.width) {
                    val p = small.getPixel(x, y)
                    val pr = AColor.red(p); val pg = AColor.green(p); val pb = AColor.blue(p)
                    r += pr; g += pg; b += pb; n++

                    val mx = maxOf(pr, pg, pb) / 255.0
                    val mn = minOf(pr, pg, pb) / 255.0
                    val sat = if (mx == 0.0) 0.0 else (mx - mn) / mx
                    val lum = (0.299 * pr + 0.587 * pg + 0.114 * pb) / 255.0
                    if (sat > bestSat && lum in 0.15..0.88) {
                        bestSat = sat
                        bestColor = AColor.rgb(pr, pg, pb)
                    }
                }
            }
            if (small !== src) small.recycle()

            val avgLum = if (n > 0) (0.299 * (r.toDouble() / n) + 0.587 * (g.toDouble() / n) + 0.114 * (b.toDouble() / n)) / 255.0 else 0.5
            val chosen = if (bestSat > 0.12) bestColor else AColor.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())

            // إن كانت الخلفية داكنة جداً أو فاتحة جداً، ارفع التشبع قليلاً حتى لا يختفي التلوين
            var cr = AColor.red(chosen); var cg = AColor.green(chosen); var cb = AColor.blue(chosen)
            if (avgLum < 0.18 || avgLum > 0.9) {
                val mixTo = if (avgLum < 0.18) intArrayOf(120, 150, 210) else intArrayOf(60, 90, 150)
                cr = ((cr + mixTo[0]) / 2); cg = ((cg + mixTo[1]) / 2); cb = ((cb + mixTo[2]) / 2)
            }
            Color(cr / 255f, cg / 255f, cb / 255f, 1f)
        } catch (e: Exception) {
            null
        }
    }
}
