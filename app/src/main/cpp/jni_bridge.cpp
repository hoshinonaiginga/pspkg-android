// SPDX-License-Identifier: GPL-2.0-or-later
// JNI bridge between the Android UI and the PKG extraction core.
#include <jni.h>
#include <android/log.h>
#include <exception>
#include <filesystem>
#include <string>

#include "pkg_tool/lib.h"

#define LOG_TAG "pspkg"

namespace {

std::string ToStd(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(s, chars);
    return out;
}

jstring ToJ(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());
}

} // namespace

extern "C" {

// Returns String[5] = { error, titleId, pkgSize, fileCount, contentFlags }.
// error is "" on success.
JNIEXPORT jobjectArray JNICALL
Java_io_github_hoshinonaiginga_pspkg_NativeLib_readMeta(JNIEnv* env, jclass, jstring jpath) {
    std::string error;
    PkgMetadata meta;
    try {
        const std::filesystem::path path(ToStd(env, jpath));
        if (auto err = ReadPkgMetadata(path, meta)) error = *err;
    } catch (const std::exception& e) {
        error = e.what();
    } catch (...) {
        error = "Unknown native error";
    }
    if (!error.empty()) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "readMeta: %s", error.c_str());

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray arr = env->NewObjectArray(5, stringClass, nullptr);
    env->SetObjectArrayElement(arr, 0, ToJ(env, error));
    env->SetObjectArrayElement(arr, 1, ToJ(env, meta.title_id));
    env->SetObjectArrayElement(arr, 2, ToJ(env, std::to_string(meta.pkg_size)));
    env->SetObjectArrayElement(arr, 3, ToJ(env, std::to_string(meta.file_count)));
    env->SetObjectArrayElement(arr, 4, ToJ(env, std::to_string(meta.flags)));
    return arr;
}

// Extracts pkg into outDir. Calls listener.onProgress(int done, int total) per file.
// Returns null on success, otherwise an error message.
JNIEXPORT jstring JNICALL
Java_io_github_hoshinonaiginga_pspkg_NativeLib_extract(JNIEnv* env, jclass, jstring jpkg,
                                                        jstring jout, jobject listener) {
    std::string error;
    jmethodID onProgress = nullptr;
    if (listener) {
        jclass cls = env->GetObjectClass(listener);
        onProgress = env->GetMethodID(cls, "onProgress", "(II)V");
    }
    try {
        const std::filesystem::path pkg(ToStd(env, jpkg));
        const std::filesystem::path out(ToStd(env, jout));
        auto err = ExtractPkg(pkg, out, {}, [&](uint32_t done, uint32_t total) {
            if (onProgress) {
                env->CallVoidMethod(listener, onProgress, static_cast<jint>(done),
                                    static_cast<jint>(total));
                if (env->ExceptionCheck()) env->ExceptionClear();
            }
        });
        if (err) error = *err;
    } catch (const std::exception& e) {
        error = e.what();
    } catch (...) {
        error = "Unknown native error";
    }
    if (error.empty()) return nullptr;
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "extract: %s", error.c_str());
    return ToJ(env, error);
}

} // extern "C"
