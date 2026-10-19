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
import org.junit.Assert.*
import io.github.auroralucas365.pausecart.core.Shops
import io.github.auroralucas365.pausecart.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import android.os.ParcelFileDescriptor
import android.os.SystemClock

class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun saveItemAndRecordDecisionWithoutAccessibilityPermission() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(0,ctx.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertEquals(PackageManager.PERMISSION_DENIED,ctx.packageManager.checkPermission("android.permission.INTERNET",ctx.packageName))
        screenshot("01-home.png")
        compose.onNodeWithTag("nav_1").performClick()
        compose.onNodeWithTag("add_item").performClick()
        compose.onNodeWithTag("item_title").performTextInput("测试冷静商品")
        compose.onNodeWithTag("item_price").performTextInput("199.90")
        screenshot("02-add-item.png")
        compose.onNodeWithTag("save_item").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("测试冷静商品").fetchSemanticsNodes().isNotEmpty() }
        val saved=runBlocking { CartDatabase.get(ctx).items().observe().first { it.any { item -> item.title=="测试冷静商品" } }.first { it.title=="测试冷静商品" } }
        assertEquals(19990L,saved.cents)
        screenshot("03-list.png")
        compose.onNodeWithText("查看与决定").performScrollTo().performClick()
        compose.onNodeWithTag("skip_item").performClick()
        compose.onNodeWithText("已决定").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("决定不买了").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun settingsFollowEveryTapWithoutWaitingForATimer() {
        val prefs=Preferences(compose.activity)
        compose.runOnIdle { prefs.consent=true; prefs.setMode(Shops.all.first().packageName,io.github.auroralucas365.pausecart.core.Mode.OFF) }
        compose.waitForIdle()
        val switch=compose.onNodeWithTag("shop_${Shops.all.first().packageName}")
        switch.assertIsOff()
        repeat(20) { index ->
            val start=SystemClock.elapsedRealtime()
            switch.performClick()
            if(index%2==0) switch.assertIsOn() else switch.assertIsOff()
            assertTrue("Selection should not wait for the old 30-second tick",SystemClock.elapsedRealtime()-start<2000)
        }
        compose.onNodeWithTag("nav_2").performClick()
        compose.onNodeWithTag("hours_72").performClick().assertIsSelected()
        compose.onNodeWithTag("hours_1").performClick().assertIsSelected()
        screenshot("04-settings.png")
        compose.runOnIdle { prefs.reset() }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val dir=File(instrumentation.targetContext.getExternalFilesDir(null),"screenshots").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            val file=File(dir,name); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("mkdir -p /data/local/tmp/pausecart-screenshots; cp ${file.absolutePath} /data/local/tmp/pausecart-screenshots/$name")).use { it.readBytes() }
        }
    }
}
