package com.spineagent.plugin.plugins

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import com.spineagent.NativeMapScreen
import com.spineagent.plugin.MapLayerContribution
import com.spineagent.plugin.ModuleDescriptor
import com.spineagent.plugin.ScopedContext
import com.spineagent.plugin.SpinePlugin

/**
 * 地图插件：提供「地图」模块本体，并向 ctx 贡献实时事件图层。
 * 图层只依赖 MapLayerApi 抽象，不感知 AMap 的存在。
 */
object MapPlugin : SpinePlugin {
    override val id = "map"
    override val title = "地图"

    override fun install(ctx: ScopedContext) {
        ctx.module(ModuleDescriptor(
            id = id, title = title, icon = Icons.Default.Public, order = 10,
            screen = { NativeMapScreen() }
        ))

        ctx.mapLayer(MapLayerContribution("flights", "航班", 0xFF4176E6.toInt(), true, 10) { api ->
            ctx.mapUi.lastData?.flights?.take(80)?.forEach { f ->
                api.addPolyline("flights", listOf(f.startLat to f.startLng, f.endLat to f.endLng), 0xFF4176E6.toInt(), 3f)
            }
        })
        ctx.mapLayer(MapLayerContribution("quakes", "地震", 0xFFFF5F5F.toInt(), true, 20) { api ->
            ctx.mapUi.lastData?.quakes?.forEach { q ->
                api.addCircle("quakes", q.lat, q.lng, maxOf(60000.0, q.maxRadius * 200000.0), parseHexColor(q.color), 2f, 46)
            }
        })
        ctx.mapLayer(MapLayerContribution("sats", "卫星", 0xFFF2C94C.toInt(), true, 30) { api ->
            ctx.mapUi.lastData?.sats?.forEach { s ->
                api.addCircle("sats", s.lat, s.lng, 40000.0, 0xFFF2C94C.toInt(), 1f, 153)
            }
        })
        ctx.mapLayer(MapLayerContribution("vessels", "船舶", 0xFF54D68F.toInt(), false, 40) { api ->
            ctx.mapUi.lastData?.vessels?.forEach { v ->
                api.addCircle("vessels", v.lat, v.lng, 40000.0, 0xFF54D68F.toInt(), 1f, 153)
            }
        })
    }
}

internal fun parseHexColor(s: String?): Int =
    runCatching { android.graphics.Color.parseColor(s ?: "#ff5f5f") }.getOrDefault(0xFFFF5F5F.toInt())
