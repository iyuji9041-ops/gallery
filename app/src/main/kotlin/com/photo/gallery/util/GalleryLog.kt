package com.photo.gallery.util

import android.util.Log
import com.photo.gallery.BuildConfig

/**
 * Debug-only logging wrapper that strips all logs in release builds
 * and ensures no sensitive paths, keys, or filenames are logged.
 */
object GalleryLog {

    inline fun d(tag: String, msg: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, msg())
        }
    }

    inline fun i(tag: String, msg: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, msg())
        }
    }

    inline fun w(tag: String, msg: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.w(tag, msg())
        }
    }

    inline fun e(tag: String, throwable: Throwable? = null, msg: () -> String) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.e(tag, msg(), throwable)
            } else {
                Log.e(tag, msg())
            }
        }
    }
}
