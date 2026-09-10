# Sprecher-Web — Read API des Live Servers

Der Live Server stellt den aktuellen Wettkampfzustand **maschinenlesbar** über
HTTP bereit. Die API ist bewusst **generisch**: Sie ist auf keinen einzelnen
Consumer zugeschnitten und enthält keine Begriffe eines bestimmten
Zielsystems. Vorgesehen sind unter anderem Overlay- und Timing-Systeme wie
`finish-stream-overlay` und die GFX Engine, Monitoring und eigene
Integrationen.

```text
GET http://<live-server>:44440/api/v1/state       kleiner Zustand, für häufiges Polling
GET http://<live-server>:44440/api/v1/startlist   vollständiger Startlistenbestand
GET http://<live-server>:44440/api/v1/runtime     WebSocket-Endpunkt des Web Viewers
```

## Zwei Zusagen, die für jede Antwort gelten

1. **Jede erfolgreiche Antwort enthält den aktuellen Zeitblock** — die zuletzt
   von WinLaufen gemeldete Wettkampfzeit und die Zeitmessungen beider
   Messstellen —, auch `/api/v1/startlist`.
2. **Jede erfolgreiche Antwort enthält den aktuellen Verbindungs- und
   Freshness-Status der Kette** WinLaufen → Bridge → Live Server.

Der zweite Punkt ist nicht aus dem ersten ableitbar: Eine Wettkampfzeit und
eine Ergebnistabelle sind der zuletzt bekannte Stand und bleiben absichtlich
lesbar, während die Quelle längst weg ist. Ein Consumer darf aus vorhandenen
Daten deshalb **nie** schließen, dass die Kette gerade funktioniert.

## Die API fragt WinLaufen nicht ab

Der Live Server ist ein Präsentationsknoten. Er beantwortet jeden Request aus
dem Zustand, den die Bridge ihm zuletzt veröffentlicht hat.

Pro HTTP-Request gibt es deshalb:

- **keine** Verbindung zu WinLaufen,
- **keine** Rückfrage bei der Bridge,
- **keinen** Dateizugriff.

`GET /api/v1/state` ist damit ausdrücklich für häufiges Polling gedacht und
mehrmals pro Sekunde abrufbar. `GET /api/v1/startlist` liefert je nach
Veranstaltung einige hundert Kilobyte und wird typischerweise selten abgerufen.

## Zeitmodell

Die API führt mehrere **Zeitdomänen** nebeneinander, weil sie verschiedene
Fragen beantworten und nicht ineinander umgerechnet werden dürfen.

| Feld | Beantwortet |
|---|---|
| `time.competitionTime` | Welche Wettkampfzeit meldet WinLaufen? |
| `time.competitionTimeZone` | In welcher Zeitzone wurde diese Tageszeit für die Differenzen unten gelesen? |
| `time.clockChangedAt` | Seit wann hat die Wettkampfzeit diesen Wert? |
| `time.clockSampleRevision` | Wie viele echte Uhrtelegramme hat die Bridge in diesem Lauf verarbeitet? |
| `time.bridge` | Was hat die **Bridge** beim Empfang dieses Telegramms gemessen? |
| `time.liveServer` | Was hat der **Live Server** beim Eintreffen desselben Samples gemessen? |
| `time.apiGeneratedAt` | Wann hat der Live Server diese Antwort erzeugt? |
| `connection.lastUpdateAt` | Wann kam zuletzt irgendein Snapshot der Bridge an? |

### Bridge und Live Server sind Messstellen

Beide erfassen Werte und rechnen unmittelbare Differenzen aus. Sie
**entscheiden nichts**: Sie korrigieren die Wettkampfzeit nicht, wählen keinen
Offset aus, führen keine Kalibrierung durch und bewerten nicht, welche
Messstelle vertrauenswürdiger ist. Beide Messungen stehen nebeneinander; was
daraus folgt, entscheidet der Consumer.

### Clock-Sample: die technische Beobachtung

Ein **Clock-Sample** entsteht bei **jedem** von der Bridge erkannten
WinLaufen-Uhrtelegramm — auch dann, wenn WinLaufen denselben Wert erneut
sendet. Das Protokoll erlaubt gleiche Werte ausdrücklich, und jedes Telegramm
erneuert die Liveness.

Damit trennt die API endlich zwei Dinge, die vorher nicht unterscheidbar waren:

```text
Wettkampfzeit steht fachlich still   ->  clockChangedAt bleibt stehen
Uhrdaten kommen technisch weiter     ->  clockSampleRevision steigt
```

