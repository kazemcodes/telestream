package com.lagradost.cloudstream3.utils

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object StringUtils {
    fun String.decodeUrl(): String = URLDecoder.decode(this, StandardCharsets.UTF_8.name())
    fun String.encodeUrl(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())
    fun String.encodeUri(): String = encodeUrl()
    fun String.decodeUri(): String = decodeUrl()
}
