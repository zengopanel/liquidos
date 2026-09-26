@file:OptIn(ExperimentalFoundationApi::class)

package com.liquidos.launcher

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.ActivityOptions
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ───────────────────────── أدوات عامة ───────────────────────── */

private val DarkGlass = Color(0xFF15151A)

private fun labelStyle() = TextStyle(
    color = Color.White,
    fontSize = 11.5.sp,
    fontWeight = FontWeight.Medium,
    textAlign = TextAlign.Center,
    shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 8f)
)

private fun Modifier.tap(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

private fun pick(value: Float, vOpen: Float): Float = when {
    vOpen > 700f -> 1f
    vOpen < -700f -> 0f
    value > 0.35f -> 1f
    else -> 0f
}

private fun openSys(ctx: Context, vararg actions: String) {
    for (a in actions) {
        try {
            ctx.startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: Exception) {
        }
    }
}

private fun launchApp(ctx: Context, app: AppEntry, lc: LayoutCoordinates?, view: View) {
    val i = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(app.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    try {
        var bundle: android.os.Bundle? = null
        if (lc != null && lc.isAttached) {
            val r = lc.boundsInWindow()
            bundle = ActivityOptions.makeScaleUpAnimation(
                view, r.left.toInt(), r.top.toInt(), r.width.toInt(), r.height.toInt()
            ).toBundle()
        }
        ctx.startActivity(i, bundle)
    } catch (e: Exception) {
        Toast.makeText(ctx, "تعذر فتح التطبيق", Toast.LENGTH_SHORT).show()
    }
}

private fun a11y(ctx: Context, action: Int) {
    val s = NotifService.instance
    if (s != null) {
        s.performGlobalAction(action)
    } else {
        Toast.makeText(ctx, "فعّل خدمة LiquidOS من إعدادات إمكانية الوصول", Toast.LENGTH_LONG).show()
        openSys(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }
}

private var torchOn by mutableStateOf(false)

private fun setTorch(ctx: Context, on: Boolean): Boolean {
    return try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return false
        cm.setTorchMode(id, on)
        true
    } catch (e: Exception) {
        false
    }
}

/* ───────────────────────── الجذر ───────────────────────── */

@Composable
fun LauncherRoot() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Root() }
}

