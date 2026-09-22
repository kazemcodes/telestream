package com.telestream

import com.googlecode.d2j.dex.Dex2jar
import com.googlecode.d2j.reader.MultiDexFileReader
import com.googlecode.dex2jar.tools.BaksmaliBaseDexExceptionHandler
import org.junit.jupiter.api.Test
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.assertTrue

class Dex2JarTest {

    @Test
    fun testConvertDexToJar() {
        val cs3File = if (File("test_plugin.cs3").exists()) File("test_plugin.cs3") else File("data/plugins_cache/KissKH.cs3")
        assertTrue(cs3File.exists(), "cs3 plugin file should exist")

        val targetJar = File("build/test_plugin.jar")
        targetJar.parentFile?.mkdirs()
        if (targetJar.exists()) targetJar.delete()

        // Extract classes.dex from cs3 zip
        val zip = ZipFile(cs3File)
        val dexEntry = zip.getEntry("classes.dex")
        assertTrue(dexEntry != null, "classes.dex should exist inside cs3")

        val dexBytes = zip.getInputStream(dexEntry).use { it.readBytes() }
        zip.close()

        val reader = MultiDexFileReader.open(dexBytes)
        val handler = BaksmaliBaseDexExceptionHandler()
        Dex2jar.from(reader)
            .withExceptionHandler(handler)
            .reUseReg(false)
            .topoLogicalSort()
            .skipDebug(true)
            .optimizeSynchronized(false)
            .printIR(false)
            .noCode(false)
            .skipExceptions(false)
            .computeFrames(false)
            .to(targetJar.toPath())

        assertTrue(targetJar.exists(), "targetJar should have been created")
        println("Generated JAR size: ${targetJar.length()} bytes")

        // Inspect classes in targetJar
        val jarZip = ZipFile(targetJar)
        val entries = jarZip.entries().asSequence().map { it.name }.toList()
        jarZip.close()

        println("Found ${entries.size} entries in converted JAR:")
        entries.take(15).forEach { println(" - $it") }

        // Inspect all classes with ASM bytecode fix for kotlin.Result
        val cl = object : java.net.URLClassLoader(arrayOf(targetJar.toURI().toURL()), this::class.java.classLoader) {
            override fun findClass(name: String): Class<*> {
                val path = name.replace('.', '/') + ".class"
                val input = getResourceAsStream(path) ?: return super.findClass(name)
                val rawBytes = input.use { it.readBytes() }
                val cr = org.objectweb.asm.ClassReader(rawBytes)
                val cw = org.objectweb.asm.ClassWriter(0)
                val cv = object : org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9, cw) {
                    override fun visitMethod(
                        access: Int, mName: String, descriptor: String, signature: String?, exceptions: Array<out String>?
                    ): org.objectweb.asm.MethodVisitor {
                        val mv = super.visitMethod(access, mName, descriptor, signature, exceptions)
                        return object : org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9, mv) {
                            override fun visitMethodInsn(
                                opcode: Int, owner: String, methodName: String, descriptor: String, isInterface: Boolean
                            ) {
                                var fixedName = methodName
                                if (owner == "kotlin/Result" && methodName.endsWith("_impl")) {
                                    fixedName = methodName.replace("_impl", "-impl")
                                }
                                super.visitMethodInsn(opcode, owner, fixedName, descriptor, isInterface)
                            }
                        }
                    }
                }
                cr.accept(cv, 0)
                val bytes = cw.toByteArray()
                return defineClass(name, bytes, 0, bytes.size)
            }
        }
        for (entry in entries.filter { it.endsWith(".class") }) {
            val className = entry.removeSuffix(".class").replace('/', '.')
            try {
                val clazz = cl.loadClass(className)
                println("Loaded class: $className")
            } catch (e: Throwable) {
                println("Failed to load $className: ${e::class.simpleName}: ${e.message}")
            }
        }

        // Now instantiate the Plugin and load it
        val pluginClass = cl.loadClass("com.byayzen.KissKHPlugin")
        val pluginInstance = pluginClass.getDeclaredConstructor().newInstance() as com.lagradost.cloudstream3.plugins.Plugin
        val context = android.content.SimulatedContext(customClassLoader = cl)
        pluginInstance.load(context)

        println("Registered providers after plugin.load(): ${com.telestream.providers.ProviderManager.providers.map { it.name }}")
        val kisskh = com.telestream.providers.ProviderManager.getProvider("KissKH")
        assertTrue(kisskh != null, "KissKH provider should be registered")
        println("Found KissKH: name=${kisskh.name}, url=${kisskh.mainUrl}")
        println("kotlin.Result methods: " + kotlin.Result::class.java.declaredMethods.map { it.name })

        // Test running real search!
        kotlinx.coroutines.runBlocking {
            try {
                val searchResults = kisskh.search("solo leveling")
                println("Search returned ${searchResults.size} results:")
                for (r in searchResults.take(5)) {
                    println(" - ${r.name} (${r.url}) [poster=${r.posterUrl}]")
                }
            } catch (e: Throwable) {
                println("Search execution note: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
