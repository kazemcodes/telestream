package com.lagradost.cloudstream3.utils

enum class Qualities(var value: Int, val defaultPriority: Int = 0) {
    Unknown(400, 0),
    P144(144, 1),
    P240(240, 2),
    P360(360, 3),
    P480(480, 4),
    P720(720, 5),
    P1080(1080, 6),
    P1440(1440, 7),
    P2160(2160, 8);

    companion object {
        fun getStringByInt(qual: Int?): String = when (qual) {
            144 -> "144p"
            240 -> "240p"
            360 -> "360p"
            480 -> "480p"
            720 -> "720p"
            1080 -> "1080p"
            1440 -> "1440p"
            2160 -> "4K"
            else -> "Auto"
        }

        fun getStringByIntFull(qual: Int): String = getStringByInt(qual)
    }
}
