# Sprecher-Web — Schnittstellenreferenz

Dieses Dokument beschreibt **alle** öffentlichen und administrativen
Schnittstellen von Sprecher-Web: die generische Read API des Live Servers für
externe Consumer, die Browser-Schnittstellen des Web Viewers, den
Bridge-Ingest und die Bridge-Control-API.

Andere Dokumente: [README](../README.md) für den Überblick,
[INSTALLATION.md](INSTALLATION.md) für Installation und Konfiguration,
[BEDIENERHANDBUCH.md](BEDIENERHANDBUCH.md) für die Bedienung,
[MODULAR_ARCHITECTURE.md](MODULAR_ARCHITECTURE.md) für die verbindlichen
Architekturentscheidungen.

## 1. Überblick

Zwei Prozesse bieten Schnittstellen an:

```text
WinLaufen ──TCP 4444, read-only──▶ Bridge ──WebSocket──▶ Live Server ──▶ Browser
                                     │                        │
                                Bridge Control            Read API
                                (administrativ)        (externe Consumer)
```

| Schnittstelle | Zielgruppe |
|---|---|
| **Read API** des Live Servers | externe Consumer: Overlay- und Timing-Systeme wie `finish-stream-overlay` und die GFX Engine, Monitoring, eigene Integrationen |
| **Web Viewer** und Browser-WebSocket | Zuschauer im Browser |
| **Bridge-Ingest** | ausschließlich die Bridge |
| **Bridge Control** | der Veranstalter im vertrauenswürdigen LAN |

Die Read API ist bewusst **generisch**. Sie enthält keine Felder, Namen oder
Begriffe eines bestimmten Zielsystems, und die fachliche Auswertung findet im
jeweiligen Consumer statt.

## 2. Ports und Endpunkte

| Port | Komponente | Richtung | Zweck | Erreichbarkeit |
|---|---|---|---|---|
| **4444** | WinLaufen | Bridge → WinLaufen, **ausgehend** | Sprecher-PC-Schnittstelle, read-only | keine eingehende Freigabe dieses Projekts |
| **44440** | Live Server | eingehend | Web Viewer und Read API über HTTP | für die vorgesehenen Zuschauer- und Consumer-Geräte |
| **44441** | Live Server | eingehend | Browser-WebSocket **und** Bridge-Ingest auf getrennten Pfaden | für Zuschauergeräte und die Bridge |
| **44442** | Bridge | eingehend | Bridge Control | nur vertrauenswürdiges LAN, **nie** öffentlich |

Vollständige Endpunktmatrix:

| Komponente | Protokoll | Methode | Endpunkt | Zweck | Zielgruppe |
|---|---|---|---|---|---|
| Live Server | HTTP 44440 | GET | `/api/v1/state` | laufender Zustand, klein und pollbar | externe Consumer, Viewer-Start |
| Live Server | HTTP 44440 | GET | `/api/v1/startlist` | vollständiger Startlistenbestand | externe Consumer |
| Live Server | HTTP 44440 | GET | `/api/v1/runtime` | Laufzeitkonfiguration des Web Viewers (nennt Port und Pfad des Browser-WebSockets) | Web Viewer |
| Live Server | HTTP 44440 | GET | `/`, `/viewer` | HTML des Web Viewers | Browser |
| Live Server | HTTP 44440 | GET | `/renderer` | Weiterleitung auf `/` | alte Lesezeichen |
| Live Server | HTTP 44440 | GET | `/assets/viewer.css`, `/assets/viewer.js` | Viewer-Ressourcen | Browser |
| Live Server | WebSocket 44441 | — | `/live/v1` | Live-Nachrichten an Browser | Browser |
| Live Server | WebSocket 44441 | — | `/bridge/v1/channels/<channel>` | authentifizierter Ingest | ausschließlich die Bridge |
| Bridge Control | HTTP 44442 | GET | `/` | Oberfläche | Veranstalter |
| Bridge Control | HTTP 44442 | GET | `/assets/control.css`, `/assets/control.js` | Oberflächen-Ressourcen | Veranstalter |
| Bridge Control | HTTP 44442 | GET | `/api/v1/config` | aktuelle Konfiguration | Veranstalter |
| Bridge Control | HTTP 44442 | GET | `/api/v1/status` | Laufzeitzustand von Quelle, Targets, Startliste | Veranstalter |
| Bridge Control | HTTP 44442 | POST | `/api/v1/config` | Konfiguration speichern | Veranstalter |
| Bridge Control | HTTP 44442 | POST | `/api/v1/startlist?name=<datei>` | Startliste importieren | Veranstalter |

Gezählt sind das **14 Pfade** (je Komponente; `/`, `/api/v1/startlist` und
`/api/v1/config` kommen in beiden Komponenten oder mit beiden Methoden vor),
**15 HTTP-Operationen** aus Methode und Pfad — `/api/v1/config` beantwortet `GET`
und `POST` — und **2 WebSocket-Endpunkte**. Weitere öffentliche Routen gibt es
nicht; alles andere antwortet mit `404`.

## 3. Grundsatz: messen, nicht entscheiden

Bridge und Live Server sind **Messstellen**. Sie erfassen Zeitwerte,
Empfangszeitpunkte und unmittelbar messbare Differenzen, benennen den Status
ihrer Zeitreferenz und liefern Verbindungs- und Freshness-Informationen.

Sie **korrigieren keine Wettkampfzeit**, wählen keinen Offset, bestimmen keinen
„besten" Offset, führen keine ClockCalibration durch, korrigieren keinen Drift,
interpolieren keine Wettkampfzeit und leiten keine Zielzeit ab. Diese
Entscheidungen trifft der Consumer.

### Genauigkeit ist ein Architekturziel, keine Zusage

> Sprecher-Web ist kein hochpräzises Zeitmess-, PTP- oder
> Broadcast-Timecode-System. Für die vorgesehenen On-Screen- und
> Broadcast-Anwendungen wird eine zeitliche Zuordnungsgenauigkeit in der
> Größenordnung von etwa 100 ms angestrebt. Rohzeitstempel werden trotzdem mit
> der jeweils verfügbaren höheren Auflösung erfasst und unverändert
> bereitgestellt. Zusätzliche Komplexität für deutlich höhere Präzision wird nur
> eingeführt, wenn sie für den Anwendungsfall tatsächlich erforderlich ist.

