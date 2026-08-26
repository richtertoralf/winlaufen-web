# WinLaufen Sprecher-PC LAN Protocol

## Status

This document distinguishes verified observations from unknown behavior.

Do not implement undocumented behavior by guessing.

Evidence and provenance are kept distinct:

- Official documentation: the WinLaufen Sprecher-PC LAN protocol description.
  It is a source for documented object meanings, but the source document itself
  is currently not stored in this repository.
- Captured wire evidence: the immutable PCAP fixtures under
  `testdata/protocol/`, including serialized types, object order, values and TCP
  direction.
- Observed application behavior: the original Sprecher-PC display and the
  WinLaufen UI/workflow used while producing a capture. Such observations give
  scenario context but are not additional wire fields.

Statements below identify documented, captured or UI-observed behavior where
that distinction matters. A WinLaufen UI value must not be treated as a wire
field unless it was also captured.

## Transport

Verified:

- TCP
- server: WinLaufen
- client: Sprecher-PC / WinLaufen Web Bridge
- default port: 4444
- Java Object Serialization
- read-only from the bridge perspective

In captured sessions no client-to-server application payload was observed.

## Java serialization

A fresh connection starts with Java serialization stream header:

AC ED 00 05

Java object reference handles are reused inside a connection.

A parser must therefore maintain the serialization context for the lifetime of
the connection.

After disconnect/reconnect the old decoding context must be discarded.

Do not parse using printable strings, regexes or fixed byte offsets.

## Clock

WinLaufen sends strings of the form:

UhrHH:MM:SS

Observed approximately once per second.

The WinLaufen clock is authoritative and is used as heartbeat.

## Competition/result block

Documented/observed logical sequence:

0. String
   competition type / competition section

1. Integer
   evaluation mode

2. Integer
   class count

3. String[]
   class names

4. int[]
   rounds or team size

5. Integer
   WinSpringen position

6. Integer
   Sprecher class number

7. Integer
   round / heat

8. Integer
   current round/finish

9...
   Object[] result rows

followed by:

String "tabelle"

String[] table headers

String "ende"

## Index semantics

Verified:

Sprecher class number:
- zero-based index into the class array.

Current finish:
- zero-based index into the current transmitted result rows.
- not a rank.

## Snapshot semantics

Verified:

Result transmissions contain a complete current class snapshot.

They are not merely one-athlete delta events.

A later snapshot may update rows that were already transmitted earlier.

The newest valid snapshot is authoritative for that class.

## Running

Verified competition type:

Standardwettkampf

Observed evaluation mode:

1

Verified result headers:

- Rang
- StNr
- Name, Vorname
- Verein
- Vbd
- Laufzeit
- Rückstand

Example rows observed include:

1 | 111 | KREISSL Tobias | SSV Neuhausen | SVS | 2:08:55.9 | 0:00:00.0
2 | 112 | EISENLAUER Sebastian | SC Sonthofen | BSVA | 2:09:08.0 | 0:00:12.1
3 | 113 | TSCHARNKE Tim | SV Biberau | TSV | 2:09:23.7 | 0:00:27.8

See:

testdata/protocol/running/session.pcapng

SHA256:

8599e0dcec5dfcfacb851b40108fae047b84ea524e77fbff320111e7af2cd7ce

## Biathlon

WinLaufen UI configuration of the captured demo:

Sport:
Biathlon

Competition type:
Standardwettkampf

Evaluation:
nach Altersklassen

Observed evaluation mode:

1

Verified result headers:

- Rang
- StNr
- Name, Vorname
- Verein
- Vbd
- Schießen
- Gesamtzeit
- Rückstand

Example shooting fields:

1 0 2 0
0 1 1 2
0 3 0 0
5 5 5 5

Verified behavior:

- shooting values are part of the result row,
- entering shooting data alone does not result in a separately visible
  Sprecher-PC result update in the tested workflow,
- the shooting values appear together with the athlete result when a
  running/finish time exists,
- later class snapshots may contain changed shooting values for athletes that
  were already present.

See:

testdata/protocol/biathlon/session.pcapng

SHA256:

c43dcf63640de2b55e3f1864afb84dd210b10f740fcc622da5666fdf13397ec5

## Text messages

The official protocol documentation describes server messages represented as a
`java.util.Vector` containing a message text `String` and the marker
`"nachricht"`. No repository fixture currently demonstrates such a message.

v0.1 must narrowly allow this documented Vector structure during safe
deserialization and consume it without destroying the protocol connection. Other
Vector contents are rejected. No further message UI is required.

## WinSpringen

The official protocol contains WinSpringen-specific fields.

WinSpringen is not supported by v0.1 because no usable licensed
Sprecher-PC capture is currently available.

Do not infer WinSpringen behavior from running or biathlon data.

## Start lists

A future WinLaufen interface may provide complete start-list data.

No verified LAN start-list wire format is currently available for this project.

Do not invent one.

## Unknown / still subject to evidence

Examples:

- additional evaluation modes,
- relay/team-specific row formats,
- pursuit-specific behavior,
- DNS/DNF/DSQ representations,
- successful reconnect snapshot behavior,
- future start-list transport,
- WinSpringen-specific result structures.

The existing Biathlon capture begins in the middle of an established Java
serialization stream. It remains valid captured evidence for the documented
Biathlon scenario, including row structure, indices and snapshot replacement.
References whose original objects precede the capture cannot be independently
resolved from that PCAP; this limitation is recorded in its fixture analysis
and does not invalidate the observed values that are present.

New behavior must be documented using real protocol evidence before production
logic depends on it.
