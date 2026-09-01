# Modulare Zielarchitektur: Bridge, Live Server und Web Viewer

Status: implementierte und verbindliche Architektur, umgesetzt im
Refactoring-Block auf Basis des Checkpoints `6481a3b`. Der Weg vom früheren
Monolithen dorthin ist nicht mehr Teil dieses Dokuments — siehe Git-Historie.

## 1. Scope

Drei fachliche Ebenen, zwei unabhängig installierbare Java-Runtimes:

- **Bridge** — liest proprietäre Quellen, erzeugt den kanonischen Live-State.
- **Live Server** — nimmt den Bridge-Vertrag entgegen, veröffentlicht den
  State.
- **Web Viewer** — zeigt ausschließlich die öffentliche Live-Server-Sicht.

Unverändert: TCP/4444 read-only, Java-Serialisierungsreferenzen,
ObjectInputFilter, Clock-/Heartbeat-Regeln, vollständige Klassensnapshots,
dynamische Header, Current Finish, Biathlon-Schießen, Nachrichten. Keine
WinSpringen- oder Startlisten-Protokollannahmen.

Nicht Teil dieses Scopes: persönliche Browseroptionen, Remote-Output-
Produkte, Datenbank, Broker, komplexe PKI. SELFHOST und RICHTER_PROJECTS
erhalten vorerst denselben technischen Zielvertrag; produktive
Bereitstellung/Betrieb bleibt gesonderte Arbeit.

## 2. Begriffe

- **Bridge** — Backend beim Veranstalter/an der Zeitnahme. Einzige Instanz,
  die WinLaufen (oder künftige proprietäre Quellsysteme) kennt. Besitzt
  einzige Veranstalter-Konfiguration, kanonischen State, 0..n unabhängige
  Output-Verbindungen.
- **Live Server** — unabhängig betreibbares Backend für lokale/LAN/
  öffentliche Verteilung. Kennt nur den Bridge-Live-Server-Vertrag, hält
  zuletzt akzeptierten veröffentlichten State, bedient Browser über HTTP und
  WebSocket.
- **Web Viewer** — vom Live Server ausgeliefertes Browser-Frontend (HTML/
  CSS/JS). Ersetzt den früheren Begriff „Renderer".
- **Bridge Control** — einzige Veranstalter-Oberfläche, Teil der Bridge.
  Konfiguriert Quelle, Output Targets, Presentation Config. Ist nicht der
  Web Viewer und keine Live-Server-Administration.

## 3. Implementierte modulare Architektur

```mermaid
flowchart LR
    SRC[WinLaufen<br/>später verifizierte Quellen] -->|TCP 4444, read-only| BA[Bridge Source Adapter]
    BA --> CBS[Canonical Bridge State<br/>memory-only]
    BC[Bridge Config Store] --> BA
    BC --> OT[Output Target Manager]
    BC --> CBS
    CUI[Bridge Control<br/>HTTP 44442] --> BC
    CBS --> OT
    OT -->|eigene ausgehende WS-Verbindung| LS1[LOCAL Live Server]
    OT -->|eigene ausgehende WSS-Verbindung| LS2[SELFHOST Live Server]
    OT -->|eigene ausgehende WSS-Verbindung| LS3[RICHTER_PROJECTS Live Server]
    LS1 --> PSS1[Published State]
    LS2 --> PSS2[Published State]
    LS3 --> PSS3[Published State]
    PSS1 --> API1[Public HTTP/API + Browser WS]
    API1 --> WV1[Web Viewer]
```

Verbindliche Regeln:

- Nur Source Adapter kennen proprietäre Protokolle.
- Der kanonische Bridge-State ist outputneutral.
- Jeder konfigurierte Output besitzt Adapterinstanz, Worker,
  Verbindungszustand, Retry und Resync unabhängig von allen anderen Outputs.
- Der Live Server importiert nur das Contract-Modul, nie das Bridge-Modul.
- Der Web Viewer wird nur vom Live Server ausgeliefert und kennt nur dessen
  öffentliche API.
- LOCAL verwendet exakt denselben Output-Adapter und Vertrag wie ein
  entferntes Ziel. Lediglich Endpoint, TLS-Policy und Credentials sind
  Konfiguration.

## 4. Prozess- und Installationsgrenzen

Mindestens zwei ausführbare Runtime-Artefakte:

