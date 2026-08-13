// JNI bridge to whisper.cpp.
//
// The important design decision here is that we return per-token probabilities
// alongside the text. Those probabilities are the only genuinely audio-derived
// signal we have about pronunciation clarity: a word the acoustic model was
// unsure of is a word that came out unclearly. Everything the app says about
// pronunciation in free conversation traces back to the numbers produced here.

#include <jni.h>
#include <android/log.h>

#include <string>
#include <vector>

#include "whisper.h"

#define LOG_TAG "SpeakWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

void append_escaped(std::string &out, const char *text) {
    if (text == nullptr) return;
    for (const char *p = text; *p != '\0'; ++p) {
        const unsigned char c = static_cast<unsigned char>(*p);
        switch (c) {
            case '"':  out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n";  break;
            case '\r': out += "\\r";  break;
            case '\t': out += "\\t";  break;
            default:
                if (c < 0x20) {
                    char buf[7];
                    snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                } else {
                    out += static_cast<char>(c);
                }
        }
    }
}

void append_float(std::string &out, float value) {
    char buf[32];
    snprintf(buf, sizeof(buf), "%.5f", value);
    out += buf;
}

void append_i64(std::string &out, int64_t value) {
    out += std::to_string(value);
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_speak_app_stt_WhisperBridge_nativeInit(JNIEnv *env, jobject, jstring j_model_path) {
    const char *model_path = env->GetStringUTFChars(j_model_path, nullptr);
    if (model_path == nullptr) return 0;

    whisper_context_params cparams = whisper_context_default_params();
    // Mid-range Android phones have no dependable GPU/NPU path for ggml, and
    // falling back to CPU explicitly is far more predictable than probing.
    cparams.use_gpu = false;
    cparams.flash_attn = false;

    whisper_context *ctx = whisper_init_from_file_with_params(model_path, cparams);
    if (ctx == nullptr) {
        LOGE("failed to load whisper model from %s", model_path);
    } else {
        LOGI("whisper model loaded: %s", model_path);
    }
    env->ReleaseStringUTFChars(j_model_path, model_path);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_speak_app_stt_WhisperBridge_nativeRelease(JNIEnv *, jobject, jlong handle) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx != nullptr) whisper_free(ctx);
}

/**
 * Transcribes 16 kHz mono float PCM and returns a JSON document containing the
 * text plus per-token probabilities and timings.
 */
JNIEXPORT jstring JNICALL
Java_com_speak_app_stt_WhisperBridge_nativeTranscribe(
        JNIEnv *env,
        jobject,
        jlong handle,
        jfloatArray j_samples,
        jint n_threads) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx == nullptr) return env->NewStringUTF("");

    const jsize n_samples = env->GetArrayLength(j_samples);
    if (n_samples <= 0) return env->NewStringUTF("");

    std::vector<float> samples(static_cast<size_t>(n_samples));
    env->GetFloatArrayRegion(j_samples, 0, n_samples, samples.data());

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads         = n_threads > 0 ? n_threads : 4;
    params.language          = "en";
    params.translate         = false;
    params.detect_language   = false;
    // A learner's errors must survive transcription. Greedy decoding at
    // temperature 0 with no prior context and no initial prompt gives whisper
    // the least room to "helpfully" rewrite ungrammatical speech into
    // grammatical text, which would hide the very mistakes we exist to catch.
    params.temperature       = 0.0f;
    params.temperature_inc   = 0.0f;
    params.no_context        = true;
    params.initial_prompt    = nullptr;
    params.suppress_blank    = false;
    params.suppress_nst      = true;
    params.single_segment    = false;
    params.token_timestamps  = true;
    params.print_progress    = false;
    params.print_realtime    = false;
    params.print_timestamps  = false;
    params.print_special     = false;

    if (whisper_full(ctx, params, samples.data(), static_cast<int>(n_samples)) != 0) {
        LOGE("whisper_full failed");
        return env->NewStringUTF("");
    }

    const whisper_token eot = whisper_token_eot(ctx);

    std::string json;
    json.reserve(4096);
    json += "{\"segments\":[";

    const int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; ++i) {
        if (i > 0) json += ',';
        json += "{\"t0\":";
        append_i64(json, whisper_full_get_segment_t0(ctx, i));
        json += ",\"t1\":";
        append_i64(json, whisper_full_get_segment_t1(ctx, i));
        json += ",\"text\":\"";
        append_escaped(json, whisper_full_get_segment_text(ctx, i));
        json += "\",\"tokens\":[";

        const int n_tokens = whisper_full_n_tokens(ctx, i);
        bool wrote_token = false;
        for (int j = 0; j < n_tokens; ++j) {
            const whisper_token_data data = whisper_full_get_token_data(ctx, i, j);
            // Skip special/timestamp tokens: they carry no pronunciation signal.
            if (data.id >= eot) continue;
            const char *text = whisper_full_get_token_text(ctx, i, j);
            if (text == nullptr) continue;

            if (wrote_token) json += ',';
            wrote_token = true;
            json += "{\"text\":\"";
            append_escaped(json, text);
            json += "\",\"p\":";
            append_float(json, data.p);
            json += ",\"plog\":";
            append_float(json, data.plog);
            json += ",\"t0\":";
            append_i64(json, data.t0);
            json += ",\"t1\":";
            append_i64(json, data.t1);
            json += ",\"vlen\":";
            append_float(json, data.vlen);
            json += '}';
        }
        json += "]}";
    }
    json += "]}";

    return env->NewStringUTF(json.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_speak_app_stt_WhisperBridge_nativeSystemInfo(JNIEnv *env, jobject) {
    return env->NewStringUTF(whisper_print_system_info());
}

} // extern "C"
