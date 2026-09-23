package android.content

import java.io.File
import java.util.concurrent.ConcurrentHashMap

open class SimulatedContext(
    private val packageName: String = "com.lagradost.cloudstream3",
    private val dataDir: File = File("data").apply { mkdirs() },
    private val customClassLoader: ClassLoader = SimulatedContext::class.java.classLoader
) : Context() {
    private val prefs = ConcurrentHashMap<String, SharedPreferences>()

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        return prefs.computeIfAbsent(name) { NoOpSharedPreferences() }
    }

    override fun getPackageName(): String = packageName
    override fun getCacheDir(): File = File(dataDir, "cache").apply { mkdirs() }
    override fun getFilesDir(): File = File(dataDir, "files").apply { mkdirs() }
    override fun getDataDir(): File = dataDir
    override fun getApplicationContext(): Context = this
    override fun getClassLoader(): ClassLoader = customClassLoader
    override fun getSystemService(name: String): Any? = when (name) {
        "activity" -> android.app.ActivityManager()
        else -> null
    }
}
