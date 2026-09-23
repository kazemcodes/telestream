package android.app

class ActivityManager {
    class MemoryInfo {
        @JvmField
        var availMem: Long = 1024L * 1024L * 1024L * 4L
        @JvmField
        var totalMem: Long = 1024L * 1024L * 1024L * 8L
        @JvmField
        var lowMemory: Boolean = false
        @JvmField
        var threshold: Long = 1024L * 1024L * 1024L
    }

    fun getMemoryInfo(outInfo: MemoryInfo) {
        val runtime = Runtime.getRuntime()
        outInfo.totalMem = runtime.totalMemory()
        outInfo.availMem = runtime.freeMemory()
        outInfo.lowMemory = outInfo.availMem < (outInfo.totalMem / 10)
    }
}
