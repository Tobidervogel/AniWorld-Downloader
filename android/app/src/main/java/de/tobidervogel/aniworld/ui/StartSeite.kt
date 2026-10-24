package de.tobidervogel.aniworld.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.tobidervogel.aniworld.App
import de.tobidervogel.aniworld.site.AniWorldParser
import de.tobidervogel.aniworld.site.Karte
import de.tobidervogel.aniworld.site.NeueFolge
import kotlinx.coroutines.CoroutineScope

class StartZustand(scope: CoroutineScope, app: App) {
    val start = Ladung(scope) { app.aniworld.startseite() }
    val neueFolgen = Ladung(scope) { app.aniworld.neueFolgen() }
    val liste = LazyListState()

    fun laden(neu: Boolean = false) {
        start.start(neu)
        neueFolgen.start(neu)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun StartSeite(z: StartZustand, oeffnen: (Ziel) -> Unit) {
    val app = App.self
    LaunchedEffect(Unit) { z.laden() }
    val stand by app.db.stand.collectAsState()
    val verlauf = remember(stand) { app.db.verlauf() }
    val start = z.start.daten

    PullToRefreshBox(
        isRefreshing = z.start.laeuft && start != null,
        onRefresh = { z.laden(neu = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(state = z.liste, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
                    Text("AniWorld", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Farbe.flaeche2)
                            .clickable { oeffnen(Ziel.Suche) }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Search, null, tint = Farbe.text2)
                        Spacer(Modifier.width(10.dp))
                        Text("Anime suchen", color = Farbe.text2)
                    }
                }
            }

            if (verlauf.isNotEmpty()) {
                item { Abschnitt("Zuletzt geöffnet") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(verlauf, key = { it.url }) { v ->
                            Poster(v.titel, v.cover, Modifier.combinedClickable(
                                onClick = { oeffnen(Ziel.Serie(v.url, v.titel, v.cover)) },
                                onLongClick = { app.db.vergessen(v.url) },
                            ))
                        }
                    }
                }
            }

            z.neueFolgen.daten?.takeIf { it.isNotEmpty() }?.let { folgen ->
                item { Abschnitt("Neue Folgen") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(folgen.take(40), key = { it.url }) { f -> NeueFolgeKarte(f, oeffnen) }
                    }
                }
            }

            when {
                start != null -> {
                    zeile("Derzeit beliebt", start.beliebt, oeffnen)
                    zeile("Neue Animes", start.neu, oeffnen)
                    if (start.genres.isNotEmpty()) {
                        item { Abschnitt("Genres") }
                        item {
                            FlowRow(
                                Modifier.padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                start.genres.forEach { g -> Pille(g.name, gewaehlt = false) { oeffnen(Ziel.GenreListe(g)) } }
                            }
                        }
                    }
                }
                z.start.fehler != null -> item { Fehlerkasten(z.start.fehler!!, Modifier.padding(top = 12.dp)) { z.laden(neu = true) } }
                else -> item { LadeKreis() }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.zeile(titel: String, karten: List<Karte>, oeffnen: (Ziel) -> Unit) {
    if (karten.isEmpty()) return
    item { Abschnitt(titel) }
    item {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(karten, key = { it.url }) { k ->
                Poster(k.titel, k.cover, Modifier.clickable { oeffnen(Ziel.Serie(k.url, k.titel, k.cover)) })
            }
        }
    }
}

@Composable
fun Poster(titel: String, cover: String?, modifier: Modifier = Modifier, breite: androidx.compose.ui.unit.Dp = 116.dp) {
    Column(modifier.width(breite)) {
        Cover(cover, Modifier.width(breite).height(breite * 1.5f))
        Spacer(Modifier.height(6.dp))
        Text(titel, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Farbe.text)
    }
}

@Composable
private fun NeueFolgeKarte(f: NeueFolge, oeffnen: (Ziel) -> Unit) {
    val serie = AniWorldParser.serienUrl(f.url) ?: return
    Column(
        Modifier
            .width(230.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Farbe.flaeche)
            .clickable { oeffnen(Ziel.Serie(serie, f.titel, null, f.staffel)) }
            .padding(12.dp),
    ) {
        Text(f.titel, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(40.dp))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("S%02d · E%02d".format(f.staffel, f.folge), color = Farbe.akzent, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
            f.sprachen.forEach { SprachMarke(it); Spacer(Modifier.size(4.dp)) }
        }
        Spacer(Modifier.height(4.dp))
        Text(f.datum, color = Farbe.text3, style = MaterialTheme.typography.labelSmall)
    }
}