@Composable
private fun Root() {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val drawer = remember { Animatable(0f) }
    val panel = remember { Animatable(0f) }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var libraryMode by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(0) }

    val perfLevel = Store.perf.level
    val spec = remember(perfLevel, Store.reduceMotion) {
        if (Store.reduceMotion) spring<Float>(dampingRatio = 1f, stiffness = 900f)
        else when (perfLevel) {
            0 -> spring(dampingRatio = 0.92f, stiffness = 420f)
            2 -> spring(dampingRatio = 0.78f, stiffness = 340f)
            else -> spring(dampingRatio = 0.86f, stiffness = 380f)
        }
    }

    fun go(a: Animatable<Float, AnimationVector1D>, to: Float) {
        scope.launch { a.animateTo(to, spec) }
    }

    fun snap(a: Animatable<Float, AnimationVector1D>, v: Float) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { a.snapTo(v.coerceIn(0f, 1f)) }
    }

    fun closeAll() {
        go(drawer, 0f); go(panel, 0f)
        menuApp = null; showSettings = false; libraryMode = false
        query = ""; focus.clearFocus()
    }

    LaunchedEffect(Store.homeSignal) { if (Store.homeSignal > 0) closeAll() }

    BackHandler(true) {
        when {
            menuApp != null -> menuApp = null
            showSettings -> showSettings = false
            libraryMode -> libraryMode = false
            drawer.value > 0f -> { go(drawer, 0f); query = ""; focus.clearFocus() }
            panel.value > 0f -> go(panel, 0f)
            else -> {}
        }
    }

    val drawerOn by remember { derivedStateOf { drawer.value > 0.001f } }
    val panelOn by remember { derivedStateOf { panel.value > 0.001f } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = constraints.maxHeight.toFloat()
        val topEdge = with(LocalDensity.current) { 110.dp.toPx() }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val k = 1f - 0.06f * drawer.value
                    scaleX = k; scaleY = k
                    alpha = 1f - drawer.value * 0.9f
                }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { dy ->
                        when (mode) {
                            1 -> snap(drawer, drawer.value - dy / (h * 0.55f))
                            2 -> snap(panel, panel.value + dy / (h * 0.45f))
                            else -> {}
                        }
                    },
                    onDragStarted = { start -> mode = if (start.y < topEdge) 2 else 1 },
                    onDragStopped = { v ->
                        when (mode) {
                            1 -> go(drawer, pick(drawer.value, -v))
                            2 -> go(panel, pick(panel.value, v))
                            else -> {}
                        }
                        mode = 0
                    }
                )
        ) {
            HomeContent(onMenu = { menuApp = it })
        }

        if (drawerOn) {
            DrawerLayer(
                p = { drawer.value }, h = h, query = query, onQuery = { query = it },
                library = libraryMode, onToggleLibrary = { libraryMode = !libraryMode },
                onPull = { dy -> snap(drawer, drawer.value - dy / (h * 0.55f)) },
                onRelease = { v -> go(drawer, pick(drawer.value, -v)) },
                onMenu = { menuApp = it }
            )
        }

        if (panelOn) {
            PanelLayer(
                p = { panel.value },
                onDrag = { dy -> snap(panel, panel.value + dy / (h * 0.45f)) },
                onStop = { v -> go(panel, pick(panel.value, v)) },
                onClose = { go(panel, 0f) },
                onSettings = { go(panel, 0f); showSettings = true }
            )
        }

        if (showSettings) SettingsOverlay(onClose = { showSettings = false })

        val m = menuApp
        if (m != null) MenuOverlay(app = m, onClose = { menuApp = null })
    }
}

/* ───────────────────────── الرئيسية ───────────────────────── */

@Composable
private fun HomeContent(onMenu: (AppEntry) -> Unit) {
    val apps = Store.apps.filter { it.pkg !in Store.hidden }
    val byPkg = remember(apps) { apps.groupBy { it.pkg } }
    val pins = Store.pins.mapNotNull { byPkg[it]?.firstOrNull() }
    val dock = Store.dock.mapNotNull { byPkg[it]?.firstOrNull() }
    val cols = Store.gridColumns
    val iconSize = Store.iconSizeDp.dp

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(44.dp))
        ClockBlock()
        Spacer(Modifier.height(26.dp))

        if (pins.isEmpty()) {
            BasicText(
                "اضغط مطولاً على أي تطبيق داخل الدرج لتثبيته هنا",
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Center)
            )
        } else {
            pins.chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    row.forEach { a ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            AppIcon(a, iconSize, Store.showLabels, onMenu)
                        }
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(44.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.55f)))
        }

        if (dock.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth()
                    .liquidGlass(GlassLevel.ULTRA, 34.dp, accent = Store.accent, perf = Store.perf.level)
                    .padding(vertical = 14.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                dock.forEach { AppIcon(it, iconSize - 2.dp, false, onMenu) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ClockBlock() {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L + 50L)
        }
    }
    val is24 = android.text.format.DateFormat.is24HourFormat(ctx)
    val time = remember(now) { SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(now)) }
    val sh = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 4f), 16f)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(time, style = TextStyle(color = Color.White, fontSize = 88.sp, fontWeight = FontWeight.Thin, shadow = sh))
        BasicText(date, style = TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = 17.sp, fontWeight = FontWeight.Medium, shadow = sh))
    }
}

/* ───────────────────────── الأيقونة ───────────────────────── */

