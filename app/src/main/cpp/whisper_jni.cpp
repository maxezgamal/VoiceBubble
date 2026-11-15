#include <jni.h>
#include <string>
#include <vector>
#include "whisper.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "VoiceBubble", __VA_ARGS__)
#else
#include <cstdio>
#define LOGI(...) do { fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); } while (0)
#endif

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_rawag_voicebubble_WhisperLib_initContext(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    LOGI("model loaded: %s -> %p", path, (void *) ctx);
    env->ReleaseStringUTFChars(modelPath, path);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_rawag_voicebubble_WhisperLib_freeContext(JNIEnv *, jobject, jlong ptr) {
    auto *ctx = reinterpret_cast<whisper_context *>(ptr);
    if (ctx) whisper_free(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_rawag_voicebubble_WhisperLib_transcribe(JNIEnv *env, jobject, jlong ptr,
                                                  jfloatArray samples, jstring lang,
                                                  jint threads, jstring prompt) {
    auto *ctx = reinterpret_cast<whisper_context *>(ptr);
    if (!ctx) return env->NewStringUTF("");

    const char *langC = env->GetStringUTFChars(lang, nullptr);
    const char *promptC = env->GetStringUTFChars(prompt, nullptr);
    std::string langS(langC), promptS(promptC);
    env->ReleaseStringUTFChars(lang, langC);
    env->ReleaseStringUTFChars(prompt, promptC);

    jsize n = env->GetArrayLength(samples);
    std::vector<float> pcm(n);
    env->GetFloatArrayRegion(samples, 0, n, pcm.data());

    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.print_realtime   = false;
    p.print_progress   = false;
    p.print_timestamps = false;
    p.print_special    = false;
    p.translate        = false;
    p.no_timestamps    = true;
    p.n_threads        = threads;
    p.language         = langS.c_str();   // "ar", "en" or "auto"
    p.detect_language  = false;
    p.suppress_blank   = true;
    if (!promptS.empty()) p.initial_prompt = promptS.c_str();

    std::string out;
    if (whisper_full(ctx, p, pcm.data(), (int) pcm.size()) != 0) {
        LOGI("whisper_full failed");
        return env->NewStringUTF("");
    }
    const int segs = whisper_full_n_segments(ctx);
    for (int i = 0; i < segs; ++i) {
        out += whisper_full_get_segment_text(ctx, i);
    }
    return env->NewStringUTF(out.c_str());
}

}
