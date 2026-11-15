package com.rawag.voicebubble

import android.content.Context
import java.io.File

const val BUNDLED_MODEL = "ggml-small-q5_1.bin"

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("vb", Context.MODE_PRIVATE)

    /** "ar", "en" or "auto" */
    fun lang(c: Context): String = sp(c).getString("lang", "ar")!!
    fun setLang(c: Context, l: String) = sp(c).edit().putString("lang", l).apply()

    fun autoTranscribe(c: Context): Boolean = sp(c).getBoolean("auto", true)
    fun setAutoTranscribe(c: Context, v: Boolean) = sp(c).edit().putBoolean("auto", v).apply()

    fun bubbleX(c: Context): Int = sp(c).getInt("bx", 0)
    fun bubbleY(c: Context): Int = sp(c).getInt("by", 400)
    fun setBubblePos(c: Context, x: Int, y: Int) = sp(c).edit().putInt("bx", x).putInt("by", y).apply()
}

/**
 * The Whisper Small model ships inside the APK (assets/, stored uncompressed).
 * whisper.cpp needs a real file path, so on first launch it is copied once to internal storage.
 */
object ModelManager {

    private fun target(c: Context) = File(File(c.filesDir, "models").apply { mkdirs() }, BUNDLED_MODEL)

    private fun assetSize(c: Context): Long =
        try { c.assets.openFd(BUNDLED_MODEL).use { it.length } } catch (e: Exception) { -1L }

    fun isReady(c: Context): Boolean {
        val f = target(c)
        return f.exists() && f.length() > 1_000_000 && (assetSize(c).let { it <= 0 || it == f.length() })
    }

    @Synchronized
    fun ensureReady(c: Context, onProgress: (Int) -> Unit = {}): String {
        val dest = target(c)
        if (isReady(c)) return dest.path
        val total = assetSize(c).coerceAtLeast(1)
        val tmp = File(dest.path + ".part")
        c.assets.open(BUNDLED_MODEL).use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(1 shl 20)
                var done = 0L
                var last = -1
                while (true) {
                    val r = input.read(buf)
                    if (r < 0) break
                    out.write(buf, 0, r)
                    done += r
                    val p = (done * 100 / total).toInt()
                    if (p != last) { last = p; onProgress(p) }
                }
            }
        }
        dest.delete()
        tmp.renameTo(dest)
        return dest.path
    }
}
