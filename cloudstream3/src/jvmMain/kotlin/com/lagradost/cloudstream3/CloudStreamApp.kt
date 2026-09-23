package com.lagradost.cloudstream3

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SimulatedContext
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.removeKeys
import com.lagradost.cloudstream3.utils.DataStore.setKey

/**
 * CloudStreamApp compatibility stub for plugins that access application context
 * (such as phisher98's DonationManager or preference helpers).
 */
open class CloudStreamApp {
    companion object {
        @JvmField
        var context: Context? = SimulatedContext()

        @JvmStatic
        fun getContext(): Context? = context

        @JvmStatic
        fun setContext(ctx: Context?) {
            context = ctx
        }

        tailrec fun Context.getActivity(): Activity? {
            return when (this) {
                is Activity -> this
                is ContextWrapper -> baseContext.getActivity()
                else -> null
            }
        }

        @JvmStatic
        fun <T : Any> getKeyClass(path: String, valueType: Class<T>): T? {
            return context?.getKey(path, valueType)
        }

        @JvmStatic
        fun <T : Any> setKeyClass(path: String, value: T) {
            context?.setKey(path, value)
        }

        @JvmStatic
        fun removeKeys(folder: String): Int? {
            return context?.removeKeys(folder)
        }

        @JvmStatic
        fun <T> setKey(path: String, value: T) {
            context?.setKey(path, value)
        }

        @JvmStatic
        fun <T> setKey(folder: String, path: String, value: T) {
            context?.setKey(folder, path, value)
        }

        inline fun <reified T : Any> getKey(path: String, defVal: T?): T? {
            return context?.getKey(path, defVal)
        }

        inline fun <reified T : Any> getKey(path: String): T? {
            return context?.getKey(path)
        }

        inline fun <reified T : Any> getKey(folder: String, path: String): T? {
            return context?.getKey(folder, path)
        }

        inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? {
            return context?.getKey(folder, path, defVal)
        }

        @JvmStatic
        fun getKeys(folder: String): List<String>? {
            return context?.getKeys(folder)
        }

        @JvmStatic
        fun removeKey(folder: String, path: String) {
            context?.removeKey(folder, path)
        }

        @JvmStatic
        fun removeKey(path: String) {
            context?.removeKey(path)
        }
    }
}
