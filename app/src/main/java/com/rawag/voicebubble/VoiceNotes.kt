package com.rawag.voicebubble

import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File

data class VoiceNote(val file: File) {
    val path: String get() = file.path
    val time: Long get() = file.lastModified()
}

/** Finds WhatsApp / WhatsApp Business voice notes and keeps a cache of their transcripts. */
object VoiceNotes {

    private fun roots(): List<File> {
        val ext = Environment.getExternalStorageDirectory()
        return listOf(
            File(ext, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes"),
            File(ext, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Voice Notes"),
            File(ext, "WhatsApp/Media/WhatsApp Voice Notes"),              // Android 10 and older
            File(ext, "WhatsApp Business/Media/WhatsApp Business Voice Notes"),
        )
    }

    fun whatsappFolderFound(): Boolean = roots().any { it.isDirectory }

    /** Latest voice notes, newest first. WhatsApp stores them in weekly sub-folders like 202546/. */
    fun latest(limit: Int = 20): List<VoiceNote> {
        val now = System.currentTimeMillis()
        val all = ArrayList<File>()
        for (root in roots()) {
            val dirs = root.listFiles { f -> f.isDirectory }?.sortedByDescending { it.name }?.take(3) ?: continue
            for (d in dirs + root) {
                d.listFiles { f -> f.isFile && f.length() > 0 && (f.name.endsWith(".opus") || f.name.endsWith(".m4a") || f.name.endsWith(".aac")) }
                    ?.let { all.addAll(it) }
            }
        }
        return all
            .filter { now - it.lastModified() > 1500 }     // skip files still being written
            .sortedByDescending { it.lastModified() }
            .take(limit)
            .map { VoiceNote(it) }
    }

    // ---- transcript cache ----
    private var cache: JSONObject? = null
    private fun cacheFile(c: Context) = File(c.filesDir, "transcripts.json")

    @Synchronized
    private fun load(c: Context): JSONObject {
        cache?.let { return it }
        val f = cacheFile(c)
        val j = try { if (f.exists()) JSONObject(f.readText()) else JSONObject() } catch (e: Exception) { JSONObject() }
        cache = j
        return j
    }

    @Synchronized
    fun transcript(c: Context, path: String): String? = load(c).optString(path, "").ifEmpty { null }

    @Synchronized
    fun save(c: Context, path: String, text: String) {
        val j = load(c)
        j.put(path, text)
        // keep cache small
        if (j.length() > 500) {
            val keys = j.keys().asSequence().toList()
            keys.take(j.length() - 400).forEach { j.remove(it) }
        }
        cacheFile(c).writeText(j.toString())
    }
}
