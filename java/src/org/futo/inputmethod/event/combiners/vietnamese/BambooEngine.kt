package org.futo.inputmethod.event.combiners.vietnamese

import android.util.Log

object BambooEngine {
    private const val TAG = "BambooEngine"

    // Mirrors JniUtils.java:26-32: the Go engine ships as its own library
    // (libbamboo.so, built by native/bamboo-go/build.sh) so it can carry its
    // own JNI_OnLoad with the method table registered below.
    init {
        try {
            System.loadLibrary("bamboo")
        } catch (ule: UnsatisfiedLinkError) {
            Log.e(TAG, "Could not load native library bamboo", ule)
        }
    }

    external fun nativeNew(method: Int): Long
    external fun nativeProcess(handle: Long, cp: Int): String
    external fun nativeOutput(handle: Long): String
    external fun nativeRemoveLastChar(handle: Long)
    external fun nativeRemoveLastOutputChar(handle: Long)
    external fun nativeReset(handle: Long)
    external fun nativeFree(handle: Long)
}
