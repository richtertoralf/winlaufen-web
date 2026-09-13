# WinLaufen Sprecher-PC LAN-Protokoll

## Status

Dieses Dokument trennt verifizierte Beobachtungen von unbekanntem Verhalten.

Undokumentiertes Verhalten darf nicht durch Raten implementiert werden.

Evidenz und Herkunft werden bewusst unterschieden:

- Offizielle Dokumentation: die Beschreibung des WinLaufen-Sprecher-PC-
  LAN-Protokolls. Sie ist Quelle für dokumentierte Objektbedeutungen, das
  Quelldokument selbst liegt derzeit aber nicht in diesem Repository.
- Aufgezeichnete Wire-Evidenz: die unveränderlichen PCAP-Fixtures unter
  `testdata/protocol/`, einschließlich serialisierter Typen, Objektreihenfolge,
  Werte und TCP-Richtung.
- Beobachtetes Anwendungsverhalten: die originale Sprecher-PC-Anzeige und die
  WinLaufen-Oberfläche bzw. der Workflow während einer Aufzeichnung. Solche
  Beobachtungen liefern Szenariokontext, sind aber keine zusätzlichen
  Wire-Felder.

Die folgenden Aussagen kennzeichnen dokumentiertes, aufgezeichnetes oder in der
Oberfläche beobachtetes Verhalten, wo diese Unterscheidung relevant ist. Ein
Wert aus der WinLaufen-Oberfläche darf nicht als Wire-Feld behandelt werden,
solange er nicht ebenfalls aufgezeichnet wurde.

## Transport

Verifiziert:

- TCP
- Server: WinLaufen
- Client: Sprecher-PC / Sprecher-Web Bridge
- Standardport: 4444
- Java Object Serialization
- aus Sicht der Bridge strikt read-only

In den aufgezeichneten Sitzungen wurde keine Anwendungsnutzlast vom Client zum
Server beobachtet.

## Autorität und Validierungsgrenze

WinLaufen ist die alleinige Autorität für dokumentierte Wire-Werte. WinLaufen
Web erhält deren Text, Reihenfolge und Indexbedeutung ohne fachliche
Plausibilitätsprüfungen oder Korrekturen. Das gilt für die Uhr und alle
Ergebnisfelder, einschließlich Rängen, Startnummern, Zeiten, Rückständen,
Schießwerten, Namen, Vereinen, Verbänden, Headern und Zellen. Strukturelle
Prüfungen bleiben verpflichtend: Nachrichtentypen, Java-Typen, Array-/
Tabellenform, Marker und defensive Grenzen je Objekt dürfen geprüft werden.

## Java-Serialisierung

Eine frische Verbindung beginnt mit dem Java-Serialisierungs-Streamheader:

AC ED 00 05

Java-Objektreferenz-Handles werden innerhalb einer Verbindung wiederverwendet.

Ein Parser muss den Serialisierungskontext deshalb über die gesamte
Verbindungsdauer aufrechterhalten.

Nach Disconnect/Reconnect muss der alte Decodierkontext verworfen werden.

Nicht über druckbare Strings, reguläre Ausdrücke oder feste Byte-Offsets parsen.

## Uhr

### Bisher unterstützte Sprecher-PC-Variante

WinLaufen sendet Strings der Form:

UhrHH:MM:SS

Beobachtet etwa einmal pro Sekunde.

Die Erkennung ist rein strukturell: `Uhr`, gefolgt von exakt zwei
Dezimalziffern, `:`, zwei Dezimalziffern, `:`, zwei Dezimalziffern. Für die
Felder gelten keine Wertebereichsprüfungen. `Uhr99:99:99` ist damit ein
erkanntes Telegramm und wird als `99:99:99` weitergegeben; das hält fest, was
WinLaufen gesendet hat, und behauptet nicht, dass es eine gültige Uhrzeit ist.

### Legacy-Variante (mit WinLaufen 14 beobachtet)

