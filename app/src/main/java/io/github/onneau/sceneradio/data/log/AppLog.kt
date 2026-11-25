package io.github.onneau.sceneradio.data.log

import android.app.Activity
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Surface
import androidx.annotation.RequiresApi
import io.github.onneau.sceneradio.AppVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Soort regel in het logboek; bepaalt het label en onder welk filter de regel valt. */
enum class LogCategory(val label: String, val group: LogGroup?) {
    START("START", null),
    SCHERM("SCHERM", LogGroup.SCHERM),
    TAB("TAB", LogGroup.SCHERM),
    SENSOR("SENSOR", LogGroup.SCHERM),
    AFSPELEN("AFSPELEN", LogGroup.AFSPELEN),
    NETWERK("NETWERK", LogGroup.AFSPELEN),
    COVER("COVER", LogGroup.AFSPELEN),
    HANGT("HANGT", LogGroup.FOUTEN),
    FOUT("FOUT", LogGroup.FOUTEN),
    DOWNLOADS("DOWNLOADS", LogGroup.UPDATE),
    UPDATE("UPDATE", LogGroup.UPDATE),
    AUTO("AUTO", LogGroup.AUTO),
    LOGBOEK("LOGBOEK", null)
}

/** De filterknoppen boven het logboek. */
enum class LogGroup(val title: String) {
    FOUTEN("Fouten"),
    SCHERM("Scherm"),
    UPDATE("Update"),
    AFSPELEN("Afspelen"),
    AUTO("Auto")
}

/** Eén regel uit het logboekbestand, uit elkaar gehaald voor de weergave. */
data class LogEntry(
    val date: String,
    val time: String,
    val label: String,
    val message: String
) {
    val group: LogGroup?
        get() = LogCategory.values().firstOrNull { it.label == label }?.group
}

/** Een crash of vastloper die nog niet is bekeken (voor de melding bovenaan het logboek). */
data class LogProblem(val kind: String, val timeMillis: Long, val text: String) {
    val isHang: Boolean get() = kind == KIND_HANG

    companion object {
        const val KIND_CRASH = "crash"
        const val KIND_HANG = "hang"
    }
}

/**
 * Het logboek van de app: een tekstbestand op het toestel (alleen de laatste 7 dagen, hooguit 500 KB) met wat de
 * app deed, ook na een crash of vastloper. Er wordt niets vanzelf verstuurd. Tokens en wachtwoorden komen er
 * niet in: de tekst gaat door [mask]. Alle schrijfwerk gebeurt op een eigen achtergrondthread; alleen bij een
 * crash wordt direct geschreven, omdat het proces dan stopt.
 *
 * Wat erin komt: start en einde, schermwissels en draaien (met de stand van "automatisch draaien"), een crash
 * (met de foutmelding), een vastloper (het scherm reageert meer dan 3 seconden niet, met waar het vastzit),
 * afspeelfouten, updates, opslaan in Downloads en Android Auto. Met "Uitgebreid loggen" ook elke tabwissel,
 * afspeelstap en netwerkfout, en wat de draaisensor van het toestel ziet.
 */
object AppLog {

    private const val DIR_NAME = "applog"
    private const val FILE_NAME = "sceneradio.log"
    private const val MAX_BYTES = 500L * 1024
    private const val KEEP_DAYS = 7
    private const val MAX_LINE_CHARS = 600
    private const val LABEL_WIDTH = 9
    private const val TIME_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"
    private const val STAMP_LENGTH = 23

    private const val PREFS = "applog"
    private const val KEY_VERBOSE = "verbose"
    private const val KEY_PROBLEM_KIND = "problem_kind"
    private const val KEY_PROBLEM_TIME = "problem_time"
    private const val KEY_PROBLEM_TEXT = "problem_text"
    private const val KEY_LAST_EXIT = "last_exit_timestamp"

    // Zolang het hoofdscherm zo lang niet reageert, melden we een vastloper (en later nog eens met de stand van dat moment).
    private const val HANG_BANNER_MS = 5_000L
    private val HANG_REPORTS = longArrayOf(3_000L, 8_000L, 15_000L, 30_000L, 60_000L)

