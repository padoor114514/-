package com.spineagent

import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// WorldMap — 世界地图（二维点阵 / 球形正交投影）· 大地巡礼

private data class Continent(val pts: List<Pair<Double, Double>>)

private val CONTINENTS = listOf(
    Continent(listOf(-167.0 to 66.0, -160.0 to 58.0, -150.0 to 60.0, -137.0 to 59.0, -130.0 to 53.0,
        -124.0 to 44.0, -117.0 to 32.0, -110.0 to 24.0, -100.0 to 20.0, -97.0 to 26.0, -90.0 to 29.0,
        -83.0 to 25.0, -80.0 to 32.0, -74.0 to 39.0, -70.0 to 44.0, -62.0 to 47.0, -54.0 to 52.0,
        -60.0 to 59.0, -72.0 to 64.0, -90.0 to 68.0, -110.0 to 70.0, -130.0 to 70.0, -152.0 to 69.0,
        -167.0 to 66.0)),
    Continent(listOf(-92.0 to 16.0, -96.0 to 16.0, -105.0 to 20.0, -99.0 to 18.0, -92.0 to 17.0,
        -85.0 to 12.0, -80.0 to 9.0, -77.0 to 8.0, -83.0 to 13.0, -88.0 to 16.0, -92.0 to 16.0)),
    Continent(listOf(-55.0 to 60.0, -45.0 to 60.0, -35.0 to 66.0, -30.0 to 71.0, -40.0 to 76.0,
        -55.0 to 77.0, -68.0 to 75.0, -73.0 to 66.0, -62.0 to 60.0, -55.0 to 60.0)),
    Continent(listOf(-77.0 to 8.0, -70.0 to 11.0, -62.0 to 10.0, -55.0 to 5.0, -50.0 to 0.0,
        -44.0 to -4.0, -39.0 to -9.0, -40.0 to -16.0, -47.0 to -26.0, -53.0 to -34.0, -58.0 to -39.0,
        -63.0 to -42.0, -67.0 to -45.0, -72.0 to -47.0, -73.0 to -40.0, -73.0 to -30.0, -72.0 to -22.0,
        -70.0 to -12.0, -75.0 to -5.0, -77.0 to 8.0)),
    Continent(listOf(-10.0 to 36.0, -8.0 to 42.0, -9.0 to 48.0, -1.0 to 50.0, 2.0 to 52.0, 5.0 to 55.0,
        8.0 to 57.0, 11.0 to 59.0, 16.0 to 61.0, 22.0 to 60.0, 27.0 to 59.0, 29.0 to 56.0, 28.0 to 52.0,
        30.0 to 46.0, 26.0 to 41.0, 22.0 to 38.0, 18.0 to 40.0, 14.0 to 42.0, 10.0 to 44.0, 6.0 to 43.0,
        0.0 to 40.0, -10.0 to 37.0, -10.0 to 36.0)),
    Continent(listOf(-17.0 to 15.0, -15.0 to 28.0, -10.0 to 34.0, -3.0 to 37.0, 3.0 to 37.0, 11.0 to 35.0,
        20.0 to 32.0, 32.0 to 31.0, 34.0 to 27.0, 38.0 to 20.0, 40.0 to 12.0, 44.0 to 8.0, 48.0 to 12.0,
        43.0 to 2.0, 40.0 to -8.0, 36.0 to -18.0, 29.0 to -27.0, 21.0 to -34.0, 17.0 to -34.0, 14.0 to -26.0,
        11.0 to -16.0, 7.0 to -6.0, 3.0 to 2.0, -5.0 to 5.0, -12.0 to 8.0, -16.0 to 12.0, -17.0 to 15.0)),
    Continent(listOf(28.0 to 30.0, 32.0 to 42.0, 42.0 to 50.0, 52.0 to 59.0, 62.0 to 68.0, 78.0 to 72.0,
        95.0 to 74.0, 115.0 to 72.0, 132.0 to 70.0, 150.0 to 68.0, 165.0 to 64.0, 177.0 to 63.0,
        178.0 to 60.0, 170.0 to 58.0, 160.0 to 52.0, 150.0 to 45.0, 140.0 to 39.0, 134.0 to 33.0,
        128.0 to 30.0, 120.0 to 28.0, 113.0 to 24.0, 110.0 to 18.0, 106.0 to 10.0, 101.0 to 14.0,
        96.0 to 22.0, 88.0 to 22.0, 82.0 to 12.0, 78.0 to 8.0, 73.0 to 8.0, 70.0 to 18.0, 64.0 to 26.0,
        57.0 to 28.0, 50.0 to 30.0, 44.0 to 35.0, 38.0 to 37.0, 33.0 to 36.0, 28.0 to 30.0)),
    Continent(listOf(70.0 to 20.0, 73.0 to 24.0, 80.0 to 27.0, 87.0 to 25.0, 89.0 to 22.0, 85.0 to 16.0,
        80.0 to 10.0, 76.0 to 8.0, 72.0 to 10.0, 70.0 to 20.0)),
    Continent(listOf(114.0 to -22.0, 122.0 to -14.0, 132.0 to -12.0, 142.0 to -11.0, 152.0 to -14.0,
        154.0 to -24.0, 150.0 to -34.0, 144.0 to -39.0, 136.0 to -38.0, 128.0 to -32.0, 120.0 to -31.0,
        114.0 to -34.0, 112.0 to -26.0, 114.0 to -22.0)),
    Continent(listOf(-170.0 to -70.0, -120.0 to -74.0, -60.0 to -72.0, 0.0 to -70.0, 60.0 to -72.0,
        120.0 to -74.0, 170.0 to -70.0, 180.0 to -78.0, 180.0 to -88.0, -180.0 to -88.0, -180.0 to -70.0,
        -170.0 to -70.0))
)

