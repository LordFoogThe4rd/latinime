//go:build android

/*
 * JNI glue for the Go bamboo-core engine (native/bamboo-go/bamboo_android).
 *
 * Compiled by cgo into libbamboo.so (see ../build.sh) alongside the Go shim
 * (engine.go). JNI_OnLoad registers the exact 7-method table the Kotlin
 * object org/futo/inputmethod/event/combiners/vietnamese/BambooEngine expects
 * (nativeNew/nativeProcess/nativeOutput/nativeRemoveLastChar/
 * nativeRemoveLastOutputChar/nativeReset/nativeFree). Kotlin signatures are
 * unchanged from the Rust-era wrapper.
 *
 * The Go functions below are the cgo-exported entry points of engine.go;
 * handles are opaque int64 registry ids. String returns are malloc-allocated
 * by the cgo bridge and must be freed with free().
 */

#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

#include <android/log.h>

#define LOG_TAG "BambooEngine"

/* cgo-exported Go entry points (engine.go). */
extern int64_t EngineNew(int64_t method);
extern char *EngineProcess(int64_t handle, int32_t cp);
extern char *EngineOutput(int64_t handle);
extern void EngineRemoveLastChar(int64_t handle);
extern void EngineRemoveLastOutputChar(int64_t handle);
extern void EngineReset(int64_t handle);
extern void EngineFree(int64_t handle);

static void log_bad_handle(const char *fn) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s called with invalid handle", fn);
}

static jstring engine_output(JNIEnv *env, const char *fn, char *out) {
    if (out == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s returned NULL", fn);
        return (*env)->NewStringUTF(env, "");
    }
    jstring result = (*env)->NewStringUTF(env, out);
    free(out);
    return result;
}

static jlong BambooEngine_new(JNIEnv *env, jclass clazz, jint method) {
    (void)env;
    (void)clazz;
    int64_t handle = EngineNew((int64_t)method);
    if (handle == 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "EngineNew(%d) failed", (int)method);
    }
    return (jlong)handle;
}

static jstring BambooEngine_process(JNIEnv *env, jclass clazz, jlong handle, jint cp) {
    (void)clazz;
    if (handle == 0) {
        log_bad_handle("process");
        return (*env)->NewStringUTF(env, "");
    }
    return engine_output(env, "process", EngineProcess((int64_t)handle, (int32_t)cp));
}

static jstring BambooEngine_output(JNIEnv *env, jclass clazz, jlong handle) {
    (void)clazz;
    if (handle == 0) {
        log_bad_handle("output");
        return (*env)->NewStringUTF(env, "");
    }
    return engine_output(env, "output", EngineOutput((int64_t)handle));
}

static void BambooEngine_removeLastChar(JNIEnv *env, jclass clazz, jlong handle) {
    (void)env;
    (void)clazz;
    if (handle == 0) {
        log_bad_handle("removeLastChar");
        return;
    }
    EngineRemoveLastChar((int64_t)handle);
}

static void BambooEngine_removeLastOutputChar(JNIEnv *env, jclass clazz, jlong handle) {
    (void)env;
    (void)clazz;
    if (handle == 0) {
        log_bad_handle("removeLastOutputChar");
        return;
    }
    EngineRemoveLastOutputChar((int64_t)handle);
}

static void BambooEngine_reset(JNIEnv *env, jclass clazz, jlong handle) {
    (void)env;
    (void)clazz;
    if (handle == 0) {
        log_bad_handle("reset");
        return;
    }
    EngineReset((int64_t)handle);
}

static void BambooEngine_free(JNIEnv *env, jclass clazz, jlong handle) {
    (void)env;
    (void)clazz;
    if (handle == 0) {
        return;
    }
    EngineFree((int64_t)handle);
}

static const JNINativeMethod sMethods[] = {
        {"nativeNew", "(I)J", (void *)BambooEngine_new},
        {"nativeProcess", "(JI)Ljava/lang/String;", (void *)BambooEngine_process},
        {"nativeOutput", "(J)Ljava/lang/String;", (void *)BambooEngine_output},
        {"nativeRemoveLastChar", "(J)V", (void *)BambooEngine_removeLastChar},
        {"nativeRemoveLastOutputChar", "(J)V", (void *)BambooEngine_removeLastOutputChar},
        {"nativeReset", "(J)V", (void *)BambooEngine_reset},
        {"nativeFree", "(J)V", (void *)BambooEngine_free},
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "GetEnv failed");
        return JNI_ERR;
    }
    jclass clazz = (*env)->FindClass(
            env, "org/futo/inputmethod/event/combiners/vietnamese/BambooEngine");
    if (clazz == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "FindClass failed");
        return JNI_ERR;
    }
    jint rc = (*env)->RegisterNatives(env, clazz, sMethods,
                                      (jint)(sizeof(sMethods) / sizeof(sMethods[0])));
    (*env)->DeleteLocalRef(env, clazz);
    if (rc != JNI_OK) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "RegisterNatives failed (%d)", (int)rc);
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}
