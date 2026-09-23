package android.os

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

// -----------------------------------------------------------------------------
// MessageQueue & Looper
// -----------------------------------------------------------------------------

class MessageQueue internal constructor(private val looper: Looper) {
    private val scheduledExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "Looper-${looper.thread.name}").apply { isDaemon = true }
    }
    private val isQuitting = AtomicBoolean(false)
    private val scheduledTasks = ConcurrentHashMap<Message, ScheduledFuture<*>>()

    fun enqueueMessage(msg: Message, uptimeMillis: Long): Boolean {
        if (isQuitting.get()) return false
        val delayMs = maxOf(0L, uptimeMillis - System.currentTimeMillis())

        val future = scheduledExecutor.schedule({
            scheduledTasks.remove(msg)
            try {
                msg.target?.dispatchMessage(msg)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }, delayMs, TimeUnit.MILLISECONDS)

        scheduledTasks[msg] = future
        return true
    }

    fun removeMessages(h: Handler, what: Int, obj: Any?) {
        val it = scheduledTasks.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            val msg = entry.key
            if (msg.target == h && msg.what == what && (obj == null || msg.obj == obj)) {
                entry.value.cancel(false)
                it.remove()
            }
        }
    }

    fun removeMessages(h: Handler, r: Runnable) {
        val it = scheduledTasks.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            val msg = entry.key
            if (msg.target == h && msg.callback == r) {
                entry.value.cancel(false)
                it.remove()
            }
        }
    }

    fun hasMessages(h: Handler, what: Int, obj: Any?): Boolean {
        return scheduledTasks.keys.any { msg ->
            msg.target == h && msg.what == what && (obj == null || msg.obj == obj)
        }
    }

    fun quit() {
        if (isQuitting.compareAndSet(false, true)) {
            scheduledTasks.values.forEach { it.cancel(false) }
            scheduledTasks.clear()
            scheduledExecutor.shutdownNow()
        }
    }

    fun loop() {
        // In this executor-backed looper, events are dispatched on scheduledExecutor.
    }
}

class Looper private constructor(val thread: Thread, val isMain: Boolean = false) {
    val queue: MessageQueue = MessageQueue(this)

    fun quit() {
        queue.quit()
    }

    fun isCurrentThread(): Boolean = Thread.currentThread() == thread

    companion object {
        private val sThreadLocal = ThreadLocal<Looper>()
        private var sMainLooper: Looper? = null
        private val mainThread: Thread = Thread({
            // Dummy keepalive loop for main looper thread identity
        }, "AndroidMainLooperThread").apply { isDaemon = true }

        init {
            mainThread.start()
        }

        @JvmStatic
        fun prepare() {
            if (sThreadLocal.get() != null) {
                throw RuntimeException("Only one Looper may be created per thread")
            }
            sThreadLocal.set(Looper(Thread.currentThread()))
        }

        @JvmStatic
        fun prepareMainLooper() {
            synchronized(Looper::class.java) {
                if (sMainLooper != null) {
                    return
                }
                sMainLooper = Looper(mainThread, isMain = true)
                sThreadLocal.set(sMainLooper)
            }
        }

        @JvmStatic
        fun getMainLooper(): Looper {
            synchronized(Looper::class.java) {
                if (sMainLooper == null) {
                    sMainLooper = Looper(mainThread, isMain = true)
                    sThreadLocal.set(sMainLooper)
                }
                return sMainLooper!!
            }
        }

        @JvmStatic
        fun myLooper(): Looper? {
            return sThreadLocal.get() ?: getMainLooper()
        }

        @JvmStatic
        fun loop() {
            myLooper()?.queue?.loop()
        }
    }
}

// -----------------------------------------------------------------------------
// Handler & Message
// -----------------------------------------------------------------------------

open class Handler {
    val looper: Looper
    val callback: Callback?

    fun interface Callback {
        fun handleMessage(msg: Message): Boolean
    }

    constructor() : this(Looper.myLooper() ?: Looper.getMainLooper(), null)
    constructor(callback: Callback?) : this(Looper.myLooper() ?: Looper.getMainLooper(), callback)
    constructor(looper: Looper) : this(looper, null)
    constructor(looper: Looper, callback: Callback?) {
        this.looper = looper
        this.callback = callback
    }

    open fun handleMessage(msg: Message) {}

    open fun dispatchMessage(msg: Message) {
        if (msg.callback != null) {
            msg.callback?.run()
        } else {
            if (callback != null && callback.handleMessage(msg)) {
                return
            }
            handleMessage(msg)
        }
    }

    open fun post(r: Runnable): Boolean = sendMessageDelayed(getPostMessage(r), 0)

    open fun postDelayed(r: Runnable, delayMillis: Long): Boolean =
        sendMessageDelayed(getPostMessage(r), delayMillis)

