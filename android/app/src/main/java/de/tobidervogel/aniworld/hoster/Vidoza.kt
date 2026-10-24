package de.tobidervogel.aniworld.hoster

import de.tobidervogel.aniworld.net.Netz
import de.tobidervogel.aniworld.net.NetzFehler

/** Vidoza, übertragen aus extractors/provider/vidoza.py. Liefert eine direkte MP4. */
object Vidoza : Hoster {
    override val name = "Vidoza"

    private val QUELLE = Regex("""src:\s*"([^"]+)"""")

    fun ausHtml(html: String): String? =
        if ("sourcesCode:" in html) QUELLE.find(html)?.groupValues?.get(1) else Js.MP4.find(html)?.value

    override suspend fun aufloesen(netz: Netz, embed: String): Stream {
        val kopf = mapOf("User-Agent" to netz.userAgent)
        val seite = netz.anfrage(embed, kopf)
        if (!seite.ok) throw NetzFehler("Vidoza antwortet mit HTTP ${seite.code}")
        val url = ausHtml(seite.text) ?: throw NetzFehler("Vidoza: keine Videoquelle gefunden")
        return Stream(url, kopf + ("Referer" to Js.ursprung(seite.url) + "/"), name)
    }
}
