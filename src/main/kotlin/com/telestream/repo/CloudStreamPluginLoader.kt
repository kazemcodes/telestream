package com.telestream.repo

import android.content.SimulatedContext
import com.googlecode.d2j.dex.Dex2jar
import com.googlecode.d2j.reader.MultiDexFileReader
import com.googlecode.dex2jar.tools.BaksmaliBaseDexExceptionHandler
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.Plugin
import com.telestream.providers.ProviderManager
import okhttp3.OkHttpClient
import okhttp3.Request
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.net.URL
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * Custom ClassLoader for dynamically loaded CloudStream plugins.
 * Intercepts bytecode to remap Dalvik-specific identifiers (such as kotlin/Result._impl)
 * to standard JVM equivalents (kotlin/Result.-impl), and remap SerializersKt.serializer to serializerOrNull
 * for graceful Jackson fallback when parsing data classes lacking @Serializable.
 */
class CloudStreamClassLoader(
    urls: Array<URL>,
    parent: ClassLoader
) : URLClassLoader(urls, parent) {

    override fun findClass(name: String): Class<*> {
        val path = name.replace('.', '/') + ".class"
        val input: InputStream = getResourceAsStream(path) ?: return super.findClass(name)
        val rawBytes = input.use { it.readBytes() }

        val cr = ClassReader(rawBytes)
        val cw = ClassWriter(0)
        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitMethod(
                access: Int,
                mName: String,
                descriptor: String,
                signature: String?,
                exceptions: Array<out String>?
            ): MethodVisitor {
                val mv = super.visitMethod(access, mName, descriptor, signature, exceptions)
                return object : MethodVisitor(Opcodes.ASM9, mv) {
                    override fun visitMethodInsn(
                        opcode: Int,
                        owner: String,
                        methodName: String,
                        descriptor: String,
                        isInterface: Boolean
                    ) {
                        var fixedName = methodName
                        // Remap Dalvik names for Kotlin Result inline methods to JVM -impl
                        if (owner == "kotlin/Result" && methodName.endsWith("_impl")) {
                            fixedName = methodName.replace("_impl", "-impl")
                        }
                        if (owner.startsWith("kotlinx/coroutines/BuildersKt") && methodName == "runBlockingK") {
                            fixedName = "runBlocking"
                        }
                        if (owner.startsWith("kotlinx/coroutines/DelayKt") && methodName.startsWith("delay_")) {
                            fixedName = methodName.replace("delay_", "delay-")
                        }
                        if (owner.startsWith("kotlinx/coroutines/TimeoutKt") && methodName.contains("_")) {
                            fixedName = methodName.replace('_', '-')
                        }
                        // Remap SerializersKt.serializer to serializerOrNull so non-@Serializable
                        // data classes (e.g. StreamPlay$Data) return null instead of throwing SerializationException,
                        // allowing CloudStream's AppUtils.parseJson to cleanly fall back to Jackson.
                        if (owner == "kotlinx/serialization/SerializersKt" && methodName == "serializer") {
                            fixedName = "serializerOrNull"
                        }
                        super.visitMethodInsn(opcode, owner, fixedName, descriptor, isInterface)
                    }
                }
            }
        }
        cr.accept(cv, 0)
        val fixedBytes = cw.toByteArray()
        return defineClass(name, fixedBytes, 0, fixedBytes.size)
    }
}

object CloudStreamPluginLoader {
    private val logger = LoggerFactory.getLogger(CloudStreamPluginLoader::class.java)
    val cacheDir = File("data/plugins_cache").apply { mkdirs() }

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    // Loaded classloaders cache
    private val classLoaders = ConcurrentHashMap<String, CloudStreamClassLoader>()
    private val pluginLocks = ConcurrentHashMap<String, Any>()

