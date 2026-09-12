package com.spineagent.plugin.plugins

import com.spineagent.plugin.MapLayerContribution
import com.spineagent.plugin.ScopedContext
import com.spineagent.plugin.SpinePlugin

/**
 * 生物分布插件：默认关闭；数据放在 assets/bio 目录下的 GeoJSON（FeatureCollection）。
 * 未导入任何数据时图层不可用（图例灰显），导入即生效，无需改动地图插件。
 */
object BioPlugin : SpinePlugin {
    override val id = "bio"
    override val title = "生物分布"

    override fun install(ctx: ScopedContext) {
        val assets = ctx.appContext.assets
        ctx.mapLayer(MapLayerContribution(
            id = "bio", label = "生物", colorArgb = 0xFF8A6AE8.toInt(), defaultOn = false, order = 60,
            available = { BioData.hasData(assets) }
        ) { api ->
            BioData.polygons(assets).forEach { poly ->
                api.addPolygon("bio", poly, 0xFF8A6AE8.toInt(), 2f, 60)
            }
        })
    }
}