`clockSampleRevision` zählt innerhalb eines Bridge-Laufs und beginnt mit einer
neuen `streamId` erneut, wie jede andere Revision dieses Vertrags.

Ein Snapshot, der **kein** Uhrtelegramm ist — Ergebnisblock, Nachricht,
Health-Wechsel, Präsentationsänderung — führt das bestehende Sample unverändert
mit. Weder `clockSampleRevision` noch eine der beiden Messungen bewegt sich
dabei.

### Was an jeder Messstelle erfasst wird

```json
"bridge": {
  "systemTimeAtReceipt": "2026-09-10T08:12:57.100Z",
  "competitionMinusReferenceMs": 99900,
  "referenceStatus": "UNVERIFIED",
  "referenceSource": "SYSTEM_CLOCK"
}
```

- `systemTimeAtReceipt` — die Ablesung der **Systemuhr dieser Messstelle** in
  dem Moment, in dem sie das Telegramm verarbeitet bzw. empfangen hat. Das ist
  **keine** garantierte UTC: Ein Rechner kann Minuten falsch gehen und trotzdem
  einen plausiblen Zeitstempel liefern. Wie genau er ist, sagt
  `referenceStatus`.
- `competitionMinusReferenceMs` — `Wettkampfzeit − Zeitreferenz dieser
  Messstelle` in Millisekunden, **positiv**, wenn die Wettkampfzeit vorausläuft.
  Im Beispiel oben liegt WinLaufen gegenüber der Bridge-Uhr 99,9 Sekunden
  voraus. Das heißt **nicht**, dass WinLaufen 99,9 Sekunden vor der echten
  Weltzeit liegt — ob die Differenz eine reale Abweichung ist, hängt allein am
  Referenzstatus. `null`, wenn keine Differenz bildbar war.
- `referenceStatus` — `SYNCHRONIZED`, `UNVERIFIED` oder `UNAVAILABLE`.
- `referenceSource` — woher die Zeitstempel stammen, derzeit `SYSTEM_CLOCK`.

`liveServer` hat dieselbe Struktur und misst dasselbe Sample beim Eintreffen.

**Aus der Differenz der beiden `systemTimeAtReceipt` darf keine
Transportlatenz abgeleitet werden.** Solange nicht nachweislich beide
Rechneruhren synchronisiert sind, wird dieser Abstand vom Uhrenversatz der
beiden Maschinen bestimmt und nicht vom Netz. Die API gibt beide Zeitpunkte aus
und zieht selbst keinen solchen Schluss.

### Referenzstatus: `UNVERIFIED` ist der Normalfall

`SYNCHRONIZED` wird nur ausgegeben, wenn das tatsächlich feststellbar ist.
Sprecher-Web besitzt heute keine solche Feststellung: Java bietet keinen
portablen Weg, den Synchronisationszustand der Systemuhr zu erfragen, und aus
„Rechner ist online" oder „läuft unter Linux" folgt er nicht. Deshalb lautet
die Antwort heute überall `UNVERIFIED` — eine ehrliche Aussage, **kein
Fehlerzustand**.

Die Messwerte sind trotzdem vorhanden und nutzbar. Ein Consumer, der eine
verlässliche Zeitbasis braucht, weiß aus dem Status, dass er sich auf die
Differenz nicht blind verlassen darf.

### Zeitreferenz und Verbindung sind unabhängig

Die beiden Dinge haben nichts miteinander zu tun:

```text
connection.status      = CONNECTED     Daten fließen einwandfrei
bridge.referenceStatus = UNVERIFIED    über die Genauigkeit der Uhr ist nichts bekannt
```

Diese Kombination ist der Regelfall und völlig in Ordnung. Umgekehrt kann bei
`WINLAUFEN_DISCONNECTED` das zuletzt gemessene Sample weiterhin sichtbar sein.

### Zeitzone

Die Wettkampfzeit ist eine **Tageszeit ohne Datum**. Um sie von einem Zeitpunkt
abzuziehen, muss bekannt sein, in welcher Zone sie gelesen wird. Diese Zone
steht in jeder Antwort unter `time.competitionTimeZone` und reist im Sample mit,
damit beide Messstellen dieselbe Auslegung verwenden.

Voreingestellt ist die Zone des Bridge-Rechners. Das stimmt, solange die Bridge
in der Zeitzone der Veranstaltung läuft — der Normalfall. **Es stimmt nicht auf
einem Server, der auf UTC steht.** Dort ist die Zone explizit zu setzen:

