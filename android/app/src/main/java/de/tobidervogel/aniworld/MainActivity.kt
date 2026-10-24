package de.tobidervogel.aniworld

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import de.tobidervogel.aniworld.download.DownloadService
import de.tobidervogel.aniworld.download.Status
import de.tobidervogel.aniworld.net.BrowserCheck
import de.tobidervogel.aniworld.ui.BrowserFenster
import de.tobidervogel.aniworld.ui.DownloadsSeite
import de.tobidervogel.aniworld.ui.EinstellungenSeite
import de.tobidervogel.aniworld.ui.Farbe
import de.tobidervogel.aniworld.ui.GenreSeite
import de.tobidervogel.aniworld.ui.ProtokollSeite
import de.tobidervogel.aniworld.ui.SerieSeite
import de.tobidervogel.aniworld.ui.StartSeite
import de.tobidervogel.aniworld.ui.StartZustand
import de.tobidervogel.aniworld.ui.SucheSeite
import de.tobidervogel.aniworld.ui.Thema
import de.tobidervogel.aniworld.ui.Ziel

class MainActivity : ComponentActivity() {
    private var tab by mutableStateOf(TAB_START)
    private val stapel = mutableStateListOf<Ziel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val start = StartZustand(lifecycleScope, App.self)
        verarbeiten(intent)
        // Nach einem Neustart des Handys oder einem Absturz liegen evtl. noch wartende Folgen da.
        if (App.self.db.alle().any { it.status == Status.WARTET || it.status == Status.LAEUFT }) DownloadService.starten(this)
        setContent { Thema { Oberflaeche(start) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        verarbeiten(intent)
    }

    override fun onStart() {
        super.onStart()
        BrowserCheck.appSichtbar = true
    }

    override fun onStop() {
        BrowserCheck.appSichtbar = false
        super.onStop()
    }

    /** Benachrichtigung (Reiter) oder geteilter/geöffneter aniworld-Link. */
    private fun verarbeiten(i: Intent?) {
        i ?: return
        i.getStringExtra(EXTRA_TAB)?.let { tab = it; stapel.clear() }
        val text = when (i.action) {
            Intent.ACTION_VIEW -> i.dataString
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return
        val link = Regex("""https?://(?:www\.)?(?:aniworld\.to/anime/stream|serienstream\.(?:to|cx)/serie|s\.to/serie)/[^\s"']+""")
            .find(text)?.value ?: return
        val serie = App.self.anbieter(link).serienUrl(link) ?: return
        val staffel = Regex("staffel-(\\d+)").find(link)?.groupValues?.get(1)?.toIntOrNull()
        tab = TAB_START
        stapel.clear()
        stapel += Ziel.Serie(serie, serie.substringAfterLast('/').replace('-', ' '), null, staffel)
    }

    @Composable
    private fun Oberflaeche(start: StartZustand) {
        val app = App.self
        val oeffnen: (Ziel) -> Unit = { stapel += it }
        val zurueck: () -> Unit = { if (stapel.isNotEmpty()) stapel.removeAt(stapel.lastIndex) }
        BackHandler(enabled = stapel.isNotEmpty()) { zurueck() }
        val stand by app.db.stand.collectAsState()
        val live by DownloadService.live.collectAsState()
        val offen = remember(stand) { app.db.alle().count { it.status == Status.WARTET || it.status == Status.LAEUFT } }

        Scaffold(
            containerColor = Farbe.hintergrund,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                NavigationBar(containerColor = Farbe.flaeche, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
                    val farben = NavigationBarItemDefaults.colors(
                        selectedIconColor = Farbe.akzent,
                        selectedTextColor = Farbe.akzent,
                        indicatorColor = Farbe.akzentFlaeche,
                        unselectedIconColor = Farbe.text2,
                        unselectedTextColor = Farbe.text2,
                    )
                    fun waehlen(t: String) {
                        if (tab == t) stapel.clear() else { tab = t; stapel.clear() }
                    }
                    NavigationBarItem(tab == TAB_START, { waehlen(TAB_START) }, { Icon(Icons.Rounded.Explore, null) }, label = { Text("Entdecken") }, colors = farben)
                    NavigationBarItem(tab == TAB_DOWNLOADS, { waehlen(TAB_DOWNLOADS) }, {
                        BadgedBox(badge = { if (offen > 0 || live.isNotEmpty()) Badge(containerColor = Farbe.akzent) { Text("${maxOf(offen, live.size)}") } }) {
                            Icon(Icons.Rounded.Download, null)
                        }
                    }, label = { Text("Downloads") }, colors = farben)
                    NavigationBarItem(tab == TAB_EINSTELLUNGEN, { waehlen(TAB_EINSTELLUNGEN) }, { Icon(Icons.Rounded.Settings, null) }, label = { Text("Einstellungen") }, colors = farben)
                }
            },
        ) { innen ->
            Box(Modifier.fillMaxSize().padding(innen)) {
                when (val z = stapel.lastOrNull()) {
                    null -> when (tab) {
                        TAB_DOWNLOADS -> DownloadsSeite(oeffnen)
                        TAB_EINSTELLUNGEN -> EinstellungenSeite(oeffnen)
                        else -> StartSeite(start, oeffnen)
                    }
                    is Ziel.Serie -> SerieSeite(z, zurueck)
                    is Ziel.GenreListe -> GenreSeite(z, zurueck, oeffnen)
                    Ziel.Suche -> SucheSeite(zurueck, oeffnen)
                    Ziel.ProtokollAnsicht -> ProtokollSeite(zurueck)
                }
            }
        }
        BrowserFenster()
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_START = "start"
        const val TAB_DOWNLOADS = "downloads"
        const val TAB_EINSTELLUNGEN = "einstellungen"
    }
}