Am 13.09.2026 wurde an einem realen WinLaufen-14-PC auf TCP 4444 nach dem
Streamheader `AC ED 00 05` etwa einmal pro Sekunde ein `java.util.Vector`
mit genau einem logischen Element beobachtet: `Vector["09:33:50"]`, danach
`Vector["09:33:51"]`, `Vector["09:33:52"]` und weitere Uhrwerte.
Die interne Kapazität betrug 10; maßgeblich ist ausschließlich `Vector.size() == 1`.

Diese Variante wird nur erkannt, wenn Element 0 ein `String` ist und exakt
`^\d{2}:\d{2}:\d{2}$` entspricht. Es gibt keine Wertebereichsprüfung.
Andere Vector-Inhalte werden dadurch nicht zur Uhr. Die bisherige
String-Variante bleibt unverändert; die Erkennung benötigt keinen Versionsschalter.
Der genaue Versionswechsel ist nicht nachgewiesen. Dieses Format ist deshalb
nicht als ausschließliches Merkmal von Version 14 zu verstehen.

Quelle dieser neuen Beobachtung ist der berichtete Mitschnitt vom 13.09.2026;
die neuen Regressionstests serialisieren dessen beschriebene Objektstruktur
synthetisch, einschließlich Kapazität 10. Sie sind kein neues Original-PCAP.

Beide Varianten verwenden denselben Clock-/Heartbeat-/Liveness-Callback.
Ein gültiger Legacy-Vector schließt die Verbindung nicht und löst keinen
Reconnect aus. Ein `ObjectInputStream` bleibt für die gesamte TCP-Verbindung
erhalten, auch für aufeinanderfolgende Legacy-Telegramme.

### Gemeinsame Uhr- und Heartbeat-Semantik

Die WinLaufen-Uhr ist autoritativ. Jedes erkannte Uhrentelegramm wird unverändert
veröffentlicht und zählt als Heartbeat, unabhängig davon, ob sein Wert gleich,
kleiner oder größer als der vorherige ist. Sprecher-Web validiert, korrigiert
und plausibilisiert den Uhrenverlauf nicht.

Gleiche Werte, rückwärtslaufende Werte, große Sprünge und `Uhr23:59:59` gefolgt
von `Uhr00:00:00` werden alle unverändert akzeptiert. Es gibt keine Regel für
Dauer, Überlauf, Mitternacht oder Fortschritt. Jedes erkannte Telegramm
erneuert die technische Liveness. Mehr als vier Sekunden ohne ein solches
Telegramm — auch vor der ersten Uhr auf einem frischen Stream — setzt die
Verbindung auf stale und löst einen Reconnect aus.

### Die Wettkampfzeit auf dem gesamten Weg bis zum Browser

Der angezeigte Wert ist die **Wettkampfzeit aus WinLaufen** und keine Uhrzeit
irgendeines beteiligten Rechners. Er ist insbesondere nicht die Systemzeit des
WinLaufen-PCs, der Bridge, des Live Servers oder des Browsers.

Bridge, Contract, Live Server und Browser dürfen diesen Wert ausschließlich
transportieren, speichern und anzeigen. Er wird auf keiner nachgelagerten Stufe
erzeugt, hochgezählt, fortgeschrieben oder interpoliert. Verboten sind dafür
insbesondere `Date.now()`, `new Date()`, `System.currentTimeMillis()`,
`Instant.now()`, ein `setInterval`-Zähler und jede Rechnung „letzte
Wettkampfzeit plus vergangene lokale Zeit". Zeitfunktionen für technische
Zwecke — Timeouts, Reconnect-Backoff, Heartbeat-Überwachung — bleiben davon
unberührt.

Kommt keine neue Wettkampfzeit an, bleibt im Browser exakt der zuletzt von
WinLaufen gelieferte Wert stehen. Kommt wieder eine an, wird genau dieser neue
Wert angezeigt; es wird nichts nachgeholt.

Das ist ausdrücklich gewollt, denn die laufende Wettkampfzeit ist für den
Sprecher ein **sichtbares End-to-End-Lebenszeichen der fachlichen Kette**
WinLaufen → Bridge → Live Server → Browser. Eine stehende Wettkampfzeit ist die
ehrliche Aussage „hier kommen gerade keine frischen Daten an" — genau daran
wurde der Fehler erkannt, bei dem der Browser trotz verlorener Verbindung
weiterhin `CONNECTED` anzeigte.

