package com.lagradost.api

actual object Log {
    actual fun d(tag: String, message: String) {
        android.util.Log.d(tag, message)
    }

    actual fun i(tag: String, message: String) {
        android.util.Log.i(tag, message)
    }

    actual fun w(tag: String, message: String) {
        android.util.Log.w(tag, message)
    }

    actual fun e(tag: String, message: String) {
        android.util.Log.e(tag, message)
    }
}