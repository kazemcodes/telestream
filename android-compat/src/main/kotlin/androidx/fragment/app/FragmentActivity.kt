package androidx.fragment.app

import android.app.Activity
import android.content.Context

open class FragmentActivity : Activity() {
    private val fragmentManager = FragmentManager()

    open fun getSupportFragmentManager(): FragmentManager = fragmentManager
    open fun isFinishing(): Boolean = false
    open fun isDestroyed(): Boolean = false
}
