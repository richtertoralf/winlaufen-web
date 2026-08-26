# Biathlon Capture Analysis

**Capture:** `session(1).pcapng`
**Szenario:** WinLaufen-Demo `Biathlon_Demo.wtk`, Sprecher-PC über TCP/4444
**Erfasst:** 26.08.2026, ca. 22:30–22:33
**Status:** Verifizierter wettkampfartspezifischer Beispieldatensatz

## 1. Kontext aus WinLaufen

Die Demo ist in WinLaufen als:

- Sportart: **Biathlon**
- Wettkampfart: **Standardwettkampf**
- Auswertungsmodus: **nach Altersklassen**
- Teilnehmer: **20**

konfiguriert.

Wichtig: Die Sprecher-PC-Schnittstelle liefert im beobachteten Wettkampfdatenblock
`Standardwettkampf`; die separate WinLaufen-Einstellung `Sportart: Biathlon` ist in
diesem Capture nicht als eigener String nachgewiesen.

## 2. Capture-Besonderheit

Der Sprecher-PC war beim Start dieses Captures bereits mit WinLaufen verbunden.
Der Capture beginnt deshalb mitten in einem bestehenden Java-Object-Serialization-Kontext.

Folge:

- kein neuer Stream-Header `AC ED 00 05` am Anfang dieses Captures,
- Wettkampfart, Klassenbezeichnungen und Tabellenüberschriften erscheinen teilweise
  nur noch als Java-`TC_REFERENCE`-Handles,
- Ergebniszeilen und die jeweils neu erzeugten Integer-Werte lassen sich trotzdem
  eindeutig auswerten.

Für einen vollständig selbsttragenden Biathlon-Fixture ist zusätzlich ein Capture mit
**frischem Sprecher-PC-Connect nach Capture-Start** sinnvoll.

## 3. Struktur des Biathlon-Ergebnisblocks

Für den beobachteten Standardwettkampf ist die Kopfstruktur dieselbe wie beim Lauf:

- Element 0: Wettkampfart / Abschnitt → `Standardwettkampf` (aus UI + bestehendem Streamkontext bestätigt)
- Element 1: Auswertungsmodus → `1`
- Element 2: Anzahl Klassen → `4`
- Element 3: Klassenbezeichnungen → vier Einträge, in diesem Midstream-Capture nur als Handles
- Element 4: Rundenzahl / Teamgröße → `[0, 0, 0, 0]`
- Element 5: Position WinSpringen → Referenz auf bereits vorhandenes Integer-Objekt; im bekannten Standardkontext `0`
- Element 6: Sprecher-Nr. Klasse → 0-basierter Klassenindex
- Element 7: Runde / Durchgang → `0`
- Element 8: Aktueller Runden-/Zieleinlauf → 0-basierter Index in die übertragenen Ergebniszeilen

Die Ergebniszeile hat beim Biathlon **acht Felder**:

```text
Rang
StNr
Name, Vorname
Verein
Vbd
Schießen
Gesamtzeit
Rückstand
```

Das entspricht der sichtbaren Tabellenstruktur im originalen Sprecher-PC.

## 4. Verifizierte Ergebnisblöcke

### 22:30:35 — Klasse Index 0, aktueller Einlauf 0

```text
1 | 101 | KREISSL Tommy | <Handle> | <Handle> | 1 0 2 0 | 3:30:35.1 | 0:00:00.0
```

### 22:30:41 — Klasse Index 0, aktueller Einlauf 1

```text
1 | 101 | KREISSL Tommy   | <Handle>          | <Handle> | 1 0 2 0 | 3:30:35.1 | 0:00:00.0
2 | 102 | HASSLER Dominik | SC Rückershausen  | WSV      | 0 1 1 2 | 3:30:41.2 | 0:00:06.1
```

### 22:30:48 — Klasse Index 3, aktueller Einlauf 0

```text
1 | 120 | EHRLICH Rebecca | SWV Goldlauter | TSV | [leer] | 3:25:48.0 | 0:00:00.0
```

Das Schießfeld besteht hier aus Leerzeichen und ist fachlich leer.

### 22:30:55 — Klasse Index 0, aktueller Einlauf 0

```text
1 | 103 | AHLGRIMM Bjoern | SC Münstertal | SBW      | 0 3 0 0 | 3:30:25.1 | 0:00:00.0
2 | 101 | KREISSL Tommy   | <Handle>       | <Handle> | 1 0 2 0 | 3:30:35.1 | 0:00:10.0
3 | 102 | HASSLER Dominik | <Handle>       | <Handle> | 0 1 1 2 | 3:30:41.2 | 0:00:16.1
```

Der aktuelle Zieleinläufer steht auf Index 0 und ist zugleich neuer Rang 1. Das bestätigt:
**Aktueller Einlauf ist kein Rangwert, sondern ein Zeilenindex im sortierten Snapshot.**

### 22:32:40 — Klasse Index 0, aktueller Einlauf 3

```text
1 | 103 | AHLGRIMM Bjoern   | <Handle>      | <Handle> | 0 3 0 0 | 3:30:25.1 | 0:00:00.0
2 | 101 | KREISSL Tommy     | <Handle>      | <Handle> | 5 0 1 1 | 3:30:35.1 | 0:00:10.0
3 | 102 | HASSLER Dominik   | <Handle>      | <Handle> | 3 3 3 3 | 3:30:41.2 | 0:00:16.1
4 | 104 | SCHALLINGER Michael | SV Vachendorf | BSVC   | 5 5 5 5 | 3:32:09.7 | 0:01:44.6
```

