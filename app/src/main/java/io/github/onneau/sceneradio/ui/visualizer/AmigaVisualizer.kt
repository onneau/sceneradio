package io.github.onneau.sceneradio.ui.visualizer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import io.github.onneau.sceneradio.data.model.CopperMode
import io.github.onneau.sceneradio.data.model.CoverPosition
import io.github.onneau.sceneradio.data.model.CoverSize
import io.github.onneau.sceneradio.data.model.VisualizerSettings
import io.github.onneau.sceneradio.playback.AudioLevels
import kotlinx.coroutines.delay
import java.text.Normalizer
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import io.github.onneau.sceneradio.i18n.tr

// ------------------------------------------------------------------------------------------------
// Vaste maten. Alles wordt getekend op 256 regels hoog, zoals een Amiga PAL-scherm, zodat de
// lettertypes (ontworpen voor dat scherm) precies pixel voor pixel passen.
// ------------------------------------------------------------------------------------------------
private const val LOGICAL_HEIGHT = 256
private const val COPPER_HEIGHT = 70          // koperzone onderin
private const val COPPER_BARS = 6            // zes balken draaien om elkaar heen
private const val COPPER_THICKNESS = 16       // regels per balk
private const val COPPER_ORBIT = 25f          // hoever het midden van een balk boven en onder het midden van de koperzone komt
private const val TITLE_TOP = 12
private const val SLOGAN_TOP = 56
private const val SCROLLER_CENTER = 106       // middenlijn waar de letters van de scroller omheen golven
private const val WAVE_AMPLITUDE = 14
private const val EQ_BLOCK_HEIGHT = 3         // lage blokjes: een fijnere LED-look
private const val EQ_OVERLAP = 1              // de onderste blokjes lopen 1 regel achter het koperschijnsel: geen spleet
private const val REFLECTION_ROWS = 30
private const val REFLECTION_FADE_POWER = 1.4f  // > 1: het spiegelbeeld blijft langer dicht voordat het wegdithert

// Lettergroottes (1 = het lettertype zoals het is getekend). Snelheden, groottes en kleurtinten die de
// gebruiker kan instellen staan in VisualizerSettings; hier alleen de vaste maten eromheen.
private const val SLOGAN_SCALE_PER_PX = 0.375f / 11f  // schaal van de slogan per beeldpunt hoogte (11 px = 0.375)
private const val SCROLL_MIN_SCALE = 0.3f
private const val WAVE_LENGTH_DIVISOR = 1.6f  // golflengte = breedte / 1.6
private const val EQ_STEPS = 16               // kleurstappen van onder naar boven in de equalizer
private const val EQ_ALPHA_BOTTOM = 0.45f     // de equalizer staat vaag op de achtergrond: onderin 45 %, bovenin 30 %
private const val EQ_ALPHA_TOP = 0.30f
private const val EQ_TOP_GAP = 16             // de equalizer begint zoveel regels onder de slogan, dus achter de scroller
private const val TITLE_SWING_SPEED = 0.5f    // de zendernaam doet er 1 / dit keer zo lang over als de oude fata morgana
private const val TITLE_LEAN = 2.571f         // beeldpunten waarmee boven en onder tegengesteld bewegen, per beeldpunt van hazeAmplitude
private const val TITLE_SWAY = 2.286f         // beeldpunten waarmee de hele naam heen en weer zwiert, per beeldpunt van hazeAmplitude
private const val SLOGAN_LEAN_RATIO = 0.55f   // de slogan helt en zwiert tegengesteld aan de zendernaam, met dit deel van zijn hellen ...
private const val SLOGAN_SWAY_RATIO = 1.25f   // ... en dit deel van zijn zwieren (dus iets sterker heen en weer, iets minder scheef)
// Slogan-ritme: de tekst van een slogan verandert alleen op het linkerpunt van de zwaai. Daar staat de slogan stil
// terwijl hij wordt gewist en de nieuwe wordt getypt; daarna wacht hij tot het volgende linkerpunt, zwaait minstens
// een hele slag heen en weer en blijft dan weer op het linkerpunt staan voor de volgende tekst.
private const val SLOGAN_TYPE_CPS = 16f      // letters per seconde bij het typen
private const val SLOGAN_HOLD = 0.35f        // seconden dat de volledige tekst minstens stilstaat voor de zwaai begint
private const val SLOGAN_GAP = 0.15f         // seconden leeg tussen wissen en het typen van de volgende
private const val SLOGAN_ERASE = 0.45f       // seconden die het wissen van de hele tekst duurt
private const val SLOGAN_MIN_SWING = 3.5f    // minstens zoveel seconden zwaait een slogan (en altijd een hele slag)
// Hoes en sterren
// De grootte van de hoes staat in CoverSize: klein 44 en middel 62 regels staan midden in de koperzone van 70 regels, groot (84)
// staat links onder het spiegelbeeld van de scroller.
private const val COVER_GAP = 4              // lege regels tussen het spiegelbeeld van de scroller en een grote hoes
private const val COVER_BOTTOM_MARGIN = 4    // en onderaan (ruimte voor de schaduw)
private const val COVER_LEFT_MARGIN = 14     // een hoes die links of rechts staat (en een grote hoes) staat zoveel beeldpunten van de rand
private const val COVER_FADE_SECONDS = 0.6f  // zo lang duurt het in- en uitfaden van de hoes
private const val EQ_FLOOR_BASE = 0.65f      // echte meting: onder dit niveau (bij gevoeligheid 100 %) blijft de balk leeg ...
private const val EQ_FLOOR_SPREAD = 0.5f     // ... en de gevoeligheid verschuift die drempel met zoveel per 100 %
private const val EQ_TOP = 0.95f             // ... boven dit niveau staat de balk vol
private const val EQ_GAMMA = 1.2f            // > 1: rustiger onderin, pieken springen er beter uit
private const val GLINT_CORE = 0xFFFFFFFF.toInt()
private const val GLINT_RAY = 0xFFB9D0FF.toInt()
private const val GLINT_DIAGONAL = 0xFFDBE7FF.toInt()
private val MAX_TITLE_SCALE = VisualizerSettings.TITLE_SCALE_RANGE.endInclusive
private val MAX_SLOGAN_PX = VisualizerSettings.SLOGAN_PX_RANGE.last

// Beperkt kleurenpalet voor achtergrond en balken.
private object Amiga {
    val BLACK = 0xFF000014.toInt()
    val BLUE = 0xFF0033CC.toInt()
    val LBLUE = 0xFF3377FF.toInt()
    val WHITE = 0xFFFFFFFF.toInt()

    // Equalizer: zonsondergang van onderin oranje via roze en paars naar donkerblauw bovenin
    // (steunkleuren op deze plekken tussen 0 = onderin en 1 = bovenin).
    val EQ_STOPS = floatArrayOf(0f, 0.35f, 0.65f, 1f)
    val EQ_COLORS = intArrayOf(0xFFFF8A1E.toInt(), 0xFFF2475A.toInt(), 0xFF9B3BA6.toInt(), 0xFF1E3AA0.toInt())

