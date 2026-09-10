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

1. **Jede erfolgreiche Antwort enthält die aktuelle Wettkampfzeit**, also den
   zuletzt von WinLaufen gemeldeten Uhrwert, den dieser Live Server kennt —
   auch `/api/v1/startlist`.
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

## Wettkampfzeit und Beobachtungszeitpunkt

Zwei Felder, die nicht verwechselt werden dürfen:

| Feld | Bedeutung |
|---|---|
| `clock` | Die **Wettkampfzeit aus WinLaufen**, unverändert als Zeichenkette durchgereicht, zum Beispiel `"10:14:37"`. Kein Datum, keine Zeitzone, kein Zeitstempel. Keine Stufe der Kette erzeugt, korrigiert oder zählt sie weiter. |
| `clockObservedAt` | Ein gewöhnlicher **UTC-Zeitpunkt dieses Live Servers**, zu dem er den Snapshot mit genau diesem Uhrwert angenommen hat, ISO-8601, zum Beispiel `"2026-09-10T08:14:37.123Z"`. |

`clockObservedAt` beantwortet nur eine Frage: **wie alt** ist der Uhrwert
darüber. Es ist kein Ersatz für die Wettkampfzeit und wird nie in sie
umgerechnet.

Hat dieser Live Server seit seinem Start noch **nie** einen Snapshot erhalten,
sind beide Felder `null`. Eine Wettkampfzeit wird in diesem Fall nicht
erfunden.

## Verbindungsstatus

Jede Antwort enthält ein `connection`-Objekt:

```json
"connection": {
  "status": "CONNECTED",
  "winlaufen": "CONNECTED",
  "bridge": "CONNECTED",
  "fresh": true,
  "stateAvailable": true
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

**Bei einem Abbruch werden Daten nicht verworfen.** Wettkampfzeit, Ergebnisse
und Startliste bleiben als zuletzt bekannter Stand lesbar; der Status sagt
dazu, dass sie nicht mehr aktuell sind. Das entspricht dem Verhalten des Web
Viewers und ist keine Sondersemantik der API.

Ein Hinweis zur Erkennungsdauer: Ein sauber geschlossener oder abgebrochener
Bridge-Ingest wird sofort erkannt. Eine still gestorbene TCP-Verbindung
erkennt erst die WebSocket-Überwachung; bis dahin steht `bridge` weiter auf
`CONNECTED`, während `clockObservedAt` sichtbar altert. Ein Consumer mit
eigenen Anforderungen an die Aktualität sollte deshalb zusätzlich das Alter von
`clockObservedAt` auswerten.

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
  "clock" : "10:14:37",
  "clockObservedAt" : "2026-09-10T08:14:37.123Z",
  "connection" : {
    "status" : "CONNECTED",
    "winlaufen" : "CONNECTED",
    "bridge" : "CONNECTED",
    "fresh" : true,
    "stateAvailable" : true
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
  "clock" : "10:14:37",
  "clockObservedAt" : "2026-09-10T08:14:37.123Z",
  "connection" : {
    "status" : "CONNECTED",
    "winlaufen" : "CONNECTED",
    "bridge" : "CONNECTED",
    "fresh" : true,
    "stateAvailable" : true
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

Sie stellt Daten bereit und interpretiert sie nicht. Ausdrücklich **nicht**
Bestandteil:

- keine Umrechnung der Wettkampfzeit in einen Zeitstempel und keine Zeitzone,
- keine Ableitung einer Zielzeit aus Uhrwert oder Empfangszeit,
- kein Abgleich der Wettkampfzeit gegen eine andere Uhr,
- keine Zusammenführung mit anderen Zeitnahmequellen,
- keine Felder, Namen oder Begriffe eines bestimmten Consumers.

Diese fachliche Auswertung gehört in das jeweilige Zielsystem.
