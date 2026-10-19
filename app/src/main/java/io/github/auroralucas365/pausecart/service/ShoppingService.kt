package io.github.auroralucas365.pausecart.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import io.github.auroralucas365.pausecart.MainActivity
import io.github.auroralucas365.pausecart.BuildConfig
import io.github.auroralucas365.pausecart.core.*
import io.github.auroralucas365.pausecart.data.Preferences
import java.util.ArrayDeque

class ShoppingService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val gate = ReminderGate()
    private lateinit var prefs: Preferences
    private var overlay: View? = null
    private var activePkg = ""
    private var pending: Runnable? = null
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        pending?.let(handler::removeCallbacks)
        hide()
    }
    private val wm by lazy { getSystemService(WindowManager::class.java) }
    override fun onServiceConnected() { prefs = Preferences(this); prefs.listen(preferenceListener); connected = true }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::prefs.isInitialized || event == null) return
        val pkg = event.packageName?.toString() ?: return
        // Window metadata is observed to remove our UI on app switches. No foreign content is read.
        if (pkg == packageName && overlay != null) {
            if (event.className?.toString() == MainActivity::class.java.name) hide() else return
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != activePkg) {
            activePkg = pkg
            pending?.let(handler::removeCallbacks)
            hide()
        }
        // A debug-only local fixture lets instrumentation exercise the service without merchant apps.
        val shop = Shops.find(pkg) ?: if (BuildConfig.DEBUG && pkg == packageName && prefs.mode(pkg) == Mode.CHECKOUT) Shop(pkg, "本机测试页") else return
        if (!prefs.consent || prefs.paused || prefs.mode(pkg) == Mode.OFF) { hide(); return }
        if (!getSystemService(PowerManager::class.java).isInteractive || getSystemService(KeyguardManager::class.java).isKeyguardLocked) { hide(); return }
        val now = System.currentTimeMillis()
        if (overlay != null || !gate.mayShow(pkg, now, prefs.passUntil(pkg), prefs.lastDismissed)) return
        if (prefs.mode(pkg) == Mode.ENTRY) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) show(shop, null, false)
            return
        }
        pending?.let(handler::removeCallbacks)
        pending = Runnable {
            if (activePkg != pkg || prefs.paused || prefs.mode(pkg) != Mode.CHECKOUT) return@Runnable
            val root = rootInActiveWindow ?: return@Runnable
            if (root.packageName?.toString() != pkg) { root.recycle(); return@Runnable }
            val texts = readTexts(root)
            val rulePkg = if (BuildConfig.DEBUG && pkg == packageName) Shops.all.first().packageName else pkg
            val decision = CheckoutDetector.evaluate(rulePkg, texts)
            if (decision.checkout && overlay == null) show(shop, decision.amountCents, true)
        }.also { handler.postDelayed(it, 350) }
    }

    private fun readTexts(root: AccessibilityNodeInfo): List<String> {
        val queue = ArrayDeque<AccessibilityNodeInfo>(); queue.add(root)
        val result = mutableListOf<String>(); var visited = 0
        while (queue.isNotEmpty() && visited++ < 180) {
            val node = queue.removeFirst()
            if (!node.isPassword && node.isVisibleToUser) {
                node.text?.toString()?.take(120)?.let(result::add)
                node.contentDescription?.toString()?.take(120)?.let(result::add)
                for (i in 0 until node.childCount.coerceAtMost(40)) node.getChild(i)?.let(queue::add)
            }
            node.recycle()
        }
        while (queue.isNotEmpty()) queue.removeFirst().recycle()
        return result
    }

    private fun show(shop: Shop, cents: Long?, checkout: Boolean) {
        val now = System.currentTimeMillis()
        if (!gate.mayShow(shop.packageName, now, prefs.passUntil(shop.packageName), prefs.lastDismissed)) return
        val dp = resources.displayMetrics.density
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding((24*dp).toInt(), (24*dp).toInt(), (24*dp).toInt(), (24*dp).toInt())
            setBackgroundColor(Color.rgb(245,247,243)); elevation = 16*dp
        }
        fun label(text: String, size: Float) { panel.addView(TextView(this).apply { this.text = text; textSize = size; setTextColor(Color.rgb(31,48,39)); setPadding(0,0,0,(14*dp).toInt()) }) }
        label("缓购 · 先给自己一点时间", 22f)
        label(if (checkout) "${shop.name}：识别到可能的结算页面${cents?.let { " · ¥${Money.format(it)}（请核对）" } ?: ""}。这次购买在计划之内吗？" else "准备打开${shop.name}。这次是计划内购买，还是随便逛逛？", 16f)
        label("${if(checkout) "实验性识别，可能误判或漏报。" else "入口提醒，不表示正在付款。"} 最后的决定由你来做。", 13f)
        fun button(text: String, action: () -> Unit) { panel.addView(Button(this).apply { this.text = text; setOnClickListener { action() } }, LinearLayout.LayoutParams(-1,-2)) }
        button("先放冷静清单") {
            prefs.pass(shop.packageName); prefs.lastDismissed = now; hide()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("capture_source", shop.name).putExtra("capture_cents", cents ?: -1L))
        }
        button("继续本次购物 · 放行 15 分钟") { prefs.pass(shop.packageName); prefs.lastDismissed = System.currentTimeMillis(); hide() }
        button("关闭提醒 · 我自行离开") { prefs.lastDismissed = System.currentTimeMillis(); hide() }
        val params = WindowManager.LayoutParams((resources.displayMetrics.widthPixels - 32*dp).toInt(), -2,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.CENTER }
        val scroll = ScrollView(this).apply { addView(panel); isFillViewport = false }
        params.height = (resources.displayMetrics.heightPixels * 0.72f).toInt()
        try { wm.addView(scroll, params); overlay = scroll; gate.markShown(shop.packageName, now) }
        catch (_: RuntimeException) { overlay = null }
    }
    private fun hide() { overlay?.let { runCatching { wm.removeView(it) } }; overlay = null }
    override fun onInterrupt() { pending?.let(handler::removeCallbacks); hide() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); if(::prefs.isInitialized) prefs.unlisten(preferenceListener); hide(); connected = false; super.onDestroy() }
    companion object { @Volatile var connected = false; private set }
}
