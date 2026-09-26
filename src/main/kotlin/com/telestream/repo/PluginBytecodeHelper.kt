package com.telestream.repo

import java.lang.reflect.Modifier
import java.util.regex.Pattern
import org.objectweb.asm.Type

/**
 * Resilient, null-tolerant drop-in replacements for the `kotlin.text.StringsKt*` helpers that
 * dynamically loaded CloudStream plugins (like SuperStream / StreamPlay) call from their own
 * bytecode. [CloudStreamClassLoader] redirects those call sites here.
 *
 * Two rules govern every function in this object:
 *
 * 1. **No parameter may be declared non-null.** Plugins compiled from Kotlin get an
 *    `Intrinsics.checkNotNullParameter` guard when they enter a *normal* (non-`$default`)
 *    function. Because the compiler passes `null` placeholders plus a `mask` to the synthetic
 *    `...$default` bridges, a non-null declaration makes that guard fire and the call dies with
 *    `NullPointerException: Parameter specified as non-null is null`. Every parameter here is
 *    therefore nullable, so no guard is generated.
 *
 * 2. **Every `...$default` bridge must reproduce the real mask semantics.** A `$default` bridge
 *    receives the caller-supplied arguments *plus* a `mask` whose bit `n` means "the argument at
 *    parameter index `n` was omitted, substitute the declared default". The real stdlib bridges
 *    perform that substitution; a naive copy that just forwards its arguments returns wrong
 *    values (or throws). The handling below mirrors `kotlin.text.StringsKt` exactly.
 *
 * For a non-null receiver every function delegates straight to the Kotlin stdlib, so the result
 * is identical to running the plugin inside the real CloudStream app. Only a `null` receiver
 * deviates: instead of throwing (which the main app would do) a safe, non-null value is returned,
 * so an upstream API returning `null` for a field the plugin models as non-null can never crash.
 */
object PluginBytecodeHelper {

    // ---------------------------------------------------------------------------------------
    // `$default` mask helpers. Bit `n` of `mask` corresponds to declared parameter index `n`;
    // the extension receiver sits below index 0 and is therefore never masked.
    // ---------------------------------------------------------------------------------------

    /** Applies the `default` of a `Boolean` parameter, which is always `false` in the stdlib. */
    private fun boolArg(mask: Int, index: Int, value: Boolean): Boolean =
        if ((mask shr index) and 1 == 1) false else value

    /** Applies the `default` of an `Int` parameter that defaults to `0`. */
    private fun intArg(mask: Int, index: Int, value: Int): Int =
        if ((mask shr index) and 1 == 1) 0 else value

    /** Applies the `default` of a `Char` parameter that defaults to `' '` (padStart / padEnd). */
    private fun charArg(mask: Int, index: Int, value: Char): Char =
        if ((mask shr index) and 1 == 1) ' ' else value

    // ---------------------------------------------------------------------------------------
    // substringBefore / substringAfter / substringBeforeLast / substringAfterLast
    //
    // `missingDelimiterValue` defaults to the receiver (`missingDelimiterValue = this`), so a
    // `$default` call must copy the receiver into that slot. That copy is exactly what used to
    // blow up here, so it is resolved explicitly instead of via a non-null parameter.
    // ---------------------------------------------------------------------------------------

    private fun resolvedMissing(text: String?, mask: Int, missingDelimiterValue: String?): String? =
        if ((mask and 2) != 0) text else missingDelimiterValue

    @JvmStatic
    fun substringBefore(text: String?, delimiter: String?, missingDelimiterValue: String?): String {
        if (text == null) return missingDelimiterValue ?: ""
        val d = delimiter ?: return text
        return text.substringBefore(d, missingDelimiterValue ?: text)
    }

    @JvmStatic
    fun substringBefore(text: String?, delimiter: Char, missingDelimiterValue: String?): String {
        if (text == null) return missingDelimiterValue ?: ""
        return text.substringBefore(delimiter, missingDelimiterValue ?: text)
    }

    @JvmStatic
    fun `substringBefore$default`(
        text: String?,
        delimiter: String?,
        missingDelimiterValue: String?,
        mask: Int,
        marker: Any?
    ): String = substringBefore(text, delimiter, resolvedMissing(text, mask, missingDelimiterValue))

