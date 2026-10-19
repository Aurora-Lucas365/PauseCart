package io.github.auroralucas365.pausecart

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.auroralucas365.pausecart.core.*
import io.github.auroralucas365.pausecart.data.*
import io.github.auroralucas365.pausecart.service.ShoppingService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ImportDraft(val title: String = "", val cents: Long? = null, val link: String = "", val source: String = "手动记录", val nonce: Long = System.nanoTime())

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<ImportDraft?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) readIntent(intent)
        setContent { PauseCartApp(incoming) { incoming = null } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readIntent(intent) }
    private fun readIntent(intent: Intent?) {
        when {
            intent?.action == Intent.ACTION_SEND && intent.type == "text/plain" -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().take(8000)
                incoming = ImportDraft(SharedText.title(text), link = SharedText.link(text).orEmpty(), source = "分享导入")
            }
            intent?.hasExtra("capture_source") == true -> {
                val source = intent.getStringExtra("capture_source").orEmpty().take(30)
                incoming = ImportDraft("$source · 待考虑商品", intent.getLongExtra("capture_cents", -1).takeIf { it > 0 }, source = source)
            }
        }
        intent?.removeExtra("capture_source")
        intent?.removeExtra(Intent.EXTRA_TEXT)
    }
}

private val Forest = Color(0xFF30674F)
@Composable
fun PauseCartApp(incoming: ImportDraft?, consumeIncoming: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Preferences(ctx) }
    val dao = remember { CartDatabase.get(ctx).items() }
    val saved by dao.observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var page by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf<ImportDraft?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var demo by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    var experimentalShop by remember { mutableStateOf<Shop?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<SavedItem?>(null) }
    var confirmLink by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if(event == Lifecycle.Event.ON_RESUME) { refresh++; now = System.currentTimeMillis() } }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(30_000) }
        }
    }
    LaunchedEffect(incoming?.nonce) { if(incoming != null) { draft = incoming; consumeIncoming() } }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        prefs.reminders = granted; refresh++
        if (granted) saved.filter { it.status == "WAITING" }.forEach { ReminderScheduler.schedule(ctx, it) }
    }
    fun systemSettings() {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .onFailure { error = "无法打开无障碍设置，请在手机设置中搜索“无障碍”。" }
    }
    fun updateItem(item: SavedItem) { scope.launch { runCatching { dao.update(item); ReminderScheduler.cancel(ctx, item.id); ReminderScheduler.schedule(ctx, item) }.onFailure { error = "保存失败，请重试。" } } }
    MaterialTheme(colorScheme = lightColorScheme(primary = Forest, secondary = Color(0xFF61745C), background = Color(0xFFF5F7F3), surface = Color(0xFFF5F7F3))) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { NavigationBar { listOf("首页", "冷静清单", "提醒设置", "说明").forEachIndexed { i, label ->
                NavigationBarItem(selected = page == i, onClick = { page = i }, icon = { Text(listOf("Ⅱ", "□", "⚙", "i")[i]) }, label = { Text(label) })
            } } }) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text("缓购", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold); Text("PauseCart · 想清楚，再决定", color = Forest) }
                when(page) {
                    0 -> {
                        item { InfoCard("给想买的东西一点时间", "先保留它，再决定是否需要。基础清单无需授权，也无需账户。") }
                        item { Button(onClick = { draft = ImportDraft() }, modifier = Modifier.fillMaxWidth().testTag("add_item")) { Text("＋ 记下想买的东西") } }
                        item { StatusCard(refresh, prefs) }
                        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Stat("等待中", saved.count { it.status == "WAITING" }.toString(), Modifier.weight(1f))
                            Stat("已作决定", saved.count { it.status != "WAITING" }.toString(), Modifier.weight(1f))
                        } }
                        item { InfoCard("自动提醒是可选功能", "首版提供淘宝、京东、拼多多的入口提醒与实验性结算规则。尚未完成这些平台的真机验证，不能确保在付款前出现。") }
                        item { OutlinedButton(onClick = { demo = true }, modifier = Modifier.fillMaxWidth()) { Text("体验一次暂停提醒（演示）") } }
                        item { TextButton(onClick = { page = 2 }) { Text("设置自动提醒 →") } }
                    }
                    1 -> {
                        item { Text("冷静清单", style = MaterialTheme.typography.headlineSmall); Text("到期只是可以重新考虑，不是催你购买。") }
                        item { Button(onClick = { draft = ImportDraft() }) { Text("＋ 新增记录") } }
                        if(saved.isEmpty()) item { InfoCard("这里还很安静", "手动记录，或从购物 App 的系统分享菜单选择“缓购”。分享入口不可用时，复制后主动粘贴到新增记录。") }
                        items(saved, key = { it.id }) { item ->
                            ItemCard(item, now,
                                decide = { status -> updateItem(item.copy(status = status)) },
                                waitLonger = { updateItem(item.copy(status = "WAITING", dueAt = System.currentTimeMillis() + 24*60*60_000L)) },
                                feel = { updateItem(item.copy(feeling = it)) },
                                delete = { confirmDelete = item }, open = { confirmLink = item.link })
                        }
                    }
                    2 -> {
                        item { Text("提醒设置", style = MaterialTheme.typography.headlineSmall); Text("默认全部关闭。只开启你需要的功能。") }
                        item { StatusCard(refresh, prefs) }
                        item { Button(onClick = { if(prefs.consent) systemSettings() else consent = true }, modifier = Modifier.fillMaxWidth()) { Text("查看授权说明 / 打开无障碍设置") } }
                        item { SettingsSwitch("暂停自动提醒", "暂停后不提取购物页面内容，并撤下当前提醒。", prefs.paused, refresh) { prefs.paused = it; refresh++ } }
                        items(Shops.all) { shop ->
                            Card(modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(shop.name, fontWeight = FontWeight.SemiBold)
                                Text("结算识别：未真机验证", style = MaterialTheme.typography.bodySmall)
                                Mode.entries.forEach { mode ->
                                    Row { RadioButton(selected = prefs.mode(shop.packageName) == mode, onClick = {
                                        if(mode == Mode.CHECKOUT) experimentalShop = shop else { prefs.setMode(shop.packageName, mode); refresh++ }
                                    }); TextButton(onClick = { if(mode == Mode.CHECKOUT) experimentalShop = shop else { prefs.setMode(shop.packageName, mode); refresh++ } }) { Text(mode.label) } }
                                }
                                if(prefs.passUntil(shop.packageName) > now) Text("临时放行至 ${date(prefs.passUntil(shop.packageName))}")
                            } }
                        }
                        item { OutlinedButton(onClick = { prefs.clearPasses(); refresh++ }) { Text("结束所有临时放行") } }
                        item { SettingsSwitch("冷静期到期通知", "默认关闭。系统可能延后送达；同时到期的记录合并成一条提示。", prefs.reminders, refresh) { enabled ->
                            if(!enabled) { prefs.reminders = false; ReminderScheduler.cancelAll(ctx); refresh++ }
                            else if(Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else { prefs.reminders = true; saved.filter { it.status == "WAITING" }.forEach { ReminderScheduler.schedule(ctx, it) }; refresh++ }
                        } }
                        item { InfoCard("后台运行与权限", "界面无需一直打开，自动提醒依赖无障碍服务连接。重启、省电模式、强行停止或系统安全设置可能使它失效。没有任何权限能保证识别所有付款。无需 Root、ADB、Shizuku、银行账号或录屏。") }
                    }
                    3 -> {
                        item { InfoCard("v${BuildConfig.VERSION_NAME} · 首个测试版", "Android 8.0+ 为安装要求；无原生 ABI 限制，不依赖 Google 服务。可安装不等于所有功能已验证。荣耀 400 / MagicOS 10 / Android 16 是待测试设备，尚未实测。iOS 暂无安装包。") }
                        item { InfoCard("数据只留在这台手机", "本应用不声明互联网权限、不使用广告或分析 SDK。商品与决定存储在应用本地数据库，关闭系统备份。无障碍仅处理所选购物 App 的必要节点，页面文字只在内存中短暂处理，不保存完整页面、不自动点击、不付款、不读通知或后台剪贴板。") }
                        item { InfoCard("识别与统计边界", "结算规则可能漏报或误报，保护界面可能不可读。商品价格需要你确认。是否购买与满意度由你手动记录；放弃商品的标价不等于真实节省金额。基础清单不需要无障碍权限。") }
                        item { TextButton(onClick = { confirmLink = "https://github.com/Aurora-Lucas365/PauseCart" }) { Text("项目、下载与完整说明 →") } }
                        item { TextButton(onClick = { confirmClear = true }) { Text("删除全部本机数据并停用提醒", color = MaterialTheme.colorScheme.error) } }
                    }
                }
                item { Spacer(Modifier.height(12.dp)); Text("购买与否，由你决定。", style = MaterialTheme.typography.bodySmall, color = Forest) }
            }
        }
        draft?.let { value -> AddItemDialog(value, dismiss = { draft = null }, save = { title, cents, link, hours ->
            scope.launch { runCatching {
                val item = SavedItem(title = title, cents = cents, link = link, source = value.source, dueAt = System.currentTimeMillis() + hours * 60*60_000L)
                val id = dao.insert(item); ReminderScheduler.schedule(ctx, item.copy(id = id)); draft = null; page = 1
            }.onFailure { error = "保存失败，记录尚未关闭，请重试。" } }
        }) }
        if(consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text("开启前，请了解权限") }, text = { Text("无障碍权限允许服务读取屏幕。本应用仅在你选定的购物 App 中按模式判断入口或结算页面；接收切换事件以撤下提醒。内容在本机处理，不上传、不保存完整页面、不代替你操作。部分界面不可读，不能保证付款前提醒。基础清单不需要此权限。你可以拒绝或随时在系统关闭。") },
            confirmButton = { TextButton(onClick = { prefs.consent = true; consent = false; systemSettings() }) { Text("同意并前往系统设置") } }, dismissButton = { TextButton(onClick = { consent = false }) { Text("仅使用清单") } })
        experimentalShop?.let { shop -> AlertDialog(onDismissRequest = { experimentalShop = null }, title = { Text("${shop.name} · 实验性结算识别") }, text = { Text("此版本尚未在真实购物页面验证。可能漏报、误报或完全不可用。系统保护的页面不会被绕过。请勿把它当成付款拦截器；不支持时可改用入口提醒。") }, confirmButton = { TextButton(onClick = { prefs.setMode(shop.packageName, Mode.CHECKOUT); experimentalShop = null; refresh++ }) { Text("了解，试用实验规则") } }, dismissButton = { TextButton(onClick = { experimentalShop = null }) { Text("取消") } }) }
        if(demo) AlertDialog(onDismissRequest = { demo = false }, title = { Text("先给自己一点时间") }, text = { Text("演示场景：一件 ¥199 的商品准备结算。\n\n这次购买在计划之内吗？\n\n这只是演示，没有读取其他 App，也没有发生交易。") }, confirmButton = { TextButton(onClick = { demo = false; draft = ImportDraft("演示商品（可删除）", 19900, source = "演示") }) { Text("先放冷静清单") } }, dismissButton = { TextButton(onClick = { demo = false }) { Text("关闭演示") } })
        confirmDelete?.let { item -> AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("删除这条记录？") }, text = { Text(item.title) }, confirmButton = { TextButton(onClick = { scope.launch { dao.delete(item.id); ReminderScheduler.cancel(ctx, item.id); confirmDelete = null } }) { Text("删除") } }, dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } }) }
        if(confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("删除全部本机数据？") }, text = { Text("商品与决定会被删除，通知会取消，所有自动提醒会关闭。系统无障碍开关请在手机设置中关闭。无法撤销。") }, confirmButton = { TextButton(onClick = { scope.launch { dao.deleteAll(); ReminderScheduler.cancelAll(ctx); prefs.reset(); confirmClear = false; refresh++; page = 0 } }) { Text("确认删除") } }, dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } })
        confirmLink?.let { url -> AlertDialog(onDismissRequest = { confirmLink = null }, title = { Text("打开外部链接？") }, text = { Text(url.take(300) + "\n\n链接内容由外部网站提供，请自行核对。") }, confirmButton = { TextButton(onClick = {
            val uri = Uri.parse(url)
            if(uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank()) runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure { error = "没有可打开链接的应用。" }
            else error = "仅支持有效的 http / https 链接。"
            confirmLink = null
        }) { Text("继续打开") } }, dismissButton = { TextButton(onClick = { confirmLink = null }) { Text("取消") } }) }
        error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("提示") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("知道了") } }) }
    }
}

