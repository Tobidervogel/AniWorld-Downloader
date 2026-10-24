package de.tobidervogel.aniworld.ui

import android.widget.Toast
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.tobidervogel.aniworld.App
import de.tobidervogel.aniworld.download.Auftrag
import de.tobidervogel.aniworld.download.Benennung
import de.tobidervogel.aniworld.download.DownloadService
import de.tobidervogel.aniworld.download.Live
import de.tobidervogel.aniworld.download.Status
import de.tobidervogel.aniworld.hoster.Aufloeser
import de.tobidervogel.aniworld.site.Folge
import de.tobidervogel.aniworld.site.FolgenLinks
import de.tobidervogel.aniworld.site.Serie
import de.tobidervogel.aniworld.site.Sprache
import de.tobidervogel.aniworld.site.StaffelRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SerieSeite(ziel: Ziel.Serie, zurueck: () -> Unit) {
    val app = App.self
    val scope = rememberCoroutineScope()
    var serie by remember(ziel.url) { mutableStateOf<Serie?>(null) }
    var fehler by remember(ziel.url) { mutableStateOf<String?>(null) }
    var staffelNr by rememberSaveable(ziel.url) { mutableIntStateOf(-1) }
    val folgen = remember(ziel.url) { mutableStateMapOf<String, List<Folge>>() }
    val folgenFehler = remember(ziel.url) { mutableStateMapOf<String, String>() }
    var gewaehlteFolge by remember { mutableStateOf<Folge?>(null) }
    var staffelLaden by remember { mutableStateOf<List<StaffelRef>?>(null) }
    var beschreibungOffen by remember { mutableStateOf(false) }

    fun serieLaden(neu: Boolean = false) {
        fehler = null
        scope.launch {
            try {
                val s = app.aniworld.serie(ziel.url, neu)
                serie = s
                app.db.merken(s.url, s.titel, s.cover ?: ziel.cover)
                if (staffelNr == -1 || s.staffeln.none { it.nummer == staffelNr }) {
                    staffelNr = (s.staffeln.firstOrNull { it.nummer == ziel.staffel } ?: s.staffeln.firstOrNull { !it.istFilme }
                        ?: s.staffeln.firstOrNull())?.nummer ?: 1
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fehler = e.message
            }
        }
    }
    LaunchedEffect(ziel.url) { if (serie == null) serieLaden() }

    val s = serie
    val ref = s?.staffeln?.firstOrNull { it.nummer == staffelNr }
    LaunchedEffect(ref?.url) {
        val r = ref ?: return@LaunchedEffect
        if (folgen[r.url] != null) return@LaunchedEffect
        folgenFehler.remove(r.url)
        try {
            folgen[r.url] = app.aniworld.staffel(r)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            folgenFehler[r.url] = e.message ?: "Fehler"
        }
    }

    val stand by app.db.stand.collectAsState()
    val live by DownloadService.live.collectAsState()
    val auftraege = remember(stand, ziel.url) { app.db.fuerSerie(ziel.url).groupBy { it.url } }
    val liste = ref?.let { folgen[it.url] }
    val positionen = remember(stand, liste) { app.db.positionen(liste.orEmpty().map { it.url }) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { Kopf(s, ziel, zurueck) }
        if (s == null) {
            item { if (fehler != null) Fehlerkasten(fehler!!) { serieLaden(true) } else LadeKreis() }
            return@LazyColumn
        }
        if (s.beschreibung.isNotBlank()) {
            item {
                Text(
                    s.beschreibung,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Farbe.text2,
                    maxLines = if (beschreibungOffen) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp).clickable { beschreibungOffen = !beschreibungOffen },
                )
            }
        }
        if (s.genres.isNotEmpty()) {
            item {
                FlowRow(Modifier.padding(16.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.genres.take(8).forEach { g ->
                        Text(g, style = MaterialTheme.typography.labelMedium, color = Farbe.text2,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Farbe.flaeche).padding(8.dp, 4.dp))
                    }
                }
            }
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(s.staffeln, key = { it.url }) { st -> Pille(st.name, gewaehlt = st.nummer == staffelNr) { staffelNr = st.nummer } }
            }
        }
        if (ref != null) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { staffelLaden = listOf(ref) }, enabled = liste != null) {
                        Icon(Icons.Rounded.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(if (ref.istFilme) "Alle Filme laden" else "${ref.name} laden")
                    }
                    if (s.staffeln.size > 1) {
                        OutlinedButton(onClick = { staffelLaden = s.staffeln }) { Text("Alles laden") }
                    }
                }
            }
        }
        when {
            ref == null -> item { Text("Keine Staffeln auf der Seite gefunden.", color = Farbe.text2, modifier = Modifier.padding(16.dp)) }
            liste != null -> {
                itemsIndexed(liste, key = { _, f -> f.url }) { _, f ->
                    FolgenZeile(s, f, auftraege[f.url].orEmpty(), live, positionen[f.url]) { gewaehlteFolge = f }
                }
                if (liste.isEmpty()) item { Text("Keine Folgen gefunden.", color = Farbe.text2, modifier = Modifier.padding(16.dp)) }
            }
            folgenFehler[ref.url] != null -> item {
                Fehlerkasten(folgenFehler[ref.url]!!) {
                    folgenFehler.remove(ref.url)
                    scope.launch {
                        try { folgen[ref.url] = app.aniworld.staffel(ref, neu = true) } catch (e: Exception) { folgenFehler[ref.url] = e.message ?: "Fehler" }
                    }
                }
            }
            else -> item { LadeKreis() }
        }
    }

    gewaehlteFolge?.let { f ->
        if (s != null) FolgenBlatt(s, f, auftraege[f.url].orEmpty()) { gewaehlteFolge = null }
    }
    staffelLaden?.let { refs ->
        if (s != null) StaffelBlatt(s, refs, folgen) { staffelLaden = null }
    }
}