    @JvmStatic
    fun `substringBefore$default`(
        text: String?,
        delimiter: Char,
        missingDelimiterValue: String?,
        mask: Int,
        marker: Any?
    ): String = substringBefore(text, delimiter, resolvedMissing(text, mask, missingDelimiterValue))

    @JvmStatic
    fun substringAfter(text: String?, delimiter: String?, missingDelimiterValue: String?): String {
        if (text == null) return missingDelimiterValue ?: ""
        val d = delimiter ?: return text
        return text.substringAfter(d, missingDelimiterValue ?: text)
    }

    @JvmStatic
    fun substringAfter(text: String?, delimiter: Char, missingDelimiterValue: String?): String {
        if (text == null) return missingDelimiterValue ?: ""
        return text.substringAfter(delimiter, missingDelimiterValue ?: text)
    }

    @JvmStatic
    fun `substringAfter$default`(
        text: String?,
        delimiter: String?,
        missingDelimiterValue: String?,
        mask: Int,
        marker: Any?
    ): String = substringAfter(text, delimiter, resolvedMissing(text, mask, missingDelimiterValue))

    @JvmStatic
    fun `substringAfter$default`(
        text: String?,
        delimiter: Char,
        missingDelimiterValue: String?,
        mask: Int,
        marker: Any?
    ): String = substringAfter(text, delimiter, resolvedMissing(text, mask, missingDelimiterValue))
    @JvmStatic
    fun substringBeforeLast(text: String?, delimiter: String?, missingDelimiterValue: String?): String {
        if (text == null) return missingDelimiterValue ?: ""
        val d = delimiter ?: return text
        return text.substringBeforeLast(d, missingDelimiterValue ?: text)
    }

    @JvmStatic
    fun substringBeforeLast(text: String?, delimiter: Char, missingDelimiterValue: String?): String {
        if (text == null) return missingDelimiterValue ?: ""
        return text.substringBeforeLast(delimiter, missingDelimiterValue ?: text)
    }

    @JvmStatic
    fun `substringBeforeLast$default`(
        text: String?,
        delimiter: String?,
        missingDelimiterValue: String?,
        mask: Int,
        marker: Any?
    ): String = substringBeforeLast(text, delimiter, resolvedMissing(text, mask, missingDelimiterValue))

    @JvmStatic
    fun `substringBeforeLast$default`(
        text: String?,
        delimiter: Char,
        missingDelimiterValue: String?,
        mask: Int,
        marker: Any?
    ): String = substringBeforeLast(text, delimiter, resolvedMissing(text, mask, missingDelimiterValue))

    @JvmStatic
    fun substringAfterLast(text: String?, delimiter: String?, missingDelimiterValue: String?): String {
        if (text == null) return missingDelimiterValue ?: ""
        val d = delimiter ?: return text
        return text.substringAfterLast(d, missingDelimiterValue ?: text)
    }

    @JvmStatic
    fun substringAfterLast(text: String?, delimiter: Char, missingDelimiterValue: String?): String {
        if (text == null) return missingDelimiterValue ?: ""
        return text.substringAfterLast(delimiter, missingDelimiterValue ?: text)
    }

    @JvmStatic
    fun `substringAfterLast$default`(
        text: String?,
        delimiter: String?,
        missingDelimiterValue: String?,
        mask: Int,
        marker: Any?
    ): String = substringAfterLast(text, delimiter, resolvedMissing(text, mask, missingDelimiterValue))

    @JvmStatic
    fun `substringAfterLast$default`(
        text: String?,
        delimiter: Char,
        missingDelimiterValue: String?,
        mask: Int,
        marker: Any?
    ): String = substringAfterLast(text, delimiter, resolvedMissing(text, mask, missingDelimiterValue))

    // ---------------------------------------------------------------------------------------
    // split / splitToSequence
    //
    // `ignoreCase` is parameter index 1 and `limit` is index 2, so the mask bits are 2 and 4.
    // A null receiver yields an empty result rather than the stdlib's NPE; a non-null receiver -
    // including the empty string - is delegated untouched so behaviour matches the main app.
    // ---------------------------------------------------------------------------------------

    @JvmStatic
    fun split(text: CharSequence?, delimiters: Array<String>?, ignoreCase: Boolean, limit: Int): List<String> {
        if (text == null) return emptyList()
        val d = delimiters ?: return listOf(text.toString())
        return text.split(*d, ignoreCase = ignoreCase, limit = limit)
    }

