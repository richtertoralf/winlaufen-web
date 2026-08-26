# WinLaufen Sprecher-PC LAN – Capture `lauf-standard`

Quelle: `session.pcapng`, aufgenommen am 26.08.2026 auf Linux/Wine über Loopback TCP/4444.

## Transport

- TCP Server: WinLaufen, Port 4444
- Client: SprecherPC
- Client sendet keine Anwendungsnutzlast.
- Java Object Serialization Header am Streamanfang: `AC ED 00 05`.
- Hauptverbindung: ein erfolgreicher Stream mit Nutzdaten.
- Nach Verbindungsende: weitere Verbindungsversuche ungefähr alle 10 s; Server antwortete jeweils mit RST, weil Port 4444 zu diesem Zeitpunkt nicht mehr annahm.

## Uhr

WinLaufen sendet Java-Strings `UhrHH:MM:SS` ungefähr im Sekundentakt.
Im Capture nachgewiesen: `Uhr22:08:01` bis `Uhr22:09:31`.

## Ergebnisblock – exakte Struktur des Standardwettkampfs

Jeder Ergebnisblock hat diese Objektfolge:

1. `String` Wettkampfart: `Standardwettkampf`
2. `Integer` Auswertungsmodus: `1`
3. `Integer` Anzahl Klassen: `2`
4. `String[]` Klassen: `["Schüler U13 m", "Schüler U13 w"]`
5. `int[]` Rundenzahl/Teamgröße: `[0, 0]`
6. `Integer` Position WinSpringen: `0`
7. `Integer` Sprecher-Nr. Klasse: `0` oder `1`
8. `Integer` Runde/Durchgang: `0`
9. `Integer` aktueller Runden-/Zieleinlauf: 0-basierter Index in die Ergebniszeilen
10. eine oder mehrere `Object[]` Ergebniszeilen
11. `String`: `tabelle`
12. `String[]` Tabellenüberschriften
13. `String`: `ende`

Tabellenüberschriften:

```text
Rang
StNr
Name, Vorname
Verein
Vbd
Laufzeit
Rückstand
```

Jede Ergebniszeile besteht aus genau sieben Strings in derselben Reihenfolge.

## Dekodierte Ergebnisblöcke

| # | Capture-Zeit relativ | Klasse | Klassenindex | Aktueller Einlauf | Zeilen |
|---:|---:|---|---:|---:|---:|
| 1 | 16.195 s | Schüler U13 w | 1 | 0 | 1 |
| 2 | 26.521 s | Schüler U13 w | 1 | 1 | 2 |
| 3 | 54.874 s | Schüler U13 m | 0 | 0 | 1 |
| 4 | 62.414 s | Schüler U13 w | 1 | 2 | 3 |
| 5 | 67.108 s | Schüler U13 m | 0 | 1 | 2 |
| 6 | 75.454 s | Schüler U13 w | 1 | 3 | 4 |
| 7 | 82.749 s | Schüler U13 m | 0 | 2 | 3 |

Damit ist empirisch bestätigt:

- Sprecher-Nr. Klasse ist ein 0-basierter Index in `Klassenbezeichnungen`.
- Aktueller Einlauf ist ein 0-basierter Index in die Ergebniszeilen.
- Ein neuer Zieleinlauf führt zu einem vollständigen Snapshot der betroffenen Klasse.
- Die Klasse des letzten Zieleinlaufs ist direkt aus dem Klassenindex des Blocks bestimmbar.
- Die komplette aktuelle Ergebnisliste der Klasse wird übertragen, nicht nur der neue Teilnehmer.
- Ergebnisblöcke sind ereignisgetrieben; zwischen ihnen laufen nur die Uhrstrings weiter.

## Beispiel: Schüler U13 w

Nach vier Zieleinläufen:

```text
1 | 120 | WILHELM Karolin | SV Rotterode  | TSV  | 1:38:17.2 | 0:00:00.0
2 | 117 | JACOB Anne      | SV Rotterode  | TSV  | 1:38:27.4 | 0:00:10.2
3 | 119 | LECHNER Sophie  | SC Schliersee | BSVO | 1:39:03.6 | 0:00:46.4
4 | 118 | EHRLICH Rebecca | SWV Goldlauter| TSV  | 1:39:16.4 | 0:00:59.2
```

## Beispiel: Schüler U13 m

Nach drei Zieleinläufen:

```text
1 | 111 | KREISSL Tobias        | SSV Neuhausen | SVS  | 2:08:55.9 | 0:00:00.0
2 | 112 | EISENLAUER Sebastian  | SC Sonthofen  | BSVA | 2:09:08.0 | 0:00:12.1
3 | 113 | TSCHARNKE Tim          | SV Biberau    | TSV  | 2:09:23.7 | 0:00:27.8
```

## LIVE-Renderer-Vertrag, durch Capture belegt

Für einen Ergebnisblock:

```text
class = classes[speakerClassIndex]
currentAthlete = rows[currentFinishIndex]
currentTable = rows
```

Damit kann der LIVE-Modus ohne Heuristik:

1. auf die Klasse des neuen Zieleinlaufs wechseln,
2. die vollständige Ergebnistabelle anzeigen,
3. exakt den aktuellen Zieleinläufer hervorheben.

## Java-Serialization-Hinweis

Mehrere Werte werden innerhalb derselben Java-Serialisierungssitzung später nur als Handle-Referenz übertragen. Daher darf ein Decoder nicht mit `strings`, Regex oder einfacher Byte-Suche arbeiten. Er muss Java Object Serialization einschließlich `TC_REFERENCE` korrekt dekodieren.

Im Capture werden beispielsweise die Marker `Standardwettkampf`, `tabelle`, `ende` und die Tabellenüberschriften nach ihrer ersten Übertragung später per Java-Handle referenziert.

## Noch offene Captures

1. erfolgreicher Reconnect: Port 4444 muss während desselben Capture wieder verfügbar werden; prüfen, ob WinLaufen sofort einen aktuellen Snapshot sendet.
2. Server-/Sprechernachrichten: `java.util.Vector`, inklusive Verhalten nach Reconnect.
3. Biathlon: Wettkampfart, Auswertungsmodus, Runden-/Zwischenstandslogik, Tabellenfelder.
4. WinSpringen: `Position WinSpringen`, Durchgang, Tabellen-/Punktelogik.
5. Staffel/Team/Verfolgung: Auswertungsmodus, `int[]` Rundenzahl/Teamgröße, Tabellenfelder.
6. Startliste: erst mit der angekündigten WinLaufen-Erweiterung.
