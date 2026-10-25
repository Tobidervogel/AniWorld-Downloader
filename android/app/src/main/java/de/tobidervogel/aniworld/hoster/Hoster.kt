package de.tobidervogel.aniworld.hoster

import de.tobidervogel.aniworld.net.Netz

/** Eine abspielbare Adresse samt den Kopfzeilen, die der Hoster beim Laden erwartet. */
data class Stream(
    val url: String,
    val header: Map<String, String>,
    val hoster: String,
) {
    val istHls get() = url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) || ".m3u8" in url
}

interface Hoster {
    val name: String
    suspend fun aufloesen(netz: Netz, embed: String): Stream
}

object Hosters {
    val alle: List<Hoster> = listOf(Voe, Filemoon, Doodstream, Vidmoly, Vidoza)

    // Wie _ALIASES in models/common/provider_map.py: Teilstring im Namen oder in der Adresse.
    private val aliase = listOf(
        "voe" to Voe,
        "dood" to Doodstream,
        "vidmoly" to Vidmoly,
        "vidoza" to Vidoza,
        "filemoon" to Filemoon,
        "byse" to Filemoon,
    )

    fun fuer(name: String): Hoster? {
        val n = name.lowercase()
        return aliase.firstOrNull { n.contains(it.first) }?.second
    }
}

/** Gemeinsame Hilfen der Extraktoren. */
internal object Js {
    private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private val PACKED = Regex(
        """eval\(function\(p,a,c,k,e,d\)\{.*?\}\('(.+?)',\s*(\d+),\s*(\d+),\s*'(.+?)'\.split\('\|'\)""",
        RegexOption.DOT_MATCHES_ALL,
    )

    private fun basisN(token: String, radix: Int): Int {
        var r = 0
        for (c in token) {
            val d = ALPHABET.indexOf(c)
            if (d !in 0 until radix) return -1
            r = r * radix + d
        }
        return r
    }

    /** Entpackt Dean Edwards' Packer (eval(function(p,a,c,k,e,d)…), wie unpack_js() im Original. */
    fun entpacken(p: String, radix: Int, woerter: List<String>): String =
        Regex("\\b(\\w+)\\b").replace(p) { m ->
            val i = basisN(m.value, radix)
            if (i in woerter.indices && woerter[i].isNotEmpty()) woerter[i] else m.value
        }

    /** Alle gepackten Skripte einer Seite entpackt (leer, wenn keins da ist). */
    fun alleEntpackt(html: String): List<String> = PACKED.findAll(html).map { m ->
        entpacken(m.groupValues[1].replace("\\'", "'"), m.groupValues[2].toInt(), m.groupValues[4].split('|'))
    }.toList()

    val M3U8 = Regex("""https?://[^\s'"<>\\]+?\.m3u8[^\s'"<>\\]*""")
    val MP4 = Regex("""https?://[^\s'"<>\\]+?\.mp4[^\s'"<>\\]*""")

    fun ursprung(url: String): String {
        val ohne = url.substringAfter("://")
        return url.substringBefore("://") + "://" + ohne.substringBefore('/')
    }
}