Das ist eine **Architektur- und Designgrenze**, keine garantierte Eigenschaft
einer einzelnen API-Antwort. WinLaufen liefert Uhrtelegramme mit
**Sekundenauflösung** (`HH:MM:SS`); ein einzelnes Sample kann deshalb
grundsätzlich keine 100-ms-genaue absolute Kalibrierung tragen.

### Offline ist der Normalfall

Sprecher-Web funktioniert vollständig in einem vom Internet getrennten lokalen
Netz. Dann gilt an beiden Messstellen `referenceStatus = UNVERIFIED`. **Das ist
kein Funktionsfehler**, sondern die ehrliche Aussage, dass die Genauigkeit der
Systemuhr nicht belegt werden kann. Es gibt keine NTP- und keine Internetpflicht
in dieser Architektur.

Ein Consumer wie `finish-stream-overlay` kann in diesem Fall weiterhin einen
**manuell bestimmten Zeitkorrekturwert** verwenden, so wie bisher. Sprecher-Web
ersetzt ihn nicht, blockiert ihn nicht und entscheidet nicht darüber.

## 4. Zwei Zusagen der Read API

1. **Jede erfolgreiche Antwort enthält den aktuellen Zeitblock** — die zuletzt
   von WinLaufen gemeldete Wettkampfzeit und die Zeitmessungen beider
   Messstellen —, auch `/api/v1/startlist`.
2. **Jede erfolgreiche Antwort enthält den aktuellen Verbindungs- und
   Freshness-Status der Kette** WinLaufen → Bridge → Live Server.

Der zweite Punkt ist aus dem ersten nicht ableitbar: Eine Wettkampfzeit und eine
Ergebnistabelle sind der zuletzt bekannte Stand und bleiben absichtlich lesbar,
während die Quelle längst weg ist.

### Die API fragt WinLaufen nicht ab

Der Live Server ist ein Präsentationsknoten. Er beantwortet jeden Request aus
dem Zustand, den die Bridge ihm zuletzt veröffentlicht hat. Pro HTTP-Request
gibt es **keine** Verbindung zu WinLaufen, **keine** Rückfrage bei der Bridge und
**keinen** Dateizugriff.

`GET /api/v1/state` ist damit ausdrücklich für häufiges Polling gedacht und
mehrmals pro Sekunde abrufbar. `GET /api/v1/startlist` liefert je nach
Veranstaltung einige hundert Kilobyte und wird typischerweise selten abgerufen.

## 5. Zeitmodell

Die API führt mehrere **Zeitdomänen** nebeneinander, weil sie verschiedene
Fragen beantworten und nicht ineinander umgerechnet werden dürfen.

| Feld | Beantwortet |
|---|---|
| `time.competitionTime` | Welche Wettkampfzeit meldet WinLaufen? |
| `time.competitionTimeZone` | In welcher Zeitzone wurde diese Tageszeit für die Differenzen unten gelesen? |
| `time.competitionTimeZoneSource` | Wurde diese Zone ausdrücklich konfiguriert oder nur vom System übernommen? |
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

