package com.telestream.repo

/**
 * Resilient, null-tolerant string helpers used by CloudStreamClassLoader
 * to safely intercept calls made by dynamic CloudStream plugins (like SuperStream)
 * when upstream APIs return null or missing fields in models marked as non-null.
 */
object PluginBytecodeHelper {

    @JvmStatic
    fun `split$default`(
        text: CharSequence?,
        delimiters: Array<String>,
        ignoreCase: Boolean,
        limit: Int,
        flags: Int,
        obj: Any?
    ): List<String> {
        if (text.isNullOrEmpty()) return emptyList()
        return text.split(*delimiters, ignoreCase = ignoreCase, limit = limit)
    }

    @JvmStatic
    fun `split$default`(
        text: CharSequence?,
        delimiters: CharArray,
        ignoreCase: Boolean,
        limit: Int,
        flags: Int,
        obj: Any?
    ): List<String> {
        if (text.isNullOrEmpty()) return emptyList()
        return text.split(*delimiters, ignoreCase = ignoreCase, limit = limit)
    }

    @JvmStatic
    fun split(
        text: CharSequence?,
        delimiters: Array<String>,
        ignoreCase: Boolean,
        limit: Int
    ): List<String> {
        if (text.isNullOrEmpty()) return emptyList()
        return text.split(*delimiters, ignoreCase = ignoreCase, limit = limit)
    }

    @JvmStatic
    fun split(
        text: CharSequence?,
        delimiters: CharArray,
        ignoreCase: Boolean,
        limit: Int
    ): List<String> {
        if (text.isNullOrEmpty()) return emptyList()
        return text.split(*delimiters, ignoreCase = ignoreCase, limit = limit)
    }

    @JvmStatic
    fun `contains$default`(
        text: CharSequence?,
        other: CharSequence,
        ignoreCase: Boolean,
        flags: Int,
        obj: Any?
    ): Boolean {
        if (text == null) return false
        return text.contains(other, ignoreCase = ignoreCase)
    }

    @JvmStatic
    fun contains(
        text: CharSequence?,
        other: CharSequence,
        ignoreCase: Boolean
    ): Boolean {
        if (text == null) return false
        return text.contains(other, ignoreCase = ignoreCase)
    }

    @JvmStatic
    fun `startsWith$default`(
        text: String?,
        prefix: String,
        ignoreCase: Boolean,
        flags: Int,
        obj: Any?
    ): Boolean {
        if (text == null) return false
        return text.startsWith(prefix, ignoreCase = ignoreCase)
    }

    @JvmStatic
    fun `substringAfterLast$default`(
        text: String?,
        delimiter: String,
        missingDelimiterValue: String,
        flags: Int,
        obj: Any?
    ): String {
        if (text == null) return missingDelimiterValue
        return text.substringAfterLast(delimiter, missingDelimiterValue)
    }

    @JvmStatic
    fun `substringBefore$default`(
        text: String?,
        delimiter: String,
        missingDelimiterValue: String,
        flags: Int,
        obj: Any?
    ): String {
        if (text == null) return missingDelimiterValue
        return text.substringBefore(delimiter, missingDelimiterValue)
    }

    @JvmStatic
    fun `substringAfter$default`(
        text: String?,
        delimiter: String,
        missingDelimiterValue: String,
        flags: Int,
        obj: Any?
    ): String {
        if (text == null) return missingDelimiterValue
        return text.substringAfter(delimiter, missingDelimiterValue)
    }

    @JvmStatic
    fun `replace$default`(
        text: String?,
        oldValue: String,
        newValue: String,
        ignoreCase: Boolean,
        flags: Int,
        obj: Any?
    ): String {
        if (text == null) return ""
        return text.replace(oldValue, newValue, ignoreCase = ignoreCase)
    }

    @JvmStatic
    fun toIntOrNull(text: String?): Int? {
        if (text == null) return null
        return text.toIntOrNull()
    }
}