- `winlaufen-web-bridge` — Source Adapter, Canonical State, Fan-out, Bridge
  Config Store, Bridge Control.
- `winlaufen-web-live-server` — Contract-Empfang, Published State, Public
  API, Browser-WebSocket, statischer Web Viewer.
- `winlaufen-web-contract` — kleine Bibliothek, kein Prozess.

Ein All-in-One-Installer darf beide Runtimes gemeinsam installieren, startet
aber zwei Prozesse — kein In-Process-Kurzschluss, kein gemeinsamer Store.

Unterstützte Installationen:

```text
Bridge allein:
  winlaufen-web-bridge

Live Server + Web Viewer allein:
  winlaufen-web-live-server

All-in-One:
  winlaufen-web-bridge process --WS--> winlaufen-web-live-server process
```

- Live-Server-Classpath braucht weder `ObjectInputStream`-basierte Adapter
  noch WinLaufen-Protokollklassen.
- Reine Bridge-Installation braucht keine Viewer-Ressourcen und keinen
  öffentlichen Browser-WebSocket-Server.

### Fester Netzwerkvertrag

| Quelle | Ziel | Protokoll/Port | Zweck |
|---|---|---|---|
| Bridge | WinLaufen-PC | TCP 4444 | read-only Sprecher-PC-Protokoll; nur ausgehend |
| Viewer | Live Server | TCP 44440, Bind `0.0.0.0` | Web View / Public HTTP / API |
| Browser | Live Server | TCP 44441, Bind `0.0.0.0` | `/live/v1` |
| Bridge | Live Server | TCP 44441, gleicher Listener | `/bridge/v1/channels/<channel>` |
| Admin | Bridge | TCP 44442, Bind `0.0.0.0` | Bridge Control im vertrauenswürdigen LAN |

- Bridge Control bindet standardmäßig an alle lokalen Interfaces
  (Administration im vertrauenswürdigen LAN).
- Port 44441 bedient Browser-WebSockets und, über getrennte Pfade/
  Handshake-Regeln, Bridge-Ingestion — kein separater Listener.

Pfade:

- Bridge Control: `http://<bridge>:44442/`
- Web Viewer: `http://<live-server>:44440/`
- Public State: `GET http://<live-server>:44440/api/v1/state`
- Browser live: `ws://<live-server>:44441/live/v1`
- Bridge ingest: `ws://<live-server>:44441/bridge/v1/channels/<channel-id>`;
  über Internet zwingend `wss://...` (typischer externer Port 443)

- Installer prüft vor Start nur die Listener des gewählten Profils; TCP
  4444 ist kein lokaler Preflight-Port.
- Nach Start werden Dienste, Listener und lokale HTTP-Endpunkte validiert.
- Linux-Firewalls bleiben unverändert; Windows erhält nach
  Administratorprüfung nur die profilabhängigen Private-/Domain-Regeln,
  Uninstall entfernt nur eigene Regeln.

## 5. State Ownership

| Ebene | Autoritativer Owner | Kopie/Revision | Persistenz | Neustart und Resync |
|---|---|---|---|---|
| A. Source State | WinLaufen bzw. verifizierte Quelle | Wirewerte und Reihenfolge sind allein maßgeblich | Quelle entscheidet | Bridge eröffnet neue Source-Verbindung und neue Deserialisierungskontexte |
| B. Canonical Bridge State | Bridge | immutable State; `sourceRevision` steigt pro Bridge-Stream | memory-only | startet leer; neue `streamId`; Quelle füllt ihn wieder; letzte Werte werden nie aus lokaler Uhr rekonstruiert |
| C. Published Live Server State | je Live-Server-Channel der Live Server | Kopie des zuletzt akzeptierten vollständigen Bridge-Snapshots; eigene `publicationRevision` für Browser (nur prozesslaufzeitgültig, startet nach Neustart wieder bei 0) | memory-only in v0.1 | startet leer; Bridge-Verbindung liefert sofort Vollsnapshot; bleibt bei Ausfall als stale/disconnected markierte letzte Kopie sichtbar |
| D. Browser State | jeweiliger Browser | flüchtige Kopie des letzten Live-Server-Snapshots; Revisionsschutz pro Verbindung | keine Serverpersistenz; optionaler Browser-UI-State out of scope | WebSocket-Reconnect erhält sofort autoritativen Vollsnapshot |

