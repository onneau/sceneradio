package io.github.onneau.sceneradio.data.log

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.onneau.sceneradio.data.export.DownloadsSaver
import io.github.onneau.sceneradio.data.export.SavedFile
import java.io.File
import io.github.onneau.sceneradio.i18n.tr

/**
 * Het logboek als tekstbestand naar buiten brengen: opslaan in Downloads of delen met een andere app.
 * Beide doen echt werk en horen dus niet op de hoofdthread te draaien.
 */
object LogExport {

    private const val MIME = "text/plain"
    private const val FILE_PREFIX = "sceneradio-logboek-"

    fun saveToDownloads(context: Context, withSystemLog: Boolean, withDeviceInfo: Boolean): Result<SavedFile> {
        val text = AppLog.exportText(context, withSystemLog, withDeviceInfo)
        // DownloadsSaver zet zelf een regel in het logboek over het resultaat.
        return DownloadsSaver.saveText(context, AppLog.exportFileName(), MIME, text)
    }

    /** Het deelmenu van Android met het logboek als bijlage (via de FileProvider, map exports in de cache). */
    fun shareIntent(context: Context, withSystemLog: Boolean, withDeviceInfo: Boolean): Result<Intent> =
        runCatching {
            val text = AppLog.exportText(context, withSystemLog, withDeviceInfo)
            val folder = File(context.cacheDir, "exports").apply { mkdirs() }
            folder.listFiles()?.filter { it.name.startsWith(FILE_PREFIX) }?.forEach { it.delete() }
            val file = File(folder, AppLog.exportFileName())
            file.writeText(text, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = MIME
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Intent.createChooser(send, tr("Logboek delen", "Share log")).apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }.also { result ->
            result.onSuccess {
                AppLog.i(
                    LogCategory.LOGBOEK,
                    "logboek aan het deelmenu gegeven (systeemlog: ${if (withSystemLog) "ja" else "nee"}, " +
                        "apparaatgegevens: ${if (withDeviceInfo) "ja" else "nee"})"
                )
            }.onFailure { error ->
                AppLog.e(LogCategory.LOGBOEK, "delen van het logboek mislukt", error)
            }
        }
}