    /**
     * Download (if needed), translate DEX to JAR, load into JVM, and return the registered MainAPI.
     */
    fun loadPlugin(metadata: PluginMetadata): MainAPI? {
        val safeName = (metadata.internalName ?: metadata.name).replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        val existing = ProviderManager.providers.firstOrNull {
            it.name.equals(metadata.name, ignoreCase = true) ||
            it.name.equals(metadata.internalName, ignoreCase = true)
        }
        if (existing != null) return existing

        val lock = pluginLocks.computeIfAbsent(safeName) { Any() }
        synchronized(lock) {
            val doubleCheck = ProviderManager.providers.firstOrNull {
                it.name.equals(metadata.name, ignoreCase = true) ||
                it.name.equals(metadata.internalName, ignoreCase = true)
            }
            if (doubleCheck != null) return doubleCheck

            val cs3File = File(cacheDir, "$safeName.cs3")
            val jarFile = File(cacheDir, "$safeName.jar")

            try {
                // 1. Download .cs3 if not present
                if (!cs3File.exists() || cs3File.length() == 0L) {
                    val url = metadata.url ?: run {
                        logger.warn("No download URL for plugin ${metadata.name}")
                        return null
                    }
                    logger.info("Downloading plugin ${metadata.name} from $url ...")
                    val request = Request.Builder().url(url).build()
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            logger.error("Failed to download ${metadata.name}: HTTP ${response.code}")
                            return null
                        }
                        val body = response.body ?: return null
                        cs3File.outputStream().use { out ->
                            body.byteStream().copyTo(out)
                        }
                    }
                    logger.info("Downloaded ${metadata.name} (${cs3File.length()} bytes)")
                }

                // 2. Convert DEX to JAR if jar does not exist or cs3 is newer
                if (!jarFile.exists() || jarFile.length() == 0L || jarFile.lastModified() < cs3File.lastModified()) {
                    logger.info("Translating DEX to JAR for ${metadata.name}...")
                    val zip = ZipFile(cs3File)
                    val dexEntry = zip.getEntry("classes.dex") ?: run {
                        logger.error("No classes.dex found in $cs3File")
                        zip.close()
                        return null
                    }
                    val dexBytes = zip.getInputStream(dexEntry).use { it.readBytes() }
                    zip.close()

                    val reader = MultiDexFileReader.open(dexBytes)
                    val handler = object : BaksmaliBaseDexExceptionHandler() {
                        override fun handleMethodTranslateException(
                            method: com.googlecode.d2j.Method?,
                            node: com.googlecode.d2j.node.DexMethodNode?,
                            mv: org.objectweb.asm.MethodVisitor?,
                            e: Exception?
                        ) {
                            logger.warn("Method translation issue in ${method?.owner}.${method?.name}: ${e?.javaClass?.simpleName} - ${e?.message}")
                            super.handleMethodTranslateException(method, node, mv, e)
                        }
                    }
                    val tempJar = File(jarFile.parentFile, "${jarFile.name}.tmp")
                    if (tempJar.exists()) tempJar.delete()
                    try {
                        Dex2jar.from(reader)
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
                            .to(tempJar.toPath())

                        if (jarFile.exists()) jarFile.delete()
                        tempJar.renameTo(jarFile)
                    } catch (t: Throwable) {
                        if (tempJar.exists()) tempJar.delete()
                        if (jarFile.exists() && jarFile.length() == 0L) jarFile.delete()
                        throw t
                    }

                    logger.info("Generated JAR for ${metadata.name} (${jarFile.length()} bytes)")
                }

                // 3. Read manifest.json from cs3
                val manifestJson = ZipFile(cs3File).use { zip ->
                    val entry = zip.getEntry("manifest.json") ?: return@use null
                    zip.getInputStream(entry).use { it.bufferedReader().readText() }
                } ?: run {
                    logger.error("manifest.json missing in $cs3File")
                    return null
                }

                val manifest = com.lagradost.cloudstream3.mapper.readTree(manifestJson)
                val pluginClassName = manifest.get("pluginClassName")?.asText() ?: run {
                    logger.error("pluginClassName missing in manifest.json for ${metadata.name}")
                    return null
                }

                // 4. Create classloader and load plugin
                val cl = classLoaders.computeIfAbsent(safeName) {
                    CloudStreamClassLoader(arrayOf(jarFile.toURI().toURL()), this::class.java.classLoader)
                }

                val pluginClass = cl.loadClass(pluginClassName)
                val pluginInstance = pluginClass.getDeclaredConstructor().newInstance() as Plugin
                val simulatedContext = SimulatedContext(customClassLoader = cl)
                pluginInstance.load(simulatedContext)

                val cleanMetaName = metadata.name.replace(" ", "")
                val cleanInternalName = metadata.internalName?.replace(" ", "")
                val provider = ProviderManager.providers.firstOrNull {
                    it.name.equals(metadata.name, ignoreCase = true) ||
                    it.name.replace(" ", "").equals(cleanMetaName, ignoreCase = true) ||
                    it.name.equals(metadata.internalName, ignoreCase = true) ||
                    it.name.replace(" ", "").equals(cleanInternalName, ignoreCase = true) ||
                    (it.sourcePlugin != null && it.sourcePlugin.equals(pluginClassName, ignoreCase = true))
                } ?: ProviderManager.providers.lastOrNull()
                if (provider != null) {
                    logger.info("Successfully loaded CloudStream plugin: ${provider.name} (${provider.mainUrl})")
                } else {
                    logger.warn("Plugin loaded for ${metadata.name}, but no provider matching was found in ProviderManager.")
                }
                return provider
            } catch (e: Throwable) {
                logger.error("Failed to load plugin ${metadata.name}: ${e.message}", e)
                return null
            }
        }
    }
}
