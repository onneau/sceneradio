package io.github.onneau.sceneradio.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.onneau.sceneradio.SceneRadioApp
import io.github.onneau.sceneradio.data.manage.LocalLogos
import io.github.onneau.sceneradio.data.manage.LogoTools
import io.github.onneau.sceneradio.data.manage.StreamCheckResult
import io.github.onneau.sceneradio.data.manage.StreamChecker
import io.github.onneau.sceneradio.data.manage.normalizeGenres
import io.github.onneau.sceneradio.data.manage.slugify
import io.github.onneau.sceneradio.data.model.RadioStation
import io.github.onneau.sceneradio.data.model.splitGenres
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Voortgang en uitkomst van "Streams controleren". */
data class StreamCheckState(
    val running: Boolean = false,
    val finished: Boolean = false,
    val total: Int = 0,
    val done: Int = 0,
    // Per zender-id.
    val results: Map<Long, StreamCheckResult> = emptyMap()
)

/** Een gekozen logo, al omgezet naar JPEG (max. 500 px), met een voorbeeld om te tonen. */
class PendingLogo(val jpeg: ByteArray, val preview: Bitmap)

/** De velden van het scherm "Zender toevoegen of bewerken". */
data class StationForm(
    // null = nieuwe zender.
    val editId: Long? = null,
    val name: String = "",
    val url: String = "",
    val genres: String = "",
    val existingLogoUrl: String? = null
)

sealed interface StreamTest {
    object Idle : StreamTest
    object Running : StreamTest
    class Done(val result: StreamCheckResult) : StreamTest
}

/**
 * Alles rond "Zenders beheren": streams controleren, logo's in bulk koppelen en zenders toevoegen of bewerken.
 * Wijzigingen gelden alleen op dit toestel; SceneRadio schrijft nooit naar GitHub.
 */
class ManageViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as SceneRadioApp).repository
    private val appContext = application.applicationContext

    val stations: StateFlow<List<RadioStation>> = repository.allStations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val defaultPlaylistId = MutableStateFlow<Long?>(null)

    // Korte melding bovenaan de lijstschermen na een gelukte actie.
    val notice = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            repository.allStations.collect { defaultPlaylistId.value = repository.defaultPlaylistId() }
        }
    }

    fun clearNotice() {
        notice.value = null
    }

    private fun failureText(e: Exception): String = when (e) {
        is IllegalArgumentException -> e.message ?: "Dit kon niet worden verwerkt."
        else -> "Mislukt: ${e.message ?: e.javaClass.simpleName}"
    }

    // ---------------------------------------------------------------- streams controleren

    val streamCheck = MutableStateFlow(StreamCheckState())
    private var streamJob: Job? = null

    fun startStreamCheck() {
        if (streamCheck.value.running) return
        streamJob = viewModelScope.launch {
            val list = repository.allStations.first().filter { it.isEnabled }
            streamCheck.value = StreamCheckState(running = true, total = list.size)
            val limit = Semaphore(8)
            try {
                coroutineScope {
                    list.map { station ->
                        async {
                            limit.withPermit {
                                val result = StreamChecker.check(station.streamUrl)
                                streamCheck.update {
                                    it.copy(done = it.done + 1, results = it.results + (station.id to result))
                                }
                            }
                        }
                    }.awaitAll()
                }
                streamCheck.update { it.copy(running = false, finished = true) }
            } finally {
                streamCheck.update { it.copy(running = false) }
            }
        }
    }

    fun stopStreamCheck() {
        streamJob?.cancel()
    }

    /** "Verberg": de zender verdwijnt uit de lijsten op dit toestel (Stations toont hem nog wel, om terug te zetten). */
    fun hideStation(stationId: Long) {
        viewModelScope.launch { repository.setStationEnabled(stationId, false) }
    }

    // ---------------------------------------------------------------- logo's in bulk

    val selected = MutableStateFlow<Set<Long>>(emptySet())
    val bulkLogo = MutableStateFlow<PendingLogo?>(null)
    val bulkBusy = MutableStateFlow(false)
    val bulkMessage = MutableStateFlow<String?>(null)

    fun toggleSelected(stationId: Long) {
        selected.update { if (stationId in it) it - stationId else it + stationId }
    }

    fun selectAll(ids: Collection<Long>) {
        selected.value = ids.toSet()
    }

    fun clearSelection() {
        selected.value = emptySet()
    }

    fun prepareBulkSheet() {
        bulkLogo.value = null
        bulkMessage.value = null
    }

    private fun loadLogo(
        load: () -> ByteArray,
        target: MutableStateFlow<PendingLogo?>,
        message: MutableStateFlow<String?>
    ) {
        viewModelScope.launch {
            message.value = "Afbeelding verwerken…"
            try {
                val pending = withContext(Dispatchers.IO) {
                    val jpeg = LogoTools.toJpeg(load())
                    val preview = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                        ?: throw IllegalArgumentException("Dit bestand is geen bruikbare afbeelding.")
                    PendingLogo(jpeg, preview)
                }
                target.value = pending
                message.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                message.value = "De afbeelding is te groot voor dit toestel."
            } catch (e: Exception) {
                message.value = failureText(e)
            }
        }
    }

    fun loadBulkLogoFromUri(uri: Uri) =
        loadLogo({ LogoTools.readUri(appContext, uri) }, bulkLogo, bulkMessage)

    fun loadBulkLogoFromUrl(url: String) =
        loadLogo({ LogoTools.download(url) }, bulkLogo, bulkMessage)

    fun applyBulkLogo(onDone: () -> Unit) {
        val logo = bulkLogo.value ?: return
        if (bulkBusy.value) return
        val ids = selected.value
        viewModelScope.launch {
            bulkBusy.value = true
            bulkMessage.value = null
            try {
                val chosen = repository.allStations.first().filter { it.id in ids }
                withContext(Dispatchers.IO) {
                    for (station in chosen) {
                        val url = LocalLogos.save(appContext, station.id, logo.jpeg)
                        repository.updateStationLogo(station.id, url)
                    }
                }
                notice.value = "Logo gekoppeld aan ${chosen.size} ${if (chosen.size == 1) "zender" else "zenders"}."
                clearSelection()
                bulkLogo.value = null
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                bulkMessage.value = failureText(e)
            } finally {
                bulkBusy.value = false
            }
        }
    }

    // ---------------------------------------------------------------- zender toevoegen of bewerken

    val form = MutableStateFlow(StationForm())
    val formLogo = MutableStateFlow<PendingLogo?>(null)
    val formLogoMessage = MutableStateFlow<String?>(null)
    val formTest = MutableStateFlow<StreamTest>(StreamTest.Idle)
    val formBusy = MutableStateFlow(false)
    val formMessage = MutableStateFlow<String?>(null)

    private fun resetFormState() {
        formLogo.value = null
        formLogoMessage.value = null
        formTest.value = StreamTest.Idle
        formMessage.value = null
    }

    fun startNewStation() {
        form.value = StationForm()
        resetFormState()
    }

    fun startEditStation(station: RadioStation) {
        form.value = StationForm(
            editId = station.id,
            name = station.name,
            url = station.streamUrl,
            genres = splitGenres(station.groupTitle).joinToString(";"),
            existingLogoUrl = station.logoUrl
        )
        resetFormState()
    }

    fun updateForm(change: (StationForm) -> StationForm) {
        val old = form.value
        val new = change(old)
        form.value = new
        if (new.url != old.url) formTest.value = StreamTest.Idle
    }

    fun testFormStream() {
        val url = form.value.url.trim()
        if (url.isEmpty() || formTest.value is StreamTest.Running) return
        formTest.value = StreamTest.Running
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { StreamChecker.checkOnce(url) }
            // Is de URL intussen aangepast, dan hoort dit resultaat er niet meer bij.
            if (form.value.url.trim() == url) formTest.value = StreamTest.Done(result)
        }
    }

    fun loadFormLogoFromUri(uri: Uri) =
        loadLogo({ LogoTools.readUri(appContext, uri) }, formLogo, formLogoMessage)

    fun loadFormLogoFromUrl(url: String) =
        loadLogo({ LogoTools.download(url) }, formLogo, formLogoMessage)

    fun clearFormLogo() {
        formLogo.value = null
        formLogoMessage.value = null
    }

    fun saveForm(onSaved: () -> Unit) {
        if (formBusy.value) return
        val f = form.value
        val name = f.name.trim().replace(Regex("[\\u0000-\\u001f]+"), " ")
        val url = f.url.trim()
        val genres = normalizeGenres(f.genres)
        val startsOk = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
        val problem = when {
            name.isEmpty() -> "Naam is verplicht."
            name.length > 80 -> "Naam is te lang (maximaal 80 tekens)."
            !startsOk || url.contains(' ') -> "De stream-URL moet met http:// of https:// beginnen en mag geen spaties bevatten."
            genres.isEmpty() -> "Geef minstens één genre op."
            else -> null
        }
        if (problem != null) {
            formMessage.value = problem
            return
        }

        val logo = formLogo.value
        val editId = f.editId
        viewModelScope.launch {
            formBusy.value = true
            formMessage.value = null
            try {
                val slug = slugify(name)
                if (editId == null) {
                    val defaultId = repository.defaultPlaylistId()
                    val taken = repository.allStations.first()
                        .any { it.playlistId == defaultId && slugify(it.name) == slug }
                    if (taken) {
                        formMessage.value = "Er bestaat al een zender met deze naam."
                        return@launch
                    }
                }

                val stationId: Long = if (editId == null) {
                    repository.addLocalStation(name, url, genres)
                } else {
                    repository.updateStationDetails(editId, url, genres)
                    editId
                }
                if (logo != null) {
                    val logoUrl = withContext(Dispatchers.IO) { LocalLogos.save(appContext, stationId, logo.jpeg) }
                    repository.updateStationLogo(stationId, logoUrl)
                }

                notice.value = "'$name' is opgeslagen op dit toestel."
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                formMessage.value = failureText(e)
            } finally {
                formBusy.value = false
            }
        }
    }
}
