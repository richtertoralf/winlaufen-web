# WinLaufen Web — Product Specification

## 1. Purpose

WinLaufen Web is a small open-source bridge for WinLaufen.

It connects read-only to the existing WinLaufen Sprecher-PC LAN interface and
provides a modern responsive browser interface for competition results.

The open-source version is intended to be simple enough for sports clubs to use
without additional server infrastructure or recurring costs.

## 2. Product principle

WinLaufen remains the authoritative competition system.

WinLaufen Web:

- reads data from WinLaufen,
- normalizes the received state,
- distributes complete canonical snapshots to configured Live Servers,
- exposes published state through the Live Server HTTP/WebSocket service,
- renders the data in a browser,
- never writes competition data back to WinLaufen.

All documented competition values received from WinLaufen are authoritative and
remain strings where the wire supplies strings. The bridge does not validate,
correct, normalize, reinterpret, sort or replace clocks, ranks, bibs, result
times, gaps, shooting values, names, clubs, associations, table headers/cells or
current-finish values based on domain plausibility. Protocol structure, Java
types, markers and technical resource safety are still validated.

## 3. Supported deployment topology

The bridge must support both normal deployment variants.

### 3.1 Separate bridge computer

Preferred operational setup:

WinLaufen runs on the timing computer.

WinLaufen Web runs on another Windows or Linux computer in the same LAN and
connects to the WinLaufen computer over TCP port 4444.

No WinLaufen Web software needs to be installed on the timing computer.

### 3.2 Same computer

For clubs with only one computer, WinLaufen Web may run directly on the Windows
computer running WinLaufen.

In this case the source can normally be localhost:4444.

Both modes are first-class supported configurations.

## 4. Supported platforms

v0.1:

- Windows x64
- Linux amd64

The code must remain platform-neutral wherever technically possible.

A later Linux arm64 build should not require an architectural redesign.

## 5. Supported sports in v0.1

Verified and supported:

- running competitions
- biathlon

Not supported in v0.1:

- WinSpringen / ski jumping

WinSpringen must not be implemented from assumptions because no verified
Sprecher-PC capture is currently available.

## 6. Local web application

The local web application is a complete product feature.

It must work:

- on localhost,
- from other devices in the same LAN,
- without Internet access,
- with multiple browser clients at the same time.

The modular local installation therefore provides:

- embedded HTTP server,
- WebSocket live updates,
- complete initial state over WebSocket,
- state retrieval through HTTP for diagnostics and fallback,
- Bridge Control on the Bridge,
- Web Viewer on the Live Server.

Default local endpoints:

- HTTP: `0.0.0.0:8080`
- WebSocket: `0.0.0.0:8081`

Bridge Control defaults to `127.0.0.1:8090`. If one of a runtime's own ports is
already in use, that runtime stops with a clear error and does not select an
alternative port.

Upgrading a pre-modular configuration keeps the WinLaufen host, the presentation
values and the former LOCAL output; the old browser WebSocket port becomes the
port of the local ingest endpoint. The old HTTP port now belongs to the separate
live-server process and cannot be migrated into the bridge configuration, so the
bridge reports it as a start-up notice with the matching live-server option.

## 7. Bridge Control

Bridge Control configures and displays the bridge state.

At minimum it provides:

- WinLaufen host/IP,
- WinLaufen connection state,
- current WinLaufen clock,
- independently enabled output targets and their runtime state,
- the single Presentation Config.

TCP port 4444 is the protocol default and does not need to be prominent in the
normal user interface.

## 8. Output targets

Each target has one of these types:

- LOCAL
- SELFHOST
- RICHTER_PROJECTS

Targets are not exclusive. A Bridge may serve multiple targets, including
multiple targets of the same type. Every enabled target uses an independent
outgoing WebSocket connection, retry state and full-snapshot resynchronization.
LOCAL uses the same adapter and contract as remote targets.

## 9. Web Viewer

The Web Viewer is a public audience view for spectators on phones, tablets and
desktops, not a Sprecher-PC operator workspace. A compact header and navigation
leave the available area to exactly one competition view at a time.

Main navigation:

- Startliste
- LIVE
- Ergebnisse

### LIVE

LIVE reacts to WinLaufen result snapshots.

When a new finish/result snapshot arrives:

- use the transmitted class index, on every snapshot and not only the first one,
  so LIVE follows WinLaufen when it switches to another class,
- display the complete current class result,
- use the transmitted current-finish index to identify the current athlete,
- temporarily highlight that row; the emphasis fades out and is suppressed when
  the viewer prefers reduced motion.

The current-finish index is not the athlete's rank.

### Ergebnisse

The user selects a class manually.

The selected class remains selected even if other classes receive new results.

### Startliste

The UI position exists from the beginning.

While no verified start-list protocol exists, the view displays a clear notice
that participant data is not yet available.

No unsupported WinLaufen start-list protocol may be invented.

Functionality requiring a future start-list interface is implemented only when
verified protocol data is available.

### Public display configuration