Davon strikt getrennt ist das **technische Lebenszeichen** des Live Servers an
den Browser (siehe `docs/PRODUCT_SPEC.md`, Abschnitt Browser-Synchronisation).
Es beantwortet allein „trägt die Verbindung Browser ↔ Live Server?" und darf
weder die Wettkampfzeit noch `SourceHealth` noch Ergebnisdaten verändern.

## Wettkampf-/Ergebnisblock

### Bisher unterstützte Sprecher-PC-Variante

Dokumentierte bzw. beobachtete logische Reihenfolge:

0. String
   Wettkampftyp / Wettkampfabschnitt

1. Integer
   Auswertungsmodus

2. Integer
   Klassenanzahl

3. String[]
   Klassennamen

4. int[]
   Runden oder Mannschaftsgröße

5. Integer
   WinSpringen-Position

6. Integer
   Sprecher-Klassennummer

7. Integer
   Runde / Durchgang

8. Integer
   aktuelle Runde / aktueller Einlauf

9...
   Object[] Ergebniszeilen

gefolgt von:

String "tabelle"

String[] Tabellenheader

String "ende"

### Legacy-Variante: mit WinLaufen 14 real beobachtet am 13.09.2026

Der berichtete vollständige PCAP wurde mit Java `ObjectInputStream`
deserialisiert. Neben der Uhr als `Vector<String>[1]` wurde folgende
Ergebnishülle beobachtet: ein `java.util.Vector` mit **size() == 11**,
unmittelbar gefolgt vom separaten Java-Objekt `String "ende"`.

| Index | Java-Typ | Zuordnung zur vorhandenen Ergebnisstruktur | Beobachteter Wert |
|---|---|---|---|
| 0 | String | Wettkampftyp/-abschnitt | Standardwettkampf |
| 1 | Integer | evaluationMode (Auswertungsmodus) | 2 |
| 2 | Integer | Klassenanzahl | 21 |
| 3 | String[] | Klassennamen | 21 Einträge |
| 4 | int[] | roundsOrTeamSize | 21 Nullen |
| 5 | Integer | winSpringenPosition | 0 |
| 6 | Integer | classIndex (Sprecher-Klassennummer) | 10 |
| 7 | Integer | roundOrHeat | 0 |
| 8 | Integer | currentFinishIndex | 0 |
| 9 | Object[][] | Ergebniszeilen | eine Zeile mit sieben Zellen |
| 10 | String[] | Tabellenheader | sieben Header |

Die Zuordnung von [0] bis [8] folgt der identischen Reihenfolge und den
Java-Typen des bereits dokumentierten Protokolls und des vorhandenen Parsers.
Sie ist keine aus den Beispielzahlen erratene neue Fachsemantik. Insbesondere
wird `2` unverändert als Auswertungsmodus übernommen, ohne diesem Wert eine
neue Bedeutung zuzuweisen. Bestehende Strukturprüfungen, etwa Klassenanzahl,
Arraylängen und nullbasierte Indizes, gelten für beide Hüllen.

Beobachtete Zeile:

```text
Integer 1 | Integer 68 | MÜLLER Rebecca-Anna | SSV-Geyer | SVSAC | 0:14:23.5 | 0:00:00.0
```

Gelieferte Header:

```text
Rang | StNr | Name, Vorname | Verein | Vbd | Laufzeit | Rückstand
```

Der Source-Adapter überführt nur die Hülle in die vorhandene logische
Feldfolge: [1..8], einzelne Zeilen, Tabellenmarker und Header. Integer-Zellen
werden ausschließlich in den ersten beiden Spalten (Rang und Startnummer)
in ihre dezimale String-Darstellung überführt. Bereits vorhandene String-Zellen
bleiben unverändert; andere Zelltypen werden nicht allgemein mit `toString()`
akzeptiert. Der vorhandene Ergebnisparser prüft und erzeugt anschließend
denselben `ResultBlock`; kanonischer Vertrag und Snapshot-Verarbeitung ändern
sich nicht. Der Filter lässt zusätzlich genau den benötigten Typ `Object[][]` zu.

