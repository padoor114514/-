package com.spineagent.plugin

// ── 地图扩展点 ────────────────────────────────────────────────────

/** 地图绘制原语：由地图插件实现（原生 AMap 覆盖物），其它插件只依赖这层抽象 */
interface MapLayerApi {
    fun addMarker(layerId: String, lat: Double, lng: Double, colorArgb: Int, title: String, onTap: (() -> Unit)? = null)
    fun addPolyline(layerId: String, points: List<Pair<Double, Double>>, colorArgb: Int, width: Float)
    fun addCircle(layerId: String, lat: Double, lng: Double, radiusMeters: Double, colorArgb: Int, strokeWidth: Float, fillAlpha: Int)
    fun addPolygon(layerId: String, ring: List<Pair<Double, Double>>, colorArgb: Int, strokeWidth: Float, fillAlpha: Int)
    fun clearLayer(layerId: String)
    fun clearAllLayers()
    /** 待确认光标（两段式添加点位的第一阶段指示；不受图层重绘影响） */
    fun setCursor(lat: Double, lng: Double)
    fun clearCursor()
    /** 点击光标本身 = 确认（由宿主编排到 tap 动作） */
    fun setCursorTapHandler(cb: (() -> Unit)?)
}

/** 一个地图图层注册项（数据/绘制逻辑由贡献插件提供） */
class MapLayerContribution(
    val id: String,
    val label: String,
    val colorArgb: Int,
    val defaultOn: Boolean,
    val order: Int,
    /** 是否可用（例如生物层在未导入数据前为 false，图例里灰显） */
    val available: () -> Boolean = { true },
    val render: (MapLayerApi) -> Unit
)

class MapLayerRegistry {
    private val items = LinkedHashMap<String, MapLayerContribution>()
    fun register(l: MapLayerContribution): Disposable {
        items[l.id] = l
        return Disposable { items.remove(l.id) }
    }
    fun all(): List<MapLayerContribution> = items.values.sortedBy { it.order }
    fun byId(id: String): MapLayerContribution? = items[id]
}

/** 地图点击动作：点击地图任意位置的处理器（优先级大者先执行，返回 true 表示已消费） */
class MapTapAction(
    val id: String,
    val label: String,
    val priority: Int,
    /** lat, lng, 以及点击处屏幕像素坐标（用于"再次点击同一点"的判定） */
    val handler: (lat: Double, lng: Double, sx: Float, sy: Float) -> Boolean
)

class MapTapRegistry {
    private val items = LinkedHashMap<String, MapTapAction>()
    fun register(a: MapTapAction): Disposable {
        items[a.id] = a
        return Disposable { items.remove(a.id) }
    }
    fun all(): List<MapTapAction> = items.values.sortedByDescending { it.priority }
    fun dispatch(lat: Double, lng: Double, sx: Float = 0f, sy: Float = 0f): Boolean {
        for (a in all()) {
            if (a.handler(lat, lng, sx, sy)) return true
        }
        return false
    }
}

/** 依据开关状态重绘所有可用图层 */
fun MapLayerRegistry.renderEnabled(api: MapLayerApi, enabled: Map<String, Boolean>) {
    api.clearAllLayers()
    for (l in all()) {
        if (!l.available()) continue
        if (enabled[l.id] ?: l.defaultOn) l.render(api)
    }
}
