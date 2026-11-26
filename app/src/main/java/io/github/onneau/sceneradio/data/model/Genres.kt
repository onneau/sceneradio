package io.github.onneau.sceneradio.data.model

import io.github.onneau.sceneradio.i18n.tr

/**
 * Een zender kan meerdere genres hebben. In de M3U staan ze gescheiden door ";" in `group-title`
 * (bijvoorbeeld "Pop;Dance"). Oude lijsten met één genre blijven gewoon werken.
 */
fun splitGenres(value: String?): List<String> =
    value.orEmpty()
        .split(';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }

fun RadioStation.genres(): List<String> = splitGenres(groupTitle)

/** Het genre dat een zender zonder eigen genre krijgt. Dit staat zo in de database; alleen de weergave is vertaald. */
const val DEFAULT_GENRE = "Algemeen"

/** De sleutel voor "alle genres" in het genrefilter. Dit is geen tekst om te tonen, zie [genreLabel]. */
const val ALL_GENRES = "Alles"

/** Hoe een genre getoond wordt: de twee vaste namen in de taal van de app, andere genres zoals ze zijn. */
fun genreLabel(genre: String): String = when (genre) {
    DEFAULT_GENRE -> tr("Algemeen", "General")
    ALL_GENRES -> tr("Alles", "All")
    else -> genre
}

/** De genres van een zender als één regel om te tonen. */
fun RadioStation.genreText(): String =
    genres().joinToString(" · ") { genreLabel(it) }.ifBlank { genreLabel(DEFAULT_GENRE) }
