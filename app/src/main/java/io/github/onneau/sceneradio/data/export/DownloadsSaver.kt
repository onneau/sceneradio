package io.github.onneau.sceneradio.data.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import io.github.onneau.sceneradio.data.log.AppLog
import io.github.onneau.sceneradio.data.log.LogCategory
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

/** Wat er is opgeslagen en waar, voor de melding aan de gebruiker. */
data class SavedFile(val name: String, val place: String)

/**
 * Zet een bestand in de map Downloads van het apparaat, voor wie het daar met de bestandsbeheerder wil openen
 * (bijvoorbeeld een update op een auto-radio waar het installatiescherm niet vanzelf opent, of een M3U-lijst).
 *
 * Android 10 en nieuwer: echt de map Downloads, zonder toestemming (MediaStore). Oudere versies mogen dat niet
 * zomaar; daar komt het bestand in de eigen map van de app (Android/data/.../files/Download).
 */
object DownloadsSaver {

    fun save(context: Context, displayName: String, mimeType: String, write: (OutputStream) -> Unit): Result<SavedFile> =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToMediaStore(context, displayName, mimeType, write)
            } else {
                saveToAppFolder(context, displayName, write)
            }
        }.also { result ->
            // Elke poging komt in het logboek, gelukt of niet: zo is te zien of opslaan in Downloads werkt.
            result.onSuccess { saved ->
                AppLog.i(LogCategory.DOWNLOADS, "\"$displayName\" opgeslagen in ${saved.place} als \"${saved.name}\"")
            }.onFailure { error ->
                AppLog.e(LogCategory.DOWNLOADS, "opslaan van \"$displayName\" in Downloads mislukt", error)
            }
        }

    /** Kopieert een bestaand bestand (zoals de gedownloade update) naar Downloads. */
    fun saveFile(context: Context, source: File, displayName: String, mimeType: String): Result<SavedFile> =
        save(context, displayName, mimeType) { out -> FileInputStream(source).use { it.copyTo(out) } }

    fun saveText(context: Context, displayName: String, mimeType: String, text: String): Result<SavedFile> =
        save(context, displayName, mimeType) { out -> out.write(text.toByteArray(Charsets.UTF_8)) }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(
        context: Context,
        displayName: String,
        mimeType: String,
        write: (OutputStream) -> Unit
    ): SavedFile {
        val resolver = context.contentResolver
        val pending = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pending)
            ?: error("Android wilde het bestand niet aanmaken in Downloads.")
        try {
            val stream = resolver.openOutputStream(uri) ?: error("Het bestand kon niet worden geopend om te schrijven.")
            stream.use { write(it) }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        // Bestaat de naam al, dan geeft Android er zelf een nummer aan ("naam (1).apk"): de echte naam melden.
        val finalName = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: displayName
        return SavedFile(finalName, "Downloads")
    }

    private fun saveToAppFolder(context: Context, displayName: String, write: (OutputStream) -> Unit): SavedFile {
        val folder = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("Er is geen opslag beschikbaar om het bestand in te zetten.")
        folder.mkdirs()
        val target = File(folder, displayName)
        target.outputStream().use { write(it) }
        return SavedFile(target.name, "de app-map ${folder.absolutePath}")
    }
}
