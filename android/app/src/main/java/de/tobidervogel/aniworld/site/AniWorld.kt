package de.tobidervogel.aniworld.site

import de.tobidervogel.aniworld.net.BrowserCheck
import de.tobidervogel.aniworld.net.Netz
import de.tobidervogel.aniworld.net.NetzFehler
import de.tobidervogel.aniworld.site.AniWorldParser.BASIS
import java.util.concurrent.ConcurrentHashMap

data class Startseite(val beliebt: List<Karte>, val neu: List<Karte>, val genres: List<Genre>)

/** Zugriff auf aniworld.to: holt Seiten über [Netz] und lässt sie vom [AniWorldParser] lesen. */
class AniWorld(private val netz: Netz) {
    // Einfache Zwischenspeicher für die Sitzung: Zurück-Navigation soll nicht neu laden.
    private val serien = ConcurrentHashMap<String, Serie>()
    private val staffeln = ConcurrentHashMap<String, List<Folge>>()

    suspend fun startseite(): Startseite {
        val html = netz.seite("$BASIS/").text
        return Startseite(
            beliebt = AniWorldParser.abschnitt(html, "Derzeit beliebt"),
            neu = AniWorldParser.abschnitt(html, "Neue Animes"),
            genres = AniWorldParser.genres(html),
        )
    }

    suspend fun neueFolgen(): List<NeueFolge> =
        AniWorldParser.neueFolgen(netz.seite("$BASIS/neue-episoden").text)

    suspend fun suche(text: String): List<Treffer> {
        if (text.isBlank()) return emptyList()
        val a = netz.seite(
            "$BASIS/ajax/search",
            formular = mapOf("keyword" to text.trim()),
            header = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "Referer" to "$BASIS/search",
            ),
        )
        return AniWorldParser.suche(a.text)
    }

    suspend fun genre(slug: String, seite: Int): Pair<List<Karte>, Boolean> {
        val url = if (seite > 1) "$BASIS/genre/$slug/$seite" else "$BASIS/genre/$slug"
        return AniWorldParser.genreSeite(netz.seite(url).text, slug, seite)
    }

    suspend fun serie(url: String, neu: Boolean = false): Serie {
        if (!neu) serien[url]?.let { return it }
        return AniWorldParser.serie(netz.seite(url).text, url).also { serien[url] = it }
    }

    suspend fun staffel(ref: StaffelRef, neu: Boolean = false): List<Folge> {
        if (!neu) staffeln[ref.url]?.let { return it }
        return AniWorldParser.staffel(netz.seite(ref.url).text, ref).also { staffeln[ref.url] = it }
    }

    suspend fun links(folgenUrl: String): FolgenLinks =
        AniWorldParser.folgenLinks(netz.seite(folgenUrl).text)

    /**
     * `/redirect/<id>` führt per HTTP-Weiterleitung zum Hoster. Bleibt die Antwort auf
     * aniworld.to (Captcha vor dem Hoster), öffnet sich das Browserfenster.
     */
    suspend fun hosterAdresse(redirect: String, folgenUrl: String): String {
        val a = netz.anfrage(redirect, mapOf("Referer" to folgenUrl))
        // Was der Hoster selbst antwortet, ist hier egal; das prüft danach der Extraktor.
        if (Netz.host(a.url) != "aniworld.to") return a.url
        val ende = BrowserCheck.loesen(redirect, verlassen = true)
        if (ende != null && Netz.host(ende) != "aniworld.to") return ende
        throw NetzFehler("Weiterleitung zum Hoster hat nicht geklappt (HTTP ${a.code})")
    }
}
