package com.spineagent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * WorkspaceScreen — 「工作区」选项卡。
 * 会话选择改由侧边栏点击「工作区」向下展开的会话树完成；
 * 本页只显示选中会话的上下文（session.history）与对话。
 * 发送走 DSH session.prompt + 轮询 session.history。
 */
@Composable
fun WorkspaceScreen() {
    val scope = rememberCoroutineScope()
    val selectedSid = AppUiState.wsSessionId
    val msgs = AppUiState.wsMessages
    val status = AppUiState.wsStatus
    val busy = AppUiState.wsBusy
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    var loadProgress by remember { mutableStateOf(0f) }
    var showLoadBar by remember { mutableStateOf(false) }
    var scrollIntent by remember { mutableStateOf<Int?>(null) }

    fun loadSession(sid: String, force: Boolean = false) {
        // 切换前保存当前会话的滑动位置（列表第一项索引）
        val prev = AppUiState.wsSessionId
        if (prev != null && prev != sid) {
            AppUiState.scrollPositions[prev] = listState.firstVisibleItemIndex
        }
        AppUiState.wsSessionId = sid
        val cached = AppUiState.sessionCache[sid]
        if (!force && cached != null) {
            AppUiState.wsBusy = false
            AppUiState.wsStatus = "已缓存 · 会话 " + sid.take(8)
            AppUiState.wsMessages.clear()
            AppUiState.wsMessages.addAll(cached)
            val saved = AppUiState.scrollPositions[sid]
            scrollIntent = if (saved != null) saved else (AppUiState.wsMessages.size - 1).coerceAtLeast(0)
            return
        }
        AppUiState.wsStatus = "同步上下文…"
        AppUiState.wsBusy = true
        AppUiState.wsMessages.clear()
        scope.launch {
            try {
                val api = DshApi(AppUiState.dshUrl)
                val (list, _) = withContext(Dispatchers.IO) { api.history(sid) }
                AppUiState.wsMessages.clear()
                AppUiState.wsMessages.addAll(list)
                AppUiState.sessionCache[sid] = list
                AppUiState.wsStatus = "已连接 · 会话 " + sid.take(8)
                scrollIntent = (AppUiState.wsMessages.size - 1).coerceAtLeast(0)
            } catch (e: Exception) {
                AppUiState.wsStatus = "加载失败：" + e.message
            } finally {
                AppUiState.wsBusy = false
            }
        }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || selectedSid == null || busy) return
        input = ""
        AppUiState.wsBusy = true
        AppUiState.wsStatus = "任务执行中…"
        AppUiState.wsMessages.add(DshMessage("user", text))
        scope.launch {
            try {
                val api = DshApi(AppUiState.dshUrl)
                withContext(Dispatchers.IO) { api.prompt(selectedSid!!, text) }
                var lastLen = -1
                var stable = 0
                while (true) {
                    delay(2000)
                    val (list, ended) = withContext(Dispatchers.IO) { api.history(selectedSid!!) }
                    val atBottom = msgs.isEmpty() || listState.firstVisibleItemIndex >= msgs.size - 2
                    val keep = listState.firstVisibleItemIndex
                    AppUiState.wsMessages.clear()
                    AppUiState.wsMessages.addAll(list)
                    AppUiState.sessionCache[selectedSid!!] = list
                    scrollIntent = if (atBottom) (msgs.size - 1).coerceAtLeast(0) else keep.coerceIn(0, msgs.size - 1)
                    if (ended) { AppUiState.wsStatus = "任务完成"; break }
                    val last = list.lastOrNull()?.text ?: ""
                    if (last.length == lastLen) { stable++; if (stable >= 4) { AppUiState.wsStatus = "任务完成（暂无明显更新）"; break } }
                    else { stable = 0; lastLen = last.length }
                }
            } catch (e: Exception) {
                AppUiState.wsStatus = "任务失败：" + e.message
            } finally {
                AppUiState.wsBusy = false
            }
        }
    }

    LaunchedEffect(Unit) { loadWorkspacesFromDsh() }
    LaunchedEffect(selectedSid) { if (selectedSid != null) loadSession(selectedSid) }
    // 应用滚动意图：新载入→置底，切换会话→恢复上次位置
    LaunchedEffect(scrollIntent) {
        val target = scrollIntent ?: return@LaunchedEffect
        if (msgs.isNotEmpty()) {
            listState.scrollToItem(target.coerceIn(0, msgs.size - 1))
        }
        scrollIntent = null
    }

    // 上下文加载/任务执行进度：0→100，完成后消失
    LaunchedEffect(busy) {
        if (busy) {
            showLoadBar = true
            loadProgress = 0f
            var local = 0f
            while (true) {
                delay(100)
                local = (local + 0.015f).coerceAtMost(0.85f)
                loadProgress = local
            }
        } else if (showLoadBar) {
            loadProgress = 1f
            delay(180)
            showLoadBar = false
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFEFF9F2))) {
        // 顶部状态栏
        Surface(color = DshColors.bgLayer, shadowElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (selectedSid != null) Color(0xFF22C55E) else Color(0xFFEC1313)))
                Spacer(Modifier.width(8.dp))
                Text(status, color = DshColors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { selectedSid?.let { loadSession(it, force = true) } }, enabled = selectedSid != null) {
                    Text("刷新", color = DshColors.primary, fontSize = 13.sp)
                }
            }
        }
        if (showLoadBar) {
            LinearProgressIndicator(
                progress = { loadProgress },
                modifier = Modifier.fillMaxWidth(),
                color = DshColors.primary,
                trackColor = Color(0x1F3E9B6F)
            )
        }
        if (selectedSid == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("请从左侧侧边栏点击「工作区」展开，\n再选择一个会话查看上下文与对话",
                    color = DshColors.textTertiary, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                if (msgs.isEmpty()) item { Text("该会话暂无消息", color = DshColors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(12.dp)) }
                items(msgs) { m -> DshBubble(m) }
            }
        }
        // 输入栏
        Surface(color = DshColors.bgLayer, shadowElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("向该会话发布任务…", color = DshColors.textTertiary) }, maxLines = 4,
                    shape = RoundedCornerShape(22.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = DshColors.textPrimary),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DshColors.border, unfocusedBorderColor = DshColors.border,
                        focusedContainerColor = Color.White, unfocusedContainerColor = Color.White))
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = { send() }, enabled = input.isNotBlank() && selectedSid != null && !busy, modifier = Modifier.size(46.dp)) {
                    Icon(Icons.Default.Send, "发送", tint = Color.White)
                }
            }
        }
    }
}