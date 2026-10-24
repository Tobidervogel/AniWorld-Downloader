package de.tobidervogel.aniworld.hoster

import de.tobidervogel.aniworld.net.Netz
import de.tobidervogel.aniworld.net.NetzFehler

/** Doodstream, übertragen aus extractors/provider/doodstream.py. */
object Doodstream : Hoster {
    override val name = "Doodstream"

    private val PASS_MD5 = Regex("""\$\.get\('([^']*/pass_md5/[^']*)'""")
    private val TOKEN = Regex("""token=([a-zA-Z0-9]+)""")
    private const val ZEICHEN = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    /** Die Direktadresse baut das Skript der Seite selbst zusammen; hier dasselbe in Kotlin. */
    fun direktLink(basis: String, token: String, jetzt: Long = System.currentTimeMillis() / 1000): String {
        val zufall = (1..10).map { ZEICHEN.random() }.joinToString("")
        return "$basis$zufall?token=$token&expiry=$jetzt"
    }

    override suspend fun aufloesen(netz: Netz, embed: String): Stream {
        val kopf = mapOf("User-Agent" to netz.userAgent, "Referer" to embed)
        val seite = netz.anfrage(embed, kopf)
        if (!seite.ok) throw NetzFehler("Doodstream antwortet mit HTTP ${seite.code}")
        val pfad = PASS_MD5.find(seite.text)?.groupValues?.get(1) ?: throw NetzFehler("Doodstream: pass_md5 fehlt")
        val token = TOKEN.find(seite.text)?.groupValues?.get(1) ?: throw NetzFehler("Doodstream: Token fehlt")
        // Relativ zur Seite auflösen, auf der wir nach den Weiterleitungen gelandet sind (Dood wechselt Domains).
        val md5Url = if (pfad.startsWith("http")) pfad else Js.ursprung(seite.url) + pfad
        val basis = netz.anfrage(md5Url, kopf).text.trim()
        if (!basis.startsWith("http")) throw NetzFehler("Doodstream: leere Antwort von pass_md5")
        // Der Link ist an den User-Agent gebunden, deshalb dieselben Kopfzeilen beim Laden.
        return Stream(direktLink(basis, token), mapOf("User-Agent" to netz.userAgent, "Referer" to Js.ursprung(seite.url) + "/"), name)
    }
}
