package xyz.nulldev.androidcompat.webkit

import android.webkit.WebSettings

class HeadlessWebSettings : WebSettings() {
    private var _userAgentString: String? = System.getProperty("http.agent") ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private var _javaScriptEnabled: Boolean = true
    private var _domStorageEnabled: Boolean = true
    private var _databaseEnabled: Boolean = true
    private var _loadsImagesAutomatically: Boolean = true
    private var _blockNetworkImage: Boolean = false
    private var _blockNetworkLoads: Boolean = false
    private var _mediaPlaybackRequiresUserGesture: Boolean = false
    private var _mixedContentMode: Int = MIXED_CONTENT_ALWAYS_ALLOW
    private var _cacheMode: Int = LOAD_DEFAULT
    private var _allowFileAccess: Boolean = true
    private var _allowContentAccess: Boolean = true
    private var _allowFileAccessFromFileURLs: Boolean = true
    private var _allowUniversalAccessFromFileURLs: Boolean = true
    private var _supportZoom: Boolean = true
    private var _builtInZoomControls: Boolean = false
    private var _displayZoomControls: Boolean = false
    private var _useWideViewPort: Boolean = true
    private var _loadWithOverviewMode: Boolean = true
    private var _textZoom: Int = 100
    private var _defaultFontSize: Int = 16
    private var _defaultFixedFontSize: Int = 13
    private var _minimumFontSize: Int = 8
    private var _minimumLogicalFontSize: Int = 8
    private var _defaultTextEncodingName: String? = "UTF-8"

    override fun getUserAgentString(): String? = _userAgentString
    override fun setUserAgentString(ua: String?) { _userAgentString = ua }

    override fun getJavaScriptEnabled(): Boolean = _javaScriptEnabled
    override fun setJavaScriptEnabled(enabled: Boolean) { _javaScriptEnabled = enabled }

    override fun getDomStorageEnabled(): Boolean = _domStorageEnabled
    override fun setDomStorageEnabled(enabled: Boolean) { _domStorageEnabled = enabled }

    override fun getDatabaseEnabled(): Boolean = _databaseEnabled
    override fun setDatabaseEnabled(enabled: Boolean) { _databaseEnabled = enabled }

    override fun getLoadsImagesAutomatically(): Boolean = _loadsImagesAutomatically
    override fun setLoadsImagesAutomatically(loads: Boolean) { _loadsImagesAutomatically = loads }

    override fun getBlockNetworkImage(): Boolean = _blockNetworkImage
    override fun setBlockNetworkImage(b: Boolean) { _blockNetworkImage = b }

    override fun getBlockNetworkLoads(): Boolean = _blockNetworkLoads
    override fun setBlockNetworkLoads(b: Boolean) { _blockNetworkLoads = b }

    override fun getMediaPlaybackRequiresUserGesture(): Boolean = _mediaPlaybackRequiresUserGesture
    override fun setMediaPlaybackRequiresUserGesture(require: Boolean) { _mediaPlaybackRequiresUserGesture = require }

    override fun getMixedContentMode(): Int = _mixedContentMode
    override fun setMixedContentMode(mode: Int) { _mixedContentMode = mode }

    override fun getCacheMode(): Int = _cacheMode
    override fun setCacheMode(mode: Int) { _cacheMode = mode }

    override fun getAllowFileAccess(): Boolean = _allowFileAccess
    override fun setAllowFileAccess(allow: Boolean) { _allowFileAccess = allow }

    override fun getAllowContentAccess(): Boolean = _allowContentAccess
    override fun setAllowContentAccess(allow: Boolean) { _allowContentAccess = allow }

    override fun getAllowFileAccessFromFileURLs(): Boolean = _allowFileAccessFromFileURLs
    override fun setAllowFileAccessFromFileURLs(allow: Boolean) { _allowFileAccessFromFileURLs = allow }

    override fun getAllowUniversalAccessFromFileURLs(): Boolean = _allowUniversalAccessFromFileURLs
    override fun setAllowUniversalAccessFromFileURLs(allow: Boolean) { _allowUniversalAccessFromFileURLs = allow }

    override fun supportZoom(): Boolean = _supportZoom
    override fun setSupportZoom(support: Boolean) { _supportZoom = support }

    override fun getBuiltInZoomControls(): Boolean = _builtInZoomControls
    override fun setBuiltInZoomControls(enabled: Boolean) { _builtInZoomControls = enabled }

    override fun getDisplayZoomControls(): Boolean = _displayZoomControls
    override fun setDisplayZoomControls(enabled: Boolean) { _displayZoomControls = enabled }

    override fun getUseWideViewPort(): Boolean = _useWideViewPort
    override fun setUseWideViewPort(use: Boolean) { _useWideViewPort = use }

    override fun getLoadWithOverviewMode(): Boolean = _loadWithOverviewMode
    override fun setLoadWithOverviewMode(overview: Boolean) { _loadWithOverviewMode = overview }

    override fun getTextZoom(): Int = _textZoom
    override fun setTextZoom(textZoom: Int) { _textZoom = textZoom }

    override fun getDefaultFontSize(): Int = _defaultFontSize
    override fun setDefaultFontSize(size: Int) { _defaultFontSize = size }

    override fun getDefaultFixedFontSize(): Int = _defaultFixedFontSize
    override fun setDefaultFixedFontSize(size: Int) { _defaultFixedFontSize = size }

    override fun getMinimumFontSize(): Int = _minimumFontSize
    override fun setMinimumFontSize(size: Int) { _minimumFontSize = size }

    override fun getMinimumLogicalFontSize(): Int = _minimumLogicalFontSize
    override fun setMinimumLogicalFontSize(size: Int) { _minimumLogicalFontSize = size }

    override fun getDefaultTextEncodingName(): String? = _defaultTextEncodingName
    override fun setDefaultTextEncodingName(encoding: String?) { _defaultTextEncodingName = encoding }
}
