package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import com.lagradost.nicehttp.ignoreAllSSLErrors
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit
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

class ResilientDns(
    private val dohDns: Dns,
    private val systemDns: Dns = Dns.SYSTEM
) : Dns {
    private val blockedIps = setOf(
        "10.10.34.34",
        "10.10.34.35",
        "10.10.34.36",
        "127.0.0.1",
        "0.0.0.0"
    )

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            val addresses = dohDns.lookup(hostname)
            val filtered = addresses.filterNot { it.hostAddress in blockedIps }
            if (filtered.isNotEmpty()) filtered else systemDns.lookup(hostname)
        } catch (_: Throwable) {
            systemDns.lookup(hostname)
        }
    }
}

fun parseProxy(proxyStr: String?): Proxy? {
    if (proxyStr.isNullOrBlank()) return null
    return try {
        val clean = proxyStr.trim()
        val uri = if (clean.contains("://")) URI(clean) else URI("http://$clean")
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 8080
        val type = when (uri.scheme?.lowercase()) {
            "socks", "socks5", "socks4" -> Proxy.Type.SOCKS
            else -> Proxy.Type.HTTP
        }
        Proxy(type, InetSocketAddress(host, port))
    } catch (_: Exception) {
        null
    }
}

fun buildResilientClient(proxy: Proxy? = null, useDoh: Boolean = true, verifySsl: Boolean = true): OkHttpClient {
    val builder = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)

    if (useDoh) {
        try {
            val bootstrapClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .apply { if (proxy != null) proxy(proxy) }
                .build()

            val doh = DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                .bootstrapDnsHosts(
                    listOf(
                        InetAddress.getByName("1.1.1.1"),
                        InetAddress.getByName("1.0.0.1"),
                        InetAddress.getByName("8.8.8.8"),
                        InetAddress.getByName("8.8.4.4")
                    )
                )
                .includeIPv6(false)
                .build()

            builder.dns(ResilientDns(doh, Dns.SYSTEM))
        } catch (_: Throwable) {
            // Fallback to system DNS
        }
    }

    if (proxy != null) {
        builder.proxy(proxy)
    }

    if (!verifySsl) {
        builder.ignoreAllSSLErrors()
    }

    return builder.build()
}

/** The default networking helper. This helper performs SSL checks.
 * If you need to make requests to websites with invalid SSL certificates use insecureApp instead. */
var app = Requests(
    baseClient = buildResilientClient(
        proxy = parseProxy(System.getenv("SCRAPER_PROXY") ?: System.getenv("ALL_PROXY") ?: System.getenv("HTTP_PROXY")),
        useDoh = System.getenv("ENABLE_DOH") != "false",
        verifySsl = true
    ),
    responseParser = jsonResponseParser
).apply {
    defaultHeaders = mapOf("user-agent" to USER_AGENT)
}

/** Same as the default app networking helper, but this instance ignores SSL certificates.
 * This should NEVER be used for sensitive networking operations such as logins. Only use this when required. */
@UnsafeSSL
var insecureApp = Requests(
    baseClient = buildResilientClient(
        proxy = parseProxy(System.getenv("SCRAPER_PROXY") ?: System.getenv("ALL_PROXY") ?: System.getenv("HTTP_PROXY")),
        useDoh = System.getenv("ENABLE_DOH") != "false",
        verifySsl = false
    ),
    responseParser = jsonResponseParser
).apply {
    defaultHeaders = mapOf("user-agent" to USER_AGENT)
}

class MainActivity {
    companion object {
        val afterPluginsLoadedEvent = Event<Boolean>()
        var lastError: String? = null

        fun initNetwork(proxyUrl: String? = null, useDoh: Boolean = true) {
            val p = parseProxy(proxyUrl ?: System.getenv("SCRAPER_PROXY") ?: System.getenv("ALL_PROXY") ?: System.getenv("HTTP_PROXY"))
            app.baseClient = buildResilientClient(proxy = p, useDoh = useDoh, verifySsl = true)
            insecureApp.baseClient = buildResilientClient(proxy = p, useDoh = useDoh, verifySsl = false)
        }
    }
}
