# AniWorld-App für Android

Eigene Android-App zu diesem Fork, gebaut nur für Tobis **OnePlus Nord 3** (Android 16).
Sie macht auf dem Handy das, was das Python-Programm am PC macht: auf **aniworld.to** und
**serienstream.to** suchen, Staffeln und Folgen ansehen, streamen und herunterladen.

Die App ist ein eigenständiger Nachbau in Kotlin. Python, FFmpeg und Chromium laufen auf dem Handy nicht;
die Logik aus `src/aniworld/` ist deshalb übertragen, nicht eingebunden.

## Was sie kann

- **Zwei Seiten:** AniWorld (Animes) und SerienStream (Serien), oben auf „Entdecken“ umschaltbar.
  Fällt serienstream.to aus, geht es über serienstream.cx weiter.
- **Entdecken:** AniWorld mit „Derzeit beliebt“, „Neue Animes“, „Neue Folgen“; SerienStream mit
  „Neue Staffeln diese Woche“ und „Meistgesehen gerade“. Dazu Genres und zuletzt geöffnete Serien.
- **Suche** auf beiden Seiten gleichzeitig.
- **Serie:** Cover, Beschreibung, Staffeln und Filme, Folgen mit Sprachen (AniWorld: DE, DE-Sub, EN-Sub, EN;
  SerienStream: DE, EN).
- **Streamen** im eingebauten Player (Media3), mit Fortsetzen an der letzten Stelle.
- **AniSkip:** Bei Animes holt der Player Opening- und Ending-Zeiten (MyAnimeList über Jikan, dann
  api.aniskip.com) und zeigt währenddessen „Opening überspringen“. Auf Wunsch springt er automatisch.
  Ohne Daten bleibt der Knopf „Opening +85 s“.
- **Herunterladen:** einzelne Folgen, ganze Staffeln oder die ganze Serie, im Hintergrund als Vordergrunddienst.
  Fertige Folgen liegen unter `Movies/AniWorld/<Serie>/Staffel 01/<Serie> S01E01.mp4`.
- **Hoster:** VOE, Filemoon, Doodstream, Vidoza, Vidmoly. Reihenfolge in den Einstellungen; scheitert einer,
  kommt der nächste. Die Sprache wechselt dabei nie von selbst (wie im Original).
- Geladene Folgen spielen im Player nacheinander durch.
- **Links teilen:** Einen aniworld.to- oder serienstream.to-Link an die App teilen öffnet direkt die Serie.

## Installieren

1. `app-release.apk` aufs Nord 3 laden und öffnen (Installation aus unbekannter Quelle einmal erlauben).
2. Beim ersten Start Benachrichtigungen erlauben.
3. Unter **Einstellungen → Zugriff auf alle Dateien** erlauben. Ohne landen Folgen in `Android/media/de.tobidervogel.aniworld/`.
4. Unter **Einstellungen → Akku-Optimierung** ausschalten, sonst stoppt OxygenOS lange Downloads.

Updates lassen sich direkt drüber installieren: Alle Builds sind mit `app/aniworld.keystore` signiert.

## Wie es gebaut ist

| Python-Original | App |
|---|---|
| `models/aniworld_to/*.py`, `search.py` | `site/AniWorldParser.kt` (Jsoup statt `find()`-Ketten), `site/AniWorld.kt` |
| `models/s_to/*.py` | `site/SerienStreamParser.kt`, `site/SerienStream.kt` (Spiegel-Ausweich wie `http.py`) |
| `aniskip/` | `site/AniSkip.kt` + Knopf im Player |
| `config.py` `GLOBAL_SESSION` mit `doh+cloudflare` | `net/Netz.kt`: OkHttp mit DNS über HTTPS, Cookies aus der WebView |
| `playwright/captcha.py` | `net/BrowserCheck.kt` + `ui/BrowserFenster.kt`: sichtbares Browserfenster, Tobi löst ein Rätsel selbst |
| `extractors/provider/*.py` | `hoster/Voe.kt`, `Filemoon.kt`, `Doodstream.kt`, `Vidmoly.kt`, `Vidoza.kt` |
| – | `hoster/Mitleser.kt`: Ausweichweg, Hoster unsichtbar im Browser öffnen und die Videoadresse mitlesen |
| `models/common/hls.py` + FFmpeg | `download/Hls.kt`, `Lader.kt` (Segmente parallel, AES-128), `Umpacker.kt` (TS → MP4 mit MediaMuxer) |
| Web-UI-Warteschlange | `download/Datenbank.kt` (SQLite), `DownloadService.kt` |

Versionen wie bei Live Up: AGP 8.4.1, Kotlin 2.3.21, Gradle 8.7, compileSdk 34. `targetSdk` bleibt bewusst 34:
Ab 35 begrenzt Android Download-Dienste auf 6 Stunden am Tag.

## Bauen

Am PC (wie Live Up, JDK 17 und SDK 34):

```
cd android
./gradlew assembleRelease        # APK: app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest      # Parser-, Hoster- und HLS-Tests
```

In einer Cloud-Sitzung antwortet Maven Central manchmal mit 429. Dann hilft ein Gradle-Init-Skript, das
`repo.maven.apache.org` auf `maven-central.storage-download.googleapis.com/maven2/` umbiegt.

## Grenzen (Stand 0.2.0)

- **Nicht auf dem Gerät getestet.** Gebaut und mit Unit-Tests geprüft wurde in einer Cloud-Sitzung.
  aniworld.to sperrt Rechenzentrums-IPs mit einer DDoS-Guard-Prüfung; der AniWorld-Parser ist deshalb nur
  gegen die Beispiel-HTML und Fixtures des Python-Codes geprüft. Der SerienStream-Parser ist gegen Ausschnitte
  der echten Seite vom 24.10.2026 geprüft (`sto_*.html`), die Weiterleitung `/r` zum Hoster nicht
  (dort steht für Rechenzentren ebenfalls DDoS-Guard). Das AniSkip-Format ist live geprüft, Jikan war
  aus der Cloud nicht erreichbar.
- Bei Fehlern: **Einstellungen → Protokoll → Kopieren** und den Text an Claude geben.
- Von den Seiten des Originals fehlen noch MegaKino, Filmo, Moflix, MangaFire und die übrigen.
- Kein Syncplay.
