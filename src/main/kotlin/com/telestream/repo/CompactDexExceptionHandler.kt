package com.telestream.repo

import com.googlecode.d2j.Method
import com.googlecode.d2j.node.DexMethodNode
import com.googlecode.dex2jar.tools.BaksmaliBaseDexExceptionHandler
import org.objectweb.asm.MethodTooLargeException
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Exception handler for the DEX -> JAR translation performed by [CloudStreamPluginLoader].
 *
 * The stock dex2jar handler ([BaksmaliBaseDexExceptionHandler]) replaces a method it cannot
 * translate with a stub that throws, embedding the *entire* exception stack trace as a string
 * constant:
 *
 * ```
 * new  java/lang/RuntimeException
 * ldc  "d2j fail translate: <exception>\n\tat ...\n\tat ... (200+ lines)"
 * athrow
 * ```
 *
 * That has two costs we do not want:
 *
 *  1. Every stub method carries a multi-kilobyte string constant.
 *  2. When the plugin calls the stub at runtime, that stack trace is thrown and then logged by the
 *     plugin's own retry logic - twice, since it retries before giving up. The operator sees a wall
 *     of translation frames that say nothing about the actual problem.
 *
 * This handler emits a compact, self-describing stub instead, and records what went wrong so
 * [CloudStreamPluginLoader] can report it once at load time with real context.
 *
 * The one failure we cannot work around is [MethodTooLargeException]. The JVM class file format
 * stores a method's `code_length` as a u2, capping a method at 65535 bytes. A DEX method only has
 * to stay under 65535 *16-bit code units* (131070 bytes) to be valid on Android, so a plugin can
 * legally ship a method that Android runs fine but that no JVM class file can represent. StreamPlay's
 * `invokeMovieBox` needs ~68868 bytes. No dex2jar setting changes that (reUseReg / topoLogicalSort
 * were both measured and stay within ~100 bytes of each other), so the honest outcome is a stub -
 * just a quiet, well-described one.
 */
open class CompactDexExceptionHandler : BaksmaliBaseDexExceptionHandler() {

    /** Methods that could not be represented as JVM bytecode, as `owner.name`. */
    val oversizedMethods = mutableListOf<String>()

    /** Any other translation failure, kept as `owner.name: exceptionType - message`. */
    val otherFailures = mutableListOf<String>()

    override fun handleMethodTranslateException(
        method: Method?,
        methodNode: DexMethodNode?,
        mv: MethodVisitor?,
        e: Exception?
    ) {
        // `Method.getOwner()` hands back a JVM type descriptor ("Lcom/phisher98/StreamPlay;") and
        // `getName()` only the bare name, so stitch them into a readable dotted form. When the
        // failure carries its own identity (MethodTooLargeException) prefer that, it is exact.
        val where = if (e is MethodTooLargeException) {
            "${e.className}.${e.methodName}"
        } else {
            "${method?.owner?.removeSurrounding("L", ";")}.${method?.name}"
        }

        val stubMessage = when (e) {
            is MethodTooLargeException ->
                "Unavailable on the JVM runtime: $where needs ${e.codeSize} bytes of bytecode, " +
                    "but a JVM method is capped at 65535. This extractor works in the CloudStream " +
                    "Android app, where the limit is measured in 16-bit code units, but cannot be " +
                    "loaded here."
            else ->
                "Could not be translated for the JVM runtime ($where): " +
                    "${e?.javaClass?.simpleName ?: "unknown error"} - " +
                    (e?.message?.lineSequence()?.firstOrNull() ?: "no message")
        }

        when (e) {
            is MethodTooLargeException -> oversizedMethods += where
            else -> otherFailures += "$where: ${e?.javaClass?.simpleName} - ${e?.message?.lineSequence()?.firstOrNull()}"
        }

        emitThrowingStub(mv, stubMessage)
    }

    /**
     * Writes `throw new RuntimeException(message)`.
     *
     * Mirrors the shape the stock handler emits - notably it does *not* call `visitCode`/`visitMaxs`,
     * because dex2jar has already framed the visitor by the time it calls us.
     */
    private fun emitThrowingStub(mv: MethodVisitor?, message: String) {
        if (mv == null) return
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException")
        mv.visitInsn(Opcodes.DUP)
        mv.visitLdcInsn(message)
        mv.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/RuntimeException",
            "<init>",
            "(Ljava/lang/String;)V",
            false
        )
        mv.visitInsn(Opcodes.ATHROW)
    }
}