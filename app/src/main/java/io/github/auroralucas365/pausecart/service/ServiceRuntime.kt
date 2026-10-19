package io.github.auroralucas365.pausecart.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RuntimeSnapshot(
    val connected: Boolean = false,
    val appliedSettings: String = "",
    val overlay: Boolean = false,
    val lastPackage: String = "",
    val lastEventAt: Long = 0,
    val result: String = "尚未检测到购物 App",
    val windowCheck: String = "尚未检查提醒窗口",
    val shownAt: Long = 0,
    val latencyMs: Long? = null
)

/** Process-local metadata only. No screen text, files, network or telemetry. */
object ServiceRuntime {
    private val mutable = MutableStateFlow(RuntimeSnapshot())
    val state = mutable.asStateFlow()
    fun update(change: (RuntimeSnapshot) -> RuntimeSnapshot) { mutable.value = change(mutable.value) }
}
