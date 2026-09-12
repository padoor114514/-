package com.spineagent

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewKanban
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ═══════════════════════════════════════════════════════════
// Sidebar.kt — 侧边栏（竖屏抽屉 / 横屏常驻 25%）
// 主视图：工作区 → 下属会话（从 DSH API 同步真实数据）
// 设置视图：若藻连接方式（直连 / 私有局域网）+ Harness 地址
// ═══════════════════════════════════════════════════════════

data class DshWsSession(val id: String, val title: String, val running: Boolean)
data class DshWorkspace(val id: String, val title: String, val sessions: List<DshWsSession>)

// ── 从 DSH API 加载工作区/会话 ──
suspend fun loadWorkspacesFromDsh(force: Boolean = false) {
    // 工作区/会话列表缓存：进程存活期间不重复拉取，除非强制刷新
    if (!force && AppUiState.workspaces.isNotEmpty()) {
        AppUiState.wsLoading = false
        AppUiState.wsError = null
        return
    }
    AppUiState.wsLoading = true
    AppUiState.wsError = null
    try {
        val api = DshApi(AppUiState.dshUrl)
        val (wsList, sessMap) = withContext(Dispatchers.IO) {
            val ws = api.workspaceList()
            val sessions = api.sessionList()
            ws to sessions
        }
        AppUiState.workspaces.clear()
        for (w in wsList) {
            val sessions = w.second.mapNotNull { sid ->
                sessMap[sid]?.let { DshWsSession(sid, it.first, it.second) }
            }
            AppUiState.workspaces.add(DshWorkspace(w.first, w.first, sessions))
        }
    } catch (e: Exception) {
        AppUiState.wsError = e.message
    }
    AppUiState.wsLoading = false
}

// ── 侧边栏覆盖层（修复：显式左对齐 + zIndex 分层，避免占满屏幕）──
@Composable
fun SidebarOverlay(isLandscape: Boolean) {
    val open = AppUiState.sidebarOpen
    val collapsed = AppUiState.sidebarCollapsed
    val visible = if (isLandscape) !collapsed else open

    Box(Modifier.fillMaxSize()) {
        // 遮罩层（仅竖屏抽屉展开时，盖住内容但低于侧边栏）
        if (!isLandscape && open) {
            Box(
                Modifier.fillMaxSize().zIndex(1f)
                    .background(Color(0x5C1F4A36))
                    .clickable { AppUiState.sidebarOpen = false }
            )
        }
        // 侧边栏本体：左对齐，竖屏固定 276dp / 横屏 25%
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(tween(260)) { -it },
            exit = slideOutHorizontally(tween(260)) { -it },
            modifier = Modifier
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .zIndex(2f)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .then(if (isLandscape) Modifier.fillMaxWidth(0.25f) else Modifier.width(276.dp))
                    .background(Color(0xFFEFF9F2))
                    .drawBehind {
                        drawLine(
                            color = Color(0x1A000000),
                            start = Offset(size.width - 0.5f, 0f),
                            end = Offset(size.width - 0.5f, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
            ) {
                SidebarContent()
            }
        }
    }
}

@Composable
fun SidebarContent() {
    val view = AppUiState.sbView
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 20.dp, end = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (view == "settings") {
                IconButton(onClick = { AppUiState.sbView = "main" }) {
                    Icon(Icons.Default.ArrowBack, "返回", tint = Color(0xFF1F4A36))
                }
            } else {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF3E9B6F)), contentAlignment = Alignment.Center) {
                    Text("◈", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(if (view == "settings") "连接设置" else "大地巡礼",
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F4A36),
                modifier = Modifier.weight(1f))
            if (view == "main") {
                IconButton(onClick = { AppUiState.wsReloadTick++ }) {
                    Icon(Icons.Default.Refresh, "刷新", tint = Color(0xFF7FAE92), modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = { sidebarClose() }) {
                Icon(Icons.Default.Close, "关闭", tint = Color(0xFF7FAE92), modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(color = Color(0x1A000000))
        if (view == "main") {
            WorkspaceList()
        } else {
            SettingsView()
        }
    }
}

fun sidebarClose() {
    if (AppUiState.isLandscape) AppUiState.sidebarCollapsed = true
    else AppUiState.sidebarOpen = false
}

fun sidebarToggle() {
    if (AppUiState.isLandscape) AppUiState.sidebarCollapsed = false
    else AppUiState.sidebarOpen = !AppUiState.sidebarOpen
}

// ── 模块导航：完全由插件注册表驱动（ctx.modules）──
@Composable
fun ModuleNav() {
    com.spineagent.plugin.Spine.ctx.modules.all().forEach { m ->
        NavModuleItem(m)
    }
}

@Composable
fun AgentNavItem() {
    val selected = AppUiState.module == "agent"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFFE4F5EA) else Color.Transparent)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { AppUiState.miniOpen = true },
                    onTap = { AppUiState.module = "agent" }
                )
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Android, null, tint = if (selected) Color(0xFF3E9B6F) else Color(0xFF7FAE92), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("agent", color = if (selected) Color(0xFF3E9B6F) else Color(0xFF1F4A36), fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, modifier = Modifier.weight(1f))
        Text("长按开小窗", fontSize = 10.sp, color = Color(0xFF7FAE92))
    }
}

