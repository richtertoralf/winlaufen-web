# WinLaufen Web — Architecture

Die Anwendung implementiert die in
[MODULAR_ARCHITECTURE.md](MODULAR_ARCHITECTURE.md) festgelegte modulare
Architektur. Dieses Dokument fasst den aktuellen technischen IST-Stand zusammen.

## Module und Prozesse

```text
WinLaufen --TCP/4444 read-only--> winlaufen-web-bridge
                                      |
                                      | ausgehendes WS/WSS, Snapshot + ACK
                                      v
                                winlaufen-web-live-server
                                      |
                                      | HTTP + Browser-WebSocket
                                      v
                                  Web Viewer
```

Der Root-POM ist nur Parent/Aggregator. `winlaufen-web-contract` ist eine kleine
Bibliothek. Bridge und Live Server sind getrennte ausführbare JARs und teilen
weder Prozess noch Store. Es existiert kein monolithischer Main und kein
In-Process-LOCAL-Pfad.

## State und Konfiguration

WinLaufen bleibt Source Authority. Die Bridge besitzt den memory-only Canonical
State, `streamId/sourceRevision`, die einzige Veranstalter-Konfiguration und
unabhängige Output-Worker. Jeder Live Server besitzt pro Channel einen
memory-only Published State mit eigener `publicationRevision`. Browser halten
nur eine flüchtige öffentliche Kopie.

`BridgeConfig` enthält Source, 0..n Targets und Presentation Config. Der Live
Server kennt nur technische Bind-, Channel- und Ingest-Credential-Konfiguration.

## Transport und Ausfallgrenzen

Jedes aktivierte Target besitzt einen eigenen ausgehenden WebSocket-Client,
Retryzähler, ACK-Stand, letzten Fehler und Coalescing auf den neuesten
Vollsnapshot. Retry erfolgt sofort, nach 2 s, nach 5 s und danach alle 10 s;
neue Snapshots verkürzen eine laufende Retry-Wartezeit nicht. Ein Zielausfall
beeinflusst weder Source noch andere Targets. Nach jedem Handshake wird
unabhängig vom ACK-Stand der aktuelle Vollsnapshot gesendet.

Eine Änderung der Target-Liste wird inkrementell angewendet: unveränderte
Targets behalten Verbindung, ACK-Stand und Retryzähler; nur entfernte, neue und
tatsächlich geänderte Targets werden angefasst.

Ein Target, das den Handshake abschließt, danach aber über längere Zeit keine
ACK-Fortschritte mehr liefert, wechselt in Bridge Control nach `STALE` und wird
nicht weiter als gesund dargestellt. Pro Verbindung ist höchstens der neueste
unbestätigte Vollsnapshot unterwegs; ein blockiertes Ziel kann keine
Snapshot-Historie im Speicher aufstauen.

Ein Snapshot, der den Vertrag verletzt, ist ein Datenfehler und kein
Transportfehler: die betroffene Revision wird übersprungen, die Verbindung
bleibt bestehen, und die nächste kanonische Revision stellt den Stand wieder
her. Reader-, Canonical- und Contract-Grenzen sind aufeinander abgestimmt, damit
dieser Fall im Normalbetrieb gar nicht entstehen kann.

Der Live-Server-WebSocket trennt `/bridge/v1/channels/<channel>`
(Bearer-Authentifizierung, kein Browser-Origin) von `/live/v1`
(Same-Host-Origin, read-only). Beide Pfade haben harte, vor dem Aufbau im
Speicher greifende Nachrichtenlimits: Ingest höchstens einen Vertragssnapshot,
Browser nur eine sehr kleine Nutzlast. Klartext-`ws` ist nur für `localhost`
und für Loopback-/LAN-IP-Literale zulässig; alle anderen Ziele erfordern WSS.

## Ports

- WinLaufen: TCP 4444
- Bridge Control: `127.0.0.1:8090`
- Live Server HTTP: `0.0.0.0:8080`
- Live Server WebSocket: `0.0.0.0:8081`

Portbelegung ist ein klarer Startfehler. Die vollständigen Entscheidungen,
Contract-Felder, Sicherheits- und Acceptance-Regeln stehen in der modularen
Architekturdokumentation.

## Installationsprofile

Die Modulgrenzen bilden direkt die drei Installationsprofile ab. Details in
[INSTALLATION.md](INSTALLATION.md).

| Profil | Prozesse | systemd-Units (Linux) |
|---|---|---|
| All-in-One | Bridge + Live Server | `winlaufen-bridge`, `winlaufen-live-server` |
| Bridge only | Bridge | `winlaufen-bridge` |
| Presentation Node | Live Server | `winlaufen-live-server` |

All-in-One installiert zwei getrennte Prozesse auf einem Rechner, keinen
kombinierten Prozess. Das lokale Output Target verwendet den regulären
Bridge→Live-Server-Pfad; einen In-Process-Kurzschluss gibt es weiterhin nicht.

Die Bridge findet ihre Konfiguration über die Systemproperty
`winlaufen.bridge.config`. Ohne sie gilt der Ort im Benutzerprofil
(`${user.home}/.winlaufen-web/config.properties`), mit ihr eine systemweite
Datei wie `/etc/winlaufen-web/bridge.properties`. Das Dateiformat ist identisch.

## Bekannte Prototyp-Einschränkung

Der Bridge-Ingest ist authentifiziert, verwendet in der Prototype Baseline aber
weiterhin ein bekanntes Default-Secret. Die konkrete Manipulationsmöglichkeit
und die daraus folgenden Einsatzgrenzen sind in README.md unter
"Known prototype security limitation" verbindlich dokumentiert.