```sh
java -Dwinlaufen.competition.timezone=Europe/Berlin -jar winlaufen-web-bridge.jar
```

Ein unbekannter Wert wird gemeldet und die Systemzone verwendet.

### Mitternacht

Die Differenz ist ohne Datum nur modulo 24 Stunden definiert. Sie wird deshalb
auf das Intervall **(−12 h, +12 h]** normalisiert. Nur so ergibt der
interessante Fall das Richtige:

```text
competitionTime 00:00:02  gegen  Referenz 23:59:59   ->  +3 000 ms
```

statt knapp minus 24 Stunden. Der Preis ist, dass eine echte Abweichung von
mehr als zwölf Stunden „andersherum" gemeldet würde — eine Fehlkonfiguration
weit außerhalb dessen, wofür diese Messung gedacht ist.

### Auflösung und erreichbare Genauigkeit

Die WinLaufen-Uhr hat **Sekundenauflösung**. Schon deshalb ist die Differenz
nicht genauer als etwa eine Sekunde, unabhängig davon, wie genau die
Systemuhren gehen. Für optische Produktionssysteme wie ein Finish-Overlay ist
eine Größenordnung von etwa 100 ms brauchbar; die API sagt zu, die tatsächlich
gemessenen Werte samt ihrem Status zu liefern, und **garantiert keine
Genauigkeit**.

### Wenn keine verlässliche Referenz vorhanden ist

Dann funktioniert Sprecher-Web unverändert vollständig. Ein Consumer wie
`finish-stream-overlay` kann weiterhin einen **manuell bestimmten Offset**
verwenden, so wie bisher. Das ist kein Fehlerzustand und wird von dieser API
weder verhindert noch ersetzt.

### `clockChangedAt` bleibt daneben bestehen

`clockChangedAt` und das Clock-Sample beantworten verschiedene Fragen und
ersetzen einander nicht:

| | Frage |
|---|---|
| `clockChangedAt` | Seit wann hat die Wettkampfzeit **fachlich** diesen Wert? |
| `time.bridge.systemTimeAtReceipt` | Wann wurde zuletzt **technisch** ein Telegramm beobachtet? |

Das Feld heißt bewusst nicht `clockObservedAt`: Es benennt eine Wertänderung,
keine Beobachtung. **Das Alter von `clockChangedAt` ist kein Freshness-Maß** —
eine Wettkampfzeit darf bei völlig gesunder Quelle stillstehen. Ob die Kette
funktioniert, beantworten `connection.status`, `connection.lastUpdateAt` und
`clockSampleRevision`.

Hat dieser Live Server seit seinem Start noch **nie** einen Snapshot erhalten,
sind alle Zeitfelder bis auf `apiGeneratedAt` `null`. Eine Wettkampfzeit wird
in diesem Fall nicht erfunden.

## Verbindungsstatus

Jede Antwort enthält ein `connection`-Objekt:

```json
"connection": {
  "status": "CONNECTED",
  "winlaufen": "CONNECTED",
  "bridge": "CONNECTED",
  "fresh": true,
  "stateAvailable": true,
  "lastUpdateAt": "2026-09-10T08:14:37.123Z"
}
```

`status` fasst die ganze Kette zu einem Wert zusammen:

| `status` | Bedeutung |
|---|---|
| `CONNECTED` | WinLaufen ist mit der Bridge verbunden **und** die Bridge veröffentlicht an diesen Live Server. Nur dieser Wert bedeutet, dass die Kette gerade gesund ist. |
| `STALE` | Die Bridge ist verbunden und meldet, dass ihre WinLaufen-Quelle verstummt ist — kein Uhrtelegramm länger als das Stale-Fenster des Protokolls. |
| `WINLAUFEN_DISCONNECTED` | Bridge und Live Server arbeiten, die Bridge hat aber aktuell keine Verbindung zu WinLaufen. |
| `BRIDGE_DISCONNECTED` | Dieser Live Server hat aktuell keine Bridge-Ingest-Verbindung. Was WinLaufen tut, ist von hier aus nicht beobachtbar. |
| `NO_STATE` | Seit dem Start dieses Live Servers ist noch kein einziger Snapshot eingetroffen. |

Die Einzelfelder bleiben daneben erhalten, damit erkennbar ist, **wo** die
Kette unterbrochen ist:

