package de.tobidervogel.aniworld.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.webkit.CookieManager
import android.widget.Toast
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tobidervogel.aniworld.App
import de.tobidervogel.aniworld.BuildConfig
import de.tobidervogel.aniworld.Einstellungen
import de.tobidervogel.aniworld.Protokoll
import de.tobidervogel.aniworld.download.Speicher
import de.tobidervogel.aniworld.site.Sprache

@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("BatteryLife")
@Composable
fun EinstellungenSeite(oeffnen: (Ziel) -> Unit) {
    val app = App.self
    val e = app.einstellungen
    val ctx = LocalContext.current
    // Die Werte liegen in SharedPreferences; der Zähler sorgt dafür, dass nach jeder Änderung neu gezeichnet wird.
    e.stand.collectAsState().value
    var ordnerDialog by remember { mutableStateOf(false) }
    val akkuFrei = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Kopfzeile("Einstellungen")

        Gruppe("Speicher") {
            Zeile("Ordner", if (Speicher.vollzugriff()) e.ordner else "Android/media (Zugriff fehlt)") { ordnerDialog = true }
            Zeile(
                "Zugriff auf alle Dateien",
                if (Speicher.vollzugriff()) "Erlaubt" else "Nicht erlaubt – tippen",
            ) {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            }
            Schalter("Zu MP4 umpacken", "HLS-Folgen als .mp4 statt .ts speichern", e.mp4) { e.mp4 = it }
        }

        Gruppe("Sprache") {
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Sprache.entries.forEach { s -> Pille(s.lang, e.sprache == s) { e.sprache = s } }
            }
            Text("Fehlt die Sprache bei einer Folge, wird diese Folge nicht in einer anderen geladen.",
                color = Farbe.text3, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        Gruppe("Hoster-Reihenfolge") {
            val liste = e.hosterReihenfolge
            liste.forEachIndexed { i, h ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}.", color = Farbe.text3, modifier = Modifier.width(28.dp))
                    Text(h, Modifier.weight(1f))
                    IconButton(enabled = i > 0, onClick = { e.hosterReihenfolge = liste.toMutableList().apply { add(i - 1, removeAt(i)) } }) {
                        Icon(Icons.Rounded.KeyboardArrowUp, "Hoch")
                    }
                    IconButton(enabled = i < liste.lastIndex, onClick = { e.hosterReihenfolge = liste.toMutableList().apply { add(i + 1, removeAt(i)) } }) {
                        Icon(Icons.Rounded.KeyboardArrowDown, "Runter")
                    }
                }
            }
            Schalter("Browser als Ausweichweg", "Klappt kein Extraktor, öffnet die App den Hoster unsichtbar und liest die Videoadresse mit", e.browserWeg) { e.browserWeg = it }
        }

        Gruppe("Player") {
            Schalter("AniSkip", "Opening und Ending erkennen und einen Überspringen-Knopf zeigen (Daten von aniskip.com)", e.aniskip) { e.aniskip = it }
            Schalter("Automatisch überspringen", "Statt Knopf direkt springen, einmal pro Folge", e.aniskipAuto) { e.aniskipAuto = it }
        }

        Gruppe("Laden") {
            Text("Folgen gleichzeitig", Modifier.padding(start = 16.dp, top = 8.dp))
            Row(Modifier.padding(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..3).forEach { n -> Pille("$n", e.parallel == n) { e.parallel = n } }
            }
            Text("Teile pro Folge gleichzeitig", Modifier.padding(start = 16.dp, top = 4.dp))
            Row(Modifier.padding(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(4, 8, 12, 16).forEach { n -> Pille("$n", e.segmente == n) { e.segmente = n } }
            }
            Schalter("Nur im WLAN", "Neue Folgen erst starten, wenn das Netz nicht getaktet ist", e.nurWlan) { e.nurWlan = it }
            Zeile("Akku-Optimierung", if (akkuFrei) "Aus – Downloads laufen im Hintergrund weiter" else "An – OxygenOS kann Downloads stoppen. Tippen") {
                if (!akkuFrei) ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
            }
        }

        Gruppe("Netz") {
            Schalter("DNS über HTTPS", "Umgeht die DNS-Sperre deutscher Provider (Cloudflare 1.1.1.1)", e.doh) { e.doh = it }
            Zeile("Cookies löschen", "Hilft, wenn eine Seite hängen bleibt") {
                CookieManager.getInstance().removeAllCookies(null)
                Toast.makeText(ctx, "Cookies gelöscht", Toast.LENGTH_SHORT).show()
            }
        }

        Gruppe("Hilfe") {
            Zeile("Protokoll", "Fehler ansehen und kopieren") { oeffnen(Ziel.ProtokollAnsicht) }
            Zeile("Version", "${BuildConfig.VERSION_NAME} · nur fürs Nord 3 gebaut") {}
        }
    }

    if (ordnerDialog) {
        var text by remember { mutableStateOf(e.ordner) }
        AlertDialog(
            onDismissRequest = { ordnerDialog = false },
            title = { Text("Ordner für Downloads") },
            text = {
                Column {
                    OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { text = Einstellungen.STANDARD_ORDNER }) { Text("Standard") }
                }
            },
            confirmButton = { TextButton(onClick = { e.ordner = text; ordnerDialog = false }) { Text("Speichern") } },
            dismissButton = { TextButton(onClick = { ordnerDialog = false }) { Text("Abbrechen") } },
            containerColor = Farbe.flaeche,
        )
    }
}

@Composable
private fun Gruppe(titel: String, inhalt: @Composable () -> Unit) {
    Text(titel.uppercase(), color = Farbe.akzent, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 6.dp))
    Column(Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(16.dp)).background(Farbe.flaeche).padding(vertical = 6.dp)) { inhalt() }
}

@Composable
private fun Zeile(titel: String, wert: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titel)
            Text(wert, color = Farbe.text2, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Farbe.text3)
    }
}

@Composable
private fun Schalter(titel: String, text: String, an: Boolean, aendern: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { aendern(!an) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titel)
            Text(text, color = Farbe.text2, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Switch(an, aendern, colors = SwitchDefaults.colors(checkedTrackColor = Farbe.akzent, checkedThumbColor = Farbe.hintergrund))
    }
}

@Composable
fun ProtokollSeite(zurueck: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(Protokoll.lesen()) }
    val scroll = rememberScrollState()
    LaunchedEffect(text) { scroll.scrollTo(scroll.maxValue) }
    Column(Modifier.fillMaxSize()) {
        Kopfzeile("Protokoll", zurueck) {
            TextButton(onClick = {
                ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("AniWorld-Protokoll", text.takeLast(20_000)))
                Toast.makeText(ctx, "Kopiert", Toast.LENGTH_SHORT).show()
            }) { Text("Kopieren") }
            TextButton(onClick = { Protokoll.leeren(); text = "" }) { Text("Leeren") }
        }
        SelectionContainer(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).horizontalScroll(rememberScrollState())) {
            Text(
                text.ifEmpty { "Noch nichts protokolliert." },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Farbe.text2,
                modifier = Modifier.padding(12.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
