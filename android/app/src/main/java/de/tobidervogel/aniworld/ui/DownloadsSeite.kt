package de.tobidervogel.aniworld.ui

import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.tobidervogel.aniworld.App
import de.tobidervogel.aniworld.download.Auftrag
import de.tobidervogel.aniworld.download.DownloadService
import de.tobidervogel.aniworld.download.Speicher
import de.tobidervogel.aniworld.download.Status
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadsSeite(oeffnen: (Ziel) -> Unit) {
    val app = App.self
    val ctx = LocalContext.current
    val stand by app.db.stand.collectAsState()
    val live by DownloadService.live.collectAsState()
    val alle = remember(stand) { app.db.alle() }
    val offen = alle.filter { it.status != Status.FERTIG }
        .sortedWith(compareBy({ reihe(it.status) }, { it.erstellt }, { it.staffel }, { it.folge }))
    val fertig = alle.filter { it.status == Status.FERTIG }
    var reiter by rememberSaveable { mutableIntStateOf(if (offen.isEmpty() && fertig.isNotEmpty()) 1 else 0) }
    val aufgeklappt = remember { mutableStateMapOf<String, Boolean>() }
    var loeschen by remember { mutableStateOf<Auftrag?>(null) }

    Column(Modifier.fillMaxSize()) {
        Kopfzeile("Downloads")
        if (!Speicher.vollzugriff()) {
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Farbe.akzentFlaeche).padding(14.dp),
            ) {
                Text("Speicherzugriff fehlt", fontWeight = FontWeight.SemiBold, color = Farbe.akzent)
                Text(
                    "Ohne „Zugriff auf alle Dateien“ landen die Folgen in Android/media statt in ${app.einstellungen.ordner}.",
                    color = Farbe.text2, style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                }) { Text("Erlauben") }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pille("Laufend · ${offen.size}", reiter == 0) { reiter = 0 }
            Pille("Geladen · ${fertig.size}", reiter == 1) { reiter = 1 }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (reiter == 0) {
                if (offen.isEmpty()) {
                    item { Leer("Nichts in der Warteschlange. Auf einer Serie ⤓ antippen oder eine ganze Staffel laden.") }
                }
                if (offen.any { it.status == Status.FEHLER }) {
                    item {
                        TextButton(onClick = { app.db.fehlerNeuStarten(); DownloadService.starten(app) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Alle mit Fehler nochmal")
                        }
                    }
                }
                items(offen, key = { it.id }) { a -> OffenZeile(a, live[a.id]) }
            } else {
                if (fertig.isEmpty()) item { Leer("Noch keine fertigen Folgen.") }
                val gruppen = fertig.groupBy { it.serieUrl }
                gruppen.forEach { (url, folgen) ->
                    val erste = folgen.first()
                    val auf = aufgeklappt[url] ?: (gruppen.size == 1)
                    item(key = "kopf-$url") {
                        Row(
                            Modifier.fillMaxWidth().clickable { aufgeklappt[url] = !auf }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Cover(erste.cover, Modifier.width(46.dp).height(68.dp).combinedClickable(
                                onClick = { aufgeklappt[url] = !auf },
                                onLongClick = { oeffnen(Ziel.Serie(url, erste.serie, erste.cover)) },
                            ), ecken = 8.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(erste.serie, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${folgen.size} Folgen · ${DownloadService.groesse(folgen.sumOf { it.bytes })}", color = Farbe.text2, style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(if (auf) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Farbe.text2)
                        }
                    }
                    if (auf) {
                        items(folgen.sortedWith(compareBy({ it.istFilm }, { it.staffel }, { it.folge })), key = { it.id }) { a ->
                            FertigZeile(a, abspielen = {
                                PlayerActivity.starten(ctx, a.datei!!, "${a.serie} · ${a.kuerzel}", emptyMap(), a.url)
                            }, loeschen = { loeschen = a })
                        }
                    }
                }
            }
        }
    }

    loeschen?.let { a ->
        AlertDialog(
            onDismissRequest = { loeschen = null },
            title = { Text("${a.kuerzel} löschen?") },
            text = { Text("Die Datei wird vom Handy gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    a.datei?.let { d ->
                        File(d).delete()
                        MediaScannerConnection.scanFile(ctx, arrayOf(d), null, null)
                    }
                    app.db.loeschen(a.id)
                    loeschen = null
                }) { Text("Löschen", color = Farbe.fehler) }
            },
            dismissButton = { TextButton(onClick = { loeschen = null }) { Text("Abbrechen") } },
            containerColor = Farbe.flaeche,
        )
    }
}

