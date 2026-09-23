package com.telestream

import android.content.SimulatedContext
import android.graphics.Bitmap
import android.webkit.*
import com.lagradost.cloudstream3.network.WebViewResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import xyz.nulldev.androidcompat.AndroidCompatInitializer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HeadlessWebViewAndDispatcherTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            AndroidCompatInitializer().init()
        }
    }

    @Test
    fun testDispatchersMainResolvesOnBackgroundThread() = runBlocking {
        // Run inside worker dispatcher
        val result = withContext(Dispatchers.Default) {
            // Should resolve Dispatchers.Main without IllegalStateException
            withContext(Dispatchers.Main) {
                "MainDispatcherWorking"
            }
        }
        assertTrue(result == "MainDispatcherWorking", "Dispatchers.Main should execute cleanly across threads")
    }

    @Test
    fun testHeadlessWebViewLifecycleOnBackgroundThread() = runBlocking {
        withContext(Dispatchers.Default) {
            val webView = WebView(SimulatedContext())
            assertNotNull(webView, "WebView should instantiate on background coroutine thread")

            webView.settings.javaScriptEnabled = true
            webView.settings.userAgentString = "Mozilla/5.0 HeadlessTest"

            val latch = CountDownLatch(1)
            var pageFinishedCalled = false

            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    println("WebView onPageStarted: $url")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    println("WebView onPageFinished: $url")
                    pageFinishedCalled = true
                    latch.countDown()
                }

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    println("WebView shouldInterceptRequest: ${request?.url}")
                    return super.shouldInterceptRequest(view, request)
                }
            }

            webView.loadDataWithBaseURL("https://example.com", "<html><body><h1>Hello Headless</h1></body></html>", "text/html", "UTF-8", null)

            val finished = latch.await(5, TimeUnit.SECONDS)
            assertTrue(finished, "Headless WebView should trigger onPageFinished within timeout")
            assertTrue(pageFinishedCalled, "onPageFinished must be invoked")

            // Test JS evaluation
            val jsLatch = CountDownLatch(1)
            var jsResult: String? = null
            webView.evaluateJavascript("document.title = 'Updated Title'; document.title;") { res ->
                jsResult = res
                jsLatch.countDown()
            }
            jsLatch.await(3, TimeUnit.SECONDS)
            println("WebView JS evaluation result: $jsResult")
        }
    }

    @Test
    fun testCookieManagerOperations() {
        val cookieManager = CookieManager.getInstance()
        assertNotNull(cookieManager, "CookieManager.getInstance() should not be null")
        cookieManager.setCookie("https://top.xdmovies.wtf", "cf_clearance=mock_token_12345; Path=/; Domain=.xdmovies.wtf")
        val cookies = cookieManager.getCookie("https://top.xdmovies.wtf")
        assertTrue(cookies?.contains("cf_clearance") == true, "CookieManager should retain cookies")
    }

    @Test
    fun testWebViewResolverInstantiation() {
        val interceptPattern = Regex(".*example\\.com.*")
        val resolver = WebViewResolver(interceptPattern)
        assertNotNull(resolver, "WebViewResolver should instantiate successfully")
    }
}
