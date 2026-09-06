package com.spineagent

import android.graphics.Color
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.Circle
import com.amap.api.maps.model.CircleOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// ── 后端 /api/map 数据结构（与 DSH 后端 map_collector.py 对齐）───────────
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

private fun colorInt(alpha255: Int, rgb: Int): Int = (alpha255 shl 24) or (rgb and 0xFFFFFF)
private fun parseHex(s: String?): Int =
    runCatching { Color.parseColor(s ?: "#ff5f5f") }.getOrDefault(0xFFFF5F5F.toInt())

/** 原生高德地图控制器：持有 MapView/AMap，叠加 航班折线 / 地震圆 / 卫星点 / 船舶点。 */
private class NativeMapHolder {
    var mapView: MapView? = null
        private set
    private var aMap: AMap? = null
    private val placeMarkers = mutableListOf<Marker>()
    private var places: List<LocalEntry> = emptyList()
    private var placeClick: ((LocalEntry) -> Unit)? = null
    private val flightLines = mutableListOf<Polyline>()
    private val quakeCircles = mutableListOf<Circle>()
    private val satCircles = mutableListOf<Circle>()
    private val vesselCircles = mutableListOf<Circle>()

    private var last: DshMapData? = null
    private var showFlights = true
    private var showQuakes = true
    private var showSats = true
    private var showVessels = false

