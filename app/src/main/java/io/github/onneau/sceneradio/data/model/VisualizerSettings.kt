package io.github.onneau.sceneradio.data.model

/**
 * Instellingen van de Amiga-visualizer (Instellingen > Weergave > Visualizer).
 * De standaardwaarden zijn het uiterlijk van versie 1.18 (met in 1.19 de vonkelende sterren erbij).
 */

/** Hoe de koperbalken van kleur veranderen: nooit, een stukje heen en weer (subtiel) of door de hele regenboog. */
enum class CopperMode(val key: String) {
    OFF("off"),
    SUBTLE("subtle"),
    ALL("all");

    companion object {
        fun fromKey(key: String?): CopperMode? = values().firstOrNull { it.key == key }
    }
}

/**
 * Grootte van de hoes in de visualizer (in beeldpunten van de 256 regels hoge Amiga-afbeelding): klein is zoals
 * voorheen (midden in de koperbalken), middel is 1,4 × zo groot (ook in de koperbalken), groot is 1,9 × zo groot en
 * staat links onder het spiegelbeeld van de scroller (en wordt kleiner als de scroller of de zendernaam groter is).
 */
enum class CoverSize(val key: String, val pixels: Int) {
    SMALL("small", 44),
    MEDIUM("medium", 62),
    LARGE("large", 84);

    companion object {
        fun fromKey(key: String?): CoverSize? = values().firstOrNull { it.key == key }
    }
}

/**
 * Waar de hoes staat, in de visualizer en in de speler: standaard (zoals voor versie 1.24: in de visualizer Klein en
 * Middel in het midden en Groot links; in de speler staand in het midden en liggend links), of vast links, midden of rechts.
 */
enum class CoverPosition(val key: String) {
    DEFAULT("default"),
    LEFT("left"),
    CENTER("center"),
    RIGHT("right");

    companion object {
        fun fromKey(key: String?): CoverPosition? = values().firstOrNull { it.key == key }
    }
}

