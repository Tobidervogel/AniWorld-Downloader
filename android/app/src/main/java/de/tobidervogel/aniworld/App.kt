package de.tobidervogel.aniworld

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.webkit.CookieManager
import androidx.core.app.NotificationCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import de.tobidervogel.aniworld.download.Datenbank
import de.tobidervogel.aniworld.net.Netz
import de.tobidervogel.aniworld.site.Anbieter
import de.tobidervogel.aniworld.site.AniWorld
import de.tobidervogel.aniworld.site.SerienStream

class App : Application(), ImageLoaderFactory {
    lateinit var einstellungen: Einstellungen
    lateinit var netz: Netz
    lateinit var aniworld: AniWorld
    lateinit var serienstream: SerienStream
    lateinit var db: Datenbank

    val anbieter: List<Anbieter> get() = listOf(aniworld, serienstream)

    /** Zuständige Seite für eine Adresse; unbekannte Adressen gehen an AniWorld. */
    fun anbieter(url: String): Anbieter = anbieter.firstOrNull { it.passt(url) } ?: aniworld

    override fun onCreate() {
        super.onCreate()
        self = this
        Protokoll.init(filesDir)
        // CookieManager einmal auf dem Hauptthread holen; danach geht er auch aus dem Netz-Thread.
        CookieManager.getInstance()
        einstellungen = Einstellungen(this)
        netz = Netz(this, einstellungen)
        aniworld = AniWorld(netz)
        serienstream = SerienStream(netz)
        db = Datenbank(this)

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(KANAL_DOWNLOADS, "Downloads", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(KANAL_HINWEISE, "Hinweise", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { netz.client }
        .crossfade(true)
        .build()

    fun oeffnenIntent(tab: String? = null): PendingIntent {
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (tab != null) i.putExtra(MainActivity.EXTRA_TAB, tab)
        return PendingIntent.getActivity(this, tab.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Läuft ein Download im Hintergrund in die Schutzseite, holt diese Meldung Tobi in die App. */
    fun meldeCheck() {
        val n = NotificationCompat.Builder(this, KANAL_HINWEISE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("AniWorld braucht kurz dich")
            .setContentText("Eine Seite will einen Browser-Check. Tippen zum Öffnen.")
            .setContentIntent(oeffnenIntent())
            .setAutoCancel(true)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(ID_CHECK, n) }
    }

    fun checkMeldungWeg() {
        getSystemService(NotificationManager::class.java).cancel(ID_CHECK)
    }

    companion object {
        lateinit var self: App
        const val KANAL_DOWNLOADS = "downloads"
        const val KANAL_HINWEISE = "hinweise"
        const val ID_CHECK = 2
    }
}
