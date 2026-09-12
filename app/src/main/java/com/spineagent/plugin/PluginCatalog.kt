package com.spineagent.plugin

import com.spineagent.plugin.plugins.AgentPlugin
import com.spineagent.plugin.plugins.BioPlugin
import com.spineagent.plugin.plugins.BoardPlugin
import com.spineagent.plugin.plugins.DatabasePlugin
import com.spineagent.plugin.plugins.MapPlugin
import com.spineagent.plugin.plugins.WorkspacePlugin

/**
 * 组合根：内置插件清单（对应 DSH 的组合包/组合层）。
 * 增删模块＝增删一行，内核不感知任何具体模块。
 */
object PluginCatalog {
    val builtins: List<SpinePlugin> = listOf(
        MapPlugin,
        DatabasePlugin,
        BioPlugin,
        BoardPlugin,
        WorkspacePlugin,
        AgentPlugin
    )

    fun installAll(host: PluginHost) {
        builtins.forEach { host.install(it) }
    }
}
