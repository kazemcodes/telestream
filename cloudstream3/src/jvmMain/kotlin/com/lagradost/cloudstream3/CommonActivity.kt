package com.lagradost.cloudstream3

import android.app.Activity
import androidx.fragment.app.FragmentActivity

open class HeadlessFragmentActivity : FragmentActivity()

/**
 * Compatibility stub for CommonActivity accessed by plugins (e.g. CF dialogs, donation dialogues, toasts).
 */
object CommonActivity {
    @JvmField
    var activity: Activity? = HeadlessFragmentActivity()

    fun showToast(message: String, duration: Int? = null) {
        // No-op in headless JVM bot environment
    }

    fun showToast(activity: Activity?, message: String, duration: Int? = null) {
        // No-op in headless JVM bot environment
    }
}
