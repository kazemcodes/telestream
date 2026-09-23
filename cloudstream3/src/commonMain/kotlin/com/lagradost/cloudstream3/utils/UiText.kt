package com.lagradost.cloudstream3.utils

import android.content.Context
import com.lagradost.cloudstream3.mvvm.logError

sealed class UiText {
    companion object {
        const val TAG = "UiText"
    }

    data class DynamicString(val value: String) : UiText() {
        override fun toString(): String = value

        override fun equals(other: Any?): Boolean {
            if (other !is DynamicString) return false
            return this.value == other.value
        }

        override fun hashCode(): Int = value.hashCode()
    }

    class StringResource(
        val resId: Int,
        val args: List<Any>
    ) : UiText() {
        override fun toString(): String =
            "resId = $resId\nargs = ${args.toList().map { "(${it::class} = $it)" }}"
        override fun equals(other: Any?): Boolean {
            if (other !is StringResource) return false
            return this.resId == other.resId && this.args == other.args
        }

        override fun hashCode(): Int {
            var result = resId
            result = 31 * result + args.hashCode()
            return result
        }
    }

    fun asStringNull(context: Context?): String? {
        try {
            return asString(context ?: return null)
        } catch (e: Exception) {
            logError(e)
            return null
        }
    }

    fun asString(context: Context): String {
        return when (this) {
            is DynamicString -> value
            is StringResource -> {
                val str = context.getString(resId)
                if (args.isEmpty()) {
                    str
                } else {
                    str.format(*args.map {
                        when (it) {
                            is UiText -> it.asString(context)
                            else -> it
                        }
                    }.toTypedArray())
                }
            }
        }
    }
}

fun txt(value: String): UiText {
    return UiText.DynamicString(value)
}

@JvmName("txtNull")
fun txt(value: String?): UiText? {
    return UiText.DynamicString(value ?: return null)
}

fun txt(resId: Int, vararg args: Any): UiText {
    return UiText.StringResource(resId, args.toList())
}

@JvmName("txtNull")
fun txt(resId: Int?, vararg args: Any?): UiText? {
    if (resId == null || args.any { it == null }) {
        return null
    }
    return UiText.StringResource(resId, args.filterNotNull().toList())
}
