package com.rawag.voicebubble

object WhisperLib {
    init { System.loadLibrary("voicebubble") }

    external fun initContext(modelPath: String): Long
    external fun freeContext(ptr: Long)
    external fun transcribe(ptr: Long, samples: FloatArray, lang: String, threads: Int, prompt: String): String
}
