package com.spineagent

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * AgentMiniWindow — 长按侧边栏「agent」打开的可拖动小窗，浮于任意选项卡之上。
 * 用 WebSocket 连接后端 agent（ws_bridge 8787），与若藻聊天共用 AppUiState.messages。
 */
@Composable
fun AgentMiniWindow() {
    val act = LocalContext.current as MainActivity
    var input by remember { mutableStateOf("") }
    val msgs = AppUiState.messages
    val offX = AppUiState.miniX
    val offY = AppUiState.miniY

    Box(Modifier.fillMaxSize()) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 14.dp,
            color = DshColors.bgLayer,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset { IntOffset(offX.roundToInt(), offY.roundToInt()) }
                .size(width = 300.dp, height = 360.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, amt ->
                        change.consume()
                        AppUiState.miniX += amt.x
                        AppUiState.miniY += amt.y
                    }
                }
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("agent · 小窗", color = DshColors.textPrimary, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { AppUiState.miniOpen = false }, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Close, "关闭", tint = DshColors.textTertiary, modifier = Modifier.size(16.dp))
                    }
                }
                HorizontalDivider(color = DshColors.border)
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    if (msgs.isEmpty()) item { Text("连接后即可与 agent 对话", color = DshColors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
                    items(msgs) { m -> MiniBubble(m) }
                }
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("发送给 agent…", color = DshColors.textTertiary) },
                        maxLines = 3, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = DshColors.textPrimary),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DshColors.border, unfocusedBorderColor = DshColors.border,
                            focusedContainerColor = DshColors.bgBase, unfocusedContainerColor = DshColors.bgBase)
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = { val t = input.trim(); if (t.isNotEmpty()) { act.send(t); input = "" } }, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(DshColors.primary)) {
                        Icon(Icons.Default.Send, "发送", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniBubble(m: ChatMessage) {
    val user = m.role == Role.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(12.dp, 12.dp, if (user) 4.dp else 12.dp, if (user) 12.dp else 4.dp),
            color = if (user) DshColors.userBubble else DshColors.bubble,
            modifier = Modifier.widthIn(max = 220.dp)
        ) {
            Text(if (m.text.isEmpty().not()) m.text else "…", modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                color = if (user) Color.White else DshColors.textPrimary, fontSize = 13.sp)
        }
    }
}