- `sourceRevision` monoton innerhalb einer `streamId`; jeder
  Bridge-Prozessstart erzeugt eine neue zufällige `streamId` (eindeutiger
  Revision-Neustart).
- Die WinLaufen-Wettkampfzeit ist ein reiner Durchreichewert. Keine Stufe
  dieser Kette erzeugt, korrigiert, zählt oder interpoliert sie; siehe
  `docs/WINLAUFEN_PROTOCOL.md`.
- Live Server vergibt zusätzlich `publicationRevision` (monoton innerhalb
  der Prozesslaufzeit) — Fortführung der Browser-Revisionsgarantie.
- Target-ACK referenziert `streamId`/`sourceRevision`; Browser sehen
  `publicationRevision`.
- Presentation Config ist Teil jedes vollständigen Bridge-Snapshots —
  Published State = Competition State + atomar veröffentlichte
  Presentation Config.
- Output-Runtime-Health ist Bridge-Telemetrie, nicht Teil des kanonischen
  Wettkampf-State.
- Kein Disk-Persistieren des State in v0.1 — nach Neustart keine
  vorgetäuscht aktuellen alten Daten aus einer Datei.

## 6. Bridge → Live Server Contract

### Modul und Versionierung

`winlaufen-web-contract` enthält ausschließlich:

- immutable DTOs für kanonischen Competition State und Presentation Config;
- Envelopes und ACKs des Bridge-Live-Server-Vertrags;
- zentrale JSON-Codec- und strikte Validierungsregeln für diesen Vertrag;
- `schemaVersion = 1` und kompatibilitätsrelevante Limits.

Enthält NICHT: Source Adapter, Sockets, Retrylogik, Stores, HTTP-Handler,
Browsermodelle, Output-spezifische Implementierung. Eine kleine, etablierte
JSON-Bibliothek ist gerechtfertigt (Live Server muss erstmals untrusted
JSON sicher lesen); Nutzung bleibt strikt auf Contract-Codec begrenzt.

### V1-Nachricht

- Bridge sendet bei jeder kanonischen Revision einen vollständigen
  autoritativen Snapshot (kein Delta-Zustand im Output).
- Spätere Optimierungen dürfen additive Nachrichtentypen ergänzen, aber nie
  den verpflichtenden Resync-Snapshot ersetzen.

Normative Form (Feldnamen verbindlich, Beispielwerte illustrativ):

```json
{
  "type": "snapshot",
  "schemaVersion": 1,
  "channelId": "event-2026",
  "streamId": "550e8400-e29b-41d4-a716-446655440000",
  "sourceRevision": 42,
  "state": {
    "sourceHealth": "CONNECTED",
    "clock": "99:99:99",
    "competition": {
      "type": "Standardwettkampf",
      "evaluationMode": 1,
      "classCount": 2,
      "winSpringenPosition": 0,
      "roundOrHeat": 0,
      "classes": [
        {
          "index": 0,
          "name": "U13 m",
          "roundsOrTeamSize": 0,
          "snapshot": {
            "sourceRevision": 41,
            "headers": ["Rang", "StNr"],
            "rows": [["1", "101"]]
          }
        }
      ]
    },
    "currentFinish": {
      "classIndex": 0,
      "rowIndex": 0,
      "snapshotSourceRevision": 41
    },
    "message": "Start verschiebt sich"
  },
  "presentation": {
    "showClub": true,
    "showAssociation": true,
    "showNation": false,
    "showShooting": true,
    "showPublicMessages": false
  }
}
```

- `null` zulässig für noch nicht empfangene `clock`, `competition`,
  `currentFinish`, `message`.
- Header, Zellen, Reihenfolge, Indizes, Clock, Nachrichten: ohne fachliche
  Korrektur übertragen.
- Strukturelle Größenlimits gelten auch hier.

Der Live Server antwortet nach atomarer Annahme:

```json
{
  "type": "ack",
  "schemaVersion": 1,
  "channelId": "event-2026",
  "streamId": "550e8400-e29b-41d4-a716-446655440000",
  "sourceRevision": 42
}
```

Verhalten:

- Nach erfolgreichem WebSocket-Handshake sendet die Bridge immer sofort
  ihren kompletten aktuellen Snapshot, auch wenn seit dem Disconnect nichts
  änderte.
- Innerhalb derselben `streamId` lehnt der Live Server niedrigere
  Revisionen ab und darf gleiche Revisionen idempotent bestätigen.
