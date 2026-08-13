// JNI bridge to llama.cpp.
//
// Generation is exposed one token at a time rather than as a single blocking
// call. That matters a great deal here: a 1B model on a mid-range CPU produces
// roughly 8-12 tokens a second, so waiting for a complete reply costs 10-20
// seconds of silence. Streaming lets the Kotlin layer hand each finished
// sentence to the text-to-speech engine while the model is still working on the
// next one, which is the difference between a usable tutor and an unusable one.

#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <cstring>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "SpeakLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct SpeakLlamaSession {
    llama_model   *model  = nullptr;
    llama_context *ctx    = nullptr;
    const llama_vocab *vocab = nullptr;
    llama_sampler *sampler = nullptr;

    int  n_generated = 0;
    int  max_tokens  = 256;
    bool finished    = true;
    std::atomic<bool> cancelled{false};
};

std::atomic<bool> g_backend_ready{false};

void ensure_backend() {
    bool expected = false;
    if (g_backend_ready.compare_exchange_strong(expected, true)) {
        llama_backend_init();
        llama_log_set([](ggml_log_level level, const char *text, void *) {
            if (text == nullptr) return;
            const int prio = level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR
                           : level == GGML_LOG_LEVEL_WARN  ? ANDROID_LOG_WARN
                                                           : ANDROID_LOG_DEBUG;
            __android_log_write(prio, LOG_TAG, text);
        }, nullptr);
    }
}

void destroy_sampler(SpeakLlamaSession *session) {
    if (session->sampler != nullptr) {
        llama_sampler_free(session->sampler);
        session->sampler = nullptr;
    }
}

