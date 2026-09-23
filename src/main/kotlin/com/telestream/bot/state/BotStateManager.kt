package com.telestream.bot.state

import java.util.concurrent.ConcurrentHashMap

class BotStateManager {
    // Multi-user debounce cache to prevent rapid double-clicks (350ms window)
    private val userLastAction = ConcurrentHashMap<Long, Long>()

    // In-flight active API request tracker to prevent duplicate requests ("so they won't send it again")
    private val userActiveApiRequests = ConcurrentHashMap<Long, String>()

    // Tracks if user explicitly selected a source for their upcoming text input
    val userSearchPending = ConcurrentHashMap<Long, String>()

    // Tracks if user is in source keyword filter mode
    val userSourceFilterPending = ConcurrentHashMap<Long, Boolean>()

    fun isDebounced(userId: Long, debounceMs: Long = 350L): Boolean {
        val now = System.currentTimeMillis()
        val last = userLastAction[userId] ?: 0L
        if (now - last < debounceMs) {
            return true
        }
        userLastAction[userId] = now
        return false
    }

    fun isRequestInFlight(userId: Long): Boolean {
        return userActiveApiRequests.containsKey(userId)
    }

    fun isRequestInProgress(userId: Long): Boolean {
        return isRequestInFlight(userId)
    }

    fun getInFlightRequestType(userId: Long): String? {
        return userActiveApiRequests[userId]
    }

    fun tryAcquireRequestLock(userId: Long, requestType: String): Boolean {
        return userActiveApiRequests.putIfAbsent(userId, requestType) == null
    }

    fun releaseRequestLock(userId: Long) {
        userActiveApiRequests.remove(userId)
    }
}
