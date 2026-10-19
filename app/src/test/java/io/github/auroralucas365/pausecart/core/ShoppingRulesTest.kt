package io.github.auroralucas365.pausecart.core

import org.junit.Assert.*
import org.junit.Test

class ShoppingRulesTest {
    private val taobao = "com.taobao.taobao"
    @Test fun checkoutRequiresPageAndAction() {
        assertTrue(CheckoutDetector.evaluate(taobao, listOf("确认订单", "提交订单", "合计：￥199.90")).checkout)
        assertFalse(CheckoutDetector.evaluate(taobao, listOf("首页", "立即购买", "¥199")).checkout)
        assertFalse(CheckoutDetector.evaluate(taobao, listOf("提交订单操作说明")).checkout)
        assertFalse(CheckoutDetector.evaluate(taobao, listOf("确认订单", "选择地址")).checkout)
    }
    @Test fun eachSupportedShopUsesConservativeRules() {
        Shops.all.forEach { assertTrue(CheckoutDetector.evaluate(it.packageName, listOf("填写订单", "提交并支付")).checkout) }
    }
    @Test fun paymentAndOrderHistoryAreExcluded() {
        listOf("退款详情", "订单详情", "付款成功", "请输入支付密码", "付款码").forEach {
            assertFalse(CheckoutDetector.evaluate(taobao, listOf("确认订单", "去支付", it)).checkout)
        }
        assertFalse(CheckoutDetector.evaluate("com.eg.android.AlipayGphone", listOf("确认订单", "去支付")).checkout)
        assertFalse(CheckoutDetector.evaluate("com.tencent.mm", listOf("确认订单", "去支付")).checkout)
    }
    @Test fun totalIsOptionalAndMustBeLabeled() {
        assertEquals(19990L, CheckoutDetector.evaluate(taobao, listOf("确认订单", "提交订单", "合计：￥199.90")).amountCents)
        assertNull(CheckoutDetector.evaluate(taobao, listOf("确认订单", "提交订单", "优惠券￥50")).amountCents)
        assertTrue(CheckoutDetector.evaluate(taobao, listOf("确认订单", "提交订单")).checkout)
    }
    @Test fun invalidAndFractionalMoneyCannotBeSilentlyRounded() {
        assertEquals(101L, Money.parseCents("1.01"))
        assertEquals(100L, Money.parseCents("￥1"))
        listOf("0", "-1", "NaN", "1.001", "99999999", "1,000", "1e9").forEach { assertNull(Money.parseCents(it)) }
    }
    @Test fun remindersRespectPassCooldownAndDismissal() {
        val gate = ReminderGate(); val t = 1_000_000L
        assertTrue(gate.mayShow(taobao, t, 0, 0))
        assertFalse(gate.mayShow(taobao, t, t+1, 0))
        assertFalse(gate.mayShow(taobao, t, 0, t-1000))
        gate.markShown(taobao, t)
        assertFalse(gate.mayShow(taobao, t+30_000, 0, 0))
        assertTrue(gate.mayShow(taobao, t+900_000, 0, 0))
        assertTrue(gate.mayShow("com.jingdong.app.mall", t, 0, 0))
    }
    @Test fun shareImportDoesNotInventProductDetails() {
        assertEquals("https://item.example/a", SharedText.link("看看 https://item.example/a，喜欢吗"))
        assertNull(SharedText.link("复制口令 ABC123"))
        assertTrue(SharedText.title("https://item.example/a").isNotBlank())
        assertTrue(SharedText.title("a".repeat(500)).length <= 120)
    }
}
