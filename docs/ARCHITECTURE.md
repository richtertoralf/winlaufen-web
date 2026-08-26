# WinLaufen Web — Architecture

## 1. Design goals

The architecture must be:

- simple,
- cross-platform,
- local-first,
- read-only toward WinLaufen,
- usable without Internet access,
- extensible to remote output without replacing the local core.

## 2. Data flow

WinLaufen
    |
    | TCP 4444
    | Java Object Serialization
    v
Protocol Adapter
    |
    v
Normalized State
    |
    +-------------------+
    |                   |
    v                   v
HTTP API            WebSocket Publisher
    |                   |
    +---------+---------+
              |
              v
       Browser Renderer

The normalized state is also the common source for future output adapters.

## 3. Major components

### WinLaufen connection

Responsibilities:

- connect to configurable host,
- use TCP port 4444,
- use a 5 second connect timeout,
- reconnect after connection loss,
- consume Java Object Serialization stream,
- never send application data to WinLaufen,
- create a fresh decoding context after reconnect.

Connection lifecycle for v0.1:

- the first syntactically valid `UhrHH:MM:SS` message sets the state to
  connected and clock advancing,
- later confirmation requires a clock value advanced relative to the last
  accepted value; repeated equal values are not progress,
- `23:59:59` to `00:00:00` is valid progress,
- stale after more than 4 seconds without accepting an advanced clock value,
- close a stale connection and reconnect,
- retry immediately, then after 2 seconds, then 5 seconds, and every 10 seconds
  thereafter.

The last valid competition state may remain visible during reconnect, marked
stale or disconnected.

Local monotonic time is used only to measure the 4 second interval. It never
replaces the displayed WinLaufen clock.

### Protocol adapter

Responsibilities:

- safely deserialize allowed Java types,
- understand the documented object sequence,
- preserve Java serialization reference semantics,
- reject unexpected or unsafe object types,
- produce normalized state.

Do not use regex/string scraping as the protocol parser.

### State store

The active competition state is held in memory.

It contains at least:

- connection status,
- current WinLaufen clock,
- competition metadata,
- classes,
- current class snapshots,
- table headers,
- current-finish information.

A newly received full class snapshot is authoritative for that class.

### HTTP server

Embedded in the application.

Provides:

- static Dashboard,
- static Renderer,
- initial state API,
- configuration API,
- health information.

It must be reachable from other devices in the LAN when configured accordingly.

The default bind address and port are `0.0.0.0:8080`. A bind failure is fatal
and reported clearly; the server does not silently choose another port.

### WebSocket server

Provides live state updates to connected browser clients.

Browser startup pattern:

1. load application,
2. establish WebSocket connection,
3. receive a complete state snapshot immediately,
4. receive live state updates.

The default bind address and port are `0.0.0.0:8081`. A bind failure is fatal
and reported clearly. `GET /api/v1/state` remains available for diagnostics and
fallback, but is not required before the WebSocket connection.

Every published state has a monotonically increasing revision. The minimal
message types are `snapshot`, `clock` and `classSnapshot`. No general event bus
or more elaborate delta infrastructure is required.

The protocol should remain small and versionable.

### Frontend

Plain HTML, CSS and JavaScript.

No framework and no compilation/build pipeline.

### Output abstraction

Output modes:

- LOCAL
- SELFHOST
- RICHTER_PROJECTS

All consume the same normalized state.

LOCAL is active in v0.1.

SELFHOST and RICHTER_PROJECTS exist structurally but are disabled.

For v0.1, structural support consists only of the `OutputMode` values,
availability metadata in configuration and UI, and the shared normalized state
model. No remote network adapters or speculative remote protocols are included.

No remote-output implementation may compromise the fully offline local mode.

## 4. Deployment

### Same computer

Windows:
WinLaufen -> localhost:4444 -> WinLaufen Web

### Separate computer

Windows WinLaufen PC:
WinLaufen -> LAN TCP/4444 -> Windows or Linux Bridge

The bridge may expose its web interface to the local LAN.

## 5. Platform independence

Core code must not depend on:

- systemd,
- Windows services,
- Linux filesystem layout,
- shell scripts for runtime behavior.

Platform-specific packaging/startup code must remain outside the core.

Use Java platform APIs for:

- networking,
- file paths,
- concurrency,
- HTTP,
- configuration where practical.

## 6. Persistence

Competition/result state:

- memory only.

Persist only configuration that must survive restart.

Configuration uses `java.util.Properties` at
`${user.home}/.winlaufen-web/config.properties`. No JSON configuration library
or database is used.

Do not introduce a database in v0.1.

## 7. Protocol state

A TCP connection owns one Java serialization context.

On disconnect:

- discard decoder/reference state.

On reconnect:

- create a new socket, ObjectInputStream and completely fresh Java serialization
  decoder/context.

A socket being open alone does not mean the connection is healthy.

Health is based primarily on advancing WinLaufen clock messages.

## 8. Result state semantics

WinLaufen sends complete result snapshots for a class.

The application must not model received rows as immutable finish events.

Later snapshots may:

- add athletes,
- change rank,
- change gap,
- change shooting values,
- update existing rows.

The latest valid snapshot is authoritative.

The current-finish index identifies the row to highlight.

## 9. Security

The WinLaufen connection is untrusted serialized input.

Use restrictive deserialization filtering.

Only explicitly required Java classes/types may be accepted.

Do not expose arbitrary Java object deserialization to remote HTTP/WebSocket
clients.

The local web service does not enable CORS. Configuration changes are accepted
only as `POST` requests with `application/x-www-form-urlencoded` after Origin
validation. WebSocket handshakes also validate Origin.

The HTTP page and WebSocket intentionally have different endpoints:
`http://<bridge-host>:8080` and `ws://<bridge-host>:8081`. The browser therefore
sends the HTTP page Origin, including port 8080, during the WebSocket handshake.
The Origin hostname or IP must match the WebSocket request host, but its port is
not required to match the WebSocket port. The local HTTP Origin is accepted and
foreign Origins are rejected.

The WinLaufen target host is validated and its port remains fixed at 4444. v0.1
does not add CORS, accounts or authentication.

Officially documented `java.util.Vector` messages containing a message string
and the marker `nachricht` are narrowly allowed and consumed without disrupting
the protocol connection. No message UI is required for v0.1.

Remote output modes remain disabled in v0.1.