- Eine neue authentifizierte `streamId` beginnt einen neuen Bridge-Stream
  und darf bei Revision 0 beginnen.
- Ein Snapshot ersetzt den kompletten Published State des Channels atomar.
- ACK bedeutet „validiert und im Published-State-Store übernommen", nicht
  nur „TCP-Bytes empfangen".
- Schema-Versionen werden explizit geprüft; unbekannte Majorversionen
  führen zu einer klaren Protokollschließung, nicht zu stiller
  Teilinterpretation.

## 7. Transportentscheidung

### Vergleich

| Kandidat | Vorteil | Nachteil | Entscheidung |
|---|---|---|---|
| HTTP Push (`POST` pro Snapshot) | sehr einfach, gut durch Proxies | eigenes Retry/ACK je Request; häufige Clock-Updates erzeugen neue Requests; Liveness weniger direkt | nicht gewählt |
| HTTP + separates WebSocket | flexible Zusatz-API | zwei Mechanismen für einen kleinen Bridge-Vertrag | nicht gewählt |
| SSE | gut für Server→Client | falsche Hauptrichtung; Bridge→Server benötigt zusätzlich HTTP | nicht gewählt |
| WebSocket-only | dauerhafte ausgehende Bridge-Verbindung, bidirektionale ACK/Ping-Pong, Vollsnapshot nach Reconnect, lokal/LAN/WSS gleich | benötigt bereits vorhandene kleine WS-Bibliothek und klare Backpressure | **gewählt** |

### Verbindliche v0.1-Entscheidung

- Bridge → Live Server: persistenter, von der Bridge ausgehender WebSocket
  pro aktiviertem Output Target.
- `ws` lokal/vertrauenswürdiges LAN sowie für ein `SELFHOST`-Ziel an einem
  öffentlichen IP-Adressliteral; `wss` sonst verpflichtend für Internetziele.
  Transport trägt ausschließlich V1-JSON-Envelopes aus dem Contract-Modul.
- Adapter hält pro Target höchstens den neuesten unbestätigten Vollsnapshot
  plus aktuellen kanonischen Snapshot — keine globale Output-Queue, kein
  unbeschränktes Event-Backlog.
- Solange die vorige Nachricht nicht geschrieben ist, wird keine weitere
  eingereiht; dieselbe Revision wird pro Verbindung höchstens einmal
  gesendet.
- Bei langsamen Zielen dürfen ältere ungesendete Vollsnapshots durch den
  neuesten ersetzt werden (jeder Snapshot ist vollständig und autoritativ).
- Nach Reconnect wird unabhängig vom Queuezustand der aktuelle Vollsnapshot
  gesendet.
- Gleiches `LiveOutputAdapter`-Interface für LOCAL, SELFHOST,
  RICHTER_PROJECTS — Zieltyp steuert nur Defaults/Verfügbarkeitsmetadaten,
  nicht State oder Pipeline.

## 8. Output-Target-Modell und Fan-out

Das exklusive `OutputMode` entfällt, ersetzt durch getrennte Config- und
Runtime-Modelle:

```text
OutputTargetConfig (persistent, Bridge)
  id              stabile, eindeutige ID
  type            LOCAL | SELFHOST | RICHTER_PROJECTS
  enabled         boolean
  endpoint        ws://... oder wss://...
  channelId       Ziel-Channel/Instanz
  credentialRef   Verweis auf lokal gespeichertes Secret, nie das Secret im State

OutputTargetRuntime (flüchtig, Bridge)
  targetId
  state           DISABLED | CONNECTING | CONNECTED | STALE | RETRY_WAIT
  lastAckedStreamId
  lastAckedSourceRevision
  retryAttempt
  lastError        sanitisiert, ohne Secret
```

- `STALE`: Transport offen, Ziel bestätigt seit längerer Zeit keine neuen
  Revisionen — darf dem Veranstalter nicht als gesund angezeigt werden.
- `type` ist ein Enum, keine globale Auswahl — `List<OutputTargetConfig>`
  erlaubt mehrere Ziele/mehrere Instanzen desselben Typs. Deaktivierte
  Produktfähigkeit startet keinen Adapter.

LOCAL ist ein normales, standardmäßig vorkonfiguriertes Target:

```text
id=local
type=LOCAL
enabled=true
endpoint=ws://127.0.0.1:44441/bridge/v1/channels/local
channelId=local
```