    // Koperbalken: van helder midden naar donkere rand (index = afstand tot het midden, 0..6).
    val GOLD = intArrayOf(
        0xFFFFF3B0.toInt(), 0xFFFFE070.toInt(), 0xFFFFC22E.toInt(), 0xFFE0A020.toInt(),
        0xFFB87818.toInt(), 0xFF8A5410.toInt(), 0xFF5A3208.toInt()
    )
    val COPPER = intArrayOf(
        0xFFFFD2A8.toInt(), 0xFFF0A070.toInt(), 0xFFD07838.toInt(), 0xFFB05A22.toInt(),
        0xFF8A4012.toInt(), 0xFF62290A.toInt(), 0xFF3C1606.toInt()
    )
}

/** Kleur op positie p (0..1) tussen de steunkleuren van een verloop. */
private fun gradientColor(stops: FloatArray, colors: IntArray, p: Float): Int {
    var k = 0
    while (k < stops.size - 2 && p > stops[k + 1]) k++
    val f = ((p - stops[k]) / (stops[k + 1] - stops[k])).coerceIn(0f, 1f)
    fun mix(shift: Int): Int {
        val a = (colors[k] shr shift) and 0xFF
        val b = (colors[k + 1] shr shift) and 0xFF
        return (a + (b - a) * f).roundToInt()
    }
    return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
}

/** Kleur met gedraaide tint (in graden). Grijs en wit blijven wat ze zijn. */
internal fun shiftHue(color: Int, degrees: Float): Int {
    if (degrees == 0f) return color
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color, hsv)
    hsv[0] = ((hsv[0] + degrees) % 360f + 360f) % 360f
    return android.graphics.Color.HSVToColor(color ushr 24, hsv)
}

// Alle kleuren van de visualizer, met de tinten uit de instellingen erin verwerkt.
private class Palette(settings: VisualizerSettings) {
    val background = shiftHue(Amiga.BLACK, settings.backgroundHue)
    val starDark = shiftHue(Amiga.BLUE, settings.backgroundHue)
    val starLight = shiftHue(Amiga.LBLUE, settings.backgroundHue)
    val starWhite = Amiga.WHITE

    // Equalizer, van onder (oranje) naar boven (donkerblauw), met doorzichtigheid zodat hij vaag op de achtergrond staat.
    val equalizer = IntArray(EQ_STEPS) { i ->
        val p = i / (EQ_STEPS - 1f)
        val color = shiftHue(gradientColor(Amiga.EQ_STOPS, Amiga.EQ_COLORS, p), settings.equalizerHue)
        val alpha = ((EQ_ALPHA_BOTTOM + (EQ_ALPHA_TOP - EQ_ALPHA_BOTTOM) * p) * 255f).roundToInt()
        (alpha shl 24) or (color and 0xFFFFFF)
    }

    val gold = Amiga.GOLD.map { shiftHue(it, settings.copperHue) }.toIntArray()
    val copper = Amiga.COPPER.map { shiftHue(it, settings.copperHue) }.toIntArray()

    // Donker schijnsel achter de balken, per regel van de koperzone.
    val glow = IntArray(COPPER_HEIGHT) { row ->
        val strength = 1f - abs(row.toFloat() / COPPER_HEIGHT - 0.55f) * 1.2f
        val red = (0x2A * strength).toInt() + 6
        val green = (0x12 * strength).toInt() + 3
        val blue = (0x05 * strength).toInt() + 4
        shiftHue((0xFF shl 24) or (red shl 16) or (green shl 8) or blue, settings.copperHue)
    }
}

// Een koperbalk is een rond buisje: per regel de kleurindex uit de GOLD/COPPER-reeks (0 = lichtst, 6 = donkerst),
// licht in het midden en donker aan de randen.
private val BAR_PROFILE = IntArray(COPPER_THICKNESS) { i ->
    ((abs((i + 0.5f) / COPPER_THICKNESS - 0.5f) * 2f).pow(1.25f) * 6f).roundToInt()
}

// 4x4 Bayer-matrix voor het "gedithered" vervagen van de reflectie.
private val BAYER = floatArrayOf(
    0f, 8f, 2f, 10f,
    12f, 4f, 14f, 6f,
    3f, 11f, 1f, 9f,
    15f, 7f, 13f, 5f
).map { (it + 0.5f) / 16f }.toFloatArray()

// ------------------------------------------------------------------------------------------------
// Lettertypes: Amiga-kleurenfonts als PNG-spritesheet (16 x 16 tekens, rij * 16 + kolom = teken).
// Ze bevatten alleen hoofdletters, cijfers en een paar leestekens.
// ------------------------------------------------------------------------------------------------
private val ACCENTS = Regex("\\p{M}+")

private val FALLBACKS = mapOf(
    '&' to '+', '/' to '-', '\\' to '-', '_' to '-', '|' to ':', '[' to '(', ']' to ')',
    '{' to '(', '}' to ')', '*' to '+', '’' to '\'', '‘' to '\'', '“' to '"',
    '”' to '"', '–' to '-', '—' to '-', '…' to '.'
)

/** Hoofdletters, zonder accenten, met vervangers voor tekens die de lettertypes niet hebben. */
private fun fontText(raw: String): String {
    val plain = Normalizer.normalize(raw, Normalizer.Form.NFD).replace(ACCENTS, "")
    val out = StringBuilder(plain.length)
    for (c in plain.uppercase(Locale.ROOT)) out.append(FALLBACKS[c] ?: c)
    return out.toString()
}

// De slogans die na elkaar langskomen: de teksten uit de instellingen, zoals ze zijn ingesteld (dus met {tijd}).
// Ze gaan pas door fontText als de tijd is ingevuld (zie AmigaRenderer.resolveSlogan).
private fun buildSlogans(settings: VisualizerSettings): List<String> =
    settings.slogans.filter { fontText(it).isNotBlank() }

private enum class SloganPhase { TYPE, SWING, ERASE, GAP }

// De vaste kleur van de slogan, uit de tint van de instellingen.
private fun fixedSloganColor(hue: Float): Int =
    android.graphics.Color.HSVToColor(floatArrayOf(((hue % 360f) + 360f) % 360f, 0.85f, 1f))

internal class PixelFont(context: Context, asset: String, val cellWidth: Int, val cellHeight: Int) {

    private val sheet: Bitmap
    private val left = IntArray(256)   // eerste gevulde kolom in de cel
    private val ink = IntArray(256)    // breedte van het teken zelf (0 = teken ontbreekt)

    /** Hoogte van de hoogste letter (rijen daaronder in de cel zijn leeg). */
    var glyphHeight = 0
        private set

    private val spaceWidth = (cellWidth * 0.45f).roundToInt()
    private val src = Rect()
    private val dst = Rect()

