package io.github.onneau.sceneradio.data.api

import io.github.onneau.sceneradio.data.DefaultPlaylistSource
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.InetAddress
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import io.github.onneau.sceneradio.i18n.tr

data class RadioBrowserStationItem(
    val stationuuid: String,
    val name: String,
    val url: String,
    @SerializedName("url_resolved") val urlResolved: String?,
    val favicon: String?,
    val tags: String?,
    val country: String?,
    val bitrate: Int?
)

/** De standaard zenderlijst zoals hij nu in de openbare repository staat; `sha` is de SHA-256 van de inhoud. */
data class PlaylistFile(
    val sha: String,
    val text: String
)

object RadioApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private const val USER_AGENT = "SceneRadio/1.0"
    private const val FALLBACK_RADIO_BROWSER = "https://de1.api.radio-browser.info"

    // Bovengrens voor gedownloade afspeellijsten, zodat een enorm bestand niet het geheugen vult.
    private const val MAX_IMPORT_BYTES = 5L * 1024 * 1024

    // Leest de body als tekst, of geeft null terug als die groter is dan MAX_IMPORT_BYTES.
    private fun readBodyLimited(response: Response): String? {
        val body = response.body ?: return ""
        if (body.source().request(MAX_IMPORT_BYTES + 1)) return null
        return body.string()
    }

    // Zelfde grens, maar als bytes (voor de controlesom van de standaardlijst).
    private fun readBytesLimited(response: Response): ByteArray? {
        val body = response.body ?: return ByteArray(0)
        if (body.source().request(MAX_IMPORT_BYTES + 1)) return null
        return body.bytes()
    }

    @Volatile
    private var cachedBaseHost: String? = null

    // DNS van all.api.radio-browser.info levert IP-adressen op.
    // Open nooit direct HTTPS naar een IP-adres. Gebruik alleen een geldige hostnaam,
    // en anders direct https://de1.api.radio-browser.info (nooit www.radio-browser.info).
    suspend fun getRadioBrowserBaseUrl(): String = withContext(Dispatchers.IO) {
        cachedBaseHost?.let { return@withContext it }

        var resolvedHost = "de1.api.radio-browser.info"
        try {
            val addresses = InetAddress.getAllByName("all.api.radio-browser.info")
            for (addr in addresses.toList().shuffled()) {
                val candidateHost = addr.canonicalHostName
                if (!candidateHost.isNullOrBlank() &&
                    candidateHost.endsWith(".radio-browser.info", ignoreCase = true) &&
                    !candidateHost.startsWith("www.") &&
                    !candidateHost.matches(Regex("^[0-9.:]+$"))
                ) {
                    resolvedHost = candidateHost
                    break
                }
            }
        } catch (_: Exception) {
            resolvedHost = "de1.api.radio-browser.info"
        }

        val baseUrl = "https://$resolvedHost"
        cachedBaseHost = baseUrl
        baseUrl
    }

    // Zoeken via /json/stations/byname/{term} of /json/stations/search
    suspend fun searchStationsByName(term: String): Result<List<RadioBrowserStationItem>> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getRadioBrowserBaseUrl()
            val encoded = URLEncoder.encode(term.trim(), "UTF-8")
            val url = "$baseUrl/json/stations/byname/$encoded?limit=25&hidebroken=true"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception(tr("Radio Browser fout: HTTP ${response.code}", "Radio Browser error: HTTP ${response.code}")))
                }
                val body = response.body?.string() ?: return@withContext Result.success(emptyList())
                val type = object : TypeToken<List<RadioBrowserStationItem>>() {}.type
                val list: List<RadioBrowserStationItem> = gson.fromJson(body, type)
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Zoeken via /json/stations/bytag/{term}
    suspend fun searchStationsByTag(tag: String): Result<List<RadioBrowserStationItem>> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getRadioBrowserBaseUrl()
            val encoded = URLEncoder.encode(tag.trim(), "UTF-8")
            val url = "$baseUrl/json/stations/bytag/$encoded?limit=25&hidebroken=true"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception(tr("Radio Browser fout: HTTP ${response.code}", "Radio Browser error: HTTP ${response.code}")))
                }
                val body = response.body?.string() ?: return@withContext Result.success(emptyList())
                val type = object : TypeToken<List<RadioBrowserStationItem>>() {}.type
                val list: List<RadioBrowserStationItem> = gson.fromJson(body, type)
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Bij een keuze /json/url/{stationuuid} aanroepen (registreert klik en geeft resolved stream)
    suspend fun registerClickAndResolveUrl(stationuuid: String): String? = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getRadioBrowserBaseUrl()
            val url = "$baseUrl/json/url/$stationuuid"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                json.get("url")?.asString
            }
        } catch (_: Exception) {
            null
        }
    }

    // Naam vergelijken zonder hoofdletters, leestekens en algemene woorden ("Radio 538 FM" == "538").
    private fun normalizeStationName(name: String): String =
        name.lowercase()
            .replace(Regex("\\b(radio|fm|online|live|stream|hd)\\b"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private fun normalizeStreamUrl(url: String): String =
        url.lowercase()
            .substringBefore("?")
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .trimEnd('/')

    // Logo ophalen via Radio-Browser. Volgorde van zekerheid: dezelfde stream-URL, daarna dezelfde
    // naam, daarna een naam die ermee begint. Anders krijgt een zender het logo van een andere.
    suspend fun findLogoForStation(stationName: String, streamUrl: String? = null): String? =
        withContext(Dispatchers.IO) {
            try {
                // Voorvoegsels als "Radio-" (komt voor in DAB-lijsten) maken zoeken onmogelijk.
                val query = stationName
                    .replace(Regex("^\\s*radio\\s*[-–:]\\s*", RegexOption.IGNORE_CASE), "")
                    .trim()
                    .ifBlank { stationName }

                val candidates = searchStationsByName(query).getOrNull()
                    ?.filter { !it.favicon.isNullOrBlank() }
                    ?: return@withContext null

                if (!streamUrl.isNullOrBlank()) {
                    val wanted = normalizeStreamUrl(streamUrl)
                    val sameStream = candidates.firstOrNull {
                        normalizeStreamUrl(it.url) == wanted ||
                            it.urlResolved?.let { resolved -> normalizeStreamUrl(resolved) == wanted } == true
                    }
                    if (sameStream != null) return@withContext sameStream.favicon
                }

                val target = normalizeStationName(stationName)
                if (target.isBlank()) return@withContext null

                val sameName = candidates.firstOrNull { normalizeStationName(it.name) == target }
                if (sameName != null) return@withContext sameName.favicon

                if (target.length >= 4) {
                    val prefixMatch = candidates.firstOrNull {
                        val candidateName = normalizeStationName(it.name)
                        candidateName.length >= 4 &&
                            (candidateName.startsWith(target) || target.startsWith(candidateName))
                    }
                    if (prefixMatch != null) return@withContext prefixMatch.favicon
                }
                null
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Haalt de standaard zenderlijst op uit de openbare repository (geen inlog en geen token). De versie van de lijst
     * is de SHA-256 van de inhoud: zo ziet de app een wijziging zonder de GitHub-API, die maar weinig verzoeken toestaat.
     */
    suspend fun fetchDefaultPlaylist(): Result<PlaylistFile> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(DefaultPlaylistSource.PLAYLIST_URL)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> Result.failure(Exception(tr("De zenderlijst is niet gevonden (404).", "The station list was not found (404).")))
                    response.code == 429 -> Result.failure(Exception(tr("GitHub-limiet bereikt; probeer het later opnieuw.", "GitHub rate limit reached; try again later.")))
                    !response.isSuccessful -> Result.failure(Exception(tr("GitHub-fout: HTTP ${response.code}", "GitHub error: HTTP ${response.code}")))
                    else -> {
                        val bytes = readBytesLimited(response)
                            ?: return@withContext Result.failure(Exception(tr("Bestand is te groot (max 5 MB)", "File is too large (max 5 MB)")))
                        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                        Result.success(PlaylistFile(sha, String(bytes, Charsets.UTF_8)))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Directe URL ophalen (HTTP of HTTPS)
    suspend fun fetchUrlContent(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception(tr("Kon URL niet downloaden (HTTP ${response.code})", "Could not download URL (HTTP ${response.code})")))
                }
                val text = readBodyLimited(response)
                    ?: return@withContext Result.failure(Exception(tr("Afspeellijst is te groot (max 5 MB)", "Playlist is too large (max 5 MB)")))
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
