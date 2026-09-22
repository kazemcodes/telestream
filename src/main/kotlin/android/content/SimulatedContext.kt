package android.content

import java.io.File
import java.util.concurrent.ConcurrentHashMap

open class ContextWrapper(var baseContext: Context) : Context() {
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        baseContext.getSharedPreferences(name, mode)

    override fun getPackageName(): String = baseContext.getPackageName()
    override fun getCacheDir(): File = baseContext.getCacheDir()
    override fun getFilesDir(): File = baseContext.getFilesDir()
    override fun getDataDir(): File = baseContext.getDataDir()
    override fun getApplicationContext(): Context = baseContext.getApplicationContext()
    override fun getClassLoader(): ClassLoader = baseContext.getClassLoader()
    override fun getSystemService(name: String): Any? = baseContext.getSystemService(name)
}

class SimulatedContext(
    private val packageName: String = "com.lagradost.cloudstream3",
    private val dataDir: File = File("data").apply { mkdirs() },
    private val customClassLoader: ClassLoader = SimulatedContext::class.java.classLoader
) : Context() {
    private val prefs = ConcurrentHashMap<String, SharedPreferences>()

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        return prefs.computeIfAbsent(name) { InMemorySharedPreferences() }
    }

    override fun getPackageName(): String = packageName
    override fun getCacheDir(): File = File(dataDir, "cache").apply { mkdirs() }
    override fun getFilesDir(): File = File(dataDir, "files").apply { mkdirs() }
    override fun getDataDir(): File = dataDir
    override fun getApplicationContext(): Context = this
    override fun getClassLoader(): ClassLoader = customClassLoader
    override fun getSystemService(name: String): Any? = null
}
