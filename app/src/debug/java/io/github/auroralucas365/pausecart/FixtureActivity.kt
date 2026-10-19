package io.github.auroralucas365.pausecart

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.accessibility.AccessibilityEvent

/** Only included in debug/test APKs. No external shop or transaction is involved. */
class FixtureActivity : Activity() {
    override fun onResume() {
        super.onResume()
        // Both screens share the app package in this debug-only fixture. Emit its
        // identity through the actual system event path even when an OS reports
        // only a FrameLayout event. Real shopping apps use their distinct packages.
        val decor=window.decorView
        decor.postDelayed({
            if(!isFinishing && !isDestroyed) {
                val event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                event.packageName=packageName
                event.className=FixtureActivity::class.java.name
                event.setSource(decor)
                decor.parent?.requestSendAccessibilityEvent(decor,event)
            }
        },300)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 80, 32, 32)
            addView(TextView(context).apply { text = "本机模拟页面 · 仅测试"; textSize = 20f })
            addView(TextView(context).apply { text = "确认订单"; textSize = 24f })
            addView(TextView(context).apply { text = "合计：￥199.90"; textSize = 20f })
            addView(Button(context).apply { text = "提交订单"; setOnClickListener { /* No transaction. */ } })
        })
    }
}
