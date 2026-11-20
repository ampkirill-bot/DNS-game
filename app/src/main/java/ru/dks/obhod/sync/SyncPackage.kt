package ru.dks.obhod.sync

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.dks.obhod.App
import ru.dks.obhod.data.HangarNote
import ru.dks.obhod.data.HangarPhoto
import ru.dks.obhod.data.Reading
import ru.dks.obhod.data.Remark
import ru.dks.obhod.data.RemarkPhoto
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class TransferSelection(
    val readings: Boolean = true,
    val hangar: Boolean = true,
    val remarks: Boolean = true,
) {
    val any: Boolean get() = readings || hangar || remarks
}

data class ImportStats(val added: Int = 0, val updated: Int = 0, val skipped: Int = 0) {
    fun message(): String = "Добавлено: $added · обновлено: $updated · без изменений: $skipped"
}

/** Один ZIP-пакет содержит JSON-описание и выбранные фотографии. */
class SyncPackage(private val app: App) {
    suspend fun create(selection: TransferSelection): File = withContext(Dispatchers.IO) {
        val manifest = JSONObject()
            .put("format", 1)
            .put("createdAt", System.currentTimeMillis())
        val files = linkedMapOf<String, File>()

        if (selection.readings) {
            manifest.put("readings", JSONArray().apply {
                app.db.readings().snapshot().forEach { r ->
                    put(JSONObject().apply {
                        put("syncId", r.syncId); put("gpaId", r.gpaId); put("timestamp", r.timestamp)
                        put("updatedAt", r.updatedAt); put("note", r.note)
                        putNullable("cyl1", r.cyl1); putNullable("cyl2", r.cyl2); putNullable("cyl3", r.cyl3)
                        putNullable("cyl4", r.cyl4); putNullable("cyl5", r.cyl5)
                        putNullable("mskOilLevel", r.mskOilLevel); putNullable("mskFilterDiff", r.mskFilterDiff)
                        putNullable("sguLeak1", r.sguLeak1); putNullable("sguLeak2", r.sguLeak2)
                        putNullable("engineOilLevel", r.engineOilLevel)
                        putNullable("pressureBefore", r.pressureBefore); putNullable("pressureAfter", r.pressureAfter)
                    })
                }
            })
        }

        if (selection.hangar) {
            manifest.put("hangarNotes", JSONArray().apply {
                app.db.hangar().notesSnapshot().forEach { n ->
                    put(JSONObject().put("gpaId", n.gpaId).put("text", n.text).put("updatedAt", n.updatedAt))
                }
            })
            manifest.put("hangarPhotos", JSONArray().apply {
                app.db.hangar().photosSnapshot().forEach { p ->
                    val file = File(p.path)
                    if (!file.isFile) return@forEach
                    val entry = photoEntry(p.syncId)
                    files[entry] = file
                    put(JSONObject().put("syncId", p.syncId).put("gpaId", p.gpaId)
                        .put("timestamp", p.timestamp).put("caption", p.caption)
                        .put("updatedAt", p.updatedAt).put("file", entry))
                }
            })
        }

        if (selection.remarks) {
            val allPhotos = app.db.remarks().photosSnapshot()
            manifest.put("remarks", JSONArray().apply {
                app.db.remarks().snapshot().forEach { r ->
                    val photos = JSONArray()
                    allPhotos.filter { it.remarkId == r.id }.forEach { p ->
                        val file = File(p.path)
                        if (!file.isFile) return@forEach
                        val entry = photoEntry(p.syncId)
                        files[entry] = file
                        photos.put(JSONObject().put("syncId", p.syncId).put("file", entry))
                    }
                    put(JSONObject().put("syncId", r.syncId).put("gpaId", r.gpaId)
                        .put("timestamp", r.timestamp).put("text", r.text).put("resolved", r.resolved)
                        .put("updatedAt", r.updatedAt).put("photos", photos))
                }
            })
        }

        val out = File(File(app.cacheDir, "transfers").apply { mkdirs() }, "dks-${UUID.randomUUID()}.zip")
        runCatching {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                files.forEach { (name, file) ->
                    zip.putNextEntry(ZipEntry(name))
                    FileInputStream(file).use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }.onFailure { out.delete() }.getOrThrow()
        out
    }

    suspend fun importPackage(file: File, selection: TransferSelection): ImportStats = withContext(Dispatchers.IO) {
        val manifest = readManifest(file)
        check(manifest.getInt("format") == 1) { "Неподдерживаемый формат пакета" }
        val wanted = linkedMapOf<String, String>()
        if (selection.hangar) manifest.optJSONArray("hangarPhotos").forEach { o ->
            val entry = o.getString("file")
            if (validEntry(entry)) wanted[entry] = o.getString("syncId")
        }
        if (selection.remarks) manifest.optJSONArray("remarks").forEach { r ->
            r.getJSONArray("photos").forEach { p ->
                val entry = p.getString("file")
                if (validEntry(entry)) wanted[entry] = p.getString("syncId")
            }
        }

        val localPaths = mutableMapOf<String, String>()
        val createdFiles = mutableListOf<File>()
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name in wanted) {
                        val output = app.photos.newFile()
                        FileOutputStream(output).use { zip.copyTo(it) }
                        localPaths[entry.name] = output.absolutePath
                        createdFiles += output
                    }
                    zip.closeEntry()
                }
            }
            val stats = merge(manifest, localPaths, selection)
            val retainedPaths = runCatching {
                buildSet {
                    app.db.hangar().photosSnapshot().forEach { add(it.path) }
                    app.db.remarks().photosSnapshot().forEach { add(it.path) }
                }
            }.getOrNull()
            if (retainedPaths != null) {
                createdFiles.filter { it.absolutePath !in retainedPaths }.forEach { runCatching { it.delete() } }
            }
            stats
        } catch (e: Throwable) {
            createdFiles.forEach { it.delete() }
            throw e
        }
    }

    private suspend fun merge(manifest: JSONObject, paths: Map<String, String>, selection: TransferSelection): ImportStats {
        var added = 0
        var updated = 0
        var skipped = 0
        app.db.withTransaction {
            if (selection.readings) manifest.optJSONArray("readings").forEach { o ->
                val incoming = reading(o)
                val local = app.db.readings().bySyncId(incoming.syncId)
                when {
                    local == null -> { app.db.readings().upsert(incoming); added++ }
                    incoming.updatedAt > local.updatedAt -> {
                        app.db.readings().upsert(incoming.copy(id = local.id)); updated++
                    }
                    else -> skipped++
                }
            }
            if (selection.hangar) {
                manifest.optJSONArray("hangarNotes").forEach { o ->
                    val incoming = HangarNote(o.getString("gpaId"), o.getString("text"), o.getLong("updatedAt"))
                    val local = app.db.hangar().note(incoming.gpaId)
                    when {
                        local == null -> { app.db.hangar().saveNote(incoming); added++ }
                        incoming.updatedAt > local.updatedAt -> { app.db.hangar().saveNote(incoming); updated++ }
                        else -> skipped++
                    }
                }
                manifest.optJSONArray("hangarPhotos").forEach { o ->
                    val incoming = HangarPhoto(
                        gpaId = o.getString("gpaId"), path = "", timestamp = o.getLong("timestamp"),
                        caption = o.getString("caption"), syncId = o.getString("syncId"), updatedAt = o.getLong("updatedAt"),
                    )
                    val local = app.db.hangar().photoBySyncId(incoming.syncId)
                    when {
                        local == null -> {
                            val path = paths[o.getString("file")] ?: return@forEach
                            app.db.hangar().insertPhoto(incoming.copy(path = path)); added++
                        }
                        incoming.updatedAt > local.updatedAt -> {
                            app.db.hangar().updatePhoto(incoming.copy(id = local.id, path = local.path)); updated++
                        }
                        else -> skipped++
                    }
                }
            }
            if (selection.remarks) manifest.optJSONArray("remarks").forEach { o ->
                val incoming = Remark(
                    gpaId = o.getString("gpaId"), timestamp = o.getLong("timestamp"), text = o.getString("text"),
                    resolved = o.getBoolean("resolved"), syncId = o.getString("syncId"), updatedAt = o.getLong("updatedAt"),
                )
                val local = app.db.remarks().bySyncId(incoming.syncId)
                when {
                    local == null || incoming.updatedAt > local.updatedAt -> {
                        val localId = app.db.remarks().upsert(incoming.copy(id = local?.id ?: 0))
                        app.db.remarks().clearPhotos(localId)
                        val photos = o.getJSONArray("photos").mapNotNull { p ->
                            val path = paths[p.getString("file")] ?: return@mapNotNull null
                            RemarkPhoto(remarkId = localId, path = path, syncId = p.getString("syncId"))
                        }
                        if (photos.isNotEmpty()) app.db.remarks().insertPhotos(photos)
                        if (local == null) added++ else updated++
                    }
                    else -> skipped++
                }
            }
        }
        return ImportStats(added, updated, skipped)
    }

    private fun readManifest(file: File): JSONObject = ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.name == "manifest.json") {
                val bytes = zip.readBytesLimited(10 * 1024 * 1024)
                return JSONObject(String(bytes, Charsets.UTF_8))
            }
        }
        error("В пакете отсутствует описание данных")
    }

    private fun reading(o: JSONObject) = Reading(
        gpaId = o.getString("gpaId"), timestamp = o.getLong("timestamp"),
        cyl1 = o.doubleOrNull("cyl1"), cyl2 = o.doubleOrNull("cyl2"), cyl3 = o.doubleOrNull("cyl3"),
        cyl4 = o.doubleOrNull("cyl4"), cyl5 = o.doubleOrNull("cyl5"),
        mskOilLevel = o.doubleOrNull("mskOilLevel"), mskFilterDiff = o.doubleOrNull("mskFilterDiff"),
        sguLeak1 = o.doubleOrNull("sguLeak1"), sguLeak2 = o.doubleOrNull("sguLeak2"),
        engineOilLevel = o.doubleOrNull("engineOilLevel"), pressureBefore = o.doubleOrNull("pressureBefore"),
        pressureAfter = o.doubleOrNull("pressureAfter"), note = o.getString("note"),
        syncId = o.getString("syncId"), updatedAt = o.getLong("updatedAt"),
    )

    private fun photoEntry(syncId: String): String = "photos/$syncId"
    private fun validEntry(name: String): Boolean = Regex("photos/[A-Za-z0-9.-]+").matches(name)
    private fun JSONObject.doubleOrNull(name: String): Double? = if (isNull(name)) null else getDouble(name)
    private fun JSONObject.putNullable(name: String, value: Double?) = put(name, value ?: JSONObject.NULL)

    private fun ZipInputStream.readBytesLimited(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            check(out.size() + count <= limit) { "Слишком большое описание пакета" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
}

private inline fun JSONArray?.forEach(block: (JSONObject) -> Unit) {
    if (this == null) return
    for (i in 0 until length()) block(getJSONObject(i))
}

private inline fun <T> JSONArray.mapNotNull(block: (JSONObject) -> T?): List<T> {
    val result = mutableListOf<T>()
    for (i in 0 until length()) block(getJSONObject(i))?.let(result::add)
    return result
}
