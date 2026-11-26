package io.github.onneau.sceneradio.data.manage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt
import io.github.onneau.sceneradio.i18n.tr

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * Logo's voorbereiden zoals sceneradio-m3u ze bewaart: JPEG, langste zijde maximaal 500 pixels.
 * Fouten komen als IllegalArgumentException met een melding voor de gebruiker.
 */
object LogoTools {

    const val MAX_SIDE = 500
    private const val MAX_BYTES = 12 * 1024 * 1024
    private const val JPEG_QUALITY = 90

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun readLimited(stream: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_BYTES) throw IllegalArgumentException(tr("De afbeelding is te groot (maximaal 12 MB).", "The image is too large (maximum 12 MB)."))
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /** Leest een gekozen afbeelding van het toestel. */
    fun readUri(context: Context, uri: Uri): ByteArray {
        val stream: InputStream? = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            null
        }
        if (stream == null) throw IllegalArgumentException(tr("Deze afbeelding kon niet worden geopend.", "This image could not be opened."))
        return stream.use { readLimited(it) }
    }

    /** Haalt een afbeelding op van een URL. Zoals in de generator moet de server een afbeelding-Content-Type (image/jpeg, image/png enzovoort) teruggeven. */
    fun download(url: String): ByteArray {
        val request = try {
            Request.Builder().url(url.trim()).header("User-Agent", "SceneRadio/1.0").build()
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(tr("Dit is geen geldige URL (begin met http:// of https://).", "This is not a valid URL (start with http:// or https://)."))
        }
        return try {
            client.newCall(request).execute().use { response ->
                if (response.code != 200) {
                    throw IllegalArgumentException(tr("Downloaden mislukt (HTTP ${response.code}).", "Download failed (HTTP ${response.code})."))
                }
                val type = (response.header("Content-Type") ?: "").substringBefore(';').trim().lowercase()
                if (!type.startsWith("image/")) {
                    val shown = if (type.isEmpty()) tr("geen Content-Type", "no Content-Type") else type
                    throw IllegalArgumentException(tr("Dit is geen afbeelding ($shown).", "This is not an image ($shown)."))
                }
                val body = response.body ?: throw IllegalArgumentException(tr("Lege reactie van de server.", "Empty response from the server."))
                readLimited(body.byteStream())
            }
        } catch (e: IOException) {
            throw IllegalArgumentException(tr("Downloaden mislukt: ${e.message ?: "geen verbinding"}.", "Download failed: ${e.message ?: "no connection"}."))
        }
    }

    /** Maakt van een afbeelding een JPEG van maximaal 500 pixels, met een witte achtergrond onder doorzichtige delen. */
    fun toJpeg(source: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException(tr("Dit bestand is geen bruikbare afbeelding. Gebruik PNG of JPG (SVG wordt niet ondersteund).", "This file is not a usable image. Use PNG or JPG (SVG is not supported)."))
        }

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(source, 0, source.size, options)
            ?: throw IllegalArgumentException(tr("Dit bestand is geen bruikbare afbeelding.", "This file is not a usable image."))

        val longest = max(decoded.width, decoded.height)
        val scaled = if (longest > MAX_SIDE) {
            val factor = MAX_SIDE.toFloat() / longest
            Bitmap.createScaledBitmap(
                decoded,
                max(1, (decoded.width * factor).roundToInt()),
                max(1, (decoded.height * factor).roundToInt()),
                true
            )
        } else {
            decoded
        }

        val flat = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flat)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(scaled, 0f, 0f, null)

        val out = ByteArrayOutputStream()
        flat.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        return out.toByteArray()
    }
}

/**
 * Logo's die alleen op dit toestel staan. Per zender één bestand
 * "<id>-<hash>.jpg": met de hash in de naam krijgt een nieuw logo ook een nieuwe URL, dus geen oude cache.
 */
object LocalLogos {

    private fun dir(context: Context): File = File(context.filesDir, "logos").apply { mkdirs() }

    /** Een logo uit de eigen opslag van de app (niet uit de assets en niet van internet). */
    fun isLocal(url: String?): Boolean =
        url != null && url.startsWith("file://") && !url.startsWith("file:///android_asset")

    private fun filesOf(context: Context, stationId: Long): List<File> {
        val pattern = Regex("^$stationId-[0-9a-f]{8}\\.jpg$")
        return dir(context).listFiles { file -> pattern.matches(file.name) }?.toList().orEmpty()
    }

    /** Bewaart het logo en geeft de URL terug waarmee het in de lijst getoond wordt. */
    fun save(context: Context, stationId: Long, jpeg: ByteArray): String {
        val target = File(dir(context), "$stationId-${sha256Hex(jpeg).take(8)}.jpg")
        target.writeBytes(jpeg)
        filesOf(context, stationId).filter { it != target }.forEach { it.delete() }
        return Uri.fromFile(target).toString()
    }

    /** Ruimt het lokale logo van een zender op (bijvoorbeeld als de zender wordt verwijderd). */
    fun delete(context: Context, stationId: Long) {
        filesOf(context, stationId).forEach { it.delete() }
    }
}
