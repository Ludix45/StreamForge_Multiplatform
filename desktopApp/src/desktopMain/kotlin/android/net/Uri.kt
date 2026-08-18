package android.net

import java.net.URI
import java.net.URLEncoder

/** Small compatibility facade for the Android Uri calls used by Scraper.kt. */
class Uri private constructor(private val rawValue: String) {
    private val parsed: URI? get() = runCatching { URI(rawValue) }.getOrNull()
    val host: String? get() = parsed?.host
    val scheme: String? get() = parsed?.scheme
    val authority: String? get() = parsed?.rawAuthority
    val path: String? get() = parsed?.rawPath
    override fun toString(): String = rawValue

    fun getQueryParameter(name: String): String? = parsed?.rawQuery
        ?.split('&')
        ?.firstOrNull { it.substringBefore('=') == name }
        ?.substringAfter('=', "")
        ?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }

    fun buildUpon(): Builder = Builder(rawValue)

    class Builder(private var base: String = "") {
        private val parameters = mutableListOf<Pair<String, String>>()

        fun appendQueryParameter(key: String, value: String): Builder = apply {
            parameters += key to value
        }

        fun scheme(value: String?): Builder = apply {
            base = "${value.orEmpty()}://" + base.removePrefix("https://").removePrefix("http://")
        }

        fun authority(value: String?): Builder = apply {
            base = base.substringBefore("://", "") + "://" + value.orEmpty()
        }

        fun path(value: String?): Builder = apply {
            base = base.trimEnd('/') + "/" + value.orEmpty().trimStart('/')
        }

        fun build(): Uri {
            if (parameters.isEmpty()) return Uri(base)
            val separator = if (base.contains('?')) '&' else '?'
            val query = parameters.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, Charsets.UTF_8)}=${URLEncoder.encode(value, Charsets.UTF_8)}"
            }
            return Uri(base + separator + query)
        }
    }

    companion object {
        fun parse(value: String): Uri = Uri(value)
    }
}