private fun inside(poly: List<Pair<Double, Double>>, lon: Double, lat: Double): Boolean {
    var res = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val xi = poly[i].first; val yi = poly[i].second
        val xj = poly[j].first; val yj = poly[j].second
        if (((yi > lat) != (yj > lat)) && (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)) res = !res
        j = i
    }
    return res
}

private val LAND: List<Pair<Double, Double>> by lazy {
    val pts = mutableListOf<Pair<Double, Double>>()
    var lat = -84.0
    while (lat <= 84.0) {
        var lon = -180.0
        while (lon <= 180.0) {
            if (CONTINENTS.any { inside(it.pts, lon, lat) }) pts.add(lon to lat)
            lon += 3.0
        }
        lat += 3.0
    }
    pts
}

private const val DEEPS_BLUE = 0xFF679EFE.toInt()
private const val DEEPS_500 = 0xFF4176E6.toInt()

@Composable
fun WorldMapScreen() {
    var mode by remember { mutableStateOf("2d") }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Public, null, tint = Color(DEEPS_BLUE), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("世界地图", color = DshColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x1F000000)).padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                MapModeBtn("二维地图", mode == "2d") { mode = "2d" }
                MapModeBtn("球形地图", mode == "globe") { mode = "globe" }
            }
        }

        Box(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF16202C), Color(0xFF0E1520), Color(0xFF101A29))))
        ) {
            if (mode == "2d") Map2DCanvas(Modifier.fillMaxSize()) else MapGlobeCanvas(Modifier.fillMaxSize())
            Row(Modifier.align(Alignment.BottomStart).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(DEEPS_BLUE)))
                Spacer(Modifier.width(8.dp))
                Text("已标记为工作区 · 大地巡礼", color = Color(0xFFCFD3D6), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MapModeBtn(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color(0xFF0F1115) else Color(0xFFCFD3D6),
        fontSize = 12.5.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun Map2DCanvas(modifier: Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offX by remember { mutableFloatStateOf(0f) }
    var offY by remember { mutableFloatStateOf(0f) }
    val land = remember { LAND }

    Box(modifier) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offX += pan.x
                    offY += pan.y
                }
            }
        ) {
            val w = size.width; val h = size.height
            fun ex(lon: Double) = ((((lon + 180.0) / 360.0) * w - offX) * scale).toFloat()
            fun ey(lat: Double) = ((((90.0 - lat) / 180.0) * h - offY) * scale).toFloat()
            drawRect(Brush.linearGradient(listOf(Color(0xFF16202C), Color(0xFF0E1520))))
            var lon = -180.0
            while (lon <= 180.0) {
                drawLine(Color(0x22679EFE), Offset(ex(lon), ey(-90.0)), Offset(ex(lon), ey(90.0)), 0.8f)
                lon += 30.0
            }
            var lat = -90.0
            while (lat <= 90.0) {
                drawLine(Color(0x22679EFE), Offset(ex(-180.0), ey(lat)), Offset(ex(180.0), ey(lat)), 0.8f)
                lat += 30.0
            }
            val r = 1.6.dp.toPx()
            for ((ln, lt) in land) drawCircle(Color(DEEPS_BLUE), radius = r, center = Offset(ex(ln), ey(lt)))
            drawLine(Color(0x55679EFE), Offset(ex(-180.0), ey(0.0)), Offset(ex(180.0), ey(0.0)), 1.2f)
            drawLine(Color(0x40679EFE), Offset(ex(0.0), ey(-90.0)), Offset(ex(0.0), ey(90.0)), 1.2f)
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MapIconBtn(Icons.Default.Add, "放大") { scale = (scale * 1.3f).coerceAtMost(6f) }
            MapIconBtn(Icons.Default.Remove, "缩小") { scale = (scale / 1.3f).coerceAtLeast(1f) }
            MapIconBtn(Icons.Default.RestartAlt, "重置") { scale = 1f; offX = 0f; offY = 0f }
        }
    }
}

