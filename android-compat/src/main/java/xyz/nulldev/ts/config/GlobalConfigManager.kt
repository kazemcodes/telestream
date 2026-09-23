package xyz.nulldev.ts.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object GlobalConfigManager : ConfigManager {
    private val modules = mutableMapOf<Class<*>, Any>()

    override val config: Config
        get() = try {
            ConfigFactory.load(
                ConfigFactory.parseResources("compat-reference.conf")
                    .withFallback(ConfigFactory.load())
            ).resolve()
        } catch (e: Exception) {
            try {
                ConfigFactory.parseResources("compat-reference.conf").resolve()
            } catch (ignored: Exception) {
                ConfigFactory.empty()
            }
        }

    fun registerModules(vararg configs: ConfigModule) {
        configs.forEach { modules[it::class.java] = it }
    }

    override fun <T : Any> module(clazz: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return modules[clazz] as? T ?: throw IllegalArgumentException("Module not registered: ${clazz.name}")
    }
}

inline fun <reified T : Any> ConfigManager.module(): T = module(T::class.java)