    @JvmStatic
    fun split(text: CharSequence?, delimiters: CharArray?, ignoreCase: Boolean, limit: Int): List<String> {
        if (text == null) return emptyList()
        val d = delimiters ?: return listOf(text.toString())
        return text.split(*d, ignoreCase = ignoreCase, limit = limit)
    }

    @JvmStatic
    fun split(text: CharSequence?, pattern: Pattern?, limit: Int): List<String> {
        if (text == null) return emptyList()
        val p = pattern ?: return listOf(text.toString())
        return p.split(text, limit).toList()
    }

    @JvmStatic
    fun `split$default`(
        text: CharSequence?,
        delimiters: Array<String>?,
        ignoreCase: Boolean,
        limit: Int,
        mask: Int,
        marker: Any?
    ): List<String> = split(text, delimiters, boolArg(mask, 1, ignoreCase), intArg(mask, 2, limit))

    @JvmStatic
    fun `split$default`(
        text: CharSequence?,
        delimiters: CharArray?,
        ignoreCase: Boolean,
        limit: Int,
        mask: Int,
        marker: Any?
    ): List<String> = split(text, delimiters, boolArg(mask, 1, ignoreCase), intArg(mask, 2, limit))

    @JvmStatic
    fun splitToSequence(
        text: CharSequence?,
        delimiters: Array<String>?,
        ignoreCase: Boolean,
        limit: Int
    ): Sequence<String> {
        if (text == null) return emptySequence()
        val d = delimiters ?: return sequenceOf(text.toString())
        return text.splitToSequence(*d, ignoreCase = ignoreCase, limit = limit)
    }

    @JvmStatic
    fun splitToSequence(
        text: CharSequence?,
        delimiters: CharArray?,
        ignoreCase: Boolean,
        limit: Int
    ): Sequence<String> {
        if (text == null) return emptySequence()
        val d = delimiters ?: return sequenceOf(text.toString())
        return text.splitToSequence(*d, ignoreCase = ignoreCase, limit = limit)
    }

    @JvmStatic
    fun `splitToSequence$default`(
        text: CharSequence?,
        delimiters: Array<String>?,
        ignoreCase: Boolean,
        limit: Int,
        mask: Int,
        marker: Any?
    ): Sequence<String> =
        splitToSequence(text, delimiters, boolArg(mask, 1, ignoreCase), intArg(mask, 2, limit))

    @JvmStatic
    fun `splitToSequence$default`(
        text: CharSequence?,
        delimiters: CharArray?,
        ignoreCase: Boolean,
        limit: Int,
        mask: Int,
        marker: Any?
    ): Sequence<String> =
        splitToSequence(text, delimiters, boolArg(mask, 1, ignoreCase), intArg(mask, 2, limit))
    // ---------------------------------------------------------------------------------------
    // contains / startsWith / endsWith
    //
    // The stdlib declares these on `CharSequence`, so the descriptors are
    // `(Ljava/lang/CharSequence;Ljava/lang/CharSequence;ZILjava/lang/Object;)Z` and friends.
    // `ignoreCase` is parameter index 1, so the mask bit is 2. A null receiver yields `false`.
    // ---------------------------------------------------------------------------------------

    @JvmStatic
    fun contains(text: CharSequence?, other: CharSequence?, ignoreCase: Boolean): Boolean {
        if (text == null || other == null) return false
        return text.contains(other, ignoreCase = ignoreCase)
    }

    @JvmStatic
    fun contains(text: CharSequence?, other: Char, ignoreCase: Boolean): Boolean {
        if (text == null) return false
        return text.contains(other, ignoreCase = ignoreCase)
    }

    @JvmStatic
    fun `contains$default`(
        text: CharSequence?,
        other: CharSequence?,
        ignoreCase: Boolean,
        mask: Int,
        marker: Any?
    ): Boolean = contains(text, other, boolArg(mask, 1, ignoreCase))

    @JvmStatic
    fun `contains$default`(
        text: CharSequence?,
        other: Char,
        ignoreCase: Boolean,
        mask: Int,
        marker: Any?
    ): Boolean = contains(text, other, boolArg(mask, 1, ignoreCase))

