package android.app

open class ActivityManager {
    class MemoryInfo {
        @JvmField var availMem: Long = Runtime.getRuntime().freeMemory()
        @JvmField var totalMem: Long = 8L * 1024 * 1024 * 1024 // 8GB default
        @JvmField var lowMemory: Boolean = false
        @JvmField var threshold: Long = 0L
    }

    open fun getMemoryInfo(outInfo: MemoryInfo) {
        val maxMem = Runtime.getRuntime().maxMemory()
        outInfo.totalMem = if (maxMem != Long.MAX_VALUE && maxMem > 512 * 1024 * 1024) maxMem else (8L * 1024 * 1024 * 1024)
        outInfo.availMem = Runtime.getRuntime().freeMemory()
        outInfo.lowMemory = false
    }
}
