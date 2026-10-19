package io.github.auroralucas365.pausecart.core

import java.math.BigDecimal
import java.math.RoundingMode

enum class Mode(val label: String) { OFF("关闭"), ENTRY("打开时提醒"), CHECKOUT("下单前提醒 · 试用") }
data class Shop(val packageName: String, val name: String)
object Shops {
    val all = listOf(Shop("com.taobao.taobao", "淘宝"), Shop("com.jingdong.app.mall", "京东"), Shop("com.xunmeng.pinduoduo", "拼多多"))
    fun find(pkg: String) = all.find { it.packageName == pkg }
}

data class Decision(val checkout: Boolean, val amountCents: Long? = null)
object CheckoutDetector {
    private val excluded = listOf("退款详情", "退款成功", "退货详情", "订单详情", "订单已取消", "支付成功", "付款成功", "请输入支付密码", "付款码", "收款码")
    private val pageTitles = listOf("确认订单", "填写订单", "订单确认", "确认购买", "结算")
    private val actions = listOf("提交订单", "去支付", "立即支付", "提交并支付", "提交并付款", "确认交易")
    // Amounts are accepted only when explicitly attached to an order-total label.
    private val total = Regex("(?:实付款|应付款|应付|合计|实付|待支付)[：:\\s]*(?:[¥￥]\\s*)?([0-9]{1,7}(?:\\.[0-9]{1,2})?)(?:元)?")
    fun evaluate(pkg: String, texts: List<String>): Decision {
        if (Shops.find(pkg) == null) return Decision(false)
        val normalized = texts.take(180).map { it.trim().replace(Regex("\\s+"), " ") }
        if (normalized.any { t -> excluded.any(t::contains) }) return Decision(false)
        val title = normalized.any { t -> pageTitles.any { p -> t == p || (t.length <= 24 && t.contains(p)) } }
        val action = normalized.any { t -> actions.any { a -> t.startsWith(a) && t.length <= 50 } }
        if (!title || !action) return Decision(false)
        val amount = normalized.firstNotNullOfOrNull { total.find(it)?.groupValues?.get(1)?.let(Money::parseCents) }
        return Decision(true, amount)
    }
}

object Money {
    fun parseCents(input: String): Long? = try {
        val clean = input.trim().removePrefix("¥").removePrefix("￥").trim()
        if (!Regex("[0-9]{1,7}(?:\\.[0-9]{1,2})?").matches(clean)) null
        else BigDecimal(clean).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact().takeIf { it > 0 }
    } catch (_: Exception) { null }
    fun format(cents: Long) = BigDecimal.valueOf(cents, 2).toPlainString()
}

/** Times are monotonic elapsed milliseconds, never wall-clock dates. */
class VisitGate(private val newVisitAfter: Long = 30_000) {
    private data class Visit(var shown: Boolean = false, var leftAt: Long? = null)
    private val visits = mutableMapOf<String, Visit>()
    var active: String? = null
        private set
    fun foreground(pkg: String?, elapsed: Long) {
        if (pkg == active) return
        active?.let { visits.getOrPut(it) { Visit() }.leftAt = elapsed }
        active = pkg
        if (pkg != null) {
            val visit = visits.getOrPut(pkg) { Visit() }
            if (visit.leftAt?.let { elapsed - it >= newVisitAfter } == true) visit.shown = false
            visit.leftAt = null
        }
    }
    fun mayShow(pkg: String, wallTime: Long, passUntil: Long): Boolean =
        active == pkg && wallTime >= passUntil && visits[pkg]?.shown != true
    fun markShown(pkg: String) { visits.getOrPut(pkg) { Visit() }.shown = true }
    fun reset(pkg: String) { visits.remove(pkg) }
    fun resetAll() { visits.clear() }
}

object SharedText {
    fun link(text: String): String? = Regex("https?://[^\\s<>\"，。]+", RegexOption.IGNORE_CASE)
        .find(text.take(8000))?.value?.trimEnd(')', '）', ']', '】')
    fun title(text: String): String = text.take(2000).replace(Regex("https?://\\S+"), "").trim().take(120).ifBlank { "待考虑的商品" }
}