    @JvmStatic
    fun startsWith(text: CharSequence?, prefix: CharSequence?, ignoreCase: Boolean): Boolean {
        if (text == null || prefix == null) return false
        return text.startsWith(prefix, ignoreCase = ignoreCase)
    }

    @JvmStatic
    fun startsWith(text: CharSequence?, prefix: Char, ignoreCase: Boolean): Boolean {
        if (text == null) return false
        return text.startsWith(prefix, ignoreCase = ignoreCase)
    }

    @JvmStatic
    fun `startsWith$default`(
        text: CharSequence?,
        prefix: CharSequence?,
        ignoreCase: Boolean,
        mask: Int,
        marker: Any?
    ): Boolean = startsWith(text, prefix, boolArg(mask, 1, ignoreCase))

    @JvmStatic
    fun `startsWith$default`(
        text: CharSequence?,
        prefix: Char,
        ignoreCase: Boolean,
        mask: Int,
        marker: Any?
    ): Boolean = startsWith(text, prefix, boolArg(mask, 1, ignoreCase))

    /**
     * Deprecated stdlib overload `startsWith(prefix, ignoreIndex, ignoreCase)`.
     *
     * Only `ignoreCase` (parameter index 2, so mask bit 4) is defaulted; `ignoreIndex` is always
     * supplied by the caller and shifts where the comparison starts, so it has to be honoured.
     */
    @JvmStatic
    fun `startsWith$default`(
        text: CharSequence?,
        prefix: CharSequence?,
        ignoreIndex: Int,
        ignoreCase: Boolean,
        mask: Int,
        marker: Any?
    ): Boolean {
        if (text == null || prefix == null) return false
        val ic = boolArg(mask, 2, ignoreCase)
        if (ignoreIndex < 0 || ignoreIndex > text.length - prefix.length) return false
        return text.toString().regionMatches(ignoreIndex, prefix.toString(), 0, prefix.length, ic)
    }

    @JvmStatic
    fun endsWith(text: CharSequence?, suffix: CharSequence?, ignoreCase: Boolean): Boolean {
        if (text == null || suffix == null) return false
        return text.endsWith(suffix, ignoreCase = ignoreCase)
    }

    @JvmStatic
    fun endsWith(text: CharSequence?, suffix: Char, ignoreCase: Boolean): Boolean {
        if (text == null) return false
        return text.endsWith(suffix, ignoreCase = ignoreCase)
    }

    @JvmStatic
    fun `endsWith$default`(
        text: CharSequence?,
        suffix: CharSequence?,
        ignoreCase: Boolean,
        mask: Int,
        marker: Any?
    ): Boolean = endsWith(text, suffix, boolArg(mask, 1, ignoreCase))

    @JvmStatic
    fun `endsWith$default`(
        text: CharSequence?,
        suffix: Char,
        ignoreCase: Boolean,
        mask: Int,
        marker: Any?
    ): Boolean = endsWith(text, suffix, boolArg(mask, 1, ignoreCase))

    // ---------------------------------------------------------------------------------------
    // indexOf / lastIndexOf
    //
    // `startIndex` is parameter index 1 and `ignoreCase` is index 3, so the mask bits are 2 and
    // 8. `lastIndexOf` defaults `startIndex` to the receiver length rather than 0. A null receiver
    // yields -1 (the stdlib's "not found" answer) instead of throwing.
    // ---------------------------------------------------------------------------------------

    @JvmStatic
    fun indexOf(text: CharSequence?, other: String?, startIndex: Int, ignoreCase: Boolean): Int {
        if (text == null || other == null) return -1
        return text.indexOf(other, startIndex, ignoreCase = ignoreCase)
    }

    @JvmStatic
    fun indexOf(text: CharSequence?, other: Char, startIndex: Int, ignoreCase: Boolean): Int {
        if (text == null) return -1
        return text.indexOf(other, startIndex, ignoreCase = ignoreCase)
    }

    @JvmStatic
    fun `indexOf$default`(
        text: CharSequence?,
        other: String?,
        startIndex: Int,
        ignoreCase: Boolean,
        mask: Int,
        marker: Any?
    ): Int = indexOf(text, other, intArg(mask, 1, startIndex), boolArg(mask, 3, ignoreCase))