    fun bind(v: MapView) {
        mapView = v
        aMap = v.map.apply {
            mapType = AMap.MAP_TYPE_NORMAL
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isScaleControlsEnabled = false
            uiSettings.isRotateGesturesEnabled = false
            uiSettings.isTiltGesturesEnabled = false
            moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(30.0, 105.0), 3f))
        }
        last?.let { apply(it) }
    }

    fun unbind() {
        clearAll()
        clearPlaces()
        aMap = null
        mapView = null
    }

    // ── 我的地点标记（数据库登记、含坐标的条目）──
    fun setPlaces(list: List<LocalEntry>, onClick: (LocalEntry) -> Unit) {
        places = list
        val map = aMap ?: run { placeClick = onClick; return }
        placeClick = onClick
        removePlaceMarkers()
        for (p in places) {
            if (!p.hasCoords) continue
            val hue = when (p.type) {
                EntryType.PLACE.name -> BitmapDescriptorFactory.HUE_RED
                EntryType.GAZETTEER.name -> BitmapDescriptorFactory.HUE_AZURE
                else -> BitmapDescriptorFactory.HUE_VIOLET
            }
            val m = map.addMarker(
                MarkerOptions().position(LatLng(p.lat!!, p.lng!!)).title(p.title)
                    .icon(BitmapDescriptorFactory.defaultMarker(hue)).anchor(0.5f, 1f)
            )
            m?.setObject(p.id)
            if (m != null) placeMarkers.add(m)
        }
        map.setOnMarkerClickListener { marker ->
            val id = marker.getObject() as? Long ?: -1L
            val hit = places.firstOrNull { it.id == id }
            if (hit != null) { placeClick?.invoke(hit); true } else false
        }
    }

    fun focus(e: LocalEntry) {
        val a = aMap ?: return
        if (e.hasCoords) a.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(e.lat!!, e.lng!!), 12f))
    }

    fun clearPlaces() {
        removePlaceMarkers()
        places = emptyList()
        aMap?.setOnMarkerClickListener(null)
    }

    private fun removePlaceMarkers() {
        placeMarkers.forEach { it.remove() }
        placeMarkers.clear()
    }

    fun zoomIn() { aMap?.animateCamera(CameraUpdateFactory.zoomIn()) }
    fun zoomOut() { aMap?.animateCamera(CameraUpdateFactory.zoomOut()) }
    fun zoomReset() {
        aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(30.0, 105.0), 3f))
    }

    fun update(data: DshMapData?, fl: Boolean, qk: Boolean, st: Boolean, vs: Boolean) {
        last = data
        showFlights = fl; showQuakes = qk; showSats = st; showVessels = vs
        apply(data)
    }

    private fun apply(data: DshMapData?) {
        clearAll()
        val map = aMap ?: return
        if (data == null) return
        try {
            if (showFlights) {
                for (f in (data.flights ?: emptyList()).take(80)) {
                    map.addPolyline(
                        PolylineOptions()
                            .add(LatLng(f.startLat, f.startLng), LatLng(f.endLat, f.endLng))
                            .width(3f)
                            .color(0xFF4176E6.toInt())
                    ).let { flightLines.add(it) }
                }
            }
            if (showQuakes) {
                for (q in data.quakes ?: emptyList()) {
                    val base = parseHex(q.color)
                    val radius = maxOf(60000.0, q.maxRadius * 200000.0)
                    map.addCircle(
                        CircleOptions()
                            .center(LatLng(q.lat, q.lng))
                            .radius(radius)
                            .strokeWidth(2f)
                            .strokeColor(base)
                            .fillColor(colorInt(46, base))
                    ).let { quakeCircles.add(it) }
                }
            }
            if (showSats) {
                for (s in data.sats ?: emptyList()) {
                    map.addCircle(
                        CircleOptions()
                            .center(LatLng(s.lat, s.lng))
                            .radius(40000.0)
                            .strokeWidth(1f)
                            .strokeColor(0xFFF2C94C.toInt())
                            .fillColor(colorInt(153, 0xFFF2C94C.toInt()))
                    ).let { satCircles.add(it) }
                }
            }
            if (showVessels) {
                for (v in data.vessels ?: emptyList()) {
                    map.addCircle(
                        CircleOptions()
                            .center(LatLng(v.lat, v.lng))
                            .radius(40000.0)
                            .strokeWidth(1f)
                            .strokeColor(0xFF54D68F.toInt())
                            .fillColor(colorInt(153, 0xFF54D68F.toInt()))
                    ).let { vesselCircles.add(it) }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MapNative", "overlay apply failed", e)
        }
    }

    private fun clearAll() {
        flightLines.forEach { it.remove() }
        quakeCircles.forEach { it.remove() }
        satCircles.forEach { it.remove() }
        vesselCircles.forEach { it.remove() }
        flightLines.clear(); quakeCircles.clear(); satCircles.clear(); vesselCircles.clear()
    }
}

// ── 数据获取（后端优先；后端挂了回退 USGS 地震 + ISS）───────────────────
private suspend fun loadMapData(holder: NativeMapHolder, onState: (String) -> Unit) {
    val base = AppUiState.harnessUrl.trim().trimEnd('/').ifEmpty { "http://127.0.0.1:8080" }
    val d = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$base/api/map").build()
            mapNet.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null
                else resp.body?.string()?.let { gson.fromJson(it, DshMapData::class.java) }
            }
        } catch (e: Exception) { null }
    }
    if (d != null) {
        val n = (d.flights?.size ?: 0) + (d.quakes?.size ?: 0) + (d.sats?.size ?: 0) + (d.vessels?.size ?: 0)
        holder.update(d, true, true, true, false)
        onState("高德地图 · 实时事件 $n · ${d.updatedAt ?: ""}".trim())
        return
    }
    val fb = withContext(Dispatchers.IO) { fetchPublicFallback() }
    if (fb != null) {
        holder.update(fb, true, true, true, false)
        onState("后端离线 · 公开源兜底（地震 ${fb.quakes?.size ?: 0} · ISS）")
    } else {
        onState("暂无数据 · 检查 DSH 后端连接")
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
                    quakes.add(
                        MapQuake(
                            lat = c[1].asDouble, lng = c[0].asDouble,
                            maxRadius = minOf(3.0, 0.25 * mag),
                            color = if (mag >= 5) "#ff5f5f" else "#ff9f6f"
                        )
                    )
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

// ── Compose 页面 ───────────────────────────────────────────────────────
@Composable
fun NativeMapScreen() {
    val holder = remember { NativeMapHolder() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var fl by remember { mutableStateOf(true) }
    var qk by remember { mutableStateOf(true) }
    var st by remember { mutableStateOf(true) }
    var vs by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("高德地图 · 加载中…") }
    val ctxMap = LocalContext.current
    val db = remember { LocalDb(ctxMap) }
    var places by remember { mutableStateOf<List<LocalEntry>>(emptyList()) }
    var selected by remember { mutableStateOf<LocalEntry?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var placeOn by remember { mutableStateOf(true) }
    var bioOn by remember { mutableStateOf(false) }   // 生物分布层默认不标出


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
            val rows = withContext(Dispatchers.IO) { db.listWithCoords() }
            places = rows
            holder.setPlaces(if (placeOn) rows else emptyList()) { selected = it }
            loadMapData(holder) { status = it }
            delay(30_000)
        }
    }

    Box(Modifier.fillMaxSize().background(ComposeColor(0xFFDFF2E4))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                // 高德 3D SDK 隐私合规：必须在任何 SDK 接口前调用（errorCode 555570/10001）
                MapsInitializer.updatePrivacyShow(ctx, true, true)
                MapsInitializer.updatePrivacyAgree(ctx, true)
                // 显式设置 key（同 Manifest meta-data，双保险；SDK 10.x 读取 com.amap.api.v2.apikey）
                MapsInitializer.setApiKey("249aa0d64aa16a4b2aaa2854b17fa065")
                MapView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    onCreate(null)
                    holder.bind(this)
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) onResume()
                }
            },
            onRelease = { v ->
                v.onDestroy()
                holder.unbind()
            }
        )

        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Public, null,
                tint = ComposeColor(0xFF3E9B6F), modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    "世界地图", color = ComposeColor(0xFF1F4A36),
                    fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
                Text(status, color = ComposeColor(0xFF5F9678), fontSize = 11.sp, maxLines = 1)
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LayerChip("航班", 0xFF4176E6.toInt(), fl) { fl = !fl; holder.update(null, fl, qk, st, vs) }
            LayerChip("地震", 0xFFFF5F5F.toInt(), qk) { qk = !qk; holder.update(null, fl, qk, st, vs) }
            LayerChip("卫星", 0xFFF2C94C.toInt(), st) { st = !st; holder.update(null, fl, qk, st, vs) }
            LayerChip("船舶", 0xFF54D68F.toInt(), vs) { vs = !vs; holder.update(null, fl, qk, st, vs) }
            LayerChip("地点", 0xFFE8543D.toInt(), placeOn) { placeOn = !placeOn; holder.setPlaces(if (placeOn) places else emptyList()) { selected = it } }
            LayerChip("生物", 0xFF8A6AE8.toInt(), bioOn) { bioOn = !bioOn }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ZoomBtn(Icons.Default.Add) { holder.zoomIn() }
            ZoomBtn(Icons.Default.Remove) { holder.zoomOut() }
            ZoomBtn(Icons.Default.RestartAlt) { holder.zoomReset() }
        }

        PlaceSearchUi(
            places = places,
            searchOpen = searchOpen,
            searchText = searchText,
            onToggle = { searchOpen = !searchOpen; searchText = "" },
            onSearch = { searchText = it },
            onPick = { e -> holder.focus(e); selected = e; searchText = ""; searchOpen = false }
        )
        PlaceDetailPanel(selected, onClose = { selected = null })
    }
}

