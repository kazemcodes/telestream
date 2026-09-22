package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import kotlin.reflect.KClass

private val jsonResponseParser = object : ResponseParser {
    override fun <T : Any> parse(text: String, kClass: KClass<T>): T {
        return parseJson(text, kClass)
    }

    override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? {
        return try {
            parse(text, kClass)
        } catch (_: Exception) {
            null
        }
    }

    override fun writeValueAsString(obj: Any): String {
        return obj.toJson()
    }
}

/** The default networking helper. */
var app = Requests(responseParser = jsonResponseParser).apply {
    defaultHeaders = mapOf("user-agent" to USER_AGENT)
}

/** Same as the default app networking helper, but this instance ignores SSL certificates. */
var insecureApp = Requests(responseParser = jsonResponseParser).apply {
    defaultHeaders = mapOf("user-agent" to USER_AGENT)
}

open class MainActivity {
    companion object {
        var nextSearchQuery: String? = null
        val afterPluginsLoadedEvent = com.lagradost.cloudstream3.utils.Event<Boolean>()
        val mainPluginsLoadedEvent = com.lagradost.cloudstream3.utils.Event<Boolean>()
        val afterRepositoryLoadedEvent = com.lagradost.cloudstream3.utils.Event<Boolean>()
        val bookmarksUpdatedEvent = com.lagradost.cloudstream3.utils.Event<Boolean>()
        val reloadHomeEvent = com.lagradost.cloudstream3.utils.Event<Boolean>()
        val reloadLibraryEvent = com.lagradost.cloudstream3.utils.Event<Boolean>()
    }
}
