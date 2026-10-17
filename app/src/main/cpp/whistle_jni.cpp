// JNI bridge: com.oro.app.voice.WhistleNative <-> needle.h (Cactus Needle engine).
#include <jni.h>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>
#include "needle.h"

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
