package com.lagradost.cloudstream3.plugins

import android.content.Context
import kotlin.Throws

abstract class Plugin : BasePlugin() {
    @Throws(Throwable::class)
    open fun load(context: Context) {
        load()
    }

    var openSettings: ((context: Context) -> Unit)? = null
}
