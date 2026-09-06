package com.spineagent

import android.content.Context
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit

// ═══════════════════════════════════════════════════════════
// Harness 远程控制页：原生 UI 直连电脑上的 DeepSeek Harness
// 协议：DSH host-apiproxy（POST /api/<method>，JSON 信封 + rpcId）
// 连接地址：http://<电脑Tailscale MagicDNS名>:8080（tailscale serve 转发）
// ═══════════════════════════════════════════════════════════

// ─── DSH 设计系统色板（浅色主题）───
object DshColors {
    val bgBase = Color(0xFFF4FAF5)          // 淡绿底
    val bgLayer = Color(0xFFF2FAF4)
    val bgOverlay = Color(0xFFE6F5EC)
    val textPrimary = Color(0xFF1F4A36)
    val textSecondary = Color(0xFF5F9678)
    val textTertiary = Color(0xFF7FAE92)
    val primary = Color(0xFF3E9B6F)         // 主绿
    val bubble = Color(0xFFE4F5EA)
    val border = Color(0x22000000)
    val success = Color(0xFF22C55E)
    val error = Color(0xFFEC1313)
    val userBubble = Color(0xFF2B6B45)
}

data class DshMessage(val role: String, val text: String)  // "user" | "assistant"

// 全局会话状态（切 Tab 不丢；连接地址使用 AppUiState.harnessUrl，在侧边栏设置中配置）
object DshSessionState {
    var connected by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var statusText by mutableStateOf("未连接")
    private val _messages = mutableStateListOf<DshMessage>()
    val messages: List<DshMessage> get() = _messages
    fun clear() { _messages.clear() }
    fun add(m: DshMessage) { _messages.add(m) }
}

// ─── DSH API 客户端 ──────────────────────────────
class DshApi(baseUrl: String) {
    private val base = baseUrl.trim().removeSuffix("/")
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private var rpcSeq = 0

    private fun rpc(method: String, payload: Map<String, Any>): JsonObject {
        val rpcId = "sp-" + System.currentTimeMillis() + "-" + rpcSeq++
        val body = mapOf("type" to "client-request", "rpcId" to rpcId, "method" to method, "payload" to payload)
        val req = Request.Builder()
            .url(base + "/api/" + method)
            .post(RequestBody.create("application/json; charset=utf-8".toMediaType(), gson.toJson(body)))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP " + resp.code)
            val text = resp.body?.string() ?: throw Exception("空响应")
            val root = JsonParser.parseString(text).asJsonObject
            val result = root.getAsJsonObject("result")
            if (!result.get("ok").asBoolean) {
                val err = result.getAsJsonObject("error")
                throw Exception(err.get("message").asString)
            }
            return result.getAsJsonObject("value")
        }
    }

    fun ping() { rpc("host.describe", emptyMap()) }

    fun ensureSession(context: Context): String {
        val prefs = context.getSharedPreferences("dsh", Context.MODE_PRIVATE)
        prefs.getString("sessionId", null)?.let { return it }
        val v = rpc("session.create", emptyMap())
        val sid = v.get("sessionId").asString
        prefs.edit().putString("sessionId", sid).apply()
        return sid
    }

    fun prompt(sessionId: String, text: String) {
        rpc("session.prompt", mapOf(
            "sessionId" to sessionId,
            "mode" to "queue",
            "content" to listOf(mapOf("type" to "text", "text" to text))
        ))
    }

    /** 拉取历史 → (消息列表, 是否已 turn/end)。只保留我方发送的 user 消息（rpcId 前缀 sp-）。 */
    fun history(sessionId: String): Pair<List<DshMessage>, Boolean> {
        // 限制历史消息数，避免直连 DSH 时把大量 assistant/chunk 拉爆内存
        val v = rpc("session.history", mapOf("sessionId" to sessionId, "maxMessages" to 20))
        val arr = v.getAsJsonArray("events")
        val msgs = mutableListOf<DshMessage>()
        var turnEnded = false
        for (el in arr) {
            val ev = el.asJsonObject.getAsJsonObject("event")
            when (ev.get("type").asString) {
                "user/message" -> {
                    val data = ev.getAsJsonObject("data")
                    val src = data.getAsJsonObject("source")
                    val kind = src.get("kind")?.asString ?: ""
                    val rpcId = src.get("rpcId")?.asString ?: ""
                    // 保留我方用户消息：host 回传的 rpcId 前缀（sp-）或 source.kind == user 兜底
                    if (!rpcId.startsWith("sp-") && kind != "user") continue
                    val t = extractText(data)
                    if (t.isNotBlank()) msgs.add(DshMessage("user", t))
                }
                "assistant/message" -> {
                    val t = extractText(ev.getAsJsonObject("data"))
                    if (t.isNotBlank()) msgs.add(DshMessage("assistant", t))
                }
                "turn/end" -> turnEnded = true
            }
        }
        return msgs to turnEnded
    }

    /** 工作区列表 → (标题, 下属会话ID列表) */
    fun workspaceList(): List<Pair<String, List<String>>> {
        val v = rpc("workspace.list", emptyMap())
        val arr = v.getAsJsonArray("items")
        val result = mutableListOf<Pair<String, List<String>>>()
        for (el in arr) {
            val o = el.asJsonObject
            val title = o.get("title")?.asString ?: ""
            val ids = o.getAsJsonArray("sessionIds").map { it.asString }
            result.add(title to ids)
        }
        return result
    }

    /** 会话列表 → sessionId to (标题, 是否运行中) */
    fun sessionList(): Map<String, Pair<String, Boolean>> {
        val v = rpc("session.list", emptyMap())
        val arr = v.getAsJsonArray("items")
        val result = mutableMapOf<String, Pair<String, Boolean>>()
        for (el in arr) {
            val o = el.asJsonObject
            val sid = o.get("sessionId").asString
            val title = try {
                o.getAsJsonObject("projections").getAsJsonObject("values").get("title")?.asString ?: ""
            } catch (_: Exception) { "" }
            val running = o.get("running")?.asBoolean ?: false
            result[sid] = title to running
        }
        return result
    }

    private fun extractText(data: JsonObject): String {
        // 兼容新旧两种结构：正文在 data.content（旧）或 data.message.content（新）
        var content = data.get("content")
        if (content == null || !content.isJsonArray) {
            val msg = data.getAsJsonObject("message")
            if (msg != null) content = msg.get("content")
        }
        if (content == null || content.isJsonNull) return ""
        if (content.isJsonArray) {
            val sb = StringBuilder()
            for (b in content.asJsonArray) {
                val o = b.asJsonObject
                if (o.get("type")?.asString == "text") sb.append(o.get("text")?.asString ?: "")
            }
            return sb.toString()
        }
        return content.asString
    }
}

