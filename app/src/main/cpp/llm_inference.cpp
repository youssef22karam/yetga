#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <atomic>

#include "llama.h"

#define TAG "JarvisLLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct LlamaHandle {
    llama_model   *model   = nullptr;
    llama_context *ctx     = nullptr;
    std::atomic<bool> stop_flag{false};
};

static std::string jstring2str(JNIEnv *env, jstring jstr) {
    if (!jstr) return {};
    const char *chars = env->GetStringUTFChars(jstr, nullptr);
    std::string s(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return s;
}

// Helper: tokenize using llama.cpp b4618 new API
// llama_tokenize now takes llama_vocab* + raw buffer, returns int32_t count
static std::vector<llama_token> do_tokenize(
        const llama_model *model,
        const std::string &text,
        bool add_special,
        bool parse_special) {

    const llama_vocab *vocab = llama_model_get_vocab(model);

    // Probe required size
    int n = llama_tokenize(vocab,
                           text.c_str(), (int32_t)text.size(),
                           nullptr, 0,
                           add_special, parse_special);
    int needed = (n < 0) ? -n : n;
    if (needed == 0) return {};

    std::vector<llama_token> buf(needed + 4);
    int r = llama_tokenize(vocab,
                           text.c_str(), (int32_t)text.size(),
                           buf.data(), (int32_t)buf.size(),
                           add_special, parse_special);
    if (r < 0) return {};
    buf.resize(r);
    return buf;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_jarvis_app_engine_LlamaEngine_nativeLoadModel(
        JNIEnv *env, jobject,
        jstring modelPath, jint nThreads, jint nCtx) {

    std::string path = jstring2str(env, modelPath);
    LOGI("Loading model: %s  threads=%d  ctx=%d", path.c_str(), nThreads, nCtx);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;

    llama_model *model = llama_model_load_from_file(path.c_str(), mparams);
    if (!model) { LOGE("Load failed: %s", path.c_str()); return 0L; }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx           = static_cast<uint32_t>(nCtx);
    cparams.n_threads       = static_cast<uint32_t>(nThreads);
    cparams.n_threads_batch = static_cast<uint32_t>(nThreads);

    llama_context *ctx = llama_init_from_model(model, cparams);
    if (!ctx) { llama_model_free(model); LOGE("Context failed"); return 0L; }

    auto *h = new LlamaHandle{model, ctx};
    LOGI("Loaded OK handle=%p", (void*)h);
    return reinterpret_cast<jlong>(h);
}

JNIEXPORT jint JNICALL
Java_com_jarvis_app_engine_LlamaEngine_nativeGenerate(
        JNIEnv *env, jobject,
        jlong handlePtr, jstring jPrompt,
        jint maxTokens, jfloat temperature,
        jobject callback) {

    auto *h = reinterpret_cast<LlamaHandle *>(handlePtr);
    if (!h || !h->ctx) return -1;
    h->stop_flag.store(false);

    std::string prompt = jstring2str(env, jPrompt);
    const llama_vocab *vocab = llama_model_get_vocab(h->model);

    // Tokenise with new API
    bool add_bos = llama_vocab_get_add_bos(vocab);
    std::vector<llama_token> tokens = do_tokenize(h->model, prompt, add_bos, true);
    if (tokens.empty()) return -2;

    llama_kv_cache_clear(h->ctx);

    llama_batch batch = llama_batch_get_one(tokens.data(), (int)tokens.size());
    if (llama_decode(h->ctx, batch) != 0) { LOGE("Prefill failed"); return -3; }

    // Sampler
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler *smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    jclass    cbCls   = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cbCls, "onToken", "(Ljava/lang/String;)Z");

    int n_gen = 0;
    char pbuf[256];

    while (n_gen < maxTokens) {
        if (h->stop_flag.load()) break;

        llama_token id = llama_sampler_sample(smpl, h->ctx, -1);

        // EOG check with new API
        if (llama_vocab_is_eog(vocab, id)) break;

        // Detokenise with new API
        int len = llama_token_to_piece(vocab, id, pbuf, (int)sizeof(pbuf)-1, 0, true);
        if (len <= 0) break;
        pbuf[len] = '\0';

        jstring piece = env->NewStringUTF(pbuf);
        jboolean cont = env->CallBooleanMethod(callback, onToken, piece);
        env->DeleteLocalRef(piece);
        if (!cont || env->ExceptionCheck()) break;

        llama_batch nb = llama_batch_get_one(&id, 1);
        if (llama_decode(h->ctx, nb) != 0) break;
        ++n_gen;
    }

    llama_sampler_free(smpl);
    return n_gen;
}

JNIEXPORT void JNICALL
Java_com_jarvis_app_engine_LlamaEngine_nativeStopGeneration(
        JNIEnv *, jobject, jlong handlePtr) {
    if (auto *h = reinterpret_cast<LlamaHandle *>(handlePtr))
        h->stop_flag.store(true);
}

JNIEXPORT void JNICALL
Java_com_jarvis_app_engine_LlamaEngine_nativeFreeModel(
        JNIEnv *, jobject, jlong handlePtr) {
    auto *h = reinterpret_cast<LlamaHandle *>(handlePtr);
    if (!h) return;
    h->stop_flag.store(true);
    if (h->ctx)   llama_free(h->ctx);
    if (h->model) llama_model_free(h->model);
    delete h;
    LOGI("Model freed");
}

JNIEXPORT jstring JNICALL
Java_com_jarvis_app_engine_LlamaEngine_nativeGetModelDescription(
        JNIEnv *env, jobject, jlong handlePtr) {
    auto *h = reinterpret_cast<LlamaHandle *>(handlePtr);
    if (!h || !h->model) return env->NewStringUTF("(no model)");
    char buf[512];
    llama_model_desc(h->model, buf, sizeof(buf));
    return env->NewStringUTF(buf);
}

} // extern "C"
