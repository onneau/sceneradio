package io.github.onneau.sceneradio.data.manage

import java.text.Normalizer
import java.util.Locale

/**
 * Dezelfde regels als slugify() in generate_m3u.py van de lijstgenerator: NFKD, alleen ASCII, kleine letters,
 * al het andere wordt een streepje. Het logo van een zender staat in de lijst-repository als logos/<slug>.jpg.
 */
fun slugify(name: String): String {
    val normalized = Normalizer.normalize(name, Normalizer.Form.NFKD)
    val ascii = StringBuilder()
    for (c in normalized) {
        if (c.code < 128) ascii.append(c)
    }
    val slug = ascii.toString().lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
    return slug.ifEmpty { "station" }
}

/**
 * Genres staan gescheiden door ";" (zo komen ze in group-title van de M3U). Een komma telt ook als
 * scheiding en dubbelen vallen weg, net als in de lijstgenerator.
 */
fun normalizeGenres(text: String): String {
    val genres = mutableListOf<String>()
    for (part in text.split(';', ',')) {
        val genre = part.replace(Regex("[\\u0000-\\u001f]+"), " ").trim()
        if (genre.isNotEmpty() && genres.none { it.equals(genre, ignoreCase = true) }) {
            genres.add(genre)
        }
    }
    return genres.joinToString(";")
}
