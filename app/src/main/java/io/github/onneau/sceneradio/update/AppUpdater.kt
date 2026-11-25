package io.github.onneau.sceneradio.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Een nieuwere versie van de app die als GitHub Release klaarstaat. */
data class UpdateInfo(
    val version: String,
    // De regels van "Wat is nieuw", uit CHANGELOG.md (de workflow zet ze in de Release).
    val notes: List<String>,
    // ISO-tijdstip van GitHub, bijvoorbeeld 2026-11-03T14:30:00Z.
    val publishedAt: String,
    val assetId: Long,
    val assetName: String,
    val sizeBytes: Long,
    // SHA-256 van de APK zoals GitHub die meldt (kan ontbreken).
    val sha256: String?
)

/** Waar de app zijn updates vandaan haalt: de Releases van de openbare repository (geen token nodig). */
object AppUpdateSource {
    const val OWNER = "onneau"
    const val REPO = "sceneradio"
}

/** Vergelijkt versienummers per deel als getal, zodat 1.10 hoger is dan 1.9. */
object VersionNumber {
    fun parts(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V").split('.')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    fun compare(a: String, b: String): Int {
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}

/** Voortgang van het downloaden van een update. */
sealed interface UpdateDownload {
    object Idle : UpdateDownload
    data class Downloading(val done: Long, val total: Long) : UpdateDownload
    object Verifying : UpdateDownload
    data class Ready(val file: File, val version: String) : UpdateDownload
}

/**
 * Zoekt een nieuwere versie in de GitHub Releases, downloadt de APK, controleert hem en geeft hem door aan
 * het installatiescherm van Android. De repository is openbaar: er is geen inlog of token nodig en de app stuurt
 * ook nooit een token mee. Alleen verbindingen naar GitHub zelf (https) worden gevolgd.
 */
object AppUpdater {
    private const val API = "https://api.github.com/repos/${AppUpdateSource.OWNER}/${AppUpdateSource.REPO}"
    private const val USER_AGENT = "SceneRadio-Updater"
    private const val MAX_JSON_BYTES = 1L * 1024 * 1024
    private const val MAX_APK_BYTES = 200L * 1024 * 1024
    private const val PROGRESS_INTERVAL_NS = 150_000_000L

    // Doorverwijzingen volgen we zelf, zodat we alleen naar GitHub-hosts gaan (zie isGitHubHost).
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /** Geeft de nieuwste release, of null als er geen release met een APK is. De vergelijking doet de aanroeper. */
    suspend fun fetchLatest(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$API/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", USER_AGENT)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                // Een openbare repository zonder release geeft 404: er is dan gewoon niets nieuwers.
                if (response.code == 404) return@withContext Result.success(null)
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception(httpMessage(response.code, response.header("X-RateLimit-Remaining"))))
                }
                val body = response.body ?: return@withContext Result.failure(Exception("Leeg antwoord van GitHub"))
                if (body.source().request(MAX_JSON_BYTES + 1)) {
                    return@withContext Result.failure(Exception("Onverwacht groot antwoord van GitHub"))
                }
                Result.success(parseRelease(body.string()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun parseRelease(body: String): UpdateInfo? {
        val json = JSONObject(body)
        val version = json.optString("tag_name").trim().removePrefix("v").removePrefix("V")
        if (version.isEmpty()) return null
        val assets = json.optJSONArray("assets") ?: return null
        var apk: JSONObject? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                apk = asset
                break
            }
        }
        val found = apk ?: return null
        val digest = found.optString("digest")
            .takeIf { it.startsWith("sha256:") }
            ?.removePrefix("sha256:")
            ?.lowercase()
        return UpdateInfo(
            version = version,
            notes = parseNotes(json.optString("body")),
            publishedAt = json.optString("published_at"),
            assetId = found.optLong("id"),
            assetName = found.optString("name"),
            sizeBytes = found.optLong("size"),
            sha256 = digest
        )
    }

    /** De opsommingstekens uit de Release-tekst; zonder opsomming de gewone regels (zonder koppen). */
    internal fun parseNotes(body: String): List<String> {
        if (body.isBlank() || body == "null") return emptyList()
        val lines = body.lines().map { it.trim() }
        val bullets = lines
            .filter { it.startsWith("- ") || it.startsWith("* ") }
            .map { it.drop(2).trim() }
            .filter { it.isNotEmpty() }
        if (bullets.isNotEmpty()) return bullets
        return lines.filter { it.isNotEmpty() && !it.startsWith("#") }
    }

