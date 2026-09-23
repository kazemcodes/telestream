package com.lagradost.cloudstream3.plugins

import android.content.Context
import com.lagradost.api.Log
import kotlin.Throws

abstract class Plugin : BasePlugin() {
    /**
     * Called when your Plugin is loaded
     * @param context Context
     */
    @Throws(Throwable::class)
    open fun load(context: Context) {
        // If not overridden by an extension then try the cross-platform load()
        load()
    }

    var openSettings: ((context: Context) -> Unit)? = null
}