    open fun postAtTime(r: Runnable, uptimeMillis: Long): Boolean {
        val delay = uptimeMillis - System.currentTimeMillis()
        return sendMessageDelayed(getPostMessage(r), if (delay > 0) delay else 0)
    }

    open fun removeCallbacks(r: Runnable) {
        looper.queue.removeMessages(this, r)
    }

    open fun sendMessage(msg: Message): Boolean = sendMessageDelayed(msg, 0)

    open fun sendEmptyMessage(what: Int): Boolean = sendEmptyMessageDelayed(what, 0)

    open fun sendEmptyMessageDelayed(what: Int, delayMillis: Long): Boolean {
        val msg = Message.obtain()
        msg.what = what
        return sendMessageDelayed(msg, delayMillis)
    }

    open fun sendMessageDelayed(msg: Message, delayMillis: Long): Boolean {
        val delay = if (delayMillis < 0) 0 else delayMillis
        return sendMessageAtTime(msg, System.currentTimeMillis() + delay)
    }

    open fun sendMessageAtTime(msg: Message, uptimeMillis: Long): Boolean {
        msg.target = this
        return looper.queue.enqueueMessage(msg, uptimeMillis)
    }

    open fun hasMessages(what: Int): Boolean = looper.queue.hasMessages(this, what, null)

    open fun hasMessages(what: Int, obj: Any?): Boolean = looper.queue.hasMessages(this, what, obj)

    open fun removeMessages(what: Int) {
        looper.queue.removeMessages(this, what, null)
    }

    open fun removeMessages(what: Int, obj: Any?) {
        looper.queue.removeMessages(this, what, obj)
    }

    open fun obtainMessage(): Message = Message.obtain(this)
    open fun obtainMessage(what: Int): Message = Message.obtain(this, what)
    open fun obtainMessage(what: Int, obj: Any?): Message = Message.obtain(this, what, obj)
    open fun obtainMessage(what: Int, arg1: Int, arg2: Int): Message = Message.obtain(this, what, arg1, arg2)
    open fun obtainMessage(what: Int, arg1: Int, arg2: Int, obj: Any?): Message = Message.obtain(this, what, arg1, arg2, obj)

    private fun getPostMessage(r: Runnable): Message {
        val m = Message.obtain()
        m.callback = r
        return m
    }
}

open class Message {
    var what: Int = 0
    var arg1: Int = 0
    var arg2: Int = 0
    var obj: Any? = null
    var data: Bundle? = null
    var target: Handler? = null
    var callback: Runnable? = null
    var whenTime: Long = 0

    fun sendToTarget() {
        target?.sendMessage(this)
    }

    fun copyFrom(other: Message) {
        this.what = other.what
        this.arg1 = other.arg1
        this.arg2 = other.arg2
        this.obj = other.obj
        this.data = other.data
        this.target = other.target
        this.callback = other.callback
    }

    fun recycle() {
        what = 0
        arg1 = 0
        arg2 = 0
        obj = null
        data = null
        target = null
        callback = null
    }

    companion object {
        @JvmStatic fun obtain(): Message = Message()
        @JvmStatic fun obtain(h: Handler): Message = Message().apply { target = h }
        @JvmStatic fun obtain(h: Handler, what: Int): Message = Message().apply { target = h; this.what = what }
        @JvmStatic fun obtain(h: Handler, what: Int, obj: Any?): Message = Message().apply { target = h; this.what = what; this.obj = obj }
        @JvmStatic fun obtain(h: Handler, what: Int, arg1: Int, arg2: Int): Message = Message().apply { target = h; this.what = what; this.arg1 = arg1; this.arg2 = arg2 }
        @JvmStatic fun obtain(h: Handler, what: Int, arg1: Int, arg2: Int, obj: Any?): Message = Message().apply { target = h; this.what = what; this.arg1 = arg1; this.arg2 = arg2; this.obj = obj }
        @JvmStatic fun obtain(orig: Message): Message = Message().apply { copyFrom(orig) }
    }
}

// -----------------------------------------------------------------------------
// Bundle
// -----------------------------------------------------------------------------

open class Bundle(private val map: MutableMap<String, Any?> = HashMap()) : Cloneable {
    constructor(capacity: Int) : this(HashMap(capacity))
    constructor(bundle: Bundle) : this(HashMap(bundle.map))

    fun putString(key: String?, value: String?) { if (key != null) map[key] = value }
    fun getString(key: String?): String? = if (key != null) map[key] as? String else null
    fun getString(key: String?, defaultValue: String): String = getString(key) ?: defaultValue

    fun putInt(key: String?, value: Int) { if (key != null) map[key] = value }
    fun getInt(key: String?, defaultValue: Int = 0): Int =
        (if (key != null) map[key] as? Number else null)?.toInt() ?: defaultValue

