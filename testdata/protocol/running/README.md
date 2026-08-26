# Running protocol fixture

Real WinLaufen Sprecher-PC TCP/4444 capture.

Scenario:
- Sport: running
- WinLaufen competition type: Standardwettkampf
- evaluation mode observed: 1
- two classes
- several finish events
- original Sprecher-PC used as client

Capture:
- file: session.pcapng
- packets: 454
- size: approx. 49 kB
- SHA256: 8599e0dcec5dfcfacb851b40108fae047b84ea524e77fbff320111e7af2cd7ce

Verified characteristics:
- TCP port 4444
- Java Object Serialization
- server-to-client application data only
- WinLaufen clock approximately once per second
- complete class result snapshots
- zero-based class index
- zero-based current-finish row index

Verified table schema:

Rang | StNr | Name, Vorname | Verein | Vbd | Laufzeit | Rückstand

This capture is protocol evidence.

Do not modify or replace this fixture merely to make tests pass.
