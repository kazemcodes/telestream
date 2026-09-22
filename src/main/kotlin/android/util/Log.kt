package android.util

import org.slf4j.LoggerFactory

object Log {
    private val logger = LoggerFactory.getLogger("AndroidLog")

    @JvmStatic fun d(tag: String, msg: String): Int { logger.debug("[$tag] $msg"); return 0 }
    @JvmStatic fun i(tag: String, msg: String): Int { logger.info("[$tag] $msg"); return 0 }
    @JvmStatic fun w(tag: String, msg: String): Int { logger.warn("[$tag] $msg"); return 0 }
    @JvmStatic fun e(tag: String, msg: String): Int { logger.error("[$tag] $msg"); return 0 }
    @JvmStatic fun e(tag: String, msg: String, tr: Throwable?): Int { logger.error("[$tag] $msg", tr); return 0 }
    @JvmStatic fun v(tag: String, msg: String): Int { logger.trace("[$tag] $msg"); return 0 }
    @JvmStatic fun getStackTraceString(tr: Throwable?): String = tr?.stackTraceToString() ?: ""
}