```text
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
  `null`, wenn keine Differenz bildbar war. Was dieser Wert bedeutet, steht
  unten unter [Messdifferenz, kein Uhrenfehler](#messdifferenz-kein-uhrenfehler)
  — er ist bewusst weniger, als er auf den ersten Blick aussieht.
- `referenceStatus` — `SYNCHRONIZED`, `UNVERIFIED` oder `UNAVAILABLE`.
- `referenceSource` — woher die Zeitstempel stammen, derzeit `SYSTEM_CLOCK`.

`liveServer` hat dieselbe Struktur und misst dasselbe Sample beim Eintreffen.

### Messdifferenz, kein Uhrenfehler

`competitionMinusReferenceMs` ist **eine unmittelbar gemessene Differenz** und
sonst nichts:

> Der im konkreten WinLaufen-Uhrtelegramm enthaltene Wettkampfzeitwert gegenüber
> der Referenz-/Systemzeit dieser Messstelle beim Empfang genau dieses Samples.

Ein Beispiel: `competitionMinusReferenceMs = +99 900` bedeutet ausschließlich,
dass der Wert in diesem Telegramm gegenüber der Referenzzeit dieser Messstelle
beim Empfang um rund 99,9 Sekunden vorauslag.

Es ist **nicht**:

- ein automatisch bestimmter Uhrenfehler des WinLaufen-PCs,
- ein kalibrierter Offset,
- eine garantierte Abweichung zur Weltzeit,
- eine Netzwerk- oder Transportlatenz,
- ein Wert, den ein Consumer ungeprüft als Korrektur anwenden sollte.

Der Grund ist nicht Vorsicht, sondern fehlende Information. Unbekannt bleibt:

- wann innerhalb der laufenden Sekunde WinLaufen den Wert intern umschaltet,
- wann daraus ein Telegramm gebaut und gesendet wird,
- welche Verarbeitungs- und Übertragungsverzögerung davor liegt.

Ob aus der Differenz auf eine reale Abweichung zur Weltzeit geschlossen werden
darf, hängt zusätzlich an `referenceStatus`, `referenceSource`, der Herkunft der
Wettkampf-Zeitzone und dem Übertragungsverhalten von WinLaufen. Diese Bewertung
findet in Bridge und Live Server **nicht** statt.

Eine einzelne Messung taugt deshalb nicht als absolute Kalibrierung. Ein
Consumer, der mehrere Samples über die Zeit betrachtet, kann daraus ein Muster
ableiten — diese Auswertung gehört in das Zielsystem und ausdrücklich nicht
hierher.

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
Differenz nicht blind verlassen darf, und kann stattdessen mit einem eigenen
Referenzwert arbeiten — siehe [Offline ist der Normalfall](#offline-ist-der-normalfall).

### Zeitreferenz und Verbindung sind unabhängig

Die beiden Dinge haben nichts miteinander zu tun:

```text
connection.status      = CONNECTED     Daten fließen einwandfrei
bridge.referenceStatus = UNVERIFIED    über die Genauigkeit der Uhr ist nichts bekannt
```

Diese Kombination ist der Regelfall und völlig in Ordnung. Umgekehrt kann bei
`WINLAUFEN_DISCONNECTED` das zuletzt gemessene Sample weiterhin sichtbar sein.

### Wettkampf-Zeitzone und ihre Herkunft

Die Wettkampfzeit ist eine **Tageszeit ohne Datum**. Um sie von einem Zeitpunkt
abzuziehen, muss bekannt sein, in welcher Zone sie gelesen wird. Diese Zone steht
in jeder Antwort unter `time.competitionTimeZone` und **reist im Clock-Sample
mit**, damit beide Messstellen dieselbe Auslegung verwenden. Ein Live Server auf
einem UTC-Rechner deutet eine in Ortszeit gelesene Wettkampfzeit deshalb nicht
still um.

`time.competitionTimeZoneSource` sagt, **wie** diese Zone zustande kam:

| Wert | Bedeutung |
|---|---|
| `CONFIGURED` | Die Zone wurde für diese Bridge ausdrücklich festgelegt. Jemand hat sich entschieden. |
| `SYSTEM_DEFAULT` | Es wurde nichts konfiguriert, also gilt die Zone des Rechners, auf dem die Bridge läuft. |

**`SYSTEM_DEFAULT` heißt nicht, dass die Zone fachlich richtig ist.** Es heißt
nur, dass keine konfiguriert wurde. Das trifft zu, solange die Bridge in der
Zeitzone der Veranstaltung läuft — der Normalfall — und ist genau dort falsch, wo
es leicht übersehen wird: auf einem Linux-Server, dessen Systemzone UTC ist.
Jede Differenz wäre dann um den vollen UTC-Versatz daneben, ohne dass irgendetwas
ungewöhnlich aussieht.

Konfiguriert wird sie in `bridge.properties`:

```properties
competition.timezone=Europe/Berlin
```

Für Veranstaltungen in Deutschland sollte dieser Eintrag gesetzt werden,
insbesondere wenn die Bridge auf einem Linux-System mit Systemzone UTC läuft.
Der Eintrag überlebt ein Speichern in Bridge Control, obwohl die Oberfläche kein
Feld dafür hat.

Alternativ und nachrangig wirkt weiterhin die Systemproperty
`-Dwinlaufen.competition.timezone=Europe/Berlin`; sie greift nur, wenn die
Konfigurationsdatei nichts sagt. Ein unbekannter Wert wird beim Start gemeldet
und wie „nicht konfiguriert" behandelt — die Bridge startet trotzdem, denn eine
falsche Zone betrifft nur eine Messung, die dann ohnehin als Rückfall markiert
ist.

### Zeitzonenherkunft und Uhrqualität sind zwei Dinge

`competitionTimeZoneSource` und `referenceStatus` beantworten verschiedene
Fragen und dürfen nicht vermischt werden:

```text
competitionTimeZoneSource = CONFIGURED     jemand hat die Wettkampfzone festgelegt
bridge.referenceStatus    = UNVERIFIED     über die Genauigkeit der Bridge-Uhr ist nichts bekannt
```

```text
competitionTimeZoneSource = SYSTEM_DEFAULT gut möglich, dass die Zone passt — bestätigt hat es niemand
bridge.referenceStatus    = SYNCHRONIZED   die Uhr geht nachweislich richtig
```

Beide Kombinationen sind möglich und sagen jeweils nichts über die andere Frage
aus.

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

### Auflösung

Die WinLaufen-Uhr hat **Sekundenauflösung**. Die Differenz ist deshalb nicht
genauer als diese Auflösung, unabhängig davon, wie genau die Systemuhren gehen.
Wofür das reicht und wofür nicht, steht unter
[Genauigkeit ist ein Architekturziel, keine Zusage](#genauigkeit-ist-ein-architekturziel-keine-zusage).

### `clockChangedAt` bleibt daneben bestehen

`clockChangedAt` und das Clock-Sample beantworten verschiedene Fragen und
ersetzen einander nicht:

| | Frage |
|---|---|
| `clockChangedAt` | Wann hat Sprecher-Web erstmals ein Sample mit **diesem** Wettkampfzeitwert gesehen? |
| `time.bridge.systemTimeAtReceipt` | Wann wurde zuletzt **technisch** ein Telegramm beobachtet? |

Genauer gesagt ist `clockChangedAt` der Zeitpunkt, zu dem der Live Server
erstmals ein Clock-Sample mit einem gegenüber dem vorherigen bekannten Sample
**geänderten** Wettkampfzeitwert erkannt hat. Es ist bewusst nicht formuliert als
„seit wann hat die Wettkampfzeit diesen Wert", denn der interne Umschaltmoment
innerhalb von WinLaufen ist von außen nicht sichtbar. Was hier steht, ist die
beobachtete Wertänderung im Sprecher-Web-Datenpfad — nicht mehr.

Das Feld heißt deshalb auch nicht `clockObservedAt`: Es benennt eine
Wertänderung, keine Beobachtung der Quelle. Es ist ausdrücklich:

- **kein Freshness-Maß** — eine Wettkampfzeit darf bei völlig gesunder Quelle
  stillstehen. Ob die Kette funktioniert, beantworten `connection.status`,
  `connection.lastUpdateAt` und `clockSampleRevision`;
- **kein Beobachtungszeitpunkt der Quelle**;
- **kein Kalibrierungsanker mit zugesagter Genauigkeit** — er liegt näher am
  tatsächlichen Umschalten als jede spätere Wiederholung desselben Werts, aber
  wie nah, sagt niemand zu.

Hat dieser Live Server seit seinem Start noch **nie** einen Snapshot erhalten,
sind alle Zeitfelder bis auf `apiGeneratedAt` `null`. Eine Wettkampfzeit wird
in diesem Fall nicht erfunden.

## 6. Verbindung und Freshness

Jede Antwort enthält ein `connection`-Objekt:

```text
"connection": {
  "status": "CONNECTED",
  "winlaufen": "CONNECTED",
  "bridge": "CONNECTED",
  "fresh": true,
  "stateAvailable": true,
  "lastUpdateAt": "2026-09-10T08:14:37.150Z"
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

### Beispiele

**WinLaufen getrennt, Bridge liefert weiter.** Der letzte Stand bleibt lesbar,
und der Status sagt, dass er nicht mehr aktuell ist:

```json
{
  "time" : {
    "competitionTime" : "10:14:37",
    "competitionTimeZone" : "Europe/Berlin",
    "competitionTimeZoneSource" : "CONFIGURED",
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
    "apiGeneratedAt" : "2026-09-10T08:16:00Z"
  },
  "connection" : {
    "status" : "WINLAUFEN_DISCONNECTED",
    "winlaufen" : "DISCONNECTED",
    "bridge" : "CONNECTED",
    "fresh" : false,
    "stateAvailable" : true,
    "lastUpdateAt" : "2026-09-10T08:14:37.150Z"
  }
}
```

**Bridge getrennt.** Was WinLaufen tut, ist von hier aus nicht mehr beobachtbar;
`winlaufen` ist der zuletzt bekannte Wert:

```json
{
  "time" : {
    "competitionTime" : "10:14:37",
    "competitionTimeZone" : "Europe/Berlin",
    "competitionTimeZoneSource" : "CONFIGURED",
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
    "apiGeneratedAt" : "2026-09-10T08:17:00Z"
  },
  "connection" : {
    "status" : "BRIDGE_DISCONNECTED",
    "winlaufen" : "DISCONNECTED",
    "bridge" : "DISCONNECTED",
    "fresh" : false,
    "stateAvailable" : true,
    "lastUpdateAt" : "2026-09-10T08:14:37.150Z"
  }
}
```

**Noch nie ein Zustand empfangen.** Es wird nichts erfunden; nur
`apiGeneratedAt` ist beantwortbar:

```json
{
  "time" : {
    "competitionTime" : null,
    "competitionTimeZone" : null,
    "competitionTimeZoneSource" : null,
    "clockChangedAt" : null,
    "clockSampleRevision" : null,
    "bridge" : null,
    "liveServer" : null,
    "apiGeneratedAt" : "2026-09-10T08:00:00Z"
  },
  "connection" : {
    "status" : "NO_STATE",
    "winlaufen" : "DISCONNECTED",
    "bridge" : "DISCONNECTED",
    "fresh" : false,
    "stateAvailable" : false,
    "lastUpdateAt" : null
  }
}
```

Ein Hinweis zur Erkennungsdauer: Ein sauber geschlossener oder abgebrochener
Bridge-Ingest wird sofort erkannt. Eine still gestorbene TCP-Verbindung
erkennt erst die WebSocket-Überwachung; bis dahin steht `bridge` weiter auf
`CONNECTED`, während `connection.lastUpdateAt` sichtbar altert. Ein Consumer mit
eigenen Anforderungen an die Aktualität wertet deshalb zusätzlich dessen Alter
aus — **nicht** das von `clockChangedAt`, das bei stehender Wettkampfzeit auch
im Normalbetrieb alt wird.

## 7. GET /api/v1/state

Der laufende Zustand für externe Consumer: Zeitblock, Verbindungsstatus,
Ergebnisse, Präsentationseinstellungen und die **Metadaten** der Startliste.

Die Startlisteneinträge sind hier bewusst **nicht** enthalten. Dieser Endpunkt
ist für häufiges Polling gedacht; die Teilnehmerliste ändert sich ein paarmal am
Tag und gehört deshalb nach [`/api/v1/startlist`](#8-get-apiv1startlist).

Antwort mit `200`, `Content-Type: application/json; charset=utf-8`,
`Cache-Control: no-store`.

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
    "competitionTimeZoneSource" : "CONFIGURED",
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

### Kopffelder

| Feld | Bedeutung |
|---|---|
| `apiVersion` | Version dieses Antwortformats, aktuell `1`. Nicht die Produktversion und nicht die Schemaversion des Bridge-Contracts. |
| `type` | `"snapshot"`. Dieselbe Kennung wie in der Browsernachricht, damit ein Consumer beide Wege gleich behandeln kann. |
| `channelId` | Kanal dieses Live Servers, üblicherweise `"local"`. |
| `streamId` | Kennzeichnet den Bridge-Lauf, aus dem der Zustand stammt. Nach einem Bridge-Neustart ein anderer Wert. `null`, solange nie ein Snapshot ankam. |
| `sourceRevision` | Revision der Bridge zu diesem Zustand. `-1`, solange nie ein Snapshot ankam. |
| `publicationRevision` | Zählung dieses Live Servers, gültig nur für die Laufzeit dieses Prozesses; nach einem Neustart beginnt sie wieder bei 0. Wird **nie** mit `sourceRevision` verglichen. |

### `time`

Vollständig beschrieben unter [Zeitmodell](#5-zeitmodell).

| Feld | Bedeutung |
|---|---|
| `competitionTime` | Wettkampfzeit aus WinLaufen, unveränderte Zeichenkette. `null`, solange keine ankam. |
| `competitionTimeZone` | Zone, in der diese Tageszeit für die Differenzen gelesen wurde. |
| `competitionTimeZoneSource` | `CONFIGURED` oder `SYSTEM_DEFAULT`. |
| `clockChangedAt` | Wann erstmals ein Sample mit **diesem** Wert erkannt wurde, ISO-8601 UTC. |
| `clockSampleRevision` | Anzahl der von der Bridge verarbeiteten Uhrtelegramme in diesem Bridge-Lauf. |
| `bridge` | Messung der Bridge: `systemTimeAtReceipt`, `competitionMinusReferenceMs`, `referenceStatus`, `referenceSource`. `null`, solange kein Sample vorliegt. |
| `liveServer` | Dieselbe Messung dieses Live Servers für dasselbe Sample. `null`, solange keines gemessen wurde. |
| `apiGeneratedAt` | Systemzeit dieses Live Servers beim Erzeugen genau dieser Antwort. Immer gesetzt. |

### `connection`

Vollständig beschrieben unter [Verbindung und Freshness](#6-verbindung-und-freshness).

| Feld | Bedeutung |
|---|---|
| `status` | `CONNECTED`, `STALE`, `WINLAUFEN_DISCONNECTED`, `BRIDGE_DISCONNECTED` oder `NO_STATE`. |
| `winlaufen` | Was die **Bridge** über ihre Quelle gemeldet hat: `CONNECTED`, `STALE` oder `DISCONNECTED`. Ohne Bridge-Verbindung der zuletzt bekannte Wert. |
| `bridge` | `CONNECTED`, solange eine Bridge-Ingest-Verbindung besteht, sonst `DISCONNECTED`. |
| `fresh` | Kurzform für `status == "CONNECTED"`. |
| `stateAvailable` | Ob überhaupt schon einmal ein Zustand empfangen wurde. |
| `lastUpdateAt` | Wann zuletzt ein Snapshot der Bridge angenommen wurde. `null`, solange keiner ankam. |

### `startList` — nur Metadaten

| Feld | Bedeutung |
|---|---|
| `present` | Ob überhaupt eine Startliste vorliegt. Gleichbedeutend mit `generation > 0`. |
| `generation` | Importzähler der Bridge, nur innerhalb derselben `streamId` vergleichbar. `0`, wenn keine Liste vorliegt. |
| `source` | `IMPORT_CSV` oder `IMPORT_XLSX`. `null`, wenn keine Liste vorliegt. |
| `sourceLabel` | Dateiname des Imports, nur der letzte Pfadbestandteil. `null`, wenn keine Liste vorliegt. |
| `entryCount` | Anzahl der Teilnehmer. |
| `classCount` | Anzahl verschiedener Klassen. |

`source` und `sourceLabel` sind bei `present: false` bewusst `null`: Die Bridge
muss diese Felder auch dann füllen, wenn sie „ich habe keine Startliste" meldet,
und dieser Platzhalter würde sonst Herkunft und Format eines Bestands nennen,
den es nicht gibt.

### `state` — der Wettkampfstand

| Feld | Bedeutung |
|---|---|
| `health` | Browserseitige Quellengesundheit. Für eine Diagnose der Kette ist `connection` maßgeblich: Dieser Wert wird beim Verlust der Bridge-Verbindung bewusst auf `DISCONNECTED` abgewertet und unterscheidet deshalb nicht zwischen „WinLaufen weg" und „Bridge weg". |
| `clock` | Dieselbe Wettkampfzeit wie `time.competitionTime`, an ihrem angestammten Platz für den Web Viewer. |
| `competition` | Der aktuelle vollständige Wettkampfstand oder `null`. |
| `currentFinish` | Klassen- und Zeilenindex des aktuellen Einlaufs im Snapshot dieser Klasse: `classIndex`, `rowIndex`, `snapshotRevision`. **Kein Rang und keine Zeit.** `null`, solange keiner gemeldet wurde. |
| `message` | Öffentliche WinLaufen-Nachricht oder `null`. |

`competition` enthält `type`, `evaluationMode`, `classCount`,
`winSpringenPosition`, `roundOrHeat` und `classes`. Jede Klasse hat `index`,
`name`, `roundsOrTeamSize` und `snapshot`; ein `snapshot` besteht aus
`revision`, `headers` und `rows`, wobei `rows` eine Liste von Zeilen aus
Zeichenketten ist. Spaltenüberschriften und Zellwerte werden unverändert von
WinLaufen durchgereicht und hier nicht ausgewertet.

**Snapshot-Semantik, kein Ereignisprotokoll.** Jeder Abruf liefert den
**aktuell bekannten vollständigen Stand**. Zwischen zwei Abrufen können sich
vorhandene Zeilen ändern, weil WinLaufen vollständige Klassen-Snapshots liefert
und spätere Snapshots bereits vorhandene Zeilen korrigieren dürfen. Ein Consumer
darf die Zeilen deshalb **nicht** als unveränderliche Finish-Ereignisse
behandeln.

Drei Zustände sind zu unterscheiden:

| Fall | Bedeutung |
|---|---|
| `competition: null` | Von der Quelle **noch nie empfangen**. Nicht „die Quelle meldet nichts". |
| `competition` mit Klassen ohne `snapshot` | Wettkampfstruktur bekannt, für diese Klasse liegt noch kein Ergebnisstand vor. |
| `competition` mit `snapshot` und leerem `rows` | Autoritative Aussage: In dieser Klasse gibt es aktuell keine Zeilen. |

### `presentation`

Die Anzeigeeinstellungen des Veranstalters: `showClub`, `showAssociation`,
`showNation`, `showShooting`, `showPublicMessages`. Sie steuern, welche Spalten
der Web Viewer zeigt. Ein externer Consumer kann sie ignorieren — die Daten
selbst werden dadurch nicht gefiltert.

## 8. GET /api/v1/startlist

Der vollständige aktuelle Startlistenbestand — **mit dem Zeitblock und dem
Verbindungsstatus dieses Requests**, nicht mit denen des Zeitpunkts, zu dem die
Startliste veröffentlicht wurde.

Antwort mit `200`, `Content-Type: application/json; charset=utf-8`,
`Cache-Control: no-store`.

```json
{
  "apiVersion" : 1,
  "type" : "startlist",
  "channelId" : "local",
  "streamId" : "b7f1c2",
  "time" : {
    "competitionTime" : "10:14:37",
    "competitionTimeZone" : "Europe/Berlin",
    "competitionTimeZoneSource" : "CONFIGURED",
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

`apiVersion`, `channelId`, `streamId`, `time` und `connection` sind identisch
mit `/api/v1/state`; `type` lautet hier `"startlist"`. Dazu kommen:

| Feld | Bedeutung |
|---|---|
| `present`, `generation`, `source`, `sourceLabel`, `entryCount`, `classCount` | wie in den Startlisten-Metadaten von `/api/v1/state` |
| `entries` | alle Teilnehmer in der Reihenfolge des Imports |

Jeder Eintrag hat genau diese Felder, alle als Zeichenkette und unverändert aus
dem WinLaufen-Export:

| Feld | Inhalt |
|---|---|
| `bib` | Startnummer. **Zeichenkette**: `0012`, `12` und `A12` sind verschiedene Startnummern und dürfen nicht in Zahlen umgewandelt werden. |
| `className` | Klasse |
| `startTime` | Startzeit, unverändert durchgereicht |
| `lastName`, `firstName` | Name |
| `club`, `association` | Verein, Verband |
| `course` | Strecke |
| `birthYear` | Jahrgang |
| `gender` | Geschlecht |
| `nation` | Nation |

Nicht belegte Felder sind leere Zeichenketten, nicht `null`.

**Reihenfolge und Identität.** `entries` steht exakt in der Reihenfolge der
veröffentlichten `CanonicalStartList`, also in der des WinLaufen-Exports. Es
wird **nicht** sortiert und **nicht** gruppiert. Es gibt **keine
Teilnehmer-ID** — die Quelle liefert keine, also erfindet keine Stufe eine.
Eindeutig innerhalb eines Bestands ist das Paar `(className, bib)`; dieselbe
Startnummer in verschiedenen Klassen ist erlaubt.

**Fehlende Startliste.** `present: false`, `generation: 0`, `entries: []`,
`source` und `sourceLabel` `null`. Das ist eine klare Aussage und kein Fehler.

### Warum `/state` und `/startlist` getrennt sind

`/api/v1/state` ist klein und für häufiges Polling gedacht; eine reale Liste
bringt tausende Einträge mit. Real gemessen mit 1 978 Teilnehmern in 46 Klassen:
`/api/v1/state` rund 700 Byte, `/api/v1/startlist` rund 418 KB.

Trotz unveränderter Startliste enthält jede `/startlist`-Antwort den
**aktuellen** Zeitblock und Verbindungsstatus. Beides wird erst beim HTTP-Read
zusammengeführt; eine eingefrorene Uhr aus dem Zeitpunkt der
Startlisten-Publikation gibt es nicht.

Wer nur wissen will, ob sich der Bestand geändert hat, liest `generation` aus
dem kleinen `/api/v1/state`.

## 9. GET /api/v1/runtime

Ein gewöhnlicher **HTTP-Endpunkt** mit der Laufzeitkonfiguration des Web
Viewers. Er ist selbst **kein** WebSocket, sondern nennt dem Viewer Port und
Pfad des Browser-WebSockets, damit dieser sie nicht erraten muss.

```json
{"webSocketPort":44441,"webSocketPath":"/live/v1"}
```

Antwort mit `200`, `Cache-Control: no-store`. Für externe Consumer ohne
Bedeutung.

## 10. Web Viewer

| Endpunkt | Inhalt |
|---|---|
| `GET /` und `GET /viewer` | HTML des Web Viewers |
| `GET /renderer` | `302` auf `/`; Kompatibilitätsroute der früheren Renderer-URL |
| `GET /assets/viewer.css` | Stylesheet |
| `GET /assets/viewer.js` | Skript |

Ausgeliefert mit `Cache-Control: no-cache` und `X-Content-Type-Options: nosniff`.
Der Viewer ist reines HTML/CSS/JavaScript ohne Framework.

Ablauf im Browser: HTML laden, `GET /api/v1/state` und `GET /api/v1/runtime`
für den Startzustand, dann WebSocket verbinden und Live-Nachrichten empfangen.

## 11. WebSocket `/live/v1` — Browser

Port 44441. **Read-only**: Sendet ein Browser eine Anwendungsnachricht, wird die
Verbindung geschlossen.

Handshake: Der `Origin` muss vom selben Host stammen wie die Anfrage; geprüft
wird der Host, nicht der Port. Ein fremder oder fehlender Origin führt zur
Ablehnung. Nachrichten eines Browsers sind auf 4 096 Byte begrenzt.

Der Server sendet drei Nachrichtenarten, jeweils JSON mit einem `type`:

| `type` | Wann | Inhalt |
|---|---|---|
| `snapshot` | beim Verbinden und bei jeder neuen Veröffentlichung | `publicationRevision`, `state`, `presentation` |
| `startlist` | beim Verbinden und nach jedem angenommenen Import | `publicationRevision`, `generation`, `source`, `sourceLabel`, `entries` |
| `heartbeat` | alle 2 Sekunden | nur `type` |

Der `heartbeat` ist bewusst zustandslos: Ein Browser kann eine tote
TCP-Verbindung sonst nicht von einem ruhigen Wettkampf unterscheiden, und
während eines Quellenausfalls veröffentlicht die Bridge gar nichts. Der
`snapshot` dieser Nachrichten trägt **nicht** den Zeitblock der Read API — dieser
Weg dient der Anzeige, nicht der Zeitmessung.

Innerhalb einer Verbindung sinkt die ausgelieferte `publicationRevision`
niemals. Sie gilt nur für die Laufzeit eines Live-Server-Prozesses; ein Browser
setzt seinen Schutz deshalb bei jeder neuen Verbindung zurück.

## 12. WebSocket `/bridge/v1/channels/<channel>` — Bridge-Ingest

Port 44441, derselbe Listener wie der Browserpfad, getrennt über Pfad und
Handshake-Regeln. `<channel>` ist die Kanalkennung des Live Servers,
üblicherweise `local`.

**Ausschließlich für die Bridge.** Der Handshake verlangt
`Authorization: Bearer <secret>`; der Vergleich erfolgt zeitkonstant. Der
`Origin` spielt hier keine Rolle. Nachrichten sind auf 16 MB begrenzt.

Die Bridge sendet zwei Nachrichtentypen:

| `type` | Wann | Antwort des Live Servers |
|---|---|---|
| `snapshot` | bei jeder Revision des kanonischen Zustands | `ack` mit `channelId`, `streamId`, `sourceRevision` |
| `startlist` | nach jedem Import und bei jedem Verbindungsaufbau | keine — siehe unten |

Der Snapshot trägt `channelId`, `streamId`, `sourceRevision`, `state` und
`presentation`; im `state` steckt seit MS3 zusätzlich das `clockSample`. Eine
Startliste wird **nicht** quittiert: Der Zustandsstrom belegt bei jeder Revision,
dass dieser Live Server verarbeitet, was er empfängt, und eine unbrauchbare
Startliste schließt die Verbindung, was die Bridge mit Reconnect und
vollständigem Resync beantwortet. Eine zweite ACK-Infrastruktur für eine
Nachricht, die einige Male am Tag kommt, gibt es bewusst nicht.

Eine ungültige Nachricht führt zum Schließen der Verbindung mit einem Grund, der
benennt, welche Nachricht fehlerhaft war.

Details zu Revisionen, Reconnect und Resync:
[MODULAR_ARCHITECTURE.md](MODULAR_ARCHITECTURE.md).

## 13. Bridge Control API

Port 44442, **administrativ**. Bridge Control besitzt in dieser Prototypversion
**keine Benutzeranmeldung**; der Port gehört ausschließlich in ein
vertrauenswürdiges LAN. Details unter [Sicherheit](#16-sicherheit).

Schreibende Aufrufe sind ausschließlich `POST` und verlangen einen `Origin`, der
zum `Host` der Anfrage passt. **CORS wird nie aktiviert.**

### GET /api/v1/config

Die aktuelle Konfiguration:

| Feld | Inhalt |
|---|---|
| `sourceType` | immer `"WINLAUFEN"` |
| `sourceHost` | Adresse des WinLaufen-PCs |
| `sourcePort` | immer `4444` |
| `targets` | je Ziel: `id`, `type` (`LOCAL`/`SELFHOST`/`RICHTER_PROJECTS`), `enabled`, `endpoint`, `channelId`, `secretConfigured`, `transportWarning`, `secretWarning` |
| `presentation` | `showClub`, `showAssociation`, `showNation`, `showShooting`, `showPublicMessages` |

**Verbindungsschlüssel werden nie ausgegeben.** `secretConfigured` sagt nur, ob
einer hinterlegt ist. Die Wettkampf-Zeitzone ist nicht Teil dieser Antwort; sie
wird in `bridge.properties` gepflegt.

### GET /api/v1/status

Der Laufzeitzustand:

| Feld | Inhalt |
|---|---|
| `sourceRevision` | aktuelle Revision des kanonischen Zustands |
| `sourceHealth` | `CONNECTED`, `STALE` oder `DISCONNECTED` |
| `clock` | zuletzt gemeldete Wettkampfzeit oder `null` |
| `outputs` | je Ziel: `targetId`, `state`, `lastAckedSourceRevision`, `retryAttempt`, `lastError` |
| `startList` | `generation`, `source`, `sourceLabel`, `entryCount`, `classCount` |

### POST /api/v1/config

Speichert die Konfiguration aus dem Formular der Oberfläche.

- `Content-Type: application/x-www-form-urlencoded`, sonst `415`
- `Origin` muss passen, sonst `403`
- Body maximal 32 768 Byte, sonst `413`
- Pflichtfeld `sourceHost`; fehlende oder ungültige Felder ergeben `400` mit
  `{"error": "..."}`
- Erfolg: `200` mit derselben Struktur wie `GET /api/v1/config`

Die Konfiguration wird vollständig neu geschrieben. Werte ohne Formularfeld —
etwa `competition.timezone` — bleiben dabei erhalten.

### POST /api/v1/startlist?name=&lt;datei&gt;

Importiert einen WinLaufen-Startlistenexport und **ersetzt den bisherigen
Bestand vollständig**.

- Die Datei ist der **rohe Request-Body**; der Dateiname steht im
  Query-Parameter `name`
- `Content-Type: application/octet-stream`, sonst `415`
- `Origin` muss passen, sonst `403`
- Fehlender `name`: `400`
- Body größer als **16 MiB**: `413`
- Unlesbare oder unplausible Datei: `400` mit verständlicher Meldung ohne
  Java-Stacktrace
- Erfolg: `200` mit `generation`, `source`, `sourceLabel`, `entryCount`,
  `classCount`

Der `name` bestimmt ausschließlich das Format und die Beschriftung; es wird
niemals ein vom Client gelieferter Pfad verwendet und nichts unter dem
Originalnamen gespeichert.

Unterstützt sind **CSV**, **TXT** und **XLSX**. Das alte binäre `.xls` wird
abgelehnt. Maximal **20 000** Teilnehmer — dieselbe Grenze wie auf dem
Transportweg, damit ein angenommener Import immer auch veröffentlichbar ist.

**Ein fehlgeschlagener Import ändert nichts.** Erst eine vollständig geprüfte
Liste ersetzt den Bestand, und nur dann steigt die `generation` — auch bei einem
inhaltlich identischen Import.

## 14. Statuscodes

| Code | Wo | Bedeutung |
|---|---|---|
| `200` | alle | Erfolg |
| `302` | Live Server `/renderer` | Weiterleitung auf `/` |
| `400` | Bridge Control | fehlerhafte Eingabe, JSON mit `error` |
| `403` | Bridge Control | `Origin` passt nicht zum `Host` |
| `404` | beide | unbekannter Pfad |
| `405` | beide | Methode nicht erlaubt; die Read API kennt nur `GET` |
| `413` | Bridge Control | Body größer als erlaubt |
| `415` | Bridge Control | falscher `Content-Type` |
| `500` | Bridge Control | interner Fehler, JSON mit `error` |

Die Read API des Live Servers antwortet im Normalbetrieb ausschließlich mit
`200`, `302`, `404` oder `405`. Es gibt **keinen** Fehlercode für „keine Daten":
Ein Live Server ohne Zustand antwortet mit `200` und
`connection.status = "NO_STATE"`.

## 15. Caching

Beide Read-API-Endpunkte antworten mit:

```text
Content-Type: application/json; charset=utf-8
Cache-Control: no-store
```

Das ist notwendig, weil jede Antwort eine laufende Wettkampfzeit und einen
aktuellen Verbindungsstatus trägt: Eine zwischengespeicherte Antwort würde einem
Consumer eine alte Uhr liefern, ohne dass er es merkt.

**Kein ETag, kein `304 Not Modified`.** Für `/api/v1/startlist` wäre ein ETag aus
`streamId` und `generation` naheliegend, denn der Bestand ändert sich selten.
Genau das wäre hier falsch: Die Startliste kann unverändert sein, während die
Uhr weitergelaufen ist. Ein `304` würde die Antwort vollständig unterdrücken und
dem Consumer damit den aktuellen Zeitblock und Verbindungsstatus vorenthalten —
also genau das, was diese API zusagt.

Die Viewer-Ressourcen werden mit `Cache-Control: no-cache` ausgeliefert.

## 16. Sicherheit

| Schnittstelle | Regel |
|---|---|
| **Read API** (44440) | read-only, nicht authentifiziert, nur `GET`. Kein Endpunkt ändert etwas: keine Konfiguration, kein Import, keine Bridge-Steuerung. |
| **Browser-WebSocket** (44441) | `Origin` muss zum Host passen; Browser dürfen nichts senden. |
| **Bridge-Ingest** (44441) | `Authorization: Bearer <secret>`, zeitkonstant verglichen. |
| **Bridge Control** (44442) | nur `POST` schreibt, mit Origin-Prüfung. **Keine Benutzeranmeldung.** |

**CORS wird nie aktiviert.**

**Bekannte Prototyp-Einschränkung:** Der Bridge-Ingest verwendet weiterhin ein
bekanntes Default-Secret, solange keines gesetzt ist. Wer Port 44441 erreicht und
das Secret kennt, kann sich als Bridge ausgeben und den veröffentlichten Stand
ersetzen. Verbindliche Einsatzgrenzen: README.md, Abschnitt
[Known prototype security limitation](../README.md#known-prototype-security-limitation).

Die Read API ist nicht authentifiziert, weil sie auf demselben Port dieselben
Daten liefert wie der öffentliche Web Viewer.

### „Read-only" heißt nicht „beliebig freigeben"

Read-only sagt nur, dass niemand über diese Schnittstelle etwas ändern kann. Es
sagt nichts darüber, wer die Daten **lesen** darf — und `GET /api/v1/startlist`
liefert reale Teilnehmerdaten:

```text
Vorname · Nachname · Jahrgang · Verein · Verband · Nation
Startnummer · Klasse · Startzeit · Strecke
```

Das ist ein vollständiger Teilnehmerbestand, nicht nur der öffentlich
angezeigte Wettkampfstand. Er liegt auf demselben unauthentifizierten Port wie
der Web Viewer.

**Empfehlung:** Port 44440 nur in Netzen oder über Zugänge bereitstellen, in
denen diese Teilnehmerdaten gelesen werden dürfen. Wer einen Presentation Node
öffentlich betreibt, veröffentlicht damit auch die Startliste. Diese Entscheidung
trifft der Veranstalter bewusst; Sprecher-Web schränkt sie technisch nicht ein.

## 17. Versionierung

Der Präfix `/api/v1/` versioniert das **Antwortformat der HTTP-API**, nicht das
Produkt und nicht den Bridge-Contract; letzterer hat seine eigene
`schemaVersion`.

Innerhalb von `v1` sind **additive** Änderungen zulässig: neue Felder, neue
Endpunkte, neue Enum-Werte in Feldern, die bereits als erweiterbar dokumentiert
sind. Ein Consumer sollte deshalb unbekannte Felder ignorieren und nicht auf
eine feste Feldreihenfolge bauen.

Eine Umbenennung oder Entfernung eines Feldes wäre ein Bruch und erforderte
`v2`. Ein solcher Schritt wird nicht auf Verdacht eingeführt.

### Bridge und Live Server müssen zusammenpassen

Der interne Bridge→Live-Server-Contract ist etwas anderes als diese HTTP-API. Er
prüft beim Lesen **streng**: Ein unbekanntes Feld wird abgelehnt, nicht
ignoriert. Für gemischte Versionen folgt daraus eine Richtung, die funktioniert,
und eine, die es nicht tut:

| Kombination | Verhalten |
|---|---|
| Bridge **0.4.0** → Live Server **neuer** | funktioniert. Die Zeitmessung fehlt schlicht: `time.clockSampleRevision`, `time.bridge` und `time.liveServer` sind `null`, alles andere ist vollständig. |
| Bridge **neuer** → Live Server **0.4.0** | funktioniert **nicht**. Der ältere Live Server lehnt jeden Snapshot ab, schließt die Verbindung, die Bridge verbindet neu — und nichts wird veröffentlicht. |

Daraus ergibt sich eine verbindliche **Upgrade-Reihenfolge: erst der Live
Server, dann die Bridge.** Bei einer All-in-One-Installation werden ohnehin beide
gemeinsam ersetzt. Details und Nachweis: [RELEASE.md](RELEASE.md).

## 18. Was diese API nicht tut

Sie stellt Messwerte bereit und interpretiert sie nicht. Ausdrücklich **nicht**
Bestandteil:

- **keine Korrektur** der Wettkampfzeit,
- **keine Auswahl** eines Offsets — es gibt kein `effectiveOffset`, kein
  `selectedOffset`, kein `correctedCompetitionTime`, kein `calibratedClock`,
  kein `bestReference` und keine Empfehlung,
- keine Entscheidung, ob die Bridge- oder die Live-Server-Messung besser ist,
- **keine Zusage über UTC-Genauigkeit**,
- **keine Aussage, dass eine gemessene Differenz der Uhrenfehler eines Rechners
  ist**,
- keine ClockCalibration und keine Driftkorrektur,
- keine Ableitung einer Zielzeit aus Uhrwert oder Empfangszeit,
- keine abgeleitete Transportlatenz aus den beiden Empfangszeitpunkten,
- keine Zusammenführung mit anderen Zeitnahmequellen,
- **kein Ersatz für einen manuellen Offset** in einem Consumer,
- keine Felder, Namen oder Begriffe eines bestimmten Consumers.

Diese fachliche Auswertung gehört in das jeweilige Zielsystem.
