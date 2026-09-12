package com.spineagent.plugin.plugins

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import com.spineagent.DatabaseScreen
import com.spineagent.EntryType
import com.spineagent.plugin.MapLayerContribution
import com.spineagent.plugin.MapTapAction
import com.spineagent.plugin.ModuleDescriptor
import com.spineagent.plugin.ScopedContext
import com.spineagent.plugin.SpinePlugin

/**
 * 数据库插件：地方志 / 档案 / 地点登记模块。
 * 它同时是地图的两个扩展点贡献者：
 *  - 地点图层（把登记的坐标画成可点图钉，点击开详情）
 *  - 地图点击动作（点击地图任意位置 → 进入"新建点位"流程）
 */
object DatabasePlugin : SpinePlugin {
    override val id = "db"
    override val title = "数据库"

    override fun install(ctx: ScopedContext) {
        ctx.module(ModuleDescriptor(
            id = id, title = title, icon = Icons.Default.Dns, order = 20,
            screen = { DatabaseScreen() }
        ))

        // 地点图层：红=地点，蓝=地方志，紫=档案（有坐标才画）
        ctx.mapLayer(MapLayerContribution("places", "地点", 0xFFE8543D.toInt(), true, 50) { api ->
            ctx.mapUi.places.forEach { p ->
                if (!p.hasCoords) return@forEach
                val color = when (p.type) {
                    EntryType.PLACE.name -> 0xFFE8543D.toInt()
                    EntryType.GAZETTEER.name -> 0xFF2A7FFF.toInt()
                    else -> 0xFF8A6AE8.toInt()
                }
                api.addMarker("places", p.lat!!, p.lng!!, color, p.title) {
                    ctx.mapUi.selected = p
                }
            }
        })

        // 地图点击动作：任意位置点击 → 预填坐标的新建点位
        ctx.mapTap(MapTapAction(id = "db.addPoint", label = "添加点位", priority = 100) { lat, lng ->
            val p = lat to lng
            if (ctx.mapUi.pendingPoint == p) return@MapTapAction false
            ctx.mapUi.pendingPoint = p
            true
        })
    }
}
