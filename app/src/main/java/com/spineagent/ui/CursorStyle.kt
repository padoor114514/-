package com.spineagent.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 地图待确认光标样式（可切换，持久化） */
enum class CursorStyle(val label: String) {
    CROSSHAIR("准星"),
    PIN("水滴"),
    RADAR("雷达"),
    FINDER("取景"),
    SEAL("印章");

    companion object {
        fun from(s: String?): CursorStyle = entries.firstOrNull { it.name == s } ?: CROSSHAIR
    }
}

object CursorState {
    var current by mutableStateOf(CursorStyle.CROSSHAIR)
        private set

    private const val PREF = "cursorStyle"
    private var appContext: Context? = null

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
        current = CursorStyle.from(
            appContext!!.getSharedPreferences("spineagent", Context.MODE_PRIVATE).getString(PREF, null)
        )
    }

    fun set(style: CursorStyle) {
        current = style
        appContext?.getSharedPreferences("spineagent", Context.MODE_PRIVATE)
            ?.edit()?.putString(PREF, style.name)?.apply()
    }
}