data class VisualizerSettings(
    // Snelheid (radialen per seconde; sterren: factor op de eigen snelheid)
    val waveSpeed: Float = 2.2f,        // golf van de scroller
    val copperSpeed: Float = 2.0f,      // koperbalken
    val hazeSpeed: Float = 2.0f,        // hellen en zwieren van de zendernaam (de oude naam "haze" is gebleven voor de bewaarde instellingen)
    val starSpeed: Float = 1.0f,        // sterren
    // Grootte
    val titleScale: Float = 1.2f,       // zendernaam (wordt kleiner als hij niet past)
    val scrollerRatio: Float = 0.75f,   // scroller = dit deel van de zendernaam
    val sloganPx: Int = 11,             // hoogte van de slogan in beeldpunten
    val hazeAmplitude: Float = 3.5f,    // uitslag van de zendernaam in beeldpunten: hij helt ongeveer 2,6 × dit en zwiert ongeveer 2,3 × dit heen en weer
    // Kleuren: draaiing van de tint in graden (0 = standaard)
    val copperHue: Float = 0f,
    val equalizerHue: Float = 0f,
    val backgroundHue: Float = 0f,
    // Koperbalken: vaste kleur (uit), de kleur schuift rustig heen en weer rond de gekozen tint (subtiel)
    // of de kleur loopt langzaam door de hele regenboog (alle kleuren)
    val copperMode: CopperMode = CopperMode.OFF,
    val copperDriftRange: Float = 12f,  // subtiel: uitslag in graden (±)
    val copperDriftSeconds: Int = 60,   // subtiel: duur van een heel rondje
    val copperCycleSeconds: Int = 60,   // alle kleuren: duur van een heel rondje door de regenboog
    // Sterren: ze flikkeren zacht en af en toe fonkelt er een
    val sparklingStars: Boolean = true,
    // Hoes van het huidige nummer in de koperbalken (zoekt via MusicBrainz en Cover Art Archive; standaard uit)
    val showCover: Boolean = false,
    val coverSize: CoverSize = CoverSize.SMALL,
    // Waar de hoes staat (visualizer en speler)
    val coverPosition: CoverPosition = CoverPosition.DEFAULT,
    // Is er geen hoes (ook niet met artiest en titel omgewisseld), dan een foto van de artiest (Wikimedia Commons); standaard uit
    val artistPhoto: Boolean = false,
    // Hoes van het nummer groot in de speler (met het zenderlogo als badge; tik voor volledig scherm); standaard uit
    val coverInPlayer: Boolean = false,
    // Equalizer: lager = rustiger, alleen de pieken komen hoog (1.0 = standaard)
    val equalizerSensitivity: Float = 1.0f,
    // Slogan: kleurcyclus (standaard) of één vaste kleur (tint 0..360 graden)
    val sloganColorFixed: Boolean = false,
    val sloganHue: Float = 330f,        // roze
    // De slogans die na elkaar langskomen: standaard de 15 teksten hieronder (waaronder de tijd), aan te passen, te verwijderen en aan te vullen
    val slogans: List<String> = DEFAULT_SLOGANS,
    // Dimmen: na dimDelaySec seconden met de visualizer in beeld (zonder aanraking) zakt de helderheid naar dimLevel
    val dimEnabled: Boolean = true,
    val dimDelaySec: Int = 30,
    val dimLevel: Float = 0.8f
) {
    companion object {
        val SPEED_RANGE = 0.5f..4.0f
        val STAR_SPEED_RANGE = 0f..3f
        val TITLE_SCALE_RANGE = 0.8f..1.6f
        val SCROLLER_RATIO_RANGE = 0.4f..1.0f
        val SLOGAN_PX_RANGE = 7..16
        val HAZE_AMPLITUDE_RANGE = 0f..5f
        val HUE_RANGE = -180f..180f
        val SLOGAN_HUE_RANGE = 0f..360f
        val DIM_DELAY_RANGE = 10..120
        val DIM_LEVEL_RANGE = 0.3f..1.0f
        val COPPER_DRIFT_RANGE_RANGE = 5f..60f
        val COPPER_DRIFT_SECONDS_RANGE = 20..180
        val COPPER_CYCLE_SECONDS_RANGE = 20..300
        val EQ_SENSITIVITY_RANGE = 0.5f..1.5f
        const val MAX_SLOGANS = 30
        const val MAX_SLOGAN_LENGTH = 32

        /** In een slogan wordt dit de actuele lokale tijd van het apparaat (versie 1.25). */
        const val TIME_TOKEN = "{tijd}"
        val TIME_REGEX = Regex("\\{tijd\\}", RegexOption.IGNORE_CASE)
        const val TIME_SLOGAN = "Local time $TIME_TOKEN"

        /** Versie van de bewaarde sloganlijst: 2 = de tijd-slogan is toegevoegd (versie 1.25). */
        const val SLOGANS_VERSION = 2

        /**
         * Een lijst die vóór versie 1.25 is bewaard krijgt één keer de tijd-slogan bovenaan, tenzij er al een slogan
         * met {tijd} in staat of de lijst bewust leeg is. Verwijder je hem daarna, dan komt hij niet terug
         * (alleen "Standaardslogans herstellen" zet hem terug).
         */
        fun upgradeSlogans(stored: List<String>, version: Int): List<String> =
            if (version >= SLOGANS_VERSION || stored.none { it.isNotBlank() } || stored.any { TIME_REGEX.containsMatchIn(it) }) {
                stored
            } else {
                listOf(TIME_SLOGAN) + stored
            }

        val DEFAULT_SLOGANS = listOf(
            TIME_SLOGAN,
            "SceneRadio is proud to present",
            "Onno rulez",
            "SceneRadio brings you the beat",
            "Respect to the scene",
            "Surf the SceneRadio tonight",
            "Keep the scene alive",
            "SceneRadio rocks your Radio",
            "Amiga forever",
            "Turn it up loud on SceneRadio",
            "Hello world",
            "Greetings to all SceneRadio fans",
            "SceneRadio thnx Onno",
            "Music in the air by SceneRadio",
            "Stay tuned SceneRadio"
        )

        /** Slogans uit versie 1.17 (eigen teksten en de schakelaar "alleen eigen") omzetten naar de ene lijst van nu. */
        fun migrateSlogans(own: List<String>, onlyOwn: Boolean): List<String> {
            val cleaned = own.map { it.trim() }.filter { it.isNotEmpty() }
            return if (onlyOwn && cleaned.isNotEmpty()) cleaned else DEFAULT_SLOGANS + cleaned
        }
    }

    /** Houdt waarden binnen hun bereik, ook als er iets geks is opgeslagen. */
    fun coerced() = VisualizerSettings(
        waveSpeed = waveSpeed.coerceIn(SPEED_RANGE),
        copperSpeed = copperSpeed.coerceIn(SPEED_RANGE),
        hazeSpeed = hazeSpeed.coerceIn(SPEED_RANGE),
        starSpeed = starSpeed.coerceIn(STAR_SPEED_RANGE),
        titleScale = titleScale.coerceIn(TITLE_SCALE_RANGE),
        scrollerRatio = scrollerRatio.coerceIn(SCROLLER_RATIO_RANGE),
        sloganPx = sloganPx.coerceIn(SLOGAN_PX_RANGE),
        hazeAmplitude = hazeAmplitude.coerceIn(HAZE_AMPLITUDE_RANGE),
        copperHue = copperHue.coerceIn(HUE_RANGE),
        equalizerHue = equalizerHue.coerceIn(HUE_RANGE),
        backgroundHue = backgroundHue.coerceIn(HUE_RANGE),
        copperMode = copperMode,
        copperDriftRange = copperDriftRange.coerceIn(COPPER_DRIFT_RANGE_RANGE),
        copperDriftSeconds = copperDriftSeconds.coerceIn(COPPER_DRIFT_SECONDS_RANGE),
        copperCycleSeconds = copperCycleSeconds.coerceIn(COPPER_CYCLE_SECONDS_RANGE),
        sparklingStars = sparklingStars,
        showCover = showCover,
        coverSize = coverSize,
        coverPosition = coverPosition,
        artistPhoto = artistPhoto,
        coverInPlayer = coverInPlayer,
        equalizerSensitivity = equalizerSensitivity.coerceIn(EQ_SENSITIVITY_RANGE),
        sloganColorFixed = sloganColorFixed,
        sloganHue = sloganHue.coerceIn(SLOGAN_HUE_RANGE),
        // Een lege lijst mag: dan toont de visualizer geen slogan.
        slogans = slogans.map { it.trim().take(MAX_SLOGAN_LENGTH).trim() }.filter { it.isNotEmpty() }
            .distinct().take(MAX_SLOGANS),
        dimEnabled = dimEnabled,
        dimDelaySec = dimDelaySec.coerceIn(DIM_DELAY_RANGE),
        dimLevel = dimLevel.coerceIn(DIM_LEVEL_RANGE)
    )
}
