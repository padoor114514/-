package com.spineagent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewKanban
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * BoardScreen — 任务看板（地图同级模块）。
 * 目前为占位：后续可接 DSH session.list / 待办投影做任务卡。
 */
@Composable
fun BoardScreen() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.ViewKanban, null, tint = DshColors.primary, modifier = Modifier.height(48.dp))
        Spacer(Modifier.height(10.dp))
        Text("任务看板", color = DshColors.textPrimary, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("工作区暂无任务 · 从 DSH API 同步后展示", color = DshColors.textTertiary, fontSize = 13.sp)
    }
}
