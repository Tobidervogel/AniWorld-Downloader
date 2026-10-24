package de.tobidervogel.aniworld.ui

import de.tobidervogel.aniworld.site.Genre

/** Seiten, die über die Reiter gelegt werden. Ein einfacher Stapel statt Navigation-Bibliothek. */
sealed interface Ziel {
    data class Serie(val url: String, val titel: String, val cover: String?, val staffel: Int? = null) : Ziel
    /** @param quelle Name des Anbieters (AniWorld, SerienStream) */
    data class GenreListe(val genre: Genre, val quelle: String) : Ziel
    data object Suche : Ziel
    data object ProtokollAnsicht : Ziel
}
