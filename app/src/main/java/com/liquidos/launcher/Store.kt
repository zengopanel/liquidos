package com.liquidos.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

data class AppEntry(
    val label: String,
    val pkg: String,
    val component: ComponentName,
    val category: Int
) {
    val key: String get() = component.flattenToString()
}

/** فئات مكتبة التطبيقات، مبنية على تصنيف أندرويد الرسمي لكل تطبيق (متاح من أندرويد 8). */
enum class LibCategory(val labelAr: String) {
    SOCIAL("اجتماعي"),
    GAME("ألعاب"),
    PRODUCTIVITY("إنتاجية"),
    EDUCATION("تعليم"),
    MEDIA("وسائط"),
    PHOTO("تصوير"),
    COMM("تواصل"),
    FINANCE("مالية"),
    UTILITY("أدوات"),
    OTHER("أخرى")
}

/** أوضاع الأداء الثلاثة. يتحكم كل وضع بحدة الزجاج وسرعة/قوة الحركات. */
enum class PerfProfile(val level: Int) {
    PERFORMANCE(0), // أخف رسم، أقصى سلاسة — الافتراضي على الأجهزة المتوسطة/الضعيفة
    BALANCED(1),
    ULTRA(2)
}

object Store {
    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var dock by mutableStateOf<List<String>>(emptyList())
        private set
    var pins by mutableStateOf<List<String>>(emptyList())
        private set
    var hidden by mutableStateOf<Set<String>>(emptySet())
        private set

    var iconStyle by mutableIntStateOf(0) // 0 زجاج سائل / 1 كلاسيكي / 2 دائري
        private set
    var perf by mutableStateOf(PerfProfile.PERFORMANCE)
        private set
    var accent by mutableStateOf<Color?>(null)
        private set
    var iconSizeDp by mutableFloatStateOf(60f)
        private set
    var cornerPct by mutableFloatStateOf(0.28f)
        private set
    var gridColumns by mutableIntStateOf(4)
        private set
    var reduceMotion by mutableStateOf(false)
        private set
    var showLabels by mutableStateOf(true)
        private set
    var glassIntensity by mutableFloatStateOf(1f) // مضاعف عام لكثافة تعتيم الزجاج
        private set
    var homeSignal by mutableIntStateOf(0)
        private set

    private var prefs: SharedPreferences? = null
    private var started = false

    fun setup(ctx: Context) {
        if (started) return
        started = true
        val p = ctx.applicationContext.getSharedPreferences("liquidos", Context.MODE_PRIVATE)
        prefs = p
        iconStyle = p.getInt("iconStyle", 0)
        perf = PerfProfile.entries.getOrElse(p.getInt("perf", suggestProfile())) { PerfProfile.PERFORMANCE }
        iconSizeDp = p.getFloat("iconSize", 60f)
        cornerPct = p.getFloat("corner", 0.28f)
        gridColumns = p.getInt("grid", 4)
        reduceMotion = p.getBoolean("reduceMotion", false)
        showLabels = p.getBoolean("showLabels", true)
        glassIntensity = p.getFloat("glassIntensity", 1f)
        pins = (p.getString("pins", "") ?: "").split(",").filter { it.isNotBlank() }
        hidden = (p.getString("hidden", "") ?: "").split(",").filter { it.isNotBlank() }.toSet()
        val d = p.getString("dock", null)
        if (d != null) dock = d.split(",").filter { it.isNotBlank() }
        reloadApps(ctx)
        refreshAccent(ctx)
    }

    /** يقترح وضع أداء افتراضياً حسب قوة الجهاز التقريبية (رام + عدد الأنوية). */
    private fun suggestProfile(): Int {
        return try {
            val cores = Runtime.getRuntime().availableProcessors()
            if (cores <= 4 || Build.VERSION.SDK_INT < 29) 0 else 1
        } catch (e: Exception) {
            0
        }
    }

    fun refreshAccent(ctx: Context) {
        Thread {
            accent = WallpaperTint.extract(ctx.applicationContext)
        }.start()
    }

