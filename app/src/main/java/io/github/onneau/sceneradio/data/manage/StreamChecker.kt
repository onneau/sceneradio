package io.github.onneau.sceneradio.data.manage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

data class StreamCheckResult(
    val ok: Boolean,
    val status: Int?,
    val contentType: String,
    // Korte reden voor in de lijst, bijvoorbeeld "HTTP 404" of "Time-out". Leeg als de stream werkt.
    val error: String
)

/**
 * Test of een stream bereikbaar is, met dezelfde regels als check_streams.py in sceneradio-m3u:
 * een GET met `Range: bytes=0-1`; alleen status 200 of 206 met een audio-Content-Type (audio/mpeg, audio/aac enzovoort) telt als goed.
 */
object StreamChecker {

    private const val RETRY_DELAY_MS = 2_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /** Eén poging. Blokkeert; aanroepen vanuit een achtergrondthread. */
    fun checkOnce(url: String): StreamCheckResult {
        val request = try {
            Request.Builder()
                .url(url)
                .header("User-Agent", "NederlandseRadioStreamCheck/1.0")
                .header("Range", "bytes=0-1")
                .header("Accept", "audio/*,*/*;q=0.1")
                .build()
        } catch (e: IllegalArgumentException) {
            return StreamCheckResult(false, null, "", "Ongeldige URL")
        }

        return try {
            client.newCall(request).execute().use { response ->
                val status = response.code
                val contentType = (response.header("Content-Type") ?: "")
                    .substringBefore(';').trim().lowercase()
                when {
                    status != 200 && status != 206 ->
                        StreamCheckResult(false, status, contentType, "HTTP $status")
                    !contentType.startsWith("audio/") -> {
                        val shown = if (contentType.isEmpty()) "geen Content-Type" else contentType
                        StreamCheckResult(false, status, contentType, "Geen audio ($shown)")
                    }
                    else -> StreamCheckResult(true, status, contentType, "")
                }
            }
        } catch (e: Exception) {
            StreamCheckResult(false, null, "", describe(e))
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is UnknownHostException -> "Host onbekend (DNS)"
        is InterruptedIOException -> "Time-out"
        is SSLException -> "TLS-fout"
        is ConnectException -> "Verbinding geweigerd"
        else -> "Verbinding mislukt"
    }

    /** Zoals de generator: bij een fout één nieuwe poging, zodat een hapering niet meteen telt. */
    suspend fun check(url: String): StreamCheckResult = withContext(Dispatchers.IO) {
        var result = checkOnce(url)
        if (!result.ok) {
            delay(RETRY_DELAY_MS)
            result = checkOnce(url)
        }
        result
    }
}
