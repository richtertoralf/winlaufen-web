# WinLaufen Web

WinLaufen Web besteht aus zwei unabhängig startbaren Java-Runtimes. Die
**Bridge** liest WinLaufen strikt read-only über TCP/4444 und verteilt den
kanonischen Live-State an 0..n Ziele. Der **Live Server** hält den
veröffentlichten State und liefert den Web Viewer an Browser aus.

> **Status: Prototype Baseline.** Diese Version ist für ausgewählte Vereine in
> **kontrollierten Netzen** gedacht, nicht für einen offenen Internetbetrieb.
> Lies vor dem Einsatz den Abschnitt
> [Known prototype security limitation](#known-prototype-security-limitation).

## Module

- `winlaufen-web-contract`: kleiner versionierter Snapshot-/ACK-Vertrag
- `winlaufen-web-bridge`: WinLaufen-Adapter, BridgeConfig, Fan-out und Bridge Control
- `winlaufen-web-live-server`: authentifizierter Ingest, Public API, Browser-WebSocket und Web Viewer

Der Live Server enthält keinen WinLaufen-Protokollcode. LOCAL verwendet wie
entfernte Ziele eine echte ausgehende WebSocket-Verbindung der Bridge; beide
Runtimes bleiben getrennte Prozesse.

## Bauen

Voraussetzungen: Java 25 und Maven 3.9+.

```sh
mvn test
mvn package
```

Ausführbare Artefakte:

```text
bridge/target/winlaufen-web-bridge.jar
live-server/target/winlaufen-web-live-server.jar
```

## Starten

Getrennt:

```sh
java -jar live-server/target/winlaufen-web-live-server.jar
java -jar bridge/target/winlaufen-web-bridge.jar
```

Danach:

- Bridge Control: `http://localhost:8090/`
- Web Viewer: `http://localhost:8080/`
- Browser-WebSocket: Port 8081, Pfad `/live/v1`
- lokaler Bridge-Ingest: Port 8081, Pfad `/bridge/v1/channels/local`

All-in-One-Entwicklungsbetrieb mit weiterhin zwei Prozessen:

```sh
./devtools/start-local.sh
./devtools/status-local.sh
./devtools/restart-local.sh
./devtools/stop-local.sh
```

Einzelne Runtime verwalten:

```sh
./devtools/component.sh start|stop|restart|status bridge
./devtools/component.sh start|stop|restart|status live-server
```

Reproduzierbarer Zwei-Prozess-/Multi-Endpoint-Smoke (eine Bridge, zwei Live
Server, Ausfall, Neustart, Vollresync; benötigt keine WinLaufen-Installation):

```sh
mvn package
./devtools/smoke-fanout.sh
```

Live-Test gegen eine echte WinLaufen-Quelle:

```sh
./devtools/smoke-winlaufen-clock.sh <winlaufen-host> [port]
```

## Konfiguration

Die einzige Veranstalter-Konfiguration liegt in der Bridge unter
`${user.home}/.winlaufen-web/config.properties`. Bridge Control verwaltet
Quelle, mehrere Output Targets und die öffentliche Darstellung. Target-Secrets
werden nie über die API an den Browser zurückgegeben.

Der Live Server besitzt ausschließlich technische Konfiguration über
Java-Systemproperties:

```text
winlaufen.live.http.bind       default 0.0.0.0
winlaufen.live.http.port       default 8080
winlaufen.live.websocket.bind  default 0.0.0.0
winlaufen.live.websocket.port  default 8081
winlaufen.live.channel         default local
winlaufen.live.secret          default local-development-secret
```

### Transportregel für Output Targets

`wss://` ist immer zulässig. Unverschlüsseltes `ws://` ist nur erlaubt für
`localhost` und für **IP-Adressliterale** aus dem Loopback-, Link-Local- oder
privaten LAN-Bereich (z. B. `ws://192.168.1.20:8081/...`). Jeder andere Host —
insbesondere jeder DNS-Name — erfordert `wss://`. Das Projekt führt bewusst
keine DNS- oder Geo-Auflösung durch, um „LAN" von „Internet" zu unterscheiden;
diese rein syntaktische Regel ist der konservative Ersatz. Ein
LAN-Ziel muss für Klartext also über seine IP-Adresse konfiguriert werden.
`RICHTER_PROJECTS` erfordert unabhängig davon immer `wss://`.

### Upgrade einer v0.1-Konfiguration

Eine alte Konfiguration wird beim ersten Start deterministisch übernommen:

- `winlaufen.host` wird zu `source.host`;
- `public.show*` wird zu `presentation.show*`;
- der frühere exklusive LOCAL-Output-Modus wird zum ersten regulären Output
  Target `local`;
- `websocket.port` wird als Port des lokalen Ingest-Endpunkts übernommen.

`http.port` gehört mit der neuen Prozessgrenze zum Live-Server-Prozess und kann
nicht in die Bridge-Konfiguration migriert werden. Weicht einer der alten
Webports vom Standard ab, gibt die Bridge beim Start einen Hinweis mit der
passenden Startzeile für den Live Server aus, zum Beispiel:

```text
Hinweis: Die früheren Webports gehören jetzt zum Live-Server-Prozess.
Starte ihn mit: -Dwinlaufen.live.http.port=9080 -Dwinlaufen.live.websocket.port=9081
```

## Known prototype security limitation

**Diese Einschränkung ist bekannt, bewusst akzeptiert und noch nicht behoben.**

Der Bridge-Ingest des Live Servers ist zwar authentifiziert, verwendet aber
weiterhin ein **bekanntes, im Quelltext und in dieser README stehendes
Development-Secret** (`local-development-secret`), solange
`winlaufen.live.secret` nicht gesetzt ist. Der Live Server bindet seinen
WebSocket-Port standardmäßig auf `0.0.0.0`.

Konkrete Folge:

**Jeder Teilnehmer, der den Ingest-WebSocket auf Port 8081 erreichen kann und
das bekannte Secret kennt, kann sich gegenüber dem Live Server als Bridge
ausgeben.** Er kann damit den kompletten veröffentlichten Stand ersetzen und
insbesondere folgende Daten fälschen oder manipulieren:

- Wettkampfdaten und Wettkampfstruktur,
- die angezeigte Uhrzeit,
- Ergebnisse und Ranglisten,
- Klassenstände und Current-Finish-Markierung,
- öffentliche WinLaufen-Nachrichten.

Gefälschte Daten werden vom Live Server angenommen, bestätigt und sofort an
**alle** verbundenen Browser ausgeliefert. Die echte Bridge bemerkt das nicht.

Daraus folgen für diese Prototypversion verbindliche Einsatzgrenzen:

- Einsatz **nur** in kontrollierten Vereins- bzw. Veranstaltungsnetzen.
- Port 8081 darf **nicht** unkontrolliert aus nicht vertrauenswürdigen Netzen
  erreichbar sein.
- **Keine Portweiterleitung** des Ingest-WebSockets ins öffentliche Internet.
- Kein Betrieb in offenen Gäste-WLANs oder gemeinsam genutzten Netzen.
- Wo möglich, `winlaufen.live.secret` und das zugehörige Target-Secret in
  Bridge Control auf einen eigenen Wert setzen; das reduziert das Risiko,
  ersetzt aber keine echte Provisionierung.

Für einen späteren produktiven oder harten Internetbetrieb sind **individuell
provisionierte Secrets pro Target** erforderlich. Das ist bewusst nicht Teil
dieser Prototype Baseline und bleibt ein offenes Production-Hardening-Thema.

Der Live Server weist beim Start ausdrücklich auf das aktive Default-Secret hin.

## Unterstützte Funktion

Verifiziert sind Lauf und Biathlon. Tabellenheader, Zeilen, Indizes, Clock,
Current Finish, Schießen und Nachrichten werden ohne fachliche Korrektur
transportiert. Der Web Viewer bietet Startlisten-Platzhalter, LIVE und
Ergebnisse. WinSpringen, ein erfundenes Startlistenprotokoll, Datenbank und
Broker bleiben ausdrücklich außerhalb von v0.1.

Details: [modulare Architektur](docs/MODULAR_ARCHITECTURE.md) und
[WinLaufen-Protokoll](docs/WINLAUFEN_PROTOCOL.md).
