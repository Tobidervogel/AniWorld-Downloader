# AniWorld-App für Android

Eigene Android-App zu diesem Fork, gebaut nur für Tobis **OnePlus Nord 3** (Android 16).
Sie macht auf dem Handy das, was das Python-Programm am PC macht: auf aniworld.to suchen,
Staffeln und Folgen ansehen, streamen und herunterladen.

Die App ist ein eigenständiger Nachbau in Kotlin. Python, FFmpeg und Chromium laufen auf dem Handy nicht;
die Logik aus `src/aniworld/` ist deshalb übertragen, nicht eingebunden.

## Was sie kann

- **Entdecken:** „Derzeit beliebt“, „Neue Animes“, „Neue Folgen“, Genres und zuletzt geöffnete Serien.
- **Suche** über dieselbe Schnittstelle wie das Original (`/ajax/search`).
- **Serie:** Cover, Beschreibung, Staffeln und Filme, Folgen mit Sprachen (DE, DE-Sub, EN-Sub, EN).
- **Streamen** im eingebauten Player (Media3), mit Fortsetzen an der letzten Stelle und „Opening +85 s“.
- **Herunterladen:** einzelne Folgen, ganze Staffeln oder die ganze Serie, im Hintergrund als Vordergrunddienst.
  Fertige Folgen liegen unter `Movies/AniWorld/<Serie>/Staffel 01/<Serie> S01E01.mp4`.
- **Hoster:** VOE, Filemoon, Doodstream, Vidoza, Vidmoly. Reihenfolge in den Einstellungen; scheitert einer,
  kommt der nächste. Die Sprache wechselt dabei nie von selbst (wie im Original).
- Geladene Folgen spielen im Player nacheinander durch.
- **Links teilen:** Einen aniworld.to-Link aus dem Browser an die App teilen öffnet direkt die Serie.

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

## Grenzen (Stand 0.1.0)

- **Nicht auf dem Gerät getestet.** Gebaut und mit Unit-Tests geprüft wurde in einer Cloud-Sitzung; aniworld.to
  sperrt Rechenzentrums-IPs mit einer DDoS-Guard-Prüfung, deshalb lief kein Test gegen die echte Seite.
  Die Parser sind gegen die Beispiel-HTML aus dem Python-Code und dessen Test-Fixtures geprüft.
- Bei Fehlern: **Einstellungen → Protokoll → Kopieren** und den Text an Claude geben.
- Nur aniworld.to. SerienStream und die anderen Seiten des Originals fehlen noch.
- Kein AniSkip und kein Syncplay.