// ─── 界面 ─────────────────────────────────────────
@Composable
fun HarnessScreen() {
    val context = LocalContext.current
    val connected = DshSessionState.connected
    val busy = DshSessionState.busy
    val statusText = DshSessionState.statusText
    var input by rememberSaveable { mutableStateOf("") }
    var sessionId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun connect() {
        scope.launch {
            DshSessionState.statusText = "连接中…"
            try {
                val api = DshApi(AppUiState.harnessUrl)
                withContext(Dispatchers.IO) { api.ping() }
                val sid = withContext(Dispatchers.IO) { api.ensureSession(context) }
                sessionId = sid
                val (msgs, _) = withContext(Dispatchers.IO) { api.history(sid) }
                DshSessionState.clear()
                msgs.forEach { DshSessionState.add(it) }
                DshSessionState.connected = true
                DshSessionState.statusText = "已连接 · 会话 " + sid.take(8)
            } catch (e: Exception) {
                DshSessionState.connected = false
                DshSessionState.statusText = "连接失败：" + e.message
            }
        }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || !connected || busy || sessionId == null) return
        input = ""
        DshSessionState.add(DshMessage("user", text))
        DshSessionState.busy = true
        DshSessionState.statusText = "任务执行中…"
        scope.launch {
            try {
                val api = DshApi(AppUiState.harnessUrl)
                withContext(Dispatchers.IO) { api.prompt(sessionId!!, text) }
                // 立即同步一次历史（用户消息 + 已有回复）
                val (msgs0, _) = withContext(Dispatchers.IO) { api.history(sessionId!!) }
                DshSessionState.clear()
                msgs0.forEach { DshSessionState.add(it) }
                var lastLen = -1
                var stableRounds = 0
                while (true) {
                    delay(2000)
                    val (msgs, ended) = withContext(Dispatchers.IO) { api.history(sessionId!!) }
                    DshSessionState.clear()
                    msgs.forEach { DshSessionState.add(it) }
                    if (ended) { DshSessionState.statusText = "任务完成"; break }
                    val lastText = msgs.lastOrNull()?.text ?: ""
                    if (lastText.length == lastLen) {
                        stableRounds++
                        if (stableRounds >= 4) { DshSessionState.statusText = "任务完成（暂无明显更新）"; break }
                    } else { stableRounds = 0; lastLen = lastText.length }
                }
            } catch (e: Exception) {
                DshSessionState.statusText = "任务失败：" + e.message
            } finally {
                DshSessionState.busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ── 连接栏（DSH 同款状态行，地址在侧边栏设置中配置）──
        Surface(color = DshColors.bgLayer, shadowElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { sidebarToggle() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Menu, "菜单", tint = DshColors.textPrimary, modifier = Modifier.size(20.dp))
                }
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (connected) DshColors.success else DshColors.error))
                Spacer(Modifier.width(6.dp))
                Text(statusText, color = DshColors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { connect() }, enabled = !busy) {
                    Text(if (connected) "重连" else "连接", color = DshColors.primary, fontSize = 14.sp)
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DshColors.primary)

        // ── 消息区 ──
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (DshSessionState.messages.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "连接后即可向电脑上的 DeepSeek Harness 发布任务\n（WiFi / 移动流量均可用）",
                        color = DshColors.textTertiary, fontSize = 13.sp
                    )
                }
            }
            items(DshSessionState.messages) { m -> DshBubble(m) }
        }

        // ── 输入栏 ──
        Surface(color = DshColors.bgLayer, shadowElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("向 Harness 发布任务…", color = DshColors.textTertiary) },
                    maxLines = 4,
                    shape = RoundedCornerShape(22.dp),
                    textStyle = TextStyle(fontSize = 15.sp, color = DshColors.textPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DshColors.border,
                        unfocusedBorderColor = DshColors.border,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { send() },
                    enabled = input.isNotBlank() && connected && !busy,
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "发送", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun DshBubble(m: DshMessage) {
    val user = m.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, if (user) 4.dp else 16.dp, if (user) 16.dp else 4.dp),
            color = if (user) DshColors.userBubble else DshColors.bubble,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                if (m.text.isEmpty()) "…" else m.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (user) Color.White else DshColors.textPrimary,
                fontSize = 15.sp
            )
        }
    }
}
