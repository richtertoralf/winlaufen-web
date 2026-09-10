# Sprecher-Web — Architecture

Kurzer IST-Stand der in [MODULAR_ARCHITECTURE.md](MODULAR_ARCHITECTURE.md)
festgelegten Zielarchitektur. Vollständige Entscheidungen, Contract-Felder
und Sicherheitsregeln stehen ausschließlich dort — hier keine Duplikate.

## Produktname

- Sichtbar: **Sprecher-Web**, öffentliche Oberfläche mit dem Untertitel
  „Live-Ergebnisse aus WinLaufen"
- Technisch (Kompatibilität): `winlaufen-web-*`, Java-Packages,
  Installationspfade bleiben unverändert
- Keine Web-Version von WinLaufen — nutzt nur dessen Sprecher-PC-Schnittstelle
- Bridge Control zeigt LOCAL vereinfacht als „Live-Ergebnisse im Browser"
  (ID/Typ/Endpoint/Channel/Secret verborgen); intern ein normales Output
  Target

## Module und Prozesse

```text
WinLaufen --TCP/4444, read-only------------------->|
Startlisten-Dateiexport --Bridge Control---------->|
                                                   v
                                        winlaufen-web-bridge
                                          Canonical Competition State   memory-only
                                          CanonicalStartList            persistent
                                                   |
                                                   |  ausgehend WS/WSS
                                                   |    Snapshot + ACK  je Revision
                                                   |    StartList       nur bei Import
                                                   |                    oder Connect
                                                   v
                                     winlaufen-web-live-server
                                          Published Competition State   memory-only
                                          Published StartList           memory-only
                                                   |
                                                   |  HTTP + Browser-WebSocket
                                                   v
                                              Web Viewer
                                          Uhr / LIVE / Ergebnisse
                                          Startliste nach Klassen
```

- Root-POM: reiner Aggregator, keine Runtime-Klassen
- `winlaufen-web-contract`: kleine Bibliothek, kein Prozess
- Bridge und Live Server: getrennte JARs, kein gemeinsamer Prozess/Store,
  kein In-Process-LOCAL-Pfad

## State und Konfiguration

- WinLaufen = Source Authority
- Bridge: memory-only Canonical State, `streamId`/`sourceRevision`, einzige
  Veranstalter-Konfiguration, unabhängige Output-Worker
- Bridge zusätzlich: in Bridge Control importierte Startliste mit eigener
  `generation`, neben der Konfiguration persistiert und über einen Neustart
  hinweg gültig. Veranstalterdaten, kein Quell-State
- Startliste als eigener Nachrichtentyp im Fan-out: nach jedem Import und
  auf jeder neuen Output-Verbindung, **nicht** mit Uhr- oder
  Ergebnisänderungen
- Live Server zusätzlich: Published StartList je Channel, memory-only,
  vollständiger Replace; nach Neustart liefert der Bridge-Reconnect sie
  wieder
- Web Viewer: Startliste klassenweise in Importreihenfolge, als eigene
  Browsernachricht neben dem Zustandssnapshot
- Live Server: pro Channel memory-only Published State mit eigener
  `publicationRevision`
- Live Server zusätzlich: seit wann die Wettkampfzeit ihren aktuellen Wert hat,
  wann zuletzt irgendein Snapshot ankam und der Zustand der eigenen
  Bridge-Ingest-Verbindung. Alles drei ist Metadatum der Read API und fließt nie
  in die Wettkampfzeit ein. Bewusst kein Beobachtungszeitpunkt der Quelle: Die
  Bridge erhöht ihre Revision für jede Art von Änderung und sagt nicht, welche
  es war — mehr als „zu diesem Zeitpunkt kam ein Snapshot mit diesem Uhrwert an"
  ist nicht belegbar, und kein Feld behauptet mehr.
  Damit lässt sich „WinLaufen getrennt" von „Bridge getrennt" unterscheiden —
  die browserseitige `SourceHealth` wird beim Bridge-Verlust bewusst abgewertet
  und kann das allein nicht ausdrücken
- Browser: nur flüchtige öffentliche Kopie
- `BridgeConfig`: Source, 0..n Targets, Presentation Config
- Live Server kennt nur technische Bind-/Channel-/Ingest-Credential-Config

### Startliste