Wichtig: Gegenüber früheren Snapshots haben sich bei bereits vorhandenen Athleten die
Schießwerte geändert:

```text
KREISSL Tommy:
1 0 2 0  ->  5 0 1 1

HASSLER Dominik:
0 1 1 2  ->  3 3 3 3
```

Damit ist nachgewiesen, dass ein späterer vollständiger Klassen-Snapshot auch
**aktualisierte Felder bereits vorhandener Ergebniszeilen** enthalten kann.

Eine Bridge darf Ergebniszeilen daher nicht als nach dem ersten Empfang unveränderliche
Events behandeln. Der vollständige Klassen-Snapshot ist für den aktuellen Tabellenzustand
maßgeblich.

### 22:33:05 — Klasse Index 2, aktueller Einlauf 0

```text
1 | 111 | HIEMER Benedikt | SC Krün | BSVW | 1 1 0 0 | 3:30:35.4 | 0:00:00.0
```

Ein später im Sprecher-PC sichtbarer Stand mit anderen Schießwerten bzw. einem weiteren
Athleten liegt zeitlich **nach Ende dieses Captures** und ist daher nicht Teil dieses
PCAPs.

## 5. Übertragungsverhalten Schießen

Im getesteten Bedienablauf wurden die Schießergebnisse in WinLaufen zuerst erfasst und
anschließend die Zielzeit eingetragen.

Beobachtet wurde:

1. Das Eintragen der Schießergebnisse allein erzeugte keinen eigenständigen
   Sprecher-PC-Ergebnisblock.
2. Mit der Zielzeit wurde ein vollständiger Ergebnis-Snapshot der Klasse übertragen.
3. Dieser Snapshot enthielt das Schießfeld des Zieleinläufers.
4. Spätere vollständige Snapshots können aktualisierte Schießwerte bereits vorhandener
   Athleten enthalten.

Für diesen getesteten Workflow gilt deshalb:

```text
Schießeingabe
    │
    └── kein eigenständiger Ergebnis-Snapshot beobachtet

Zielzeit
    │
    ▼
vollständiger Klassen-Snapshot
    ├── Rang
    ├── Startnummer
    ├── Name
    ├── Verein / Verband
    ├── Schießen
    ├── Gesamtzeit
    └── Rückstand
```

Das Sprecher-PC-Protokoll ist damit in diesem Szenario **kein eigenständiger
Live-Schießdatenfeed**.

Soll der spätere Webrenderer Schießstände bereits vor dem Zieleinlauf anzeigen, muss
separat geprüft werden, ob WinLaufen bei anderen Abwicklungszuständen, Runden oder
Zwischenzeiten entsprechende Sprecher-PC-Blöcke sendet.

## 6. Konsequenzen für WinLaufen Web

### LIVE

Beim Zielereignis:

- Klasse über `Sprecher-Nr.` bestimmen,
- vollständigen Klassen-Snapshot übernehmen,
- Zeile `Aktueller Einlauf` hervorheben,
- Biathlon-Spalte `Schießen` mit anzeigen,
- `Gesamtzeit` statt laufwettkampfspezifisch fest verdrahtetem `Laufzeit` verwenden.

### Ergebnisse

Die manuelle Ergebnisansicht muss die vom Stream gelieferten Tabellenüberschriften und
wettkampfartspezifischen Spalten berücksichtigen.

Insbesondere darf der Renderer nicht global auf das Lauf-Schema

```text
Rang | StNr | Name | Verein | Vbd | Laufzeit | Rückstand
```

festgelegt werden.

Für den beobachteten Biathlon-Standardwettkampf gilt:

```text
Rang | StNr | Name | Verein | Vbd | Schießen | Gesamtzeit | Rückstand
```

## 7. Noch offen für Biathlon

Für Biathlon fehlen noch:

1. Fresh-connect-Capture mit Stream-Header, Klassenbezeichnungen und Tabellenüberschriften
   innerhalb desselben Captures.
2. Verhalten von Zwischenzeiten / Runden vor dem Ziel.
3. Prüfung, ob Schießdaten in irgendeinem anderen Sprecher-PC-Zustand schon vor einer
   Zielzeit übertragen werden.
4. Bedeutung von Element 7/8 bei echten Runden-/Zwischenständen außerhalb `Ziel`.
5. Verhalten bei DNS/DNF/DSQ und unvollständigen Schießserien.
6. Prüfung weiterer Biathlon-Wettkampfarten bzw. Auswertungsmodi, falls WinLaufen dort
   andere Tabellenstrukturen liefert.

## 8. Wichtigster neuer Protokollbefund

Der gemeinsame Transportvertrag bleibt sportartenneutral.

Die **Tabellenstruktur ist jedoch wettkampfartspezifisch** und muss aus den vom
Sprecher-PC-Protokoll gelieferten Tabellenüberschriften bzw. verifizierten Demo-Daten
abgeleitet werden.

Für Biathlon ist zusätzlich nachgewiesen:

> Schießergebnisse sind Bestandteil der Ergebniszeile. Im getesteten Ablauf wurden sie
> nicht als separates Schießereignis übertragen, sondern mit einem durch die Zielzeit
> ausgelösten vollständigen Klassen-Snapshot.