- `winlaufen` — der Zustand, den die **Bridge** über ihre eigene Quelle
  gemeldet hat: `CONNECTED`, `STALE` oder `DISCONNECTED`. Solange `bridge`
  nicht `CONNECTED` ist, ist das der **zuletzt bekannte** Wert und kein
  aktueller: Ohne Bridge kann niemand WinLaufen beobachten.
- `bridge` — `CONNECTED`, solange gerade eine Bridge-Ingest-Verbindung zu
  diesem Live Server besteht, sonst `DISCONNECTED`.
- `fresh` — Kurzform für `status == "CONNECTED"`.
- `stateAvailable` — ob überhaupt schon einmal ein Zustand empfangen wurde.
- `lastUpdateAt` — wann zuletzt ein Snapshot der Bridge angenommen wurde,
  unabhängig davon, was er enthielt. Das ist das einzige ehrliche Maß dafür,
  dass noch Daten fließen, denn die Wettkampfzeit darf bei gesunder Quelle
  stillstehen. Der Verlust der Verbindung selbst aktualisiert diesen Wert
  **nicht** — er bleibt beim letzten echten Empfang stehen.

**Bei einem Abbruch werden Daten nicht verworfen.** Wettkampfzeit, Ergebnisse
und Startliste bleiben als zuletzt bekannter Stand lesbar; der Status sagt
dazu, dass sie nicht mehr aktuell sind. Das entspricht dem Verhalten des Web
Viewers und ist keine Sondersemantik der API.

Ein Hinweis zur Erkennungsdauer: Ein sauber geschlossener oder abgebrochener
Bridge-Ingest wird sofort erkannt. Eine still gestorbene TCP-Verbindung
erkennt erst die WebSocket-Überwachung; bis dahin steht `bridge` weiter auf
`CONNECTED`, während `connection.lastUpdateAt` sichtbar altert. Ein Consumer mit
eigenen Anforderungen an die Aktualität wertet deshalb zusätzlich dessen Alter
aus — **nicht** das von `clockChangedAt`, das bei stehender Wettkampfzeit auch
im Normalbetrieb alt wird.

## GET /api/v1/state

Der laufende Zustand: Wettkampfzeit, Verbindungsstatus, Ergebnisse,
Präsentationseinstellungen und die **Metadaten** der Startliste.

Die Startlisteneinträge sind hier bewusst **nicht** enthalten. Dieser Endpunkt
ist für häufiges Polling gedacht; die Teilnehmerliste ändert sich ein paarmal
am Tag und gehört deshalb nach `/api/v1/startlist`.

```json
{
  "apiVersion" : 1,
  "type" : "snapshot",
  "channelId" : "local",
  "streamId" : "b7f1c2",
  "sourceRevision" : 41,
  "publicationRevision" : 1,
  "time" : {
    "competitionTime" : "10:14:37",
    "competitionTimeZone" : "Europe/Berlin",
    "clockChangedAt" : "2026-09-10T08:14:37.150Z",
    "clockSampleRevision" : 1234,
    "bridge" : {
      "systemTimeAtReceipt" : "2026-09-10T08:12:57.100Z",
      "competitionMinusReferenceMs" : 99900,
      "referenceStatus" : "UNVERIFIED",
      "referenceSource" : "SYSTEM_CLOCK"
    },
    "liveServer" : {
      "systemTimeAtReceipt" : "2026-09-10T08:14:37.150Z",
      "competitionMinusReferenceMs" : -150,
      "referenceStatus" : "UNVERIFIED",
      "referenceSource" : "SYSTEM_CLOCK"
    },
    "apiGeneratedAt" : "2026-09-10T08:14:37.480Z"
  },
  "connection" : {
    "status" : "CONNECTED",
    "winlaufen" : "CONNECTED",
    "bridge" : "CONNECTED",
    "fresh" : true,
    "stateAvailable" : true,
    "lastUpdateAt" : "2026-09-10T08:14:37.150Z"
  },
  "startList" : {
    "present" : true,
    "generation" : 2,
    "source" : "IMPORT_CSV",
    "sourceLabel" : "Startliste.csv",
    "entryCount" : 1,
    "classCount" : 1
  },
  "state" : {
    "health" : "CONNECTED",
    "clock" : "10:14:37",
    "competition" : {
      "type" : "Standardwettkampf",
      "evaluationMode" : 1,
      "classCount" : 1,
      "winSpringenPosition" : 0,
      "roundOrHeat" : 0,
      "classes" : [ {
        "index" : 0,
        "name" : "H30",
        "roundsOrTeamSize" : 1,
        "snapshot" : {
          "revision" : 41,
          "headers" : [ "Rang", "StNr", "Name", "Zeit" ],
          "rows" : [ [ "1", "201", "Mustermann, Max", "0:31:12,4" ] ]
        }
      } ]
    },
    "currentFinish" : {
      "classIndex" : 0,
      "rowIndex" : 0,
      "snapshotRevision" : 41
    },
    "message" : null
  },
  "presentation" : {
    "showClub" : true,
    "showAssociation" : true,
    "showNation" : false,
    "showShooting" : true,
    "showPublicMessages" : false
  }
}
```

