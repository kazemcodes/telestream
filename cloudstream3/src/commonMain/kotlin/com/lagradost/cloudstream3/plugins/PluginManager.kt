package com.lagradost.cloudstream3.plugins

data class PluginData(
    val internalName: String = "",
    val filePath: String = "",
)

object PluginManager {
    fun getPluginsOnline(): Array<PluginData> = emptyArray()
    fun unloadPlugin(filePath: String) {}
}
