package com.spineagent

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.Circle
import com.amap.api.maps.model.CircleOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.Polygon
import com.amap.api.maps.model.PolygonOptions
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.spineagent.plugin.MapLayerApi
import com.spineagent.plugin.Spine
import com.spineagent.plugin.SpineContext
import com.spineagent.plugin.renderEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

// ══════════════════════════════════════════════════════════════════
// 地图模块（插件宿主视图）
//  - 图层来自 ctx.mapLayers（MapPlugin / DatabasePlugin / BioPlugin 贡献）
//  - 地图点击派发给 ctx.mapTaps（插件注册动作，含"任意位置添加点位"）
//  - 本文件只认识 MapLayerApi 抽象 + 注册表，不认识任何具体图层
// ══════════════════════════════════════════════════════════════════

data class DshMapData(
    val flights: List<MapFlight>? = null,
    val quakes: List<MapQuake>? = null,
    val sats: List<MapMark>? = null,
    val vessels: List<MapMark>? = null,
    val updatedAt: String? = null,
    val source: String? = null
)
data class MapFlight(
    val startLat: Double = 0.0, val startLng: Double = 0.0,
    val endLat: Double = 0.0, val endLng: Double = 0.0
)
data class MapQuake(
    val lat: Double = 0.0, val lng: Double = 0.0,
    val maxRadius: Double = 0.0, val color: String? = null
)
data class MapMark(val lat: Double = 0.0, val lng: Double = 0.0)

