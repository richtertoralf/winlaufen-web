# WinLaufen Sprecher Web — Architecture

Kurzer IST-Stand der in [MODULAR_ARCHITECTURE.md](MODULAR_ARCHITECTURE.md)
festgelegten Zielarchitektur. Vollständige Entscheidungen, Contract-Felder
und Sicherheitsregeln stehen ausschließlich dort — hier keine Duplikate.

## Produktname

- Sichtbar: **WinLaufen Sprecher Web**
- Technisch (Kompatibilität): `winlaufen-web-*`, Java-Packages,
  Installationspfade bleiben unverändert
- Keine Web-Version von WinLaufen — nutzt nur dessen Sprecher-PC-Schnittstelle
- Bridge Control zeigt LOCAL vereinfacht als „Live-Ergebnisse im Browser"
  (ID/Typ/Endpoint/Channel/Secret verborgen); intern ein normales Output
  Target

## Module und Prozesse

```text
WinLaufen --TCP/4444 read-only--> winlaufen-web-bridge
                                      | ausgehend WS/WSS, Snapshot + ACK
                                      v
                                winlaufen-web-live-server
                                      | HTTP + Browser-WebSocket
                                      v
                                  Web Viewer
```

- Root-POM: reiner Aggregator, keine Runtime-Klassen
- `winlaufen-web-contract`: kleine Bibliothek, kein Prozess
- Bridge und Live Server: getrennte JARs, kein gemeinsamer Prozess/Store,
  kein In-Process-LOCAL-Pfad

## State und Konfiguration

- WinLaufen = Source Authority
- Bridge: memory-only Canonical State, `streamId`/`sourceRevision`, einzige
  Veranstalter-Konfiguration, unabhängige Output-Worker
- Live Server: pro Channel memory-only Published State mit eigener
  `publicationRevision`
- Browser: nur flüchtige öffentliche Kopie
- `BridgeConfig`: Source, 0..n Targets, Presentation Config
- Live Server kennt nur technische Bind-/Channel-/Ingest-Credential-Config

Details: MODULAR_ARCHITECTURE.md §5 (State Ownership), §9
(Konfigurationsbesitz).

## Transport und Ausfallgrenzen

Implementiert wie in MODULAR_ARCHITECTURE.md §7 (Transportentscheidung),
§8 (Output-Target-Modell) und §10 (Failure Isolation) festgelegt — Retry-
Timing, Coalescing, `STALE`-Zustand, Nachrichtenlimits, ws/wss-Policy. Keine
Abweichung vom Zieldesign bekannt.

## Netzwerkvertrag

Vollständige, verbindliche Tabelle: MODULAR_ARCHITECTURE.md §4.
Kurzreferenz:

| Port  | Dienst                                             |
| ----- | --------------------------------------------------- |
| 4444  | WinLaufen (Bridge → WinLaufen, nur ausgehend)        |
| 44440 | Live Server: Web View / Public HTTP/API              |
| 44441 | Live Server: Browser-WebSocket + Bridge-Ingest       |
| 44442 | Bridge Control (nur vertrauenswürdiges LAN, v0.1 ohne Auth) |

Installation prüft nur die eigenen Listener-Ports des gewählten Profils;
kein Ersatzport bei Belegung. Linux-Firewalls bleiben unverändert; Windows
erhält nur die profilabhängigen Private-/Domain-Regeln.

## Installationsprofile

Details: [INSTALLATION.md](INSTALLATION.md).

| Profil            | Prozesse             | systemd-Units (Linux)                        |
| ------------------ | --------------------- | ---------------------------------------------- |
| All-in-One         | Bridge + Live Server  | `winlaufen-bridge`, `winlaufen-live-server`    |
| Bridge only        | Bridge                 | `winlaufen-bridge`                             |
| Presentation Node  | Live Server            | `winlaufen-live-server`                        |

- All-in-One = zwei getrennte Prozesse, kein kombinierter Prozess, kein
  In-Process-Kurzschluss für LOCAL
- Bridge-Konfigurationsort: `winlaufen.bridge.config` Systemproperty falls
  gesetzt (z. B. `/etc/winlaufen-web/bridge.properties`), sonst
  `${user.home}/.winlaufen-web/config.properties`

## Bekannte Prototyp-Einschränkung

Bridge-Ingest ist authentifiziert, nutzt in der Prototype Baseline aber
weiterhin ein bekanntes Default-Secret. Verbindliche Details und
Einsatzgrenzen: README.md, Abschnitt "Known prototype security limitation".