Auf dem Legacy-Wire gibt es an dieser Stelle keinen separaten `tabelle`-Marker.
Das folgende separate `ende` wird konsumiert, bevor der Block veröffentlicht
wird. Ein Socket-Lesetimeout zwischen Vector und Abschluss lässt den Block
weiter auf seinen Abschluss warten. Fehlt `ende` und folgt ein anderes Objekt,
wird der unvollständige Block verworfen und dieses Folgeobjekt normal behandelt.

Der genaue Versionswechsel ist unbekannt; dieses Format wird nicht als
exklusives Merkmal von WinLaufen 14 behauptet. Die neue Test-Fixture bildet die
berichtete deserialisierte Struktur synthetisch nach. Nicht vollständig
mitgeteilte Klassennamen sind ausdrücklich synthetische Platzhalter. Ein
Original-PCAP dieser Sitzung ist damit nicht als Repository-Fixture behauptet.

### Fachlich unbekannte Telegramme und Verbindungslebensdauer

Ein erfolgreich deserialisierter, fachlich unbekannter Vector bzw. eine
`ProtocolException` der fachlichen Strukturprüfung löst keinen TCP-Reconnect
aus und veröffentlicht keine fehlerhaften Ergebnisse. Ein isoliertes `ende`
wird ebenfalls konsumiert. Solche Objekte sind keine Uhr und erneuern den
Heartbeat nicht. Die bisherige Viersekunden-Heartbeat-Überwachung bleibt bestehen.

EOF, Socket-/Transportfehler, beschädigte Java-Serialisierung und Ablehnungen
des restriktiven Deserialisierungsfilters führen weiterhin zum Reconnect.
Pro Verbindung bleibt genau ein `ObjectInputStream` erhalten. Die Regression
prüft Ergebnis-Vector, verzögertes `ende`, unbekannten Vector und anschließende
Legacy-Uhren auf derselben Verbindung sowie Reconnect nach echtem EOF.

## Indexsemantik

Verifiziert:

Sprecher-Klassennummer:
- nullbasierter Index in das Klassenarray.

Aktueller Einlauf:
- nullbasierter Index in die aktuell übertragenen Ergebniszeilen,
- kein Rang.

## Snapshot-Semantik

Verifiziert:

Ergebnisübertragungen enthalten einen vollständigen aktuellen Klassensnapshot.

Sie sind nicht lediglich ein Delta-Ereignis für einen Sportler.

Ein späterer Snapshot darf Zeilen aktualisieren, die zuvor bereits übertragen
wurden.

Der neueste gültige Snapshot ist für diese Klasse autoritativ.

Zeilen und Zellen bleiben in der von WinLaufen gelieferten Reihenfolge und
Textform. Insbesondere bleiben Rang, Startnummer, alle Zeit-/Rückstandsfelder
und Schießwerte Strings; der aktuelle Einlauf bleibt der gelieferte
nullbasierte Zeilenindex. Spätere Werte ersetzen frühere ohne fachlichen
Plausibilitätsvergleich.

## Laufwettkampf

Verifizierter Wettkampftyp:

Standardwettkampf

Beobachteter Auswertungsmodus:

1

Verifizierte Ergebnisheader:

- Rang
- StNr
- Name, Vorname
- Verein
- Vbd
- Laufzeit
- Rückstand

Beispielzeilen aus der Aufzeichnung:

1 | 111 | KREISSL Tobias | SSV Neuhausen | SVS | 2:08:55.9 | 0:00:00.0
2 | 112 | EISENLAUER Sebastian | SC Sonthofen | BSVA | 2:09:08.0 | 0:00:12.1
3 | 113 | TSCHARNKE Tim | SV Biberau | TSV | 2:09:23.7 | 0:00:27.8

Siehe:

testdata/protocol/running/session.pcapng

SHA256:

8599e0dcec5dfcfacb851b40108fae047b84ea524e77fbff320111e7af2cd7ce

## Biathlon

WinLaufen-Oberflächenkonfiguration der aufgezeichneten Demo:

Sportart:
Biathlon

Wettkampftyp:
Standardwettkampf

Auswertung:
nach Altersklassen

