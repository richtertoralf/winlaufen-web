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
- exposes the state through a local HTTP/WebSocket service,
- renders the data in a browser,
- never writes competition data back to WinLaufen.

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

The bridge therefore provides:

- embedded HTTP server,
- WebSocket live updates,
- initial state retrieval through HTTP,
- Dashboard,
- Renderer.

## 7. Dashboard

The Dashboard configures and displays the bridge state.

At minimum it provides:

- WinLaufen host/IP,
- WinLaufen connection state,
- current WinLaufen clock,
- selected output mode,
- local Renderer URL,
- link/button to open the Renderer.

TCP port 4444 is the protocol default and does not need to be prominent in the
normal user interface.

## 8. Output modes

The final output concept exists from the beginning:

- LOCAL
- SELFHOST
- RICHTER_PROJECTS

### v0.1 behavior

LOCAL:
- enabled,
- fully functional,
- usable from browsers in the LAN.

SELFHOST:
- represented in configuration and UI,
- disabled/not selectable for productive operation.

RICHTER_PROJECTS:
- represented in configuration and UI,
- disabled/not selectable for productive operation.

The disabled modes must not establish productive remote connections.

They exist so that future remote operation can be activated without replacing
the core architecture.

## 9. Renderer

Main navigation:

- Teilnehmer
- LIVE
- Ergebnisse

### LIVE

LIVE reacts to WinLaufen result snapshots.

When a new finish/result snapshot arrives:

- use the transmitted class index,
- display the complete current class result,
- use the transmitted current-finish index to identify the current athlete,
- temporarily highlight that row.

The current-finish index is not the athlete's rank.

### Ergebnisse

The user selects a class manually.

The selected class remains selected even if other classes receive new results.

### Teilnehmer

The UI position exists from the beginning.

No unsupported WinLaufen start-list protocol may be invented.

Functionality requiring a future start-list interface is implemented only when
verified protocol data is available.

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

The renderer must therefore not be hard-coded to one running-only table schema.

## 12. Connection health

The WinLaufen clock is authoritative.

It is also used as the application heartbeat.

At minimum distinguish:

- connected and clock advancing,
- stale / connected but clock no longer advancing,
- disconnected.

Do not replace the WinLaufen clock with the local computer clock.

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

## 14. Installation goal

The final end user should not have to install a development environment.

Packaged releases should contain the required Java runtime.

Target experience:

install/start WinLaufen Web,
configure WinLaufen host,
open Renderer,
use it in the LAN.