private fun reihe(s: Status) = when (s) {
    Status.LAEUFT -> 0
    Status.WARTET -> 1
    Status.PAUSE -> 2
    Status.FEHLER -> 3
    Status.FERTIG -> 4
}

@Composable
private fun Leer(text: String) {
    Text(text, color = Farbe.text2, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(20.dp))
}

@Composable
private fun OffenZeile(a: Auftrag, l: de.tobidervogel.aniworld.download.Live?) {
    val app = App.self
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(a.cover, Modifier.width(40.dp).height(60.dp), ecken = 6.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.serie, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${a.kuerzel} · ${a.sprache.kurz}" + (a.titel.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                color = Farbe.text2, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            when (a.status) {
                Status.LAEUFT -> {
                    val anteil = l?.anteil
                    if (anteil != null) {
                        LinearProgressIndicator(progress = { anteil }, color = Farbe.akzent, trackColor = Farbe.flaeche2,
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape))
                    } else {
                        LinearProgressIndicator(color = Farbe.akzent, trackColor = Farbe.flaeche2, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape))
                    }
                    Spacer(Modifier.height(4.dp))
                    val details = listOfNotNull(
                        l?.text,
                        l?.bytes?.takeIf { it > 0 }?.let { DownloadService.groesse(it) },
                        l?.tempo?.takeIf { it > 0 }?.let { DownloadService.groesse(it) + "/s" },
                    )
                    Text(details.joinToString(" · ").ifEmpty { "Startet …" }, color = Farbe.text3, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                }
                Status.WARTET -> Text("Wartet", color = Farbe.text3, style = MaterialTheme.typography.labelSmall)
                Status.PAUSE -> Text("Pausiert" + (a.bytes.takeIf { it > 0 }?.let { " · ${DownloadService.groesse(it)}" } ?: ""), color = Farbe.text3, style = MaterialTheme.typography.labelSmall)
                Status.FEHLER -> Text(a.fehler ?: "Fehler", color = Farbe.fehler, style = MaterialTheme.typography.labelSmall, maxLines = 4)
                Status.FERTIG -> Unit
            }
        }
        when (a.status) {
            Status.LAEUFT, Status.WARTET -> IconButton(onClick = { DownloadService.pausieren(app, a.id) }) { Icon(Icons.Rounded.Pause, "Pause", tint = Farbe.text2) }
            Status.PAUSE, Status.FEHLER -> IconButton(onClick = { DownloadService.fortsetzen(app, a.id) }) {
                Icon(if (a.status == Status.FEHLER) Icons.Rounded.Refresh else Icons.Rounded.PlayArrow, "Weiter", tint = Farbe.text2)
            }
            Status.FERTIG -> Unit
        }
        IconButton(onClick = { DownloadService.entfernen(app, a.id) }) { Icon(Icons.Rounded.Close, "Entfernen", tint = Farbe.text3) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FertigZeile(a: Auftrag, abspielen: () -> Unit, loeschen: () -> Unit) {
    val da = a.datei?.let { File(it).exists() } == true
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(enabled = true, onClick = { if (da) abspielen() }, onLongClick = loeschen)
            .padding(start = 74.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${a.kuerzel} · ${a.sprache.kurz}", style = MaterialTheme.typography.titleSmall)
            Text(
                if (da) listOfNotNull(a.titel.takeIf { it.isNotBlank() }, DownloadService.groesse(a.bytes)).joinToString(" · ") else "Datei fehlt",
                color = if (da) Farbe.text2 else Farbe.fehler, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (da) Icon(Icons.Rounded.PlayArrow, "Abspielen", tint = Farbe.akzent, modifier = Modifier.size(26.dp))
    }
}
