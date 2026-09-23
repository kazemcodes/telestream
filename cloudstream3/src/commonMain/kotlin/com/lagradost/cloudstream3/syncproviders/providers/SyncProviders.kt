package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.SubtitleAPI
import com.lagradost.cloudstream3.syncproviders.SyncIdName

open class AniListApi : SyncAPI() {
    override val name = "AniList"
    override val idPrefix = "anilist"
    override val syncIdName = SyncIdName.Anilist
    override val mainUrl = "https://anilist.co"
}

open class MALApi : SyncAPI() {
    override val name = "MyAnimeList"
    override val idPrefix = "mal"
    override val syncIdName = SyncIdName.MyAnimeList
    override val mainUrl = "https://myanimelist.net"
}

open class KitsuApi : SyncAPI() {
    override val name = "Kitsu"
    override val idPrefix = "kitsu"
    override val syncIdName = SyncIdName.Kitsu
    override val mainUrl = "https://kitsu.io"
}

open class SimklApi : SyncAPI() {
    override val name = "Simkl"
    override val idPrefix = "simkl"
    override val syncIdName = SyncIdName.Simkl
    override val mainUrl = "https://simkl.com"
}

open class LocalList : SyncAPI() {
    override val name = "LocalList"
    override val idPrefix = "locallist"
    override val syncIdName = SyncIdName.LocalList
}

open class OpenSubtitlesApi : SubtitleAPI() {
    override val name = "OpenSubtitles"
    override val idPrefix = "opensubtitles"
}

open class Addic7ed : SubtitleAPI() {
    override val name = "Addic7ed"
    override val idPrefix = "addic7ed"
}

open class SubDlApi : SubtitleAPI() {
    override val name = "SubDl"
    override val idPrefix = "subdl"
}

open class SubSourceApi : SubtitleAPI() {
    override val name = "SubSource"
    override val idPrefix = "subsource"
}
