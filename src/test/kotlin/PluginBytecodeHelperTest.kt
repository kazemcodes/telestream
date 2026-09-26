package com.telestream

import com.telestream.repo.PluginBytecodeHelper
import org.junit.jupiter.api.Test
import org.objectweb.asm.Type
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.regex.Pattern
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the [PluginBytecodeHelper] contract.
 *
 * The shim stands in for `kotlin.text.StringsKt` inside the bytecode of dynamically loaded
 * CloudStream plugins, so it has to be indistinguishable from the real thing for every non-null
 * input - and it must never throw a NullPointerException, because plugins model upstream fields
 * that can legitimately be absent as non-null Kotlin `String`s.
 */
class PluginBytecodeHelperTest {

    // -------------------------------------------------------------------------------------------
    // Regression: the exact production failure
    //
    // "load in provider 'StreamPlay' failed: NullPointerException - Parameter specified as non-null
    //  is null: method PluginBytecodeHelper.substringBefore$default, parameter missingDelimiterValue"
    //
    // A call to `text.substringBefore(".html")` compiles to the `$default` bridge with a `null`
    // placeholder for the omitted `missingDelimiterValue` and mask bit 1 set. Declaring that
    // parameter as non-null made Kotlin emit checkNotNullParameter, which rejected the placeholder.
    // -------------------------------------------------------------------------------------------

    @Test
    fun `substringBefore default bridge tolerates the null placeholder`() {
        assertEquals("abc", PluginBytecodeHelper.`substringBefore$default`("abc.def", ".", null, 2, null))
        assertEquals("", PluginBytecodeHelper.`substringBefore$default`(null, ".", null, 2, null))
    }

    @Test
    fun `substringAfter default bridge tolerates the null placeholder`() {
        assertEquals("def", PluginBytecodeHelper.`substringAfter$default`("abc.def", ".", null, 2, null))
        // delimiter absent => missingDelimiterValue falls back to the receiver
        assertEquals("abc", PluginBytecodeHelper.`substringAfter$default`("abc", ".", null, 2, null))
        assertEquals("", PluginBytecodeHelper.`substringAfter$default`(null, ".", null, 2, null))
    }

    @Test
    fun `no helper entry point throws a null pointer exception`() {
        // Every public entry point must survive a null receiver / null placeholder, which is the
        // whole point of the shim. Any Intrinsics guard left over from a non-null declaration would
        // surface here.
        var probed = 0
        for (method in PluginBytecodeHelper::class.java.declaredMethods) {
            if (!Modifier.isStatic(method.modifiers)) continue
            if (method.parameterCount == 0) continue
            probed++
            val args = arrayOfNulls<Any?>(method.parameterTypes.size)
            for (i in args.indices) args[i] = zeroValueFor(method.parameterTypes[i])
            try {
                method.invoke(null, *args)
            } catch (e: java.lang.reflect.InvocationTargetException) {
                val cause = e.cause
                assertFalse(
                    cause is NullPointerException,
                    "${method.name} threw NullPointerException: ${cause?.message}"
                )
            }
        }
        assertTrue(probed > 40, "expected to probe the whole helper surface, probed $probed")
    }

    private fun zeroValueFor(type: Class<*>): Any? = when (type) {
        Integer.TYPE -> 0
        java.lang.Boolean.TYPE -> false
        Character.TYPE -> ' '
        else -> null
    }
    // -------------------------------------------------------------------------------------------
    // Parity with the real Kotlin stdlib
    // -------------------------------------------------------------------------------------------

    private val stdlibOwners = listOf(
        "kotlin.text.StringsKt",
        "kotlin.text.StringsKt__StringsKt",
        "kotlin.text.StringsKt__StringsJVMKt",
        "kotlin.text.StringsKt__StringNumberConversionsKt",
        "kotlin.text.StringsKt__StringNumberConversionsJVMKt",
    )

    private fun stdlibMethod(name: String, descriptor: String): Method? {
        for (owner in stdlibOwners) {
            val cls = try {
                Class.forName(owner)
            } catch (_: Throwable) {
                continue
            }
            val found = cls.declaredMethods.firstOrNull {
                it.name == name && Type.getMethodDescriptor(it) == descriptor
            }
            if (found != null) {
                found.isAccessible = true
                return found
            }
        }
        return null
    }

    private fun helperMethod(name: String, descriptor: String): Method? =
        PluginBytecodeHelper::class.java.declaredMethods.firstOrNull {
            Modifier.isStatic(it.modifiers) &&
                it.name == name &&
                Type.getMethodDescriptor(it) == descriptor
        }