    fun putLong(key: String?, value: Long) { if (key != null) map[key] = value }
    fun getLong(key: String?, defaultValue: Long = 0L): Long =
        (if (key != null) map[key] as? Number else null)?.toLong() ?: defaultValue

    fun putBoolean(key: String?, value: Boolean) { if (key != null) map[key] = value }
    fun getBoolean(key: String?, defaultValue: Boolean = false): Boolean =
        (if (key != null) map[key] as? Boolean else null) ?: defaultValue

    fun putFloat(key: String?, value: Float) { if (key != null) map[key] = value }
    fun getFloat(key: String?, defaultValue: Float = 0f): Float =
        (if (key != null) map[key] as? Number else null)?.toFloat() ?: defaultValue

    fun putDouble(key: String?, value: Double) { if (key != null) map[key] = value }
    fun getDouble(key: String?, defaultValue: Double = 0.0): Double =
        (if (key != null) map[key] as? Number else null)?.toDouble() ?: defaultValue

    fun putByteArray(key: String?, value: ByteArray?) { if (key != null) map[key] = value }
    fun getByteArray(key: String?): ByteArray? = if (key != null) map[key] as? ByteArray else null

    fun putStringArrayList(key: String?, value: ArrayList<String>?) { if (key != null) map[key] = value }
    fun getStringArrayList(key: String?): ArrayList<String>? {
        if (key == null) return null
        @Suppress("UNCHECKED_CAST")
        return map[key] as? ArrayList<String>
    }

    fun putBundle(key: String?, value: Bundle?) { if (key != null) map[key] = value }
    fun getBundle(key: String?): Bundle? = if (key != null) map[key] as? Bundle else null

    fun get(key: String?): Any? = if (key != null) map[key] else null
    fun containsKey(key: String?): Boolean = if (key != null) map.containsKey(key) else false
    fun remove(key: String?) { if (key != null) map.remove(key) }
    fun clear() { map.clear() }
    fun isEmpty(): Boolean = map.isEmpty()
    fun size(): Int = map.size
    fun keySet(): Set<String> = map.keys

    public override fun clone(): Any = Bundle(this)
}

// -----------------------------------------------------------------------------
// Build & SystemClock
// -----------------------------------------------------------------------------

object Build {
    const val BOARD = "desktop"
    const val BOOTLOADER = "unknown"
    const val BRAND = "Google"
    const val DEVICE = "generic"
    const val DISPLAY = "Pure JVM"
    const val FINGERPRINT = "google/generic/desktop:14/UKQ1.230917.001/1:user/release-keys"
    const val HARDWARE = "desktop"
    const val HOST = "localhost"
    const val ID = "UKQ1.230917.001"
    const val MANUFACTURER = "Google"
    const val MODEL = "Pure JVM Headless"
    const val PRODUCT = "generic"
    const val SERIAL = "unknown"

    object VERSION {
        const val BASE_OS = ""
        const val CODENAME = "REL"
        const val INCREMENTAL = "1"
        const val PREVIEW_SDK_INT = 0
        const val RELEASE = "14"
        const val SDK = "34"
        const val SDK_INT = 34
        const val SECURITY_PATCH = "2024-01-01"
    }

    object VERSION_CODES {
        const val BASE = 1
        const val BASE_1_1 = 2
        const val CUPCAKE = 3
        const val DONUT = 4
        const val ECLAIR = 5
        const val ECLAIR_0_1 = 6
        const val ECLAIR_MR1 = 7
        const val FROYO = 8
        const val GINGERBREAD = 9
        const val GINGERBREAD_MR1 = 10
        const val HONEYCOMB = 11
        const val HONEYCOMB_MR1 = 12
        const val HONEYCOMB_MR2 = 13
        const val ICE_CREAM_SANDWICH = 14
        const val ICE_CREAM_SANDWICH_MR1 = 15
        const val JELLY_BEAN = 16
        const val JELLY_BEAN_MR1 = 17
        const val JELLY_BEAN_MR2 = 18
        const val KITKAT = 19
        const val KITKAT_WATCH = 20
        const val LOLLIPOP = 21
        const val LOLLIPOP_MR1 = 22
        const val M = 23
        const val N = 24
        const val N_MR1 = 25
        const val O = 26
        const val O_MR1 = 27
        const val P = 28
        const val Q = 29
        const val R = 30
        const val S = 31
        const val S_V2 = 32
        const val TIRAMISU = 33
        const val UPSIDE_DOWN_CAKE = 34
    }
}

object SystemClock {
    @JvmStatic fun uptimeMillis(): Long = System.currentTimeMillis()
    @JvmStatic fun elapsedRealtime(): Long = System.currentTimeMillis()
    @JvmStatic fun sleep(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) {}
    }
}
