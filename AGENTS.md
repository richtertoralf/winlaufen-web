# WinLaufen Web - Agent Instructions

## Project goal

WinLaufen Web is a small, open-source bridge for WinLaufen.

It connects read-only to the existing WinLaufen Sprecher-PC LAN interface and
provides a modern responsive web interface for results.

The project must remain simple, inexpensive to operate, easy to understand,
and easy for sports clubs to deploy.

Do not turn this project into a platform, enterprise stack, or framework-heavy
application.

## Supported deployment topology

The same application must work in both of these configurations:

1. Bridge on the same Windows computer as WinLaufen
   - WinLaufen source normally `localhost:4444`.

2. Bridge on another Windows or Linux computer in the same LAN
   - WinLaufen source is the IP address or hostname of the WinLaufen computer.

A separate bridge computer is a normal and important deployment scenario.
Nothing in the architecture may require installation on the WinLaufen PC.

The one-computer setup must nevertheless remain fully supported for clubs that
only have one computer.

Development is performed on Linux, but production code must be portable between
Windows and Linux.

Do not introduce Linux-only assumptions into application code.

## v0.1 supported sports

Protocol behavior verified for the documented scenarios:

- running competitions
- biathlon

Do not implement WinSpringen / ski jumping based on assumptions.
Support may only be added when verified protocol data is available.

## WinLaufen protocol

WinLaufen is the authoritative data source.

WinLaufen Web observes, transports and renders documented wire values. It must
preserve clocks, ranks, bibs, times, gaps, shooting values, classes, headers,
cells and indices exactly as supplied, without domain plausibility checks,
correction, replacement, reinterpretation or reordering. Structural protocol
validation and defensive resource limits remain required and are distinct from
judging whether a supplied competition value is plausible.

Connection:

- TCP
- default port 4444
- connect timeout 5 seconds
- Java Object Serialization
- bridge is strictly read-only

The bridge must never write application data back to WinLaufen.

The parser must correctly handle Java serialization references and stream state.
Do not parse the protocol using regexes, printable-string extraction, or guessed
byte offsets.

Use a restrictive ObjectInputFilter or equivalent defensive deserialization
mechanism.

The officially documented `java.util.Vector` message form may be narrowly
allowed when it contains a message text String and the marker `nachricht`.
v0.1 must consume such messages without breaking the protocol connection. The
latest message is shown publicly only when the instance configuration explicitly
enables it; the default is disabled.

Known protocol behavior must be documented and tested against real captures.

Do not invent undocumented protocol fields.

Do not invent a start-list protocol before real WinLaufen data exists for it.

## State semantics

WinLaufen result transmissions are complete class snapshots, not immutable
single-athlete events.

A newer snapshot is authoritative for the current state of that class.

Existing rows may change in later snapshots.

The protocol field "Aktueller Einlauf" is a zero-based row index into the
current sorted snapshot. It is not a rank.

The Sprecher class number is a zero-based index into the class array.

The WinLaufen clock is authoritative. Clock telegram arrival is also the
connection heartbeat; the numeric clock value is not interpreted for health.

Do not replace the WinLaufen clock with the local system clock.

Connection state must distinguish at least:

- connected and receiving clock telegrams
- stale / no clock telegrams arriving
- disconnected

For v0.1, the first structurally recognized `UhrHH:MM:SS` message sets the connection
to connected. Every subsequently received valid clock telegram confirms the
connection, regardless of whether its value is equal, lower, or higher than the
previous value. WinLaufen Web does not validate, correct, or plausibilize clock
progression.

Recognition requires exactly two decimal digits in each field, but imposes no
numeric ranges. Values such as `Uhr99:99:99` are therefore preserved as supplied;
this recognizes a wire message and does not claim that it is a valid time.

If no recognized clock telegram has been received for more than 4 seconds,
connection becomes stale, is closed, and reconnect begins. Local monotonic time
may be used only to measure this interval and must never replace or modify the
displayed WinLaufen clock. Reconnect attempts are immediate, then after 2
seconds, then 5 seconds, and every 10 seconds thereafter.

Every successful reconnect must create a new socket, ObjectInputStream and Java
serialization context. The last valid competition state may remain visible
during reconnect, but it must be marked stale or disconnected.

The same timeout applies while waiting for the first clock telegram after a
connection has established. `CONNECTED` means only that clock telegrams are
arriving within this interval; it says nothing about numeric progression or
competition plausibility.

## Runtimes and web application

The application is split into two independently installable Java runtimes plus
one small shared library. The binding architecture is `docs/MODULAR_ARCHITECTURE.md`.

- `winlaufen-web-contract`: versioned snapshot/ACK contract. Library, no runtime.
- `winlaufen-web-bridge`: WinLaufen source adapter, canonical state, organiser
  configuration, output fan-out and Bridge Control.
