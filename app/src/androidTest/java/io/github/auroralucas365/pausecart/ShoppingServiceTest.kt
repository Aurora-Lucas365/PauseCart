package io.github.auroralucas365.pausecart

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import io.github.auroralucas365.pausecart.core.Mode
import io.github.auroralucas365.pausecart.data.Preferences
import io.github.auroralucas365.pausecart.service.ShoppingService
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ShoppingServiceTest {
    @Test fun localFixtureTriggersOverlayAndContinueGrantsTemporaryPass() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ctx = instrumentation.targetContext
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
        val previous = shell("settings get secure enabled_accessibility_services").takeUnless { it == "null" }.orEmpty()
        check(previous.matches(Regex("[A-Za-z0-9._/:]*")))
        val own = "${ctx.packageName}/io.github.auroralucas365.pausecart.service.ShoppingService"
        val prefs = Preferences(ctx)
        try {
            prefs.reset(); prefs.consent = true; prefs.setMode(ctx.packageName, Mode.CHECKOUT)
            shell("settings put secure enabled_accessibility_services ${if(previous.isBlank()) own else "$previous:$own"}")
            shell("settings put secure accessibility_enabled 1")
            waitFor { ShoppingService.connected }
            ctx.startActivity(Intent().setClassName(ctx, "${ctx.packageName}.FixtureActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitFor { find(automation, "继续本次购物 · 放行 15 分钟") != null }
            val dir = File(ctx.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            automation.takeScreenshot()?.let { bitmap -> File(dir, "03-service-fixture.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
            assertTrue(find(automation, "继续本次购物 · 放行 15 分钟")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            waitFor { prefs.passUntil(ctx.packageName) > System.currentTimeMillis() }
            assertTrue(prefs.passUntil(ctx.packageName) <= System.currentTimeMillis() + 15*60_000L)
        } finally {
            prefs.reset()
            if(previous.isBlank()) shell("settings delete secure enabled_accessibility_services") else shell("settings put secure enabled_accessibility_services $previous")
        }
    }
    private fun find(automation: UiAutomation, text: String): AccessibilityNodeInfo? {
        for(window in automation.windows) {
            val root = window.root ?: continue
            val nodes = root.findAccessibilityNodeInfosByText(text)
            root.recycle()
            nodes.firstOrNull { it.text?.toString() == text && it.isVisibleToUser }?.let { return it }
        }
        return null
    }
    private fun waitFor(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 15_000
        while(System.currentTimeMillis() < end) { if(condition()) return; Thread.sleep(100) }
        fail("Local test condition timed out; this is not a merchant-app compatibility result.")
    }
}
