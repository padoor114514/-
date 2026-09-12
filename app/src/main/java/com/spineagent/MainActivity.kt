package com.spineagent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.spineagent.plugin.Spine
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit

// ─── WebSocket 协议消息（匹配 ws_bridge_server.py）───
data class WsUserInput(@SerializedName("type") val type: String = "user_input",
                       @SerializedName("text") val text: String)
data class WsStateChange(@SerializedName("type") val type: String = "state_change",
                          @SerializedName("state") val state: String)
data class WsResponseStart(@SerializedName("type") val type: String = "response_start",
                            @SerializedName("message_id") val messageId: String)
data class WsResponseChunk(@SerializedName("type") val type: String = "response_chunk",
                            @SerializedName("message_id") val messageId: String,
                            @SerializedName("text") val text: String)
data class WsResponseEnd(@SerializedName("type") val type: String = "response_end",
                          @SerializedName("message_id") val messageId: String)
data class WsError(@SerializedName("type") val type: String = "error",
                    @SerializedName("text") val text: String)

// ─── UI 消息 ────────────────────────────────────
data class ChatMessage(val role: Role, val text: String)
enum class Role { USER, AGENT }

// ─── 全局 UI 状态 ──────────────────────────────
object AppUiState {
    var module by mutableStateOf("map")     // map | board | workspace | agent
    var currentInput by mutableStateOf("")
    var isLandscape by mutableStateOf(false)

    // ── 连接配置（侧边栏设置修改，SharedPreferences 持久化）──
    var wsMode by mutableStateOf("direct")  // "direct" 直连 | "lan" 私有局域网(Tailscale)
    var usbUrl by mutableStateOf("ws://127.0.0.1:8787")
    var wifiUrl by mutableStateOf("ws://192.168.0.100:8787")
    var lanUrl by mutableStateOf("ws://laptop-2j360fhf.tail108315.ts.net:8787")
    var harnessUrl by mutableStateOf("http://laptop-2j360fhf.tail108315.ts.net:8080")
    var dshUrl by mutableStateOf("http://laptop-2j360fhf.tail108315.ts.net:3080")

    // ── 侧边栏 ──
    var sidebarOpen by mutableStateOf(false)
    var sidebarCollapsed by mutableStateOf(false)  // 横屏关闭态
    var sbView by mutableStateOf("main")           // "main" | "settings"

    // ── 若藻连接状态 ──
    var activeMode by mutableStateOf("--")
    var isLoading by mutableStateOf(false)
    var isConnected by mutableStateOf(false)
    var wsStatusText by mutableStateOf("未连接")

    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> get() = _messages
    fun addMessage(msg: ChatMessage) { _messages.add(msg) }
    fun updateLast(text: String) {
        if (_messages.isNotEmpty() && _messages.last().role == Role.AGENT)
            _messages[_messages.lastIndex] = _messages.last().copy(text = text)
    }
    val urls: List<Pair<String, String>> get() =
        if (wsMode == "lan") listOf("私有局域网" to lanUrl)
        else listOf("USB" to usbUrl, "WiFi" to wifiUrl)

    // ── 工作区（DSH API 同步）──
    val workspaces = mutableStateListOf<DshWorkspace>()
    var wsLoading by mutableStateOf(false)
    var wsError by mutableStateOf<String?>(null)
    var wsReloadTick by mutableStateOf(0)

    // ── 工作区会话状态（跨 Tab 保留）──
    var wsSessionId by mutableStateOf<String?>(null)
    var wsStatus by mutableStateOf("未连接")
    var wsBusy by mutableStateOf(false)
    var workspaceExpanded by mutableStateOf(false)
    val wsMessages = mutableStateListOf<DshMessage>()
    // 上下文缓存：会话 id → 已加载的 DshMessage 列表；进程存活期间不再重复拉取
    val sessionCache = mutableStateMapOf<String, List<DshMessage>>()
    // 每个会话最后滑动到的位置（列表第一项索引），用于往返切换时恢复
    val scrollPositions = mutableStateMapOf<String, Int>()

