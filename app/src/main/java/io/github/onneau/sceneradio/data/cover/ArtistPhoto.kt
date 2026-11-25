package io.github.onneau.sceneradio.data.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min

/**
 * Een foto van de artiest, voor als er bij een nummer geen hoes is (Instellingen > Weergave > Visualizer > "Foto van de artiest").
 *
 * Bron: Wikimedia Commons. MusicBrainz kent de artiest (alleen bij een zeer sterke overeenkomst met de naam), verwijst
 * naar Wikidata, en Wikidata kent de foto van de artiest op Wikimedia Commons (eigenschap P18).
 * Alleen de naam van de artiest gaat over de lijn. Is er geen foto, of past de naam niet precies, dan blijft het gewoon bij het logo.
 *
 * De foto wordt vierkant bijgesneden en verkleind tot 500 punten, en blijft in de cache (cache/artist-photos, hooguit 300
 * bestanden). Dat er voor een artiest niets is, wordt ook onthouden (14 dagen), maar niet als het zoeken mislukte door
 * een storing of het ontbreken van internet.
 */
object ArtistPhoto {

    private const val SIZE = 500
    private const val MIN_SIDE = 120
    private const val MIN_ARTIST_SCORE = 95
    private const val MAX_FILES = 300
    private const val MISS_MILLIS = 14L * 24 * 60 * 60 * 1000
    private const val MAX_BYTES = 8 * 1024 * 1024

    private val lock = Mutex()

    /** De foto van de artiest (de eerste artiest van "A & B" of "A feat. B"), of null. Draait op de IO-thread. */
    suspend fun find(context: Context, artist: String): Bitmap? = withContext(Dispatchers.IO) {
        val name = CoverArt.leadArtist(artist)
        val key = CoverArt.normalize(name)
        if (key.length < 2) return@withContext null

        val folder = File(context.cacheDir, "artist-photos").apply { mkdirs() }
        val hash = CoverArt.sha1(key)
        val photo = File(folder, "$hash.jpg")
        val miss = File(folder, "$hash.none")

        readCached(photo, miss)?.let { return@withContext it.bitmap }

        lock.withLock {
            readCached(photo, miss)?.let { return@withContext it.bitmap }

            when (val found = fromWikimedia(name, key)) {
                is Lookup.Found -> {
                    val jpeg = squareJpeg(found.bytes)
                    if (jpeg == null) {
                        runCatching { miss.writeText("onbruikbaar") }
                        null
                    } else {
                        runCatching { photo.writeBytes(jpeg) }
                        prune(folder)
                        decode(jpeg)
                    }
                }
                is Lookup.NotFound -> {
                    runCatching { miss.writeText("geen foto") }
                    prune(folder)
                    null
                }
                is Lookup.Failed -> null
            }
        }
    }

    private class Cached(val bitmap: Bitmap?)

    // Een bewaarde foto, of "weet ik al: geen foto" (Cached met null), of null als er nog niets bekend is.
    private fun readCached(photo: File, miss: File): Cached? {
        if (photo.exists()) {
            photo.setLastModified(System.currentTimeMillis())
            val bitmap = decode(photo.readBytes())
            if (bitmap != null) return Cached(bitmap)
            photo.delete()
        }
        if (miss.exists()) {
            if (System.currentTimeMillis() - miss.lastModified() < MISS_MILLIS) return Cached(null)
            miss.delete()
        }
        return null
    }

    // ------------------------------------------------------------------ bron: Wikimedia Commons

