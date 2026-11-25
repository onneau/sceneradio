package io.github.onneau.sceneradio.data.parser

import io.github.onneau.sceneradio.data.model.RadioStation

object M3uParser {

    // Attributen kunnen met " of ' omsloten zijn; een apostrof binnen "..." (bijv. 80's) mag.
    private fun attribute(line: String, key: String): String? {
        val doubleQuoted = Regex("$key=\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(line)
        if (doubleQuoted != null) {
            return decodeEntities(doubleQuoted.groupValues[1].trim()).takeIf { it.isNotEmpty() }
        }
        val singleQuoted = Regex("$key='([^']*)'", RegexOption.IGNORE_CASE).find(line)
        return singleQuoted?.groupValues?.get(1)?.trim()
            ?.let { decodeEntities(it) }
            ?.takeIf { it.isNotEmpty() }
    }

    // Sommige lijsten bevatten HTML-tekens zoals &amp; in namen en groepen.
    private fun decodeEntities(text: String): String =
        text.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")

    // De zendernaam staat na de eerste komma die NIET tussen aanhalingstekens staat.
    private fun titleAfterComma(line: String): String? {
        var inQuotes = false
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                return line.substring(i + 1).trim().takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    fun parse(content: String, playlistId: Long = 1): List<RadioStation> {
        val stations = mutableListOf<RadioStation>()
        val lines = content.removePrefix("\uFEFF").lines()

        var currentName: String? = null
        var currentLogo: String? = null
        var currentGroup: String? = null
        var order = 0

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                currentLogo = attribute(line, "tvg-logo")
                currentGroup = attribute(line, "group-title")
                val tvgName = attribute(line, "tvg-name")

                currentName = titleAfterComma(line)?.let { decodeEntities(it) }
                    ?: tvgName
                    ?: "Onbekend Station"
            } else if (!line.startsWith("#")) {
                // Dit is een stream-URL
                if (line.startsWith("http://", ignoreCase = true) || line.startsWith("https://", ignoreCase = true) || line.startsWith("mms://", ignoreCase = true) || line.startsWith("rtsp://", ignoreCase = true)) {
                    val station = RadioStation(
                        id = 0,
                        playlistId = playlistId,
                        name = currentName ?: "Station ${stations.size + 1}",
                        streamUrl = line,
                        logoUrl = currentLogo,
                        groupTitle = currentGroup ?: "Algemeen",
                        isEnabled = true,
                        orderIndex = order++
                    )
                    stations.add(station)
                }
                // Reset voor de volgende zender
                currentName = null
                currentLogo = null
                currentGroup = null
            }
        }

        return stations
    }
}
