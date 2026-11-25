package io.github.onneau.sceneradio.data

import okhttp3.HttpUrl

/**
 * De standaard zenderlijst: een bestand in de openbare GitHub-repository onneau/sceneradio-m3u.
 * De app haalt het rechtstreeks op via raw.githubusercontent.com. Er is geen account, inlog of token nodig.
 */
object DefaultPlaylistSource {
    const val OWNER = "onneau"
    const val REPO = "sceneradio-m3u"
    const val REF = "main"
    const val PATH = "nederlandse-radio.m3u"
    const val PLAYLIST_NAME = "Nederlandse radio"

    // Hiermee herkent de app deze lijst in de database, zodat een update dezelfde lijst vernieuwt.
    const val SOURCE_ID = "github:$OWNER/$REPO"

    private const val RAW_HOST = "raw.githubusercontent.com"

    /** Het adres van de lijst zelf. */
    const val PLAYLIST_URL = "https://$RAW_HOST/$OWNER/$REPO/$REF/$PATH"

    private val directory: String = PATH.substringBeforeLast('/', "")

    /**
     * Logo's in de M3U zijn relatief aan het M3U-bestand ("logos/npo-radio-1.jpg"). Die worden hier een
     * raw-adres in dezelfde repository. `version` (de versie van de lijst) zit in de URL,
     * zodat nieuwe logo's na een update niet uit de cache komen.
     */
    fun resolveLogo(raw: String?, version: String): String? {
        val logo = raw?.trim().orEmpty()
        if (logo.isEmpty()) return null

        val lower = logo.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return logo
        if (lower.contains("://") || logo.contains("..")) return null

        // De generator zet een logo-versie in de M3U ("logos/x.jpg?v=ab12cd34"); die gebruiken we
        // als cache-sleutel, zodat een vervangen logo ook echt opnieuw wordt opgehaald.
        val clean = logo.removePrefix("./").trimStart('/')
        val pathPart = clean.substringBefore('?')
        val logoVersion = clean.substringAfter('?', "")
            .split('&')
            .firstOrNull { it.startsWith("v=") }
            ?.removePrefix("v=")
            ?.takeIf { it.isNotBlank() }
        val joined = if (directory.isEmpty()) pathPart else "$directory/$pathPart"
        return HttpUrl.Builder()
            .scheme("https")
            .host(RAW_HOST)
            .addPathSegment(OWNER)
            .addPathSegment(REPO)
            .addPathSegment(REF)
            .addPathSegments(joined)
            .addQueryParameter("v", (logoVersion ?: version).take(8))
            .build()
            .toString()
    }
}