The instance configuration controls presentation of the exact headers
`Verein`, `Vbd`, `Nation` and `Schießen`. Club, association and shooting default
to visible; nation defaults to hidden. WinLaufen server messages are retained
internally and appear as a compact notice only when `showPublicMessages` is
enabled; its default is false. These options alter only the public presentation.
They never remove or modify data in normalized state.

## 10. Running table

Verified running result columns:

- Rang
- StNr
- Name, Vorname
- Verein
- Vbd
- Laufzeit
- Rückstand

## 11. Biathlon table

Verified biathlon result columns:

- Rang
- StNr
- Name, Vorname
- Verein
- Vbd
- Schießen
- Gesamtzeit
- Rückstand

The Web Viewer must therefore not be hard-coded to one running-only table schema.

## 12. Connection health

The WinLaufen clock is authoritative.

It is also used as the application heartbeat.

At minimum distinguish:

- connected and receiving clock telegrams,
- stale / no clock telegrams arriving,
- disconnected.

The v0.1 policy is:

- TCP port 4444 and connect timeout 5 seconds,
- the first syntactically valid `UhrHH:MM:SS` message sets the state to
  connected,
- syntax means the `Uhr` prefix followed by three fields of exactly two decimal
  digits; no numeric range is imposed, so `Uhr99:99:99` is preserved,
- every subsequently received valid clock telegram confirms the connection,
  regardless of whether its value is equal, lower, or higher than before,
- WinLaufen Web does not validate, correct, or plausibilize clock progression,
- stale when no valid clock telegram has been received for more than 4 seconds,
- the same deadline starts with a fresh connection and closes/reconnects a
  stream that never supplies its first clock telegram,
- close a stale connection and start reconnecting,
- reconnect immediately, then after 2 seconds, then 5 seconds, then every 10
  seconds,
- create a new socket, `ObjectInputStream` and Java serialization context after
  every reconnect.

The last valid competition state may remain visible during reconnect, but its
connection state must be shown as stale or disconnected.

Local monotonic time may be used only to measure the 4 second heartbeat interval.
It must never replace or modify the displayed WinLaufen clock.

The API health values are exactly `DISCONNECTED`, `CONNECTED` and `STALE`.
`CONNECTED` describes continued telegram reception only, not clock progression.

## 13. Simplicity

The project must remain small and understandable.

Preferred technology:

- Java
- Maven
- plain HTML
- plain CSS
- plain JavaScript
- embedded HTTP/WebSocket functionality

No frontend build pipeline is required.

Avoid unnecessary infrastructure.

Not part of v0.1:

- database
- Redis
- message broker
- Docker runtime requirement
- nginx runtime requirement
- Node.js backend
- Spring
- Spring Boot
- Quarkus
- Micronaut
- React
- Vue
- Angular
- Electron
- accounts
- billing
- payment
- SaaS administration

Small focused dependencies are allowed only when they reduce complexity or
security risk compared with implementing a protocol manually.

## 14. Local configuration and web security

Configuration is stored as `java.util.Properties` in:

`${user.home}/.winlaufen-web/config.properties`

No database is used. A focused JSON dependency is confined to the versioned
Bridge-Live-Server contract. The WinLaufen target host must be validated. Its
port is fixed at 4444.

Bridge Control and the public web service do not enable CORS. Bridge Control configuration changes use only
`POST` with `application/x-www-form-urlencoded` and require a valid Origin.
Browser WebSocket connections also require a valid Origin.

HTTP and WebSocket intentionally use different ports. A page loaded from
`http://<live-server>:8080` connects to `ws://<live-server>:8081/live/v1`, so its
browser Origin is `http://<live-server>:8080`. The Origin hostname or IP must
match the WebSocket request host. Origin port 8080 is accepted for the WebSocket
on port 8081; equality with the WebSocket port is not required. Foreign Origins
and requests without an Origin are rejected. Bridge ingest uses the distinct
`/bridge/v1/channels/<channel>` path and Bearer authentication instead of a
browser Origin.

Plain `ws` is accepted only for `localhost` and for loopback, link-local and
private IP address literals. Every other host, including every DNS name,
requires `wss`; `RICHTER_PROJECTS` always requires `wss`.

Incoming WebSocket messages are limited before they are assembled in memory:
ingest to at most one contract snapshot, browser connections to a small payload.

This prototype release keeps a known default ingest secret working. The concrete
manipulation risk and the binding deployment limits are documented in README.md
under "Known prototype security limitation".

## 15. Browser synchronization

Normal startup is:

1. load HTML,
2. connect WebSocket,
3. receive a complete snapshot immediately after connection,
4. receive live updates.

`GET /api/v1/state` provides the complete initial/fallback state. Every
published state has a monotonically increasing `publicationRevision`; live
WebSocket messages are complete authoritative snapshots.

No general event bus or additional delta protocol is required.

Every WebSocket `snapshot`, including one after reconnect, is authoritative and
fully synchronizes browser tables. Revisions delivered to an individual client
must never decrease.

## 16. Installation goal

The final end user should not have to install a development environment.

Packaged releases should contain the required Java runtime.

Target experience:

install/start WinLaufen Web,
configure WinLaufen host,
open Web Viewer,
use it in the LAN.