    // ── agent 小窗（可拖动浮层）──
    var miniOpen by mutableStateOf(false)
    var miniX by mutableStateOf(0f)
    var miniY by mutableStateOf(0f)
}

class MainActivity : ComponentActivity() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ws: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentAiMsg = ""

    private val audioPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> if (ok) startVoice() else toast("需要录音权限") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启动插件宿主：安装组合根里的全部内置插件（仿 DSH 组合包）
        Spine.start(applicationContext)
        loadSettings()
        initTTS()
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF3E9B6F),
                    onPrimary = Color(0xFFFFFFFF),
                    secondary = Color(0xFF6BCBA0),
                    background = Color(0xFFF4FAF5),
                    surface = Color(0xFFF2FAF4),
                    onBackground = Color(0xFF1F4A36),
                    onSurface = Color(0xFF1F4A36),
                    surfaceVariant = Color(0xFFE6F5EC),
                    onSurfaceVariant = Color(0xFF5F9678),
                    outline = Color(0x22000000),
                    error = Color(0xFFEC1313)
                )
            ) { SpineAgentApp() }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 实机方向检测：configChanges 下 Activity 不重建，这里手动同步
        AppUiState.isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("dsh", Context.MODE_PRIVATE)
        AppUiState.wsMode = prefs.getString("wsMode", "direct") ?: "direct"
        AppUiState.usbUrl = prefs.getString("usbUrl", AppUiState.usbUrl) ?: AppUiState.usbUrl
        AppUiState.wifiUrl = prefs.getString("wifiUrl", AppUiState.wifiUrl) ?: AppUiState.wifiUrl
        AppUiState.lanUrl = prefs.getString("lanUrl", AppUiState.lanUrl) ?: AppUiState.lanUrl
        AppUiState.harnessUrl = prefs.getString("harnessUrl", AppUiState.harnessUrl) ?: AppUiState.harnessUrl
    }

    private fun initTTS() {
        tts = TextToSpeech(this) { s ->
            if (s == TextToSpeech.SUCCESS) { tts?.setLanguage(java.util.Locale.CHINESE); tts?.setSpeechRate(1.0f); ttsReady = true }
        }
    }

    override fun onDestroy() {
        ws?.close(1000, null); speechRecognizer?.destroy(); tts?.stop(); tts?.shutdown(); scope.cancel()
        super.onDestroy()
    }

    // ── 语音输入（长按说话，松手停止）─────────────
    fun pressVoice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            audioPerm.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        startVoice()
    }

    fun releaseVoice() {
        // 松手：停止监听（onResults 会把识别文本填入输入框）
        speechRecognizer?.stopListening()
    }

    private fun startVoice() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also { sr ->
            sr.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onResults(r: Bundle?) {
                    val txt = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (txt != null) {
                        AppUiState.currentInput = txt
                        toast("✅ 已识别")
                    } else {
                        toast("未识别到语音")
                    }
                }
                override fun onError(err: Int) {
                    toast(when (err) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "未识别到语音"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未听到声音"
                        else -> "语音错误($err)"
                    })
                }
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
        }
        speechRecognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        })
    }

    // ── WebSocket（按设置里的连接方式）────────────
    private var connectAttempt = 0
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    fun connect() {
        if (AppUiState.isConnected) { disconnect(); return }
        connectAttempt = 0
        tryNext()
    }

    fun disconnect() {
        ws?.close(1000, null)
        ws = null
        AppUiState.isConnected = false
        AppUiState.wsStatusText = "未连接"
    }

    private fun tryNext() {
        val urls = AppUiState.urls
        if (connectAttempt >= urls.size) {
            runOnUiThread {
                AppUiState.isConnected = false
                AppUiState.activeMode = "--"
                AppUiState.wsStatusText = "所有地址都无法连接"
                toast("所有地址都无法连接")
            }
            return
        }
        val (mode, url) = urls[connectAttempt]
        runOnUiThread { AppUiState.activeMode = mode }
        ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(w: WebSocket, r: Response) {
                runOnUiThread {
                    AppUiState.isConnected = true
                    AppUiState.activeMode = mode
                    AppUiState.wsStatusText = "已连接 · " + mode + " · " + url
                    toast("已连接 ($mode)")
                }
            }
            override fun onMessage(w: WebSocket, text: String) {
                runOnUiThread {
                    try {
                        val root = com.google.gson.JsonParser.parseString(text).asJsonObject
                        when (root.get("type")?.asString) {
                            "response_start" -> {
                                currentAiMsg = ""
                                AppUiState.addMessage(ChatMessage(Role.AGENT, ""))
                            }
                            "response_chunk" -> {
                                currentAiMsg += root.get("text")?.asString.orEmpty()
                                AppUiState.updateLast(currentAiMsg)
                            }
                            "response_end" -> {
                                AppUiState.isLoading = false
                                speak(currentAiMsg)
                            }
                            "error" -> {
                                AppUiState.isLoading = false
                                AppUiState.addMessage(ChatMessage(Role.AGENT, "错误: " + (root.get("text")?.asString ?: "")))
                            }
                            "pong" -> { /* heartbeat */ }
                        }
                    } catch (_: Exception) {}
                }
            }
            override fun onFailure(w: WebSocket, t: Throwable, r: Response?) {
                runOnUiThread {
                    if (!AppUiState.isConnected) {
                        connectAttempt++
                        AppUiState.wsStatusText = "连接失败，切换下一个…"
                        handler.postDelayed({ tryNext() }, 500)
                    }
                }
            }
            override fun onClosed(w: WebSocket, code: Int, reason: String) {
                runOnUiThread {
                    AppUiState.isConnected = false
                    AppUiState.wsStatusText = "连接已断开"
                    val other = (connectAttempt + 1) % urls.size
                    connectAttempt = other
                    handler.postDelayed({ tryNext() }, 1000)
                }
            }
        })
    }

    fun send(text: String) {
        if (!AppUiState.isConnected) { connect(); return }
        AppUiState.addMessage(ChatMessage(Role.USER, text))
        AppUiState.isLoading = true
        ws?.send(gson.toJson(WsUserInput(text = text)))
    }

    // ── TTS ─────────────────────────────────────
    fun speak(text: String) {
        if (!ttsReady) return
        val clean = text.replace(Regex("[*_~`#>]"), " ").replace(Regex("\\[.*?]\\(.*?\\)"), "").trim()
        if (clean.isNotBlank()) tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "tts")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

