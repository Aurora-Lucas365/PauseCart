@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.auroralucas365.pausecart.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.auroralucas365.pausecart.*
import io.github.auroralucas365.pausecart.core.*
import io.github.auroralucas365.pausecart.data.*
import io.github.auroralucas365.pausecart.service.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Light = lightColorScheme(primary=Color(0xFF30674F),onPrimary=Color.White,
    primaryContainer=Color(0xFFE9F2E8),onPrimaryContainer=Color(0xFF20352D),
    secondary=Color(0xFF30674F),secondaryContainer=Color(0xFFE9F2E8),onSecondaryContainer=Color(0xFF20352D),
    background=Color(0xFFFAFBF8),surface=Color(0xFFFAFBF8),surfaceVariant=Color(0xFFF0F3ED),
    onBackground=Color(0xFF20352D),onSurface=Color(0xFF20352D),onSurfaceVariant=Color(0xFF627169),outlineVariant=Color(0xFFE0E7DF))
private val Dark = darkColorScheme(primary=Color(0xFF92D1AC),onPrimary=Color(0xFF123425),
    primaryContainer=Color(0xFF293D31),onPrimaryContainer=Color(0xFFE5EEE9),
    secondary=Color(0xFF92D1AC),secondaryContainer=Color(0xFF293D31),onSecondaryContainer=Color(0xFFE5EEE9),
    background=Color(0xFF151D19),surface=Color(0xFF151D19),surfaceVariant=Color(0xFF202C25),
    onBackground=Color(0xFFE5EEE9),onSurface=Color(0xFFE5EEE9),onSurfaceVariant=Color(0xFFA9B7AE),outlineVariant=Color(0xFF3A473F))

