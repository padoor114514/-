package com.spineagent

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * MapWebScreen — 大地巡礼「地图」模块。
 * 用 WebView 加载本地打包的 gods-eye 地图页：优先高德 JS API 2.0 底图，
 * 高德不可用（密钥/安全码缺失、无网络）时自动回退到本地 SVG 世界地图。
 * 打开前注入：
 *  - window.DSH_MAP_API：当前 DSH 后端地址（/api/map），真机经 Tailscale 可取实时数据；
 *  - window.DSH_AMAP_KEY / window.DSH_AMAP_SECURITY_CODE：高德 Web端(JS API) key 与安全密钥。
 */
private const val AMAP_KEY = "71a3072df74d69aa5120a383ec9e1380"  // Web端(JS API) key
/** 高德 JS API 安全密钥：console.amap.com → 应用管理 → 我的应用 → 该 key → 「安全密钥」。
 *  2021-12-02 之后申请的 key 必须填写，否则地图灰屏（INVALID_USER_SCODE）。 */
private const val AMAP_SECURITY_CODE = "0ff2eeadaa9651e977366cdfd62547fb"
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapWebScreen() {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(cm: android.webkit.ConsoleMessage?): Boolean {
                        cm?.let { Log.d("MapWeb", it.message()) }
                        return true
                    }
                }

                // 从 DSH 后端地址推导 /api/map（Tailscale 可达）
                val base = AppUiState.harnessUrl.trim().trimEnd('/')
                val api = base + "/api/map"
                val html = try {
                    ctx.assets.open("gods-eye/index.html").bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    ""
                }
                val injected = if (html.isNotEmpty()) {
                    html.replace(
                        "<head>",
                        "<head><script>window.DSH_MAP_API='" + api +
                            "';window.DSH_AMAP_KEY='" + AMAP_KEY +
                            "';window.DSH_AMAP_SECURITY_CODE='" + AMAP_SECURITY_CODE + "';</script>"
                    )
                } else html
                loadDataWithBaseURL("file:///android_asset/gods-eye/", injected, "text/html", "UTF-8", null)
            }
        },
        onRelease = { it.destroy() }   // 离开地图模块时销毁 WebView，避免 WebGL 上下文累积
    )
}