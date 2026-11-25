package io.github.onneau.sceneradio.data.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.onneau.sceneradio.data.log.AppLog
import io.github.onneau.sceneradio.data.log.LogCategory
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Zoekt de hoes bij een nummer: eerst het nummer bij MusicBrainz (alleen artiest en titel gaan over de lijn),
 * daarna de hoes bij het Cover Art Archive. Gevonden hoezen blijven in de cache van de app, zodat een nummer
 * dat terugkomt niets meer opvraagt. Zonder hoes (of zonder internet) blijft het gewoon bij de koperbalken.
 *
 * Staan artiest en titel in de stream omgedraaid ("Titel - Artiest"), dan vindt de eerste zoekopdracht niets en
 * probeert de app het nog één keer met de twee omgewisseld. Met "Foto van de artiest" aan komt er daarna, als er nog
 * steeds geen hoes is, een foto van de artiest (zie ArtistPhoto).
 *
 * MusicBrainz vraagt om hooguit één verzoek per seconde en een herkenbare User-Agent; daar houdt dit zich aan.
 */
object CoverArt {

    private const val USER_AGENT = "SceneRadio/1.0 ( https://github.com/onneau/sceneradio )"
    private const val MAX_CACHED_COVERS = 150
    private val COVER_SIZES = listOf(500, 250)   // eerst de grote versie (voor de speler), anders de kleine
    private const val MIN_GAP_BETWEEN_REQUESTS_MS = 1100L
    private const val MIN_SCORE = 85
    private const val MAX_RELEASE_GROUPS = 3
    // Verzamelalbums geven nooit een hoes: liever de koperbalken dan het plaatje van een compilatie.
    private val EXCLUDED_TYPES = setOf("compilation", "dj-mix", "mixtape/street")
    private const val MAX_MISSES_REMEMBERED = 300
    // Sinds 1.1 een nieuwe map: de hoezen die eerder zijn opgeslagen, kunnen van een verzamelalbum zijn.
    private const val COVER_FOLDER = "covers-v2"
    private const val OLD_COVER_FOLDER = "covers"

    internal val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    // Eén aanvraag tegelijk bij MusicBrainz; ook de artiestenfoto's (ArtistPhoto) gebruiken dit slot en deze pauze.
    internal val lock = Mutex()
    private var lastMusicBrainzRequest = 0L
    private var oldCacheRemoved = false
    private val misses = LinkedHashSet<String>()   // nummers waarvoor niets is gevonden, tot de app wordt gesloten

    /** De hoes bij dit nummer, of (met withPhoto) een foto van de artiest als er geen hoes is. Draait op de IO-thread. */
    suspend fun findArtwork(context: Context, artist: String, title: String, withPhoto: Boolean): Bitmap? {
        find(context, artist, title)?.let { return it }
        if (!withPhoto) return null
        val photo = ArtistPhoto.find(context, artist)
        AppLog.v(LogCategory.COVER, if (photo == null) "geen foto van de artiest gevonden" else "foto van de artiest gevonden")
        return photo
    }

