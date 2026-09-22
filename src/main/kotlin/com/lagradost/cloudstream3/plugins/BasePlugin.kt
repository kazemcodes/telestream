package com.lagradost.cloudstream3.plugins

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.MainAPI
import com.telestream.providers.ProviderManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

const val PLUGIN_TAG = "PluginInstance"

abstract class BasePlugin {
    companion object {
        private val logger = LoggerFactory.getLogger(BasePlugin::class.java)
    }

    var filename: String? = null

    fun registerMainAPI(element: MainAPI) {
        logger.info("Registering MainAPI: ${element.name} (${element.mainUrl})")
        element.sourcePlugin = this.filename
        ProviderManager.register(element)
    }

    fun registerExtractorAPI(element: com.lagradost.cloudstream3.utils.ExtractorApi) {
        logger.info("Registering ExtractorApi: ${element.name} (${element.mainUrl})")
        element.sourcePlugin = this.filename
    }

    fun registerExtractorAPI(element: Any) {
        logger.info("Registering ExtractorApi: $element")
    }

    open fun beforeUnload() {}
    open fun load() {}

    @Serializable
    class Manifest {
        @JsonProperty("name") @SerialName("name")
        var name: String? = null

        @JsonProperty("pluginClassName") @SerialName("pluginClassName")
        var pluginClassName: String? = null

        @JsonProperty("requiresResources") @SerialName("requiresResources")
        var requiresResources: Boolean = false

        @JsonProperty("version") @SerialName("version")
        var version: Int? = null
    }
}
