package de.tobidervogel.aniworld.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import de.tobidervogel.aniworld.Protokoll
import de.tobidervogel.aniworld.net.Netz
import de.tobidervogel.aniworld.site.Sprache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Lädt etwas einmal und merkt sich Ergebnis oder Fehler; lebt außerhalb der Komposition. */
class Ladung<T>(private val scope: CoroutineScope, private val holen: suspend () -> T) {
    var daten by mutableStateOf<T?>(null)
        private set
    var fehler by mutableStateOf<String?>(null)
        private set
    var laeuft by mutableStateOf(false)
        private set

    fun start(neu: Boolean = false) {
        if (laeuft || (daten != null && !neu)) return
        laeuft = true
        fehler = null
        scope.launch {
            try {
                daten = holen()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Protokoll.fehler("Laden fehlgeschlagen", e)
                fehler = e.message ?: e.javaClass.simpleName
            } finally {
                laeuft = false
            }
        }
    }
}

@Composable
fun Cover(url: String?, modifier: Modifier = Modifier, ecken: Dp = 10.dp) {
    val ctx = LocalContext.current
    Box(modifier.clip(RoundedCornerShape(ecken)).background(Farbe.flaeche2)) {
        if (url != null) {
            AsyncImage(
                // Manche Seiten liefern Cover nur mit eigenem Referer aus.
                model = ImageRequest.Builder(ctx).data(url).addHeader("Referer", Netz.ursprung(url) + "/").build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun SprachMarke(s: Sprache, aktiv: Boolean = true) {
    Text(
        s.kurz,
        style = MaterialTheme.typography.labelSmall,
        color = if (aktiv) Farbe.text else Farbe.text3,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (aktiv) Farbe.flaeche2 else Farbe.flaeche)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Auswahl-Pille für Sprache, Hoster, Zahlen. */
@Composable
fun Pille(text: String, gewaehlt: Boolean, aktiv: Boolean = true, onClick: () -> Unit) {
    val form = RoundedCornerShape(50)
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = when {
            !aktiv -> Farbe.text3
            gewaehlt -> Farbe.hintergrund
            else -> Farbe.text
        },
        modifier = Modifier
            .clip(form)
            .background(if (gewaehlt && aktiv) Farbe.akzent else Farbe.flaeche2)
            .border(1.dp, if (gewaehlt && aktiv) Farbe.akzent else Farbe.linie, form)
            .clickable(enabled = aktiv, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
fun Kopfzeile(titel: String, zurueck: (() -> Unit)? = null, rechts: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (zurueck != null) {
            IconButton(onClick = zurueck) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            titel,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        rechts()
    }
}

@Composable
fun Abschnitt(titel: String, modifier: Modifier = Modifier, rechts: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(titel, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        rechts()
    }
}

@Composable
fun LadeKreis(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Farbe.akzent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun Fehlerkasten(text: String, modifier: Modifier = Modifier, nochmal: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(14.dp)).background(Farbe.flaeche).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = Farbe.fehler)
        Text(text, color = Farbe.text2, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        if (nochmal != null) TextButton(onClick = nochmal) { Text("Nochmal versuchen", fontWeight = FontWeight.SemiBold) }
    }
}

fun dauerText(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