    fun reloadApps(ctx: Context) {
        val app = ctx.applicationContext
        Thread {
            val pm = app.packageManager
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(q, 0).mapNotNull { ri ->
                val ai = ri.activityInfo
                if (ai == null || ai.packageName == app.packageName) {
                    null
                } else {
                    val cat = try { ai.applicationInfo.category } catch (e: Exception) { -1 }
                    AppEntry(ri.loadLabel(pm).toString(), ai.packageName, ComponentName(ai.packageName, ai.name), cat)
                }
            }.sortedBy { it.label.lowercase() }
            apps = list
            if (prefs?.contains("dock") != true) {
                val dd = defaultDock(pm, list)
                dock = dd
                prefs?.edit()?.putString("dock", dd.joinToString(","))?.apply()
            }
        }.start()
    }

    private fun defaultDock(pm: PackageManager, list: List<AppEntry>): List<String> {
        val probes = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_VIEW, Uri.parse("sms:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("http://")),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        )
        val have = list.map { it.pkg }.toSet()
        val res = probes.mapNotNull {
            pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.filter { it in have }.distinct()
        return if (res.isNotEmpty()) res else list.take(4).map { it.pkg }
    }

    fun libraryCategory(a: AppEntry): LibCategory {
        // android.content.pm.ApplicationInfo.CATEGORY_*
        return when (a.category) {
            0 -> LibCategory.GAME
            1 -> LibCategory.MEDIA // AUDIO
            2 -> LibCategory.MEDIA // VIDEO
            3 -> LibCategory.PHOTO
            4 -> LibCategory.SOCIAL
            5 -> LibCategory.EDUCATION // NEWS -> نبقيه قريباً من المعلومات
            6 -> LibCategory.EDUCATION // MAPS
            7 -> LibCategory.PRODUCTIVITY
            else -> guessByPackage(a.pkg)
        }
    }

    private fun guessByPackage(pkg: String): LibCategory {
        val p = pkg.lowercase()
        return when {
            "whatsapp" in p || "telegram" in p || "messenger" in p || "sms" in p || "dialer" in p || "contacts" in p -> LibCategory.COMM
            "bank" in p || "wallet" in p || "pay" in p || "finance" in p -> LibCategory.FINANCE
            "camera" in p || "gallery" in p || "photo" in p -> LibCategory.PHOTO
            "office" in p || "docs" in p || "sheet" in p || "note" in p || "calendar" in p -> LibCategory.PRODUCTIVITY
            "game" in p -> LibCategory.GAME
            "settings" in p || "tool" in p || "cleaner" in p || "file" in p -> LibCategory.UTILITY
            else -> LibCategory.OTHER
        }
    }

    fun updateIconStyle(v: Int) { iconStyle = v; prefs?.edit()?.putInt("iconStyle", v)?.apply() }
    fun updatePerf(v: PerfProfile) { perf = v; prefs?.edit()?.putInt("perf", v.level)?.apply() }
    fun updateIconSize(v: Float) { iconSizeDp = v; prefs?.edit()?.putFloat("iconSize", v)?.apply() }
    fun updateCorner(v: Float) { cornerPct = v; prefs?.edit()?.putFloat("corner", v)?.apply() }
    fun updateGrid(v: Int) { gridColumns = v; prefs?.edit()?.putInt("grid", v)?.apply() }
    fun updateReduceMotion(v: Boolean) { reduceMotion = v; prefs?.edit()?.putBoolean("reduceMotion", v)?.apply() }
    fun updateShowLabels(v: Boolean) { showLabels = v; prefs?.edit()?.putBoolean("showLabels", v)?.apply() }
    fun updateGlassIntensity(v: Float) { glassIntensity = v; prefs?.edit()?.putFloat("glassIntensity", v)?.apply() }

    fun toggleDock(pkg: String) {
        val l = dock.toMutableList()
        if (!l.remove(pkg)) { l.add(pkg); while (l.size > 5) l.removeAt(0) }
        dock = l
        prefs?.edit()?.putString("dock", l.joinToString(","))?.apply()
    }

    fun togglePin(pkg: String) {
        val l = pins.toMutableList()
        if (!l.remove(pkg)) { l.add(pkg); while (l.size > 16) l.removeAt(0) }
        pins = l
        prefs?.edit()?.putString("pins", l.joinToString(","))?.apply()
    }

    fun toggleHidden(pkg: String) {
        val s = hidden.toMutableSet()
        if (!s.remove(pkg)) s.add(pkg)
        hidden = s
        prefs?.edit()?.putString("hidden", s.joinToString(","))?.apply()
    }

    fun goHome() { homeSignal = homeSignal + 1 }
}