    /**
     * Invokes the shim and the real stdlib bridge with identical arguments and asserts the results
     * agree. Arguments are always chosen so the real stdlib succeeds, since a stdlib NPE would
     * mean the test is feeding it something the main app could never produce.
     */
    private fun assertMatchesStdlib(name: String, descriptor: String, vararg args: Any?) {
        val ours = helperMethod(name, descriptor)
        assertNotNull(ours, "PluginBytecodeHelper must implement $name$descriptor")
        val real = stdlibMethod(name, descriptor)
        assertNotNull(real, "stdlib reference $name$descriptor is required for this parity test")

        val expected = try {
            real.invoke(null, *args)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw AssertionError(
                "stdlib itself rejected $name$descriptor with ${args.toList()}: ${e.cause}",
                e.cause
            )
        }
        val actual = ours.invoke(null, *args)
        assertEquals(
            expected,
            actual,
            "$name$descriptor disagreed with the stdlib for args ${args.toList()}"
        )
    }

    companion object {
        private const val STR_DESC =
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/Object;)Ljava/lang/String;"
        private const val SEQ_DESC =
            "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;ZILjava/lang/Object;)Z"
        private const val STR_BOOL_DESC =
            "(Ljava/lang/String;Ljava/lang/String;ZILjava/lang/Object;)Z"
        private const val STR_TRIPLE_BOOL_DESC =
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZILjava/lang/Object;)Ljava/lang/String;"
        private const val STR_IC_DESC =
            "(Ljava/lang/String;ICILjava/lang/Object;)Ljava/lang/String;"
        private const val CS_STR_BOOL_DESC =
            "(Ljava/lang/CharSequence;Ljava/lang/String;IZILjava/lang/Object;)I"
        private const val SPLIT_DESC =
            "(Ljava/lang/CharSequence;[Ljava/lang/String;ZIILjava/lang/Object;)Ljava/util/List;"
    }

    @Test
    fun substringBridgesMatchTheStdlibIncludingTheMaskDefault() {
        for (t in arrayOf("abc.def.ghi", "abc", "", "a.b")) {
            for (delim in arrayOf(".", "z", "")) {
                // mask 2 => missingDelimiterValue defaults to the receiver
                assertMatchesStdlib("substringBefore\$default", STR_DESC, t, delim, null, 2, null)
                assertMatchesStdlib("substringAfter\$default", STR_DESC, t, delim, null, 2, null)
                assertMatchesStdlib("substringBeforeLast\$default", STR_DESC, t, delim, null, 2, null)
                assertMatchesStdlib("substringAfterLast\$default", STR_DESC, t, delim, null, 2, null)
                // mask 0 => the caller supplied missingDelimiterValue explicitly
                assertMatchesStdlib("substringBefore\$default", STR_DESC, t, delim, "MISSING", 0, null)
                assertMatchesStdlib("substringAfter\$default", STR_DESC, t, delim, "MISSING", 0, null)
                assertMatchesStdlib("substringBeforeLast\$default", STR_DESC, t, delim, "MISSING", 0, null)
                assertMatchesStdlib("substringAfterLast\$default", STR_DESC, t, delim, "MISSING", 0, null)
            }
        }
    }

    @Test
    fun splitBridgesMatchTheStdlibIncludingTheMaskDefault() {
        for (t in arrayOf("a,b,c", "a", "", "A,B")) {
            for (limit in intArrayOf(0, 1, 2)) {
                for (ignoreCase in booleanArrayOf(false, true)) {
                    for (mask in intArrayOf(0, 2, 4, 6)) {
                        assertMatchesStdlib(
                            "split\$default", SPLIT_DESC,
                            t, arrayOf(",", ";"), ignoreCase, limit, mask, null
                        )
                    }
                }
            }
        }
    }

    @Test
    fun predicateBridgesMatchTheStdlibIncludingTheMaskDefault() {
        for (t in arrayOf("Hello World", "", "abc")) {
            for (needle in arrayOf("o", "O", "z")) {
                for (ignoreCase in booleanArrayOf(false, true)) {
                    for (mask in intArrayOf(0, 2)) {
                        assertMatchesStdlib(
                            "contains\$default", SEQ_DESC, t, needle, ignoreCase, mask, null
                        )
                        assertMatchesStdlib(
                            "startsWith\$default", SEQ_DESC, t, needle, ignoreCase, mask, null
                        )
                        assertMatchesStdlib(
                            "endsWith\$default", SEQ_DESC, t, needle, ignoreCase, mask, null
                        )
                    }
                }
            }
        }
    }

    @Test
    fun replaceAndEqualsBridgesMatchTheStdlibIncludingTheMaskDefault() {
        for (t in arrayOf("Hello World", "")) {
            for (old in arrayOf("o", "O", "z")) {
                for (ignoreCase in booleanArrayOf(false, true)) {
                    for (mask in intArrayOf(0, 4)) {
                        assertMatchesStdlib(
                            "replace\$default", STR_TRIPLE_BOOL_DESC,
                            t, old, "0", ignoreCase, mask, null
                        )
                    }
                }
            }
            for (ignoreCase in booleanArrayOf(false, true)) {
                for (mask in intArrayOf(0, 2)) {
                    assertMatchesStdlib(
                        "equals\$default", STR_BOOL_DESC, t, t, ignoreCase, mask, null
                    )
                }
            }
        }
    }

    @Test
    fun padBridgesMatchTheStdlibIncludingTheMaskDefault() {
        for (t in arrayOf("7", "123456")) {
            for (len in intArrayOf(3, 8)) {
                for (padChar in charArrayOf('0', '*')) {
                    for (mask in intArrayOf(0, 2)) {
                        assertMatchesStdlib(
                            "padStart\$default", STR_IC_DESC, t, len, padChar, mask, null
                        )
                        assertMatchesStdlib(
                            "padEnd\$default", STR_IC_DESC, t, len, padChar, mask, null
                        )
                    }
                }
            }
        }
    }

    @Test
    fun indexOfBridgesMatchTheStdlibIncludingTheMaskDefault() {
        for (t in arrayOf("abcabc", "abc")) {
            for (needle in arrayOf("a", "z")) {
                for (start in intArrayOf(0, 2)) {
                    for (ignoreCase in booleanArrayOf(false, true)) {
                        for (mask in intArrayOf(0, 2, 8, 10)) {
                            assertMatchesStdlib(
                                "indexOf\$default", CS_STR_BOOL_DESC,
                                t, needle, start, ignoreCase, mask, null
                            )
                            assertMatchesStdlib(
                                "lastIndexOf\$default", CS_STR_BOOL_DESC,
                                t, needle, start, ignoreCase, mask, null
                            )
                        }
                    }
                }
            }
        }
    }
    // -------------------------------------------------------------------------------------------
    // The redirection contract used by CloudStreamClassLoader
    // -------------------------------------------------------------------------------------------

    @Test
    fun everySupportedSignatureResolvesToARealHelperMethod() {
        assertTrue(PluginBytecodeHelper.SUPPORTED_SIGNATURES.isNotEmpty())
        val declared = PluginBytecodeHelper::class.java.declaredMethods
            .filter { Modifier.isStatic(it.modifiers) }
            .map { it.name + Type.getMethodDescriptor(it) }
            .toSet()
        assertTrue(
            declared.containsAll(PluginBytecodeHelper.SUPPORTED_SIGNATURES),
            "SUPPORTED_SIGNATURES referenced a method that does not exist"
        )
    }

    @Test
    fun theStdlibDescriptorsTheClassLoaderRedirectsAreAllCovered() {
        // These are the descriptors the rewriter can encounter for the intercepted names. Any one
        // missing from the shim would surface in production as a NoSuchMethodError.
        val required = listOf(
            "substringBefore\$default" + STR_DESC,
            "substringAfter\$default" + STR_DESC,
            "substringBeforeLast\$default" + STR_DESC,
            "substringAfterLast\$default" + STR_DESC,
            "split\$default" + SPLIT_DESC,
            "split\$default(Ljava/lang/CharSequence;[CZIILjava/lang/Object;)Ljava/util/List;",
            "split(Ljava/lang/CharSequence;[Ljava/lang/String;ZI)Ljava/util/List;",
            "contains\$default" + SEQ_DESC,
            "startsWith\$default" + SEQ_DESC,
            "replace\$default" + STR_TRIPLE_BOOL_DESC,
            "toIntOrNull(Ljava/lang/String;)Ljava/lang/Integer;"
        )
        val missing = required.filterNot { it in PluginBytecodeHelper.SUPPORTED_SIGNATURES }
        assertTrue(missing.isEmpty(), "PluginBytecodeHelper is missing: $missing")
    }

    @Test
    fun nullReceiversDegradeInsteadOfThrowing() {
        // The main app would throw on each of these; the shim must return a safe value so an
        // upstream API returning null can never crash a plugin.
        assertEquals("", PluginBytecodeHelper.substringBefore(null, ".", null))
        assertEquals("", PluginBytecodeHelper.substringAfter(null, ".", null))
        assertEquals(emptyList(), PluginBytecodeHelper.split(null, arrayOf(","), false, 0))
        assertFalse(PluginBytecodeHelper.contains(null, "a", false))
        assertFalse(PluginBytecodeHelper.startsWith(null, "a", false))
        assertEquals(-1, PluginBytecodeHelper.indexOf(null, "a", 0, false))
        assertEquals("", PluginBytecodeHelper.replace(null, "a", "b", false))
        assertEquals(null, PluginBytecodeHelper.toIntOrNull(null))
    }

    /**
     * Exhaustive safety net: for every `name+descriptor` the class loader can redirect, drive the
     * shim and the real stdlib bridge with the same probes and assert that whenever the stdlib
     * succeeds the two agree exactly.
     *
     * This is the property that makes the shim transparent: identical results for every input the
     * main app could produce, and no exception in the cases the main app would have thrown on.
     */
    @Test
    fun everyInterceptedBridgeAgreesWithTheStdlibWheneverTheStdlibSucceeds() {
        var compared = 0
        var stdlibRejected = 0
        for (sig in PluginBytecodeHelper.SUPPORTED_SIGNATURES) {
            val name = sig.substringBefore('(')
            val descriptor = sig.substring(name.length)
            val ours = helperMethod(name, descriptor) ?: continue
            val real = stdlibMethod(name, descriptor) ?: continue

            for (probe in probesFor(ours)) {
                val expected = try {
                    real.invoke(null, *probe)
                } catch (_: java.lang.reflect.InvocationTargetException) {
                    // The stdlib rejected an input the main app could never produce (typically a
                    // null where it demands a non-null value). The shim is allowed to differ there.
                    stdlibRejected++
                    continue
                }
                val actual = try {
                    ours.invoke(null, *probe)
                } catch (e: java.lang.reflect.InvocationTargetException) {
                    throw AssertionError(
                        "$sig threw where the stdlib returned $expected for ${probe.toList()}",
                        e.cause
                    )
                }
                assertEquals(
                    normalise(expected),
                    normalise(actual),
                    "$sig disagreed with the stdlib for ${probe.toList()}"
                )
                compared++
            }
        }
        assertTrue(compared > 150, "expected a broad comparison, only made $compared")
        assertTrue(stdlibRejected > 0, "expected some probes to be rejected by the stdlib")
    }

    private fun normalise(value: Any?): Any? = when (value) {
        is Sequence<*> -> value.toList()
        is CharSequence -> value.toString()
        else -> value
    }

    /**
     * Builds probe argument arrays for a helper method: each parameter is filled either with a
     * concrete value or with the "omitted" placeholder, and for `$default` bridges the mask is
     * swept so every combination of defaulted parameters is exercised.
     */
    private fun probesFor(method: Method): List<Array<Any?>> {
        val concrete = arrayOfNulls<Any?>(method.parameterTypes.size)
        val omitted = arrayOfNulls<Any?>(method.parameterTypes.size)
        for (i in method.parameterTypes.indices) {
            when (method.parameterTypes[i]) {
                String::class.java, CharSequence::class.java -> {
                    concrete[i] = "abc.def"
                    omitted[i] = null
                }
                Int::class.javaPrimitiveType, java.lang.Integer::class.java -> {
                    concrete[i] = 2
                    omitted[i] = 0
                }
                Boolean::class.javaPrimitiveType, java.lang.Boolean::class.java -> {
                    concrete[i] = false
                    omitted[i] = false
                }
                Char::class.javaPrimitiveType, Character::class.java -> {
                    concrete[i] = '.'
                    omitted[i] = ' '
                }
                Pattern::class.java -> {
                    concrete[i] = Pattern.compile(".")
                    omitted[i] = null
                }
                else -> {
                    concrete[i] = null
                    omitted[i] = null
                }
            }
        }

        val isDefault = method.name.endsWith("\$default")
        val probes = mutableListOf<Array<Any?>>()
        probes.add(concrete.copyOf())
        probes.add(omitted.copyOf())
        if (isDefault) {
            val maskIndex = method.parameterTypes.size - 2
            for (bit in intArrayOf(0, 1, 2, 4, 8, 16, 1 or 2 or 4 or 8 or 16)) {
                val args = concrete.copyOf()
                for (i in 0 until maskIndex) {
                    if ((bit shr i) and 1 == 1) args[i] = omitted[i]
                }
                args[maskIndex] = bit
                args[method.parameterTypes.size - 1] = null
                probes.add(args)
            }
        }
        return probes
    }
}