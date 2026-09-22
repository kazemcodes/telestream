package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.UiText
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi

enum class SyncIdName {
    Anilist,
    MyAnimeList,
    Kitsu,
    Trakt,
    Imdb,
    Simkl,
    LocalList
}

data class AuthToken(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val accessTokenLifetime: Long? = null,
    val refreshTokenLifetime: Long? = null,
    val payload: String? = null
) {
    fun isAccessTokenExpired(marginSec: Long = 10L): Boolean = false
    fun isRefreshTokenExpired(marginSec: Long = 10L): Boolean = false
}

data class AuthUser(
    val id: String = "",
    val username: String = ""
)

data class AuthData(
    val user: AuthUser? = null,
    val token: AuthToken? = null
)

abstract class AuthAPI {
    open var name: String = "AuthAPI"
    open val idPrefix: String = "auth"
    open val requiresLogin: Boolean = false
    open val createAccountUrl: String? = null
    open val hasOAuth2: Boolean = false
    open val hasPin: Boolean = false
    open val hasInApp: Boolean = false
}

abstract class AuthRepo(open val api: AuthAPI) {
    val idPrefix get() = api.idPrefix
    val name get() = api.name
    val requiresLogin get() = api.requiresLogin

    open fun authUser(): AuthUser? = null
    open fun authData(): AuthData? = null
}

abstract class SyncAPI : AuthAPI() {
    open var requireLibraryRefresh: Boolean = true
    open val mainUrl: String = "NONE"
    open val syncIdName: SyncIdName? = null

    data class LibraryMetadata(
        val allLibraryLists: List<LibraryList> = emptyList()
    )

    data class LibraryList(
        val name: UiText,
        val items: List<Any> = emptyList()
    )

    abstract class AbstractSyncStatus
    data class SyncResult(
        var id: String = "",
        var totalEpisodes: Int? = null,
        var title: String? = null
    )
}

class SyncRepo(override val api: SyncAPI) : AuthRepo(api) {
    val syncIdName = api.syncIdName
    var requireLibraryRefresh: Boolean
        get() = api.requireLibraryRefresh
        set(value) {
            api.requireLibraryRefresh = value
        }

    suspend fun updateStatus(id: String, newStatus: SyncAPI.AbstractSyncStatus): Result<Boolean> =
        Result.success(true)

    suspend fun status(id: String): Result<SyncAPI.AbstractSyncStatus?> =
        Result.success(null)

    suspend fun load(id: String): Result<SyncAPI.SyncResult?> =
        Result.success(null)

    suspend fun library(): Result<SyncAPI.LibraryMetadata?> =
        Result.success(null)
}

open class AccountManager {
    companion object {
        const val NONE_ID: Int = -1
        val aniListApi = AniListApi()
    }
}
