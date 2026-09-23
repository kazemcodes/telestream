package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.syncproviders.providers.Addic7ed
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi
import com.lagradost.cloudstream3.syncproviders.providers.KitsuApi
import com.lagradost.cloudstream3.syncproviders.providers.LocalList
import com.lagradost.cloudstream3.syncproviders.providers.MALApi
import com.lagradost.cloudstream3.syncproviders.providers.OpenSubtitlesApi
import com.lagradost.cloudstream3.syncproviders.providers.SimklApi
import com.lagradost.cloudstream3.syncproviders.providers.SubDlApi
import com.lagradost.cloudstream3.syncproviders.providers.SubSourceApi
import com.lagradost.cloudstream3.utils.videoskip.AnimeSkipAuth

abstract class AccountManager {
    companion object {
        const val NONE_ID: Int = -1
        const val APP_STRING = "cloudstreamapp"

        val malApi = MALApi()
        val kitsuApi = KitsuApi()
        val aniListApi = AniListApi()
        val simklApi = SimklApi()
        val localListApi = LocalList()

        val openSubtitlesApi = OpenSubtitlesApi()
        val addic7ed = Addic7ed()
        val subDlApi = SubDlApi()
        val subSourceApi = SubSourceApi()
        val animeSkipApi = AnimeSkipAuth()

        fun accounts(prefix: String): Array<AuthData> = arrayOf()
        fun updateAccounts(prefix: String, array: Array<AuthData>) {}
        fun updateAccountsId(prefix: String, id: Int) {}
        fun accountId(prefix: String): Int = NONE_ID
    }
}
