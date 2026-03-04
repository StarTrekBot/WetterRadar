# WetterRadar für Deutschland

Eine Android-App, die aktuelle Wetterradarbilder für Deutschland anzeigt und eine einfache Animation ermöglicht.

## Funktionen

*   **Live-Radarbilder:** Zeigt aktuelle Niederschlagsradar-Daten (Produkt WN) des Deutschen Wetterdienstes (DWD) über eine OpenStreetMap-Karte von Deutschland.
*   **Animation:** Spielt die letzten 16 Radarbilder in einer Schleife mit 3-Sekunden-Intervall ab.
*   **Navigation:** Manuelle Navigation durch die einzelnen Radarbilder mit Vor/Zurück-Tasten.
*   **Automatisches Laden:** Lädt beim Starten der App die neuesten verfügbaren Radarbilder herunter.
*   **Direkte Anzeige nach Download:** Jedes neu geladene Radarbild wird sofort angezeigt (solange die Animation pausiert ist).
*   **Automatischer Start der Animation:** Nachdem alle Bilder geladen wurden, startet die Animation automatisch.
*   **Standortwahl:** Erlaubt es, den Kartenmittelpunkt manuell über einen Einstellungsdialog zu setzen (speichert den Ort lokal).
*   **Zeitanzeige:** Zeigt die Uhrzeit des aktuell angezeigten Radarbildes (in UTC) an.
*   **Farbskala:** Zeigt eine Farbskala unterhalb der Karte mit der Beschriftung "gering", "leicht", "stark", "extrem".
*   **Adaptives App-Icon:** Ein runder App-Icon-Stil für moderne Android-Versionen.

## Technologie

*   **Sprache:** Kotlin
*   **Plattform:** Android (API Level 21+)
*   **Kartendarstellung:** [OSMDroid](https://github.com/osmdroid/osmdroid) (OpenStreetMap)
*   **Datenquelle:** [Deutscher Wetterdienst (DWD)](https://www.dwd.de/) - WMS-Endpunkt für Radarprodukt WN
*   **Build-System:** Gradle (Android Studio)

## Installation

1.  Stelle sicher, dass [Android Studio](https://developer.android.com/studio) installiert ist.
2.  Klone dieses Repository:
  	bash
    git clone https://github.com/StarTrekBot/WetterRadar.git
    
3.  Öffne das Projekt in Android Studio.
4.  Stelle sicher, dass alle Abhängigkeiten in `app/build.gradle` korrekt aufgelöst werden.
5.  Verbinde ein Android-Gerät oder starte einen Emulator.
6.  Klicke in Android Studio auf `Run` -> `Run 'app'`.

## Verwendung

*   Die App lädt beim Starten automatisch die neuesten Radarbilder.
*   Nutze die Buttons "Start"/"Stop", "Vor", "Zurück" und "Download" zur Interaktion.
*   Greife über das Menü (drei Punkte oben rechts) auf die Standorteinstellungen zu.

## Lizenz

Dieses Projekt ist unter der GNU GENERAL PUBLIC LICENSE Version 3 lizenziert.

## Haftungsausschluss

Die Wetterdaten stammen vom Deutschen Wetterdienst (DWD). Diese App ist ein privates Projekt und nicht offiziell mit dem DWD verbunden. Die Genauigkeit und Aktualität der Daten ist abhängig vom DWD.

