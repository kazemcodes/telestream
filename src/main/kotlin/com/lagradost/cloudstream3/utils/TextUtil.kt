package com.lagradost.cloudstream3.utils

import android.content.Context

sealed class UiText {
    data class DynamicString(val value: String) : UiText() {
        override fun toString(): String = value
    }

    class StringResource(
        val resId: Int,
        val args: List<Any> = emptyList()
    ) : UiText() {
        override fun toString(): String = "StringResource($resId)"
    }

    fun asStringNull(context: Context?): String? = when (this) {
        is DynamicString -> value
        is StringResource -> "Res_$resId"
    }

    fun asString(context: Context): String = asStringNull(context) ?: ""
}

fun txt(string: String) = UiText.DynamicString(string)
fun txt(resId: Int, vararg args: Any) = UiText.StringResource(resId, args.toList())
