package com.rawag.voicebubble

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class WhisperModel(val fileName: String, val label: String, val sizeMb: Int) {
    BASE("ggml-base-q5_1.bin", "سريع (Base) ~60 ميجا", 60),
    SMALL("ggml-small-q5_1.bin", "أدق للمصري (Small) ~190 ميجا", 190);

    val url get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$fileName"
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("vb", Context.MODE_PRIVATE)

    fun modelPath(c: Context): String? = sp(c).getString("model", null)?.takeIf { File(it).length() > 1_000_000 }
    fun setModelPath(c: Context, p: String) = sp(c).edit().putString("model", p).apply()

    /** "ar", "en" or "auto" */
    fun lang(c: Context): String = sp(c).getString("lang", "ar")!!
    fun setLang(c: Context, l: String) = sp(c).edit().putString("lang", l).apply()

    fun autoTranscribe(c: Context): Boolean = sp(c).getBoolean("auto", true)
    fun setAutoTranscribe(c: Context, v: Boolean) = sp(c).edit().putBoolean("auto", v).apply()

    fun bubbleY(c: Context): Int = sp(c).getInt("by", 400)
    fun setBubblePos(c: Context, x: Int, y: Int) = sp(c).edit().putInt("bx", x).putInt("by", y).apply()
    fun bubbleX(c: Context): Int = sp(c).getInt("bx", 0)
}

object ModelManager {
    fun modelsDir(c: Context) = File(c.filesDir, "models").apply { mkdirs() }

    /** Blocking download with progress callback (0..100). Returns final file path. */
    fun download(c: Context, m: WhisperModel, onProgress: (Int) -> Unit): String {
        val dest = File(modelsDir(c), m.fileName)
        val tmp = File(dest.path + ".part")
        val conn = open(URL(m.url), 0)
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(256 * 1024)
                var done = 0L
                var last = -1
                while (true) {
                    val r = input.read(buf)
                    if (r < 0) break
                    out.write(buf, 0, r)
                    done += r
                    if (total > 0) {
                        val p = (done * 100 / total).toInt()
                        if (p != last) { last = p; onProgress(p) }
                    }
                }
            }
        }
        if (dest.exists()) dest.delete()
        tmp.renameTo(dest)
        Prefs.setModelPath(c, dest.path)
        return dest.path
    }

    private fun open(url: URL, hops: Int): HttpURLConnection {
        val conn = url.openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        val code = conn.responseCode
        if (code in 300..399 && hops < 6) {
            val next = URL(url, conn.getHeaderField("Location"))
            conn.disconnect()
            return open(next, hops + 1)
        }
        if (code != 200) throw RuntimeException("HTTP $code")
        return conn
    }

    /** Import a ggml .bin model the user already has on the phone. */
    fun import(c: Context, uri: Uri): String {
        val dest = File(modelsDir(c), "imported-model.bin")
        c.contentResolver.openInputStream(uri)!!.use { i -> dest.outputStream().use { o -> i.copyTo(o, 256 * 1024) } }
        Prefs.setModelPath(c, dest.path)
        return dest.path
    }
}