    /** De hoes bij dit nummer, of null als er geen is. Draait op de IO-thread. */
    suspend fun find(context: Context, artist: String, title: String): Bitmap? = withContext(Dispatchers.IO) {
        val cleanArtist = leadArtist(artist)
        val cleanTitle = cleanTitle(title)
        if (cleanArtist.isBlank() || cleanTitle.isBlank()) return@withContext null

        val key = "${normalize(cleanArtist)}|${normalize(cleanTitle)}"
        val folder = File(context.cacheDir, COVER_FOLDER).apply { mkdirs() }
        val cached = File(folder, sha1(key) + "-500.jpg")
        if (cached.exists()) {
            cached.setLastModified(System.currentTimeMillis())
            return@withContext decode(cached.readBytes())
        }

        lock.withLock {
            // Een andere aanvraag (bijvoorbeeld van de mediabediening) kan de hoes intussen al hebben opgehaald.
            if (cached.exists()) return@withContext decode(cached.readBytes())
            if (key in misses) return@withContext null
            removeOldCache(context)
            var found = lookup(cleanArtist, cleanTitle)
            if (found == null) {
                // Artiest en titel omgewisseld? Dan staat in de stream "Titel - Artiest" en is de titel hier de artiest.
                val swappedArtist = leadArtist(title)
                val swappedTitle = CoverArt.cleanTitle(artist)
                if (swappedArtist.isNotBlank() && swappedTitle.isNotBlank() &&
                    (normalize(swappedArtist) != normalize(cleanArtist) || normalize(swappedTitle) != normalize(cleanTitle))
                ) {
                    found = lookup(swappedArtist, swappedTitle)
                    if (found != null) AppLog.v(LogCategory.COVER, "hoes gevonden met artiest en titel omgewisseld")
                }
            }
            val bytes: ByteArray = found ?: run {
                misses.add(key)
                if (misses.size > MAX_MISSES_REMEMBERED) misses.remove(misses.first())
                return@withContext null
            }
            val bitmap = decode(bytes) ?: return@withContext null
            runCatching { cached.writeBytes(bytes) }
            pruneCache(folder)
            bitmap
        }
    }

    // ------------------------------------------------------------------ zoeken

    private suspend fun lookup(artist: String, title: String): ByteArray? {
        val groups = findReleaseGroups(artist, title)
        for (id in groups) {
            for (size in COVER_SIZES) {
                val bytes = download("https://coverartarchive.org/release-group/$id/front-$size") ?: continue
                if (decode(bytes) != null) return bytes
            }
        }
        return null
    }

    // MusicBrainz: de beste opnames bij artiest + titel, en daarvan de releasegroepen (singles, EP's en albums).
    // Verzamelalbums (Compilation, DJ-mix, Mixtape/Street) en releases van "Various Artists" worden nooit gebruikt.
    // De naam van de artiest moet kloppen, zodat een gelijknamig nummer geen verkeerde hoes geeft.
    private suspend fun findReleaseGroups(artist: String, title: String): List<String> {
        awaitMusicBrainzSlot()

        val url = "https://musicbrainz.org/ws/2/recording/".toHttpUrl().newBuilder()
            .addQueryParameter("query", "artist:\"${lucene(artist)}\" AND recording:\"${lucene(title)}\"")
            .addQueryParameter("fmt", "json")
            .addQueryParameter("limit", "6")
            .build()
        val body = get(url.toString())?.toString(Charsets.UTF_8) ?: return emptyList()
        val recordings: JsonArray = runCatching {
            JsonParser.parseString(body).asJsonObject.getAsJsonArray("recordings")
        }.getOrNull() ?: return emptyList()

        val wanted = normalize(artist)
        val plain = ArrayList<String>()   // gewone singles, EP's en albums
        val other = ArrayList<String>()   // live, soundtrack, remix en dergelijke: alleen als er niets gewoners is
        for (element in recordings) {
            val recording = element as? JsonObject ?: continue
            val score = recording.get("score")?.takeIf { !it.isJsonNull }?.asInt ?: 0
            if (score < MIN_SCORE) continue
            if (!artistMatches(recording, wanted)) continue
            val releases = recording.getAsJsonArray("releases") ?: continue
            for (releaseElement in releases) {
                val release = releaseElement as? JsonObject ?: continue
                if (isVariousArtists(release)) continue
                val group = release.getAsJsonObject("release-group") ?: continue
                val id = group.get("id")?.takeIf { !it.isJsonNull }?.asString ?: continue
                val types = secondaryTypes(group)
                if (types.any { it in EXCLUDED_TYPES }) continue
                val target = if (types.isEmpty()) plain else other
                if (id !in plain && id !in other) target.add(id)
            }
        }
        return (plain + other).take(MAX_RELEASE_GROUPS)
    }