@Composable
fun PauseCartApp(incoming: ImportDraft?, consumeIncoming: () -> Unit) {
    val ctx=LocalContext.current
    val prefs=remember { Preferences(ctx) }
    val cfg by remember { prefs.observe() }.collectAsStateWithLifecycle(initialValue=prefs.snapshot())
    val runtime by ServiceRuntime.state.collectAsStateWithLifecycle()
    val dao=remember { CartDatabase.get(ctx).items() }
    val saved by dao.observe().collectAsStateWithLifecycle(initialValue=emptyList())
    val scope=rememberCoroutineScope()
    val snackbar=remember { SnackbarHostState() }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var completed by rememberSaveable { mutableStateOf(false) }
    var help by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<ImportDraft?>(null) }
    var detail by remember { mutableStateOf<SavedItem?>(null) }
    var consent by remember { mutableStateOf(false) }
    var experimental by remember { mutableStateOf<Shop?>(null) }
    var preview by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf<SavedItem?>(null) }
    var link by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME) { refresh++; now=System.currentTimeMillis() } }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { while(true) { now=System.currentTimeMillis(); delay(1000) } }
    LaunchedEffect(incoming?.nonce) { incoming?.let { draft=it; consumeIncoming() } }
    val enabled=remember(refresh) { ctx.getSystemService(AccessibilityManager::class.java).getEnabledAccessibilityServiceList(-1).any { it.resolveInfo.serviceInfo.packageName==ctx.packageName && it.resolveInfo.serviceInfo.name==ShoppingService::class.java.name } }
    val installed=remember(refresh) { Shops.all.associate { it.packageName to runCatching { ctx.packageManager.getApplicationInfo(it.packageName,0).enabled }.getOrDefault(false) } }
    val count=Shops.all.count { cfg.mode(it.packageName)!=Mode.OFF }
    val status=when {
        cfg.paused -> "提醒已暂停"
        !cfg.consent || !enabled -> "自动提醒未开启"
        !runtime.connected -> "等待服务连接"
        runtime.appliedSettings!=cfg.signature -> "正在应用设置"
        count==0 -> "请选择购物 App"
        Shops.all.none { cfg.mode(it.packageName)!=Mode.OFF && installed[it.packageName]==true } -> "尚未检测到所选 App 的标准版"
        else -> "提醒已启用 · $count 个 App"
    }
    fun notify(text: String) { scope.launch { snackbar.showSnackbar(text) } }
    fun systemSettings() { runCatching { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }.onFailure { info="请在手机设置中搜索“无障碍”，找到“缓购 · 购物提醒”。" } }
    fun appSettings() { runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${ctx.packageName}"))) }.onFailure { info="请前往系统设置 → 应用 → 应用管理 → 缓购 PauseCart。" } }
    fun update(item: SavedItem) { scope.launch { runCatching { dao.update(item); ReminderScheduler.cancel(ctx,item.id); ReminderScheduler.schedule(ctx,item) }.onSuccess { detail=null }.onFailure { info="保存失败，请重试。" } } }
    fun setMode(shop: Shop, mode: Mode) {
        val wasPass=prefs.passUntil(shop.packageName)>System.currentTimeMillis()
        prefs.setMode(shop.packageName,mode)
        if(wasPass) notify("已更改提醒方式，并结束${shop.name}的临时放行")
    }
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        prefs.reminders=granted
        if(granted) saved.filter { it.status=="WAITING" }.forEach { ReminderScheduler.schedule(ctx,it) }
        else notify("通知未开启，可直接在清单查看到期商品")
    }
    BackHandler(enabled=page!=0 && draft==null && detail==null) { page=0 }
    MaterialTheme(colorScheme=if(isSystemInDarkTheme()) Dark else Light) {
        Scaffold(snackbarHost={ SnackbarHost(snackbar) },containerColor=MaterialTheme.colorScheme.background,
            bottomBar={ NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp) {
                val names=listOf("提醒","清单","设置"); val icons=listOf(Icons.Default.Notifications,Icons.Default.List,Icons.Default.Settings)
                names.forEachIndexed { index,name -> NavigationBarItem(selected=page==index,onClick={ page=index },
                    icon={ Icon(icons[index],null) },label={ Text(name) },modifier=Modifier.testTag("nav_$index")) }
            } }) { padding ->
            LazyColumn(modifier=Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("main_list"),
                contentPadding=PaddingValues(horizontal=22.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("缓购",fontSize=23.sp,fontWeight=FontWeight.SemiBold)
                    IconButton(onClick={ page=2; help=true }) { Icon(Icons.Default.Info,"使用帮助",tint=MaterialTheme.colorScheme.onSurfaceVariant) }
                } }
                when(page) {
                    0 -> {
                        item { Title("买之前，停一下。","选好购物 App，提醒交给缓购。") }
                        item { Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(16.dp)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text(status,Modifier.weight(1f),fontSize=14.sp,fontWeight=FontWeight.Medium)
                                TextButton(onClick={ when { cfg.paused -> prefs.paused=false; !cfg.consent -> consent=true; !enabled -> systemSettings(); !runtime.connected -> { help=true; refresh++ }; else -> prefs.paused=true } }) {
                                    Text(when { cfg.paused -> "恢复"; !cfg.consent || !enabled -> "去开启"; !runtime.connected -> "检查"; else -> "暂停" })
                                }
                            }
                        } }
                        item { Text("打开这些 App 时提醒",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(Shops.all,key={ it.packageName }) { shop ->
                            Column {
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(12.dp)) { Text(shop.name.take(1),Modifier.padding(horizontal=13.dp,vertical=9.dp),color=MaterialTheme.colorScheme.primary) }
                                    Column(Modifier.weight(1f)) {
                                        Text(shop.name,fontWeight=FontWeight.Medium)
                                        Text(if(installed[shop.packageName]!=true) "未检测到标准版" else cfg.mode(shop.packageName).label,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.testTag("mode_${shop.packageName}"))
                                    }
                                    Switch(checked=cfg.mode(shop.packageName)!=Mode.OFF,onCheckedChange={ value -> setMode(shop,if(value) Mode.ENTRY else Mode.OFF); if(value && !cfg.consent) consent=true },modifier=Modifier.testTag("shop_${shop.packageName}"))
                                }
                                val until=cfg.passes[shop.packageName] ?: 0
                                if(until>now) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                    Text("${shop.name} · 还有 ${remaining(until,now)} 不提醒",Modifier.weight(1f),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(onClick={ prefs.endPass(shop.packageName) }) { Text("结束") }
                                }
                                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                        item { Button(onClick={ preview=true },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)) { Icon(Icons.Default.Notifications,null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("预览提醒") } }
                        item { TextButton(onClick={ help=!help },contentPadding=PaddingValues(0.dp)) { Text(if(help) "收起检查" else "没有出现提醒？") } }
                        if(help) item { Section {
                            Text("逐步检查",fontWeight=FontWeight.Medium)
                            CheckLine("系统授权",if(enabled) "已开启" else "尚未开启")
                            CheckLine("服务连接",if(runtime.connected) "已连接" else "尚未连接")
                            CheckLine("提醒窗口",runtime.windowCheck)
                            if(runtime.lastEventAt>0) {
                                val name=Shops.find(runtime.lastPackage)?.name ?: "本机测试页"
                                CheckLine("最近检测","$name · ${date(runtime.lastEventAt)}")
                                Text(runtime.result,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            } else Text("返回桌面后打开所选购物 App，再回到这里查看检测结果。",fontSize=13.sp)
                            Button(onClick={ if(!cfg.consent) consent=true else if(!enabled) systemSettings() else if(!ShoppingService.preview()) notify("服务尚未连接，请关闭再开启系统无障碍服务") },modifier=Modifier.testTag("check_window")) { Text(if(!enabled) "开启系统授权" else "检查提醒窗口") }
                            Text("窗口检查成功，只说明可以显示提醒；真实 App 的进入检测需单独确认。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("放行中不会提醒；离开同一 App 满 30 秒再进入，会开始新一次访问。分身、极速版等需单独适配。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        } }
                        item { Text("不联网 · 数据只留在本机",Modifier.fillMaxWidth(),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    1 -> {
                        item { Title("先留着，明天再决定。","记下来，就不用急着买。") }
                        item { Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            FilterChip(selected=!completed,onClick={ completed=false },label={ Text("冷静中") })
                            FilterChip(selected=completed,onClick={ completed=true },label={ Text("已决定") })
                        } }
                        item { Button(onClick={ draft=ImportDraft() },modifier=Modifier.fillMaxWidth().testTag("add_item"),shape=RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add,null); Spacer(Modifier.width(6.dp)); Text("记下想买的东西") } }
                        val visible=saved.filter { (it.status!="WAITING")==completed }
                        if(visible.isEmpty()) item { Text(if(completed) "这里会留下你做过的决定。" else "还没有想买的东西，先轻松一下。\n也可以从购物 App 分享文字或链接到缓购。",color=MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(visible,key={ it.id }) { item ->
                            Card(onClick={ detail=item },colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),shape=RoundedCornerShape(16.dp)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                    Text(item.title,fontWeight=FontWeight.Medium)
                                    item.cents?.let { Text("¥${Money.format(it)}") }
                                    Text(when(item.status) { "BOUGHT" -> "已买了"; "SKIPPED" -> "决定不买了"; else -> if(now>=item.dueAt) "现在还想买吗？" else "再等 ${remaining(item.dueAt,now)}" },fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    if(item.status=="WAITING") TextButton(onClick={ detail=item },contentPadding=PaddingValues(0.dp)) { Text(if(now>=item.dueAt) "做个决定" else "查看与决定") }
                                }
                            }
                        }
                    }
                    2 -> {
                        item { Title("设置","按照自己的节奏来。") }
                        item { SettingsRow("冷静期到期通知") { Switch(checked=cfg.reminders,onCheckedChange={ value ->
                            if(!value) { prefs.reminders=false; ReminderScheduler.cancelAll(ctx) }
                            else if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(ctx,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else { prefs.reminders=true; saved.filter { it.status=="WAITING" }.forEach { ReminderScheduler.schedule(ctx,it) } }
                        }) } }
                        item { Text("默认冷静时间",fontWeight=FontWeight.Medium); Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf(1,24,72).forEach { hours -> FilterChip(selected=cfg.hours==hours,onClick={ prefs.hours=hours },label={ Text("${hours}小时") },modifier=Modifier.testTag("hours_$hours")) }
                        } }
                        item { TextButton(onClick={ advanced=!advanced },contentPadding=PaddingValues(0.dp)) { Text("进阶：提醒时机") } }
                        if(advanced) {
                            item { Text("下单前提醒为试用功能，真实页面兼容情况按平台单独验证。",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(Shops.all) { shop -> Section {
                                Text(shop.name,fontWeight=FontWeight.Medium)
                                if(cfg.mode(shop.packageName)==Mode.OFF) Text("先在提醒页开启这个 App",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                else Column {
                                    listOf(Mode.ENTRY,Mode.CHECKOUT).forEach { mode -> Row(verticalAlignment=Alignment.CenterVertically) {
                                        RadioButton(selected=cfg.mode(shop.packageName)==mode,onClick={ if(mode==Mode.CHECKOUT && cfg.mode(shop.packageName)!=mode) experimental=shop else setMode(shop,mode) },modifier=Modifier.testTag("${shop.packageName}_${mode.name}"))
                                        TextButton(onClick={ if(mode==Mode.CHECKOUT && cfg.mode(shop.packageName)!=mode) experimental=shop else setMode(shop,mode) }) { Text(mode.label) }
                                    } }
                                }
                            } }
                        }
                        item { HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant) }
                        item { TextButton(onClick={ help=!help },contentPadding=PaddingValues(0.dp)) { Text("权限与使用帮助") } }
                        if(help) item { Section {
                            Text("自动提醒需要无障碍权限；仅使用清单不需要。",fontSize=14.sp)
                            Text("如果荣耀提示“受限制的设置”，进入应用信息页，在更多菜单中查找系统提供的解除入口，再返回无障碍设置开启。",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick={ appSettings() }) { Text("打开应用信息") }
                            TextButton(onClick={ if(cfg.consent) systemSettings() else consent=true }) { Text("打开无障碍设置") }
                            Text("省电管理、强行停止或系统限制可能中断服务。若 App 未被检测，请检查标准版、分身与后台设置。",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        } }
                        item { TextButton(onClick={ info="商品与决定保存在本机，无账号、广告或互联网权限。自动提醒只在所选购物 App 中按模式处理，页面文字不保存、不上传，不自动点击或付款。卸载会删除本机数据。\n\nAndroid 8.0+；iPhone 暂不支持。版本 ${BuildConfig.VERSION_NAME}。真实平台兼容结果见项目记录。" },contentPadding=PaddingValues(0.dp)) { Text("隐私与关于") } }
                        item { TextButton(onClick={ link="https://github.com/Aurora-Lucas365/PauseCart" },contentPadding=PaddingValues(0.dp)) { Text("项目与版本下载") } }
                        item { TextButton(onClick={ confirmClear=true },contentPadding=PaddingValues(0.dp)) { Text("删除本机数据",color=MaterialTheme.colorScheme.error) } }
                        item { Text("PauseCart · ${BuildConfig.VERSION_NAME}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        draft?.let { initial -> AddItemSheet(initial,cfg.hours,dismiss={ draft=null },save={ title,cents,url,hours ->
            scope.launch { runCatching {
                val item=SavedItem(title=title,cents=cents,link=url,source=initial.source,dueAt=System.currentTimeMillis()+hours*60*60_000L)
                val id=dao.insert(item); ReminderScheduler.schedule(ctx,item.copy(id=id)); draft=null; page=1; completed=false
            }.onFailure { info="保存失败，内容仍在表单中，请重试。" } }
        }) }
        detail?.let { item -> ModalBottomSheet(onDismissRequest={ detail=null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal=22.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(item.title,fontSize=22.sp,fontWeight=FontWeight.Medium)
                Text(item.cents?.let { "¥${Money.format(it)}" } ?: "未记录价格")
                Text(if(item.status=="WAITING") "现在还想买吗？" else if(item.status=="BOUGHT") "你记录为已买了" else "你决定不买了",color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(item.status=="WAITING") {
                    Button(onClick={ update(item.copy(status="BOUGHT")) },modifier=Modifier.fillMaxWidth()) { Text("买了") }
                    OutlinedButton(onClick={ update(item.copy(status="SKIPPED")) },modifier=Modifier.fillMaxWidth().testTag("skip_item")) { Text("不买了") }
                    TextButton(onClick={ update(item.copy(dueAt=System.currentTimeMillis()+24*60*60_000L)) }) { Text("再等 24 小时") }
                } else {
                    TextButton(onClick={ update(item.copy(status="WAITING",dueAt=System.currentTimeMillis()+24*60*60_000L)) }) { Text("重新考虑 · 再等 24 小时") }
                    if(item.status=="BOUGHT") Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("满意","一般","后悔").forEach { feeling -> FilterChip(selected=item.feeling==feeling,onClick={ update(item.copy(feeling=feeling)) },label={ Text(feeling) }) } }
                }
                if(item.link.isNotBlank()) TextButton(onClick={ link=item.link }) { Text("打开商品链接") }
                TextButton(onClick={ delete=item }) { Text("删除这条记录",color=MaterialTheme.colorScheme.error) }
            }
        } }
        if(consent) AlertDialog(onDismissRequest={ consent=false },title={ Text("开启购物提醒") },text={ Text("无障碍权限允许服务读取屏幕。缓购只在你选择的购物 App 中判断入口或下单页面，在本机处理，不上传、不保存页面，不代替你操作。\n\n你可以随时关闭；仅使用清单不需要授权。") },confirmButton={ TextButton(onClick={ prefs.consent=true; consent=false; systemSettings() }) { Text("同意并前往设置") } },dismissButton={ TextButton(onClick={ consent=false }) { Text("暂不开启") } })
        experimental?.let { shop -> AlertDialog(onDismissRequest={ experimental=null },title={ Text("试用下单前提醒") },text={ Text("${shop.name}的实际结算页面尚需验证，可能漏报或误判。识别不到时可使用打开时提醒。") },confirmButton={ TextButton(onClick={ setMode(shop,Mode.CHECKOUT); experimental=null }) { Text("试用") } },dismissButton={ TextButton(onClick={ experimental=null }) { Text("取消") } }) }
        if(preview) ModalBottomSheet(onDismissRequest={ preview=false }) {
            Column(Modifier.padding(horizontal=22.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("缓购 · 界面预览",color=MaterialTheme.colorScheme.primary,fontSize=13.sp)
                Text("这次有明确要买的东西吗？",fontSize=23.sp,fontWeight=FontWeight.Medium)
                Text("先停一下，想买的可以记下来。",color=MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick={ preview=false; draft=ImportDraft() },modifier=Modifier.fillMaxWidth()) { Text("先记下来") }
                OutlinedButton(onClick={ preview=false },modifier=Modifier.fillMaxWidth()) { Text("关闭预览") }
                Text("这是应用内预览；服务窗口和真实 App 检测请使用“没有出现提醒？”中的分步检查。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(confirmClear) AlertDialog(onDismissRequest={ confirmClear=false },title={ Text("删除全部本机数据？") },text={ Text("清单、决定和设置会删除，通知与自动提醒会停用，无法撤销。系统无障碍开关请在手机设置中关闭。") },confirmButton={ TextButton(onClick={ scope.launch { dao.deleteAll(); ReminderScheduler.cancelAll(ctx); prefs.reset(); confirmClear=false; page=0 } }) { Text("确认删除") } },dismissButton={ TextButton(onClick={ confirmClear=false }) { Text("取消") } })
        delete?.let { item -> AlertDialog(onDismissRequest={ delete=null },title={ Text("删除这条记录？") },text={ Text(item.title) },confirmButton={ TextButton(onClick={ scope.launch { dao.delete(item.id); ReminderScheduler.cancel(ctx,item.id); delete=null; detail=null } }) { Text("删除") } },dismissButton={ TextButton(onClick={ delete=null }) { Text("取消") } }) }
        link?.let { url -> AlertDialog(onDismissRequest={ link=null },title={ Text("打开外部链接？") },text={ Text(url.take(300)) },confirmButton={ TextButton(onClick={ val uri=Uri.parse(url); if(uri.scheme in listOf("http","https") && !uri.host.isNullOrBlank()) runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW,uri)) }.onFailure { info="没有可打开链接的应用。" } else info="这段内容是分享口令，可在相应购物 App 中使用。"; link=null }) { Text("打开") } },dismissButton={ TextButton(onClick={ link=null }) { Text("取消") } }) }
        info?.let { text -> AlertDialog(onDismissRequest={ info=null },title={ Text("缓购") },text={ Text(text) },confirmButton={ TextButton(onClick={ info=null }) { Text("知道了") } }) }
    }
}

@Composable private fun Title(title: String, subtitle: String) { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { Text(title,fontSize=24.sp,fontWeight=FontWeight.Medium); Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp) } }
@Composable private fun Section(content: @Composable ColumnScope.() -> Unit) { Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=RoundedCornerShape(16.dp)) { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content) } }
@Composable private fun SettingsRow(title: String, content: @Composable () -> Unit) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) { Text(title,Modifier.weight(1f)); content() } }
@Composable private fun CheckLine(title: String,value: String) { Column { Text(title,fontWeight=FontWeight.Medium,fontSize=13.sp); Text(value,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun AddItemSheet(initial: ImportDraft,defaultHours: Int,dismiss: () -> Unit,save: (String,Long?,String,Int)->Unit) {
    var title by remember(initial.nonce) { mutableStateOf(initial.title) }
    var price by remember(initial.nonce) { mutableStateOf(initial.cents?.let(Money::format).orEmpty()) }
    var link by remember(initial.nonce) { mutableStateOf(initial.link) }
    var hours by remember(initial.nonce) { mutableIntStateOf(defaultHours) }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest=dismiss) {
        LazyColumn(Modifier.fillMaxWidth().imePadding(),contentPadding=PaddingValues(start=22.dp,end=22.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item { Text("先记下来",fontSize=24.sp,fontWeight=FontWeight.Medium) }
            item { OutlinedTextField(title,{ title=it.take(120) },label={ Text("想买什么") },modifier=Modifier.fillMaxWidth().testTag("item_title"),maxLines=3) }
            item { OutlinedTextField(price,{ price=it.take(12) },label={ Text("价格（可不填）") },modifier=Modifier.fillMaxWidth().testTag("item_price"),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true) }
            item { OutlinedTextField(link,{ link=it.take(2000) },label={ Text("链接或分享口令（可不填）") },modifier=Modifier.fillMaxWidth(),maxLines=3) }
            item { Text("先等多久？"); Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(1,24,72).forEach { value -> FilterChip(selected=hours==value,onClick={ hours=value },label={ Text("${value}小时") }) } } }
            if(error.isNotBlank()) item { Text(error,color=MaterialTheme.colorScheme.error) }
            item { Button(onClick={ val cents=if(price.isBlank()) null else Money.parseCents(price); when { title.isBlank() -> error="写下想买的东西吧"; price.isNotBlank() && cents==null -> error="价格需大于 0，最多两位小数"; else -> { saving=true; save(title.trim(),cents,link.trim(),hours); saving=false } } },enabled=!saving,modifier=Modifier.fillMaxWidth().testTag("save_item")) { Text("放入冷静清单") } }
        }
    }
}
private fun date(value: Long)=SimpleDateFormat("MM-dd HH:mm",Locale.getDefault()).format(Date(value))
private fun remaining(until: Long,now: Long): String { val minutes=((until-now).coerceAtLeast(0)+59_999)/60_000; return if(minutes>=60) "${minutes/60}小时${minutes%60}分" else "${minutes}分钟" }
