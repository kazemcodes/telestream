package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink

open class Filesim : ExtractorApi() {
    override val name = "Filesim"
    override val mainUrl = "https://files.im"
    override val requiresReferer = true
}

open class FileMoon : ExtractorApi() {
    override val name = "FileMoon"
    override val mainUrl = "https://filemoon.sx"
}

open class FilemoonV2 : FileMoon()

open class MixDrop : ExtractorApi() {
    override val name = "MixDrop"
    override val mainUrl = "https://mixdrop.co"
}

open class Mp4Upload : ExtractorApi() {
    override val name = "Mp4Upload"
    override val mainUrl = "https://www.mp4upload.com"
}

open class StreamTape : ExtractorApi() {
    override val name = "StreamTape"
    override val mainUrl = "https://streamtape.com"
}

open class StreamWishExtractor : ExtractorApi() {
    override val name = "StreamWish"
    override val mainUrl = "https://streamwish.to"
}

open class Streamlare : ExtractorApi() {
    override val name = "Streamlare"
    override val mainUrl = "https://streamlare.com"
}

open class VidHidePro : ExtractorApi() {
    override val name = "VidHidePro"
    override val mainUrl = "https://vidhidepro.com"
}

open class VidHidePro6 : VidHidePro()

open class VidhideExtractor : ExtractorApi() {
    override val name = "Vidhide"
    override val mainUrl = "https://vidhide.com"
}

open class VidStack : ExtractorApi() {
    override val name = "VidStack"
    override val mainUrl = "https://vidstack.io"
}

open class Vidmolyme : ExtractorApi() {
    override val name = "Vidmoly"
    override val mainUrl = "https://vidmoly.me"
}

open class Voe : ExtractorApi() {
    override val name = "Voe"
    override val mainUrl = "https://voe.sx"
}

open class StreamSB : ExtractorApi() {
    override val name = "StreamSB"
    override val mainUrl = "https://streamsb.net"
}

open class StreamSB8 : StreamSB()

open class OkRuSSL : ExtractorApi() {
    override val name = "OkRu"
    override val mainUrl = "https://ok.ru"
}

open class OkRuHTTP : OkRuSSL()

open class PixelDrain : ExtractorApi() {
    override val name = "PixelDrain"
    override val mainUrl = "https://pixeldrain.com"
}

open class Jeniusplay : ExtractorApi() {
    override val name = "Jeniusplay"
    override val mainUrl = "https://jeniusplay.com"
}

open class DoodLaExtractor : ExtractorApi() {
    override val name = "DoodLa"
    override val mainUrl = "https://dood.la"
}

open class DoodYtExtractor : DoodLaExtractor()