// ═══════════════════════════════════════════════════════════
// Compose UI
// ═══════════════════════════════════════════════════════════

@Composable
fun SpineAgentApp() {
    val config = LocalConfiguration.current
    AppUiState.isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 锁定横屏：竖屏时全屏提示
        if (!AppUiState.isLandscape) {
            Box(Modifier.fillMaxSize().background(Color(0xFF0F1115)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ScreenRotation, null, tint = Color(0xFF5686FE), modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(14.dp))
                    Text("请横屏使用", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("大地巡礼已锁定为横屏模式，请旋转设备", color = Color(0xFFADB2B8), fontSize = 13.sp)
                }
            }
            return@BoxWithConstraints
        }
        // 横屏常驻侧边栏（25%），内容区左移
        val sbPad = if (!AppUiState.sidebarCollapsed) maxWidth * 0.25f else 0.dp
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(start = sbPad)) {
                // 模块渲染由插件注册表决定；切换模块＝切换插件
                val modules = Spine.ctx.modules
                val screen = (modules.byId(AppUiState.module) ?: modules.default)?.screen
                if (screen != null) screen() else Text(
                    "没有可用模块", color = Color(0xFF5F9678), fontSize = 14.sp
                )
            }
            SidebarOverlay(true)
            if (AppUiState.miniOpen) AgentMiniWindow()
        }
    }
}

// ── 导航由侧边栏驱动：地图 / 工作区(若藻, Harness) ──

