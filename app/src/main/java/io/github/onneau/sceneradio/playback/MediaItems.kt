package io.github.onneau.sceneradio.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.onneau.sceneradio.data.model.RadioStation

private const val STATION_ID_PREFIX = "station_"

// De zendernaam en de groep staan ook in de extra's van het item: met nummerinfo wordt het nummer de titel en
// de artiest de regel eronder (zie RadioPlaybackService.refreshSessionMetadata), en zonder nummerinfo komen
// de zendernaam en de groep daar weer vandaan.
internal const val EXTRA_STATION_NAME = "sceneradio.station_name"
internal const val EXTRA_STATION_GROUP = "sceneradio.station_group"

fun stationMediaId(stationId: Long): String = "$STATION_ID_PREFIX$stationId"

fun stationIdFromMediaId(mediaId: String): Long? =
    if (mediaId.startsWith(STATION_ID_PREFIX)) {
        mediaId.removePrefix(STATION_ID_PREFIX).toLongOrNull()
    } else {
        null
    }

/**
 * Eén plek waar een zender naar een Media3-item wordt omgezet (app en Android Auto).
 *
 * `title` is de zendernaam, `artist` de regel eronder (standaard de groep) en `artworkUri` het zenderlogo.
 * Stuurt de stream een nummer mee, dan zet de service het nummer als titel, de artiest als regel eronder en de
 * zendernaam als album (Android Auto, melding en vergrendelscherm). Het nummer komt uit de ruwe stream-metadata
 * (zie RadioPlaybackService); de metadata van het item blijft vast, want Media3 laat die altijd winnen van de
 * metadata uit de stream.
 */
fun RadioStation.toMediaItem(): MediaItem {
    val fallbackLine = groupTitle?.takeIf { it.isNotBlank() } ?: "Live Radio"
    val metadata = MediaMetadata.Builder()
        .setTitle(name)
        .setDisplayTitle(name)
        .setArtist(fallbackLine)
        .setSubtitle(fallbackLine)
        .setArtworkUri(logoUrl?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
        .setExtras(Bundle().apply {
            putString(EXTRA_STATION_NAME, name)
            putString(EXTRA_STATION_GROUP, fallbackLine)
        })
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
        .build()

    return MediaItem.Builder()
        .setMediaId(stationMediaId(id))
        .setUri(streamUrl)
        .setMediaMetadata(metadata)
        .build()
}
