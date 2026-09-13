package com.spineagent.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 皮肤/设计令牌（可切换的视觉方案）
 *  - 所有界面只读这里的颜色，不再各自硬编码
 *  - 以后要加风格：新增一个 Skin 枚举值 + 一套 Palette 即可
 */
enum class Skin(val label: String) {
    MORANDI("莫兰迪"),
    NIGHT("夜航"),
    PAPER("宣纸")
}

class Palette(
    val bg: Color,            // 页面底
    val surface: Color,       // 卡片/面板
    val surfaceSoft: Color,   // 次级卡片
    val text: Color,          // 主文字
    val textSub: Color,       // 次文字
    val accent: Color,        // 强调色（选中/主按钮）
    val accentSoft: Color,    // 强调浅底
    val border: Color,        // 描边
    val onAccent: Color,      // 强调色上的文字
    val cursor: Color,        // 地图光标
    val darkMap: Boolean,     // 是否用高德夜间底图
    val chipAlphaBg: Long,    // 未选中 chip 的底色（含 alpha）
    val panelAlpha: Long      // 浮层面板底色（含 alpha）
)

object SkinState {
    var current by mutableStateOf(Skin.MORANDI)
        private set

    private const val PREF = "skin"
    private var appContext: Context? = null

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
        val saved = appContext!!.getSharedPreferences("spineagent", Context.MODE_PRIVATE).getString(PREF, null)
        current = Skin.entries.firstOrNull { it.name == saved } ?: Skin.MORANDI
    }

    fun set(skin: Skin) {
        current = skin
        appContext?.getSharedPreferences("spineagent", Context.MODE_PRIVATE)
            ?.edit()?.putString(PREF, skin.name)?.apply()
    }

    fun palette(): Palette = when (current) {
        Skin.MORANDI -> Palette(
            bg = Color(0xFFF2EFE9), surface = Color(0xFFFBF9F5), surfaceSoft = Color(0xFFEDE9E1),
            text = Color(0xFF43403A), textSub = Color(0xFF8C857C), accent = Color(0xFF7C8C7A),
            accentSoft = Color(0xFFE3E8E0), border = Color(0xFFDCD6CB), onAccent = Color(0xFFFFFFFF),
            cursor = Color(0xFF9A5B4F), darkMap = false,
            chipAlphaBg = 0x99FBF9F5, panelAlpha = 0xF5FBF9F5
        )
        Skin.NIGHT -> Palette(
            bg = Color(0xFF0B1220), surface = Color(0xFF131E31), surfaceSoft = Color(0xFF18263C),
            text = Color(0xFFE7EFFA), textSub = Color(0xFF8FA3BF), accent = Color(0xFF35C4F0),
            accentSoft = Color(0xFF16283D), border = Color(0xFF24374F), onAccent = Color(0xFF04212E),
            cursor = Color(0xFFFFB454), darkMap = true,
            chipAlphaBg = 0x99131E31, panelAlpha = 0xF2131E31
        )
        Skin.PAPER -> Palette(
            bg = Color(0xFFF5F1E6), surface = Color(0xFFFBF8F1), surfaceSoft = Color(0xFFEFE9DA),
            text = Color(0xFF2E2A24), textSub = Color(0xFF7A6F5F), accent = Color(0xFF9C3D2E),
            accentSoft = Color(0xFFF0E3D2), border = Color(0xFFDCD3C0), onAccent = Color(0xFFFFF8F0),
            cursor = Color(0xFF9C3D2E), darkMap = false,
            chipAlphaBg = 0x99FBF8F1, panelAlpha = 0xF7FBF8F1
        )
    }
}
