# Biathlon protocol fixture

Real WinLaufen Sprecher-PC TCP/4444 capture.

Scenario:
- Sport configured in WinLaufen: Biathlon
- WinLaufen competition type: Standardwettkampf
- evaluation: nach Altersklassen
- evaluation mode observed: 1
- four classes
- shooting results and finish times entered manually
- original Sprecher-PC used as client

Capture:
- file: session.pcapng
- packets: 584
- size: approx. 63 kB
- SHA256: c43dcf63640de2b55e3f1864afb84dd210b10f740fcc622da5666fdf13397ec5

Verified characteristics:
- TCP port 4444
- Java Object Serialization
- complete class result snapshots
- shooting values are part of result rows
- shooting input alone is not displayed as a separate Sprecher-PC result update
- shooting values become visible with a running/finish time
- later snapshots can update already existing rows

Verified table schema:

Rang | StNr | Name, Vorname | Verein | Vbd | Schießen | Gesamtzeit | Rückstand

This capture is protocol evidence.

It begins midstream in an established Java serialization context and is not an
independently parseable complete stream. Synthetic parser tests use values from
`decoded.json` only as an explicitly named evidence/contract scenario; no
synthetic serialization is described as a capture fixture.

Do not modify or replace this fixture merely to make tests pass.
