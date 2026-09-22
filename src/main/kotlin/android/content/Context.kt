package android.content

import java.io.File

abstract class Context {
    companion object {
        const val MODE_PRIVATE = 0
    }

    abstract fun getSharedPreferences(name: String, mode: Int): SharedPreferences
    abstract fun getPackageName(): String

    open fun getCacheDir(): File = File("data/cache").apply { mkdirs() }
    open fun getFilesDir(): File = File("data/files").apply { mkdirs() }
    open fun getDataDir(): File = File("data").apply { mkdirs() }
    open fun getApplicationContext(): Context = this
    open fun getClassLoader(): ClassLoader = javaClass.classLoader
    open fun getSystemService(name: String): Any? = null
}
