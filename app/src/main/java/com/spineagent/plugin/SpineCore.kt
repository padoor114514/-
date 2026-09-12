package com.spineagent.plugin

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.spineagent.LocalDb
import com.spineagent.LocalEntry

// ══════════════════════════════════════════════════════════════════
// SpineAgent 插件内核（仿 DSH / Cordis 设计）
//
//  - 一切都是插件：向共享上下文 ctx 贡献注册项，注册都是副作用，
//    插件卸载时统一撤销（Disposable 链）。
//  - 扩展点＝注册表：ctx.modules（侧边栏模块）、ctx.mapLayers（地图图层）、
//    ctx.mapTaps（地图点击动作）；新增能力＝新增注册项，不改内核。
// ══════════════════════════════════════════════════════════════════

/** 可撤销的副作用（对应 Cordis 的 dispose） */
class Disposable(val onDispose: () -> Unit) {
    private var disposed = false
    fun dispose() { if (!disposed) { disposed = true; onDispose() } }
}

class DisposableScope {
    private val items = mutableListOf<Disposable>()
    fun add(d: Disposable) { items.add(d) }
    fun disposeAll() { items.asReversed().forEach { it.dispose() }; items.clear() }
}

/** 一个插件：安装时通过作用域 ctx 贡献注册项（注册项随插件卸载自动撤销） */
interface SpinePlugin {
    val id: String
    val title: String
    fun install(ctx: ScopedContext)
}

/** 侧边栏模块注册项 */
class ModuleDescriptor(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val order: Int,
    val screen: @Composable () -> Unit,
    /** 选中时的额外动作（如展开工作区） */
    val onSelect: (() -> Unit)? = null,
    /** 长按动作（如 agent 开小窗） */
    val onLongPress: (() -> Unit)? = null,
    val hint: String? = null
)

class ModuleRegistry {
    private val items = LinkedHashMap<String, ModuleDescriptor>()
    fun register(d: ModuleDescriptor): Disposable {
        items[d.id] = d
        return Disposable { items.remove(d.id) }
    }
    fun all(): List<ModuleDescriptor> = items.values.sortedBy { it.order }
    fun byId(id: String?): ModuleDescriptor? = id?.let { items[it] }
    val default: ModuleDescriptor? get() = all().firstOrNull()
}

// ── 地图共享状态（跨插件读写，Compose 可观察）──
class MapUiState {
    /** 当前待新建的点位（地图任意处点击后由 tap action 写入） */
    var pendingPoint by mutableStateOf<Pair<Double, Double>?>(null)      // lat, lng
    /** 图层的开关状态：layerId -> on */
    val layers = mutableStateMapOf<String, Boolean>()
    /** 最近一次 /api/map 数据 */
    var lastData by mutableStateOf<com.spineagent.DshMapData?>(null)
    /** 数据库地点缓存 */
    var places by mutableStateOf<List<LocalEntry>>(emptyList())
    /** 数据库版本号：登记/修改后 +1，地图据此重载地点 */
    var placesVersion by mutableStateOf(0)
    /** 状态栏文案 */
    var status by mutableStateOf("高德地图 · 加载中…")
    /** 当前在地图上打开详情的地点（由地点图层/搜索写入） */
    var selected by mutableStateOf<LocalEntry?>(null)
}

/** 共享上下文：注册表 + 服务 + 共享状态 */
class SpineContext(val appContext: Context) {
    val modules = ModuleRegistry()
    val mapLayers = MapLayerRegistry()
    val mapTaps = MapTapRegistry()
    val mapUi = MapUiState()

    /** 地点/地方志/档案存储服务（供数据库插件与地图插件共用） */
    val db: LocalDb by lazy { LocalDb(appContext) }

    fun bumpPlaces() { mapUi.placesVersion += 1 }
}

/** 插件宿主：安装/卸载插件，持有 ctx */
class PluginHost(val ctx: SpineContext) {
    private val scopes = LinkedHashMap<String, DisposableScope>()
    private val installed = LinkedHashMap<String, SpinePlugin>()

    fun install(plugin: SpinePlugin) {
        if (installed.containsKey(plugin.id)) return
        val scope = DisposableScope()
        plugin.install(ScopedContext(ctx, scope))
        scopes[plugin.id] = scope
        installed[plugin.id] = plugin
    }

    /** 卸载插件：撤销它贡献的所有注册项（Cordis 式可逆副作用） */
    fun uninstall(id: String) {
        scopes.remove(id)?.disposeAll()
        installed.remove(id)
    }

    fun installedIds(): List<String> = installed.keys.toList()
}

/** 作用域上下文：注册项自动挂到当前插件的 dispose 链上 */
class ScopedContext(private val base: SpineContext, private val scope: DisposableScope) {
    val appContext: Context get() = base.appContext
    val mapUi: MapUiState get() = base.mapUi
    val db: LocalDb get() = base.db
    fun bumpPlaces() = base.bumpPlaces()

    fun module(d: ModuleDescriptor) { scope.add(base.modules.register(d)) }
    fun mapLayer(l: MapLayerContribution) { scope.add(base.mapLayers.register(l)) }
    fun mapTap(t: MapTapAction) { scope.add(base.mapTaps.register(t)) }
}

/** 全局入口（MainActivity.onCreate 里初始化） */
object Spine {
    @Volatile private var hostRef: PluginHost? = null

    fun start(appContext: Context): PluginHost {
        hostRef?.let { return it }
        synchronized(this) {
            hostRef?.let { return it }
            val host = PluginHost(SpineContext(appContext.applicationContext))
            PluginCatalog.installAll(host)
            hostRef = host
            return host
        }
    }

    val host: PluginHost get() = hostRef ?: error("PluginHost 未初始化：先调用 Spine.start(context)")
    val ctx: SpineContext get() = host.ctx
}
