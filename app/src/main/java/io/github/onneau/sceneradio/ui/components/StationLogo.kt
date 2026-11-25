package io.github.onneau.sceneradio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

// "Radio 538" -> "53", "NPO Radio 2" -> "N2", "Sky Radio" -> "SK": het woord "radio" zegt niets.
private fun initialsFor(name: String): String {
    val words = name.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
    val meaningful = words.filter { !it.equals("radio", ignoreCase = true) && !it.equals("fm", ignoreCase = true) }
        .ifEmpty { words }
    val letters = when {
        meaningful.size >= 2 -> "${meaningful[0].first()}${meaningful[1].first()}"
        meaningful.size == 1 -> meaningful[0].take(2)
        else -> "EW"
    }
    return letters.uppercase()
}

/**
 * Zenderlogo. Ontbreekt het logo, of kan het niet geladen worden (kapotte link,
 * niet-ondersteund formaat), dan verschijnt een tegel met de beginletters van de zender.
 */
@Composable
fun StationLogo(
    logoUrl: String?,
    stationName: String,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    imageBackground: Color? = null,
    imagePadding: Dp = size / 12
) {
    var failed by remember(logoUrl) { mutableStateOf(false) }

    if (!logoUrl.isNullOrBlank() && !failed) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(logoUrl)
                .crossfade(true)
                .build(),
            contentDescription = stationName,
            contentScale = ContentScale.Fit,
            onError = { failed = true },
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(imageBackground ?: MaterialTheme.colorScheme.surfaceVariant)
                .padding(imagePadding)
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0284C7), Color(0xFF0F766E))
                    )
                )
        ) {
            Text(
                text = initialsFor(stationName),
                fontSize = (size.value * 0.34f).sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}