**CanonicalStartList** — gehört der Bridge, persistent neben ihrer
Konfiguration, vollständiger Ersatz bei jedem angenommenen Import, eigene
`generation`. Es gibt **kein Teilnehmer-ID-Konzept**: die Quelle liefert keins,
also erfindet keine Stufe eins. Kanonisch eindeutig ist `(className, bib)`;
dieselbe Startnummer in verschiedenen Klassen ist erlaubt. Die `generation`
versioniert nur diesen Bestand und ist keine Wettkampf-, Lauf- oder
Teilnehmerkennung.

**StartList Publication** — eigener Nachrichtentyp auf derselben
Ingest-Verbindung, **nicht** Bestandteil des häufigen Competition-State-
Snapshots. Voller Snapshot, kein Delta. Gesendet nach einem erfolgreichen
Import, beim ersten Verbindungsaufbau und bei jedem Reconnect; **nicht** bei
Uhr-, Ergebnis-, Health- oder Präsentationsänderungen und nicht bei einem
abgelehnten Import. Jedes aktivierte Output Target erhält seinen eigenen Sync.

**Live Server** — hält die Startliste memory-only und ersetzt sie vollständig.
Die Bridge bleibt die autoritative Quelle; nach einem Live-Server-Neustart
liefert sie den Bestand beim Reconnect von selbst erneut, ohne erneuten Import.

**Web Viewer** — eigener Startlistenbereich, klassenweise Darstellung mit
Vor-/Zurück-Navigation und direkter Klassenauswahl. Reihenfolge der Klassen und
der Teilnehmer bleibt die des Imports.

**Read API** — der Live Server stellt beides zusätzlich generisch über HTTP
bereit: `GET /api/v1/state` mit Wettkampfzeit, Ergebnissen, Verbindungsstatus
und Startlisten-**Metadaten**, `GET /api/v1/startlist` mit dem vollständigen
Bestand. Beide Antworten führen die aktuelle Wettkampfzeit und den Zustand der
Kette WinLaufen → Bridge → Live Server mit; die Startliste wird erst beim
HTTP-Read mit dem laufenden State zusammengeführt, nie eingefroren
mitgespeichert. Vollständig: [API.md](API.md).

Details: MODULAR_ARCHITECTURE.md §5 (State Ownership), §5.1 (Startliste), §6
(Contract inkl. Startlistennachricht), §9 (Konfigurationsbesitz).

## Transport und Ausfallgrenzen

Implementiert wie in MODULAR_ARCHITECTURE.md §7 (Transportentscheidung),
§8 (Output-Target-Modell) und §10 (Failure Isolation) festgelegt — Retry-
Timing, Coalescing, `STALE`-Zustand, Nachrichtenlimits, ws/wss-Policy. Keine
Abweichung vom Zieldesign bekannt.

## Netzwerkvertrag

Vollständige, verbindliche Tabelle: MODULAR_ARCHITECTURE.md §4.
Kurzreferenz:

| Port  | Dienst                                             |
| ----- | --------------------------------------------------- |
| 4444  | WinLaufen (Bridge → WinLaufen, nur ausgehend)        |
| 44440 | Live Server: Web View / Public HTTP / Read API       |
| 44441 | Live Server: Browser-WebSocket + Bridge-Ingest       |
| 44442 | Bridge Control (nur vertrauenswürdiges LAN, v0.1 ohne Auth) |

Installation prüft nur die eigenen Listener-Ports des gewählten Profils;
kein Ersatzport bei Belegung. Linux-Firewalls bleiben unverändert; Windows
erhält nur die profilabhängigen Private-/Domain-Regeln.

## Installationsprofile

Details: [INSTALLATION.md](INSTALLATION.md).

| Profil            | Prozesse             | systemd-Units (Linux)                        |
| ------------------ | --------------------- | ---------------------------------------------- |
| All-in-One         | Bridge + Live Server  | `winlaufen-bridge`, `winlaufen-live-server`    |
| Bridge only        | Bridge                 | `winlaufen-bridge`                             |
| Presentation Node  | Live Server            | `winlaufen-live-server`                        |

- All-in-One = zwei getrennte Prozesse, kein kombinierter Prozess, kein
  In-Process-Kurzschluss für LOCAL
- Bridge-Konfigurationsort: `winlaufen.bridge.config` Systemproperty falls
  gesetzt (z. B. `/etc/winlaufen-web/bridge.properties`), sonst
  `${user.home}/.winlaufen-web/config.properties`

## Bekannte Prototyp-Einschränkung

Bridge-Ingest ist authentifiziert, nutzt in der Prototype Baseline aber
weiterhin ein bekanntes Default-Secret. Verbindliche Details und
Einsatzgrenzen: README.md, Abschnitt "Known prototype security limitation".
