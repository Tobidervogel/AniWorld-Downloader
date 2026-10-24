package de.tobidervogel.aniworld.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.tobidervogel.aniworld.App
import de.tobidervogel.aniworld.net.BrowserCheck
import de.tobidervogel.aniworld.net.Check
import de.tobidervogel.aniworld.net.Netz
import kotlinx.coroutines.delay
import org.json.JSONArray

/**
 * Sichtbares Browserfenster für Schutzseiten. Erst wenn die Seite echten Inhalt zeigt
 * (oder beim Redirect aniworld.to verlässt), schließt es sich und die App macht weiter.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserFenster() {
    val anfrage by BrowserCheck.offen.collectAsState()
    val a = anfrage ?: return
    var web by remember(a) { mutableStateOf<WebView?>(null) }

    fun fertig(url: String?) {
        CookieManager.getInstance().flush()
        a.ergebnis.complete(url)
    }

    Dialog(
        onDismissRequest = { a.ergebnis.complete(null) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(Farbe.hintergrund).navigationBarsPadding()) {
            Kopfzeile("Browser-Check", zurueck = { a.ergebnis.complete(null) })
            Text(
                if (a.verlassen) "Vor dem Hoster prüft ${Netz.host(a.url)} kurz den Browser. Kommt ein Rätsel, löse es; danach geht es von allein weiter."
                else "${Netz.host(a.url)} prüft gerade den Browser. Meist dauert das nur ein paar Sekunden; kommt ein Rätsel, löse es kurz.",
                color = Farbe.text2,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Gleicher User-Agent wie OkHttp: Die Freigabe-Cookies hängen am Browser.
                        settings.userAgentString = App.self.netz.userAgent
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            private fun verlassen(url: String): Boolean {
                                if (a.verlassen && Netz.host(url).removePrefix("www.") !in a.heim && url.startsWith("http")) {
                                    fertig(url)
                                    return true
                                }
                                return false
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean =
                                request.isForMainFrame && verlassen(request.url.toString())

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                if (url != null && verlassen(url)) view?.stopLoading()
                            }
                        }
                        loadUrl(a.url)
                        web = this
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }

    // Regelmäßig nachsehen, ob die Schutzseite weg ist.
    LaunchedEffect(a, web) {
        val w = web ?: return@LaunchedEffect
        if (a.verlassen) return@LaunchedEffect
        delay(1_500)
        while (!a.ergebnis.isCompleted) {
            w.evaluateJavascript("document.documentElement.outerHTML.substring(0, 6000)") { r ->
                val html = runCatching { JSONArray("[$r]").getString(0) }.getOrNull().orEmpty()
                val url = w.url.orEmpty()
                if (html.length > 200 && !Check.istSchutzseite(html) && Netz.host(url) == Netz.host(a.url)) fertig(url)
            }
            delay(1_500)
        }
    }

    DisposableEffect(a) {
        onDispose {
            web?.stopLoading()
            web?.destroy()
        }
    }
}