    @JvmStatic
    fun `indexOf$default`(
        text: CharSequence?,
        other: Char,
        startIndex: Int,
        ignoreCase: Boolean,
        mask: Int,
        marker: Any?
    ): Int = indexOf(text, other, intArg(mask, 1, startIndex), boolArg(mask, 3, ignoreCase))

    @JvmStatic
    fun lastIndexOf(text: CharSequence?, other: String?, startIndex: Int, ignoreCase: Boolean): Int {
        if (text == null || other == null) return -1
        return text.lastIndexOf(other, startIndex, ignoreCase = ignoreCase)
    }

    @JvmStatic
    fun lastIndexOf(text: CharSequence?, other: Char, startIndex: Int, ignoreCase: Boolean): Int {
        if (text == null) return -1
        return text.lastIndexOf(other, startIndex, ignoreCase = ignoreCase)
    }

    @JvmStatic
    fun `lastIndexOf$default`(
        text: CharSequence?,
        other: String?,
        startIndex: Int,
        ignoreCase: Boolean,
        mask: Int,
        marker: Any?
    ): Int {
        val idx = if ((mask and 2) != 0) (text?.length ?: 0) else startIndex
        return lastIndexOf(text, other, idx, boolArg(mask, 3, ignoreCase))
    }

    @JvmStatic
    fun `lastIndexOf$default`(
        text: CharSequence?,
        other: Char,
        startIndex: Int,
        ignoreCase: Boolean,
        mask: Int,
        marker: Any?
    ): Int {
        val idx = if ((mask and 2) != 0) (text?.length ?: 0) else startIndex
        return lastIndexOf(text, other, idx, boolArg(mask, 3, ignoreCase))
    }
    // ---------------------------------------------------------------------------------------
    // replace / replaceFirst / equals
    //
    // `ignoreCase` sits at parameter index 2 for `replace` / `replaceFirst` (mask bit 4) and at
    // index 1 for `equals` (mask bit 2). A null receiver yields "" / the null-safe equality answer.
    // ---------------------------------------------------------------------------------------

    @JvmStatic
    fun replace(text: String?, oldValue: String?, newValue: String?, ignoreCase: Boolean): String {
        if (text == null) return ""
        val o = oldValue ?: return text
        val n = newValue ?: return text
        return text.replace(o, n, ignoreCase = ignoreCase)
    }

    @JvmStatic
    fun replace(text: String?, oldValue: Char, newValue: Char, ignoreCase: Boolean): String {
        if (text == null) return ""
        return text.replace(oldValue, newValue, ignoreCase = ignoreCase)
    }

    @JvmStatic
    fun `replace$default`(
        text: String?,
        oldValue: String?,
        newValue: String?,
        ignoreCase: Boolean,
        mask: Int,
        marker: Any?
    ): String = replace(text, oldValue, newValue, boolArg(mask, 2, ignoreCase))

    @JvmStatic
    fun `replace$default`(
        text: String?,
        oldValue: Char,
        newValue: Char,
        ignoreCase: Boolean,
        mask: Int,
        marker: Any?
    ): String = replace(text, oldValue, newValue, boolArg(mask, 2, ignoreCase))

    @JvmStatic
    fun replaceFirst(text: String?, oldValue: String?, newValue: String?, ignoreCase: Boolean): String {
        if (text == null) return ""
        val o = oldValue ?: return text
        val n = newValue ?: return text
        return text.replaceFirst(o, n, ignoreCase = ignoreCase)
    }

    @JvmStatic
    fun `replaceFirst$default`(
        text: String?,
        oldValue: String?,
        newValue: String?,
        ignoreCase: Boolean,
        mask: Int,
        marker: Any?
    ): String = replaceFirst(text, oldValue, newValue, boolArg(mask, 2, ignoreCase))

    @JvmStatic
    fun equals(text: String?, other: String?, ignoreCase: Boolean): Boolean {
        if (text == null || other == null) return text == null && other == null
        return text.equals(other, ignoreCase = ignoreCase)
    }

    @JvmStatic
    fun `equals$default`(
        text: String?,
        other: String?,
        ignoreCase: Boolean,
        mask: Int,
        marker: Any?
    ): Boolean = equals(text, other, boolArg(mask, 1, ignoreCase))

