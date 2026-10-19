package io.github.auroralucas365.pausecart

import android.app.UiAutomation
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import io.github.auroralucas365.pausecart.core.Mode
import io.github.auroralucas365.pausecart.data.Preferences
import io.github.auroralucas365.pausecart.service.ShoppingService
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ShoppingServiceTest {
    @Test fun checkoutWindowAndExplicitPass() = withService(Mode.CHECKOUT) { prefs,automation ->
        waitFor { ShoppingService.overlayVisible }
        screenshot(automation,"05-checkout-window.png")
        val button=find(automation,"继续逛逛 · 15 分钟不提醒")
        assertNotNull(button); assertTrue(button!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)); button.recycle()
        waitFor { prefs.passUntil(InstrumentationRegistry.getInstrumentation().targetContext.packageName)>System.currentTimeMillis() }
        assertFalse(ShoppingService.overlayVisible)
    }
    @Test fun entryModeChangesAndPauseApplyImmediately() = withService(Mode.ENTRY) { prefs,automation ->
        val pkg=InstrumentationRegistry.getInstrumentation().targetContext.packageName
        waitFor { ShoppingService.overlayVisible }
        screenshot(automation,"06-entry-window.png")
        prefs.setMode(pkg,Mode.OFF)
        waitFor { !ShoppingService.overlayVisible }
        prefs.setMode(pkg,Mode.ENTRY)
        waitFor { ShoppingService.overlayVisible }
        prefs.paused=true
        waitFor { !ShoppingService.overlayVisible }
        prefs.paused=false
        waitFor { ShoppingService.overlayVisible }
        find(automation,"继续逛逛 · 15 分钟不提醒")!!.let { assertTrue(it.performAction(AccessibilityNodeInfo.ACTION_CLICK)); it.recycle() }
        waitFor { prefs.passUntil(pkg)>System.currentTimeMillis() && !ShoppingService.overlayVisible }
        prefs.endPass(pkg)
        waitFor { ShoppingService.overlayVisible }
    }
    private fun withService(mode: Mode,body: (Preferences,UiAutomation)->Unit) {
        val instrumentation=InstrumentationRegistry.getInstrumentation(); val ctx=instrumentation.targetContext
        val automation=instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo=automation.serviceInfo.apply { flags=flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        fun shell(command: String)=ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
        val previous=shell("settings get secure enabled_accessibility_services").takeUnless { it=="null" }.orEmpty()
        check(previous.matches(Regex("[A-Za-z0-9._/:]*")))
        val own="${ctx.packageName}/io.github.auroralucas365.pausecart.service.ShoppingService"
        val prefs=Preferences(ctx)
        try {
            prefs.reset(); prefs.consent=true; prefs.setMode(ctx.packageName,mode)
            shell("settings put secure enabled_accessibility_services ${if(previous.isBlank()) own else "$previous:$own"}")
            shell("settings put secure accessibility_enabled 1")
            waitFor { ShoppingService.connected }
            ctx.startActivity(Intent().setClassName(ctx,"${ctx.packageName}.FixtureActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            body(prefs,automation)
        } finally {
            prefs.reset()
            if(previous.isBlank()) shell("settings delete secure enabled_accessibility_services") else shell("settings put secure enabled_accessibility_services $previous")
        }
    }
    private fun screenshot(automation: UiAutomation,name: String) {
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("mkdir -p /data/local/tmp/pausecart-screenshots; screencap -p /data/local/tmp/pausecart-screenshots/$name")).use { it.readBytes() }
    }
    private fun find(automation: UiAutomation,text: String): AccessibilityNodeInfo? {
        for(window in automation.windows) { val root=window.root ?: continue; val nodes=root.findAccessibilityNodeInfosByText(text); root.recycle(); val target=nodes.firstOrNull { it.text?.toString()==text && it.isVisibleToUser }; nodes.filter { it!==target }.forEach { it.recycle() }; if(target!=null) return target }
        return null
    }
    private fun waitFor(condition: ()->Boolean) {
        val end=SystemClock.elapsedRealtime()+5000
        while(SystemClock.elapsedRealtime()<end) { if(condition()) return; Thread.sleep(50) }
        fail("Local fixture condition timed out: ${ShoppingService.debugDiagnostic}; not a merchant compatibility result")
    }
}