private val mapNet = OkHttpClient.Builder()
    .connectTimeout(6, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()
private val gson = Gson()

private fun dotDescriptor(color: Int, density: Float): BitmapDescriptor {
    val s = (26 * density).toInt().coerceAtLeast(22)
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = Color.WHITE
    cv.drawCircle(s / 2f, s / 2f, s / 2f, p)
    p.color = color
    cv.drawCircle(s / 2f, s / 2f, s / 2f - 2f * density, p)
    return BitmapDescriptorFactory.fromBitmap(bmp)
}

private fun crosshairDescriptor(density: Float): BitmapDescriptor {
    val s = (34 * density).toInt().coerceAtLeast(28)
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val c = s / 2f
    p.style = Paint.Style.STROKE
    p.strokeWidth = 3f * density
    p.color = 0xFFE08A3C.toInt()
    cv.drawCircle(c, c, c - 3f * density, p)
    cv.drawLine(c, 2f * density, c, s - 2f * density, p)
    cv.drawLine(2f * density, c, s - 2f * density, c, p)
    p.style = Paint.Style.FILL
    cv.drawCircle(c, c, 2.5f * density, p)
    return BitmapDescriptorFactory.fromBitmap(bmp)
}

private class NativeMapHolder : MapLayerApi {
    var mapView: MapView? = null
        private set
    private var aMap: AMap? = null
    private val overlays = LinkedHashMap<String, MutableList<Any>>()
    private val cursorMarkers = mutableListOf<Marker>()
    private var cursorTap: (() -> Unit)? = null
    var onMapTap: ((Double, Double, Float, Float) -> Unit)? = null
    var redraw: (() -> Unit)? = null

    fun bind(v: MapView) {
        mapView = v
        aMap = v.map.apply {
            mapType = AMap.MAP_TYPE_NORMAL
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isScaleControlsEnabled = false
            uiSettings.isRotateGesturesEnabled = false
            uiSettings.isTiltGesturesEnabled = false
            moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(30.0, 105.0), 3f))
            setOnMapClickListener { ll ->
                val p = projection.toScreenLocation(ll)
                onMapTap?.invoke(ll.latitude, ll.longitude, p.x.toFloat(), p.y.toFloat())
            }
            setOnMarkerClickListener { m ->
                val cb = m.getObject()
                if (cb is Function0<*>) {
                    @Suppress("UNCHECKED_CAST")
                    (cb as () -> Unit).invoke()
                    true
                } else false
            }
        }
        redraw?.invoke()
    }

    fun unbind() {
        clearAllLayers()
        aMap?.setOnMarkerClickListener(null)
        aMap?.setOnMapClickListener(null)
        aMap = null
        mapView = null
    }

    fun zoomIn() { aMap?.animateCamera(CameraUpdateFactory.zoomIn()) }
    fun zoomOut() { aMap?.animateCamera(CameraUpdateFactory.zoomOut()) }
    fun zoomReset() { aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(30.0, 105.0), 3f)) }
    fun focus(lat: Double, lng: Double, zoom: Float) {
        aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), zoom))
    }

    override fun addMarker(layerId: String, lat: Double, lng: Double, colorArgb: Int, title: String, onTap: (() -> Unit)?) {
        val map = aMap ?: return
        val dens = mapView?.resources?.displayMetrics?.density ?: 1f
        val m = map.addMarker(
            MarkerOptions().position(LatLng(lat, lng)).title(title)
                .icon(dotDescriptor(colorArgb, dens)).anchor(0.5f, 0.5f)
        ) ?: return
        m.setObject(onTap)
        store(layerId, m)
    }

    override fun addPolyline(layerId: String, points: List<Pair<Double, Double>>, colorArgb: Int, width: Float) {
        val map = aMap ?: return
        if (points.size < 2) return
        val opts = PolylineOptions().width(width).color(colorArgb)
        points.forEach { opts.add(LatLng(it.first, it.second)) }
        map.addPolyline(opts)?.let { store(layerId, it) }
    }

    override fun addCircle(layerId: String, lat: Double, lng: Double, radiusMeters: Double, colorArgb: Int, strokeWidth: Float, fillAlpha: Int) {
        val map = aMap ?: return
        val fill = (fillAlpha shl 24) or (colorArgb and 0xFFFFFF)
        map.addCircle(
            CircleOptions().center(LatLng(lat, lng)).radius(radiusMeters)
                .strokeWidth(strokeWidth).strokeColor(colorArgb).fillColor(fill)
        )?.let { store(layerId, it) }
    }

    override fun addPolygon(layerId: String, ring: List<Pair<Double, Double>>, colorArgb: Int, strokeWidth: Float, fillAlpha: Int) {
        val map = aMap ?: return
        if (ring.size < 3) return
        val fill = (fillAlpha shl 24) or (colorArgb and 0xFFFFFF)
        val opts = PolygonOptions().strokeWidth(strokeWidth).strokeColor(colorArgb).fillColor(fill)
        ring.forEach { opts.add(LatLng(it.first, it.second)) }
        map.addPolygon(opts)?.let { store(layerId, it) }
    }

    override fun clearLayer(layerId: String) {
        overlays.remove(layerId)?.forEach { o ->
            when (o) {
                is Marker -> o.remove()
                is Circle -> o.remove()
                is Polyline -> o.remove()
                is Polygon -> o.remove()
            }
        }
    }

    override fun clearAllLayers() { overlays.keys.toList().forEach { clearLayer(it) } }

    /** 两段式添加点位的光标：橙色准星（独立于图层重绘） */
    override fun setCursor(lat: Double, lng: Double) {
        val map = aMap ?: return
        clearCursor()
        val dens = mapView?.resources?.displayMetrics?.density ?: 1f
        val m = map.addMarker(
            MarkerOptions().position(LatLng(lat, lng)).title("待确认点位")
                .icon(crosshairDescriptor(dens)).anchor(0.5f, 0.5f)
        ) ?: return
        m.setObject(cursorTap)          // 点光标本身 = 确认添加
        cursorMarkers.add(m)
    }

    override fun clearCursor() {
        cursorMarkers.forEach { it.remove() }
        cursorMarkers.clear()
    }

    override fun setCursorTapHandler(cb: (() -> Unit)?) { cursorTap = cb }

    private fun store(layerId: String, o: Any) {
        overlays.getOrPut(layerId) { mutableListOf() }.add(o)
    }
}

private suspend fun loadMapData(ctx: SpineContext) {
    val base = AppUiState.harnessUrl.trim().trimEnd('/').ifEmpty { "http://127.0.0.1:8080" }
    val d = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(base + "/api/map").build()
            mapNet.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null
                else resp.body?.string()?.let { gson.fromJson(it, DshMapData::class.java) }
            }
        } catch (e: Exception) { null }
    }
    if (d != null) {
        ctx.mapUi.lastData = d
        val n = (d.flights?.size ?: 0) + (d.quakes?.size ?: 0) + (d.sats?.size ?: 0) + (d.vessels?.size ?: 0)
        ctx.mapUi.status = "高德地图 · 实时事件 " + n
        return
    }
    val fb = withContext(Dispatchers.IO) { fetchPublicFallback() }
    if (fb != null) {
        ctx.mapUi.lastData = fb
        ctx.mapUi.status = "后端离线 · 公开源兜底（地震 " + (fb.quakes?.size ?: 0) + " · ISS）"
    } else {
        ctx.mapUi.status = "暂无数据 · 检查 DSH 后端连接"
    }
}

