package io.github.onneau.sceneradio.data.model

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