// ── 若藻页 ──
@Composable
fun ChatScreen() {
    var input by AppUiState::currentInput
    val loading = AppUiState.isLoading
    val connected = AppUiState.isConnected
    val msgs = AppUiState.messages
    val statusText = AppUiState.wsStatusText
    val list = rememberLazyListState()
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size - 1) }
    val act = LocalContext.current as MainActivity
    var recording by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // 连接栏（DSH 同款状态行，配置在侧边栏设置里）
        Surface(Modifier.fillMaxWidth(), color = Color(0xFFF9FAFB), shadowElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { sidebarToggle() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Menu, "菜单", tint = Color(0xFF0F1115), modifier = Modifier.size(20.dp))
                }
                Box(Modifier.size(8.dp).clip(CircleShape)
                    .background(if (connected) Color(0xFF22C55E) else Color(0xFFEC1313)))
                Spacer(Modifier.width(6.dp))
                Text(statusText, fontSize = 12.sp, color = Color(0xFF61666B), modifier = Modifier.weight(1f))
                TextButton(onClick = { act.connect() }) {
                    Text(if (connected) "断开" else "连接", color = Color(0xFF4176E6), fontSize = 14.sp)
                }
            }
        }
        // 消息
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            state = list, verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)) {
            if (msgs.isEmpty()) item { Text("连接后即可与若藻对话（连接方式在侧边栏设置中配置）",
                color = Color(0xFF81858C), fontSize = 13.sp, modifier = Modifier.padding(24.dp)) }
            items(msgs) { m -> Bubble(m, onSpeak = { act.speak(m.text) }) }
            if (loading) item {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { Box(Modifier.size(8.dp).clip(CircleShape).background(
                        Color(0xFF4176E6).copy(alpha = 0.3f + it * 0.3f))) }
                }
            }
        }
        // 输入栏（喇叭 = 长按说话）
        Surface(Modifier.fillMaxWidth(), color = Color(0xFFF9FAFB), shadowElevation = 8.dp) {
            Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (recording) Color(0xFFEC1313) else Color.White)
                        .border(1.dp, Color(0x1A000000), CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    act.pressVoice()
                                    recording = true
                                    try { awaitRelease() } finally { recording = false; act.releaseVoice() }
                                }
                            )
                        }
                ) {
                    Icon(Icons.Default.VolumeUp, "语音",
                        tint = if (recording) Color.White else Color(0xFFFF5252),
                        modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(value = input, onValueChange = { input = it },
                    modifier = Modifier.weight(1f), placeholder = { Text("输入或长按喇叭说话...") },
                    maxLines = 3, shape = RoundedCornerShape(22.dp), textStyle = TextStyle(fontSize = 15.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0x1A000000),
                        unfocusedBorderColor = Color(0x1A000000),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White))
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { if (input.isNotBlank()) { act.send(input.trim()); input = "" } },
                    enabled = input.isNotBlank() && !loading && connected,
                    modifier = Modifier.size(46.dp).clip(CircleShape)
                        .background(Color(0xFF4176E6))) {
                    Icon(Icons.Default.Send, "发送", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
fun Bubble(msg: ChatMessage, onSpeak: () -> Unit) {
    val user = msg.role == Role.USER
    Column(horizontalAlignment = if (user) Alignment.End else Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!user) IconButton(onClick = onSpeak, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.VolumeUp, "朗读", tint = Color(0xFF4176E6), modifier = Modifier.size(18.dp)) }
            Surface(shape = RoundedCornerShape(16.dp, 16.dp, if (user) 4.dp else 16.dp, if (user) 16.dp else 4.dp),
                color = if (user) Color(0xFF0F1115) else Color(0xFFEDF3FE),
                modifier = Modifier.widthIn(max = 280.dp)) {
                Text(if (msg.text.isEmpty() && !user) "..." else msg.text, modifier = Modifier.padding(12.dp),
                    color = if (user) Color.White else Color(0xFF0F1115))
            }
        }
    }
}