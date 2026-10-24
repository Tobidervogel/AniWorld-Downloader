package de.tobidervogel.aniworld.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import de.tobidervogel.aniworld.App
import de.tobidervogel.aniworld.Protokoll
import de.tobidervogel.aniworld.download.Auftrag
import de.tobidervogel.aniworld.download.Status
import java.io.File

/**
 * Vollbild-Player für Streams und geladene Dateien. Merkt sich die Stelle pro Folge
 * (Schlüssel = Folgen-URL) und spielt bei geladenen Folgen die nächste direkt weiter.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var ansicht: PlayerView
    private lateinit var titelText: TextView
    private var schluessel = ""
    private val takt = Handler(Looper.getMainLooper())
    private val merken = object : Runnable {
        override fun run() {
            positionSichern()
            takt.postDelayed(this, 5_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt() }
        val wurzel = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        ansicht = PlayerView(this).apply {
            setShowNextButton(false)
            setShowPreviousButton(false)
            controllerShowTimeoutMs = 2_500
        }
        wurzel.addView(ansicht, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        titelText = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(24), dp(16), dp(24), dp(16))
            maxLines = 1
        }
        wurzel.addView(titelText, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))

        // Openings dauern bei fast allen Animes 85–90 Sekunden.
        val opening = TextView(this).apply {
            text = "Opening +85 s"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(0x99202128.toInt()) }
            setOnClickListener { player?.let { p -> p.seekTo(p.currentPosition + 85_000) } }
        }
        wurzel.addView(opening, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(14), dp(20), 0)
        })
        ansicht.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
            titelText.visibility = v
            opening.visibility = v
        })
        setContentView(wurzel)

        val uri = intent.getStringExtra(EXTRA_URI) ?: return finish()
        val namen = intent.getStringArrayExtra(EXTRA_KOPF_NAMEN).orEmpty()
        val werte = intent.getStringArrayExtra(EXTRA_KOPF_WERTE).orEmpty()
        val kopf = namen.zip(werte).toMap()
        abspielen(uri, intent.getStringExtra(EXTRA_TITEL).orEmpty(), kopf, intent.getStringExtra(EXTRA_SCHLUESSEL).orEmpty())
    }

    private fun abspielen(uri: String, titel: String, kopf: Map<String, String>, schluessel: String) {
        player?.release()
        this.schluessel = schluessel
        titelText.text = titel
        val app = App.self
        val http = OkHttpDataSource.Factory(app.netz.client).setDefaultRequestProperties(kopf)
        val quelle = DefaultDataSource.Factory(this, http)
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(quelle))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player = p
        ansicht.player = p

        val ziel = if (uri.startsWith("/")) Uri.fromFile(File(uri)) else Uri.parse(uri)
        val item = MediaItem.Builder().setUri(ziel)
        if (".m3u8" in uri) item.setMimeType(MimeTypes.APPLICATION_M3U8)
        p.setMediaItem(item.build())

        app.db.position(schluessel)?.let { (ms, dauer) ->
            // Nur fortsetzen, wenn nicht ganz am Anfang oder schon fast am Ende.
            if (ms > 15_000 && (dauer <= 0 || ms < dauer - 60_000)) p.seekTo(ms)
        }
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) amEnde()
            }

            override fun onPlayerError(error: PlaybackException) {
                Protokoll.fehler("Player: ${error.errorCodeName}", error)
                Toast.makeText(this@PlayerActivity, "Wiedergabe fehlgeschlagen: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
            }
        })
        p.prepare()
        p.playWhenReady = true
    }

    private fun amEnde() {
        val p = player ?: return
        App.self.db.positionMerken(schluessel, p.duration.coerceAtLeast(0), p.duration.coerceAtLeast(0))
        val n = naechsteGeladene(schluessel)
        if (n == null) {
            finish()
            return
        }
        abspielen(n.datei!!, "${n.serie} · ${n.kuerzel}", emptyMap(), n.url)
    }

    private fun naechsteGeladene(url: String): Auftrag? {
        val db = App.self.db
        val alle = db.alle().filter { it.status == Status.FERTIG && it.datei?.let { d -> File(d).exists() } == true }
        val jetzt = alle.firstOrNull { it.url == url } ?: return null
        return alle.filter { it.serieUrl == jetzt.serieUrl && it.istFilm == jetzt.istFilm }
            .firstOrNull {
                (it.staffel == jetzt.staffel && it.folge == jetzt.folge + 1) ||
                    (it.staffel == jetzt.staffel + 1 && it.folge == 1)
            }
    }

    private fun positionSichern() {
        val p = player ?: return
        if (schluessel.isEmpty() || p.duration <= 0) return
        App.self.db.positionMerken(schluessel, p.currentPosition, p.duration)
    }

    override fun onStart() {
        super.onStart()
        takt.postDelayed(merken, 5_000)
    }

    override fun onStop() {
        takt.removeCallbacks(merken)
        positionSichern()
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URI = "uri"
        private const val EXTRA_TITEL = "titel"
        private const val EXTRA_SCHLUESSEL = "schluessel"
        private const val EXTRA_KOPF_NAMEN = "kopfNamen"
        private const val EXTRA_KOPF_WERTE = "kopfWerte"

        /** @param uri Pfad einer geladenen Datei oder eine Stream-Adresse */
        fun starten(ctx: Context, uri: String, titel: String, kopf: Map<String, String>, schluessel: String) {
            val i = Intent(ctx, PlayerActivity::class.java)
                .putExtra(EXTRA_URI, uri)
                .putExtra(EXTRA_TITEL, titel)
                .putExtra(EXTRA_SCHLUESSEL, schluessel)
                .putExtra(EXTRA_KOPF_NAMEN, kopf.keys.toTypedArray())
                .putExtra(EXTRA_KOPF_WERTE, kopf.values.toTypedArray())
            ctx.startActivity(i)
        }
    }
}
