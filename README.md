# WinLaufen Web

WinLaufen Web besteht aus zwei unabhängig startbaren Java-Runtimes. Die
**Bridge** liest WinLaufen strikt read-only über TCP/4444 und verteilt den
kanonischen Live-State an 0..n Ziele. Der **Live Server** hält den
veröffentlichten State und liefert die **Web View** an Browser aus.

> **Status: Prototype Baseline.** Diese Version ist für ausgewählte Vereine in
> **kontrollierten Netzen** gedacht, nicht für einen offenen Internetbetrieb.
> Lies vor dem Einsatz den Abschnitt
> [Known prototype security limitation](#known-prototype-security-limitation).

## Installationsprofile

Bei der Installation wird genau eine Sache ausgewählt: die Rolle des Rechners.

### All-in-One — empfohlener Standard

* Bridge + Live Server auf einem Rechner
* Standard- und Defaultprofil
* geeignet für WinLaufen-PC, Sprecher-PC, separaten LAN-PC oder Raspberry Pi
* Läuft WinLaufen auf demselben Rechner, ist normalerweise **keine**
  zusätzliche Konfiguration nötig

```text
WinLaufen-PC
      |
      | TCP 4444
      v
All-in-One-Rechner
 Bridge + Live Server
      |
      | LAN/WLAN
      v
Tablet / Handy / Notebook
```

Der Installer trägt WinLaufen unter `127.0.0.1:4444` und den lokalen Live Server
als reguläres Output Target ein. Wenn WinLaufen auf einem anderen Rechner
läuft, wird nur dessen Host später in Bridge Control geändert. Der Browser muss
nicht auf dem All-in-One-Rechner laufen.

### Bridge only

* nur die Bridge
* liest WinLaufen
* verteilt an einen oder mehrere Presentation Nodes
* WinLaufen-Adresse und Output Targets werden **später** in Bridge Control
  konfiguriert

```text
WinLaufen  ->  Bridge  ->  Presentation Node A
                       ->  Presentation Node B
```

Eine Bridge ohne Output Target ist ein gültiger Installationszustand, kein
Fehler.

### Presentation Node

* nur Live Server / Web View
* für einen separaten Rechner im LAN oder WAN
* die Bridge muss diesen Node als Output Target kennen
* während der Installation ist **keine** Bridge-Adresse erforderlich

```text
Bridge  ->  Presentation Node  ->  Web View
```

### Welches Profil wann?

| Einsatzfall | Installationsprofil |
|---|---|
| WinLaufen Web direkt auf dem WinLaufen-PC | All-in-One |
| ein Rechner für Bridge und Web View | All-in-One |
| separater Rechner nur für die WinLaufen-Anbindung | Bridge only |
| separater Webserver im LAN | Presentation Node |
| entfernter Webserver / WAN | Presentation Node |

### Grundsatz

> Installation und Netzwerk-/Runtime-Konfiguration sind getrennt. Während der
> Installation werden keine WinLaufen-IP-Adressen, Target-IP-Adressen,
> Hostnamen oder URLs benötigt.

Ein Rechner kann deshalb Tage vor der Veranstaltung vollständig installiert
werden, auch wenn das Veranstaltungsnetz noch unbekannt ist. Adressen, Ziele und
TLS gehören ausschließlich in die spätere Runtime-Konfiguration über Bridge
Control.

Nicht erreichbare Quellen und Output Targets verhindern die Installation nicht.
Nach erfolgreicher Prüfung der lokalen Dienste, Listener und HTTP-Endpunkte
meldet der Installer ihren aktuellen Verbindungszustand als Betriebsdiagnose.
Auch ein noch nicht verbundener lokaler All-in-One-Datenpfad ist eine Warnung,
kein Installationsfehler. Fehler der lokalen Dienste, Listener oder
HTTP-Endpunkte bleiben dagegen harte Installationsfehler.

### Unterstützte Plattformen

| Plattform | All-in-One | Bridge only | Presentation Node |
|---|---|---|---|
| Debian (aktuell) | ja | ja | ja |
| Ubuntu 24.04 LTS | ja | ja | ja |
| Ubuntu 26.04 LTS | ja | ja | ja |
| Raspberry Pi OS (aktuell) | ja | ja | ja |
| Windows 11 | ja | ja | nein |

Presentation Node auf Windows wird bewusst nicht unterstützt; dafür bitte Linux
verwenden.

## Installation

Ausführliche Anleitung: [docs/INSTALLATION.md](docs/INSTALLATION.md).
Der Tag-basierte Ablauf für Maintainer ist unter
[docs/RELEASE.md](docs/RELEASE.md) dokumentiert.

### Release-Installation für Endanwender

Sobald Release-Pakete veröffentlicht sind:

1. passendes Archiv von [GitHub Releases](https://github.com/richtertoralf/winlaufen-web/releases) herunterladen,
2. Archiv entpacken,
3. Installer starten und das Maschinenprofil wählen.

Git, Maven, Source Checkout und ein eigener Build sind dafür nicht nötig. Ein
Paket mit gebündelter `jlink`-Runtime benötigt auch kein separat installiertes
JDK für den Betrieb.

### Developer-Installation aus dem Source Checkout

Voraussetzungen sind Git und JDK 25. Maven wird durch den Wrapper 3.9.16
bereitgestellt und muss nicht systemweit installiert sein.

```sh
git clone https://github.com/richtertoralf/winlaufen-web.git
cd winlaufen-web
./mvnw clean package
sudo ./installer/linux/install.sh
```

```powershell
git clone https://github.com/richtertoralf/winlaufen-web.git
Set-Location winlaufen-web
.\mvnw.cmd clean package
# PowerShell als Administrator:
.\installer\windows\Install-WinLaufenWeb.ps1
```

Für selbst gebaute plattformspezifische Distributionsarchive mit optionaler
Runtime:

```sh
./installer/common/build-dist.sh --with-runtime
```

```powershell
.\installer\common\build-dist.ps1 -WithRuntime
```

Unter Linux laufen die Dienste über `systemd`, unter Windows als geplante
Aufgaben mit dem Trigger „Beim Systemstart". Beide starten ohne offenes
Konsolenfenster automatisch nach einem Neustart.

Nach der Installation:

```text
Bridge Control:  http://<bridge>:44442/
Web View:        http://<live-server>:44440/
```

Der Abschlussbericht trennt die erfolgreiche lokale Installation von der
Betriebsbereitschaft externer Verbindungen. Bei Profilen mit Bridge nennt er
WinLaufen-Quelle und konfigurierte Output Targets ohne Secrets; `DISCONNECTED`,
`RETRY_WAIT` oder ein deaktiviertes Target ändern den Installationserfolg nicht.
Ein Presentation Node darf erfolgreich installiert sein und auf eine später
verbundene Bridge warten.

## Netzwerkvertrag

| Quelle | Ziel | Protokoll/Port | Zweck |
|---|---|---|---|
| Bridge | WinLaufen-PC | TCP 4444 | WinLaufen Sprecher-PC-Protokoll |
| Viewer | Live Server | TCP 44440 | Web View / Public HTTP / API |
| Browser | Live Server | TCP 44441 | Live WebSocket auf `/live/v1` |
| Bridge | Live Server | TCP 44441 | authentifizierter Bridge-Ingest auf `/bridge/v1/channels/<channel>` |
| Admin | Bridge | TCP 44442 | Bridge Control |

44440 und 44441 müssen für die gewünschten Viewer im LAN/WLAN erreichbar
sein, 44442 für die vorgesehenen Administrationsgeräte. TCP 4444 ist keine
lokale WinLaufen-Web-Freigabe: Die Bridge verbindet sich ausgehend zum
WinLaufen-PC. Der Live Server nutzt bewusst nur einen WebSocket-Listener auf
44441 für die beiden getrennt abgesicherten Pfade.

Unter Linux verändert der Installer keine Firewall. Er nennt die je Profil
benötigten Regeln; lokale oder externe Firewalls müssen passend zum
Veranstaltungsnetz gepflegt werden. Unter Windows muss die Installation in
einer PowerShell mit Administratorrechten laufen; sie richtet nur die nötigen
eingehenden Defender-Firewallregeln für Private-/Domain-Netze ein.

Bridge Control auf TCP 44442 ist ein Administrationsport. In v0.1 besitzt diese
Oberfläche bewusst keine Benutzer- oder Login-Authentifizierung. Jeder
Teilnehmer in einem Netz, aus dem der Port erreichbar ist, kann Bridge Control
grundsätzlich öffnen und die Konfiguration ändern. Deshalb darf 44442 nur in
einem vertrauenswürdigen LAN erreichbar sein: nicht im Gäste-WLAN, nicht über
unkontrollierte Portweiterleitungen und nicht direkt aus dem öffentlichen
Internet. Target-Secrets werden von der Control-API nicht ausgegeben; das ist
jedoch kein Ersatz für eine Zugriffsbeschränkung des Administrationsports.

## Module

- `winlaufen-web-contract`: kleiner versionierter Snapshot-/ACK-Vertrag
- `winlaufen-web-bridge`: WinLaufen-Adapter, BridgeConfig, Fan-out und Bridge Control
- `winlaufen-web-live-server`: authentifizierter Ingest, Public API, Browser-WebSocket und Web View

Der Live Server enthält keinen WinLaufen-Protokollcode. Das lokale Output Target
verwendet wie entfernte Ziele eine echte ausgehende WebSocket-Verbindung der
Bridge; beide Runtimes bleiben getrennte Prozesse.

## Aus dem Quellcode bauen

Voraussetzungen: Git und JDK 25. System-Maven ist nicht erforderlich.

```sh
./mvnw clean package
```

```powershell
.\mvnw.cmd clean package
```

Ausführbare Artefakte:

```text
bridge/target/winlaufen-web-bridge.jar
live-server/target/winlaufen-web-live-server.jar
```

## Entwicklungsbetrieb

Getrennt starten:

```sh
java -jar live-server/target/winlaufen-web-live-server.jar
java -jar bridge/target/winlaufen-web-bridge.jar
```

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
Server, Ausfall, Neustart, Full Resync; benötigt keine WinLaufen-Installation):

```sh
./mvnw package
./devtools/smoke-fanout.sh
```

Installer-Prüfungen (ohne root, ohne systemd):

```sh
./installer/tests/run-installer-tests.sh
```

Live-Test gegen eine echte WinLaufen-Quelle:

```sh
./devtools/smoke-winlaufen-clock.sh <winlaufen-host> [port]
```

## Konfiguration

Die einzige Veranstalter-Konfiguration liegt in der Bridge. Bridge Control
verwaltet Quelle, mehrere Output Targets und die öffentliche Darstellung.
Target-Secrets werden nie über die Control-API an den Browser zurückgegeben.

| Installationsart | Ort der Bridge-Konfiguration |
|---|---|
| Linux-Dienst | `/etc/winlaufen-web/bridge.properties` |
| Windows-Dienst | `C:\ProgramData\WinLaufen Web\bridge.properties` |
| Entwicklungsbetrieb | `${user.home}/.winlaufen-web/config.properties` |

Der Pfad wird über die Systemproperty `winlaufen.bridge.config` gesetzt; ohne
sie gilt der Ort im Benutzerprofil. Das Dateiformat ist in allen Fällen
identisch.

Der Live Server besitzt ausschließlich technische Konfiguration über
Java-Systemproperties:

```text
winlaufen.live.http.bind       default 0.0.0.0
winlaufen.live.http.port       default 44440
winlaufen.live.websocket.bind  default 0.0.0.0
winlaufen.live.websocket.port  default 44441
winlaufen.live.channel         default local
winlaufen.live.secret          default local-development-secret
```

### Transportregel für Output Targets

`wss://` ist immer zulässig. Unverschlüsseltes `ws://` ist nur erlaubt für
`localhost` und für **IP-Adressliterale** aus dem Loopback-, Link-Local- oder
privaten LAN-Bereich (z. B. `ws://192.168.1.20:44441/...`). Jeder andere Host —
insbesondere jeder DNS-Name — erfordert `wss://`. Das Projekt führt bewusst
keine DNS- oder Geo-Auflösung durch, um „LAN" von „Internet" zu unterscheiden;
diese rein syntaktische Regel ist der konservative Ersatz. Ein LAN-Ziel muss für
Klartext also über seine IP-Adresse konfiguriert werden. `RICHTER_PROJECTS`
erfordert unabhängig davon immer `wss://`.

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

Der Installer erhält gepflegte Veranstalterwerte und Target-Listen. Nur die
exakten früheren Installer-Netzwerkdefaults werden einmalig auf den festen
Portblock 44440–44442 migriert; individuelle Werte außerhalb dieser ehemaligen
Defaults bleiben unverändert.

## Known prototype security limitation

**Diese Einschränkung ist bekannt, bewusst akzeptiert und noch nicht behoben.**

Der Bridge-Ingest des Live Servers ist zwar authentifiziert, verwendet aber
weiterhin ein **bekanntes, im Quelltext und in dieser README stehendes
Development-Secret** (`local-development-secret`), solange
`winlaufen.live.secret` nicht gesetzt ist. Der Live Server bindet seinen
WebSocket-Port standardmäßig auf `0.0.0.0`. Auch der Installer erzeugt bewusst
kein eigenes Secret.

Konkrete Folge:

**Jeder Teilnehmer, der den Ingest-WebSocket auf Port 44441 erreichen kann und
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
- Port 44441 darf **nicht** unkontrolliert aus nicht vertrauenswürdigen Netzen
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
transportiert. Die Web View bietet Startlisten-Platzhalter, LIVE und Ergebnisse.
WinSpringen, ein erfundenes Startlistenprotokoll, Datenbank und Broker bleiben
ausdrücklich außerhalb von v0.1.

## Dokumentation

| Dokument | Inhalt |
|---|---|
| [docs/INSTALLATION.md](docs/INSTALLATION.md) | Installationsprofile, Linux, Windows, Dienste, Deinstallation |
| [docs/SMOKE_TESTS.md](docs/SMOKE_TESTS.md) | manuelle Abnahmetests für reale Rechner |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | technischer IST-Stand der modularen Architektur |
| [docs/MODULAR_ARCHITECTURE.md](docs/MODULAR_ARCHITECTURE.md) | vollständige verbindliche Architekturentscheidungen |
| [docs/PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md) | Produktspezifikation |
| [docs/WINLAUFEN_PROTOCOL.md](docs/WINLAUFEN_PROTOCOL.md) | WinLaufen-Protokoll und reale Evidenz |