- Auch LOCAL darf fehlschlagen, ohne Source-Verbindung oder Bridge zu
  stoppen. All-in-One installiert/startet beide gemeinsam, aber ohne
  prozessweites Warten oder direkten Java-Aufruf als Abkürzung.
- Retry pro Target: sofort, dann 2s, 5s, danach alle 10s — eigene
  Zustandsmaschine/Konstanten, unabhängig vom Source-Reconnect. Laufende
  Retry-Wartezeit nur durch Shutdown verkürzbar, nie durch neue Snapshots.
- Target-Listen-Änderung wird inkrementell angewendet: unveränderte Targets
  behalten Adapter/Verbindung/ACK-Stand/Retryzähler — kein globaler
  Neuaufbau, nie zwei Adapter für dasselbe unveränderte Target.

## 9. Konfigurationsbesitz

### Einzige Veranstalter-Konfiguration: Bridge

Persistent in `${user.home}/.winlaufen-web/config.properties` bzw. einer
kompatiblen Bridge-spezifischen Weiterentwicklung:

- Source Type und Source Host;
- protokollspezifisch feste Parameter (WinLaufen-Port bleibt 4444);
- 0..n Output Targets;
- Presentation Config: Verein, Verband, Nation, Schießen und öffentliche
  WinLaufen-Nachrichten;
- Credential-Referenzen bzw. Secrets der Output Targets.

Bridge Control ist die einzige UI hierfür. Änderungen der Presentation
Config erzeugen eine neue kanonische Revision und werden an **alle** aktiven
Targets als Teil des nächsten Vollsnapshots verteilt.

### Rein technische Live-Server-Konfiguration

Der Live Server darf lokal/deploymentseitig konfigurieren:

- HTTP-/WebSocket-Bind-Adresse und Ports;
- erlaubte `channelId`;
- Empfangs-Credentials bzw. deren Hash/Referenz;
- TLS-/Proxy-Deploymentparameter;
- optionale Instanzkennung und technische Limits.

Der Live Server hat keine Veranstalterseite für Quelle, Outputs oder
Presentation. Seine technische Konfiguration wird nicht an Browser
ausgeliefert.

### Öffentliche Browser-Konfiguration

Der Web Viewer erhält aus der öffentlichen Live-Server-API ausschließlich
die für die Darstellung benötigte Presentation Config, idealerweise
zusammen mit dem Public State. Er erhält nie Source Host, Output-Liste,
Endpoints, Credential-Referenzen oder Bridge-Control-Ports.

## 10. Failure Isolation und Reconnect

Vier unabhängige Zustandsmaschinen sind verbindlich:

### Source Connection: Quelle → Bridge

- Heutige WinLaufen-Regeln bleiben exakt erhalten.
- Erster strukturell gültiger Clock-Telegramm setzt CONNECTED; jeder
  weitere gültige Wert bestätigt Liveness unabhängig vom Zahlenwert.
- Mehr als vier Sekunden ohne Clock setzt STALE, schließt Socket und
  ObjectInputStream und startet Reconnect sofort/2s/5s/10s.
- Letzter Competition State bleibt im Bridge-State sichtbar, aber Source
  Health wird STALE/DISCONNECTED und wird so an alle erreichbaren Targets
  publiziert.
- Source-Ausfall beendet keinen Output-Worker und keinen Live Server.

### Output Connection A: Bridge → Live Server A

- eigener Worker, Socket, Retryzähler, ACK-Stand und letzter Fehler;
- Ausfall verändert weder Source-Verbindung noch Canonical State;
- keine synchronen Sends auf dem Source-Reader-Thread;
- Reconnect sendet sofort den neuesten vollständigen Snapshot;
- Ziel bleibt RETRY_WAIT/DISCONNECTED in Bridge Control sichtbar.

### Output Connection B: Bridge → Live Server B

- vollständig unabhängig von A;
- keine gemeinsame Queue, kein globaler Retry und kein „alle Targets neu
  verbinden";
- ein blockiertes A darf Zustellung und ACK-Verarbeitung von B nicht
  verzögern.

### Browser Connection: Live Server → Browser

- Browserdisconnect verändert Published State, Bridge-Ingest und andere
  Browser nicht;
