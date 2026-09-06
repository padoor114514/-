package com.spineagent

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// ── 本地“数据库”模块：地点介绍 / 地方志 / 档案馆资料 ─────────────
enum class EntryType(val label: String) {
    PLACE("地点"), GAZETTEER("地方志"), ARCHIVE("档案");

    companion object {
        fun from(s: String?): EntryType? = entries.firstOrNull { it.name == s }
    }
}

data class LocalEntry(
    val id: Long = 0L,
    val type: String = EntryType.PLACE.name,
    val title: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val body: String = "",
    val source: String = "",
    val photos: List<String> = emptyList(),   // 本地文件绝对路径
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val typeLabel: String get() = EntryType.from(type)?.label ?: type
    val hasCoords: Boolean get() = lat != null && lng != null
}

class LocalDb(context: Context) : SQLiteOpenHelper(context.applicationContext, "spineagent.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE entries(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                title TEXT NOT NULL,
                lat REAL, lng REAL,
                body TEXT DEFAULT '',
                source TEXT DEFAULT '',
                photos TEXT DEFAULT '',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX idx_entries_type ON entries(type)")
        db.execSQL("CREATE INDEX idx_entries_title ON entries(title)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
        if (oldV < 1) db.execSQL("DROP TABLE IF EXISTS entries")
        onCreate(db)
    }

    fun insert(e: LocalEntry): Long {
        val v = toValues(e)
        v.remove("id")
        return writableDatabase.insert("entries", null, v)
    }

    fun update(e: LocalEntry): Boolean {
        val v = toValues(e)
        return writableDatabase.update("entries", v, "id=?", arrayOf(e.id.toString())) > 0
    }

    fun delete(id: Long): Boolean =
        writableDatabase.delete("entries", "id=?", arrayOf(id.toString())) > 0

    fun get(id: Long): LocalEntry? {
        readableDatabase.query("entries", null, "id=?", arrayOf(id.toString()), null, null, null)
            .use { c -> return if (c.moveToFirst()) fromCursor(c) else null }
    }

    /** kw 匹配标题/正文；typeFilter=null 表示全部 */
    fun list(kw: String? = null, typeFilter: String? = null): List<LocalEntry> {
        val sel = StringBuilder("1=1")
        val args = mutableListOf<String>()
        if (!kw.isNullOrBlank()) {
            sel.append(" AND (title LIKE ? OR body LIKE ? OR source LIKE ?)")
            val like = "%" + kw.trim() + "%"
            args += like; args += like; args += like
        }
        if (!typeFilter.isNullOrBlank()) {
            sel.append(" AND type=?")
            args += typeFilter
        }
        val out = mutableListOf<LocalEntry>()
        readableDatabase.query(
            "entries", null, sel.toString(), args.toTypedArray(),
            null, null, "updated_at DESC"
        ).use { c ->
            while (c.moveToNext()) out.add(fromCursor(c))
        }
        return out
    }

    fun listWithCoords(): List<LocalEntry> = list().filter { it.hasCoords }

    private fun toValues(e: LocalEntry): ContentValues = ContentValues().apply {
        put("type", e.type)
        put("title", e.title)
        e.lat?.let { put("lat", it) } ?: putNull("lat")
        e.lng?.let { put("lng", it) } ?: putNull("lng")
        put("body", e.body)
        put("source", e.source)
        put("photos", e.photos.joinToString("\n"))
        put("created_at", e.createdAt)
        put("updated_at", e.updatedAt)
    }

    private fun fromCursor(c: android.database.Cursor): LocalEntry = LocalEntry(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        type = c.getString(c.getColumnIndexOrThrow("type")),
        title = c.getString(c.getColumnIndexOrThrow("title")),
        lat = if (c.isNull(c.getColumnIndexOrThrow("lat"))) null else c.getDouble(c.getColumnIndexOrThrow("lat")),
        lng = if (c.isNull(c.getColumnIndexOrThrow("lng"))) null else c.getDouble(c.getColumnIndexOrThrow("lng")),
        body = c.getString(c.getColumnIndexOrThrow("body")) ?: "",
        source = c.getString(c.getColumnIndexOrThrow("source")) ?: "",
        photos = (c.getString(c.getColumnIndexOrThrow("photos")) ?: "").split("\n").filter { it.isNotBlank() },
        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
        updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
    )
}
