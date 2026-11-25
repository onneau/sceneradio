package io.github.onneau.sceneradio

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import io.github.onneau.sceneradio.data.log.AppLog
import io.github.onneau.sceneradio.data.repository.RadioRepository
import io.github.onneau.sceneradio.playback.PlaybackConnection
import okhttp3.OkHttpClient

class SceneRadioApp : Application(), ImageLoaderFactory {

    lateinit var repository: RadioRepository
        private set

    lateinit var playback: PlaybackConnection
        private set

    override fun onCreate() {
        super.onCreate()
        // Het logboek als eerste, zodat ook een crash tijdens het opstarten erin komt.
        AppLog.init(this)
        repository = RadioRepository(this)
        playback = PlaybackConnection(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { OkHttpClient.Builder().build() }
            // Logo's veranderen zelden; GitHub stuurt korte cache-tijden mee, die negeren we.
            .respectCacheHeaders(false)
            .build()
}
