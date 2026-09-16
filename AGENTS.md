# Arbeitsregeln für Coding-Agenten

## Projekt und Navigation

Sprecher-Web stellt Live-Ergebnisse aus WinLaufen im Browser bereit.

Diese Datei beschreibt, wie in diesem Repository gearbeitet wird. Fachliche und
technische Projektregeln stehen an den jeweils zuständigen Stellen und werden
hier nicht wiederholt. Je nach Thema sind das README.md, `docs/`, Code, Tests
oder Konfiguration.

Die Übersicht ist eine Orientierungshilfe. Für eine Änderung zählen die
Dokumente des betroffenen Bereichs, nicht alle.

| Bereich | Dokument |
|---|---|
| Quellprotokoll, Uhr und Wettkampfzeit, Telegramme | [WINLAUFEN_PROTOCOL.md](docs/WINLAUFEN_PROTOCOL.md) |
| Module, Prozesse, Ports, Transport, Sicherheitsgrenzen, Repositorystruktur | [MODULAR_ARCHITECTURE.md](docs/MODULAR_ARCHITECTURE.md) |
| Umsetzungsstand gegenüber der Zielarchitektur | [ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Produktumfang, Plattformen, Sportarten, Abhängigkeiten, Anzeigeregeln | [PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md) |
| HTTP- und WebSocket-Schnittstelle, Datenformate | [API.md](docs/API.md) |
| Installer, Profile, Dienste, Netzwerk, Einsatzgrenzen | [INSTALLATION.md](docs/INSTALLATION.md), [installer/README.md](installer/README.md) |
| Build, Entwicklungsbetrieb, Tests, Konfiguration | [DEVELOPMENT.md](docs/DEVELOPMENT.md) |
| Releasepakete und Veröffentlichung | [RELEASE.md](docs/RELEASE.md) |
| Manuelle Abnahmen mit echter Hardware | [SMOKE_TESTS.md](docs/SMOKE_TESTS.md) |
| Texte für Anwender und Veranstalter | [BEDIENERHANDBUCH.md](docs/BEDIENERHANDBUCH.md), [README.md](README.md) |

[docs/INDEX.md](docs/INDEX.md) nennt für jedes Thema den primären Ort.

## Arbeitsweise

- Vor einer Änderung den betroffenen Code, die zugehörige Dokumentation und die
  vorhandenen Tests lesen.
- Den bestehenden Stand prüfen, statt ihn aus Annahmen zu ersetzen.
- Eine dokumentierte Festlegung nicht umgehen. Wirkt sie falsch, das melden.
- Den Scope klein und zusammenhängend halten.
- Keine spekulativen Features und keine unbeauftragten Refactors.
- Einfachen, funktionierenden Code nicht ohne Bedarf abstrahieren.
- Klarer Code vor cleverem Code.
- Die Begriffe verwenden, die in den Dokumenten stehen.

## Auftragsgrenze

- Den beauftragten Scope vollständig umsetzen. Bleibt etwas offen, das sagen.
- Nur den beauftragten Fall umsetzen. Ein Dokument erklärt Hintergrund, es ist
  kein Auftrag.
- Andere Repositories nur auf ausdrücklichen Auftrag ändern.
- Laufende oder externe Umgebungen nur auf ausdrücklichen Auftrag ändern.
- Bestehende Benutzeränderungen und Secrets nicht überschreiben.
- Branchwechsel, Commit, Push, Merge, Release und Deployment nur ausführen,
  wenn der Auftrag sie umfasst.
- Größere Verbesserungen als Folgeauftrag vorschlagen.

## Dokumentationsregel

- README.md und `docs/` sind deutsch. Etablierte Fachbegriffe bleiben
  unverändert.
- Jedes Thema gehört an seinen primären Ort nach
  [docs/INDEX.md](docs/INDEX.md). Andere Seiten verlinken dorthin.
- Verhaltensänderungen werden in der zuständigen Dokumentation nachgeführt und
  durch geeignete vorhandene oder neue Tests abgesichert, soweit der geänderte
  Bereich dies erfordert.
- `CLAUDE.md` verweist nur auf AGENTS.md. Weitere agentenspezifische Skill-
  oder Instruktionsstrukturen werden nicht angelegt.

## Verifikation und Abschluss

- Die Prüfungen ausführen, die zur Änderung passen. Welche es gibt, steht in
  [DEVELOPMENT.md](docs/DEVELOPMENT.md) und
  [SMOKE_TESTS.md](docs/SMOKE_TESTS.md).
- Ergebnisse wahrheitsgemäß berichten: tatsächlich ausgeführte Prüfungen,
  relevante Fehlermeldungen sowie ausgelassene Schritte samt Begründung.
- Vor Abschluss Diff und Repository-Status prüfen.
