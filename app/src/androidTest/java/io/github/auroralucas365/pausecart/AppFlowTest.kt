package io.github.auroralucas365.pausecart

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File

class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun saveItemAndRecordDecisionWithoutAccessibilityPermission() {
        screenshot("01-home.png")
        compose.onNodeWithTag("add_item").performClick()
        compose.onNodeWithTag("item_title").performTextInput("测试冷静商品")
        compose.onNodeWithTag("item_price").performTextInput("199.90")
        compose.onNodeWithTag("save_item").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("测试冷静商品").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("测试冷静商品").assertExists()
        compose.onNodeWithText("¥199.90").assertExists()
        screenshot("02-cooldown-list.png")
        compose.onNodeWithText("决定放弃").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("手动记录 · 已放弃（自报）").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("手动记录 · 已放弃（自报）").assertExists()
    }
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap -> File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
    }
}
