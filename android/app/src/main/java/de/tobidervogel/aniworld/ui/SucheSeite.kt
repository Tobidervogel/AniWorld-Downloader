package de.tobidervogel.aniworld.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.tobidervogel.aniworld.App
import de.tobidervogel.aniworld.site.Genre
import de.tobidervogel.aniworld.site.Karte
import de.tobidervogel.aniworld.site.Treffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SucheSeite(zurueck: () -> Unit, oeffnen: (Ziel) -> Unit) {
    val app = App.self
    var text by rememberSaveable { mutableStateOf("") }
    var treffer by remember { mutableStateOf<List<Treffer>?>(null) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var laeuft by remember { mutableStateOf(false) }
    val fokus = remember { FocusRequester() }
    val tastatur = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { fokus.requestFocus() }
    // Erst suchen, wenn 350 ms nichts getippt wurde: spart Anfragen an die Seite.
    LaunchedEffect(text) {
        if (text.trim().length < 2) { treffer = null; fehler = null; return@LaunchedEffect }
        delay(350)
        laeuft = true
        try {
            treffer = app.aniworld.suche(text)
            fehler = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fehler = e.message
        } finally {
            laeuft = false
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.statusBarsPadding().padding(end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = zurueck) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") }
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Titel eingeben") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Farbe.flaeche2,
                    unfocusedContainerColor = Farbe.flaeche2,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Farbe.akzent,
                ),
                trailingIcon = {
                    if (text.isNotEmpty()) IconButton(onClick = { text = "" }) { Icon(Icons.Rounded.Close, "Leeren") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { tastatur?.hide() }),
                modifier = Modifier.weight(1f).focusRequester(fokus),
            )
        }
        when {
            fehler != null -> Fehlerkasten(fehler!!)
            laeuft && treffer == null -> LadeKreis()
            treffer?.isEmpty() == true -> Text("Nichts gefunden.", color = Farbe.text2, modifier = Modifier.padding(20.dp))
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(treffer.orEmpty(), key = { it.url }) { t ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { tastatur?.hide(); oeffnen(Ziel.Serie(t.url, t.titel, null)) }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Text(t.titel, style = MaterialTheme.typography.titleMedium)
                    if (t.beschreibung.isNotBlank()) {
                        Text(t.beschreibung, color = Farbe.text2, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider(color = Farbe.flaeche2, modifier = Modifier.padding(horizontal = 18.dp))
            }
        }
    }
}

/** Alle Animes eines Genres als Raster, Seite für Seite nachgeladen. */
@Composable
fun GenreSeite(genre: Genre, zurueck: () -> Unit, oeffnen: (Ziel) -> Unit) {
    val app = App.self
    val karten = remember(genre.slug) { mutableStateListOf<Karte>() }
    var seite by remember(genre.slug) { mutableIntStateOf(0) }
    var weiter by remember(genre.slug) { mutableStateOf(true) }
    var laeuft by remember { mutableStateOf(false) }
    var fehler by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val raster = rememberLazyGridState()

    fun mehr() {
        if (laeuft || !weiter) return
        laeuft = true
        scope.launch {
            try {
                val (neu, gibtMehr) = app.aniworld.genre(genre.slug, seite + 1)
                karten += neu.filter { k -> karten.none { it.url == k.url } }
                seite++
                weiter = gibtMehr && neu.isNotEmpty()
                fehler = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fehler = e.message
            } finally {
                laeuft = false
            }
        }
    }

    LaunchedEffect(genre.slug) { if (karten.isEmpty()) mehr() }
    LaunchedEffect(raster) {
        snapshotFlow { raster.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { letzter ->
            if (letzter >= karten.size - 6) mehr()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Kopfzeile(genre.name, zurueck)
        fehler?.let { Fehlerkasten(it) { mehr() } }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = raster,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            items(karten, key = { it.url }) { k ->
                Poster(k.titel, k.cover, Modifier.clickable { oeffnen(Ziel.Serie(k.url, k.titel, k.cover)) }, breite = 112.dp)
            }
            if (laeuft) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) { LadeKreis() }
        }
    }
}