    private suspend fun fromWikimedia(name: String, key: String): Lookup {
        // 1. MusicBrainz: de artiest met precies deze naam en een zeer hoge score
        val searchUrl = "https://musicbrainz.org/ws/2/artist/".toHttpUrl().newBuilder()
            .addQueryParameter("query", "artist:\"${CoverArt.lucene(name)}\"")
            .addQueryParameter("fmt", "json")
            .addQueryParameter("limit", "5")
            .build().toString()
        val search = musicBrainz(searchUrl)
        if (search.failed) return Lookup.Failed
        val artists = json(search.body)?.getAsJsonArray("artists") ?: return Lookup.NotFound
        var mbid: String? = null
        for (element in artists) {
            val artist = element as? JsonObject ?: continue
            val score = artist.get("score")?.takeIf { !it.isJsonNull }?.asInt ?: 0
            if (score < MIN_ARTIST_SCORE) continue
            val artistName = artist.get("name")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            if (CoverArt.normalize(artistName) != key) continue
            mbid = artist.get("id")?.takeIf { !it.isJsonNull }?.asString
            if (mbid != null) break
        }
        if (mbid == null) return Lookup.NotFound

        // 2. MusicBrainz: de verwijzing naar Wikidata
        val relUrl = "https://musicbrainz.org/ws/2/artist/$mbid?inc=url-rels&fmt=json"
        val rels = musicBrainz(relUrl)
        if (rels.failed) return Lookup.Failed
        val relations: JsonArray = json(rels.body)?.getAsJsonArray("relations") ?: return Lookup.NotFound
        var wikidataId: String? = null
        for (element in relations) {
            val relation = element as? JsonObject ?: continue
            if (relation.get("type")?.takeIf { !it.isJsonNull }?.asString != "wikidata") continue
            val resource = relation.getAsJsonObject("url")?.get("resource")?.takeIf { !it.isJsonNull }?.asString ?: continue
            val id = resource.substringAfterLast('/')
            if (Regex("Q\\d+").matches(id)) {
                wikidataId = id
                break
            }
        }
        if (wikidataId == null) return Lookup.NotFound

        // 3. Wikidata: de bestandsnaam van de foto (eigenschap P18); alleen die eigenschap wordt opgevraagd
        val claims = fetch("https://www.wikidata.org/w/api.php?action=wbgetclaims&entity=$wikidataId&property=P18&format=json")
        if (claims.failed) return Lookup.Failed
        val fileName = runCatching {
            json(claims.body)!!
                .getAsJsonObject("claims").getAsJsonArray("P18")[0].asJsonObject
                .getAsJsonObject("mainsnak").getAsJsonObject("datavalue").get("value").asString
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return Lookup.NotFound

        // 4. Wikimedia Commons: de foto zelf, op 500 punten breed
        val image = fetch("https://commons.wikimedia.org/wiki/Special:FilePath/" + Uri.encode(fileName) + "?width=$SIZE")
        if (image.failed) return Lookup.Failed
        return image.body?.takeIf { it.isNotEmpty() }?.let { Lookup.Found(it) } ?: Lookup.NotFound
    }

    // ------------------------------------------------------------------ netwerk en plaatjes

    private sealed class Lookup {
        class Found(val bytes: ByteArray) : Lookup()
        object NotFound : Lookup()
        object Failed : Lookup()
    }

    private class Reply(val body: ByteArray?, val failed: Boolean)

    // Een 404 is "niet gevonden"; alles anders dat misgaat (geen internet, storing, te veel verzoeken) is "mislukt".
    // Een antwoord groter dan 8 MB wordt niet gelezen: dat is nooit een foto of een zoekresultaat.
    private fun fetch(url: String): Reply = try {
        CoverArt.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            when {
                response.isSuccessful -> {
                    val bytes = response.body?.let { readLimited(it) }
                    if (bytes == null) Reply(null, true) else Reply(bytes, false)
                }
                response.code == 404 -> Reply(null, false)
                else -> Reply(null, true)
            }
        }
    } catch (e: Exception) {
        Reply(null, true)
    }

    private fun readLimited(body: ResponseBody): ByteArray? {
        if (body.contentLength() > MAX_BYTES) return null
        return body.byteStream().use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_BYTES) return@use null
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }

    // MusicBrainz: één verzoek per seconde, gedeeld met het zoeken van hoezen.
    private suspend fun musicBrainz(url: String): Reply = CoverArt.lock.withLock {
        CoverArt.awaitMusicBrainzSlot()
        fetch(url)
    }

    private fun json(body: ByteArray?): JsonObject? {
        if (body == null) return null
        return runCatching { JsonParser.parseString(String(body, Charsets.UTF_8)).asJsonObject }.getOrNull()
    }

    private fun decode(bytes: ByteArray): Bitmap? =
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()

    // Vierkant bijsnijden (bij een staande foto iets boven het midden, waar meestal het gezicht staat), hooguit 500 punten, als JPEG.
    private fun squareJpeg(bytes: ByteArray): ByteArray? {
        val source = decode(bytes) ?: return null
        val side = min(source.width, source.height)
        if (side < MIN_SIDE) return null
        val x = (source.width - side) / 2
        val y = ((source.height - side) * 0.3f).toInt()
        val square = Bitmap.createBitmap(source, x, y, side, side)
        val scaled = if (side > SIZE) Bitmap.createScaledBitmap(square, SIZE, SIZE, true) else square
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
            out.toByteArray()
        }
    }

    private fun prune(folder: File) {
        val files = folder.listFiles() ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_FILES).forEach { it.delete() }
    }
}
