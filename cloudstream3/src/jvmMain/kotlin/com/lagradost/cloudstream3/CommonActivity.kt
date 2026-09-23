package com.lagradost.cloudstream3

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.UiText
import org.slf4j.LoggerFactory

open class HeadlessFragmentActivity : AppCompatActivity()

/**
 * CommonActivity implementation for CloudStream plugins (toasts, dialogs, activity references).
 * Exactly matches CloudStream object member structure without @JvmStatic to prevent IncompatibleClassChangeError.
 */
object CommonActivity {
    private val logger = LoggerFactory.getLogger(CommonActivity::class.java)

    const val TAG = "COMPACT"

    var activity: Activity? = HeadlessFragmentActivity()

    fun setActivityInstance(newActivity: Activity?) {
        activity = newActivity
    }

    fun showToast(message: String?, duration: Int? = null) {
        if (message != null) {
            logger.info("[$TAG] Toast: $message")
        }
    }

    fun showToast(resId: Int, duration: Int? = null) {
        logger.info("[$TAG] Toast (resId): $resId")
    }

    fun showToast(message: UiText?, duration: Int? = null) {
        val str = message?.asStringNull(activity) ?: message?.toString()
        if (str != null) {
            logger.info("[$TAG] Toast: $str")
        }
    }

    fun showToast(act: Activity?, text: UiText, duration: Int? = null) {
        val str = text.asStringNull(act) ?: text.toString()
        logger.info("[$TAG] Toast: $str")
    }

    fun showToast(act: Activity?, resId: Int, duration: Int? = null) {
        logger.info("[$TAG] Toast (resId): $resId")
    }

    fun showToast(act: Activity?, message: String?, duration: Int? = null) {
        if (message != null) {
            logger.info("[$TAG] Toast: $message")
        }
    }

    var isPipDesired: Boolean = false

    var isInPIPMode: Boolean = false

    val screenWidth: Int get() = 1920

    val screenHeight: Int get() = 1080

    val screenWidthWithOrientation: Int get() = 1920

    val screenHeightWithOrientation: Int get() = 1080

    @JvmField
    val onColorSelectedEvent = Event<Pair<Int, Int>>()

    @JvmField
    val onDialogDismissedEvent = Event<Int>()

    var appliedTheme: Int = 0

    var appliedColor: Int = 0
}
