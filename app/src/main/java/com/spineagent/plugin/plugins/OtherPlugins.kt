package com.spineagent.plugin.plugins

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ViewKanban
import com.spineagent.AppUiState
import com.spineagent.BoardScreen
import com.spineagent.ChatScreen
import com.spineagent.WorkspaceScreen
import com.spineagent.plugin.ModuleDescriptor
import com.spineagent.plugin.ScopedContext
import com.spineagent.plugin.SpinePlugin

/** 任务看板插件 */
object BoardPlugin : SpinePlugin {
    override val id = "board"
    override val title = "任务看板"
    override fun install(ctx: ScopedContext) {
        ctx.module(ModuleDescriptor(id, title, Icons.Default.ViewKanban, order = 30, screen = { BoardScreen() }))
    }
}

/** 工作区插件（点击即展开工作区树） */
object WorkspacePlugin : SpinePlugin {
    override val id = "workspace"
    override val title = "工作区"
    override fun install(ctx: ScopedContext) {
        ctx.module(ModuleDescriptor(
            id, title, Icons.Default.Folder, order = 40,
            screen = { WorkspaceScreen() },
            onSelect = { AppUiState.workspaceExpanded = true }
        ))
    }
}

/** 若藻对话插件（长按开小窗） */
object AgentPlugin : SpinePlugin {
    override val id = "agent"
    override val title = "agent"
    override fun install(ctx: ScopedContext) {
        ctx.module(ModuleDescriptor(
            id, title, Icons.Default.Android, order = 50,
            screen = { ChatScreen() },
            onLongPress = { AppUiState.miniOpen = true },
            hint = "长按开小窗"
        ))
    }
}
