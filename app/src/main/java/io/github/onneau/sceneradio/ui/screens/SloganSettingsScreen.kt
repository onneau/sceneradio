package io.github.onneau.sceneradio.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.onneau.sceneradio.data.model.VisualizerSettings
import io.github.onneau.sceneradio.ui.RadioViewModel

/**
 * Instellingen > Weergave > Visualizer > Slogans: de teksten die onder de zendernaam langskomen.
 * Alle slogans staan in één lijst, ook de standaardslogans: ze zijn aan te passen, te verwijderen en aan te vullen.
 * Met {tijd} in een slogan toont de visualizer de actuele lokale tijd (de standaardslogan "Local time {tijd}").
 * "Standaardslogans herstellen" zet de standaardteksten terug; je eigen teksten blijven staan.
 */
@Composable
fun SloganSettingsScreen(
    viewModel: RadioViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.visualizerSettings.collectAsState()
    val slogans = settings.slogans
    val defaults = VisualizerSettings.DEFAULT_SLOGANS
    val maxLength = VisualizerSettings.MAX_SLOGAN_LENGTH
    val maxCount = VisualizerSettings.MAX_SLOGANS
    val full = slogans.size >= maxCount

    var draft by rememberSaveable { mutableStateOf("") }
    // De slogan die in het venster wordt aangepast (null = geen venster) en de tekst in het invoerveld.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var editText by rememberSaveable { mutableStateOf("") }

    val draftClean = draft.replace('\n', ' ').trim()
    val draftDuplicate = draftClean.isNotEmpty() && draftClean in slogans
    val timeToken = VisualizerSettings.TIME_TOKEN

    // {tijd} achter de tekst zetten (met een spatie ertussen); past het niet meer, dan blijft de tekst zoals hij is.
    fun withTime(text: String): String {
        val base = if (text.isEmpty() || text.endsWith(" ")) text else "$text "
        return if (base.length + timeToken.length <= maxLength) base + timeToken else text
    }

    fun add() {
        if (draftClean.isEmpty() || full || draftDuplicate) return
        // Nieuwe slogans komen bovenaan, dan zie je meteen dat hij erbij staat.
        viewModel.updateVisualizerSettings { it.copy(slogans = listOf(draftClean) + it.slogans) }
        draft = ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug")
            }
            Text(
                text = "Slogans",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "De tekst onder de zendernaam. Tik op het potlood om een slogan aan te passen, of verwijder hem. " +
                            "Het lettertype toont hoofdletters; accenten vallen weg.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it.replace("\n", "").take(maxLength) },
                            label = { Text("Nieuwe slogan (max. $maxLength tekens)") },
                            supportingText = {
                                Text(
                                    when {
                                        full -> "De lijst is vol ($maxCount slogans). Verwijder er eerst een."
                                        draftDuplicate -> "Deze slogan staat er al."
                                        else -> "${draft.length} / $maxLength tekens"
                                    }
                                )
                            },
                            isError = draftDuplicate,
                            singleLine = true,
                            enabled = !full,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { add() }),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = { add() },
                            enabled = draftClean.isNotEmpty() && !full && !draftDuplicate
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Toevoegen")
                        }
                    }
                    TextButton(
                        onClick = { draft = withTime(draft) },
                        enabled = !full && withTime(draft) != draft
                    ) {
                        Text("Tijd invoegen ($timeToken)")
                    }
                    Text(
                        text = "$timeToken wordt op het scherm de actuele lokale tijd van je telefoon, bijvoorbeeld 13:24. " +
                            "De tijd wordt ingevuld als de slogan begint en blijft staan zolang hij zichtbaar is.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Jouw slogans (${slogans.size} van $maxCount)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                }
            }
            if (slogans.isEmpty()) {
                item {
                    Text(
                        text = "Er zijn geen slogans: de visualizer toont dan geen tekst onder de zendernaam. " +
                            "Voeg een slogan toe, of herstel de standaardslogans.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(slogans, key = { it }) { slogan ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 14.dp, end = 2.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(text = slogan, style = MaterialTheme.typography.bodyLarge)
                            val tags = listOfNotNull(
                                "eigen".takeIf { slogan !in defaults },
                                "met tijd".takeIf { VisualizerSettings.TIME_REGEX.containsMatchIn(slogan) }
                            )
                            if (tags.isNotEmpty()) {
                                Text(
                                    text = tags.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = {
                            editing = slogan
                            editText = slogan
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Aanpassen")
                        }
                        IconButton(
                            onClick = {
                                viewModel.updateVisualizerSettings { it.copy(slogans = it.slogans - slogan) }
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Verwijderen")
                        }
                    }
                }
            }
            item {
                Column(modifier = Modifier.padding(top = 10.dp, bottom = 16.dp)) {
                    OutlinedButton(
                        onClick = {
                            // De standaardteksten terug; wat je zelf hebt toegevoegd of aangepast blijft staan.
                            viewModel.updateVisualizerSettings { it.copy(slogans = defaults + it.slogans.filter { s -> s !in defaults }) }
                        },
                        enabled = defaults.any { it !in slogans },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Standaardslogans herstellen")
                    }
                    Text(
                        text = "Herstellen zet de ${defaults.size} standaardslogans terug zoals ze waren. Slogans die je zelf hebt " +
                            "toegevoegd of aangepast blijven staan (er passen er hooguit $maxCount in de lijst). Een te lange slogan " +
                            "wordt kleiner getoond.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }

    val original = editing
    if (original != null) {
        val cleaned = editText.replace('\n', ' ').trim()
        val duplicate = cleaned != original && cleaned in slogans
        val canSave = cleaned.isNotEmpty() && !duplicate
        fun save() {
            if (!canSave) return
            viewModel.updateVisualizerSettings { current ->
                current.copy(slogans = current.slogans.map { s -> if (s == original) cleaned else s })
            }
            editing = null
        }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Slogan aanpassen") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it.replace("\n", "").take(maxLength) },
                        supportingText = {
                            Text(if (duplicate) "Deze slogan staat er al." else "${editText.length} / $maxLength tekens")
                        },
                        isError = duplicate,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        onClick = { editText = withTime(editText) },
                        enabled = withTime(editText) != editText
                    ) {
                        Text("Tijd invoegen ($timeToken)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { save() }, enabled = canSave) { Text("Opslaan") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("Annuleren") }
            }
        )
    }
}
