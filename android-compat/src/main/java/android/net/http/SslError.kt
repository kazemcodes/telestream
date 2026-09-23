package android.net.http

open class SslError @JvmOverloads constructor(
    private val _error: Int = 0,
    private val _certificate: SslCertificate? = null,
    private val _url: String = ""
) {
    companion object {
        const val SSL_NOTYETVALID = 0
        const val SSL_EXPIRED = 1
        const val SSL_IDMISMATCH = 2
        const val SSL_UNTRUSTED = 3
        const val SSL_DATE_INVALID = 4
        const val SSL_INVALID = 5
    }

    open fun hasError(error: Int): Boolean = (this._error and (1 shl error)) != 0
    open fun getPrimaryError(): Int = _error
    open fun getUrl(): String = _url
    open fun getCertificate(): SslCertificate? = _certificate
}

val SslError.certificate: SslCertificate? get() = getCertificate()
val SslError.url: String get() = getUrl()
val SslError.primaryError: Int get() = getPrimaryError()