- Browser verbindet mit eigenem Retry erneut (sofort, 2 s, 5 s, dann 10 s);
- Live Server sendet unmittelbar einen vollständigen Public Snapshot;
- `publicationRevision` verhindert Rückschritte durch wartende Sends; der
  Schutz gilt pro Verbindung und wird vom Browser bei jeder neuen Verbindung
  zurückgesetzt, weil die Revision nur für eine Live-Server-Laufzeit gilt;
- Live Server sendet Browsern alle 2 s ein zustandsloses Lebenszeichen; ein
  Browser wertet dessen Ausbleiben über 6 s als Verbindungsverlust und zeigt
  dann nicht mehr `CONNECTED`;
- das technische Lebenszeichen verändert weder Wettkampfzeit noch
  `SourceHealth`, `publicationRevision` oder Ergebnisdaten; es beantwortet
  ausschließlich die Frage nach der Verbindung Browser ↔ Live Server;
- verliert der Live Server seinen Bridge-Ingest, veröffentlicht er seine letzte
  Kopie mit `SourceHealth` `DISCONNECTED` und löst die Streambindung, damit der
  Resync der zurückkehrenden Bridge auch bei gleicher `sourceRevision` wieder
  angenommen wird. Ergebnisse und letzte Wettkampfzeit bleiben sichtbar.

### Konkrete Ausfallszenarien

| Ereignis | Verhalten |
|---|---|
| WinLaufen verschwindet | Bridge markiert Source stale/disconnected, behält letzten State, publiziert Status soweit Outputs erreichbar sind und reconnectet nur Source |
| LOCAL Live Server verschwindet | nur LOCAL-Target retryt; WinLaufen und andere Targets laufen weiter |
| Cloud Live Server verschwindet | nur dieses Target retryt ausgehend; kein Portforwarding, kein lokaler globaler Stau |
| Browser verschwindet | nur dessen WS-Verbindung endet; Live Server und Bridge bleiben unverändert |
| Output kommt zurück | Authentifizierung, dann aktueller Vollsnapshot, ACK, danach neueste Revisionen |
| Bridge kommt zurück | neue `streamId`; jedes Ziel erhält vollständigen State, sobald verfügbar |
| Live Server startet neu | Published State zunächst leer; die weiter retryende Bridge liefert Vollsnapshot |

## 11. Security Boundary

### Bridge / proprietäre Quelle

- WinLaufen-Verbindung bleibt strikt read-only; Bridge schreibt keine
  Anwendungsdaten zurück.
- `ObjectInputStream`, Java-Serialisierungskontext und der restriktive
  `WinLaufenObjectFilter` bleiben ausschließlich Bridge-Verantwortung.
- Jeder Reconnect erzeugt Socket, Stream und Deserialisierungskontext neu.
- Source-Wiredaten passieren strukturelle Limits, aber keine fachliche
  Plausibilisierung.

### Bridge → Live Server

- Jeder Ingest-Handshake authentifiziert Target/Channel, v0.1 mit einem pro
  Target provisionierten Bearer-Token oder gleichwertigem Shared Secret.
- Secrets liegen nur in Bridge-Target- und technischer
  Live-Server-Konfiguration; sie sind weder Contract-State noch Public API.
- Internetziele erfordern `wss` mit normaler Zertifikatsprüfung. `ws` ist
  für Loopback bzw. bewusst vertrauenswürdiges LAN zulässig — konkret:
  `localhost` und Loopback-/Link-Local-/private IP-Adressliterale. Ein
  `SELFHOST`-Target darf `ws` zusätzlich auf ein öffentliches
  IP-Adressliteral richten: der temporäre selbst betriebene Presentation
  Node ohne Domain. Er wird zugelassen, aber dauerhaft gewarnt statt
  blockiert. Jeder andere Host, insbesondere jeder DNS-Name, und jede
  öffentliche Adresse für `LOCAL` erfordern `wss`. `RICHTER_PROJECTS`
  erfordert immer `wss`.
- Eingehende WebSocket-Nachrichten sind hart begrenzt, bevor sie im Heap
  zusammengesetzt werden: Ingest höchstens ein Vertragssnapshot, Browser-
  Pfad nur eine sehr kleine Nutzlast.
- Der Live Server validiert Channelbindung, Schema, Größen, Feldtypen und
  Revisionen vor atomarer Übernahme. Die Source-Adapter der Bridge
  erzwingen dieselben strukturellen Grenzen bereits an ihrer
  Eintrittsgrenze, damit der kanonische State nie einen Wert annimmt, der
  anschließend unpublizierbar wäre.