@Composable
private fun AppIcon(app: AppEntry, size: Dp, label: Boolean, onMenu: (AppEntry) -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val style = Store.iconStyle
    val corner = Store.cornerPct
    val accentArgb = Store.accent?.let { c ->
        android.graphics.Color.rgb((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
    }
    val perf = Store.perf.level
    val bmp by produceState<ImageBitmap?>(null, app.key, style, corner, accentArgb, perf) {
        value = withContext(Dispatchers.Default) { IconEngine.get(ctx, app, style, corner, accentArgb, perf) }
    }
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.84f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "press"
    )

    Column(
        Modifier
            .width(size + 14.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = src, indication = null,
                onClick = { launchApp(ctx, app, holder[0], view) },
                onLongClick = { onMenu(app) }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(size).onGloballyPositioned { holder[0] = it }) {
            val b = bmp
            if (b != null) {
                Image(bitmap = b, contentDescription = app.label, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(size * corner)).background(Color.White.copy(alpha = 0.12f)))
            }
        }
        if (label) {
            Spacer(Modifier.height(6.dp))
            BasicText(app.label, modifier = Modifier.fillMaxWidth(), style = labelStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/* ───────────────────────── درج التطبيقات + المكتبة ───────────────────────── */

@Composable
private fun DrawerLayer(
    p: () -> Float, h: Float, query: String, onQuery: (String) -> Unit,
    library: Boolean, onToggleLibrary: () -> Unit,
    onPull: (Float) -> Unit, onRelease: (Float) -> Unit, onMenu: (AppEntry) -> Unit
) {
    val all = Store.apps.filter { it.pkg !in Store.hidden }
    val list = remember(query, all) {
        if (query.isBlank()) all else all.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    val conn = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (available.y < 0f && p() < 1f) { onPull(available.y); Offset(0f, available.y) } else Offset.Zero

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (available.y > 0f) { onPull(available.y); Offset(0f, available.y) } else Offset.Zero

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (p() < 1f) onRelease(available.y)
                return available
            }
        }
    }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { val v = p(); alpha = v; translationY = (1f - v) * h * 0.18f }
            .background(Color.Black.copy(alpha = 0.28f))
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { SearchBar(query, onQuery) }
                Box(
                    Modifier.height(48.dp).width(56.dp)
                        .liquidGlass(GlassLevel.SOFT, 24.dp, accent = Store.accent, perf = Store.perf.level)
                        .tap(onToggleLibrary),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(if (library) "🔠" else "🗂", style = TextStyle(fontSize = 19.sp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.weight(1f).nestedScroll(conn)) {
                if (library) {
                    LibraryGrid(list, onMenu, bottom)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp + bottom),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        gridItems(list, key = { it.key }) { app ->
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                AppIcon(app, 60.dp, true, onMenu)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryGrid(list: List<AppEntry>, onMenu: (AppEntry) -> Unit, bottom: Dp) {
    val grouped = remember(list) {
        list.groupBy { Store.libraryCategory(it) }.toSortedMap(compareBy { it.ordinal })
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp + bottom)) {
        grouped.forEach { (cat, appsInCat) ->
            item(key = "h_${cat.name}") {
                BasicText(
                    cat.labelAr,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                )
            }
            item(key = "c_${cat.name}") {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 18.dp)
                        .liquidGlass(GlassLevel.MEDIUM, 26.dp, accent = Store.accent, perf = Store.perf.level)
                        .padding(14.dp)
                ) {
                    appsInCat.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            row.forEach { a ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { AppIcon(a, 54.dp, true, onMenu) }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(value: String, onChange: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(48.dp)
            .liquidGlass(GlassLevel.SOFT, 24.dp, accent = Store.accent, perf = Store.perf.level)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            BasicText("ابحث في التطبيقات", style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp))
        }
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth()
        )
    }
}

/* ───────────────────────── اللوحة العلوية (Control Center) ───────────────────────── */

@Composable
private fun PanelLayer(p: () -> Float, onDrag: (Float) -> Unit, onStop: (Float) -> Unit, onClose: () -> Unit, onSettings: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = p() }.background(Color.Black.copy(alpha = 0.25f)).tap(onClose))
        Column(
            Modifier.align(Alignment.TopCenter)
                .graphicsLayer { translationY = -(1f - p()) * size.height; alpha = minOf(1f, p() * 1.6f) }
                .fillMaxWidth().statusBarsPadding().padding(12.dp)
                .liquidGlass(GlassLevel.STRONG, 32.dp, accent = Store.accent, perf = Store.perf.level)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { dy -> onDrag(dy) },
                    onDragStopped = { v -> onStop(v) }
                )
                .padding(16.dp)
        ) {
            PanelContent(onSettings = onSettings, onClose = onClose)
        }
    }
}

@Composable
private fun PanelContent(onSettings: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol) }
    var bri by remember {
        mutableFloatStateOf(
            try { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f }
            catch (e: Exception) { 0.5f }
        )
    }
    var asked by remember { mutableStateOf(false) }
    var modeSet by remember { mutableStateOf(false) }
    var rotationLocked by remember {
        mutableStateOf(
            try { Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 0 }
            catch (e: Exception) { false }
        )
    }
    val nm = remember { ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    var dnd by remember { mutableStateOf(nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) }

    val gap = Arrangement.spacedBy(10.dp)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            Tile(Modifier.weight(1f), "📶", "واي فاي") { openSys(ctx, Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_WIFI_SETTINGS); onClose() }
            Tile(Modifier.weight(1f), "🔵", "بلوتوث") { openSys(ctx, Settings.ACTION_BLUETOOTH_SETTINGS); onClose() }
            Tile(Modifier.weight(1f), "🔦", "الكشاف", torchOn) { if (setTorch(ctx, !torchOn)) torchOn = !torchOn }
            Tile(Modifier.weight(1f), "✈️", "الطيران") { openSys(ctx, Settings.ACTION_AIRPLANE_MODE_SETTINGS); onClose() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            Tile(Modifier.weight(1f), "🔄", "قفل الدوران", rotationLocked) {
                if (Settings.System.canWrite(ctx)) {
                    val newVal = !rotationLocked
                    Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (newVal) 0 else 1)
                    rotationLocked = newVal
                } else {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                    }
                }
            }
            Tile(Modifier.weight(1f), "🌙", "عدم الإزعاج", dnd) {
                if (nm.isNotificationPolicyAccessGranted) {
                    val newVal = !dnd
                    nm.setInterruptionFilter(if (newVal) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
                    dnd = newVal
                } else {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                    }
                }
            }
            Tile(Modifier.weight(1f), "🔔", "الإشعارات") { onClose(); a11y(ctx, AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) }
            Tile(Modifier.weight(1f), "🔒", "قفل") { onClose(); a11y(ctx, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            Tile(Modifier.weight(1f), "📡", "نقطة الاتصال") { openSys(ctx, "android.settings.TETHER_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS); onClose() }
            Tile(Modifier.weight(1f), "🔋", "توفير البطارية") { openSys(ctx, Settings.ACTION_BATTERY_SAVER_SETTINGS); onClose() }
            Tile(Modifier.weight(1f), "📍", "الموقع") { openSys(ctx, Settings.ACTION_LOCATION_SOURCE_SETTINGS); onClose() }
        }

        GlassSlider("🔊", vol) {
            vol = it
            am.setStreamVolume(AudioManager.STREAM_MUSIC, (it * maxVol).toInt().coerceIn(0, maxVol), 0)
        }
        GlassSlider("☀️", bri) {
            bri = it
            if (Settings.System.canWrite(ctx)) {
                if (!modeSet) {
                    modeSet = true
                    Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                }
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (it * 255).toInt().coerceIn(1, 255))
            } else if (!asked) {
                asked = true
                try {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            Tile(Modifier.weight(1f), "⚙️", "إعدادات النظام") { openSys(ctx, Settings.ACTION_SETTINGS); onClose() }
            Tile(Modifier.weight(1f), "🎨", "مظهر اللانشر") { onSettings() }
        }
    }
}

@Composable
private fun Tile(modifier: Modifier, icon: String, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier.height(76.dp)
            .liquidGlass(if (active) GlassLevel.CLEAR else GlassLevel.SOFT, 22.dp, accent = Store.accent, perf = Store.perf.level)
            .tap(onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        BasicText(icon, style = TextStyle(fontSize = 24.sp))
        Spacer(Modifier.height(4.dp))
        BasicText(label, style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun GlassSlider(icon: String, value: Float, onChange: (Float) -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp)
            .liquidGlass(GlassLevel.SOFT, 26.dp, accent = Store.accent, perf = Store.perf.level)
            .pointerInput(Unit) { detectTapGestures { off -> onChange((off.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> change.consume(); onChange((change.position.x / size.width).coerceIn(0f, 1f)) }
            }
    ) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0.08f, 1f))
                .clip(RoundedCornerShape(26.dp)).background(Color.White.copy(alpha = 0.9f))
        )
        BasicText(icon, modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp), style = TextStyle(fontSize = 20.sp))
    }
}

/* ───────────────────────── الإعدادات ───────────────────────── */

private enum class SettingsTab(val label: String) { APPEARANCE("المظهر"), ANIMATION("الحركة"), HOME("الرئيسية"), PERF("الأداء") }

@Composable
private fun SettingsOverlay(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(SettingsTab.APPEARANCE) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).tap(onClose), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(20.dp).fillMaxWidth()
                .liquidGlass(GlassLevel.STRONG, 30.dp, accent = Store.accent, perf = Store.perf.level)
                .tap { }
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BasicText("مظهر LiquidOS", modifier = Modifier.fillMaxWidth(), style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingsTab.entries.forEach { t ->
                    Chip(Modifier.weight(1f), t.label, tab == t) { tab = t }
                }
            }

            when (tab) {
                SettingsTab.APPEARANCE -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Heading("شكل الأيقونات", 13)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(Modifier.weight(1f), "زجاج سائل", Store.iconStyle == 0) { Store.updateIconStyle(0) }
                        Chip(Modifier.weight(1f), "كلاسيكي", Store.iconStyle == 1) { Store.updateIconStyle(1) }
                        Chip(Modifier.weight(1f), "دائري", Store.iconStyle == 2) { Store.updateIconStyle(2) }
                    }
                    Heading("حجم الأيقونات", 13)
                    LabeledSlider((Store.iconSizeDp - 46f) / 30f) { Store.updateIconSize(46f + it * 30f) }
                    Heading("استدارة الزوايا", 13)
                    LabeledSlider(Store.cornerPct / 0.5f) { Store.updateCorner((it * 0.5f).coerceIn(0.05f, 0.5f)) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(Modifier.weight(1f), "إظهار الأسماء", Store.showLabels) { Store.updateShowLabels(true) }
                        Chip(Modifier.weight(1f), "إخفاء الأسماء", !Store.showLabels) { Store.updateShowLabels(false) }
                    }
                    Chip(Modifier.fillMaxWidth(), "🖼 تغيير الخلفية", false) {
                        try { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "اختر الخلفية")) } catch (e: Exception) {}
                    }
                }

                SettingsTab.ANIMATION -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Heading("تقليل الحركة (لإمكانية الوصول)", 13)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(Modifier.weight(1f), "عادي", !Store.reduceMotion) { Store.updateReduceMotion(false) }
                        Chip(Modifier.weight(1f), "مخفَّف", Store.reduceMotion) { Store.updateReduceMotion(true) }
                    }
                    BasicText(
                        "يعتمد إيقاع الحركة أيضاً على وضع الأداء في تبويب \"الأداء\".",
                        style = TextStyle(color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                    )
                }

                SettingsTab.HOME -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Heading("عدد أعمدة الشبكة", 13)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(4, 5, 6).forEach { n -> Chip(Modifier.weight(1f), "$n", Store.gridColumns == n) { Store.updateGrid(n) } }
                    }
                    if (Store.hidden.isNotEmpty()) {
                        Heading("التطبيقات المخفية (${Store.hidden.size})", 13)
                        Store.apps.filter { it.pkg in Store.hidden }.take(6).forEach { a ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                BasicText(a.label, modifier = Modifier.weight(1f), style = TextStyle(color = Color.White, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Chip(Modifier.width(90.dp), "إظهار", false) { Store.toggleHidden(a.pkg) }
                            }
                        }
                    }
                }

                SettingsTab.PERF -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Heading("وضع الأداء", 13)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(Modifier.weight(1f), "أداء", Store.perf == PerfProfile.PERFORMANCE) { Store.updatePerf(PerfProfile.PERFORMANCE) }
                        Chip(Modifier.weight(1f), "متوازن", Store.perf == PerfProfile.BALANCED) { Store.updatePerf(PerfProfile.BALANCED) }
                        Chip(Modifier.weight(1f), "Ultra", Store.perf == PerfProfile.ULTRA) { Store.updatePerf(PerfProfile.ULTRA) }
                    }
                    BasicText(
                        "وضع \"أداء\" هو الموصى به لهاتفك للحفاظ على السلاسة. جرّب \"متوازن\" إن أردت مزيداً من التفاصيل البصرية.",
                        style = TextStyle(color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                    )
                }
            }

            Chip(Modifier.fillMaxWidth(), "إغلاق", false) { onClose() }
        }
    }
}