@Composable private fun InfoCard(title: String, text: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(text, style = MaterialTheme.typography.bodyMedium) } }
}
@Composable private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Color(0xFFE1EBDD), RoundedCornerShape(16.dp)).padding(18.dp)) { Text(value, style = MaterialTheme.typography.headlineMedium, color = Forest); Text(label) }
}
@Composable private fun StatusCard(refresh: Int, prefs: Preferences) {
    val ctx = LocalContext.current
    val enabled = remember(refresh) { ctx.getSystemService(AccessibilityManager::class.java).getEnabledAccessibilityServiceList(-1).any { it.resolveInfo.serviceInfo.packageName == ctx.packageName } }
    val text = when {
        prefs.paused -> "自动提醒已暂停"
        !prefs.consent -> "自动提醒未授权 · 基础清单可用"
        !enabled -> "系统无障碍服务未开启"
        !ShoppingService.connected -> "服务尚未连接，请检查系统设置"
        Shops.all.none { prefs.mode(it.packageName) != Mode.OFF } -> "服务已连接 · 尚未选择购物 App"
        else -> "服务已连接 · 结算识别尚未真机验证"
    }
    InfoCard("提醒状态", text)
}
@Composable private fun SettingsSwitch(title: String, description: String, checked: Boolean, refresh: Int, change: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { Text(title, modifier = Modifier.weight(1f)); Switch(checked, change) }; Text(description, style = MaterialTheme.typography.bodySmall) } }
}
@Composable private fun ItemCard(item: SavedItem, now: Long, decide: (String) -> Unit, waitLonger: () -> Unit, feel: (String) -> Unit, delete: () -> Unit, open: () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(item.title, fontWeight = FontWeight.SemiBold)
        Text(item.cents?.let { "¥${Money.format(it)}" } ?: "未记录价格")
        Text("${item.source} · ${when(item.status) { "BOUGHT" -> "已购买（自报）"; "SKIPPED" -> "已放弃（自报）"; else -> if(now >= item.dueAt) "可以重新考虑" else "等待至 ${date(item.dueAt)}" }}", style = MaterialTheme.typography.bodySmall)
        if(item.status == "WAITING") {
            if(now >= item.dueAt) Row { TextButton(onClick = { decide("BOUGHT") }) { Text("已买") }; TextButton(onClick = { decide("SKIPPED") }) { Text("放弃") } }
            else TextButton(onClick = { decide("BOUGHT") }) { Text("我已提前购买 · 如实记录") }
            Row { TextButton(onClick = waitLonger) { Text("再等 24 小时") }; if(now < item.dueAt) TextButton(onClick = { decide("SKIPPED") }) { Text("决定放弃") } }
        } else {
            TextButton(onClick = { decide("WAITING") }) { Text("撤销决定") }
            if(item.status == "BOUGHT") { Text("购买感受：${item.feeling.ifBlank { "尚未记录" }}"); Row { listOf("满意", "一般", "后悔").forEach { value -> TextButton(onClick = { feel(value) }) { Text(value) } } } }
        }
        Row { if(item.link.isNotBlank()) TextButton(onClick = open) { Text("查看商品链接") }; TextButton(onClick = delete) { Text("删除") } }
    } }
}
@Composable private fun AddItemDialog(initial: ImportDraft, dismiss: () -> Unit, save: (String, Long?, String, Int) -> Unit) {
    var title by remember(initial.nonce) { mutableStateOf(initial.title) }
    var price by remember(initial.nonce) { mutableStateOf(initial.cents?.let(Money::format).orEmpty()) }
    var link by remember(initial.nonce) { mutableStateOf(initial.link) }
    var hours by remember(initial.nonce) { mutableIntStateOf(24) }
    var validation by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("先记下来，稍后决定") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.imePadding()) {
            item { OutlinedTextField(title, { title = it.take(120) }, label = { Text("商品名称 / 分享文字") }, modifier = Modifier.testTag("item_title"), maxLines = 3) }
            item { OutlinedTextField(price, { price = it.take(12) }, label = { Text("价格（可不填，请核对）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.testTag("item_price"), singleLine = true) }
            item { OutlinedTextField(link, { link = it.take(2000) }, label = { Text("链接 / 分享口令（可不填）") }, maxLines = 3) }
            item { Text("冷静时间"); Row { listOf(1,24,72).forEach { value -> FilterChip(selected = hours == value, onClick = { hours = value }, label = { Text("${value}小时") }, modifier = Modifier.padding(end = 6.dp)) } } }
            if(validation.isNotBlank()) item { Text(validation, color = MaterialTheme.colorScheme.error) }
            item { Text("默认不发到期通知，可在设置中开启。分享内容只保存你确认的字段。", style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = { TextButton(onClick = {
        val amount = if(price.isBlank()) null else Money.parseCents(price)
        if(title.isBlank()) validation = "请填写一个商品名称。"
        else if(price.isNotBlank() && amount == null) validation = "价格应大于 0，最多保留两位小数。"
        else { val url = SharedText.link(link).orEmpty(); save(title.trim(), amount, if(url.isNotBlank()) url else link.trim(), hours) }
    }, modifier = Modifier.testTag("save_item")) { Text("放入冷静清单") } }, dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}
private fun date(value: Long) = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(value))
