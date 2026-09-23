package com.lagradost.api

import android.content.Context

private var currentContext: Context? = null

actual fun getContext(): Any? = currentContext
actual fun setContext(context: Any?) {
    if (context is Context) {
        currentContext = context
    }
}