@Composable
fun WorkspaceExpandItem() {
    val expanded = AppUiState.workspaceExpanded
    val active = expanded || AppUiState.module == "workspace"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(8.dp))
            .background(if (active) Color(0xFFE4F5EA) else Color.Transparent)
            .clickable {
                AppUiState.module = "workspace"
                AppUiState.workspaceExpanded = true
                AppUiState.sidebarOpen = false
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Folder, null, tint = if (active) Color(0xFF3E9B6F) else Color(0xFF7FAE92), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("工作区", color = if (active) Color(0xFF3E9B6F) else Color(0xFF1F4A36), fontSize = 14.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(if (expanded) "▴" else "▾", fontSize = 12.sp, color = Color(0xFF7FAE92))
    }
}

/** 插件模块导航项：点击选中该插件的模块，长按触发插件声明的长按动作 */
@Composable
fun NavModuleItem(m: com.spineagent.plugin.ModuleDescriptor) {
    val selected = AppUiState.module == m.id
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFFE4F5EA) else Color.Transparent)
            .pointerInput(m.id) {
                detectTapGestures(
                    onLongPress = { m.onLongPress?.invoke() },
                    onTap = {
                        AppUiState.module = m.id
                        m.onSelect?.invoke()
                        AppUiState.sidebarOpen = false
                    }
                )
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(m.icon, null, tint = if (selected) Color(0xFF3E9B6F) else Color(0xFF7FAE92), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            m.title,
            color = if (selected) Color(0xFF3E9B6F) else Color(0xFF1F4A36),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        m.hint?.let { Text(it, fontSize = 10.sp, color = Color(0xFF7FAE92)) }
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF3E9B6F)))
        }
    }
}

