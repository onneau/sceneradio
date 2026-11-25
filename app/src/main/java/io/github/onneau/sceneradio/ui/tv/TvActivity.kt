package io.github.onneau.sceneradio.ui.tv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import io.github.onneau.sceneradio.ui.RadioViewModel
import io.github.onneau.sceneradio.ui.theme.SceneRadioTheme

class TvActivity : ComponentActivity() {

    private val viewModel: RadioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SceneRadioTheme(darkTheme = true) { // TV UI is geoptimaliseerd voor dark theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TvScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                viewModel.togglePlayPause()
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                viewModel.playNextStation()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                viewModel.playPreviousStation()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (!viewModel.playbackState.value.isPlaying) {
                    viewModel.togglePlayPause()
                }
                true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                if (viewModel.playbackState.value.isPlaying) {
                    viewModel.togglePlayPause()
                }
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
