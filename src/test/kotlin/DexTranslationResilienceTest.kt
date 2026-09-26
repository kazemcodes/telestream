package com.telestream

import com.googlecode.d2j.dex.Dex2jar
import com.googlecode.d2j.reader.MultiDexFileReader
import com.telestream.repo.CompactDexExceptionHandler
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodTooLargeException
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the DEX -> JAR translation step in [com.telestream.repo.CloudStreamPluginLoader].
 *
 * Context: a `.cs3` CloudStream plugin ships as DEX. The DEX format bounds a method by 65535
 * 16-bit *code units* (131070 bytes), while a JVM class file bounds it by a u2 `code_length`
 * (65535 bytes). A plugin can therefore legally contain a method that Android runs fine but that no
 * JVM class can represent. dex2jar replaces such a method with a throwing stub, and by default the
 * stub embeds the whole translation stack trace as a string constant - which is then thrown and
 * logged by the plugin's retry logic, burying the real message.
 */
class DexTranslationResilienceTest {

    private fun streamPlayDex(): ByteArray? {
        val cs3 = File("data/plugins_cache/StreamPlay.cs3")
        if (!cs3.exists()) return null
        return ZipFile(cs3).use { zip ->
            zip.getEntry("classes.dex")?.let { zip.getInputStream(it).use { s -> s.readBytes() } }
        }
    }

    /** Translates StreamPlay and returns the produced JAR plus the handler that observed the run. */
    private fun translate(dex: ByteArray, out: File): CompactDexExceptionHandler {
        out.parentFile?.mkdirs()
        val handler = CompactDexExceptionHandler()
        Dex2jar.from(MultiDexFileReader.open(dex))
            .withExceptionHandler(handler)
            .reUseReg(false)
            .topoLogicalSort(false)
            .skipDebug(true)
            .optimizeSynchronized(false)
            .printIR(false)
            .noCode(false)
            .skipExceptions(false)
            .dontSanitizeNames(true)
            .computeFrames(false)
            .to(out.toPath())
        return handler
    }

    @Test
    fun `oversized methods are reported rather than silently stubbed`() {
        val dex = streamPlayDex() ?: return // plugin not cached; nothing to assert

        val jar = File("build/resilience_streamplay.jar")
        val handler = translate(dex, jar)

        // StreamPlay ships exactly one method that cannot be represented on the JVM.
        assertEquals(1, handler.oversizedMethods.size, "unexpected oversized set: ${handler.oversizedMethods}")
        assertEquals(
            "com/phisher98/StreamPlayExtractor.invokeMovieBox",
            handler.oversizedMethods.single()
        )
        assertTrue(handler.otherFailures.isEmpty(), "unexpected extra failures: ${handler.otherFailures}")
        assertTrue(jar.length() > 0, "translation should still produce a usable jar")
    }

    @Test
    fun `the stub message is compact and explains the JVM limit`() {
        val dex = streamPlayDex() ?: return
        val jar = File("build/resilience_streamplay2.jar")
        translate(dex, jar)

        val bytes = ZipFile(jar).use { zip ->
            val entry = zip.getEntry("com/phisher98/StreamPlayExtractor.class")
            assertNotNull(entry, "StreamPlayExtractor should be present in the translated jar")
            zip.getInputStream(entry).use { it.readBytes() }
        }

        val messages = mutableListOf<String>()
        val collector = object : MethodVisitor(Opcodes.ASM9) {
            override fun visitLdcInsn(value: Any) {
                if (value is String && value.contains("Unavailable on the JVM runtime")) messages += value
            }
        }
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(
                access: Int,
                name: String,
                descriptor: String,
                signature: String?,
                exceptions: Array<out String>?
            ): MethodVisitor = collector
        }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)

        assertEquals(1, messages.size, "expected exactly one stub message, got $messages")
        val message = messages.single()
        // The whole point: a short, actionable message rather than a multi-KB translation stack trace.
        assertTrue(message.length < 600, "stub message should be compact, was ${message.length} chars")
        assertTrue(!message.contains("\tat "), "stub message must not embed a stack trace: $message")
        assertTrue(message.contains("65535"), "stub message should state the JVM limit: $message")
        assertTrue(message.contains("invokeMovieBox"), "stub message should name the method: $message")
    }
}