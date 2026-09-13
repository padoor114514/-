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

        // 地图点击动作（两段式）：
        //   第一次点击 → 落光标（仅指示位置，可再点别处移动）
        //   再次点击同一点 → 确认，弹出命名对话框入库
        ctx.mapTap(MapTapAction(id = "db.addPoint", label = "添加点位", priority = 100) { lat, lng, sx, sy ->
            val cursor = ctx.mapUi.cursorPoint
            val cs = ctx.mapUi.cursorScreen
            if (cursor == null || cs == null) {
                ctx.mapUi.cursorPoint = lat to lng
                ctx.mapUi.cursorScreen = sx to sy
                ctx.mapUi.status = "光标已就位 · 再次点击该处确认添加"
                true
            } else {
                val dx = cs.first - sx
                val dy = cs.second - sy
                val near = kotlin.math.sqrt(dx * dx + dy * dy) < 28f     // 同一处的像素容差
                if (near) {
                    ctx.mapUi.pendingPoint = cursor
                    ctx.mapUi.cursorPoint = null
                    ctx.mapUi.cursorScreen = null
                    ctx.mapUi.status = "已确认位置 · 填写名称后保存"
                } else {
                    ctx.mapUi.cursorPoint = lat to lng
                    ctx.mapUi.cursorScreen = sx to sy
                    ctx.mapUi.status = "光标已移动 · 再次点击确认添加"
                }
                true
            }
        })
    }
}
