package io.github.auroralucas365.pausecart.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.*
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.*
import io.github.auroralucas365.pausecart.MainActivity
import io.github.auroralucas365.pausecart.BuildConfig
import io.github.auroralucas365.pausecart.core.*
import io.github.auroralucas365.pausecart.data.Preferences
import java.util.ArrayDeque

class ShoppingService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val visits = VisitGate()
    private lateinit var prefs: Preferences
    private var overlay: View? = null
    private var foreground = ""
    private var fixtureActive = false
    private var scheduled: Runnable? = null
    private var eventElapsed = 0L
    private val wm by lazy { getSystemService(WindowManager::class.java) }
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            cancelPending(); hide(); visits.foreground(null, SystemClock.elapsedRealtime()); foreground = ""
        }
    }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == "consent" || key == "paused" || key.startsWith("mode_") || key.startsWith("pass_")) {
            cancelPending(); hide()
            when {
                key?.startsWith("mode_") == true -> visits.reset(key.removePrefix("mode_"))
                key?.startsWith("pass_") == true -> visits.reset(key.removePrefix("pass_"))
                else -> visits.resetAll()
            }
            ServiceRuntime.update { it.copy(appliedSettings = prefs.snapshot().signature) }
            handler.post { checkForeground() }
        }
    }
    override fun onServiceConnected() {
        prefs = Preferences(this); prefs.listen(preferenceListener); instance = this
        ServiceRuntime.update { it.copy(connected = true, appliedSettings = prefs.snapshot().signature) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        handler.postDelayed({ checkForeground() }, 200)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::prefs.isInitialized || event == null) return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg == packageName && overlay != null && event.className?.toString() != MainActivity::class.java.name) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg == packageName) {
            fixtureActive = BuildConfig.DEBUG && event.className?.toString()?.endsWith("FixtureActivity") == true
        }
        if (!interactive()) { cancelPending(); hide(); return }
        val actual = foregroundPackage()
        val candidate = actual ?: when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> pkg.takeIf { it.isNotBlank() }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> pkg.takeIf { it == foreground }
            else -> null
        } ?: return
        eventElapsed = SystemClock.elapsedRealtime()
        updateForeground(candidate)
        val shop = shop(candidate) ?: return
        ServiceRuntime.update { it.copy(connected = true, lastPackage = candidate, lastEventAt = System.currentTimeMillis()) }
        evaluate(shop)
    }
    private fun shop(pkg: String): Shop? = Shops.find(pkg) ?: if (BuildConfig.DEBUG && fixtureActive && pkg == packageName) Shop(pkg, "本机测试页") else null
    /** Inspect package metadata only; never extract foreign page text. */
    private fun foregroundPackage(): String? {
        val app = runCatching { windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }?.root }.getOrNull()
        if (app != null) { val pkg = app.packageName?.toString(); app.recycle(); if (!pkg.isNullOrBlank()) return pkg }
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString(); root.recycle()
        return pkg?.takeUnless { it == packageName && overlay != null }
    }
    private fun updateForeground(pkg: String) {
        if (pkg != foreground) { cancelPending(); hide(); foreground = pkg }
        visits.foreground(shop(pkg)?.packageName, SystemClock.elapsedRealtime())
    }
    private fun checkForeground() {
        if (!::prefs.isInitialized || !interactive()) return
        val pkg = foregroundPackage() ?: return
        updateForeground(pkg); shop(pkg)?.let { evaluate(it) }
    }
    private fun interactive() = getSystemService(PowerManager::class.java).isInteractive && !getSystemService(KeyguardManager::class.java).isKeyguardLocked
    private fun result(text: String) { ServiceRuntime.update { it.copy(result = text) } }
    private fun evaluate(shop: Shop) {
        when {
            !prefs.consent -> { result("尚未同意自动提醒授权"); return }
            prefs.paused -> { result("自动提醒已暂停"); return }
            prefs.mode(shop.packageName) == Mode.OFF -> { result("该 App 的提醒已关闭"); return }
            prefs.passUntil(shop.packageName) > System.currentTimeMillis() -> { result("该 App 处于 15 分钟放行中"); return }
            overlay != null -> return
            !visits.mayShow(shop.packageName, System.currentTimeMillis(), prefs.passUntil(shop.packageName)) -> { result("本次访问已提醒；离开 30 秒后再次进入会重新提醒"); return }
        }
        if (scheduled != null) return // leading-edge merge; continuous events cannot postpone the scan
        val mode = prefs.mode(shop.packageName)
        scheduled = Runnable {
            scheduled = null
            val actual = foregroundPackage() ?: foreground
            if (actual != shop.packageName || !interactive() || prefs.paused || !prefs.consent || prefs.mode(shop.packageName) != mode || prefs.passUntil(shop.packageName) > System.currentTimeMillis()) return@Runnable
            if (mode == Mode.ENTRY) show(shop, null, false)
            else {
                val root = rootInActiveWindow ?: run { result("当前页面不可读取，可改用打开时提醒"); return@Runnable }
                if (root.packageName?.toString() != shop.packageName) { root.recycle(); return@Runnable }
                val texts = readTexts(root)
                val pkg = if (BuildConfig.DEBUG && shop.packageName == packageName) Shops.all.first().packageName else shop.packageName
                val decision = CheckoutDetector.evaluate(pkg, texts)
                if (decision.checkout) show(shop, decision.amountCents, true)
                else result("已检测到 App；当前未命中下单前试用规则")
            }
        }.also { handler.postDelayed(it, if (mode == Mode.ENTRY) 180 else 300) }
    }
    private fun readTexts(root: AccessibilityNodeInfo): List<String> {
        val queue = ArrayDeque<AccessibilityNodeInfo>(); queue.add(root)
        val texts = mutableListOf<String>(); var visited = 0
        while (queue.isNotEmpty() && visited++ < 180) {
            val node = queue.removeFirst()
            if (!node.isPassword && node.isVisibleToUser) {
                node.text?.toString()?.take(120)?.let(texts::add)
                node.contentDescription?.toString()?.take(120)?.let(texts::add)
                for (i in 0 until node.childCount.coerceAtMost(40)) node.getChild(i)?.let(queue::add)
            }
            node.recycle()
        }
        while (queue.isNotEmpty()) queue.removeFirst().recycle()
        return texts
    }
    private fun show(shop: Shop, cents: Long?, checkout: Boolean, preview: Boolean = false) {
        if (overlay != null || !interactive()) return
        if (!preview && !visits.mayShow(shop.packageName, System.currentTimeMillis(), prefs.passUntil(shop.packageName))) return
        val dp = resources.displayMetrics.density
        fun d(value: Int) = (value * dp).toInt()
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val paper = Color.parseColor(if (dark) "#202C25" else "#FFFFFF")
        val ink = Color.parseColor(if (dark) "#E5EEE9" else "#20352D")
        val muted = Color.parseColor(if (dark) "#A9B7AE" else "#627169")
        val accent = Color.parseColor(if (dark) "#92D1AC" else "#30674F")
        fun bg(fill: Int, outline: Boolean = false) = GradientDrawable().apply { setColor(fill); cornerRadius = d(18).toFloat(); if(outline) setStroke(d(1), accent) }
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(d(22),d(14),d(22),d(20)); background = bg(paper) }
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(TextView(this).apply { text = if(preview) "缓购 · 窗口自检" else "缓购 · ${shop.name}"; textSize = 13f; setTextColor(accent) }, LinearLayout.LayoutParams(0,-2,1f))
        heading.addView(Button(this).apply { text = "×"; textSize = 22f; setTextColor(muted); background = null; contentDescription = "关闭本次提醒"; setOnClickListener { hide() } }, LinearLayout.LayoutParams(d(48),d(48)))
        panel.addView(heading)
        fun label(text: String, size: Float, color: Int) { panel.addView(TextView(this).apply { this.text=text; textSize=size; setTextColor(color); setPadding(0,0,0,d(12)); if(size>=22) setTypeface(null,Typeface.BOLD) }) }
        label(if(checkout) "这次购买，在计划之内吗？" else "这次有明确要买的东西吗？",23f,ink)
        label(if(preview) "窗口能够显示，不代表真实购物 App 检测已通过。" else if(checkout) "${cents?.let { "合计约 ¥${Money.format(it)}，请核对。" } ?: ""}先停一下，想买的可以记下来。" else "先停一下，想买的可以记下来。",15f,muted)
        fun button(text: String, primary: Boolean, action: () -> Unit) {
            panel.addView(Button(this).apply { this.text=text; textSize=15f; isAllCaps=false; setTextColor(if(primary) (if(dark) Color.parseColor("#123425") else Color.WHITE) else accent); background=bg(if(primary) accent else paper, !primary); minHeight=d(48); setPadding(d(10),d(8),d(10),d(8)); setOnClickListener { action() } }, LinearLayout.LayoutParams(-1,-2).apply { topMargin=d(10) })
        }
        button("先记下来", true) {
            hide()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("capture_source",if(preview) "手动记录" else shop.name).putExtra("capture_cents", cents ?: -1L))
        }
        button(if(preview) "完成窗口检查" else "继续逛逛 · 15 分钟不提醒", false) { hide(); if(!preview) prefs.pass(shop.packageName) }
        val scroll = ScrollView(this).apply { addView(panel); background=bg(paper) }
        val width = (resources.displayMetrics.widthPixels-d(32)).coerceAtMost(d(420))
        scroll.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec((resources.displayMetrics.heightPixels*.72).toInt(),View.MeasureSpec.AT_MOST))
        val navId = resources.getIdentifier("navigation_bar_height","dimen","android")
        val params = WindowManager.LayoutParams(width,scroll.measuredHeight,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply {
            gravity=Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y=d(16)+(if(navId>0) resources.getDimensionPixelSize(navId) else 0)
        }
        try {
            wm.addView(scroll,params); overlay=scroll
            val now=System.currentTimeMillis()
            if(!preview) visits.markShown(shop.packageName)
            ServiceRuntime.update { it.copy(overlay=true,
                windowCheck=if(preview) "本机提醒窗口已显示（不代表商家检测通过）" else it.windowCheck,
                result=if(preview) it.result else "提醒已显示", shownAt=if(preview) it.shownAt else now,
                latencyMs=if(!preview && eventElapsed>0) SystemClock.elapsedRealtime()-eventElapsed else it.latencyMs) }
        } catch (e: RuntimeException) {
            ServiceRuntime.update { it.copy(overlay=false,windowCheck="提醒窗口显示失败：${e.javaClass.simpleName}",result=if(preview) it.result else "检测到 App，但提醒窗口显示失败") }
        }
    }
    private fun cancelPending() { scheduled?.let(handler::removeCallbacks); scheduled=null }
    private fun hide() { overlay?.let { runCatching { wm.removeView(it) } }; overlay=null; ServiceRuntime.update { it.copy(overlay=false) } }
    override fun onInterrupt() { cancelPending(); hide(); ServiceRuntime.update { it.copy(connected=false,result="服务已中断，请重新检查") } }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null); if(::prefs.isInitialized) prefs.unlisten(preferenceListener)
        runCatching { unregisterReceiver(screenOff) }; hide(); instance=null
        ServiceRuntime.update { it.copy(connected=false,appliedSettings="") }; super.onDestroy()
    }
    companion object {
        private var instance: ShoppingService? = null
        val connected get() = ServiceRuntime.state.value.connected
        val overlayVisible get() = ServiceRuntime.state.value.overlay
        val debugDiagnostic get() = ServiceRuntime.state.value.result
        fun preview(): Boolean { val service=instance ?: return false; service.handler.post { service.hide(); service.show(Shops.all.first(),null,false,true) }; return true }
    }
}
