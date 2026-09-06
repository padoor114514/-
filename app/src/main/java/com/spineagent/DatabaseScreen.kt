package com.spineagent

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

// ── “数据库”模块：地点介绍 / 地方志 / 档案馆资料（本地 SQLite）──

@Composable
fun DatabaseScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { LocalDb(context) }

    var entries by remember { mutableStateOf<List<LocalEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<LocalEntry?>(null) }

    fun reload() {
        loading = true
        scope.launch(Dispatchers.IO) {
            val rows = db.list(query, filter)
            withContext(Dispatchers.Main) {
                entries = rows
                loading = false
            }
        }
    }

    LaunchedEffect(query, filter) { reload() }
    LaunchedEffect(Unit) { reload() }

    Box(Modifier.fillMaxSize().background(Color(0xFFEFF9F2))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("数据库", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F4A36),
                    modifier = Modifier.weight(1f))
                IconButton(onClick = { editor = LocalEntry() }) {
                    Icon(Icons.Default.Add, "新增", tint = Color(0xFF3E9B6F))
                }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索标题 / 正文 / 来源…") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF7FAE92)) },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("全部", filter == null) { filter = null }
                for (t in EntryType.entries) {
                    FilterPill(t.label, filter == t.name) { filter = t.name }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF3E9B6F))
                }
            } else if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有登记内容\n点右上角 + 添加", color = Color(0xFF7FAE92), fontSize = 14.sp)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries, key = { it.id }) { e ->
                        EntryRow(e) { editor = e.copy() }
                    }
                }
            }
        }
        editor?.let { en ->
            EntryEditor(entry = en, db = db, onClose = { editor = null; reload() })
        }
    }
}

@Composable
private fun FilterPill(label: String, on: Boolean, onClick: () -> Unit) {
    val bg = if (on) Color(0xFF3E9B6F) else Color(0xFFFFFFFF)
    val fg = if (on) Color.White else Color(0xFF5F9678)
    Text(label, color = fg, fontSize = 13.sp,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(bg).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
private fun EntryRow(e: LocalEntry, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White)
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(e.typeLabel, fontSize = 11.sp, color = Color(0xFF3E9B6F),
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFE4F5EA))
                .padding(horizontal = 6.dp, vertical = 2.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1F4A36), maxLines = 1)
            Text(e.body.replace("\n", " ").ifBlank { "（暂无正文）" }.take(48),
                fontSize = 12.sp, color = Color(0xFF7FAE92), maxLines = 1)
        }
        if (e.hasCoords) Text("📍", fontSize = 14.sp)
        if (e.photos.isNotEmpty()) Text("🖼" + e.photos.size, fontSize = 12.sp, color = Color(0xFF7FAE92))
    }
}

// ── 新建/编辑/详情 ──
@Composable
private fun EntryEditor(entry: LocalEntry, db: LocalDb, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var e by remember { mutableStateOf(entry) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun addPhoto(uri: Uri) {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val dst = File(dir, UUID.randomUUID().toString() + ".img")
        try {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                dst.outputStream().use { outs -> ins.copyTo(outs) }
            }
            e = e.copy(photos = e.photos + dst.absolutePath)
        } catch (ex: Exception) {
            error = "照片导入失败：" + ex.message
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) addPhoto(uri)
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFEFF9F2)).verticalScroll(rememberScrollState())
        .padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "返回", tint = Color(0xFF1F4A36)) }
            Text(if (e.id == 0L) "新增登记" else "编辑 · " + e.title, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, color = Color(0xFF1F4A36))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in EntryType.entries) {
                FilterPill(t.label, e.type == t.name) { e = e.copy(type = t.name) }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = e.title, onValueChange = { e = e.copy(title = it) },
            label = { Text("标题（地点名/文献名）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = e.lat?.toString() ?: "", onValueChange = { s ->
                e = e.copy(lat = s.toDoubleOrNull())
            }, label = { Text("纬度(可选)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = e.lng?.toString() ?: "", onValueChange = { s ->
                e = e.copy(lng = s.toDoubleOrNull())
            }, label = { Text("经度(可选)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f), singleLine = true)
        }
        if (e.type == EntryType.PLACE.name && !e.hasCoords) {
            Text("地点类型建议填写经纬度，地图上才能显示标记", color = Color(0xFFE08A3C), fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = e.body, onValueChange = { e = e.copy(body = it) },
            label = { Text("正文 / 介绍 / 资料摘录") }, modifier = Modifier.fillMaxWidth().height(160.dp))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = e.source, onValueChange = { e = e.copy(source = it) },
            label = { Text("来源引用（档案编号/书名/网站）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("照片 " + e.photos.size + " 张", fontSize = 13.sp, color = Color(0xFF5F9678))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { picker.launch(arrayOf("image/*")) }) {
                Icon(Icons.Default.PhotoCamera, "选图", tint = Color(0xFF3E9B6F))
            }
        }
        if (e.photos.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                e.photos.forEachIndexed { i, p ->
                    Box {
                        val bmp = remember(p) {
                            runCatching {
                                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                                BitmapFactory.decodeFile(p, opts)
                            }.getOrNull()
                        }
                        if (bmp != null) {
                            Image(bmp.asImageBitmap(), contentDescription = null,
                                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop)
                        }
                        IconButton(onClick = { e = e.copy(photos = e.photos.filterIndexed { j, _ -> j != i }) },
                            modifier = Modifier.align(Alignment.TopEnd).size(24.dp)) {
                            Text("✕", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        if (error != null) { Text(error!!, color = Color(0xFFD64545), fontSize = 12.sp) }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    if (e.title.isBlank()) { error = "标题不能为空"; return@Button }
                    saving = true
                    scope.launch(Dispatchers.IO) {
                        val now = System.currentTimeMillis()
                        val finalEntry = e.copy(createdAt = if (e.id == 0L) now else e.createdAt, updatedAt = now)
                        if (e.id == 0L) db.insert(finalEntry) else db.update(finalEntry)
                        withContext(Dispatchers.Main) { saving = false; onClose() }
                    }
                },
                enabled = !saving
            ) {
                Icon(Icons.Default.Save, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp)); Text("保存")
            }
            if (e.id != 0L) {
                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            db.delete(e.id)
                            withContext(Dispatchers.Main) { onClose() }
                        }
                    }
                ) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFD64545), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp)); Text("删除", color = Color(0xFFD64545))
                }
            }
        }
    }
}
