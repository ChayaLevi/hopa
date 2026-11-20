#include <jni.h>

#include <atomic>
#include <string>
#include <vector>

#include "llama.h"

namespace {

struct Session {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    const llama_vocab * vocab = nullptr;
};

// One generation at a time: the engine runs on a single session, and the cancel
// flag is what the chat's stop button reaches.
std::atomic<bool> g_cancel{false};

std::string to_string(JNIEnv * env, jstring value) {
    if (value == nullptr) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string out(chars);
    env->ReleaseStringUTFChars(value, chars);
    return out;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_hopa_localmodel_LlamaEngine_nativeLoad(
        JNIEnv * env,
        jobject /* thiz */,
        jstring path,
        jint contextTokens,
        jint threads) {
    const std::string modelPath = to_string(env, path);
    if (modelPath.empty()) return 0;

    llama_backend_init();

    llama_model_params modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = 0;  // CPU only: the NPU is not wired up yet.
    llama_model * model = llama_model_load_from_file(modelPath.c_str(), modelParams);
    if (model == nullptr) return 0;

    llama_context_params contextParams = llama_context_default_params();
    contextParams.n_ctx = static_cast<uint32_t>(contextTokens);
    contextParams.n_batch = 512;
    contextParams.n_threads = threads;
    contextParams.n_threads_batch = threads;
    llama_context * context = llama_init_from_model(model, contextParams);
    if (context == nullptr) {
        llama_model_free(model);
        return 0;
    }

    auto * session = new Session{model, context, llama_model_get_vocab(model)};
    return reinterpret_cast<jlong>(session);
}

JNIEXPORT void JNICALL
Java_com_hopa_localmodel_LlamaEngine_nativeFree(JNIEnv * /* env */, jobject /* thiz */, jlong handle) {
    auto * session = reinterpret_cast<Session *>(handle);
    if (session == nullptr) return;
    llama_free(session->context);
    llama_model_free(session->model);
    delete session;
}

JNIEXPORT void JNICALL
Java_com_hopa_localmodel_LlamaEngine_nativeCancel(JNIEnv * /* env */, jobject /* thiz */) {
    g_cancel.store(true);
}

JNIEXPORT jstring JNICALL
Java_com_hopa_localmodel_LlamaEngine_nativeComplete(
        JNIEnv * env,
        jobject /* thiz */,
        jlong handle,
        jstring prompt,
        jint maxTokens,
        jstring grammar) {
    auto * session = reinterpret_cast<Session *>(handle);
    if (session == nullptr) return env->NewStringUTF("");

    g_cancel.store(false);
    // A completion is stateless: the previous turn's tokens must not leak in.
    llama_memory_clear(llama_get_memory(session->context), true);

    const std::string text = to_string(env, prompt);
    if (text.empty()) return env->NewStringUTF("");

    // Tokenise. A negative result says how large the buffer must be.
    int32_t capacity = static_cast<int32_t>(text.size()) + 8;
    std::vector<llama_token> tokens(static_cast<size_t>(capacity));
    int32_t count = llama_tokenize(
            session->vocab, text.c_str(), static_cast<int32_t>(text.size()),
            tokens.data(), capacity, true, true);
    if (count < 0) {
        capacity = -count;
        tokens.resize(static_cast<size_t>(capacity));
        count = llama_tokenize(
                session->vocab, text.c_str(), static_cast<int32_t>(text.size()),
                tokens.data(), capacity, true, true);
    }
    if (count <= 0) return env->NewStringUTF("");
    tokens.resize(static_cast<size_t>(count));

    llama_sampler_chain_params samplerParams = llama_sampler_chain_default_params();
    llama_sampler * chain = llama_sampler_chain_init(samplerParams);
    const std::string grammarText = to_string(env, grammar);
    if (!grammarText.empty()) {
        llama_sampler * constrained =
                llama_sampler_init_grammar(session->vocab, grammarText.c_str(), "root");
        if (constrained != nullptr) llama_sampler_chain_add(chain, constrained);
    }
    llama_sampler_chain_add(chain, llama_sampler_init_greedy());

    llama_batch batch = llama_batch_get_one(tokens.data(), count);
    if (llama_decode(session->context, batch) != 0) {
        llama_sampler_free(chain);
        return env->NewStringUTF("");
    }

    std::string out;
    for (int32_t generated = 0; generated < maxTokens && !g_cancel.load(); ++generated) {
        llama_token token = llama_sampler_sample(chain, session->context, -1);
        if (llama_vocab_is_eog(session->vocab, token)) break;
        char piece[256];
        const int32_t length =
                llama_token_to_piece(session->vocab, token, piece, sizeof(piece), 0, true);
        if (length > 0) out.append(piece, static_cast<size_t>(length));
        llama_batch next = llama_batch_get_one(&token, 1);
        if (llama_decode(session->context, next) != 0) break;
    }

    llama_sampler_free(chain);
    return env->NewStringUTF(out.c_str());
}

}  // extern "C"