private suspend fun fetchPublicFallback(): DshMapData? = try {
    val quakes = mutableListOf<MapQuake>()
    val sats = mutableListOf<MapMark>()
    withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_day.geojson").build()
            mapNet.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching
                val root = gson.fromJson(resp.body?.string(), JsonObject::class.java)
                val feats = root.getAsJsonArray("features") ?: return@runCatching
                for (f in feats.take(120)) {
                    val o = f.asJsonObject
                    val g = o.getAsJsonObject("geometry") ?: continue
                    val c = g.getAsJsonArray("coordinates") ?: continue
                    val mag = o.getAsJsonObject("properties")?.get("mag")?.asDouble ?: 2.0
                    quakes.add(MapQuake(lat = c[1].asDouble, lng = c[0].asDouble,
                        maxRadius = minOf(3.0, 0.25 * mag), color = if (mag >= 5) "#ff5f5f" else "#ff9f6f"))
                }
            }
        }
        runCatching {
            val req = Request.Builder().url("https://api.wheretheiss.at/v1/satellites/25544").build()
            mapNet.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val o = gson.fromJson(resp.body?.string(), JsonObject::class.java)
                    sats.add(MapMark(lat = o.get("latitude").asDouble, lng = o.get("longitude").asDouble))
                }
            }
        }
    }
    DshMapData(flights = emptyList(), quakes = quakes, sats = sats, vessels = emptyList())
} catch (e: Exception) { null }

private suspend fun reloadPlaces(ctx: SpineContext) {
    val rows = withContext(Dispatchers.IO) { ctx.db.listWithCoords() }
    ctx.mapUi.places = rows
}

@Composable
fun NativeMapScreen() {
    val ctx = Spine.ctx
    val ui = ctx.mapUi
    val holder = remember { NativeMapHolder() }
    val lifecycleOwner = LocalLifecycleOwner.current

    remember {
        ctx.mapLayers.all().forEach { l -> if (!ui.layers.containsKey(l.id)) ui.layers[l.id] = l.defaultOn }
        true
    }
    DisposableEffect(Unit) {
        holder.redraw = { ctx.mapLayers.renderEnabled(holder, ui.layers) }
        holder.onMapTap = { lat, lng, sx, sy -> ctx.mapTaps.dispatch(lat, lng, sx, sy) }
        holder.setCursorTapHandler {
            val c = ui.cursorPoint
            val s = ui.cursorScreen
            if (c != null) ctx.mapTaps.dispatch(c.first, c.second, s?.first ?: 0f, s?.second ?: 0f)
        }
        onDispose { holder.redraw = null; holder.onMapTap = null }
    }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                        holder.mapView?.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> holder.mapView?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            reloadPlaces(ctx)
            loadMapData(ctx)
            holder.redraw?.invoke()
            delay(30_000)
        }
    }
    LaunchedEffect(ui.cursorPoint) {
        val c = ui.cursorPoint
        if (c == null) holder.clearCursor() else holder.setCursor(c.first, c.second)
    }
    LaunchedEffect(ui.layers.toMap()) { holder.redraw?.invoke() }
    LaunchedEffect(ui.placesVersion) { reloadPlaces(ctx); holder.redraw?.invoke() }
    LaunchedEffect(ui.places) { holder.redraw?.invoke() }

    Box(Modifier.fillMaxSize().background(ComposeColor(0xFFDFF2E4))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                MapsInitializer.updatePrivacyShow(c, true, true)
                MapsInitializer.updatePrivacyAgree(c, true)
                MapsInitializer.setApiKey("249aa0d64aa16a4b2aaa2854b17fa065")
                MapView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    onCreate(null)
                    holder.bind(this)
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) onResume()
                }
            },
            onRelease = { v -> v.onDestroy(); holder.unbind() }
        )

        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Public, null, tint = ComposeColor(0xFF3E9B6F), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text("世界地图", color = ComposeColor(0xFF1F4A36), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(ui.status, color = ComposeColor(0xFF5F9678), fontSize = 11.sp, maxLines = 1)
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ctx.mapLayers.all().forEach { layer ->
                LayerChip(layer.label, layer.colorArgb, ui.layers[layer.id] ?: layer.defaultOn, layer.available()) {
                    ui.layers[layer.id] = !(ui.layers[layer.id] ?: layer.defaultOn)
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ZoomBtn(Icons.Default.Add) { holder.zoomIn() }
            ZoomBtn(Icons.Default.Remove) { holder.zoomOut() }
            ZoomBtn(Icons.Default.RestartAlt) { holder.zoomReset() }
        }

        CursorConfirmCard(
            point = ui.cursorPoint,
            onConfirm = { p ->
                val s = ctx.mapUi.cursorScreen
                ctx.mapTaps.dispatch(p.first, p.second, s?.first ?: 0f, s?.second ?: 0f)
            },
            onCancel = { ui.cursorPoint = null; ui.cursorScreen = null; ui.status = "已取消 · 点击地图任意位置重新落点" }
        )
        PlaceSearchUi(ui.places) { e ->
            if (e.hasCoords) holder.focus(e.lat!!, e.lng!!, 12f)
            ui.selected = e
        }
        PlaceDetailPanel(ui.selected, ctx, onClose = { ui.selected = null })
        AddPointDialog(ui.pendingPoint, onCancel = { ui.pendingPoint = null }, onSave = { name ->
            val p = ui.pendingPoint
            if (p != null) {
                val now = System.currentTimeMillis()
                ctx.db.insert(LocalEntry(type = EntryType.PLACE.name, title = name,
                    lat = p.first, lng = p.second, body = "", source = "", createdAt = now, updatedAt = now))
                ui.pendingPoint = null
                ui.status = "已添加点位：" + name
                ctx.bumpPlaces()
            }
        })
    }
}