// ── 主视图：远程工作区 → 会话 ──
@Composable
fun WorkspaceList() {
    val workspaces = AppUiState.workspaces
    val loading = AppUiState.wsLoading
    val error = AppUiState.wsError
    val reloadTick = AppUiState.wsReloadTick
    val openMap = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(reloadTick) { loadWorkspacesFromDsh(force = true) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
            ModuleNav()
            if (AppUiState.workspaceExpanded) {
            HorizontalDivider(color = Color(0x1A000000), modifier = Modifier.padding(vertical = 4.dp))
            when {
                loading && workspaces.isEmpty() ->
                    Text("加载中…", fontSize = 13.sp, color = Color(0xFF7FAE92), modifier = Modifier.padding(16.dp))
                error != null && workspaces.isEmpty() ->
                    Text("⚠ 无法连接 DSH：" + error, fontSize = 12.sp, color = Color(0xFFEC1313),
                        modifier = Modifier.padding(16.dp))
                else -> {
                    for (ws in workspaces) {
                        val open = openMap[ws.id] ?: true
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(8.dp))
                            .clickable { openMap[ws.id] = !open }
                            .padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (open) "▾" else "▸", fontSize = 10.sp, color = Color(0xFF7FAE92))
                            Spacer(Modifier.width(7.dp))
                            Text("📁", fontSize = 13.sp)
                            Spacer(Modifier.width(7.dp))
                            Text(ws.title, fontSize = 13.5.sp, fontWeight = FontWeight.Medium,
                                color = Color(0xFF1F4A36), modifier = Modifier.weight(1f))
                            Text("" + ws.sessions.size, fontSize = 11.sp, color = Color(0xFF3E9B6F),
                                modifier = Modifier.clip(RoundedCornerShape(9.dp))
                                    .background(Color(0xFFBFE6CF)).padding(horizontal = 7.dp, vertical = 1.dp))
                        }
                        if (open) {
                            Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 6.dp)) {
                                for (s in ws.sessions) {
                                    val sel = s.id == AppUiState.wsSessionId
                                    Row(Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) Color(0xFFBFE6CF) else Color.Transparent)
                                        .clickable { AppUiState.module = "workspace"; AppUiState.wsSessionId = s.id }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        if (sel) Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF3E9B6F)))
                                        else Spacer(Modifier.size(6.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (s.title.isEmpty()) s.id.take(8) else s.title, fontSize = 13.5.sp,
                                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (sel) Color(0xFF3E9B6F) else Color(0xFF1F4A36), modifier = Modifier.weight(1f))
                                        if (s.running) {
                                            Text("● 运行中", fontSize = 11.sp, color = Color(0xFF22C55E))
                                        }
                                    }
                                }
                                if (ws.sessions.isEmpty()) {
                                    Text("（无会话）", fontSize = 12.sp, color = Color(0xFF7FAE92),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                }
                            }
                        }
                    }
                }
            }
            }
        }
        Surface(color = Color(0xFFE6F5EC)) {
            TextButton(onClick = { AppUiState.sbView = "settings" }, modifier = Modifier.fillMaxWidth()) {
                Text("⚙ 连接设置", color = Color(0xFF5F9678), fontSize = 13.5.sp)
            }
        }
    }
}
// ── 设置视图 ──
@Composable
fun SettingsView() {
    val context = LocalContext.current
    var wsMode by remember { mutableStateOf(AppUiState.wsMode) }
    var usbUrl by remember { mutableStateOf(AppUiState.usbUrl) }
    var wifiUrl by remember { mutableStateOf(AppUiState.wifiUrl) }
    var lanUrl by remember { mutableStateOf(AppUiState.lanUrl) }
    var harnessUrl by remember { mutableStateOf(AppUiState.harnessUrl) }

    Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Section("若藻聊天 · 连接方式")
        RadioCard(title = "直连", sub = "USB / 局域网 WiFi 直连（ws://）",
            selected = wsMode == "direct", onClick = { wsMode = "direct" }) {
            if (wsMode == "direct") {
                Field("USB 地址", usbUrl) { usbUrl = it }
                Field("WiFi 地址", wifiUrl) { wifiUrl = it }
            }
        }
        RadioCard(title = "私有局域网（Tailscale）", sub = "DSH 同款隧道，WiFi / 移动流量通用",
            selected = wsMode == "lan", onClick = { wsMode = "lan" }) {
            if (wsMode == "lan") {
                Field("服务器地址", lanUrl) { lanUrl = it }
            }
        }
        Section("Harness 远程控制")
        Field("服务器地址（HTTP API）", harnessUrl) { harnessUrl = it }

        Box(Modifier.fillMaxWidth().padding(14.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF3E9B6F),
                modifier = Modifier.fillMaxWidth().height(44.dp).clickable {
                    AppUiState.wsMode = wsMode
                    AppUiState.usbUrl = usbUrl.trim()
                    AppUiState.wifiUrl = wifiUrl.trim()
                    AppUiState.lanUrl = lanUrl.trim()
                    AppUiState.harnessUrl = harnessUrl.trim()
                    context.getSharedPreferences("dsh", Context.MODE_PRIVATE).edit()
                        .putString("wsMode", wsMode)
                        .putString("usbUrl", AppUiState.usbUrl)
                        .putString("wifiUrl", AppUiState.wifiUrl)
                        .putString("lanUrl", AppUiState.lanUrl)
                        .putString("harnessUrl", AppUiState.harnessUrl)
                        .apply()
                    AppUiState.sbView = "main"
                    AppUiState.wsStatusText = "设置已保存"
                }) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("保存设置", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    }
}

@Composable
fun Section(title: String) {
    Text(title, fontSize = 11.sp, color = Color(0xFF7FAE92),
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp))
}

@Composable
fun RadioCard(title: String, sub: String, selected: Boolean, onClick: () -> Unit, fields: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(if (selected) Color(0xFFE4F5EA) else Color.Transparent)
        .clickable { onClick() }
        .padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(16.dp).clip(CircleShape)
                .background(if (selected) Color(0xFF3E9B6F) else Color(0x1A000000)),
                contentAlignment = Alignment.Center) {
                if (selected) Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1F4A36))
                Text(sub, fontSize = 11.5.sp, color = Color(0xFF7FAE92))
            }
        }
        if (selected) fields()
    }
}

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, fontSize = 12.sp, color = Color(0xFF5F9678))
        OutlinedTextField(
            value = value, onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF1F4A36)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF3E9B6F),
                unfocusedBorderColor = Color(0x1A000000),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )
    }
}