package io.github.auroralucas365.pausecart.fixture

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Separate test APK/package. Does not impersonate or access a real merchant. */
class ShoppingFixtureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(32,80,32,32)
            addView(TextView(context).apply { text="跨 App 本机测试 · 无交易"; textSize=20f })
            addView(TextView(context).apply { text="确认订单"; textSize=24f })
            addView(TextView(context).apply { text="合计：￥199.90"; textSize=20f })
            addView(Button(context).apply { text="提交订单"; setOnClickListener {} })
        })
    }
}