@Composable
private fun LabeledSlider(value: Float, onChange: (Float) -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(40.dp)
            .liquidGlass(GlassLevel.SOFT, 20.dp, accent = Store.accent, perf = Store.perf.level)
            .pointerInput(Unit) { detectTapGestures { off -> onChange((off.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> change.consume(); onChange((change.position.x / size.width).coerceIn(0f, 1f)) }
            }
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0.06f, 1f)).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.85f)))
    }
}

/* ───────────────────────── قائمة التطبيق ───────────────────────── */

@Composable
private fun MenuOverlay(app: AppEntry, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val pinned = app.pkg in Store.pins
    val docked = app.pkg in Store.dock
    val hidden = app.pkg in Store.hidden
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).tap(onClose), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(290.dp)
                .liquidGlass(GlassLevel.STRONG, 26.dp, accent = Store.accent, perf = Store.perf.level)
                .tap { }
                .padding(vertical = 10.dp)
        ) {
            Heading(app.label, 17, Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            MenuItem(if (pinned) "إلغاء التثبيت من الرئيسية" else "تثبيت في الرئيسية") { Store.togglePin(app.pkg); onClose() }
            MenuItem(if (docked) "إزالة من الشريط السفلي" else "إضافة للشريط السفلي") { Store.toggleDock(app.pkg); onClose() }
            MenuItem(if (hidden) "إظهار من جديد" else "إخفاء من الرئيسية والدرج") { Store.toggleHidden(app.pkg); onClose() }
            MenuItem("معلومات التطبيق") {
                try { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
                onClose()
            }
            MenuItem("إلغاء التثبيت") {
                try { ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
                onClose()
            }
        }
    }
}

@Composable
private fun Heading(text: String, size: Int, modifier: Modifier = Modifier) {
    BasicText(text, modifier = modifier.fillMaxWidth(), style = TextStyle(color = Color.White, fontSize = size.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center))
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    BasicText(text, modifier = Modifier.fillMaxWidth().tap(onClick).padding(vertical = 14.dp, horizontal = 16.dp), style = TextStyle(color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center))
}

@Composable
private fun Chip(modifier: Modifier, text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier.height(44.dp)
            .liquidGlass(if (selected) GlassLevel.CLEAR else GlassLevel.SOFT, 16.dp, accent = Store.accent, perf = Store.perf.level)
            .tap(onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(text, style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center))
    }
}
