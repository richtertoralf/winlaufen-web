# WinLaufen Web - Agent Instructions

## Project purpose

WinLaufen Web is a small bridge for WinLaufen. It connects
read-only to the existing WinLaufen Sprecher-PC LAN interface and provides a
modern, responsive web interface for live results.

The project must remain simple, inexpensive to operate, and easy for sports
clubs to deploy. Do not turn this into a platform, enterprise stack, or
framework-heavy application.

Development happens on Linux, but production code must stay portable between
Windows and Linux. Do not introduce Linux-only assumptions.

## Mandatory documentation

AGENTS.md holds the binding consequences. These documents hold the full
specification, rationale and background. Read the relevant one before
changing the corresponding area.

- `docs/WINLAUFEN_PROTOCOL.md` — wire protocol, clock telegrams, message
  types, reconnect semantics. Required before changing protocol handling.
- `docs/MODULAR_ARCHITECTURE.md` — target module, process, port and
  transport boundaries and the rationale behind them. Required before
  changing module communication or deployment topology.
- `docs/ARCHITECTURE.md` — current implementation status against the target
  architecture above. Read alongside `MODULAR_ARCHITECTURE.md`, not instead
  of it.
- `docs/PRODUCT_SPEC.md` — full product scope, supported use cases and
  dependency constraints.
- `docs/INSTALLATION.md` — installation profiles, platform support,
  service and firewall details. Required before installer/packaging changes.
- `docs/DEVELOPMENT.md` — full build, dev-run and diagnostic workflows.
- `docs/SMOKE_TESTS.md` — manual acceptance tests that need real hardware
  and a real WinLaufen installation.

## Repository map

- `contract/` — `winlaufen-web-contract`: versioned snapshot/ACK contract,
  library only, no runtime.
- `bridge/` — `winlaufen-web-bridge`: WinLaufen source adapter, canonical
  state, organiser configuration, output fan-out, Bridge Control.
- `live-server/` — `winlaufen-web-live-server`: authenticated bridge
  ingest, published state, public HTTP API, browser WebSocket, Web Viewer.
- `installer/` — Linux/Windows installer scripts, dist packaging, installer
  tests.
- `devtools/` — local dev-run lifecycle and smoke-test scripts.
- `testdata/protocol/` — captured real WinLaufen wire fixtures; source of
  truth for protocol tests.
- `docs/` — full specification, see "Mandatory documentation" above.

## Hard invariants

### WinLaufen protocol

Before changing protocol handling, read `docs/WINLAUFEN_PROTOCOL.md`.

- Port 4444 is read-only. The bridge must never write application data back
  to WinLaufen.
- Parse with Java Object Serialization semantics via a restrictive
  `ObjectInputFilter`. Never parse with regexes, printable-string
  extraction, or guessed byte offsets.
- Known protocol behavior must be documented and tested against real
  captures. Do not invent undocumented fields, and do not invent a
  start-list protocol before real WinLaufen data exists for it.
- Class snapshots are complete and authoritative for that class, not
  incremental single-athlete events; existing rows may change in later
  snapshots.
- The WinLaufen clock is authoritative and its arrival is also the
  connection heartbeat. Never replace it with local system time, and never
  validate, correct or plausibilize its progression.
- Stale/reconnect timing (no clock for >4s, backoff immediate/2s/5s/10s) is
  defined in `docs/WINLAUFEN_PROTOCOL.md`. Do not change it in only one
  place.

### State and architecture

Before changing module communication, read `docs/MODULAR_ARCHITECTURE.md`.

- Bridge and live server are separate processes: no shared store, no
  in-process shortcut. The bridge must never import live-server packages;
  the live server must never import bridge packages or WinLaufen protocol
  classes.
- Fixed default ports: Bridge Control `44442`, live-server HTTP `44440`,
  live-server WebSocket `44441` (browser and bridge ingest share this port
  on different paths). A runtime must fail loudly if its own port is
  occupied — never fall back to a different port.
- Never enable CORS. Bridge Control accepts only `POST` with Origin
  validation. Browser WebSocket handshakes validate Origin by host, not by
  port.