    init {
        val options = BitmapFactory.Options().apply { inScaled = false }
        sheet = requireNotNull(
            context.assets.open(asset).use { BitmapFactory.decodeStream(it, null, options) }
        ) { "Lettertype $asset kon niet worden gelezen" }

        val w = sheet.width
        val pixels = IntArray(w * sheet.height)
        sheet.getPixels(pixels, 0, w, 0, 0, w, sheet.height)
        for (code in 0 until 256) {
            val ox = (code % 16) * cellWidth
            val oy = (code / 16) * cellHeight
            if (ox + cellWidth > w || oy + cellHeight > sheet.height) continue
            var minX = cellWidth
            var maxX = -1
            var maxY = -1
            for (y in 0 until cellHeight) {
                for (x in 0 until cellWidth) {
                    if ((pixels[(oy + y) * w + ox + x] ushr 24) != 0) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y > maxY) maxY = y
                    }
                }
            }
            if (maxX >= 0) {
                left[code] = minX
                ink[code] = maxX - minX + 1
                if (maxY + 1 > glyphHeight) glyphHeight = maxY + 1
            }
        }
    }

    private fun has(ch: Char): Boolean = ch.code < 256 && ink[ch.code] > 0

    /** Plaats die dit teken inneemt, inclusief de ruimte erna. Onbekende tekens tellen als spatie. */
    fun advance(ch: Char, scale: Float = 1f): Int =
        if (ch == ' ' || !has(ch)) (spaceWidth * scale).roundToInt()
        else (ink[ch.code] * scale).roundToInt() + (2 * scale).roundToInt()

    fun width(text: String, scale: Float = 1f): Int {
        var total = 0
        for (ch in text) total += advance(ch, scale)
        return total
    }

    fun draw(canvas: Canvas, ch: Char, x: Float, y: Float, scale: Float, paint: Paint?) {
        if (ch == ' ' || !has(ch)) return
        val code = ch.code
        val sx = (code % 16) * cellWidth + left[code]
        val sy = (code / 16) * cellHeight
        src.set(sx, sy, sx + ink[code], sy + glyphHeight)
        val dx = x.roundToInt()
        val dy = y.roundToInt()
        dst.set(dx, dy, dx + max(1, (ink[code] * scale).roundToInt()), dy + max(1, (glyphHeight * scale).roundToInt()))
        canvas.drawBitmap(sheet, src, dst, paint)
    }

    fun drawText(canvas: Canvas, text: String, x: Float, y: Float, scale: Float, paint: Paint?) {
        var cx = x
        for (ch in text) {
            draw(canvas, ch, cx, y, scale, paint)
            cx += advance(ch, scale)
        }
    }
}

internal class AmigaFonts(context: Context) {
    val metallix = PixelFont(context, "fonts/metallix.png", 36, 36) // scroller: artiest en titel
    val forest = PixelFont(context, "fonts/forest.png", 25, 32)     // overige tekst
    val scopex = PixelFont(context, "fonts/scopex.png", 32, 35)     // zendernaam
}

// ------------------------------------------------------------------------------------------------

