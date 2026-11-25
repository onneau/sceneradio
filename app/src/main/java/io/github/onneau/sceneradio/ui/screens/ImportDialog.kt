package io.github.onneau.sceneradio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.onneau.sceneradio.ui.RadioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDialog(
    viewModel: RadioViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: URL, 1: Bestand/Plakken, 2: Radio Browser

    // Tab 0: URL
    var playlistName by remember { mutableStateOf("Mijn Afspeellijst") }
    var playlistUrl by remember { mutableStateOf("") }

    // Tab 1: Bestand / Tekst
    var m3uRawText by remember { mutableStateOf("") }
    var localFileName by remember { mutableStateOf("Geïmporteerd bestand") }

    // Tab 2: Radio-Browser
    var radioBrowserQuery by remember { mutableStateOf("") }
    val radioBrowserResults by viewModel.radioBrowserSearchResults.collectAsState()
    val isSearchingRadioBrowser by viewModel.isSearchingRadioBrowser.collectAsState()
    val radioBrowserError by viewModel.radioBrowserError.collectAsState()

    val isImporting by viewModel.isImporting.collectAsState()
    val importMessage by viewModel.importMessage.collectAsState()

    // Bestanden kiezen via ACTION_OPEN_DOCUMENT (geen opslagpermissie vereist)
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val maxBytes = 5 * 1024 * 1024
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = stream.read(chunk)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) throw java.io.IOException("Bestand te groot")
                        buffer.write(chunk, 0, read)
                    }
                    val text = buffer.toString("UTF-8")
                    m3uRawText = text
                    localFileName = "Bestand (${text.lines().size} regels)"
                }
            } catch (e: Exception) {
                localFileName = "Bestand kon niet worden gelezen (max 5 MB)"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zenders & Afspeellijsten Toevoegen") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("URL Link") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Bestand / Tekst") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Radio Browser") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isImporting) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = importMessage ?: "Bezig met verwerken…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else when (selectedTab) {
                    0 -> {
                        // URL Import
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = playlistName,
                                onValueChange = { playlistName = it },
                                label = { Text("Naam van afspeellijst") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = playlistUrl,
                                onValueChange = { playlistUrl = it },
                                label = { Text("Directe M3U / M3U8 URL") },
                                placeholder = { Text("https://example.com/radio.m3u") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    1 -> {
                        // Bestand via ACTION_OPEN_DOCUMENT of geplakte tekst
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    // ACTION_OPEN_DOCUMENT via OpenDocument contract
                                    openDocumentLauncher.launch(
                                        arrayOf("*/*", "audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain")
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open lokaal .m3u / .m3u8 bestand")
                            }

                            if (m3uRawText.isNotBlank()) {
                                Text(
                                    text = "Geladen: $localFileName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                text = "Of plak hieronder de #EXTM3U tekst:",
                                style = MaterialTheme.typography.labelMedium
                            )

                            OutlinedTextField(
                                value = m3uRawText,
                                onValueChange = { m3uRawText = it },
                                placeholder = { Text("#EXTM3U\n#EXTINF:-1 tvg-logo=\"...\",Zendernaam\nhttp://stream-url") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                            )
                        }
                    }
                    2 -> {
                        // Radio-Browser catalogus
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = radioBrowserQuery,
                                    onValueChange = { radioBrowserQuery = it },
                                    label = { Text("Zendernaam of tag") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { viewModel.searchRadioBrowser(radioBrowserQuery) }
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Zoeken")
                                }
                            }

                            if (isSearchingRadioBrowser) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                            }

                            if (radioBrowserError != null) {
                                Text(
                                    text = radioBrowserError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            LazyColumn(
                                modifier = Modifier.height(180.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(radioBrowserResults) { st ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.importFromRadioBrowser(st)
                                                onDismiss()
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (!st.favicon.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = st.favicon,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(RoundedCornerShape(6.dp)),
                                                    contentScale = ContentScale.Fit
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = st.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${st.country ?: ""} • ${st.tags ?: ""}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = "Toevoegen",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 0) {
                Button(
                    onClick = {
                        if (playlistUrl.isNotBlank()) {
                            viewModel.importM3uFromUrl(playlistUrl.trim(), playlistName.trim())
                            onDismiss()
                        }
                    },
                    enabled = playlistUrl.isNotBlank() && !isImporting
                ) {
                    Text("Downloaden & Importeren")
                }
            } else if (selectedTab == 1) {
                Button(
                    onClick = {
                        if (m3uRawText.isNotBlank()) {
                            viewModel.importM3uContent(m3uRawText, localFileName, isLocal = true)
                            onDismiss()
                        }
                    },
                    enabled = m3uRawText.isNotBlank() && !isImporting
                ) {
                    Text("Opslaan in App")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Sluiten")
            }
        }
    )
}
