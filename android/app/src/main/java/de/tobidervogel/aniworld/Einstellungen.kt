package de.tobidervogel.aniworld

import android.content.Context
import android.os.Environment
import de.tobidervogel.aniworld.site.Sprache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Alle Einstellungen in einer SharedPreferences-Datei. `stand` zählt bei jeder Änderung hoch,
 * damit Compose neu zeichnet (gleiches Muster wie der Versionszähler in Live Up).
 */
class Einstellungen(context: Context) {
    private val p = context.getSharedPreferences("einstellungen", Context.MODE_PRIVATE)
    private val _stand = MutableStateFlow(0)
    val stand: StateFlow<Int> = _stand

    private fun geaendert() { _stand.value++ }

    var ordner: String
        get() = p.getString("ordner", null) ?: STANDARD_ORDNER
        set(v) { p.edit().putString("ordner", v.trim().trimEnd('/')).apply(); geaendert() }

    var sprache: Sprache
        get() = runCatching { Sprache.valueOf(p.getString("sprache", null)!!) }.getOrDefault(Sprache.DE_DUB)
        set(v) { p.edit().putString("sprache", v.name).apply(); geaendert() }

    /** Hoster in Versuchsreihenfolge. Unbekannte Namen von der Seite kommen hinten dran. */
    var hosterReihenfolge: List<String>
        get() = p.getString("hoster", null)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.let { gespeichert -> gespeichert + STANDARD_HOSTER.filter { it !in gespeichert } }
            ?: STANDARD_HOSTER
        set(v) { p.edit().putString("hoster", v.joinToString(",")).apply(); geaendert() }

    /** Wie viele Folgen gleichzeitig laden. */
    var parallel: Int
        get() = p.getInt("parallel", 2).coerceIn(1, 3)
        set(v) { p.edit().putInt("parallel", v.coerceIn(1, 3)).apply(); geaendert() }

    /** Wie viele HLS-Segmente einer Folge gleichzeitig laden. */
    var segmente: Int
        get() = p.getInt("segmente", 8).coerceIn(1, 16)
        set(v) { p.edit().putInt("segmente", v.coerceIn(1, 16)).apply(); geaendert() }

    var doh: Boolean
        get() = p.getBoolean("doh", true)
        set(v) { p.edit().putBoolean("doh", v).apply(); geaendert() }

    /** HLS-Downloads nach MP4 umpacken (sonst bleibt .ts). */
    var mp4: Boolean
        get() = p.getBoolean("mp4", true)
        set(v) { p.edit().putBoolean("mp4", v).apply(); geaendert() }

    /** Wenn kein Extraktor klappt: Hoster im versteckten Browser öffnen und die Videoadresse mitlesen. */
    var browserWeg: Boolean
        get() = p.getBoolean("browserWeg", true)
        set(v) { p.edit().putBoolean("browserWeg", v).apply(); geaendert() }

    var nurWlan: Boolean
        get() = p.getBoolean("nurWlan", false)
        set(v) { p.edit().putBoolean("nurWlan", v).apply(); geaendert() }

    companion object {
        val STANDARD_ORDNER: String =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)?.absolutePath
                ?.let { "$it/AniWorld" } ?: "/storage/emulated/0/Movies/AniWorld"

        // VOE, Filemoon und Doodstream liefen beim letzten Provider-Check des Originals, Vidmoly nicht.
        val STANDARD_HOSTER = listOf("VOE", "Filemoon", "Doodstream", "Vidoza", "Vidmoly")
    }
}