@Composable
private fun MapIconBtn(icon: ImageVector, desc: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(Color(0x1FFFFFFF))
    ) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MapGlobeCanvas(modifier: Modifier) {
    var rotLon by remember { mutableFloatStateOf(20f) }
    var rotLat by remember { mutableFloatStateOf(-14f) }
    var dragging by remember { mutableStateOf(false) }
    val land = remember { LAND }

    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            if (!dragging) rotLon = (rotLon + 0.22f) % 360f
        }
    }

    Canvas(
        modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { dragging = true },
                onDragEnd = { dragging = false },
                onDragCancel = { dragging = false },
                onDrag = { change, amount ->
                    change.consume()
                    rotLon -= amount.x * 0.4f
                    rotLat = (rotLat + amount.y * 0.4f).coerceIn(-90f, 90f)
                }
            )
        }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f - 6.dp.toPx()
        if (r <= 0f) return@Canvas
        drawCircle(
            Brush.radialGradient(listOf(Color(0x33679EFE), Color(0x0D679EFE), Color.Transparent), center = Offset(cx, cy), radius = r * 1.2f),
            radius = r * 1.2f, center = Offset(cx, cy)
        )
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF1A2740), Color(0xFF0D1523)), center = Offset(cx - r * 0.3f, cy - r * 0.3f), radius = r),
            radius = r, center = Offset(cx, cy)
        )
        clipPath(Path().apply { addOval(Rect(Offset(cx - r, cy - r), Offset(cx + r, cy + r))) }) {
            val deg = PI / 180.0
            val p0 = rotLat * deg
            val l0 = rotLon * deg
            val cosP = cos(p0).toFloat()
            val sinP = sin(p0).toFloat()
            fun proj(lon: Double, lat: Double): Pair<Offset, Float> {
                val phi = lat * deg
                val lam = lon * deg
                val x = cos(phi).toFloat() * sin(lam - l0).toFloat()
                val y = cosP * sin(phi).toFloat() - sinP * cos(phi).toFloat() * cos(lam - l0).toFloat()
                val z = sinP * sin(phi).toFloat() + cosP * cos(phi).toFloat() * cos(lam - l0).toFloat()
                return Offset(cx + x * r, cy - y * r) to z
            }
            val gridColor = Color(0x29679EFE)
            var lon = -180.0
            var lat = -90.0
            while (lon <= 180.0) {
                val path = Path(); var first = true
                lat = -90.0
                while (lat <= 90.0) {
                    val (p, z) = proj(lon, lat)
                    if (z > 0f) { if (first) { path.moveTo(p.x, p.y); first = false } else path.lineTo(p.x, p.y) }
                    lat += 2.0
                }
                drawPath(path, gridColor, style = Stroke(width = 1f))
                lon += 30.0
            }
            lat = -90.0
            while (lat <= 90.0) {
                val path = Path(); var first = true
                lon = -180.0
                while (lon <= 180.0) {
                    val (p, z) = proj(lon, lat)
                    if (z > 0f) { if (first) { path.moveTo(p.x, p.y); first = false } else path.lineTo(p.x, p.y) }
                    lon += 2.0
                }
                drawPath(path, gridColor, style = Stroke(width = 1f))
                lat += 30.0
            }
            val dr = 1.6.dp.toPx()
            for ((ln, lt) in land) {
                val (p, z) = proj(ln, lt)
                if (z > 0.02f) drawCircle(Color(DEEPS_BLUE), radius = dr, center = p)
            }
        }
        drawCircle(Color(0x66FFFFFF), radius = r, center = Offset(cx, cy), style = Stroke(width = 1.4.dp.toPx()))
    }
}