- `winlaufen-web-live-server`: authenticated bridge ingest, published state,
  public HTTP API, browser WebSocket and the Web Viewer.

The bridge must never import live-server packages and the live server must never
import bridge packages or WinLaufen protocol classes. A live-server installation
must not require `ObjectInputStream`-based adapters.

The local web application is a complete v0.1 feature, not a demo.

It must work:

- on localhost
- from other browsers in the same LAN
- with multiple browser clients
- without Internet access

An All-in-One installation therefore starts both runtimes as two processes. There
is no in-process shortcut and no shared store between them.

Default ports: Bridge Control `127.0.0.1:8090`, live-server HTTP `0.0.0.0:8080`,
live-server WebSocket `0.0.0.0:8081`. The live server uses port 8081 for browser
WebSockets and, over a separate path with its own handshake rules, for bridge
ingest. If one of a runtime's own ports is occupied, that runtime must fail with
a clear error instead of choosing another port.

After a browser connects by WebSocket, the live server immediately sends a
complete state snapshot and then live updates. Every published state has a
monotonically increasing `publicationRevision`. HTTP state retrieval remains
available for diagnostics and fallback use.

Do not enable CORS. Bridge Control configuration changes use only `POST` with
`application/x-www-form-urlencoded` and Origin validation. Browser WebSocket
handshakes also validate Origin. HTTP and WebSocket intentionally use different
ports: an HTTP page such as `http://10.77.0.18:8080` connects to
`ws://10.77.0.18:8081/live/v1`. Accept the Origin of the WinLaufen Web HTTP page
when its hostname or IP matches the WebSocket request host; do not require the
Origin port to equal the WebSocket port. Reject foreign Origins. Validate the
WinLaufen target host and keep its port fixed at 4444.

Bridge ingest is authenticated with a per-target bearer secret and is not
Origin-dependent. This is transport authentication between our own components,
not user management: do not add accounts, roles or login for v0.1.

Incoming WebSocket messages must be bounded before they are assembled in memory.
Ingest payloads are limited to one contract snapshot; browser connections are
read-only and get a much smaller limit.

Keep the wire format simple and stable.

The frontend must use plain:

- HTML
- CSS
- JavaScript

No frontend framework.

## Web Viewer

The Web Viewer replaces the former "Renderer" term. It is served only by the
live server and speaks only to the live server's public API. It never needs a
bridge address and never receives bridge configuration, output targets,
endpoints or credentials.

The Web Viewer must be responsive and usable on desktop, tablet and phone.

Main modes:

- Startliste
- LIVE
- Ergebnisse

In v0.1, Startliste displays a clear notice that participant data is not yet
available while no verified start-list protocol exists. Do not invent a
replacement interface.

Running and biathlon may expose different table columns.

Do not hard-code one global running-only result schema.

Render from the verified WinLaufen table/header data or normalized equivalent.

LIVE follows the class of the newest result snapshot, using the transmitted
class index. Do not pin it to the first class ever seen. The manual class
selection in Ergebnisse stays under user control.

## Output targets

Outputs are a fan-out, not an exclusive selection. There is no `OutputMode`.

The bridge holds `0..n` `OutputTargetConfig` instances, including several of the
same type. Target types:

- LOCAL
- SELFHOST
- RICHTER_PROJECTS

Rules:

- LOCAL is a regular output target, not a special case of the runtime. It uses
  the same `LiveOutputAdapter`, the same contract and the same ACK/retry/resync
  path as a remote target. Only endpoint, TLS policy and credentials differ.
- Every enabled target owns an independent worker, socket, connection state,
  retry counter, ACK state and last error. A slow or broken target must not
  block the source, the canonical state or another target.
- Reconfiguring the target list must not disconnect unchanged targets.
- Retry per target: immediately, then after 2 s, then 5 s, then every 10 s.
  New snapshots must never shorten a pending retry wait.
- A target holds at most the newest unconfirmed full snapshot. No shared global
  queue, no unbounded event backlog, no delta history.
- All targets share the same canonical competition/state model.

Transport policy: `wss` is always allowed. Plain `ws` is allowed only for
`localhost` and for loopback/link-local/private IP address literals. Every other
host, including every DNS name, requires `wss`. `RICHTER_PROJECTS` always
requires `wss`. Do not add DNS or geo lookups to refine this rule.

Do not build temporary local-only structures that later require an architectural
rewrite to enable remote output.

## Simplicity requirements

Keep dependencies to an absolute minimum.

Preferred stack:

- Java
- Maven as build tool
- JDK standard library wherever practical
- plain HTML/CSS/JavaScript

Maven is a build tool, not an application framework.

Forbidden unless explicitly approved:

