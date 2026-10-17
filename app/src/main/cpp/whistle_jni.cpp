// JNI bridge: com.oro.app.voice.WhistleNative <-> needle.h (Cactus Needle engine).
#include <jni.h>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>
#include "needle.h"

// libneedle.a was built with a newer libc++ than some NDKs ship: it calls
// std::__hash_memory (added in LLVM 19's libc++). Provide it, weak, so a
// newer NDK's own definition wins when present. Any good hash works: it only
// backs std::hash<std::string> inside the engine.
namespace std { inline namespace __ndk1 {
__attribute__((weak)) size_t __hash_memory(const void* data, size_t len) noexcept {
    const unsigned char* p = static_cast<const unsigned char*>(data);
    unsigned long long h = 1469598103934665603ULL;  // FNV-1a 64
    for (size_t i = 0; i < len; ++i) { h ^= p[i]; h *= 1099511628211ULL; }
    return static_cast<size_t>(h ^ (h >> 32));
}
} }

namespace {
std::mutex gLock;            // the engine is process-global and not thread-safe
unsigned char* gModel = nullptr;  // kept alive for the process: the engine may reference it
unsigned long long gModelSize = 0;

std::string lastError() {
    const char* e = needle_last_error();
    return e ? std::string(e) : std::string("unknown error");
}

jstring callOut(JNIEnv* env, int rc, const std::vector<char>& out) {
    if (rc < 0) {
        std::string msg = "{\"error\":\"" + lastError() + "\"}";
        return env->NewStringUTF(msg.c_str());
    }
    return env->NewStringUTF(out.data());
}
}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_oro_app_voice_WhistleNative_load(JNIEnv* env, jobject, jbyteArray model) {
    std::lock_guard<std::mutex> guard(gLock);
    if (gModel != nullptr) return nullptr;  // already loaded
    jsize n = env->GetArrayLength(model);
    auto* buf = static_cast<unsigned char*>(std::malloc(static_cast<size_t>(n)));
    if (!buf) return env->NewStringUTF("out of memory");
    env->GetByteArrayRegion(model, 0, n, reinterpret_cast<jbyte*>(buf));
    int rc = needle_load(buf, static_cast<unsigned long long>(n));
    if (rc < 0) {
        std::free(buf);
        return env->NewStringUTF(lastError().c_str());
    }
    gModel = buf;
    gModelSize = static_cast<unsigned long long>(n);
    return nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_oro_app_voice_WhistleNative_isLoaded(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> guard(gLock);
    return gModel != nullptr && (needle_models() & NEEDLE_SPEECH) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_oro_app_voice_WhistleNative_transcribe(JNIEnv* env, jobject, jfloatArray pcm, jstring language, jstring keywords) {
    std::lock_guard<std::mutex> guard(gLock);
    jsize n = env->GetArrayLength(pcm);
    std::vector<float> samples(static_cast<size_t>(n));
    env->GetFloatArrayRegion(pcm, 0, n, samples.data());
    const char* lang = language ? env->GetStringUTFChars(language, nullptr) : nullptr;
    const char* kw = keywords ? env->GetStringUTFChars(keywords, nullptr) : nullptr;
    std::vector<char> out(64 * 1024, 0);
    int rc = needle_transcribe(samples.data(), static_cast<int>(n), lang, kw, 0, out.data(), static_cast<int>(out.size()) - 1);
    if (lang) env->ReleaseStringUTFChars(language, lang);
    if (kw) env->ReleaseStringUTFChars(keywords, kw);
    return callOut(env, rc, out);
}

// Live transcription: feed ~1 s of 16 kHz PCM at a time; returns JSON with the words
// this call committed and the unconfirmed tail ("pending").
extern "C" JNIEXPORT jstring JNICALL
Java_com_oro_app_voice_WhistleNative_streamProcess(JNIEnv* env, jobject, jfloatArray pcm, jstring language, jstring keywords) {
    std::lock_guard<std::mutex> guard(gLock);
    jsize n = env->GetArrayLength(pcm);
    std::vector<float> samples(static_cast<size_t>(n));
    env->GetFloatArrayRegion(pcm, 0, n, samples.data());
    const char* lang = language ? env->GetStringUTFChars(language, nullptr) : nullptr;
    const char* kw = keywords ? env->GetStringUTFChars(keywords, nullptr) : nullptr;
    std::vector<char> out(64 * 1024, 0);
    int rc = needle_stream_transcribe_process(samples.data(), static_cast<int>(n), lang, kw, out.data(), static_cast<int>(out.size()) - 1);
    if (lang) env->ReleaseStringUTFChars(language, lang);
    if (kw) env->ReleaseStringUTFChars(keywords, kw);
    return callOut(env, rc, out);
}

/* Ends the stream: the tail becomes committed words. */
extern "C" JNIEXPORT jstring JNICALL
Java_com_oro_app_voice_WhistleNative_streamStop(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> guard(gLock);
    std::vector<char> out(64 * 1024, 0);
    int rc = needle_stream_transcribe_stop(out.data(), static_cast<int>(out.size()) - 1);
    return callOut(env, rc, out);
}