    private val lock = Any()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ew-log").apply { isDaemon = true }
    }
    private val stampFormat = ThreadLocal<SimpleDateFormat>()

    @Volatile private var logFile: File? = null
    @Volatile private var app: Application? = null
    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var approxSize = 0L

    /** Geeft de actuele tokens, zodat die nooit in het logboek komen (ook als ze toevallig in een foutmelding staan). */
    @Volatile var secretsProvider: () -> List<String> = { emptyList() }

    private val _verbose = MutableStateFlow(false)
    val verbose: StateFlow<Boolean> = _verbose.asStateFlow()

    private val _problem = MutableStateFlow<LogProblem?>(null)
    val problem: StateFlow<LogProblem?> = _problem.asStateFlow()

    // Telt op bij elke nieuwe regel; het logboekscherm leest het bestand opnieuw als dit verandert.
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    private var startedActivities = 0
    private var watchdog: Watchdog? = null
    private var orientationListener: OrientationEventListener? = null

    // ---------------------------------------------------------------------------------------------------------
    // Opstarten
    // ---------------------------------------------------------------------------------------------------------

    /** Aanroepen vanuit Application.onCreate, voor al het andere. */
    fun init(application: Application) {
        if (app != null) return
        app = application
        val sharedPrefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = sharedPrefs
        val dir = File(application.filesDir, DIR_NAME).apply { mkdirs() }
        val file = File(dir, FILE_NAME)
        logFile = file
        approxSize = if (file.exists()) file.length() else 0L
        _verbose.value = sharedPrefs.getBoolean(KEY_VERBOSE, false)
        loadProblem()

        installCrashHandler()
        executor.execute { trim(KEEP_DAYS) }

        i(
            LogCategory.START,
            "versie ${AppVersion.NUMBER} (build ${AppVersion.BUILD}), Android ${Build.VERSION.RELEASE} " +
                "(API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            executor.execute { runCatching { logPreviousExit(application) } }
        }
        registerLifecycle(application)
    }

    // ---------------------------------------------------------------------------------------------------------
    // Regels toevoegen
    // ---------------------------------------------------------------------------------------------------------

    /** Een gewone regel: komt altijd in het logboek. */
    fun i(category: LogCategory, message: String) = add(category, message, sync = false)

    /** Een uitgebreide regel: alleen met "Uitgebreid loggen" aan. */
    fun v(category: LogCategory, message: String) {
        if (_verbose.value) add(category, message, sync = false)
    }

    /** Een fout, met (de eerste regels van) de foutmelding. */
    fun e(category: LogCategory, message: String, error: Throwable? = null) {
        val text = if (error == null) message else message + "\n" + describe(error, 12)
        add(category, text, sync = false)
    }

    private fun add(category: LogCategory, message: String, sync: Boolean) {
        val file = logFile ?: return
        val timeText = stamp(System.currentTimeMillis())
        val builder = StringBuilder()
        for (rawLine in mask(message).split('\n')) {
            val line = rawLine.trimEnd('\r')
            if (line.isBlank()) continue
            builder.append(timeText).append(' ')
                .append(category.label.padEnd(LABEL_WIDTH)).append(' ')
                .append(if (line.length > MAX_LINE_CHARS) line.take(MAX_LINE_CHARS) + "…" else line)
                .append('\n')
        }
        if (builder.isEmpty()) return
        val text = builder.toString()
        if (sync) {
            writeText(file, text)
        } else {
            executor.execute { writeText(file, text) }
        }
    }

    private fun writeText(file: File, text: String) {
        try {
            synchronized(lock) {
                file.parentFile?.mkdirs()
                file.appendText(text, Charsets.UTF_8)
                approxSize += text.length
                if (approxSize > MAX_BYTES) trim(KEEP_DAYS)
            }
            _revision.value = _revision.value + 1
        } catch (e: Exception) {
            // Het logboek mag de app nooit in de weg zitten.
        }
    }

    /** Haalt regels ouder dan [days] dagen weg en houdt het bestand onder de grens (de oudste regels gaan eerst). */
    private fun trim(days: Int) {
        val file = logFile ?: return
        try {
            synchronized(lock) {
                if (!file.exists()) return
                val limit = stamp(System.currentTimeMillis() - days * 24L * 60 * 60 * 1000)
                val lines = file.readLines(Charsets.UTF_8).filter { it.length < STAMP_LENGTH || it.substring(0, STAMP_LENGTH) >= limit }
                var total = lines.sumOf { it.length + 1L }
                var from = 0
                val target = MAX_BYTES * 8 / 10
                while (total > target && from < lines.size) {
                    total -= lines[from].length + 1L
                    from++
                }
                val kept = lines.subList(from, lines.size)
                file.writeText(if (kept.isEmpty()) "" else kept.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
                approxSize = file.length()
            }
        } catch (e: Exception) {
            // Zie writeText.
        }
    }

    private fun stamp(millis: Long): String {
        var format = stampFormat.get()
        if (format == null) {
            format = SimpleDateFormat(TIME_FORMAT, Locale.US)
            stampFormat.set(format)
        }
        return format.format(Date(millis))
    }

    // ---------------------------------------------------------------------------------------------------------
    // Tokens en geheimen eruit halen
    // ---------------------------------------------------------------------------------------------------------

    private val urlQuery = Regex("""(https?://[^\s?"'<>)]+)\?[^\s"'<>)]*""")
    private val urlLogin = Regex("""(https?://)[^\s/@"'<>]+@""")
    private val bearer = Regex("""(?i)\bbearer\s+[A-Za-z0-9._~+/=-]{8,}""")
    private val secretPair = Regex("""(?i)\b(access_token|token|password|passwd|wachtwoord|authorization|api_key|apikey)\s*[:=]\s*\S+""")
    private val githubToken = Regex("""(ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{10,}|github_pat_[A-Za-z0-9_]{10,}""")

    /** Haalt tokens, wachtwoorden, inloggegevens in links en alles achter een "?" in links uit de tekst. */
    fun mask(text: String): String {
        var result = text
        for (secret in secretsProvider()) {
            val s = secret.trim()
            if (s.length >= 6) result = result.replace(s, "***")
        }
        result = githubToken.replace(result, "***")
        result = urlLogin.replace(result) { it.groupValues[1] + "***@" }
        result = urlQuery.replace(result) { it.groupValues[1] + "?…" }
        result = bearer.replace(result, "Bearer ***")
        result = secretPair.replace(result) { it.groupValues[1] + "=***" }
        return result
    }

    // ---------------------------------------------------------------------------------------------------------
    // Crash
    // ---------------------------------------------------------------------------------------------------------

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val summary = "${error.javaClass.name}: ${error.message.orEmpty()}".take(300)
                add(LogCategory.FOUT, "crash in thread \"${thread.name}\"\n${describe(error, 25)}", sync = true)
                setProblem(LogProblem(LogProblem.KIND_CRASH, System.currentTimeMillis(), mask(summary)), commit = true)
            } catch (e: Throwable) {
                // Nooit het afhandelen van de crash zelf laten mislukken.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** De foutmelding met de eerste [maxFrames] regels van waar het misging, en dezelfde voor de oorzaak daaronder. */
    private fun describe(error: Throwable, maxFrames: Int): String {
        val out = StringBuilder()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 4) {
            if (depth > 0) out.append("veroorzaakt door: ")
            out.append(current.javaClass.name)
            current.message?.let { out.append(": ").append(it) }
            out.append('\n')
            current.stackTrace.take(maxFrames).forEach { out.append("  at ").append(it).append('\n') }
            val next = current.cause
            current = if (next === current) null else next
            depth++
        }
        return out.toString().trimEnd()
    }

    // ---------------------------------------------------------------------------------------------------------
    // Melding "de app is vastgelopen"
    // ---------------------------------------------------------------------------------------------------------

    private fun loadProblem() {
        val p = prefs ?: return
        val kind = p.getString(KEY_PROBLEM_KIND, null) ?: return
        _problem.value = LogProblem(kind, p.getLong(KEY_PROBLEM_TIME, 0L), p.getString(KEY_PROBLEM_TEXT, "").orEmpty())
    }

    private fun setProblem(problem: LogProblem, commit: Boolean = false) {
        _problem.value = problem
        val editor = prefs?.edit() ?: return
        editor.putString(KEY_PROBLEM_KIND, problem.kind)
            .putLong(KEY_PROBLEM_TIME, problem.timeMillis)
            .putString(KEY_PROBLEM_TEXT, problem.text)
        if (commit) editor.commit() else editor.apply()
    }

    /** De melding is bekeken. */
    fun clearProblem() {
        _problem.value = null
        prefs?.edit()?.remove(KEY_PROBLEM_KIND)?.remove(KEY_PROBLEM_TIME)?.remove(KEY_PROBLEM_TEXT)?.apply()
    }

    // ---------------------------------------------------------------------------------------------------------
    // Waarom stopte de vorige keer? (Android 11 en nieuwer houdt dat zelf bij)
    // ---------------------------------------------------------------------------------------------------------

    @RequiresApi(Build.VERSION_CODES.R)
    private fun logPreviousExit(context: Context) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val sharedPrefs = prefs ?: return
        val lastSeen = sharedPrefs.getLong(KEY_LAST_EXIT, 0L)
        val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 5)
            .filter { it.timestamp > lastSeen }
            .sortedBy { it.timestamp }
        if (exits.isEmpty()) return
        sharedPrefs.edit().putLong(KEY_LAST_EXIT, exits.last().timestamp).apply()
        for (info in exits) {
            val name = exitReasonName(info.reason)
            val detail = info.description?.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
            val whenText = stamp(info.timestamp).substring(0, 19)
            val serious = info.reason == ApplicationExitInfo.REASON_ANR ||
                info.reason == ApplicationExitInfo.REASON_CRASH ||
                info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                info.reason == ApplicationExitInfo.REASON_INITIALIZATION_FAILURE ||
                info.reason == ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE
            val category = if (serious) LogCategory.FOUT else LogCategory.START
            i(category, "vorige run ($whenText) beëindigd door Android: $name$detail")
            if (info.reason == ApplicationExitInfo.REASON_ANR) {
                logAnrTrace(info)
                if (_problem.value == null || (_problem.value?.timeMillis ?: 0L) < info.timestamp) {
                    setProblem(LogProblem(LogProblem.KIND_HANG, info.timestamp, "Android meldde dat de app niet meer reageerde"))
                }
            } else if (info.reason == ApplicationExitInfo.REASON_CRASH || info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
                val existing = _problem.value
                if (existing == null || kotlin.math.abs(existing.timeMillis - info.timestamp) > 60_000L) {
                    setProblem(LogProblem(LogProblem.KIND_CRASH, info.timestamp, "Android meldde een crash ($name)"))
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun logAnrTrace(info: ApplicationExitInfo) {
        try {
            val stream = info.traceInputStream ?: return
            BufferedReader(InputStreamReader(stream)).use { reader ->
                var inMain = false
                var count = 0
                val out = StringBuilder()
                while (count < 400) {
                    val line = reader.readLine() ?: break
                    count++
                    if (!inMain) {
                        if (line.startsWith("\"main\"")) {
                            inMain = true
                            out.append(line).append('\n')
                        }
                    } else {
                        if (line.isBlank()) break
                        out.append(line).append('\n')
                        if (out.length > 6000) break
                    }
                }
                if (out.isNotEmpty()) i(LogCategory.HANGT, "hoofdthread tijdens de vastloper:\n" + out.toString().trimEnd())
            }
        } catch (e: Exception) {
            // Geen trace beschikbaar; de reden hierboven staat er al.
        }
    }

    private fun exitReasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_ANR -> "app reageerde niet (ANR)"
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash (native)"
        ApplicationExitInfo.REASON_EXIT_SELF -> "app sloot zichzelf af"
        ApplicationExitInfo.REASON_SIGNALED -> "beëindigd met signaal"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "te weinig geheugen"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "toestemming gewijzigd"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "te veel gebruik van het toestel"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "door de gebruiker gestopt"
        ApplicationExitInfo.REASON_USER_STOPPED -> "gebruiker stopte de app"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "een afhankelijk onderdeel stopte"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "starten mislukte"
        ApplicationExitInfo.REASON_OTHER -> "andere reden"
        else -> "code $reason"
    }

    // ---------------------------------------------------------------------------------------------------------
    // Scherm, draaien en levenscyclus
    // ---------------------------------------------------------------------------------------------------------

    private fun registerLifecycle(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                i(
                    LogCategory.SCHERM,
                    "${activity.javaClass.simpleName} ${if (savedInstanceState == null) "gestart" else "opnieuw opgebouwd"}: " +
                        describeScreen(activity)
                )
            }

            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                v(LogCategory.SCHERM, "app zichtbaar")
                updateWatchers()
            }

            override fun onActivityResumed(activity: Activity) {
                v(LogCategory.SCHERM, "${activity.javaClass.simpleName} in beeld")
            }

            override fun onActivityPaused(activity: Activity) {
                v(LogCategory.SCHERM, "${activity.javaClass.simpleName} niet meer actief")
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                v(LogCategory.SCHERM, "app op de achtergrond")
                updateWatchers()
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                i(
                    LogCategory.SCHERM,
                    "${activity.javaClass.simpleName} gesloten" +
                        if (activity.isChangingConfigurations) " (wordt opnieuw opgebouwd door een wijziging)" else ""
                )
            }
        })
    }

    /** Aanroepen bij onConfigurationChanged van een activiteit (draaien, schermgrootte). */
    fun logConfigurationChanged(activity: Activity, config: Configuration) {
        i(LogCategory.SCHERM, "gewijzigd → ${describeScreen(activity, config)}")
    }

    /** Wat de compositie van het scherm ziet; handig om te zien of het draaien wel bij de interface aankomt. */
    fun logLayoutOrientation(landscape: Boolean, widthDp: Int, heightDp: Int) {
        i(LogCategory.SCHERM, "interface tekent ${if (landscape) "liggend" else "staand"} (${widthDp}×${heightDp} dp)")
    }

    fun logTab(name: String) = v(LogCategory.TAB, name)

    /** "staand 1080×2400 px, 411×914 dp, scherm 0°, draaien: AAN, vergrendeling: geen". */
    fun describeScreen(activity: Activity, config: Configuration = activity.resources.configuration): String {
        val orientation = when (config.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> "liggend"
            Configuration.ORIENTATION_PORTRAIT -> "staand"
            else -> "onbekend"
        }
        val metrics = activity.resources.displayMetrics
        val locked = requestedOrientationName(activity.requestedOrientation)
        return "$orientation ${metrics.widthPixels}×${metrics.heightPixels} px, " +
            "${config.screenWidthDp}×${config.screenHeightDp} dp, scherm ${displayRotation(activity)}, " +
            "draaien: ${autoRotateText(activity)}, appvoorkeur: $locked"
    }

    private fun autoRotateText(context: Context): String = try {
        when (Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, -1)) {
            1 -> "AAN"
            0 -> "UIT"
            else -> "onbekend"
        }
    } catch (e: Exception) {
        "onbekend"
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(activity: Activity): String = try {
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display?.rotation
        } else {
            activity.windowManager.defaultDisplay.rotation
        }
        when (rotation) {
            Surface.ROTATION_0 -> "0°"
            Surface.ROTATION_90 -> "90°"
            Surface.ROTATION_180 -> "180°"
            Surface.ROTATION_270 -> "270°"
            else -> "?"
        }
    } catch (e: Exception) {
        "?"
    }

    private fun requestedOrientationName(value: Int): String = when (value) {
        -1 -> "volgt toestel"
        0 -> "altijd liggend"
        1 -> "altijd staand"
        2 -> "gebruikersvoorkeur"
        3 -> "zoals de app eronder"
        4 -> "sensor"
        5 -> "geen sensor"
        6 -> "sensor liggend"
        7 -> "sensor staand"
        8 -> "omgekeerd liggend"
        9 -> "omgekeerd staand"
        10 -> "volledige sensor"
        11 -> "gebruiker liggend"
        12 -> "gebruiker staand"
        13 -> "volledige gebruiker"
        14 -> "vergrendeld"
        else -> "code $value"
    }

    /** Gegevens van het toestel voor bij het delen (alleen als je dat aanvinkt): geen naam, geen e-mailadres. */
    fun deviceInfo(context: Context): String {
        val config = context.resources.configuration
        val metrics = context.resources.displayMetrics
        val uiType = when (config.uiMode and Configuration.UI_MODE_TYPE_MASK) {
            Configuration.UI_MODE_TYPE_CAR -> "auto"
            Configuration.UI_MODE_TYPE_TELEVISION -> "tv"
            Configuration.UI_MODE_TYPE_NORMAL -> "gewoon"
            else -> "anders"
        }
        return buildString {
            appendLine("SceneRadio ${AppVersion.NUMBER} (build ${AppVersion.BUILD})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Scherm ${metrics.widthPixels}×${metrics.heightPixels} px, ${config.screenWidthDp}×${config.screenHeightDp} dp, dichtheid ${metrics.densityDpi} dpi")
            appendLine("Stand ${if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) "liggend" else "staand"}, automatisch draaien: ${autoRotateText(context)}")
            append("Type toestel: $uiType")
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Uitgebreid loggen
    // ---------------------------------------------------------------------------------------------------------

    fun setVerbose(enabled: Boolean) {
        if (_verbose.value == enabled) return
        add(LogCategory.LOGBOEK, "uitgebreid loggen ${if (enabled) "aan" else "uit"}", sync = false)
        _verbose.value = enabled
        prefs?.edit()?.putBoolean(KEY_VERBOSE, enabled)?.apply()
        updateWatchers()
    }

    private fun updateWatchers() {
        val visible = startedActivities > 0
        // Bewaking van de hoofdthread: zolang de app in beeld is.
        if (visible && watchdog == null) {
            watchdog = Watchdog().also { it.start() }
        } else if (!visible && watchdog != null) {
            watchdog?.stopNow()
            watchdog = null
        }
        // Draaisensor: alleen bij uitgebreid loggen en zolang de app in beeld is.
        val context = app ?: return
        val wantSensor = visible && _verbose.value
        if (wantSensor && orientationListener == null) {
            val listener = object : OrientationEventListener(context) {
                private var lastBucket = -1
                override fun onOrientationChanged(orientation: Int) {
                    if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return
                    val bucket = ((orientation + 45) / 90) % 4
                    if (bucket == lastBucket) return
                    lastBucket = bucket
                    val text = when (bucket) {
                        0 -> "rechtop (staand)"
                        1 -> "gekanteld, liggend (90°)"
                        2 -> "ondersteboven"
                        else -> "gekanteld, liggend (270°)"
                    }
                    v(LogCategory.SENSOR, "draaisensor ziet het toestel $text, gemeten $orientation°")
                }
            }
            if (listener.canDetectOrientation()) {
                listener.enable()
                orientationListener = listener
            } else {
                v(LogCategory.SENSOR, "dit toestel heeft geen draaisensor die de app kan lezen")
            }
        } else if (!wantSensor && orientationListener != null) {
            orientationListener?.disable()
            orientationListener = null
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Bewaking van de hoofdthread
    // ---------------------------------------------------------------------------------------------------------

    private class Watchdog : Thread("ew-watchdog") {
        @Volatile private var running = true
        @Volatile private var pending = false
        @Volatile private var completedAt = 0L
        private val main = Handler(Looper.getMainLooper())
        private val ticker = Runnable {
            completedAt = SystemClock.uptimeMillis()
            pending = false
        }

        init {
            isDaemon = true
        }

        fun stopNow() {
            running = false
            interrupt()
        }

        private var lastLoggedStage = 0

        override fun run() {
            var postedAt = 0L
            var lastCheck = SystemClock.uptimeMillis()
            while (running) {
                if (!pending) {
                    pending = true
                    postedAt = SystemClock.uptimeMillis()
                    main.post(ticker)
                }
                try {
                    Thread.sleep(1_000L)
                } catch (e: InterruptedException) {
                    return
                }
                val now = SystemClock.uptimeMillis()
                // Zelf te lang weggeweest (toestel sliep of het proces was bevroren): dat is geen vastloper.
                if (now - lastCheck > 2_500L) {
                    lastCheck = now
                    lastLoggedStage = 0
                    if (pending) postedAt = now
                    continue
                }
                lastCheck = now
                if (pending) {
                    val stalled = now - postedAt
                    var stage = 0
                    for (k in HANG_REPORTS.indices) {
                        if (stalled >= HANG_REPORTS[k]) stage = k + 1
                    }
                    if (stage > lastLoggedStage) {
                        lastLoggedStage = stage
                        logStall(stalled)
                    }
                } else if (lastLoggedStage > 0) {
                    val total = (completedAt - postedAt).coerceAtLeast(0L)
                    lastLoggedStage = 0
                    i(LogCategory.HANGT, "hoofdthread weer vrij na ${total / 1000} s")
                    if (total >= HANG_BANNER_MS) {
                        setProblem(
                            LogProblem(
                                LogProblem.KIND_HANG,
                                System.currentTimeMillis(),
                                "het scherm reageerde ${total / 1000} seconden niet"
                            )
                        )
                    }
                }
            }
        }

        private fun logStall(stalledMs: Long) {
            val frames = Looper.getMainLooper().thread.stackTrace
            val text = StringBuilder("hoofdthread wacht al ${stalledMs / 1000} s; hij zit hier vast:")
            frames.take(30).forEach { text.append("\n  at ").append(it) }
            i(LogCategory.HANGT, text.toString())
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Lezen, delen, wissen
    // ---------------------------------------------------------------------------------------------------------

    /** Het hele logboek als tekst (nieuwste regel onderaan). */
    fun readText(): String {
        val file = logFile ?: return ""
        return try {
            synchronized(lock) { if (file.exists()) file.readText(Charsets.UTF_8) else "" }
        } catch (e: Exception) {
            ""
        }
    }

    /** De regels van het logboek, uit elkaar gehaald voor de weergave. */
    fun readEntries(): List<LogEntry> =
        readText().lineSequence().mapNotNull { parse(it) }.toList()

    private fun parse(line: String): LogEntry? {
        if (line.length < STAMP_LENGTH + 2 || line[4] != '-' || line[7] != '-') return null
        val date = line.substring(0, 10)
        val time = line.substring(11, 19)
        val rest = line.substring(STAMP_LENGTH + 1)
        val label = rest.take(LABEL_WIDTH).trim()
        val message = if (rest.length > LABEL_WIDTH + 1) rest.substring(LABEL_WIDTH + 1) else ""
        return LogEntry(date, time, label, message)
    }

    /** Het logboek leegmaken. De melding over een crash verdwijnt ook. */
    fun clear() {
        val file = logFile ?: return
        executor.execute {
            try {
                synchronized(lock) {
                    file.writeText("", Charsets.UTF_8)
                    approxSize = 0L
                }
            } catch (e: Exception) {
                // Zie writeText.
            }
            _revision.value = _revision.value + 1
        }
        clearProblem()
        i(LogCategory.LOGBOEK, "logboek gewist")
    }

    /**
     * De tekst voor het delen of opslaan: eerst (naar keuze) de apparaatgegevens, dan het logboek, en tot slot
     * (naar keuze) de systeemlog van alleen deze app. Doe dit niet op de hoofdthread.
     */
    fun exportText(context: Context, withSystemLog: Boolean, withDeviceInfo: Boolean): String {
        val out = StringBuilder()
        out.appendLine("== SceneRadio logboek, gemaakt ${stamp(System.currentTimeMillis()).substring(0, 19)} ==")
        if (withDeviceInfo) {
            out.appendLine()
            out.appendLine("-- Apparaatgegevens --")
            out.appendLine(deviceInfo(context))
        }
        out.appendLine()
        out.appendLine("-- Logboek van de app --")
        val body = readText().trimEnd()
        out.appendLine(if (body.isEmpty()) "(leeg)" else body)
        if (withSystemLog) {
            out.appendLine()
            out.appendLine("-- Systeemlog van alleen deze app (Android) --")
            out.appendLine(mask(systemLog()))
        }
        return out.toString()
    }

    /** De laatste regels die Android zelf over deze app bijhield; apps kunnen alleen hun eigen regels lezen. */
    private fun systemLog(): String {
        return try {
            val process = ProcessBuilder("logcat", "-d", "-v", "time", "-t", "700")
                .redirectErrorStream(true)
                .start()
            val lines = ArrayDeque<String>()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var count = 0
                while (count < 3000) {
                    val line = reader.readLine() ?: break
                    lines.addLast(line.take(400))
                    if (lines.size > 700) lines.removeFirst()
                    count++
                }
            }
            runCatching { process.destroy() }
            if (lines.isEmpty()) "(geen regels beschikbaar)" else lines.joinToString("\n")
        } catch (e: Exception) {
            "(de systeemlog kon niet worden gelezen: ${e.javaClass.simpleName})"
        }
    }

    /** Naam voor het bestand bij delen en opslaan, bijvoorbeeld sceneradio-logboek-2026-11-12-1650.txt. */
    fun exportFileName(): String =
        "sceneradio-logboek-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date()) + ".txt"
}