    private fun secondaryTypes(group: JsonObject): List<String> {
        val array = group.getAsJsonArray("secondary-types") ?: return emptyList()
        return array.mapNotNull { element -> element.takeIf { !it.isJsonNull }?.asString?.lowercase() }
    }

    // "Various Artists" als artiest van de release: een verzamelaar, ook als MusicBrainz hem niet zo heeft getypeerd.
    private fun isVariousArtists(release: JsonObject): Boolean {
        val credits = release.getAsJsonArray("artist-credit") ?: return false
        return credits.any { credit ->
            val obj = credit as? JsonObject ?: return@any false
            val names = listOfNotNull(
                obj.get("name")?.takeIf { !it.isJsonNull }?.asString,
                obj.getAsJsonObject("artist")?.get("name")?.takeIf { !it.isJsonNull }?.asString
            )
            names.any { normalize(it) == "variousartists" }
        }
    }

    /** Wacht tot er weer een verzoek naar MusicBrainz mag (hooguit één per seconde). Alleen aanroepen binnen lock. */
    internal suspend fun awaitMusicBrainzSlot() {
        val wait = lastMusicBrainzRequest + MIN_GAP_BETWEEN_REQUESTS_MS - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        lastMusicBrainzRequest = System.currentTimeMillis()
    }

    private fun artistMatches(recording: JsonObject, wanted: String): Boolean {
        val credits = recording.getAsJsonArray("artist-credit") ?: return false
        val names = StringBuilder()
        for (credit in credits) {
            val obj = credit as? JsonObject ?: continue
            obj.get("name")?.takeIf { !it.isJsonNull }?.let { names.append(it.asString).append(' ') }
            obj.getAsJsonObject("artist")?.get("name")?.takeIf { !it.isJsonNull }
                ?.let { names.append(it.asString).append(' ') }
        }
        val found = normalize(names.toString())
        return wanted.isNotEmpty() && (found.contains(wanted) || wanted.contains(found))
    }

    // ------------------------------------------------------------------ netwerk en bestanden

    private fun get(url: String): ByteArray? = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) response.body?.bytes() else null
        }
    } catch (e: Exception) {
        null
    }

    private fun download(url: String): ByteArray? = get(url)?.takeIf { it.isNotEmpty() }

    private fun decode(bytes: ByteArray): Bitmap? =
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()

    // Eenmaal per start van de app: de map met hoezen van vóór 1.1 weggooien.
    private fun removeOldCache(context: Context) {
        if (oldCacheRemoved) return
        oldCacheRemoved = true
        runCatching { File(context.cacheDir, OLD_COVER_FOLDER).deleteRecursively() }
    }

    // Hooguit MAX_CACHED_COVERS hoezen bewaren; de langst ongebruikte gaan eerst weg.
    private fun pruneCache(folder: File) {
        val files = folder.listFiles() ?: return
        if (files.size <= MAX_CACHED_COVERS) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_CACHED_COVERS).forEach { it.delete() }
    }

    // ------------------------------------------------------------------ tekst opschonen

    /** "Artiest feat. Gast", "A & B" of "A, B" wordt de eerste artiest. */
    internal fun leadArtist(raw: String): String {
        val separators = Regex("\\s+(feat\\.?|ft\\.?|featuring|with|x)\\s+|\\s*[&,;/]\\s*|\\s+vs\\.?\\s+", RegexOption.IGNORE_CASE)
        return raw.trim().split(separators).firstOrNull().orEmpty().trim()
    }

    /** Haakjes en "- Radio Edit" eraf: "Titel (Radio Edit)" wordt "Titel". */
    internal fun cleanTitle(raw: String): String =
        raw.replace(Regex("\\s*[(\\[][^)\\]]*[)\\]]"), "")
            .substringBefore(" - ")
            .trim()

    internal fun normalize(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() }

    // In de zoekopdracht van MusicBrainz (Lucene) zijn aanhalingstekens en backslashes bijzondere tekens.
    internal fun lucene(text: String): String = text.replace('"', ' ').replace('\\', ' ').trim()

    internal fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