/** 两段式第一步的确认卡片：显示光标坐标，可点按钮或再点地图确认 */
@Composable
private fun BoxScope.CursorConfirmCard(
    point: Pair<Double, Double>?,
    onConfirm: (Pair<Double, Double>) -> Unit,
    onCancel: () -> Unit
) {
    if (point == null) return
    Surface(
        shape = RoundedCornerShape(14.dp), color = ComposeColor(0xF2FFFFFF),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✛ 待确认：" + String.format("%.5f, %.5f", point.first, point.second),
                fontSize = 12.sp, color = ComposeColor(0xFF1F4A36))
            Spacer(Modifier.width(10.dp))
            TextButton(onClick = { onConfirm(point) }) { Text("确认添加", fontSize = 13.sp) }
            TextButton(onClick = onCancel) { Text("取消", fontSize = 13.sp, color = ComposeColor(0xFF7FAE92)) }
        }
    }
}

@Composable
private fun BoxScope.AddPointDialog(point: Pair<Double, Double>?, onCancel: () -> Unit, onSave: (String) -> Unit) {
    if (point == null) return
    var name by remember(point) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("新建点位") },
        text = {
            Column {
                Text("坐标 " + String.format("%.5f, %.5f", point.first, point.second),
                    fontSize = 12.sp, color = ComposeColor(0xFF7FAE92))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("地点名称") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim()) }, enabled = name.isNotBlank()) {
                Text("保存地点")
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } }
    )
}

