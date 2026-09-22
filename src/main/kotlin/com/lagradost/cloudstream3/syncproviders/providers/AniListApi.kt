package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.SyncIdName

open class AniListApi : SyncAPI() {
    override var name = "AniList"
    override val idPrefix = "anilist"
    override var mainUrl = "https://anilist.co"
    override val syncIdName = SyncIdName.Anilist

    data class Title(
        val english: String? = null,
        val romaji: String? = null,
        val native: String? = null,
        val userPreferred: String? = null
    )

    data class CoverImage(
        val medium: String? = null,
        val large: String? = null,
        val extraLarge: String? = null
    )

    data class MediaCoverImage(
        val extraLarge: String? = null,
        val large: String? = null,
        val medium: String? = null,
        val color: String? = null
    )

    data class MediaTitle(
        val romaji: String? = null,
        val english: String? = null,
        val native: String? = null,
        val userPreferred: String? = null
    )

    data class LikePageInfo(
        val total: Int? = null,
        val currentPage: Int? = null,
        val lastPage: Int? = null,
        val perPage: Int? = null,
        val hasNextPage: Boolean? = null
    )

    data class RecommendedMedia(
        val id: Int = 0,
        val title: Title? = null,
        val coverImage: CoverImage? = null
    )

    data class Recommendation(
        val mediaRecommendation: RecommendedMedia? = null
    )

    data class RecommendationEdge(
        val node: Recommendation? = null
    )

    data class RecommendationConnection(
        val edges: List<RecommendationEdge>? = null
    )

    data class SeasonNextAiringEpisode(
        val episode: Int? = null,
        val timeUntilAiring: Int? = null
    )
}
