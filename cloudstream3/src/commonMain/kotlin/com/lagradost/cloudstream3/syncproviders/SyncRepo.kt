package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.Score

data class AuthToken(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val accessTokenLifetime: Long? = null,
    val refreshTokenLifetime: Long? = null,
    val payload: String? = null,
)

data class AuthUser(
    val name: String?,
    val id: Int,
    val profilePicture: String? = null,
)

data class AuthData(
    val user: AuthUser,
    val token: AuthToken,
)

abstract class AuthAPI {
    open val name: String = "NONE"
    open val idPrefix: String = "NONE"
    open val icon: Int? = null
    open val requiresLogin: Boolean = false
    open val createAccountUrl: String? = null
    open val hasOAuth2: Boolean = false
    open val hasPin: Boolean = false
    open val hasInApp: Boolean = false
    open val inAppLoginRequirement: Any? = null
    open fun isValidRedirectUrl(url: String): Boolean = false
}

abstract class SyncAPI : AuthAPI() {
    open var requireLibraryRefresh: Boolean = true
    open val mainUrl: String = "NONE"
    open val syncIdName: SyncIdName? = null

    abstract class AbstractSyncStatus {
        abstract var score: Score?
        abstract var watchedEpisodes: Int?
        abstract var isFavorite: Boolean?
        abstract var maxEpisodes: Int?
    }

    data class SyncResult(
        var id: String,
        var totalEpisodes: Int? = null,
        var title: String? = null,
        var publicScore: Score? = null,
    )

    data class LibraryMetadata(
        val total: Int = 0
    )
}

open class AuthRepo(open val api: AuthAPI) {
    fun isValidRedirectUrl(url: String) = false
    val idPrefix get() = api.idPrefix
    val name get() = api.name
    val icon get() = api.icon
    val requiresLogin get() = api.requiresLogin
    val createAccountUrl get() = api.createAccountUrl
    val hasOAuth2 get() = api.hasOAuth2
    val hasPin get() = api.hasPin
    val hasInApp get() = api.hasInApp
    val inAppLoginRequirement get() = api.inAppLoginRequirement
    val isAvailable get() = !api.requiresLogin
}

open class PlainAuthRepo(api: AuthAPI) : AuthRepo(api)

open class SyncRepo(override val api: SyncAPI) : AuthRepo(api) {
    val syncIdName = api.syncIdName
    var requireLibraryRefresh: Boolean
        get() = api.requireLibraryRefresh
        set(value) {
            api.requireLibraryRefresh = value
        }

    suspend fun updateStatus(id: String, newStatus: SyncAPI.AbstractSyncStatus): Result<Boolean> =
        Result.success(false)

    suspend fun status(id: String): Result<SyncAPI.AbstractSyncStatus?> = Result.success(null)

    suspend fun load(id: String): Result<SyncAPI.SyncResult?> = Result.success(null)

    suspend fun library(): Result<SyncAPI.LibraryMetadata?> = Result.success(null)
}