@Composable
private fun BoxScope.PlaceSearchUi(places: List<LocalEntry>, onPick: (LocalEntry) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    Surface(shape = CircleShape, color = ComposeColor(0xB3FFFFFF),
        modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 14.dp)) {
        IconButton(onClick = { open = !open; if (!open) text = "" }) {
            Icon(if (open) Icons.Default.Close else Icons.Default.Search, "搜索地点",
                tint = ComposeColor(0xFF2B6B45), modifier = Modifier.size(19.dp))
        }
    }
    if (!open) return
    Column(Modifier.align(Alignment.TopCenter).padding(top = 12.dp).fillMaxWidth(0.6f)) {
        OutlinedTextField(value = text, onValueChange = { text = it },
            placeholder = { Text("搜索我登记的地点 / 地方志…", fontSize = 13.sp) },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        if (text.isNotBlank()) {
            val hits = places.filter { it.title.contains(text, true) || it.body.contains(text, true) }
            if (hits.isNotEmpty()) {
                LazyColumn(Modifier.fillMaxWidth().padding(top = 4.dp)
                    .clip(RoundedCornerShape(12.dp)).background(ComposeColor(0xF5FFFFFF))) {
                    items(hits, key = { it.id }) { e ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(e); open = false; text = "" }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(e.title, color = ComposeColor(0xFF1F4A36), fontSize = 14.sp,
                                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text(e.typeLabel, color = ComposeColor(0xFF7FAE92), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.PlaceDetailPanel(entry: LocalEntry?, ctx: SpineContext, onClose: () -> Unit) {
    entry ?: return
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var quick by remember(entry.id) { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val dir = File(c.filesDir, "photos").apply { mkdirs() }
            val dst = File(dir, UUID.randomUUID().toString() + ".img")
            runCatching {
                c.contentResolver.openInputStream(uri)?.use { ins -> dst.outputStream().use { o -> ins.copyTo(o) } }
            }
            val upd = entry.copy(photos = entry.photos + dst.absolutePath, updatedAt = System.currentTimeMillis())
            scope.launch(Dispatchers.IO) { ctx.db.update(upd) }
            ctx.mapUi.selected = upd
            ctx.mapUi.places = ctx.mapUi.places.map { if (it.id == upd.id) upd else it }
        }
    }
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp).zIndex(5f)
        .clip(RoundedCornerShape(16.dp)).background(ComposeColor(0xF7FFFFFF))) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ComposeColor(0xFF1F4A36))
                    Text(entry.typeLabel + if (entry.hasCoords)
                        " · " + String.format("%.4f, %.4f", entry.lat, entry.lng) else "",
                        fontSize = 12.sp, color = ComposeColor(0xFF7FAE92))
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "关闭", tint = ComposeColor(0xFF5F9678)) }
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (entry.body.isNotBlank()) {
                    Text(entry.body, fontSize = 14.sp, color = ComposeColor(0xFF23402F), modifier = Modifier.padding(top = 6.dp))
                }
                if (entry.photos.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        entry.photos.forEach { p ->
                            val bmp = remember(p) {
                                runCatching {
                                    val opts = BitmapFactory.Options().apply { inSampleSize = 3 }
                                    BitmapFactory.decodeFile(p, opts)
                                }.getOrNull()
                            }
                            if (bmp != null) {
                                Image(bmp.asImageBitmap(), contentDescription = null,
                                    modifier = Modifier.size(100.dp).clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop)
                            }
                        }
                    }
                }
                if (entry.source.isNotBlank()) {
                    Text("来源：" + entry.source, fontSize = 11.sp, color = ComposeColor(0xFF8AA99A),
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                OutlinedTextField(value = quick, onValueChange = { quick = it },
                    placeholder = { Text("快速补充一句…（存进介绍）", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f), singleLine = true)
                IconButton(onClick = {
                    if (quick.isNotBlank()) {
                        val upd = entry.copy(body = entry.body + "\n· " + quick.trim(),
                            updatedAt = System.currentTimeMillis())
                        scope.launch(Dispatchers.IO) { ctx.db.update(upd) }
                        ctx.mapUi.selected = upd
                        ctx.mapUi.places = ctx.mapUi.places.map { if (it.id == upd.id) upd else it }
                        quick = ""
                    }
                }) { Icon(Icons.Default.Save, "保存补充", tint = ComposeColor(0xFF3E9B6F), modifier = Modifier.size(20.dp)) }
                IconButton(onClick = { picker.launch(arrayOf("image/*")) }) {
                    Icon(Icons.Default.PhotoCamera, "加照片", tint = ComposeColor(0xFF3E9B6F), modifier = Modifier.size(20.dp))
                }
            }
            TextButton(onClick = { AppUiState.module = "db" }, modifier = Modifier.align(Alignment.End)) {
                Text("在「数据库」完整编辑", color = ComposeColor(0xFF3E9B6F), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LayerChip(label: String, rgb: Int, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg = when {
        !enabled -> ComposeColor(0x66FFFFFF)
        on -> ComposeColor(0xFF3E9B6F)
        else -> ComposeColor(0x99FFFFFF)
    }
    val fg = when {
        !enabled -> ComposeColor(0xFFB9C9BF)
        on -> ComposeColor.White
        else -> ComposeColor(0xFF5F9678)
    }
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(ComposeColor(rgb)))
        Spacer(Modifier.width(5.dp))
        Text(if (enabled) label else label + "(无数据)", color = fg, fontSize = 12.sp)
    }
}

@Composable
private fun ZoomBtn(icon: ImageVector, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(ComposeColor(0xB3FFFFFF))
    ) {
        Icon(icon, null, tint = ComposeColor(0xFF2B6B45), modifier = Modifier.size(18.dp))
    }
}