@Composable
private fun Kopf(s: Serie?, ziel: Ziel.Serie, zurueck: () -> Unit) {
    val cover = s?.cover ?: ziel.cover
    Box(Modifier.fillMaxWidth()) {
        // Unscharfes Cover als Hintergrund, nach unten in den Hintergrund ausgeblendet.
        Cover(cover, Modifier.matchParentSize().blur(28.dp), ecken = 0.dp)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Farbe.hintergrund.copy(alpha = 0.55f), Farbe.hintergrund))))
        Column(Modifier.statusBarsPadding()) {
            IconButton(onClick = zurueck, modifier = Modifier.padding(4.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") }
            Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.Bottom) {
                Cover(cover, Modifier.width(118.dp).height(177.dp), ecken = 12.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(s?.titel ?: ziel.titel, style = MaterialTheme.typography.headlineSmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    val details = listOfNotNull(
                        s?.jahre,
                        s?.fsk?.let { "FSK $it" },
                        s?.staffeln?.count { !it.istFilme }?.takeIf { it > 0 }?.let { if (it == 1) "1 Staffel" else "$it Staffeln" },
                    )
                    if (details.isNotEmpty()) Text(details.joinToString(" · "), color = Farbe.text2, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun FolgenZeile(s: Serie, f: Folge, auftraege: List<Auftrag>, live: Map<Long, Live>, gesehen: Float?, onClick: () -> Unit) {
    val app = App.self
    val ctx = LocalContext.current
    val fertig = auftraege.firstOrNull { it.status == Status.FERTIG && it.datei?.let { d -> File(d).exists() } == true }
    val laufend = auftraege.firstOrNull { it.status == Status.LAEUFT }
    val wartet = auftraege.firstOrNull { it.status == Status.WARTET }
    val pause = auftraege.firstOrNull { it.status == Status.PAUSE }
    val fehler = auftraege.firstOrNull { it.status == Status.FEHLER }

    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Farbe.flaeche), contentAlignment = Alignment.Center) {
                Text("${f.nummer}", style = MaterialTheme.typography.titleMedium, color = if (fertig != null) Farbe.akzent else Farbe.text)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(f.titel.ifBlank { f.kuerzel }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (f.titelEn.isNotBlank() && f.titelEn != f.titel) {
                    Text(f.titelEn, style = MaterialTheme.typography.bodySmall, color = Farbe.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { f.sprachen.forEach { SprachMarke(it) } }
            }
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                when {
                    fertig != null -> Icon(Icons.Rounded.CheckCircle, "Geladen", tint = Farbe.gut)
                    laufend != null -> {
                        val l = live[laufend.id]
                        if (l?.anteil != null) CircularProgressIndicator(progress = { l.anteil }, color = Farbe.akzent, trackColor = Farbe.flaeche2, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                        else CircularProgressIndicator(color = Farbe.akzent, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                    }
                    wartet != null -> Icon(Icons.Rounded.Schedule, "Wartet", tint = Farbe.text2)
                    pause != null -> Icon(Icons.Rounded.PauseCircle, "Pausiert", tint = Farbe.text2)
                    else -> IconButton(onClick = {
                        // Schnell laden: Standardsprache, sonst die erste vorhandene. Für alles andere das Blatt.
                        val sprache = app.einstellungen.sprache.takeIf { it in f.sprachen } ?: f.sprachen.firstOrNull()
                        if (sprache == null) onClick() else {
                            val n = einreihen(listOf(f), sprache, null, s)
                            Toast.makeText(ctx, if (n > 0) "${f.kuerzel} (${sprache.kurz}) eingereiht" else "Schon in der Liste", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(if (fehler != null) Icons.Rounded.ErrorOutline else Icons.Rounded.Download, "Laden", tint = if (fehler != null) Farbe.fehler else Farbe.text2)
                    }
                }
            }
        }
        if (gesehen != null && gesehen > 0.01f) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { gesehen },
                color = Farbe.akzent,
                trackColor = Farbe.flaeche2,
                modifier = Modifier.padding(start = 54.dp, end = 10.dp).fillMaxWidth().height(3.dp).clip(CircleShape),
            )
        }
    }
}

/** Reiht Folgen ein und startet den Dienst. */
private fun einreihen(folgen: List<Folge>, sprache: Sprache, hoster: String?, s: Serie): Int {
    val app = App.self
    val gruppen = folgen.groupBy { it.staffel }
    val liste = folgen.map { f ->
        val inStaffel = gruppen[f.staffel].orEmpty()
        Auftrag(
            serie = s.titel, serieUrl = s.url, cover = s.cover,
            staffel = f.staffel, folge = f.nummer, istFilm = f.istFilm,
            titel = f.titel, url = f.url, sprache = sprache, hoster = hoster,
            stellen = Benennung.stellen(inStaffel.size, inStaffel.maxOfOrNull { it.nummer } ?: 0),
        )
    }
    val n = app.db.einreihen(liste)
    DownloadService.starten(app)
    return n
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FolgenBlatt(s: Serie, f: Folge, auftraege: List<Auftrag>, schliessen: () -> Unit) {
    val app = App.self
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val zustand = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var links by remember(f.url) { mutableStateOf<FolgenLinks?>(null) }
    var fehler by remember(f.url) { mutableStateOf<String?>(null) }
    var status by remember(f.url) { mutableStateOf<String?>(null) }
    val vorhanden = links?.sprachen ?: f.sprachen
    var sprache by remember(f.url) {
        mutableStateOf(app.einstellungen.sprache.takeIf { it in f.sprachen } ?: f.sprachen.firstOrNull() ?: app.einstellungen.sprache)
    }
    var hoster by remember(f.url) { mutableStateOf<String?>(null) }
    val datei = auftraege.firstOrNull { it.status == Status.FERTIG }?.datei?.takeIf { File(it).exists() }

    LaunchedEffect(f.url) {
        try {
            links = app.aniworld.links(f.url)
            if (sprache !in links!!.sprachen) links!!.sprachen.firstOrNull()?.let { sprache = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fehler = e.message
        }
    }

    ModalBottomSheet(onDismissRequest = schliessen, sheetState = zustand, containerColor = Farbe.flaeche) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(f.kuerzel, color = Farbe.akzent, style = MaterialTheme.typography.labelLarge)
            Text(f.titel.ifBlank { f.kuerzel }, style = MaterialTheme.typography.titleLarge)
            if (f.titelEn.isNotBlank() && f.titelEn != f.titel) Text(f.titelEn, color = Farbe.text2, style = MaterialTheme.typography.bodyMedium)

            if (datei != null) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        PlayerActivity.starten(ctx, datei, "${s.titel} · ${f.kuerzel}", emptyMap(), f.url)
                        schliessen()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Farbe.akzent),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Geladene Datei abspielen", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Sprache", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Sprache.entries.filter { it in vorhanden || it in f.sprachen }.forEach { sp ->
                    Pille(sp.lang, gewaehlt = sp == sprache, aktiv = links == null || sp in vorhanden) { sprache = sp; hoster = null }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Hoster", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            when {
                fehler != null -> Text(fehler!!, color = Farbe.fehler, style = MaterialTheme.typography.bodySmall)
                links == null -> LadeKreis(Modifier.height(48.dp))
                else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pille("Automatisch", gewaehlt = hoster == null) { hoster = null }
                    links!!.hoster(sprache).forEach { h -> Pille(h, gewaehlt = hoster == h) { hoster = h } }
                }
            }

            status?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Farbe.text2, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        val l = links ?: return@OutlinedButton
                        scope.launch {
                            try {
                                val st = Aufloeser.stream(f.url, l, sprache, hoster) { status = it }
                                status = null
                                PlayerActivity.starten(ctx, st.url, "${s.titel} · ${f.kuerzel}", st.header, f.url)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                status = e.message
                            }
                        }
                    },
                    enabled = links != null && sprache in vorhanden,
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Streamen") }
                Button(
                    onClick = {
                        val n = einreihen(listOf(f), sprache, hoster, s)
                        Toast.makeText(ctx, if (n > 0) "Eingereiht" else "Schon in der Liste", Toast.LENGTH_SHORT).show()
                        schliessen()
                    },
                    enabled = sprache in vorhanden,
                    colors = ButtonDefaults.buttonColors(containerColor = Farbe.akzent),
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(4.dp)); Text("Laden", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StaffelBlatt(s: Serie, refs: List<StaffelRef>, cache: MutableMap<String, List<Folge>>, schliessen: () -> Unit) {
    val app = App.self
    val ctx = LocalContext.current
    val zustand = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var folgen by remember(refs) { mutableStateOf<List<Folge>?>(null) }
    var fehler by remember(refs) { mutableStateOf<String?>(null) }
    var ohneFilme by remember { mutableStateOf(true) }

    LaunchedEffect(refs) {
        try {
            val alle = ArrayList<Folge>()
            for (r in refs) {
                val l = cache[r.url] ?: app.aniworld.staffel(r).also { cache[r.url] = it }
                alle += l
            }
            folgen = alle
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fehler = e.message
        }
    }

    val auswahl = folgen?.filter { !(refs.size > 1 && ohneFilme && it.istFilm) }.orEmpty()
    val zaehler = Sprache.entries.associateWith { sp -> auswahl.count { sp in it.sprachen } }
    var sprache by remember(folgen) {
        mutableStateOf(app.einstellungen.sprache.takeIf { (zaehler[it] ?: 0) > 0 } ?: zaehler.maxByOrNull { it.value }?.key ?: app.einstellungen.sprache)
    }
    val passend = auswahl.filter { sprache in it.sprachen }

    ModalBottomSheet(onDismissRequest = schliessen, sheetState = zustand, containerColor = Farbe.flaeche) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(if (refs.size > 1) "Ganze Serie laden" else refs.first().name + " laden", style = MaterialTheme.typography.titleLarge)
            Text(s.titel, color = Farbe.text2, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            when {
                fehler != null -> Fehlerkasten(fehler!!)
                folgen == null -> LadeKreis()
                else -> {
                    Text("Sprache", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Sprache.entries.filter { (zaehler[it] ?: 0) > 0 }.forEach { sp ->
                            Pille("${sp.kurz} · ${zaehler[sp]}/${auswahl.size}", gewaehlt = sp == sprache) { sprache = sp }
                        }
                    }
                    if (refs.size > 1 && refs.any { it.istFilme }) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Filme weglassen", Modifier.weight(1f))
                            Switch(ohneFilme, { ohneFilme = it }, colors = SwitchDefaults.colors(checkedTrackColor = Farbe.akzent))
                        }
                    }
                    if (passend.size < auswahl.size) {
                        Spacer(Modifier.height(10.dp))
                        Text("${auswahl.size - passend.size} Folgen gibt es nicht auf ${sprache.lang}; die bleiben weg.",
                            color = Farbe.text2, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            val n = einreihen(passend, sprache, null, s)
                            Toast.makeText(ctx, "$n Folgen eingereiht", Toast.LENGTH_SHORT).show()
                            schliessen()
                        },
                        enabled = passend.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Farbe.akzent),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("${passend.size} Folgen laden", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