    // ---------------------------------------------------------------------------------------
    // padStart / padEnd / trim / removePrefix / removeSuffix
    //
    // `padChar` defaults to ' ' at parameter index 1, so the mask bit is 2. A null receiver
    // yields "" rather than the stdlib's NPE.
    // ---------------------------------------------------------------------------------------

    @JvmStatic
    fun padStart(text: CharSequence?, length: Int, padChar: Char): CharSequence {
        if (text == null) return ""
        return text.padStart(length, padChar)
    }

    @JvmStatic
    fun padStart(text: String?, length: Int, padChar: Char): String {
        if (text == null) return ""
        return text.padStart(length, padChar)
    }

    @JvmStatic
    fun `padStart$default`(
        text: CharSequence?,
        length: Int,
        padChar: Char,
        mask: Int,
        marker: Any?
    ): CharSequence = padStart(text, length, charArg(mask, 1, padChar))

    @JvmStatic
    fun `padStart$default`(
        text: String?,
        length: Int,
        padChar: Char,
        mask: Int,
        marker: Any?
    ): String = padStart(text, length, charArg(mask, 1, padChar))

    @JvmStatic
    fun padEnd(text: CharSequence?, length: Int, padChar: Char): CharSequence {
        if (text == null) return ""
        return text.padEnd(length, padChar)
    }

    @JvmStatic
    fun padEnd(text: String?, length: Int, padChar: Char): String {
        if (text == null) return ""
        return text.padEnd(length, padChar)
    }

    @JvmStatic
    fun `padEnd$default`(
        text: CharSequence?,
        length: Int,
        padChar: Char,
        mask: Int,
        marker: Any?
    ): CharSequence = padEnd(text, length, charArg(mask, 1, padChar))

    @JvmStatic
    fun `padEnd$default`(
        text: String?,
        length: Int,
        padChar: Char,
        mask: Int,
        marker: Any?
    ): String = padEnd(text, length, charArg(mask, 1, padChar))

    @JvmStatic
    fun trim(text: CharSequence?): CharSequence {
        if (text == null) return ""
        return text.trim()
    }

    @JvmStatic
    fun trimStart(text: CharSequence?): CharSequence {
        if (text == null) return ""
        return text.trimStart()
    }

    @JvmStatic
    fun trimEnd(text: CharSequence?): CharSequence {
        if (text == null) return ""
        return text.trimEnd()
    }

    @JvmStatic
    fun removePrefix(text: CharSequence?, prefix: CharSequence?): CharSequence {
        if (text == null) return ""
        val p = prefix ?: return text
        return text.removePrefix(p)
    }

    @JvmStatic
    fun removeSuffix(text: CharSequence?, suffix: CharSequence?): CharSequence {
        if (text == null) return ""
        val s = suffix ?: return text
        return text.removeSuffix(s)
    }

    // ---------------------------------------------------------------------------------------
    // Number conversions. These already tolerate a null receiver in the stdlib, so they only need
    // to be intercepted so a null never reaches a non-null Kotlin parameter.
    // ---------------------------------------------------------------------------------------

    @JvmStatic
    fun toByteOrNull(text: String?): Byte? = text?.toByteOrNull()

    @JvmStatic
    fun toShortOrNull(text: String?): Short? = text?.toShortOrNull()

    @JvmStatic
    fun toIntOrNull(text: String?): Int? = text?.toIntOrNull()

    @JvmStatic
    fun toLongOrNull(text: String?): Long? = text?.toLongOrNull()

    @JvmStatic
    fun toFloatOrNull(text: String?): Float? = text?.toFloatOrNull()

    @JvmStatic
    fun toDoubleOrNull(text: String?): Double? = text?.toDoubleOrNull()

    /**
     * Every `name+descriptor` pair actually implemented above, keyed in the same textual form a
     * bytecode visitor sees. [CloudStreamClassLoader] uses this to redirect only the call sites it
     * can genuinely resolve, so an interception can never introduce a `NoSuchMethodError`.
     */
    @JvmStatic
    val SUPPORTED_SIGNATURES: Set<String> = buildSet {
        PluginBytecodeHelper::class.java.declaredMethods.forEach { m ->
            if (Modifier.isStatic(m.modifiers) && m.parameterTypes.isNotEmpty()) {
                add(m.name + Type.getMethodDescriptor(m))
            }
        }
    }
}