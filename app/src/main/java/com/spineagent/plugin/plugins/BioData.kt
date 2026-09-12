package com.spineagent.plugin.plugins

import android.content.res.AssetManager
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 生物分布数据：assets/bio/ 下的 GeoJSON（FeatureCollection，Polygon / MultiPolygon）。
 * 数据未导入时 hasData=false，图层自动灰显；放入文件即生效。
 */
object BioData {
    private var cachedKey: String? = null
    private var cached: List<List<Pair<Double, Double>>> = emptyList()

    private fun files(assets: AssetManager): List<String> =
        runCatching {
            (assets.list("bio") ?: emptyArray())
                .filter { it.endsWith(".geojson", true) || it.endsWith(".json", true) }
        }.getOrDefault(emptyList())

    fun hasData(assets: AssetManager): Boolean = files(assets).isNotEmpty()

    /** 所有多边形外环（lat, lng） */
    fun polygons(assets: AssetManager): List<List<Pair<Double, Double>>> {
        val fs = files(assets)
        val key = fs.joinToString(",")
        if (key == cachedKey) return cached
        val out = mutableListOf<List<Pair<Double, Double>>>()
        for (name in fs) {
            runCatching {
                val text = assets.open("bio/" + name).bufferedReader().use { it.readText() }
                val root = JsonParser.parseString(text).asJsonObject
                val feats = root.getAsJsonArray("features") ?: return@runCatching
                for (f in feats) {
                    val geom = f.asJsonObject.getAsJsonObject("geometry") ?: continue
                    val type = geom.get("type")?.asString ?: continue
                    val coords = geom.getAsJsonArray("coordinates") ?: continue
                    when (type) {
                        "Polygon" -> ringOf(coords)?.let { out.add(it) }
                        "MultiPolygon" -> for (poly in coords) {
                            (poly as? com.google.gson.JsonArray)?.let { p -> ringOf(p)?.let { out.add(it) } }
                        }
                    }
                }
            }
        }
        cachedKey = key
        cached = out
        return out
    }

    private fun ringOf(poly: com.google.gson.JsonArray): List<Pair<Double, Double>>? {
        val ring = poly.get(0) as? com.google.gson.JsonArray ?: return null
        val pts = ring.mapNotNull { p ->
            val arr = p as? com.google.gson.JsonArray ?: return@mapNotNull null
            if (arr.size() < 2) null else arr[1].asDouble to arr[0].asDouble   // geojson 是 lng,lat
        }
        return if (pts.size >= 3) pts else null
    }
}