    /**
     * Downloadt de APK van de release naar de cache van de app. Controleert de SHA-256 als GitHub die kent.
     * Een geannuleerde download ruimt zijn halve bestand op en geeft de annulering door.
     */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (done: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val safeVersion = info.version.filter { it.isLetterOrDigit() || it == '.' || it == '-' }
        val target = File(dir, "sceneradio-$safeVersion.apk")
        val part = File(dir, target.name + ".part")
        try {
            val first = Request.Builder()
                .url("$API/releases/assets/${info.assetId}")
                .header("Accept", "application/octet-stream")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", USER_AGENT)
                .build()
            var response = client.newCall(first).execute()
            if (response.isRedirect) {
                // GitHub stuurt je door naar een tijdelijke downloadlink (ook bij een openbare repository).
                val next = response.request.url.resolve(response.header("Location").orEmpty())
                response.close()
                if (next == null || next.scheme != "https" || !isGitHubHost(next.host)) {
                    throw IOException("Onverwachte doorverwijzing van GitHub")
                }
                response = client.newCall(
                    Request.Builder()
                        .url(next)
                        .header("Accept", "application/octet-stream")
                        .header("User-Agent", USER_AGENT)
                        .build()
                ).execute()
            }
            response.use { r ->
                if (!r.isSuccessful) throw IOException(httpMessage(r.code, r.header("X-RateLimit-Remaining")))
                val body = r.body ?: throw IOException("Leeg antwoord van GitHub")
                val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
                if (total > MAX_APK_BYTES) throw IOException("Het bestand is onverwacht groot.")
                val digest = MessageDigest.getInstance("SHA-256")
                var done = 0L
                var lastReport = 0L
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            done += read
                            if (done > MAX_APK_BYTES) throw IOException("Het bestand is onverwacht groot.")
                            val now = System.nanoTime()
                            if (now - lastReport > PROGRESS_INTERVAL_NS) {
                                lastReport = now
                                onProgress(done, total)
                            }
                        }
                    }
                }
                onProgress(done, total)
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                val expected = info.sha256
                if (expected != null && !expected.equals(hash, ignoreCase = true)) {
                    throw IOException("De controlesom van de download klopt niet; het bestand is beschadigd.")
                }
            }
            if (!part.renameTo(target)) throw IOException("Het gedownloade bestand kon niet worden opgeslagen.")
            Result.success(target)
        } catch (e: CancellationException) {
            part.delete()
            throw e
        } catch (e: Exception) {
            part.delete()
            Result.failure(e)
        }
    }

    /** Verwijdert gedownloade updates uit de cache (na een geslaagde update of bij het opstarten). */
    fun clearDownloads(context: Context) {
        File(context.cacheDir, "updates").listFiles()?.forEach { it.delete() }
    }

    /**
     * Controleert de gedownloade APK voordat Android hem te zien krijgt: dezelfde pakketnaam, een hoger
     * versienummer en dezelfde ondertekening als de geïnstalleerde app. Geeft een foutmelding, of null als alles klopt.
     */
    @Suppress("DEPRECATION")
    fun verify(context: Context, file: File): String? {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = try {
            pm.getPackageArchiveInfo(file.absolutePath, flags)
        } catch (e: Exception) {
            null
        } ?: return "Het gedownloade bestand is geen geldige app."
        if (archive.packageName != context.packageName) {
            return "Het gedownloade bestand hoort niet bij SceneRadio (andere pakketnaam)."
        }
        val installed = try {
            pm.getPackageInfo(context.packageName, flags)
        } catch (e: Exception) {
            return "De geïnstalleerde app kon niet worden gelezen."
        }
        if (versionCodeOf(archive) <= versionCodeOf(installed)) {
            return "Het gedownloade bestand is niet nieuwer dan de geïnstalleerde versie."
        }
        val newSigners = signerHashes(archive)
        val oldSigners = signerHashes(installed)
        if (newSigners.isEmpty()) {
            // Op oude Android-versies (voor 9) kan de handtekening hier niet gelezen worden; Android zelf controleert dan bij het installeren.
            return if (Build.VERSION.SDK_INT >= 28) "De update bevat geen handtekening en wordt niet geïnstalleerd." else null
        }
        if (newSigners != oldSigners) {
            return "De update is met een andere sleutel ondertekend dan de geïnstalleerde app en wordt niet geïnstalleerd."
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signerHashes(info: PackageInfo): Set<String> {
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= 28) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
        } else {
            info.signatures
        }
        if (signatures == null) return emptySet()
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    /** Mag de app andere apps installeren? Vanaf Android 8 is daar een eenmalige toestemming voor nodig. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Opent het installatiescherm van Android voor de gedownloade APK (via de FileProvider van de app). */
    fun installIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Het Android-scherm met de schakelaar "Onbekende apps installeren" voor deze app. */
    fun permissionIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** De downloadlink van GitHub staat op github.com of op een subdomein van githubusercontent.com. */
    internal fun isGitHubHost(host: String): Boolean {
        val h = host.lowercase()
        return h == "github.com" || h.endsWith(".github.com") || h == "githubusercontent.com" || h.endsWith(".githubusercontent.com")
    }

    private fun httpMessage(code: Int, rateRemaining: String?): String = when (code) {
        403, 429 -> if (rateRemaining == "0" || code == 429) "GitHub-limiet bereikt; probeer het later opnieuw." else "GitHub weigert het verzoek (403)."
        404 -> "Geen release gevonden (404)."
        else -> "GitHub-fout: HTTP $code"
    }
}