Zu den Feldern:

- `apiVersion` — Version dieses Antwortformats. Nicht die Produktversion und
  nicht die Schemaversion des Bridge-Contracts.
- `streamId` — kennzeichnet den Bridge-Lauf, aus dem der Zustand stammt. Nach
  einem Bridge-Neustart ist es ein anderer Wert. `null`, solange nie ein
  Snapshot ankam.
- `sourceRevision` — Revision der Bridge, `publicationRevision` die des Live
  Servers. Letztere gilt nur für die Laufzeit dieses Prozesses und beginnt nach
  einem Neustart wieder bei 0. Die beiden werden nie miteinander verglichen.
- `state.competition` — der **aktuelle vollständige Ergebnisstand**, kein
  Ereignisprotokoll. Jeder Abruf liefert den Stand von jetzt; zwischen zwei
  Abrufen können sich vorhandene Zeilen ändern, weil WinLaufen vollständige
  Klassen-Snapshots liefert. `null` bedeutet „von der Quelle noch nie
  empfangen" und nicht „die Quelle meldet nichts".
- `state.currentFinish` — Klassen- und Zeilenindex des aktuellen Einlaufs im
  Snapshot dieser Klasse. Kein Rang und keine Zeit.
- `state.health` — die browserseitige Quellengesundheit. Für eine Diagnose der
  Kette ist `connection` maßgeblich: `state.health` wird beim Verlust der
  Bridge-Verbindung bewusst auf `DISCONNECTED` abgewertet und unterscheidet
  deshalb nicht zwischen „WinLaufen weg" und „Bridge weg".

## GET /api/v1/startlist

Der vollständige aktuelle Startlistenbestand — **mit der Wettkampfzeit und dem
Verbindungsstatus dieses Requests**, nicht mit denen des Zeitpunkts, zu dem die
Startliste veröffentlicht wurde.

```json
{
  "apiVersion" : 1,
  "type" : "startlist",
  "channelId" : "local",
  "streamId" : "b7f1c2",
  "time" : {
    "competitionTime" : "10:14:37",
    "competitionTimeZone" : "Europe/Berlin",
    "clockChangedAt" : "2026-09-10T08:14:37.150Z",
    "clockSampleRevision" : 1234,
    "bridge" : {
      "systemTimeAtReceipt" : "2026-09-10T08:12:57.100Z",
      "competitionMinusReferenceMs" : 99900,
      "referenceStatus" : "UNVERIFIED",
      "referenceSource" : "SYSTEM_CLOCK"
    },
    "liveServer" : {
      "systemTimeAtReceipt" : "2026-09-10T08:14:37.150Z",
      "competitionMinusReferenceMs" : -150,
      "referenceStatus" : "UNVERIFIED",
      "referenceSource" : "SYSTEM_CLOCK"
    },
    "apiGeneratedAt" : "2026-09-10T08:14:37.480Z"
  },
  "connection" : {
    "status" : "CONNECTED",
    "winlaufen" : "CONNECTED",
    "bridge" : "CONNECTED",
    "fresh" : true,
    "stateAvailable" : true,
    "lastUpdateAt" : "2026-09-10T08:14:37.150Z"
  },
  "present" : true,
  "generation" : 2,
  "source" : "IMPORT_CSV",
  "sourceLabel" : "Startliste.csv",
  "entryCount" : 1,
  "classCount" : 1,
  "entries" : [ {
    "bib" : "201",
    "className" : "H30",
    "startTime" : "09:30:00",
    "lastName" : "Mustermann",
    "firstName" : "Max",
    "club" : "SV Beispiel",
    "association" : "SVS",
    "course" : "10 km",
    "birthYear" : "1990",
    "gender" : "M",
    "nation" : "GER"
  } ]
}
```