std::string token_to_piece(const llama_vocab *vocab, llama_token token) {
    char buf[256];
    const int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, /*special=*/false);
    if (n < 0) {
        std::vector<char> big(static_cast<size_t>(-n) + 1);
        const int written = llama_token_to_piece(vocab, token, big.data(),
                                                 static_cast<int32_t>(big.size()), 0, false);
        if (written <= 0) return {};
        return std::string(big.data(), static_cast<size_t>(written));
    }
    if (n == 0) return {};
    return std::string(buf, static_cast<size_t>(n));
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_speak_app_llm_LlamaBridge_nativeLoad(
        JNIEnv *env,
        jobject,
        jstring j_model_path,
        jint n_ctx,
        jint n_threads) {
    ensure_backend();

    const char *model_path = env->GetStringUTFChars(j_model_path, nullptr);
    if (model_path == nullptr) return 0;

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;   // CPU only: no dependable Android GPU backend
    // mmap without mlock: the kernel can evict model pages under memory
    // pressure instead of the whole app being killed, which matters when a
    // 768 MB model shares a 6 GB phone with everything else the user has open.
    mparams.load_mode    = LLAMA_LOAD_MODE_MMAP;

    llama_model *model = llama_model_load_from_file(model_path, mparams);
    env->ReleaseStringUTFChars(j_model_path, model_path);

    if (model == nullptr) {
        LOGE("failed to load model");
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx         = n_ctx > 0 ? static_cast<uint32_t>(n_ctx) : 2048;
    cparams.n_batch       = 256;
    cparams.n_ubatch      = 256;
    cparams.n_threads     = n_threads > 0 ? n_threads : 4;
    cparams.n_threads_batch = n_threads > 0 ? n_threads : 4;

    llama_context *ctx = llama_init_from_model(model, cparams);
    if (ctx == nullptr) {
        LOGE("failed to create context");
        llama_model_free(model);
        return 0;
    }

    auto *session = new SpeakLlamaSession();
    session->model = model;
    session->ctx   = ctx;
    session->vocab = llama_model_get_vocab(model);
    LOGI("model ready, n_ctx=%u threads=%d", cparams.n_ctx, cparams.n_threads);
    return reinterpret_cast<jlong>(session);
}

JNIEXPORT void JNICALL
Java_com_speak_app_llm_LlamaBridge_nativeRelease(JNIEnv *, jobject, jlong handle) {
    auto *session = reinterpret_cast<SpeakLlamaSession *>(handle);
    if (session == nullptr) return;
    destroy_sampler(session);
    if (session->ctx != nullptr)   llama_free(session->ctx);
    if (session->model != nullptr) llama_model_free(session->model);
    delete session;
}

/**
 * Tokenises and evaluates the prompt, then arms the sampler. Returns the number
 * of prompt tokens consumed, or a negative value on failure.
 *
 * When `j_grammar` is non-null it is treated as GBNF and installed as a grammar
 * sampler. The tutor uses that to make malformed JSON structurally impossible
 * rather than merely unlikely, which a 1B model cannot otherwise be trusted on.
 */
JNIEXPORT jint JNICALL
Java_com_speak_app_llm_LlamaBridge_nativeBeginTurn(
        JNIEnv *env,
        jobject,
        jlong handle,
        jstring j_prompt,
        jint max_tokens,
        jfloat temperature,
        jfloat top_p,
        jint top_k,
        jint seed,
        jstring j_grammar) {
    auto *session = reinterpret_cast<SpeakLlamaSession *>(handle);
    if (session == nullptr || session->ctx == nullptr) return -1;

    const char *prompt = env->GetStringUTFChars(j_prompt, nullptr);
    if (prompt == nullptr) return -1;
    const auto prompt_len = static_cast<int32_t>(strlen(prompt));

    // Start each turn from a clean slate. Conversation history is re-supplied in
    // the prompt itself, which keeps behaviour reproducible and avoids the KV
    // cache silently drifting out of sync with what we think the model has seen.
    llama_memory_clear(llama_get_memory(session->ctx), true);

    int32_t n_tokens_estimate = prompt_len + 8;
    std::vector<llama_token> tokens(static_cast<size_t>(n_tokens_estimate));
    int32_t n_tokens = llama_tokenize(session->vocab, prompt, prompt_len,
                                      tokens.data(), n_tokens_estimate,
                                      /*add_special=*/true, /*parse_special=*/true);
    if (n_tokens < 0) {
        tokens.resize(static_cast<size_t>(-n_tokens));
        n_tokens = llama_tokenize(session->vocab, prompt, prompt_len,
                                  tokens.data(), static_cast<int32_t>(tokens.size()),
                                  true, true);
    }
    env->ReleaseStringUTFChars(j_prompt, prompt);

    if (n_tokens <= 0) {
        LOGE("tokenisation produced no tokens");
        return -1;
    }
    tokens.resize(static_cast<size_t>(n_tokens));

    const int n_ctx = static_cast<int>(llama_n_ctx(session->ctx));
    if (n_tokens + max_tokens > n_ctx) {
        // Drop the oldest prompt tokens rather than failing outright: a long
        // conversation should degrade by forgetting, not by erroring.
        const int overflow = n_tokens + max_tokens - n_ctx;
        if (overflow >= n_tokens) {
            LOGE("prompt cannot fit in context");
            return -1;
        }
        tokens.erase(tokens.begin(), tokens.begin() + overflow);
        n_tokens -= overflow;
        LOGI("trimmed %d prompt tokens to fit context", overflow);
    }

    const int n_batch = static_cast<int>(llama_n_batch(session->ctx));
    for (int offset = 0; offset < n_tokens; offset += n_batch) {
        const int chunk = std::min(n_batch, n_tokens - offset);
        llama_batch batch = llama_batch_get_one(tokens.data() + offset, chunk);
        if (llama_decode(session->ctx, batch) != 0) {
            LOGE("llama_decode failed during prompt evaluation");
            return -1;
        }
    }

    destroy_sampler(session);
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    session->sampler = llama_sampler_chain_init(sparams);

    if (j_grammar != nullptr) {
        const char *grammar = env->GetStringUTFChars(j_grammar, nullptr);
        if (grammar != nullptr) {
            llama_sampler *g = llama_sampler_init_grammar(session->vocab, grammar, "root");
            if (g != nullptr) {
                llama_sampler_chain_add(session->sampler, g);
            } else {
                LOGE("grammar failed to compile; continuing unconstrained");
            }
            env->ReleaseStringUTFChars(j_grammar, grammar);
        }
    }

    if (temperature <= 0.0f) {
        llama_sampler_chain_add(session->sampler, llama_sampler_init_greedy());
    } else {
        if (top_k > 0) llama_sampler_chain_add(session->sampler, llama_sampler_init_top_k(top_k));
        if (top_p < 1.0f) llama_sampler_chain_add(session->sampler, llama_sampler_init_top_p(top_p, 1));
        llama_sampler_chain_add(session->sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(session->sampler,
                                llama_sampler_init_dist(static_cast<uint32_t>(seed)));
    }

    session->n_generated = 0;
    session->max_tokens  = max_tokens > 0 ? max_tokens : 256;
    session->finished    = false;
    session->cancelled.store(false);
    return n_tokens;
}

/**
 * Produces the next fragment of text, or null once the turn is complete.
 */
JNIEXPORT jstring JNICALL
Java_com_speak_app_llm_LlamaBridge_nativeNextPiece(JNIEnv *env, jobject, jlong handle) {
    auto *session = reinterpret_cast<SpeakLlamaSession *>(handle);
    if (session == nullptr || session->finished || session->sampler == nullptr) return nullptr;

    if (session->cancelled.load() || session->n_generated >= session->max_tokens) {
        session->finished = true;
        return nullptr;
    }

    const llama_token token = llama_sampler_sample(session->sampler, session->ctx, -1);
    if (llama_vocab_is_eog(session->vocab, token)) {
        session->finished = true;
        return nullptr;
    }

    const std::string piece = token_to_piece(session->vocab, token);
    session->n_generated++;

    llama_batch batch = llama_batch_get_one(const_cast<llama_token *>(&token), 1);
    if (llama_decode(session->ctx, batch) != 0) {
        LOGE("llama_decode failed during generation");
        session->finished = true;
        return piece.empty() ? nullptr : env->NewStringUTF(piece.c_str());
    }

    return env->NewStringUTF(piece.c_str());
}

JNIEXPORT void JNICALL
Java_com_speak_app_llm_LlamaBridge_nativeCancel(JNIEnv *, jobject, jlong handle) {
    auto *session = reinterpret_cast<SpeakLlamaSession *>(handle);
    if (session != nullptr) session->cancelled.store(true);
}

JNIEXPORT jboolean JNICALL
Java_com_speak_app_llm_LlamaBridge_nativeIsFinished(JNIEnv *, jobject, jlong handle) {
    auto *session = reinterpret_cast<SpeakLlamaSession *>(handle);
    return (session == nullptr || session->finished) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_speak_app_llm_LlamaBridge_nativeSystemInfo(JNIEnv *env, jobject) {
    ensure_backend();
    return env->NewStringUTF(llama_print_system_info());
}

} // extern "C"