- Outputs are a fan-out (`0..n` `OutputTargetConfig`, types `LOCAL` /
  `SELFHOST` / `RICHTER_PROJECTS`), not an exclusive `OutputMode`. Every
  target has its own worker/retry/ACK state; a broken target must never
  block another target or the source.
- Transport policy: `wss` is always allowed; plain `ws` only for
  localhost/loopback/private IP literals. `RICHTER_PROJECTS` always
  requires `wss`.
- Frontend stays plain HTML/CSS/JavaScript. No frontend framework.

### Security

- The prototype's bridge ingest deliberately keeps a well-known default
  secret (`local-development-secret`) working. This is documented and
  accepted for the prototype baseline (see README.md for the binding
  deployment limits). Do not present it as solved, and do not build a
  provisioning or pairing system for it without an explicit request.

### Dependencies and simplicity

Full rationale in `docs/PRODUCT_SPEC.md`.

- Forbidden unless explicitly approved: Spring / Spring Boot, Quarkus,
  Micronaut, React / Vue / Angular, Electron, Node.js backend, Redis,
  SQL database, MongoDB, Docker or nginx as a runtime requirement, external
  message brokers.
- Every added runtime dependency needs a concrete justification. Do not
  hand-roll a complex protocol (e.g. RFC 6455) merely to avoid one small,
  focused library.

## Deployment topology and scope

The same application must work with the bridge on the same Windows PC as
WinLaufen, and with the bridge on a separate Windows or Linux PC in the same
LAN. Nothing in the architecture may require installation on the WinLaufen
PC itself.

v0.1 protocol behavior is verified only for running competitions and
biathlon. Do not implement WinSpringen (ski jumping) or a start-list
protocol based on assumptions — only once verified WinLaufen data exists.

## Installation profiles

Full details in `docs/INSTALLATION.md`.

Three roles: **All-in-One** (bridge + live server, default), **Bridge
only**, **Presentation Node** (live server only; not supported on Windows).
Installation asks only for the role — never for a WinLaufen IP, target IP,
hostname, URL or WSS address; those belong in Bridge Control after install.
Use these names consistently; do not invent parallel terms ("Web only",
"Renderer Mode", "Remote Mode", …) for the same thing.

## Commands

Build and unit tests:

```sh
./mvnw clean package
```

Two-process fan-out smoke test (bridge + two live servers, failure/restart/
resync; no WinLaufen installation needed):

```sh
./devtools/smoke-fanout.sh
```

Installer checks without root or systemd:

```sh
./installer/tests/run-installer-tests.sh
./installer/tests/run-release-workflow-tests.sh
```

Live test against a real WinLaufen source:

```sh
./devtools/smoke-winlaufen-clock.sh <winlaufen-host> [port]
```

Distribution with bundled `jlink` runtime:

```sh
./installer/common/build-dist.sh --with-runtime
```

For dev-run lifecycle scripts, diagnostics and manual hardware tests, see
`docs/DEVELOPMENT.md` and `docs/SMOKE_TESTS.md`.

## Testing

Real captured WinLaufen data (`testdata/protocol/`) is the source of truth;
where captured data and assumptions conflict, captured data wins. Never
silently modify a regression fixture to make a test pass — add a new
fixture when new protocol behavior is discovered.

Tests must also pin module boundaries and runtime behavior a single unit
test cannot show: contract limits, fan-out over real WebSocket connections,
failure isolation, retry backoff, full-snapshot resync, the browser
revision guard.

Before declaring work complete, run the full available test suite and
report the exact commands and results.

## Development rules

Before changing code:

1. Read AGENTS.md.
2. Read the relevant document(s) from "Mandatory documentation" above.
3. Inspect relevant real test fixtures and understand existing tests.
4. Make the smallest coherent change.

Do not add speculative features. Do not perform unrelated refactors. Do not
replace simple working code with abstractions without a demonstrated need.

Prefer clear code over clever code. Write normally formatted, reviewable
Java: one statement per line, normal line lengths. Minified or single-line
source destroys stack-trace line numbers, diffs and blame and is not
acceptable.

Keep architecture complete but implementation small. Update documentation
and tests together with behavioral changes.

User-facing and operations documentation (README.md and docs/) is written
in German. Established technical terms stay as they are.
