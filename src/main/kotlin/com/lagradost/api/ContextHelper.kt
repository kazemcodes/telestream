package com.lagradost.api

import android.content.Context
import java.lang.ref.WeakReference

private var contextRef: WeakReference<Context>? = null

fun getContext(): Any? = contextRef?.get()

fun setContext(context: Any?) {
    contextRef = (context as? Context)?.let { WeakReference(it) }
}
