package io.github.auroralucas365.pausecart

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import io.github.auroralucas365.pausecart.data.CartDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import android.os.ParcelFileDescriptor

class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun saveItemAndRecordDecisionWithoutAccessibilityPermission() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(0, ctx.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertEquals(PackageManager.PERMISSION_DENIED, ctx.packageManager.checkPermission("android.permission.INTERNET", ctx.packageName))
        screenshot("01-home.png")
        compose.onNodeWithTag("add_item").performClick()
        compose.onNodeWithTag("item_title").performTextInput("测试冷静商品")
        compose.onNodeWithTag("item_price").performTextInput("199.90")
        compose.onNodeWithTag("save_item").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("测试冷静商品").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("测试冷静商品").assertExists()
        screenshot("02-cooldown-list.png")
        val saved = runBlocking { CartDatabase.get(ctx).items().observe().first { it.any { item -> item.title == "测试冷静商品" } }.first { it.title == "测试冷静商品" } }
        assertEquals(19990L, saved.cents)
        compose.onNodeWithTag("main_list").performScrollToNode(hasText("¥199.90"))
        compose.onNodeWithText("¥199.90").assertExists()
        compose.onNodeWithText("决定放弃").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("手动记录 · 已放弃（自报）").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("手动记录 · 已放弃（自报）").assertExists()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            val file = File(dir, name)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("mkdir -p /data/local/tmp/pausecart-screenshots; cp ${file.absolutePath} /data/local/tmp/pausecart-screenshots/$name")).use { it.readBytes() }
        }
    }
}
