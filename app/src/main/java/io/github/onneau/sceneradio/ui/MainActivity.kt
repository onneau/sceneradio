package io.github.onneau.sceneradio.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.onneau.sceneradio.data.log.AppLog
import io.github.onneau.sceneradio.data.log.LogCategory
import io.github.onneau.sceneradio.ui.components.UpdateHost
import io.github.onneau.sceneradio.ui.screens.ImportDialog
import io.github.onneau.sceneradio.ui.screens.PlayerScreen
import io.github.onneau.sceneradio.ui.screens.SettingsScreen
import io.github.onneau.sceneradio.ui.screens.StationsScreen
import io.github.onneau.sceneradio.i18n.tr
import io.github.onneau.sceneradio.ui.theme.SceneRadioTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

sealed class Screen(val route: String, private val nl: String, private val en: String, val icon: ImageVector) {
    /** De naam in de taal van de app; leest de taalkeuze, dus het menu past zich meteen aan. */
    val title: String get() = tr(nl, en)

    /** De vaste Nederlandse naam voor het logboek, onafhankelijk van de gekozen taal. */
    val logName: String get() = nl

    object Player : Screen("player", "Speler", "Player", Icons.Default.Radio)
    object Stations : Screen("stations", "Stations", "Stations", Icons.AutoMirrored.Filled.FormatListBulleted)
    object Favorites : Screen("favorites", "Favorieten", "Favorites", Icons.Default.Star)
    object Settings : Screen("settings", "Instellingen", "Settings", Icons.Default.Settings)
}

// Liggend en tijdens het afspelen: zoveel milliseconden niets aanraken en de menu's verdwijnen.
private const val IMMERSIVE_DELAY_MS = 7_000L

class MainActivity : ComponentActivity() {

    private val viewModel: RadioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val isDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            // POST_NOTIFICATIONS permissie vragen voordat de service start (Android 13+)
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                // Permissie afgehandeld
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            SceneRadioTheme(darkTheme = isDark) {
                // Updatemelding van de app zelf (nieuwe versie, downloaden, eenmalige toestemming).
                UpdateHost(viewModel)

                var currentScreen by remember { mutableStateOf<Screen>(Screen.Player) }
                var previousScreen by remember { mutableStateOf<Screen?>(null) }
                var showImportDialog by remember { mutableStateOf(false) }

                // Deze staten staan hier, boven het wisselen van tabbladen: zo blijft de scrollpositie van
                // de lijsten behouden terwijl je in de speler of instellingen bent.
                val stationsListState = androidx.compose.foundation.lazy.rememberLazyListState()
                val favoritesListState = androidx.compose.foundation.lazy.rememberLazyListState()
                var stationsGridPage by remember { mutableIntStateOf(0) }
                var favoritesGridPage by remember { mutableIntStateOf(0) }

                // Terugknop: van de speler weer naar het tabblad waar je vandaan kwam, in plaats van de app te sluiten.
                BackHandler(enabled = previousScreen != null) {
                    currentScreen = previousScreen ?: Screen.Player
                    previousScreen = null
                }

                // Volledig-scherm-weergave: liggend, muziek aan en op de speler. Na een tijdje zonder aanraking
                // verdwijnen menu en systeembalken en komt de visualizer in beeld. Elke aanraking haalt alles terug.
                val playbackState by viewModel.playbackState.collectAsState()
                val largeControls by viewModel.largeControls.collectAsState()
                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                // Logboek: wat de interface zelf van de stand ziet (komt het draaien wel bij de compositie aan?).
                LaunchedEffect(isLandscape) {
                    AppLog.logLayoutOrientation(isLandscape, configuration.screenWidthDp, configuration.screenHeightDp)
                }
                val canHideMenu = isLandscape && playbackState.isPlaying && currentScreen == Screen.Player
                var immersive by remember { mutableStateOf(false) }
                LaunchedEffect(currentScreen) { AppLog.logTab(currentScreen.logName) }
                // Wordt niet in de compositie gelezen, zodat aanrakingen (ook scrollen) niets opnieuw laten tekenen.
                var lastTouchMs by remember { mutableLongStateOf(0L) }

                LaunchedEffect(canHideMenu) {
                    immersive = false
                    if (!canHideMenu) return@LaunchedEffect
                    snapshotFlow { lastTouchMs }.collectLatest {
                        immersive = false
                        delay(IMMERSIVE_DELAY_MS)
                        immersive = true
                    }
                }

                LaunchedEffect(immersive) {
                    AppLog.v(LogCategory.SCHERM, if (immersive) "volledig scherm aan (menu verborgen)" else "menu in beeld")
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    if (immersive) {
                        controller.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }

                // Scherm blijft aan zolang de visualizer in beeld is; zodra het menu terugkomt of de muziek
                // stopt, geldt weer de gewone schermtime-out.
                DisposableEffect(immersive) {
                    if (immersive) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                }

                // Terug in de visualizer haalt eerst het menu terug (en sluit dus de app niet).
                BackHandler(enabled = immersive) {
                    lastTouchMs = SystemClock.uptimeMillis()
                }

                Scaffold(
                    bottomBar = {
                        if (!immersive) {
                            NavigationBar {
                                val items = listOf(Screen.Player, Screen.Favorites, Screen.Stations, Screen.Settings)
                                items.forEach { screen ->
                                    NavigationBarItem(
                                        icon = {
                                            Icon(
                                                screen.icon,
                                                contentDescription = screen.title,
                                                modifier = if (largeControls) Modifier.size(30.dp) else Modifier
                                            )
                                        },
                                        label = {
                                            Text(
                                                screen.title,
                                                fontSize = if (largeControls) 14.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                                                fontWeight = if (largeControls) FontWeight.SemiBold else null
                                            )
                                        },
                                        alwaysShowLabel = true,
                                        selected = currentScreen == screen,
                                        onClick = {
                                            if (screen != currentScreen) {
                                                previousScreen = currentScreen
                                                currentScreen = screen
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    },
                    containerColor = if (immersive) Color.Black else MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxSize()
                        // Kijkt mee naar aanrakingen zonder ze op te eten: alleen de timer opnieuw starten.
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial)
                                    lastTouchMs = SystemClock.uptimeMillis()
                                }
                            }
                        }
                ) { innerPadding ->
                    when (currentScreen) {
                        Screen.Player -> PlayerScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding),
                            immersive = immersive
                        )
                        Screen.Stations -> StationsScreen(
                            viewModel = viewModel,
                            onOpenImport = { showImportDialog = true },
                            listState = stationsListState,
                            gridPage = stationsGridPage,
                            onGridPageChange = { stationsGridPage = it },
                            modifier = Modifier.padding(innerPadding)
                        )
                        Screen.Favorites -> StationsScreen(
                            viewModel = viewModel,
                            onOpenImport = { showImportDialog = true },
                            favoritesOnly = true,
                            listState = favoritesListState,
                            gridPage = favoritesGridPage,
                            onGridPageChange = { favoritesGridPage = it },
                            modifier = Modifier.padding(innerPadding)
                        )
                        Screen.Settings -> SettingsScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }

                    if (showImportDialog) {
                        ImportDialog(
                            viewModel = viewModel,
                            onDismiss = { showImportDialog = false }
                        )
                    }
                }
            }
        }
    }

    // De activiteit regelt draaien zelf (configChanges in het manifest): de wijziging komt hier binnen en gaat
    // in het logboek, met de stand van "automatisch draaien" van het toestel erbij.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLog.logConfigurationChanged(this, newConfig)
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
