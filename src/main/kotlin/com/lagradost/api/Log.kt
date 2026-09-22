package com.lagradost.api

import org.slf4j.LoggerFactory

object Log {
    private val logger = LoggerFactory.getLogger("CloudStream")

    fun d(tag: String, message: String) {
        logger.debug("[{}] {}", tag, message)
    }

    fun i(tag: String, message: String) {
        logger.info("[{}] {}", tag, message)
    }

    fun w(tag: String, message: String) {
        logger.warn("[{}] {}", tag, message)
    }

    fun e(tag: String, message: String) {
        logger.error("[{}] {}", tag, message)
    }

    fun e(tag: String, message: String, tr: Throwable?) {
        logger.error("[{}] {}", tag, message, tr)
    }

    fun v(tag: String, message: String) {
        logger.trace("[{}] {}", tag, message)
    }
}
