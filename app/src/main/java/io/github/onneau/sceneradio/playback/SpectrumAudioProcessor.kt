package io.github.onneau.sceneradio.playback

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** De laatste meting van het geluidsspectrum, voor de visualizer. Waarden lopen van 0 tot 1. */
object AudioLevels {
    const val BANDS = 24

    val bands = FloatArray(BANDS)

    @Volatile
    var lastUpdateMs: Long = 0L
}

/**
 * Luistert mee met het geluid dat ExoPlayer afspeelt (zonder iets te veranderen) en berekent daaruit een
 * spectrum. Dit werkt zonder microfoonrechten, want het gebruikt de audio binnen de speler zelf.
 */
class SpectrumAudioProcessor : BaseAudioProcessor() {

    private companion object {
        const val N = 1024           // FFT-lengte: scherp genoeg om de bas in aparte banden te meten
        const val HOP = 512          // hoeveel nieuwe geluidsmonsters tussen twee metingen
        const val MIN_FREQ = 60.0
        const val MAX_FREQ = 14000.0

        // Elke band wordt afgezet tegen zijn eigen recente piek, zodat de bas niet altijd vol staat.
        const val SPAN_DB = 24f      // zo veel dB onder de piek is een band leeg
        const val HEADROOM_DB = 3f   // pas 3 dB boven de vorige piek is een band echt vol
        const val PEAK_DECAY_DB_PER_SEC = 4f // de piek zakt langzaam, zodat zachtere stukken ook weer bewegen
        const val FLOOR_DB = -50f    // onder dit niveau wordt niets versterkt (stilte blijft stil)
    }

    private var channels = 2
    private var sampleRate = 44100

    private val ring = FloatArray(N)
    private var position = 0
    private var sinceLast = 0

    private val peak = FloatArray(AudioLevels.BANDS) { FLOOR_DB }
    private var peakDecay = PEAK_DECAY_DB_PER_SEC * HOP / 44100f

    private val re = FloatArray(N)
    private val im = FloatArray(N)
    private val hann = FloatArray(N) { (0.5 - 0.5 * cos(2.0 * PI * it / (N - 1))).toFloat() }

    private var bandStart = IntArray(AudioLevels.BANDS)
    private var bandEnd = IntArray(AudioLevels.BANDS)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Alleen gewone 16-bit audio; bij iets anders blijft deze stap uit en gaat het geluid ongemoeid door.
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        sampleRate = inputAudioFormat.sampleRate
        peakDecay = PEAK_DECAY_DB_PER_SEC * HOP / sampleRate
        computeBands()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        analyse(inputBuffer)
        val output = replaceOutputBuffer(size)
        output.put(inputBuffer)
        output.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        clear()
    }

    override fun onReset() {
        clear()
    }

    private fun clear() {
        ring.fill(0f)
        position = 0
        sinceLast = 0
        peak.fill(FLOOR_DB)
    }

    private fun computeBands() {
        val maxFreq = MAX_FREQ.coerceAtMost(sampleRate / 2.0 - 1)
        val binWidth = sampleRate.toDouble() / N
        // Logaritmisch verdeeld, maar elke band is minstens één FFT-lijn breed en de banden sluiten op elkaar aan.
        // Zo meten de laagste banden elk hun eigen stukje bas, in plaats van allemaal dezelfde twee lijnen.
        var previous = maxOf(1, (MIN_FREQ / binWidth).toInt())
        for (b in 0 until AudioLevels.BANDS) {
            val high = MIN_FREQ * (maxFreq / MIN_FREQ).pow((b + 1).toDouble() / AudioLevels.BANDS)
            val start = minOf(previous, N / 2 - 1)
            val end = (high / binWidth).roundToInt().coerceIn(start + 1, N / 2)
            bandStart[b] = start
            bandEnd[b] = end
            previous = end
        }
    }

    private fun analyse(buffer: ByteBuffer) {
        val samples = buffer.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
        val frames = samples.remaining() / channels
        for (frame in 0 until frames) {
            var sum = 0f
            for (channel in 0 until channels) sum += samples.get(frame * channels + channel)
            ring[position] = sum / channels / 32768f
            position = (position + 1) and (N - 1)
            if (++sinceLast >= HOP) {
                sinceLast = 0
                spectrum()
            }
        }
    }

    private fun spectrum() {
        for (i in 0 until N) {
            re[i] = ring[(position + i) and (N - 1)] * hann[i]
            im[i] = 0f
        }
        fft()

        for (b in 0 until AudioLevels.BANDS) {
            var power = 0f
            val count = bandEnd[b] - bandStart[b]
            for (k in bandStart[b] until bandEnd[b]) power += re[k] * re[k] + im[k] * im[k]
            val magnitude = sqrt(power / count) / (N / 4f)
            val db = 20f * log10(magnitude + 1e-7f)
            // Eerst vergelijken met de vorige piek, dan pas de piek bijwerken: alleen een hardere klap dan eerder
            // haalt de volle hoogte.
            val level = ((db - (peak[b] + HEADROOM_DB - SPAN_DB)) / SPAN_DB).coerceIn(0f, 1f)
            peak[b] = maxOf(db, peak[b] - peakDecay, FLOOR_DB)
            // Snel omhoog, langzaam omlaag: zo blijft de beweging rustig.
            AudioLevels.bands[b] = maxOf(level, AudioLevels.bands[b] - 0.04f)
        }
        AudioLevels.lastUpdateMs = SystemClock.uptimeMillis()
    }

    private fun fft() {
        var j = 0
        for (i in 1 until N) {
            var bit = N shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var length = 2
        while (length <= N) {
            val angle = -2.0 * PI / length
            val wlr = cos(angle).toFloat()
            val wli = sin(angle).toFloat()
            var i = 0
            while (i < N) {
                var wr = 1f
                var wi = 0f
                val half = length / 2
                for (k in 0 until half) {
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + half] * wr - im[i + k + half] * wi
                    val vi = re[i + k + half] * wi + im[i + k + half] * wr
                    re[i + k] = ur + vr
                    im[i + k] = ui + vi
                    re[i + k + half] = ur - vr
                    im[i + k + half] = ui - vi
                    val nr = wr * wlr - wi * wli
                    wi = wr * wli + wi * wlr
                    wr = nr
                }
                i += length
            }
            length = length shl 1
        }
    }
}

/** ExoPlayer-onderdelen met de spectrum-meting ertussen. */
class SpectrumRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean
    ): AudioSink =
        DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
            .setAudioProcessors(arrayOf(SpectrumAudioProcessor()))
            .build()
}
