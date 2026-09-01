# Sprecher-Web — Entwicklung und Betrieb

Details, die für die Einrichtung nicht nötig sind, aber beim Entwickeln,
Diagnostizieren und beim Betrieb einzelner Runtimes gebraucht werden. Der
Einstieg steht in [../README.md](../README.md), die Installation in
[INSTALLATION.md](INSTALLATION.md).

## Aus dem Quellcode bauen

Voraussetzungen: Git und JDK 25. System-Maven ist nicht erforderlich, der
Wrapper liefert die im Repository festgelegte Maven-Version.

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

Plattformspezifisches Distributionsarchiv mit optionaler `jlink`-Runtime:

```sh
./installer/common/build-dist.sh --with-runtime
```

```powershell
.\installer\common\build-dist.ps1 -WithRuntime
```

Die Runtime ist immer plattformspezifisch; ein Cross-Build wird bewusst nicht
versucht.

## Entwicklungsbetrieb

Getrennt starten — auch im Entwicklungsbetrieb bleiben es zwei Prozesse:

```sh
java -jar live-server/target/winlaufen-web-live-server.jar
java -jar bridge/target/winlaufen-web-bridge.jar
```

All-in-One-Entwicklungsbetrieb:

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

## Tests

Reproduzierbarer Zwei-Prozess-/Multi-Endpoint-Smoke — eine Bridge, zwei Live
Server, Ausfall, Neustart, Full Resync; benötigt keine WinLaufen-Installation:

```sh
./mvnw package
./devtools/smoke-fanout.sh
```

Installer-Prüfungen ohne root und ohne systemd:

```sh
./installer/tests/run-installer-tests.sh
./installer/tests/run-release-workflow-tests.sh
```

Live-Test gegen eine echte WinLaufen-Quelle:

```sh
./devtools/smoke-winlaufen-clock.sh <winlaufen-host> [port]
```

Die manuellen Abnahmen stehen in [SMOKE_TESTS.md](SMOKE_TESTS.md).

## Konfiguration

Die einzige Veranstalter-Konfiguration liegt in der Bridge. Bridge Control
verwaltet Quelle, Output Targets und die öffentliche Darstellung.
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

Ein Live Server bedient genau einen Channel. Der Wert aus
`winlaufen.live.channel` bestimmt den Ingest-Pfad
`/bridge/v1/channels/<channel>` und wird zusätzlich gegen den Channel im
Snapshot geprüft.

## Transportregel für Output Targets

`wss://` ist immer zulässig. Unverschlüsseltes `ws://` ist erlaubt für
`localhost` und für **IP-Adressliterale** aus dem Loopback-, Link-Local- oder
privaten LAN-Bereich, zum Beispiel `ws://192.168.1.20:44441/...`. Ein Target vom
Typ `SELFHOST` darf zusätzlich per `ws://` auf ein **öffentliches
IP-Adressliteral** zeigen, zum Beispiel `ws://203.0.113.7:44441/...`; das ist der
temporäre selbst betriebene Presentation Node auf einer gemieteten Cloud-VM ohne
Domain. Dieser Fall wird nicht blockiert, aber dauerhaft gewarnt
(`EndpointPolicy.transportWarning`), in Bridge Control sichtbar und im
Bridge-Log.

Jeder andere Host — insbesondere jeder DNS-Name, und jede öffentliche Adresse
für `LOCAL` — erfordert `wss://`. Das Projekt führt bewusst keine DNS- oder
Geo-Auflösung durch; diese rein syntaktische Regel ist der konservative Ersatz.
Ein Klartextziel muss also immer über seine IP-Adresse konfiguriert werden.
Wildcard- und Multicast-Adressen sind kein Ziel und bleiben abgelehnt.
`RICHTER_PROJECTS` erfordert unabhängig davon immer `wss://`.

## Upgrade einer v0.1-Konfiguration

Eine vormodulare Konfiguration wird beim ersten Start deterministisch
übernommen:

- `winlaufen.host` wird zu `source.host`;
- `public.show*` wird zu `presentation.show*`;
- der frühere exklusive LOCAL-Output-Modus wird zum ersten regulären Output
  Target `local`;
- `websocket.port` wird als Port des lokalen Ingest-Endpunkts übernommen.

`http.port` gehört mit der Prozessgrenze zum Live-Server-Prozess und kann nicht
in die Bridge-Konfiguration migriert werden. Weicht einer der alten Webports vom
damaligen Standard ab, gibt die Bridge beim Start einen Hinweis mit der
passenden Startzeile für den Live Server aus:

```text
Hinweis: Die früheren Webports gehören jetzt zum Live-Server-Prozess.
Starte ihn mit: -Dwinlaufen.live.http.port=9080 -Dwinlaufen.live.websocket.port=9081
```

Die damaligen Ports 8080, 8081 und 8090 sind **historisch** und keine aktuellen
Standardwerte. Der Installer migriert ausschließlich diese exakten früheren
Installer-Defaults einmalig auf den festen Portblock 44440–44442; individuelle
Werte außerhalb dieser ehemaligen Defaults bleiben unverändert.
