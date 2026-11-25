package io.github.onneau.sceneradio.playback

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import io.github.onneau.sceneradio.data.local.RadioDatabase
import io.github.onneau.sceneradio.data.log.AppLog
import io.github.onneau.sceneradio.data.log.LogCategory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Zenderlogo's voor Android Auto.
 *
 * Android Auto laadt de plaatjes in de lijsten alleen als content://-adres (of android.resource://), niet
 * als gewoon web-adres. Deze provider haalt een logo zelf op, maakt er een klein PNG-plaatje
 * van (hooguit 256 punten) en bewaart dat in de cache, zodat Android Auto het meteen kan tonen.
 *
 * Alleen logo's van zenders in de eigen lijst worden geleverd; de provider haalt dus nooit zomaar een
 * willekeurig adres op voor een andere app.
 *
 * Ook de hoes van het nummer dat nu speelt (of de foto van de artiest) gaat via deze provider: de service bewaart
 * hem als bestand in de cache (cache/auto-covers) en geeft Android Auto een content://-adres daarnaar, zodat ook het
 * kleine scherm naast de navigatie (splitscreen) het plaatje toont. Alleen bestanden die de service zelf heeft
 * geschreven worden geleverd, op een naam van 64 hexadecimale tekens (dus nooit een pad ergens anders heen).
 *
 * Elke weigering komt in het logboek (Instellingen > Over > Logboek, filter Auto), zodat te zien is waarom een
 * plaatje niet verscheen.
 */
class ArtworkProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String =
        if (uri.pathSegments.firstOrNull() == KIND_COVER) "image/jpeg" else "image/png"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val context = context ?: throw FileNotFoundException("geen context")
        if (mode != "r") reject("alleen lezen toegestaan (gevraagd: $mode)")

        return when (uri.pathSegments.firstOrNull()) {
            KIND_LOGO -> openLogo(context, uri)
            KIND_COVER -> openCover(context, uri)
            else -> reject("onbekend adres")
        }
    }

    // Een weigering komt altijd in het logboek: zonder die regel is niet te zien waarom Android Auto geen plaatje kreeg.
    private fun reject(reason: String): Nothing {
        AppLog.i(LogCategory.AUTO, "plaatje voor Android Auto geweigerd: $reason")
        throw FileNotFoundException(reason)
    }

    private fun openLogo(context: Context, uri: Uri): ParcelFileDescriptor {
        val logoUrl = decodeLogoUrl(uri) ?: reject("onbekend logo (het adres is niet te lezen)")
        val file = cacheFile(context, logoUrl)
        if (file.isFile && file.length() > 0L) {
            AppLog.v(LogCategory.AUTO, "zenderlogo voor Android Auto uit de cache gegeven")
        } else {
            // Per logo één download tegelijk; Android Auto vraagt vaak meerdere keren om hetzelfde plaatje.
            val lock = locks.getOrPut(file.name) { Any() }
            synchronized(lock) {
                if (!file.isFile || file.length() == 0L) {
                    if (!isKnownLogo(context, logoUrl)) reject("geen zenderlogo (het staat bij geen zender in de lijst)")
                    download(context, logoUrl, file)
                }
            }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun openCover(context: Context, uri: Uri): ParcelFileDescriptor {
        val segments = uri.pathSegments
        val id = segments.getOrNull(1)?.takeIf { segments.size == 2 && COVER_ID.matches(it) }
            ?: reject("onbekende hoes (het adres klopt niet)")
        val file = File(File(context.cacheDir, COVER_FOLDER), "$id.jpg")
        if (!file.isFile || file.length() == 0L) reject("hoes staat niet (meer) in de cache")
        AppLog.v(LogCategory.AUTO, "hoes of foto voor Android Auto uit de cache gegeven")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    // Dit is geen gegevensbron: alleen plaatjes via openFile.
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    // Staat het adres bij een zender in de eigen lijst? Bij een onbekend adres één keer opnieuw kijken, want de lijst kan net veranderd zijn.
    private fun isKnownLogo(context: Context, logoUrl: String): Boolean {
        if (logoUrl in knownLogos) return true
        val dao = RadioDatabase.getInstance(context.applicationContext).stationDao()
        knownLogos = dao.getEnabledStationsSync().mapNotNull { it.logoUrl?.trim()?.takeIf { url -> url.isNotEmpty() } }.toSet()
        return logoUrl in knownLogos
    }

    private fun download(context: Context, logoUrl: String, target: File) {
        val url = logoUrl.toHttpUrlOrNull()
        if (url == null || (url.scheme != "https" && url.scheme != "http")) {
            reject("het logo-adres is ongeldig")
        }
        val request = Request.Builder().url(url).build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw FileNotFoundException("HTTP ${response.code}")
                val bytes = response.body?.byteStream()?.use { readLimited(it) }
                    ?: throw FileNotFoundException("leeg antwoord")
                val png = toSmallPng(bytes) ?: throw FileNotFoundException("geen plaatje")
                val folder = target.parentFile ?: throw FileNotFoundException("geen map")
                folder.mkdirs()
                val temp = File(folder, target.name + ".tmp")
                temp.writeBytes(png)
                if (!temp.renameTo(target)) {
                    temp.delete()
                    throw FileNotFoundException("opslaan mislukt")
                }
                prune(folder)
            }
            AppLog.v(LogCategory.AUTO, "zenderlogo voor Android Auto opgehaald (${url.host})")
        } catch (error: FileNotFoundException) {
            AppLog.i(LogCategory.AUTO, "zenderlogo voor Android Auto niet beschikbaar (${url.host}): ${error.message}")
            throw error
        } catch (error: Exception) {
            AppLog.i(
                LogCategory.AUTO,
                "zenderlogo voor Android Auto ophalen mislukt (${url.host}): ${error.javaClass.simpleName}"
            )
            throw FileNotFoundException(error.message ?: error.javaClass.simpleName)
        }
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > MAX_DOWNLOAD_BYTES) throw FileNotFoundException("plaatje te groot")
        }
        return out.toByteArray()
    }

    /** Verkleint het plaatje tot hooguit 256 punten en geeft het terug als PNG (ook als het logo een JPEG of WebP was). */
    private fun toSmallPng(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_SIDE && bounds.outHeight / (sample * 2) >= MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

        val longest = max(decoded.width, decoded.height)
        val bitmap = if (longest > MAX_SIDE) {
            val ratio = MAX_SIDE.toFloat() / longest
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * ratio).roundToInt().coerceAtLeast(1),
                (decoded.height * ratio).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            decoded
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    // De cache klein houden: de oudste plaatjes verdwijnen als er meer dan 300 zijn.
    private fun prune(folder: File) {
        val files = folder.listFiles()?.filter { it.isFile } ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_FILES).forEach { it.delete() }
    }

    companion object {
        private const val MAX_SIDE = 256
        private const val MAX_DOWNLOAD_BYTES = 3 * 1024 * 1024
        private const val MAX_FILES = 300
        private const val MAX_COVERS = 12
        private const val KIND_LOGO = "logo"
        private const val KIND_COVER = "cover"
        private const val COVER_FOLDER = "auto-covers"
        private val COVER_ID = Regex("[0-9a-f]{64}")

        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build()
        }

        private val locks = ConcurrentHashMap<String, Any>()

        @Volatile
        private var knownLogos: Set<String> = emptySet()

        private fun sha256Hex(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        private fun cacheFile(context: Context, logoUrl: String): File =
            File(File(context.cacheDir, "auto-logos"), sha256Hex(logoUrl) + ".png")

        private fun decodeLogoUrl(uri: Uri): String? {
            val segments = uri.pathSegments
            if (segments.size != 2 || segments[0] != KIND_LOGO) return null
            return runCatching {
                String(Base64.decode(segments[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8)
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }

        /**
         * Het content://-adres waaronder Android Auto het logo van een zender kan laden, of null als de zender
         * geen logo heeft. Het adres is meteen klaar; het plaatje zelf wordt pas opgehaald als Android Auto erom vraagt.
         */
        fun contentUriFor(context: Context, logoUrl: String?): Uri? {
            val raw = logoUrl?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val key = Base64.encodeToString(
                raw.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            return Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority("${context.packageName}.artwork")
                .appendPath(KIND_LOGO)
                .appendPath(key)
                .build()
        }

        /**
         * Bewaart de hoes (of foto van de artiest) van het nummer dat nu speelt en geeft het content://-adres terug,
         * of null als het opslaan mislukte. Elk nummer krijgt een eigen adres, zodat Android Auto geen oude hoes
         * uit zijn geheugen toont; er blijven hooguit 12 hoezen in de cache. Draait op een achtergrondthread.
         */
        fun saveCover(context: Context, artist: String, title: String, jpeg: ByteArray): Uri? = runCatching {
            val id = sha256Hex("$artist\n$title")
            val folder = File(context.cacheDir, COVER_FOLDER).apply { mkdirs() }
            val target = File(folder, "$id.jpg")
            val temp = File(folder, "$id.jpg.tmp")
            temp.writeBytes(jpeg)
            check(temp.renameTo(target)) { "opslaan mislukt" }
            folder.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }?.dropLast(MAX_COVERS)?.forEach { it.delete() }
            Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority("${context.packageName}.artwork")
                .appendPath(KIND_COVER)
                .appendPath(id)
                .build()
        }.getOrNull()
    }
}