- Spring
- Spring Boot
- Quarkus
- Micronaut
- React
- Vue
- Angular
- Electron
- Node.js backend
- Redis
- SQL database
- MongoDB
- Docker as a runtime requirement
- nginx as a runtime requirement
- external message brokers

A small focused library is acceptable when it clearly reduces complexity or
risk, for example for standards-compliant WebSocket support.

Do not implement a complex protocol such as RFC 6455 manually merely to avoid
one small dependency.

Every added runtime dependency must have a concrete justification.

## Known prototype security limitation

The bridge ingest of the live server is authenticated, but the prototype baseline
deliberately keeps a well-known default development secret working. Anyone who
can reach the ingest WebSocket and knows it can impersonate the bridge and forge
the published competition state. This is accepted for the prototype and is
documented in README.md; the deployment limits stated there are binding. Do not
present it as solved, and do not build a provisioning or pairing system for it
without an explicit request.

## Persistence

Runtime competition state should stay in memory.

Persist only configuration that actually needs to survive application restart.

For v0.1, persist the organiser configuration as `java.util.Properties` in
`${user.home}/.winlaufen-web/config.properties`. It is owned by the bridge and
edited only through Bridge Control.

The live server has no organiser configuration. Its purely technical deployment
parameters (bind addresses, ports, allowed channel, ingest credential) come from
Java system properties and are never delivered to browsers.

Do not add a database for v0.1.

## Installation profiles

Installation and network/runtime configuration are strictly separated. The
installer asks for exactly one thing: the role of the machine.

- **All-in-One**: bridge + live server. Default and recommended profile,
  intended for installation directly on the WinLaufen PC. With WinLaufen on the
  same machine this must work with no further configuration.
- **Bridge only**: bridge alone. Valid installation state even without any
  output target.
- **Presentation Node**: live server / Web View alone. Needs no bridge address
  during installation.

The installer must never ask for a WinLaufen IP, a target IP, a hostname, a URL,
a WSS address or any other event-dependent network parameter, and must never
block an installation because such a value is still unknown. Those values belong
in Bridge Control after installation.

Supported: Debian, Ubuntu 24.04/26.04 and Raspberry Pi OS for all three
profiles; Windows 11 for All-in-One and Bridge only. Presentation Node on
Windows is deliberately not supported.

Existing runtime configuration must survive a reinstall or upgrade untouched.
Defaults are only created on a genuine first installation.

Use these terms consistently in user and operations documentation: All-in-One,
Bridge only, Presentation Node, Bridge Control, Output Target, Live Server,
Web View. Do not introduce parallel names such as "Web only", "Server only",
"Renderer Mode", "Remote Mode" or "Combined Mode" for the same thing.

User-facing and operations documentation (README.md and docs/) is written in
German. Established technical terms stay as they are.

## Build and packaging

Maven multi-module build: `contract`, `bridge`, `live-server`. The root POM is an
aggregator and contains no runtime classes. Packaging produces two executable
JARs; the contract stays a plain library.

`installer/common/build-dist.{sh,ps1}` assembles a distribution and can bundle a
`jlink`-reduced Java runtime, so an end user does not have to prepare a JDK. The
runtime is always platform-specific; do not attempt a cross-build. Linux
services run under systemd, Windows background services run as scheduled tasks
triggered at startup.

Use one source tree for Windows and Linux.

Application/business logic must remain platform-neutral.

Target runtime platforms:

- Windows x64
- Linux amd64

Architecture should not unnecessarily prevent later Linux arm64 packaging.

The end user should not need to install a JDK manually once packaged releases
are provided.

Packaging may use jpackage.

Do not build platform-specific behavior into core application logic.

## Testing

Real captured WinLaufen data is the source of truth.

Tests must cover verified running and biathlon examples.

Where captured data and assumptions conflict, captured data wins.

Regression fixtures must not be silently modified to make tests pass.

Add a new fixture when new protocol behavior is discovered.

Tests must also pin the module boundaries and the runtime behaviour that a single
unit test cannot show: contract limits, fan-out over real WebSocket connections,
failure isolation, retry backoff, full-snapshot resync and the browser revision
guard. `devtools/smoke-fanout.sh` covers the reproducible two-process,
multi-endpoint case and needs no WinLaufen installation.

## Development rules

Before changing code:

1. Read AGENTS.md.
2. Read the protocol documentation.
3. Inspect relevant real test fixtures.
4. Understand existing tests.
5. Make the smallest coherent change.

Do not add speculative features.

Do not perform unrelated refactors.

Do not replace simple working code with abstractions without a demonstrated
need.

Prefer clear code over clever code. Write normally formatted, reviewable Java:
one statement per line, normal line lengths. Minified or single-line source
destroys stack-trace line numbers, diffs and blame and is not acceptable.

Keep architecture complete but implementation small.

Update documentation and tests together with behavioral changes.

Before declaring work complete, run the full available test suite and report
the exact commands and results.
