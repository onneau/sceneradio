package io.github.onneau.sceneradio.data.export

import io.github.onneau.sceneradio.data.model.RadioStation
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Maakt van een lijst zenders een .m3u-bestand dat andere spelers openen en dat de app zelf ook weer kan importeren
 * (zelfde opmaak als de zenderlijst: #EXTINF met tvg-logo en group-title, de naam na de komma).
 */
object M3uWriter {

    fun write(stations: List<RadioStation>): String {
        val out = StringBuilder("#EXTM3U\n")
        for (station in stations) {
            val url = station.streamUrl.trim()
            if (url.isEmpty()) continue
            out.append("#EXTINF:-1")
            logoFor(station)?.let { out.append(" tvg-logo=\"").append(attribute(it)).append('"') }
            station.groupTitle?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { out.append(" group-title=\"").append(attribute(it)).append('"') }
            out.append(',').append(oneLine(station.name)).append('\n')
            out.append(url).append('\n')
        }
        return out.toString()
    }

    // Alleen logo's die een andere speler ook kan laden: een gewone internetlink. Logo's die alleen op dit toestel
    // staan (file://) blijven weg.
    private fun logoFor(station: RadioStation): String? {
        val logo = station.logoUrl?.trim().orEmpty()
        return if (logo.toHttpUrlOrNull() == null) null else logo
    }

    // Een aanhalingsteken in een waarde zou de waarde afsluiten; de parser van de app leest &quot; weer terug.
    private fun attribute(value: String): String = oneLine(value).replace("\"", "&quot;")

    private fun oneLine(value: String): String = value.replace('\r', ' ').replace('\n', ' ').trim()

    /** Bestandsnaam zonder rare tekens: "sceneradio-nederlandse-radio.m3u". */
    fun fileName(listName: String): String {
        val slug = listName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "zenders" }
        return "sceneradio-$slug.m3u"
    }
}