@Composable
private fun LayerChip(label: String, rgb: Int, on: Boolean, onClick: () -> Unit) {
    val bg = if (on) ComposeColor(0xFF3E9B6F) else ComposeColor(0x99FFFFFF)
    val fg = if (on) ComposeColor.White else ComposeColor(0xFF5F9678)
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(ComposeColor(rgb)))
        Spacer(Modifier.width(5.dp))
        Text(label, color = fg, fontSize = 12.sp)
    }
}

@Composable
private fun ZoomBtn(icon: ImageVector, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
            .background(ComposeColor(0xB3FFFFFF))
    ) {
        Icon(icon, null, tint = ComposeColor(0xFF2B6B45), modifier = Modifier.size(17.dp))
    }
}

// ── 地图搜索栏（搜我登记的数据库条目）──
@Composable
private fun BoxScope.PlaceSearchUi(
    places: List<LocalEntry>,
    searchOpen: Boolean,
    searchText: String,
    onToggle: () -> Unit,
    onSearch: (String) -> Unit,
    onPick: (LocalEntry) -> Unit
) {
    Surface(shape = CircleShape, color = ComposeColor(0xB3FFFFFF), modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 14.dp)) {
        IconButton(onClick = onToggle) {
            Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                "搜索地点", tint = ComposeColor(0xFF2B6B45), modifier = Modifier.size(19.dp))
        }
    }
    if (searchOpen) {
        Column(Modifier.align(Alignment.TopCenter).padding(top = 12.dp).fillMaxWidth(0.6f)) {
            OutlinedTextField(value = searchText, onValueChange = onSearch,
                placeholder = { Text("搜索我登记的地点 / 地方志…", fontSize = 13.sp) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            if (searchText.isNotBlank()) {
                val hits = places.filter { it.title.contains(searchText, true) || it.body.contains(searchText, true) }
                if (hits.isNotEmpty()) {
                    LazyColumn(Modifier.fillMaxWidth().padding(top = 4.dp)
                        .clip(RoundedCornerShape(12.dp)).background(ComposeColor(0xF5FFFFFF))) {
                        items(hits, key = { it.id }) { e ->
                            Row(Modifier.fillMaxWidth().clickable { onPick(e) }
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
}

// ── 地点详情面板：介绍 + 照片 + 来源 ──
@Composable
private fun BoxScope.PlaceDetailPanel(entry: LocalEntry?, onClose: () -> Unit) {
    entry ?: return
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp)
        .clip(RoundedCornerShape(16.dp)).background(ComposeColor(0xF7FFFFFF))) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ComposeColor(0xFF1F4A36))
                    Text(entry.typeLabel + if (entry.hasCoords) " · " + String.format("%.4f, %.4f", entry.lat, entry.lng) else "",
                        fontSize = 12.sp, color = ComposeColor(0xFF7FAE92))
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "关闭", tint = ComposeColor(0xFF5F9678)) }
            }
            if (entry.body.isNotBlank()) {
                Text(entry.body, fontSize = 14.sp, color = ComposeColor(0xFF23402F),
                    modifier = Modifier.padding(top = 6.dp))
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
                                modifier = Modifier.size(110.dp).clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop)
                        }
                    }
                }
            }
            if (entry.source.isNotBlank()) {
                Text("来源：" + entry.source, fontSize = 11.sp, color = ComposeColor(0xFF8AA99A),
                    modifier = Modifier.padding(top = 6.dp))
            }
            TextButton(onClick = { AppUiState.module = "db" }) {
                Text("在「数据库」中维护", color = ComposeColor(0xFF3E9B6F), fontSize = 12.sp)
            }
        }
    }
}
