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

## Web application

The local web application is a complete v0.1 feature, not a demo.

It must work:

- on localhost
- from other browsers in the same LAN
- with multiple browser clients
- without Internet access

The application therefore needs an embedded HTTP server and WebSocket support.

For v0.1, HTTP defaults to `0.0.0.0:8080` and WebSocket defaults to
`0.0.0.0:8081`. If either port is occupied, startup must fail with a clear error
instead of choosing another port.

After a browser connects by WebSocket, the server immediately sends a complete
state snapshot and then live updates. Every published state has a monotonically
increasing revision. HTTP state retrieval remains available for diagnostics and
fallback use.

Do not enable CORS. Configuration changes use only `POST` with
`application/x-www-form-urlencoded` and Origin validation. WebSocket handshakes
also validate Origin. HTTP and WebSocket intentionally use different ports: an
HTTP page such as `http://10.77.0.18:8080` connects to
`ws://10.77.0.18:8081`. Accept the Origin of the local WinLaufen Web HTTP page
when its hostname or IP matches the WebSocket request host; do not require the
Origin port to equal the WebSocket port. Reject foreign Origins. Validate the
WinLaufen target host and keep its port fixed at 4444. Do not add user management
or authentication for v0.1.

Keep the wire format simple and stable.

The frontend must use plain:

- HTML
- CSS
- JavaScript

No frontend framework.

## Renderer

The renderer must be responsive and usable on desktop, tablet and phone.

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

## Output modes

The architecture must contain the final output-mode concept from the beginning.

Output modes:

- LOCAL
- SELFHOST
- RICHTER_PROJECTS

For v0.1:

- LOCAL is enabled and fully functional.
- SELFHOST exists in architecture/configuration/UI but is disabled.
- RICHTER_PROJECTS exists in architecture/configuration/UI but is disabled.

The disabled modes must not make productive remote connections.

Do not implement SELFHOST or RICHTER_PROJECTS network adapters in v0.1. The
output-mode enum, availability metadata, configuration/UI representation and
shared normalized state model are sufficient.

Do not build temporary local-only structures that later require an architectural
rewrite to enable remote output.

All output modes must share the same normalized competition/state model.

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

## Persistence

Runtime competition state should stay in memory.

Persist only configuration that actually needs to survive application restart.

For v0.1, persist configuration as `java.util.Properties` in
`${user.home}/.winlaufen-web/config.properties`.

Do not add a database for v0.1.

## Build and packaging

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

Prefer clear code over clever code.

Keep architecture complete but implementation small.

Update documentation and tests together with behavioral changes.

Before declaring work complete, run the full available test suite and report
the exact commands and results.