Zu den Feldern:

- `generation` — der Importzähler der Bridge. Er versioniert **nur** diesen
  Bestand: keine Wettkampf-, Lauf- oder Teilnehmerkennung. Er ist ausschließlich
  innerhalb derselben `streamId` vergleichbar; eine neu gestartete Bridge darf
  wieder bei 1 beginnen.
- `present` ist `false` und `generation` ist `0`, wenn die Bridge keine
  Startliste hat. `entries` ist dann eine leere Liste — eine klare Aussage, kein
  Fehler. `source` und `sourceLabel` sind in diesem Fall `null`: Die Bridge muss
  diese Felder auch dann füllen, wenn sie „ich habe keine Startliste" meldet, und
  dieser Platzhalter würde sonst ein Dateiformat und eine Herkunft für einen
  Bestand nennen, den es nicht gibt.
- `entries` — die Reihenfolge ist exakt die der veröffentlichten Liste, also die
  des WinLaufen-Exports. Es wird **nicht** sortiert und **nicht** gruppiert.
- Es gibt **keine Teilnehmer-ID**. Die Quelle liefert keine, also erfindet keine
  Stufe eine. Eindeutig innerhalb eines Bestands ist das Paar
  `(className, bib)`; dieselbe Startnummer in verschiedenen Klassen ist erlaubt.
- `bib` ist eine Zeichenkette. `0012`, `12` und `A12` sind verschiedene
  Startnummern und dürfen nicht in Zahlen umgewandelt werden.
- Alle Einträge sind Zeichenketten und werden unverändert durchgereicht,
  einschließlich `startTime`.

## Caching

Beide Endpunkte antworten mit:

```text
Content-Type: application/json; charset=utf-8
Cache-Control: no-store
```

Das ist notwendig, weil jede Antwort eine laufende Wettkampfzeit trägt: Eine
zwischengespeicherte Antwort würde einem Consumer eine alte Uhr liefern, ohne
dass er es merkt.

**Kein ETag, kein `304 Not Modified`.** Für `/api/v1/startlist` wäre ein ETag
aus `streamId` und `generation` naheliegend, denn der Bestand ändert sich
selten. Genau das wäre hier aber falsch: Die Startliste kann unverändert sein,
während die Wettkampfzeit weitergelaufen ist. Ein `304` würde die Antwort
komplett unterdrücken und dem Consumer damit die aktuelle Uhr und den aktuellen
Verbindungsstatus vorenthalten — also genau das, was diese API zusagt. Wer nur
prüfen will, ob sich der Bestand geändert hat, liest `generation` aus dem
kleinen `/api/v1/state`.

## Sicherheit

Die API ist **read-only**. Es gibt keinen Endpunkt, der etwas ändert: keine
Konfiguration, keinen Startlistenimport, keine Bridge-Steuerung. Andere
Methoden als `GET` werden mit `405` abgewiesen.

Sie ist — wie der Web Viewer auf demselben Port — **nicht authentifiziert** und
folgt damit der bestehenden Sicherheitsrichtlinie des Live Servers. Es gilt
unverändert, was in README.md unter „Known prototype security limitation" steht:
Port 44440 gehört in ein kontrolliertes Netz oder auf einen bewusst öffentlich
betriebenen Presentation Node, und die dort angezeigten Daten sind ohnehin die
öffentlichen Wettkampfdaten.

## Was diese API nicht tut

Sie stellt Messwerte bereit und interpretiert sie nicht. Ausdrücklich **nicht**
Bestandteil:

- **keine Korrektur** der Wettkampfzeit,
- **keine Auswahl** eines Offsets — es gibt kein `effectiveOffset`, kein
  `selectedOffset`, kein `correctedCompetitionTime`, kein `calibratedClock`,
  kein `bestReference` und keine Empfehlung,
- keine Entscheidung, ob die Bridge- oder die Live-Server-Messung besser ist,
- **keine Zusage über UTC-Genauigkeit**,
- keine ClockCalibration und keine Driftkorrektur,
- keine Ableitung einer Zielzeit aus Uhrwert oder Empfangszeit,
- keine abgeleitete Transportlatenz aus den beiden Empfangszeitpunkten,
- keine Zusammenführung mit anderen Zeitnahmequellen,
- **kein Ersatz für einen manuellen Offset** in einem Consumer,
- keine Felder, Namen oder Begriffe eines bestimmten Consumers.

Diese fachliche Auswertung gehört in das jeweilige Zielsystem.