- **Bekannte Prototyp-Einschränkung:** In der Prototype Baseline bleibt ein
  bekanntes Default-Ingest-Secret bewusst funktionsfähig. Konkrete
  Manipulationsmöglichkeit und Einsatzgrenzen: README.md, "Known prototype
  security limitation". Individuell provisionierte Secrets pro Target
  bleiben Voraussetzung für produktiven Internetbetrieb.
- Browser- und Bridge-WebSocket-Pfade haben getrennte Handshake-Policies:
  Browser benötigen gültige Same-Host-Origin; Bridge-Ingest benötigt
  Authentifizierung und ist nicht von einem Browser-Origin abhängig.
- Keine Java-Deserialisierung wird über die Ingest- oder Public-Grenze
  exponiert.
- Bridge Control auf TCP 44442 ist ein Administrationsport. v0.1 besitzt
  dort bewusst keine Benutzer- oder Login-Authentifizierung; jeder
  Teilnehmer im erreichbaren Netz kann grundsätzlich Konfigurationen
  ändern. Der Port ist daher auf ein vertrauenswürdiges LAN zu begrenzen
  und darf weder im Gäste-WLAN noch über unkontrollierte
  Portweiterleitungen oder direkt im öffentlichen Internet erreichbar sein.
  Die Control-API gibt Target-Secrets nicht aus; das ist kein Ersatz für
  diese Netzgrenze.

### Browser

- Browser erhalten ausschließlich Published State und Presentation Config.
- Bridge-Konfiguration, Source-Ziel, Outputstatus, Credentials und
  technische Ingest-Daten sind nicht öffentlich.
- CORS bleibt aus; Browser-WebSocket-Originprüfung bleibt erhalten. Für ein
  Internetdeployment kann TLS vor oder im Live Server terminiert werden,
  ohne eine eingehende Verbindung zur Bridge zu verlangen.

## 12. Empfohlene Maven-/Repository-Struktur

```text
winlaufen-web/
├── pom.xml                         # Parent/Aggregator, keine Runtime-Klassen
├── contract/
│   └── src/{main,test}/java/.../contract/
├── bridge/
│   ├── pom.xml                     # winlaufen-web-bridge, ausführbares JAR
│   ├── src/main/java/.../bridge/
│   │   ├── BridgeMain.java
│   │   ├── config/
│   │   ├── source/winlaufen/
│   │   ├── state/
│   │   ├── output/
│   │   └── control/
│   └── src/main/resources/bridge-control/
├── live-server/
│   ├── pom.xml                     # winlaufen-web-live-server, ausführbares JAR
│   ├── src/main/java/.../liveserver/
│   │   ├── LiveServerMain.java
│   │   ├── config/
│   │   ├── ingest/
│   │   ├── state/
│   │   └── web/
│   └── src/main/resources/web-viewer/
├── testdata/protocol/              # unveränderte reale Evidenz
├── docs/
└── devtools/
```

Abhängigkeitsrichtung:

```text
contract             (keine Abhängigkeit auf bridge/live-server)
   ↑       ↑
bridge   live-server
            ↑
       web-viewer resources

bridge -X-> live-server Java packages
live-server -X-> bridge Java packages
contract -X-> Source-/HTTP-/Store-Implementierung
```

Ein separates drittes Runtime- oder „core"-Modul ist nicht empfohlen.
Weitere Module sind erst bei nachgewiesenem Bedarf sinnvoll. Insbesondere
wird `shared`, `common` oder `util` nicht als Sammelbecken angelegt.

## Architekturentscheidung in Kurzform

- Zwei Runtimes und ein kleines Contract-Modul sind erforderlich.
- Die Bridge ist alleiniger Besitzer von Source, Veranstalter-Konfiguration,
  Canonical State und Output-Fan-out.
- Der Live Server besitzt ausschließlich die veröffentlichte Kopie und die
  öffentliche Browseroberfläche.
- Bridge → Live Server ist ein ausgehender, authentifizierter WebSocket pro
  Target; über Internet WSS.
- Jeder v0.1-Transfer ist ein vollständiger autoritativer Snapshot mit
  `streamId/sourceRevision` und ACK.
- LOCAL ist ein reguläres Output Target und keine zweite Architektur.
- Der Web Viewer spricht ausschließlich mit dem Live Server.