internal class AmigaRenderer(
    val width: Int,
    val height: Int,
    private val fonts: AmigaFonts,
    initial: VisualizerSettings,
    // Is de tijd van het apparaat 24-uurs? Bepaalt hoe {tijd} in een slogan wordt getoond (13:24 of 1:24 PM).
    private val is24Hour: () -> Boolean = { true }
) {

    private var cfg = initial
    private var palette = Palette(initial)

    val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)

    private val fill = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        isDither = false
    }
    private val blit = Paint(fill)
    private val srcRect = Rect()
    private val dstRect = Rect()
    private val dstRectF = RectF()
    private val blitSmooth = Paint(blit).apply { isFilterBitmap = true }  // voor het vloeiend (op de halve beeldpunt) schuiven

    // ---------------------------------------------------------------- titel (zendernaam, Scopex)
    // De bitmap is groot genoeg voor de grootste instelbare zendernaam.
    private val titleBitmap = Bitmap.createBitmap(
        width, (fonts.scopex.glyphHeight * MAX_TITLE_SCALE).roundToInt() + 3, Bitmap.Config.ARGB_8888
    )
    private val titleHeight = titleBitmap.height
    private var titleGlyphHeight = titleHeight  // hoogte van de letters zelf (met schaduw), om de overhelling op af te stemmen
    private val titleCanvas = Canvas(titleBitmap)
    private val titleShadow = Paint(fill).apply { colorFilter = LightingColorFilter(0x000000, 0x000A2E) }
    private var titleSource = "\u0000"

    // ---------------------------------------------------------------- scroller (Metallix)
    // De grootte volgt de zendernaam (zie layoutScroller) en staat dus niet vast.
    private val scrollerFont = fonts.metallix
    private var scrollScale = initial.titleScale * initial.scrollerRatio
    private var scrollGlyphHeight = 0
    private var scrollTop = 0
    private var reflectionAxis = 0
    private val shadowPaint = Paint(fill).apply { colorFilter = LightingColorFilter(0x000000, 0x00200C) }
    private val reflectionPaint = Paint(fill).apply { colorFilter = LightingColorFilter(0x808080, 0x000000) }
    private val reflectionBitmap = Bitmap.createBitmap(width, REFLECTION_ROWS, Bitmap.Config.ARGB_8888)
    private val reflectionCanvas = Canvas(reflectionBitmap)
    private val reflectionPixels = IntArray(width * REFLECTION_ROWS)

    private var scrollSource = "\u0000"
    private var scrollChars = CharArray(0)
    private var scrollOffsets = IntArray(0)
    private var scrollTotal = 0

    // ---------------------------------------------------------------- slogans (Forest, met plasma-kleurcyclus)
    private var sloganSources = buildSlogans(initial)                                      // zoals ingesteld, met {tijd}
    private var slogans = sloganSources.map { resolveSlogan(it) }.toMutableList()        // in het lettertype, tijd ingevuld
    private var sloganFixed = fixedSloganColor(initial.sloganHue)
    private var sloganScale = initial.sloganPx * SLOGAN_SCALE_PER_PX
    private var sloganHeight = (fonts.forest.glyphHeight * sloganScale).roundToInt() + 2
    private var sloganTop = SLOGAN_TOP
    private val sloganMaxHeight = (fonts.forest.glyphHeight * MAX_SLOGAN_PX * SLOGAN_SCALE_PER_PX).roundToInt() + 2
    private val sloganBitmap = Bitmap.createBitmap(width, sloganMaxHeight, Bitmap.Config.ARGB_8888)
    private val sloganCanvas = Canvas(sloganBitmap)
    private val sloganPixels = IntArray(width * sloganMaxHeight)

    // Slogan-ritme (zie drawSlogan): de zwaai begint en eindigt in zwaaifase (radialen) altijd op een linkerpunt.
    private var sloganPhase = SloganPhase.TYPE
    private var sloganScheduled = false
    private var sloganIndex = 0
    private var sloganResumePsi = 0f    // fase (linkerpunt) waarop de zwaai begint, als de tekst er dan staat
    private var sloganLeavePsi = 0f     // fase (linkerpunt) waarop de zwaai stopt en het wissen begint
    private var sloganStartClock = 0f   // sloganClock bij het begin van het typen, wissen of de lege pauze
    private var sloganClock = 0f

    // ---------------------------------------------------------------- achtergrond
    private val starCount = 70
    private val starX = FloatArray(starCount)
    private val starY = FloatArray(starCount)
    private val starLayer = IntArray(starCount)

    private val barCount = 32
    private val barLevels = FloatArray(barCount)
    private var lastTime = 0f

    // Looptijden die met de snelheid uit de instellingen meelopen. Ze worden per beeld opgeteld, zodat
    // het schuiven aan een snelheid niets laat springen.
    private var lastRenderTime = 0f
    private var starTime = 0f
    private var wavePhase = 0f
    private var copperPhase = 0f
    private var hazePhase = 0f
    private var copperDriftPhase = 0f   // 0..1: een hele ronde van de koperkleur
    private var paletteCopperHue = initial.copperHue

    // Hoes in de koperbalken (met Amiga-look: vierkante pixels, beperkt palet, raster-dither)
    private var coverSource: Bitmap? = null
    private var coverBitmap: Bitmap? = null
    private var coverBitmapSize = 0      // op welke maat coverBitmap is gemaakt (een grote hoes kan krimpen bij een grote scroller)
    private var coverFade = 0f           // 0 = onzichtbaar, 1 = volledig
    private val coverPaint = Paint(fill)

    // De equalizer staat op de koperzone en loopt omhoog tot onder de slogan (zie layoutScroller).
    private val equalizerBottom = height - COPPER_HEIGHT + EQ_OVERLAP
    private var equalizerTop = 0
    private var maxBlocks = 1

    init {
        val random = java.util.Random(1990)
        val starSpace = height - COPPER_HEIGHT
        for (i in 0 until starCount) {
            starX[i] = random.nextFloat() * width
            starY[i] = random.nextFloat() * starSpace
            starLayer[i] = random.nextInt(3)
        }
        layoutScroller(scrollScale)
    }

    // Nieuwe instellingen: kleuren en slogan direct, de zendernaam (en daarmee de scroller en de indeling)
    // wordt bij het volgende beeld opnieuw opgebouwd.
    private fun applySettings(next: VisualizerSettings) {
        if (next == cfg) return
        val old = cfg
        cfg = next
        if (next.copperHue != old.copperHue || next.equalizerHue != old.equalizerHue ||
            next.backgroundHue != old.backgroundHue || next.copperMode != old.copperMode ||
            next.copperDriftRange != old.copperDriftRange
        ) {
            paletteCopperHue = effectiveCopperHue()
            palette = Palette(next.copy(copperHue = paletteCopperHue))
        }
        if (next.slogans != old.slogans) {
            sloganSources = buildSlogans(next)
            slogans = sloganSources.map { resolveSlogan(it) }.toMutableList()
            sloganScheduled = false          // opnieuw plannen met de nieuwe lijst
        }
        if (next.sloganHue != old.sloganHue) sloganFixed = fixedSloganColor(next.sloganHue)
        sloganScale = next.sloganPx * SLOGAN_SCALE_PER_PX
        sloganHeight = (fonts.forest.glyphHeight * sloganScale).roundToInt() + 2
        titleSource = "\u0000"
    }

    fun render(
        t: Float,
        station: String,
        text: String,
        levels: FloatArray?,
        playing: Boolean,
        settings: VisualizerSettings
    ) {
        applySettings(settings)
        val dt = (t - lastRenderTime).coerceIn(0f, 0.1f)
        lastRenderTime = t
        starTime += dt * cfg.starSpeed
        wavePhase += dt * cfg.waveSpeed
        copperPhase += dt * cfg.copperSpeed
        hazePhase += dt * cfg.hazeSpeed
        sloganClock += dt
        updateCopperDrift(dt)

        setTitle(station)
        setScrollText(text)
        canvas.drawColor(palette.background)
        drawStars()
        drawEqualizer(t, levels, playing)
        drawSlogan(t)
        drawTitle()
        drawScroller(t)
        drawCopper()
        drawCover(dt)
    }

    // Zendernaam één keer in een eigen bitmap tekenen; per beeldlijn schuift die daarna een stukje opzij (hellen en schuiven).
    private fun setTitle(name: String) {
        if (name == titleSource) return
        titleSource = name
        titleBitmap.eraseColor(0)

        val font = fonts.scopex
        // Aan de randen blijft ruimte over voor het hellen en schuiven van de naam.
        val reach = ceil(cfg.hazeAmplitude * (TITLE_LEAN + TITLE_SWAY)).toInt() + 2
        val limit = min(width * 0.94f, (width - 2 * reach).toFloat())
        var text = fontText(name)
        var scale = cfg.titleScale
        while (font.width(text, scale) > limit && scale > 0.45f) scale -= 0.05f
        while (font.width(text, scale) > limit && text.length > 1) text = text.dropLast(1)

        val x = (width - font.width(text, scale)) / 2f
        font.drawText(titleCanvas, text, x + 1f, 1f, scale, titleShadow)
        font.drawText(titleCanvas, text, x, 0f, scale, null)

        titleGlyphHeight = (font.glyphHeight * scale).roundToInt() + 1

        // De slogan staat onder de zendernaam; is die erg groot, dan schuift de slogan mee omlaag.
        sloganTop = max(SLOGAN_TOP, TITLE_TOP + (font.glyphHeight * scale).roundToInt())

        // De scroller volgt de zendernaam: een vast deel ervan (standaard driekwart).
        layoutScroller(max(SCROLL_MIN_SCALE, scale * cfg.scrollerRatio))
    }

    // Maten en posities die van de grootte van de scroller afhangen: letterbreedtes, spiegelas en
    // hoe hoog de equalizer mag komen (tot net onder de slogan: hij loopt dus achter de scroller langs).
    private fun layoutScroller(scale: Float) {
        scrollScale = scale
        scrollGlyphHeight = (scrollerFont.glyphHeight * scale).roundToInt()
        // Standaard rond SCROLLER_CENTER, maar nooit tegen de slogan aan (grote slogan of scroller).
        scrollTop = max(
            (SCROLLER_CENTER - scrollGlyphHeight / 2f).roundToInt(),
            sloganTop + sloganHeight + 3 + WAVE_AMPLITUDE
        )
        reflectionAxis = scrollTop + WAVE_AMPLITUDE + scrollGlyphHeight + 2
        equalizerTop = sloganTop + sloganHeight + EQ_TOP_GAP
        maxBlocks = max(1, (equalizerBottom - equalizerTop) / EQ_BLOCK_HEIGHT)

        scrollOffsets = IntArray(scrollChars.size)
        var x = 0
        for (i in scrollChars.indices) {
            scrollOffsets[i] = x
            x += scrollerFont.advance(scrollChars[i], scale)
        }
        scrollTotal = x
    }

    private fun setScrollText(text: String) {
        if (text == scrollSource) return
        scrollSource = text
        scrollChars = ("    " + fontText(text) + "    ").toCharArray()
        layoutScroller(scrollScale)
    }

    private fun drawStars() {
        val sparkle = cfg.sparklingStars
        val clock = lastRenderTime
        for (i in 0 until starCount) {
            val layer = starLayer[i]
            val speed = 8f + layer * 14f
            val x = floor((starX[i] + starTime * speed) % width) // van links naar rechts
            val y = floor(starY[i])
            val base = when (layer) {
                0 -> palette.starDark
                1 -> palette.starLight
                else -> palette.starWhite
            }
            fill.color = base
            if (sparkle && layer >= 1) {
                // Zacht flikkeren: de ster wordt wat helderder en dofer, elke ster in zijn eigen tempo.
                val wave = sin(clock * (1.1f + (i % 5) * 0.37f) + i * 1.7f)
                fill.alpha = ((0.55f + 0.45f * wave * wave) * 255f).roundToInt()
            }
            canvas.drawRect(x, y, x + 1f, y + 1f, fill)
            fill.alpha = 255
            if (sparkle && i % 4 == 0) drawGlint(i, x, y, clock)
        }
    }

    // Af en toe fonkelt een ster: een kruisje van lichtstralen dat opkomt en weer wegvalt.
    private fun drawGlint(index: Int, x: Float, y: Float, clock: Float) {
        val period = 2.4f + (index % 7) * 0.45f
        val duration = 0.6f
        val local = (clock + index * 0.77f) % period
        if (local >= duration) return
        val k = sin(PI.toFloat() * local / duration)      // 0 .. 1 .. 0
        val arm = 1 + (k * 5f).roundToInt()
        fill.color = GLINT_CORE
        canvas.drawRect(x, y, x + 1f, y + 1f, fill)
        for (a in 1..arm) {
            fill.color = if (a == 1) GLINT_CORE else GLINT_RAY
            fill.alpha = (max(0.15f, k * (1f - (a - 1f) / (arm + 0.5f))) * 255f).roundToInt()
            canvas.drawRect(x + a, y, x + a + 1f, y + 1f, fill)
            canvas.drawRect(x - a, y, x - a + 1f, y + 1f, fill)
            canvas.drawRect(x, y + a, x + 1f, y + a + 1f, fill)
            canvas.drawRect(x, y - a, x + 1f, y - a + 1f, fill)
        }
        if (k > 0.75f) {                                  // op het hoogtepunt ook de schuine stralen
            fill.color = GLINT_DIAGONAL
            fill.alpha = 153
            canvas.drawRect(x + 1f, y + 1f, x + 2f, y + 2f, fill)
            canvas.drawRect(x - 1f, y - 1f, x, y, fill)
            canvas.drawRect(x + 1f, y - 1f, x + 2f, y, fill)
            canvas.drawRect(x - 1f, y + 1f, x, y + 2f, fill)
        }
        fill.alpha = 255
    }

    // Koperkleur: vast (uit), heel langzaam heen en weer rond de ingestelde tint (subtiel) of rond door de hele regenboog.
    private fun effectiveCopperHue(): Float {
        val tau = (2.0 * PI).toFloat()
        return when (cfg.copperMode) {
            CopperMode.OFF -> cfg.copperHue
            CopperMode.SUBTLE -> cfg.copperHue + (cfg.copperDriftRange * sin(tau * copperDriftPhase)).roundToInt()
            CopperMode.ALL -> cfg.copperHue + (360f * copperDriftPhase).roundToInt()
        }
    }

    private fun updateCopperDrift(dt: Float) {
        val seconds = when (cfg.copperMode) {
            CopperMode.OFF -> return
            CopperMode.SUBTLE -> cfg.copperDriftSeconds
            CopperMode.ALL -> cfg.copperCycleSeconds
        }
        copperDriftPhase = (copperDriftPhase + dt / seconds) % 1f
        val hue = effectiveCopperHue()
        if (hue != paletteCopperHue) {       // het palet verandert alleen als de tint echt een graad opschuift
            paletteCopperHue = hue
            palette = Palette(cfg.copy(copperHue = hue))
        }
    }

    private fun drawEqualizer(t: Float, levels: FloatArray?, playing: Boolean) {
        val dt = (t - lastTime).coerceIn(0f, 0.1f)
        lastTime = t
        // Zonder echte meting (even geen geluid binnen): een rustige, ritmische nabootsing.
        val beat = max(0f, sin(t * 2f * PI.toFloat() * 2.1f)).pow(3)
        for (i in 0 until barCount) {
            val target = when {
                !playing -> 0.03f
                levels != null -> {
                    val position = i / (barCount - 1f) * (levels.size - 1)
                    val low = position.toInt().coerceIn(0, levels.size - 1)
                    val high = min(low + 1, levels.size - 1)
                    val fraction = position - low
                    val raw = levels[low] * (1 - fraction) + levels[high] * fraction
                    // Echte metingen zitten vlak onder de top: onder de drempel blijft de balk leeg, zodat alleen
                    // het echte ritme overblijft. De gevoeligheid uit de instellingen verschuift die drempel.
                    val floor = (EQ_FLOOR_BASE + (1f - cfg.equalizerSensitivity) * EQ_FLOOR_SPREAD).coerceIn(0f, 0.9f)
                    ((raw - floor) / (EQ_TOP - floor)).coerceIn(0f, 1f).pow(EQ_GAMMA)
                }
                else -> {
                    val wobble = 0.45f + 0.30f * sin(t * 2.1f + i * 0.7f) + 0.20f * sin(t * 3.7f + i * 1.9f)
                    (wobble * (0.65f + 0.35f * beat)).coerceIn(0.05f, 1f)
                }
            }
            barLevels[i] = if (target > barLevels[i]) target else max(target, barLevels[i] - dt * 1.6f)
        }

        val barWidth = width / barCount
        for (i in 0 until barCount) {
            val blocks = (barLevels[i] * maxBlocks).toInt()
            val left = (i * barWidth + (width - barCount * barWidth) / 2).toFloat()
            for (b in 0 until blocks) {
                val level = b.toFloat() / maxBlocks
                fill.color = palette.equalizer[min(EQ_STEPS - 1, (level * EQ_STEPS).toInt())]
                val bottom = (equalizerBottom - b * EQ_BLOCK_HEIGHT).toFloat()
                canvas.drawRect(left + 1f, bottom - EQ_BLOCK_HEIGHT + 1f, left + barWidth - 1f, bottom, fill)
            }
        }
    }

    // De slogan volgt de zwaai van de zendernaam (fase psi, rad). De tekst verandert alleen op het linkerpunt,
    // waar de slogan stilstaat (de zwaai heeft daar fase 0, 2π, 4π, ...). Per slogan:
    //  1. TYPE: de tekst wordt getypt en staat daarna stil, tot het eerstvolgende linkerpunt;
    //  2. SWING: de slogan zwaait tegengesteld aan de zendernaam, minstens een hele slag heen en weer;
    //  3. ERASE: op een linkerpunt veegt de tekst letter voor letter weg, terwijl hij stilstaat;
    //  4. GAP: even leeg, dan volgt de volgende slogan met TYPE.
    private fun typingSeconds(index: Int): Float = slogans[index % slogans.size].length / SLOGAN_TYPE_CPS

    // De tijd van nu, alleen uren en minuten: "13:24" (24-uurs) of "1:24 PM" (12-uurs), met gewone cijfers en letters
    // omdat het lettertype niets anders heeft. De formatter wordt steeds nieuw gemaakt, zodat een andere tijdzone
    // (reizen, zomertijd) meteen klopt; dat gebeurt maar één keer per slogan.
    private fun clockText(): String =
        java.text.SimpleDateFormat(if (is24Hour()) "HH:mm" else "h:mm a", Locale.US).format(java.util.Date())

    // De slogan in het lettertype. Staat er {tijd} in (ook als {TIJD}), dan wordt dat de tijd van nu.
    private fun resolveSlogan(source: String): String {
        if (!VisualizerSettings.TIME_REGEX.containsMatchIn(source)) return fontText(source)
        val now = clockText()
        return fontText(VisualizerSettings.TIME_REGEX.replace(source) { now })
    }

    // Begint met typen en plant de zwaai op het eerstvolgende linkerpunt waarop de tekst klaar kan zijn.
    private fun startTyping(psi: Float, rate: Float) {
        val tau = (2.0 * PI).toFloat()
        sloganIndex %= slogans.size
        // Een slogan met {tijd} krijgt nu de actuele tijd; die blijft staan tot deze slogan weer aan de beurt is.
        if (VisualizerSettings.TIME_REGEX.containsMatchIn(sloganSources[sloganIndex])) {
            slogans[sloganIndex] = resolveSlogan(sloganSources[sloganIndex])
        }
        sloganPhase = SloganPhase.TYPE
        sloganStartClock = sloganClock
        val lead = (typingSeconds(sloganIndex) + SLOGAN_HOLD) * rate
        sloganResumePsi = ceil((psi + lead) / tau) * tau
        sloganScheduled = true
    }

    private fun drawSlogan(t: Float) {
        if (slogans.isEmpty()) {
            sloganScheduled = false
            return
        }
        val tau = (2.0 * PI).toFloat()
        val psi = hazePhase * TITLE_SWING_SPEED                         // zwaaifase van de zendernaam
        val rate = max(0.05f, cfg.hazeSpeed * TITLE_SWING_SPEED)         // radialen per seconde
        if (!sloganScheduled) startTyping(psi, rate)
        sloganIndex %= slogans.size

        when (sloganPhase) {
            SloganPhase.TYPE -> if (psi >= sloganResumePsi) {
                val typed = (sloganClock - sloganStartClock) * SLOGAN_TYPE_CPS >= slogans[sloganIndex].length
                if (typed) {
                    sloganPhase = SloganPhase.SWING
                    sloganLeavePsi = sloganResumePsi + max(1f, ceil(SLOGAN_MIN_SWING * rate / tau)) * tau
                } else {
                    sloganResumePsi += tau        // de tekst was nog niet klaar (zwaai ineens sneller gezet): volgend linkerpunt
                }
            }
            SloganPhase.SWING -> if (psi >= sloganLeavePsi) {
                sloganPhase = SloganPhase.ERASE
                sloganStartClock = sloganClock
            }
            SloganPhase.ERASE -> if (sloganClock - sloganStartClock >= SLOGAN_ERASE) {
                sloganPhase = SloganPhase.GAP
                sloganStartClock = sloganClock
            }
            SloganPhase.GAP -> if (sloganClock - sloganStartClock >= SLOGAN_GAP) {
                sloganIndex = (sloganIndex + 1) % slogans.size
                startTyping(psi, rate)
            }
        }

        val full = slogans[sloganIndex]
        val shown = when (sloganPhase) {
            SloganPhase.TYPE -> min(full.length, ((sloganClock - sloganStartClock) * SLOGAN_TYPE_CPS).toInt())
            SloganPhase.SWING -> full.length
            SloganPhase.ERASE -> {
                val left = 1f - (sloganClock - sloganStartClock) / SLOGAN_ERASE
                if (left <= 0f) 0 else ceil(full.length * left).toInt()
            }
            SloganPhase.GAP -> 0
        }
        if (shown <= 0) return

        // Past de slogan niet naast de uitslag (langere eigen tekst, grote letters), dan wordt hij kleiner.
        val reach = ceil(cfg.hazeAmplitude * (TITLE_SWAY * SLOGAN_SWAY_RATIO + TITLE_LEAN * SLOGAN_LEAN_RATIO)).toInt() + 2
        val limit = min(width * 0.94f, (width - 2 * reach).toFloat())
        var scale = sloganScale
        while (fonts.forest.width(full, scale) > limit && scale > 0.15f) scale -= 0.02f
        // De plek volgt de volledige slogan, zodat de tekst tijdens het typen en wissen niet heen en weer springt.
        val x = (width - fonts.forest.width(full, scale)) / 2f

        sloganBitmap.eraseColor(0)
        fonts.forest.drawText(sloganCanvas, full.substring(0, shown), x, 0f, scale, null)
        sloganBitmap.getPixels(sloganPixels, 0, width, 0, 0, width, sloganHeight)
        if (cfg.sloganColorFixed) applyFixedColor() else applyPlasma(t)
        sloganBitmap.setPixels(sloganPixels, 0, width, 0, 0, width, sloganHeight)

        // Contra: precies tegengesteld aan de zendernaam (boven naar links als de naam boven naar rechts gaat, enz.),
        // met dezelfde fase, maar iets minder scheef en iets verder heen en weer. Tijdens het typen en wissen staat de
        // slogan stil op het linkerpunt (de naam staat dan het verst rechts); de zwaai sluit daar precies op aan.
        // De verschuiving is niet afgerond op hele beeldpunten: de rijen schuiven vloeiend op de halve beeldpunt.
        val swinging = sloganPhase == SloganPhase.SWING
        val amplitude = cfg.hazeAmplitude
        val lean = if (swinging) sin(psi) * amplitude * TITLE_LEAN * SLOGAN_LEAN_RATIO else 0f
        val sway = if (swinging) cos(psi) * amplitude * TITLE_SWAY * SLOGAN_SWAY_RATIO
        else amplitude * TITLE_SWAY * SLOGAN_SWAY_RATIO
        val half = sloganHeight / 2f
        for (y in 0 until sloganHeight) {
            val dx = -sway + (y - half) / half * lean
            srcRect.set(0, y, width, y + 1)
            dstRectF.set(dx, (sloganTop + y).toFloat(), dx + width, (sloganTop + y + 1).toFloat())
            canvas.drawBitmap(sloganBitmap, srcRect, dstRectF, blitSmooth)
        }
    }

    // Vaste kleur: de gekozen tint, met de lichte en donkere tinten van het lettertype erop, zodat het reliëf blijft.
    // De allerlichtste punten gaan een beetje naar wit.
    private fun applyFixedColor() {
        val baseR = ((sloganFixed shr 16) and 0xFF) / 255f
        val baseG = ((sloganFixed shr 8) and 0xFF) / 255f
        val baseB = (sloganFixed and 0xFF) / 255f
        for (y in 0 until sloganHeight) {
            for (x in 0 until width) {
                val index = y * width + x
                val argb = sloganPixels[index]
                if ((argb ushr 24) == 0) continue
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                val light = (0.30f * r + 0.59f * g + 0.11f * b) / 255f
                val strength = 0.35f + 0.90f * light
                val white = ((light - 0.75f) / 0.25f).coerceIn(0f, 1f) * 0.5f
                val pr = min(255, ((baseR * strength * (1f - white) + white) * 255f).toInt())
                val pg = min(255, ((baseG * strength * (1f - white) + white) * 255f).toInt())
                val pb = min(255, ((baseB * strength * (1f - white) + white) * 255f).toInt())
                sloganPixels[index] = (0xFF shl 24) or (pr shl 16) or (pg shl 8) or pb
            }
        }
    }

    // Plasma-kleurcyclus: een golvend veld van sinussen bepaalt per pixel de kleur, en die schuift door de tijd
    // heen door het hele spectrum. De lichte en donkere tinten van het lettertype blijven behouden, zodat de
    // letters hun reliëf houden. De kleur loopt in stappen (als een gedraaid Amiga-palet) in plaats van vloeiend.
    private fun applyPlasma(t: Float) {
        val tau = (2.0 * PI).toFloat()
        val centerX = width / 2f
        for (y in 0 until sloganHeight) {
            val sy = (y + sloganTop).toFloat()
            val rowTerm = sin(sy * 0.22f + t * 1.7f)
            val dy = sy - sloganTop
            for (x in 0 until width) {
                val index = y * width + x
                val argb = sloganPixels[index]
                if ((argb ushr 24) == 0) continue
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                val light = (0.30f * r + 0.59f * g + 0.11f * b) / 255f

                val sx = x.toFloat()
                val dx = sx - centerX
                val field = sin(sx * 0.05f + t * 1.3f) + rowTerm +
                    sin((sx + sy) * 0.035f - t * 1.1f) +
                    sin(sqrt(dx * dx + dy * dy) * 0.06f + t * 0.9f)
                val phase = floor((field / 8f + 0.5f + t * 0.12f) * 24f) / 24f
                val strength = 0.35f + 0.90f * light

                val pr = min(255, ((0.5f + 0.5f * cos(tau * phase)) * strength * 255f).toInt())
                val pg = min(255, ((0.5f + 0.5f * cos(tau * (phase + 0.33f))) * strength * 255f).toInt())
                val pb = min(255, ((0.5f + 0.5f * cos(tau * (phase + 0.67f))) * strength * 255f).toInt())
                sloganPixels[index] = (0xFF shl 24) or (pr shl 16) or (pg shl 8) or pb
            }
        }
    }

    // De zendernaam helt heen en weer: eerst gaat de bovenkant naar rechts en de onderkant naar links (cursief), dan weer
    // recht, dan de andere kant op. Daarnaast zwiert de hele naam heen en weer door het beeld, een kwart slag verschoven:
    // de naam is het verst opzij op het moment dat hij recht staat.
    private fun drawTitle() {
        val phase = hazePhase * TITLE_SWING_SPEED
        val lean = sin(phase) * cfg.hazeAmplitude * TITLE_LEAN
        val sway = cos(phase) * cfg.hazeAmplitude * TITLE_SWAY
        val half = titleGlyphHeight / 2f
        for (y in 0 until titleHeight) {
            val dx = (sway - (y - half) / half * lean).roundToInt()
            srcRect.set(0, y, width, y + 1)
            dstRect.set(dx, TITLE_TOP + y, dx + width, TITLE_TOP + y + 1)
            canvas.drawBitmap(titleBitmap, srcRect, dstRect, blit)
        }
    }

    private fun drawScroller(t: Float) {
        val count = scrollChars.size
        if (count == 0) return
        val speed = 0.22f * width
        val startX = width - ((t * speed) % (scrollTotal + width))
        // Langere, langzamere golf dan eerst: golflengte = breedte / 1.6, en een rustiger looptempo.
        val k = (2.0 * PI / (width / WAVE_LENGTH_DIVISOR)).toFloat()
        val phase = wavePhase
        val axis = reflectionAxis.toFloat()

        reflectionBitmap.eraseColor(0)

        for (i in 0 until count) {
            val ch = scrollChars[i]
            val x = (startX + scrollOffsets[i]).roundToInt().toFloat()
            if (x > width || x < -scrollerFont.cellWidth) continue
            // De golf hangt aan de schermpositie en loopt van links naar rechts, terwijl de tekst van
            // rechts naar links schuift. Elke letter heeft zo zijn eigen hoogte, zonder draaien of stuiteren.
            val wave = sin(k * (x + scrollerFont.advance(ch, scrollScale) / 2f) - phase)
            val y = (scrollTop + WAVE_AMPLITUDE * wave).roundToInt().toFloat()

            scrollerFont.draw(canvas, ch, x + 1f, y + 1f, scrollScale, shadowPaint)
            scrollerFont.draw(canvas, ch, x, y, scrollScale, null)

            // Spiegelbeeld: dezelfde horizontale positie en golf, verticaal omgedraaid onder de as.
            reflectionCanvas.save()
            reflectionCanvas.translate(x, axis - y)
            reflectionCanvas.scale(1f, -1f)
            scrollerFont.draw(reflectionCanvas, ch, 0f, 0f, scrollScale, reflectionPaint)
            reflectionCanvas.restore()
        }

        // Naar beneden toe vervagen met een geordend dither-patroon (geen vloeiende overgang).
        reflectionBitmap.getPixels(reflectionPixels, 0, width, 0, 0, width, REFLECTION_ROWS)
        for (row in 0 until REFLECTION_ROWS) {
            val fade = (row.toFloat() / REFLECTION_ROWS).pow(REFLECTION_FADE_POWER)
            val offset = row * width
            for (col in 0 until width) {
                val index = offset + col
                if (reflectionPixels[index] != 0 && fade > BAYER[(row and 3) * 4 + (col and 3)]) {
                    reflectionPixels[index] = 0
                }
            }
        }
        reflectionBitmap.setPixels(reflectionPixels, 0, width, 0, 0, width, REFLECTION_ROWS)
        canvas.drawBitmap(reflectionBitmap, 0f, axis, blit)
    }

    // ---------------------------------------------------------------- hoes (klein, middel of groot)
    // De hoes wordt verkleind tot de gekozen maat in vierkante beeldpunten en omgezet naar een beperkt palet (4 bits per kleur)
    // met een raster-dither, zodat hij past bij de rest van de Amiga-look. Hij staat vooraan, de balken lopen erachter.
    // Klein en middel staan midden in de koperbalken. Groot staat links, onder het spiegelbeeld van de scroller (zonder dat
    // er iets overheen valt); is daar minder ruimte (grote scroller of zendernaam), dan wordt hij vanzelf kleiner, en is
    // er te weinig ruimte voor minstens de kleine maat, dan staat hij gewoon klein midden in de koperbalken.
    fun setCover(source: Bitmap?) {
        if (source === coverSource) return
        coverSource = source
        coverBitmap = null
        coverBitmapSize = 0
        coverFade = 0f
    }

    private fun amigaCover(source: Bitmap, size: Int): Bitmap {
        // Eerst in halve stappen verkleinen (een nette gemiddelde), zodat een grote hoes van 500 punten niet gaat flikkeren.
        var scaled = source
        while (scaled.width / 2 >= size && scaled.height / 2 >= size) {
            val next = Bitmap.createScaledBitmap(scaled, scaled.width / 2, scaled.height / 2, true)
            if (scaled !== source) scaled.recycle()
            scaled = next
        }
        val small = Bitmap.createScaledBitmap(scaled, size, size, true)
        if (scaled !== source && scaled !== small) scaled.recycle()
        val pixels = IntArray(size * size)
        small.getPixels(pixels, 0, size, 0, 0, size, size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val index = y * size + x
                val argb = pixels[index]
                val shift = (BAYER[(y and 3) * 4 + (x and 3)] - 0.5f) * 16f
                val r = quantize(((argb shr 16) and 0xFF) + shift)
                val g = quantize(((argb shr 8) and 0xFF) + shift)
                val b = quantize((argb and 0xFF) + shift)
                pixels[index] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        if (small !== source) small.recycle()
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, size, 0, 0, size, size)
        return out
    }

    private fun quantize(value: Float): Int = (value / 17f).roundToInt().coerceIn(0, 15) * 17

    private fun drawCover(dt: Float) {
        val source = coverSource
        val shown = source != null && cfg.showCover
        coverFade = if (shown) min(1f, coverFade + dt / COVER_FADE_SECONDS) else max(0f, coverFade - dt / COVER_FADE_SECONDS)
        if (source == null || coverFade <= 0f) return

        // Maat en hoogte: klein en middel midden in de koperzone, groot onder het spiegelbeeld van de scroller.
        var size = cfg.coverSize.pixels
        var underReflection = false
        if (cfg.coverSize == CoverSize.LARGE) {
            val free = height - COVER_BOTTOM_MARGIN - (reflectionAxis + REFLECTION_ROWS + COVER_GAP)
            if (free >= CoverSize.SMALL.pixels) {
                size = min(size, free)
                underReflection = true
            } else {
                size = CoverSize.SMALL.pixels
            }
        }
        val top = if (underReflection) {
            reflectionAxis + REFLECTION_ROWS + COVER_GAP
        } else {
            height - COPPER_HEIGHT + (COPPER_HEIGHT - size) / 2
        }
        // Waar de hoes staat: standaard (klein en middel in het midden, groot links), of vast links, midden of rechts.
        val left = when (cfg.coverPosition) {
            CoverPosition.LEFT -> COVER_LEFT_MARGIN
            CoverPosition.CENTER -> (width - size) / 2
            CoverPosition.RIGHT -> width - size - COVER_LEFT_MARGIN
            CoverPosition.DEFAULT -> if (underReflection) COVER_LEFT_MARGIN else (width - size) / 2
        }
        if (coverBitmap == null || coverBitmapSize != size) {
            coverBitmap = amigaCover(source, size)
            coverBitmapSize = size
        }
        val bitmap = coverBitmap ?: return

        val alpha = (coverFade * 255f).roundToInt()
        // Schaduw rechts en onder, donkere rand om de hoes en een lichte rand links en boven (als een lijstje).
        fill.color = 0x8C000000.toInt(); fill.alpha = (coverFade * 140f).roundToInt()
        canvas.drawRect((left + 2).toFloat(), (top + 2).toFloat(), (left + size + 3).toFloat(), (top + size + 3).toFloat(), fill)
        fill.color = 0xFF2A1606.toInt(); fill.alpha = alpha
        canvas.drawRect((left - 1).toFloat(), (top - 1).toFloat(), (left + size + 1).toFloat(), (top + size + 1).toFloat(), fill)
        coverPaint.alpha = alpha
        canvas.drawBitmap(bitmap, left.toFloat(), top.toFloat(), coverPaint)
        fill.color = palette.gold[1]; fill.alpha = alpha
        canvas.drawRect((left - 1).toFloat(), (top - 1).toFloat(), (left + size + 1).toFloat(), top.toFloat(), fill)
        canvas.drawRect((left - 1).toFloat(), (top - 1).toFloat(), left.toFloat(), (top + size + 1).toFloat(), fill)
        fill.alpha = 255
    }

    // Koper en goud: een donker koperschijnsel met daarop zes balken die om één as draaien. De hoogte van een balk is
    // een sinus, de diepte een cosinus: de balken aan de achterkant zijn donkerder en worden door de voorste overdekt.
    private val copperY = FloatArray(COPPER_BARS)
    private val copperDepth = FloatArray(COPPER_BARS)
    private val copperOrder = IntArray(COPPER_BARS)

    private fun drawCopper() {
        val zoneStart = height - COPPER_HEIGHT

        for (y in zoneStart until height) {
            fill.color = palette.glow[(y - zoneStart).coerceIn(0, COPPER_HEIGHT - 1)]
            canvas.drawRect(0f, y.toFloat(), width.toFloat(), y + 1f, fill)
        }

        for (k in 0 until COPPER_BARS) {
            val angle = copperPhase + k * 2f * PI.toFloat() / COPPER_BARS
            copperY[k] = zoneStart + COPPER_HEIGHT / 2f + sin(angle) * COPPER_ORBIT
            copperDepth[k] = cos(angle)
            // Op diepte sorteren: achterste balk eerst, voorste als laatste.
            var pos = k
            while (pos > 0 && copperDepth[copperOrder[pos - 1]] > copperDepth[k]) {
                copperOrder[pos] = copperOrder[pos - 1]
                pos--
            }
            copperOrder[pos] = k
        }

        for (n in 0 until COPPER_BARS) {
            val k = copperOrder[n]
            val ramp = if (k % 2 == 0) palette.gold else palette.copper
            val dim = ((1f - (copperDepth[k] + 1f) / 2f) * 2.6f).roundToInt() // hoe verder weg, hoe donkerder
            for (i in 0 until COPPER_THICKNESS) {
                val y = (copperY[k] + i - COPPER_THICKNESS / 2f).roundToInt()
                if (y < zoneStart || y >= height) continue
                fill.color = ramp[min(6, BAR_PROFILE[i] + dim)]
                canvas.drawRect(0f, y.toFloat(), width.toFloat(), y + 1f, fill)
            }
        }
    }
}

