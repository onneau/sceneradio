package io.github.onneau.sceneradio.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/**
 * Verbindt de UI met [RadioPlaybackService] via een MediaController.
 * Het bouwen van de controller bindt (en start) de service; zonder deze verbinding
 * speelt er niets af. Alle functies horen op de main thread aangeroepen te worden.
 */
class PlaybackConnection(context: Context) {

    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingItem: MediaItem? = null

    fun connect() {
        if (controllerFuture != null) return

        val token = SessionToken(
            appContext,
            ComponentName(appContext, RadioPlaybackService::class.java)
        )
        val future = MediaController.Builder(appContext, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    this@PlaybackConnection.controller = null
                    this@PlaybackConnection.controllerFuture = null
                }
            })
            .buildAsync()
        controllerFuture = future

        future.addListener({
            try {
                val connected = future.get()
                controller = connected
                pendingItem?.let { startPlayback(connected, it) }
                pendingItem = null
            } catch (e: Exception) {
                // Verbinden mislukt: bij de volgende actie wordt opnieuw geprobeerd.
                controller = null
                controllerFuture = null
            }
        }, mainExecutor)
    }

    fun play(item: MediaItem) {
        val connected = controller
        if (connected != null) {
            startPlayback(connected, item)
        } else {
            pendingItem = item
            connect()
        }
    }

    fun togglePlayPause() {
        val connected = controller
        if (connected == null) {
            connect()
            return
        }
        if (connected.isPlaying) {
            connected.pause()
        } else {
            if (connected.playbackState == Player.STATE_IDLE) {
                connected.prepare()
            }
            connected.play()
        }
    }

    fun retry() {
        val connected = controller
        if (connected == null) {
            connect()
            return
        }
        if (connected.mediaItemCount > 0) {
            connected.prepare()
            connected.play()
        }
    }

    private fun startPlayback(connected: MediaController, item: MediaItem) {
        connected.setMediaItem(item)
        connected.prepare()
        connected.play()
    }
}
