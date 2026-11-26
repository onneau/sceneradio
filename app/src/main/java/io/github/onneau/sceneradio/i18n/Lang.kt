package io.github.onneau.sceneradio.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** De taalkeuze van de gebruiker. SYSTEM volgt het toestel: Nederlands op een Nederlands toestel, anders Engels. */
enum class LanguageSetting(val key: String) {
    SYSTEM("system"),
    DUTCH("nl"),
    ENGLISH("en");

    companion object {
        fun fromKey(key: String?): LanguageSetting = values().firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * De taal van de app. De keuze staat in Compose-state, dus elk scherm dat [tr] aanroept past zich meteen aan,
 * zonder de app te herstarten. De keuze wordt bij het opstarten synchroon gelezen (een klein bestandje),
 * zodat het eerste beeld al in de goede taal staat.
 */
object Lang {
    private const val PREFS = "language"
    private const val KEY_SETTING = "setting"
    private const val KEY_HINT_SEEN = "hint_seen"

    private var setting by mutableStateOf(LanguageSetting.SYSTEM)
    private var hintSeenState by mutableStateOf(true)

    /** De keuze zoals de gebruiker hem gaf (ook "Volg het systeem"). */
    val current: LanguageSetting get() = setting

    /** Of de kleine "nieuw"-badge bij de taalrij al is weggehaald. */
    val hintSeen: Boolean get() = hintSeenState

    /** Waar of de app nu Engels toont. Leest Compose-state, dus gebruik het in een scherm of via [tr]. */
    val isEnglish: Boolean
        get() = when (setting) {
            LanguageSetting.ENGLISH -> true
            LanguageSetting.DUTCH -> false
            LanguageSetting.SYSTEM -> Locale.getDefault().language != "nl"
        }

    /** De taal voor datums en getallen, passend bij de gekozen taal. */
    val locale: Locale
        get() = if (isEnglish) Locale.ENGLISH else Locale.forLanguageTag("nl-NL")

    /** Eenmalig bij het opstarten van de app, voordat er een scherm of dienst tekst toont. */
    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        setting = LanguageSetting.fromKey(prefs.getString(KEY_SETTING, null))
        hintSeenState = prefs.getBoolean(KEY_HINT_SEEN, false)
    }

    fun set(context: Context, value: LanguageSetting) {
        setting = value
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SETTING, value.key).apply()
    }

    /** De gebruiker heeft de taalrij gezien; de "nieuw"-badge verdwijnt. */
    fun markHintSeen(context: Context) {
        if (hintSeenState) return
        hintSeenState = true
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HINT_SEEN, true).apply()
    }
}

/** Kiest de Nederlandse of de Engelse tekst, naar de taal van de app. */
fun tr(nl: String, en: String): String = if (Lang.isEnglish) en else nl