private fun scrollText(artist: String?, title: String?): String = when {
    !artist.isNullOrBlank() && !title.isNullOrBlank() -> "$artist - $title"
    !title.isNullOrBlank() -> title
    !artist.isNullOrBlank() -> artist
    else -> tr("Live radio uitzending", "Live radio broadcast")
}

/**
 * Cracktro-visualizer in de stijl van een Amiga uit 1990: de zendernaam in Scopex, een sinus-scroller met
 * artiest en titel in Metallix en spiegelbeeld, overige tekst in Forest, koper- en goudbalken die om elkaar heen
 * draaien, sterren en een vage equalizer die op de muziek reageert. Alles wordt in lage resolutie getekend (256 regels) en daarna scherp
 * (zonder uitsmeren) naar het scherm opgeschaald.
 */
@Composable
fun AmigaVisualizer(
    artist: String?,
    title: String?,
    stationName: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    settings: VisualizerSettings = VisualizerSettings(),
    cover: Bitmap? = null
) {
    var frameNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            androidx.compose.runtime.withFrameNanos { frameNanos = it - start }
        }
    }

    val context = LocalContext.current
    // Zonder de lettertype-bestanden blijft het scherm gewoon zwart in plaats van dat de app crasht.
    val fonts = remember { runCatching { AmigaFonts(context) }.getOrNull() }

    val text = remember(artist, title) { scrollText(artist, title) }
    val heading = stationName.ifBlank { "SceneRadio" }
    var renderer by remember { mutableStateOf<AmigaRenderer?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // De renderer wordt pas opnieuw opgebouwd als de afmeting even stilstaat. Tijdens het verdwijnen van de
    // menu's en systeembalken verandert de ruimte een paar keer; de oude renderer tekent dan gewoon door.
    LaunchedEffect(canvasSize) {
        val loaded = fonts ?: return@LaunchedEffect
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return@LaunchedEffect
        if (renderer != null) delay(150)
        val logicalWidth = (LOGICAL_HEIGHT * canvasSize.width.toFloat() / canvasSize.height)
            .roundToInt().coerceIn(320, 640)
        if (renderer?.width != logicalWidth) {
            renderer = AmigaRenderer(logicalWidth, LOGICAL_HEIGHT, loaded, settings) {
                android.text.format.DateFormat.is24HourFormat(context.applicationContext)
            }
        }
    }
    // De hoes (als die er is) gaat naar de renderer; opnieuw bij een nieuwe renderer na een andere afmeting.
    LaunchedEffect(renderer, cover) { renderer?.setCover(cover) }
    val outputPaint = remember {
        Paint().apply {
            isFilterBitmap = false
            isAntiAlias = false
            isDither = false
        }
    }

    ComposeCanvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { canvasSize = it }
    ) {
        renderer?.let { active ->
            val seconds = frameNanos / 1_000_000_000f
            // Echte meting uit de speler zolang die vers is; anders een nagebootst ritme.
            val fresh = SystemClock.uptimeMillis() - AudioLevels.lastUpdateMs < 400
            active.render(seconds, heading, text, if (fresh && isPlaying) AudioLevels.bands else null, isPlaying, settings)
            drawIntoCanvas { composeCanvas ->
                composeCanvas.nativeCanvas.drawBitmap(
                    active.bitmap, null, RectF(0f, 0f, size.width, size.height), outputPaint
                )
            }
        }
    }
}
