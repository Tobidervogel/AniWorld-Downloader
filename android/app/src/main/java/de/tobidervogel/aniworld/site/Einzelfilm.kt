package de.tobidervogel.aniworld.site

/**
 * Seiten, auf denen jeder Titel ein einzelner Film ist (Filmo, FilmPalast). Solche Titel haben in
 * der App genau eine „Folge“ und landen gesammelt unter `<Ordner>/Filme/<Titel (Jahr)>.mp4`.
 */
object Einzelfilm {
    fun ist(url: String): Boolean = FilmoParser.filmUrl(url) != null || FilmPalastParser.filmUrl(url) != null
}