Beobachteter Auswertungsmodus:

1

Verifizierte Ergebnisheader:

- Rang
- StNr
- Name, Vorname
- Verein
- Vbd
- Schießen
- Gesamtzeit
- Rückstand

Beispielhafte Schießfelder:

1 0 2 0
0 1 1 2
0 3 0 0
5 5 5 5

Verifiziertes Verhalten:

- Schießwerte sind Bestandteil der Ergebniszeile,
- die alleinige Eingabe von Schießdaten führt im getesteten Workflow zu keiner
  separat sichtbaren Sprecher-PC-Ergebnisaktualisierung,
- die Schießwerte erscheinen gemeinsam mit dem Sportlerergebnis, sobald eine
  Lauf-/Zielzeit vorliegt,
- spätere Klassensnapshots dürfen geänderte Schießwerte für bereits vorhandene
  Sportler enthalten.

Siehe:

testdata/protocol/biathlon/session.pcapng

SHA256:

c43dcf63640de2b55e3f1864afb84dd210b10f740fcc622da5666fdf13397ec5

## Textnachrichten

Die offizielle Protokolldokumentation beschreibt Servernachrichten als
`java.util.Vector` mit einem Nachrichtentext-`String` und dem Marker
`"nachricht"`. Derzeit belegt keine Fixture im Repository eine solche Nachricht.

v0.1 muss diese dokumentierte Vector-Struktur bei der sicheren Deserialisierung
eng begrenzt zulassen und sie konsumieren, ohne die Protokollverbindung zu
zerstören. Die oben beschriebenen Legacy-Uhr- und Ergebnis-Vectoren werden separat erkannt.
Andere Vector-Inhalte werden fachlich abgelehnt, ohne allein deshalb die
TCP-Verbindung zu schließen. Eine weitergehende
Nachrichten-Oberfläche ist nicht erforderlich.

## WinSpringen

Das offizielle Protokoll enthält WinSpringen-spezifische Felder.

WinSpringen wird von v0.1 nicht unterstützt, weil derzeit keine verwendbare
lizenzierte Sprecher-PC-Aufzeichnung vorliegt.

WinSpringen-Verhalten darf nicht aus Lauf- oder Biathlondaten abgeleitet werden.

## Startlisten

Eine künftige WinLaufen-Schnittstelle könnte vollständige Startlistendaten
liefern.

Für dieses Projekt liegt derzeit kein verifiziertes LAN-Wire-Format für
Startlisten vor.

Es darf keines erfunden werden.

## Unbekannt / noch belegbedürftig

Beispiele:

- weitere Auswertungsmodi,
- Staffel-/Mannschaftsspezifische Zeilenformate,
- Verfolgungsspezifisches Verhalten,
- Darstellungen von DNS/DNF/DSQ,
- Snapshot-Verhalten nach erfolgreichem Reconnect,
- künftiger Startlistentransport,
- WinSpringen-spezifische Ergebnisstrukturen.

Die vorhandene reale Biathlon-Aufzeichnung beginnt mitten in einem bereits
etablierten Java-Serialisierungsstream. Sie bleibt gültige aufgezeichnete
Evidenz für das dokumentierte Biathlon-Szenario, einschließlich Zeilenstruktur,
Indizes und Snapshot-Ersetzung. Referenzen, deren ursprüngliche Objekte vor dem
Aufzeichnungsbeginn liegen, lassen sich aus diesem PCAP nicht unabhängig
auflösen; diese Einschränkung ist in der zugehörigen Fixture-Analyse festgehalten
und entwertet die vorhandenen beobachteten Werte nicht.

Synthetische Java-Serialisierungstests auf Basis von `decoded.json` belegen, dass
der Parser die nachgewiesene achtspaltige Struktur akzeptiert, Leerzeichen in
Schießfeldern erhält und vorherige Snapshots ersetzt. Es sind Vertragstests und
keine Behauptung, dass das Midstream-PCAP ein vollständig unabhängig parsbarer
Aufzeichnungsstream ist.

Neues Verhalten muss anhand realer Protokollevidenz dokumentiert werden, bevor
produktive Logik davon abhängt.
