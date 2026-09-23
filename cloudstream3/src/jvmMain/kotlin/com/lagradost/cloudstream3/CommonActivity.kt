package com.lagradost.cloudstream3

import android.app.Activity

/**
 * Compatibility stub for CommonActivity accessed by plugins (e.g. donation dialogues, toasts).
 */
object CommonActivity {
    var activity: Activity? = null

    fun showToast(message: String, duration: Int? = null) {
        // No-op in headless JVM bot environment
    }

    fun showToast(activity: Activity?, message: String, duration: Int? = null) {
        // No-op in headless JVM bot environment
    }
}
