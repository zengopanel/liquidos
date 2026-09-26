package com.liquidos.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * نظام "الزجاج السائل" بخمسة مستويات. كل مستوى له كثافة تعتيم وحدّة إضاءة حافة مختلفة،
 * ويُختار المستوى المناسب تلقائياً حسب مكان العنصر (خلفية عامة، لوحة، بطاقة، زر...).
 */
enum class GlassLevel(val fillAlpha: Float, val rimAlpha: Float, val glow: Float) {
    ULTRA(0.10f, 0.85f, 0.55f),   // شبه شفاف تماماً، للطبقات الكبيرة فوق الخلفية مباشرة
    STRONG(0.20f, 0.65f, 0.40f),  // اللوحات الرئيسية (Control Center, Settings)
    MEDIUM(0.32f, 0.50f, 0.30f),  // البطاقات والقوائم
    SOFT(0.46f, 0.38f, 0.20f),    // أزرار وشرائح صغيرة
    CLEAR(0.62f, 0.25f, 0.12f)    // عناصر تحتاج تباين نص قوي (شارات، تنبيهات)
}

private fun tintOf(base: Color, accent: Color?, mix: Float): Color {
    if (accent == null) return base
    return Color(
        red = base.red * (1 - mix) + accent.red * mix,
        green = base.green * (1 - mix) + accent.green * mix,
        blue = base.blue * (1 - mix) + accent.blue * mix,
        alpha = base.alpha
    )
}

/**
 * يطبّق مظهر الزجاج السائل. على وضع الأداء PERFORMANCE يُبسَّط الرسم (تعتيم + حدّ واحد فقط)
 * حفاظاً على السلاسة على الأجهزة الضعيفة. على BALANCED/ULTRA تُضاف حافة إضاءة متدرجة ولمعة علوية.
 */
fun Modifier.liquidGlass(
    level: GlassLevel,
    radius: Dp,
    dark: Boolean = true,
    accent: Color? = null,
    perf: Int = 1
): Modifier = composed {
    val shape = RoundedCornerShape(radius)
    val base = if (dark) Color(0xFF15151A) else Color(0xFFF4F5F8)
    val fill = tintOf(base, accent, 0.16f).copy(alpha = level.fillAlpha)

    var m = this.clip(shape).background(fill)

    if (perf == 0) {
        // Performance: حدّ واحد بسيط بلا تدرّج، أخف على الرسم
        m = m.border(1.dp, Color.White.copy(alpha = level.rimAlpha * 0.4f), shape)
    } else {
        val rim = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = level.rimAlpha),
                Color.White.copy(alpha = level.rimAlpha * 0.12f),
                tintOf(Color.White, accent, 0.3f).copy(alpha = level.rimAlpha * 0.5f)
            )
        )
        m = m.border(1.dp, rim, shape)
        if (perf == 2) {
            // Ultra: طبقة توهج داخلي إضافية
            val glow = Brush.radialGradient(
                colors = listOf(
                    tintOf(Color.White, accent, 0.4f).copy(alpha = level.glow * 0.25f),
                    Color.Transparent
                ),
                radius = 600f
            )
            m = m.background(glow, shape)
        }
    }
    m
}
