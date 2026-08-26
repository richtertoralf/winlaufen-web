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
- reconnect after connection loss,
- consume Java Object Serialization stream,
- never send application data to WinLaufen,
- create a fresh decoding context after reconnect.

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

### WebSocket server

Provides live state updates to connected browser clients.

Browser startup pattern:

1. load application,
2. retrieve initial state,
3. establish WebSocket connection,
4. receive live state updates.

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

Do not introduce a database in v0.1.

## 7. Protocol state

A TCP connection owns one Java serialization context.

On disconnect:

- discard decoder/reference state.

On reconnect:

- start a completely fresh Java serialization decoder.

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

Remote output modes remain disabled in v0.1.
