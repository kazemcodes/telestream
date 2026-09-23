package com.lagradost.cloudstream3.utils.videoskip

import com.lagradost.cloudstream3.syncproviders.AuthAPI

open class AnimeSkipAuth : AuthAPI() {
    override val name = "AnimeSkip"
    override val idPrefix = "anime-skip"
    override val hasInApp = true
}
