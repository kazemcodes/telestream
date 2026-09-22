package com.telestream

import com.telestream.config.Config
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConfigTest {

    @Test
    fun testConfigLoading() {
        assertNotNull(Config.botToken)
        assertTrue(Config.port > 0)
        assertTrue(Config.webAppUrl.isNotBlank())
        assertTrue(Config.usdtTrc20.isNotBlank())
    }
}
