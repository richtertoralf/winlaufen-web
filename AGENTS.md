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

- Neue Komponenten, Dienste, Abstraktionsschichten oder Abhängigkeiten nur einführen, wenn ein konkreter Nutzen sie rechtfertigt.
- Betriebs-, Wartungs- und Fehlersuchaufwand gehören zu den technischen Kosten. Lösungen müssen für ein kleines beziehungsweise ein Einmann-Team nachvollziehbar und wartbar bleiben.
- Nicht vorsorglich für hypothetische Anforderungen oder Skalierung bauen.

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

## Gemeinsame Regeln

Gemeinsam beschlossene Regeln der Produktfamilie. **Heute** beschreibt den
vorhandenen Stand, **Ziel** eine beschlossene, noch offene Umsetzung mit
Issue. Ein Ziel ist kein Auftrag, es nebenbei oder im Vorgriff umzusetzen.

- Verwaltung heute: `winlaufen-web --help | --version | --status | --upgrade
  [PAKET] | --uninstall [--purge] [--yes]`; Rückgabecodes 0/1/2, eine
  getrennte WinLaufen-Quelle ist kein Fehler. Ziel: `--check` (#12).
- Releasebasiert: `--upgrade` installiert das neueste geeignete Release. Bei
  einem beauftragten Release Tag `vX.Y.Z` und GitHub Release nach
  `docs/RELEASE.md`.
- Oberflächen: Ziel sind gemeinsame Token-Datei und Umschalter Hell/Dunkel
  (#27).
- Zugang heute: Bridge Control ohne Anmeldung. Ziel: Anmeldung für Bridge
  Control; Web-Ansicht und Read-API bleiben ohne Anmeldung und ohne
  Geheimnisse.
  Konten nach dem Standard „Lokale Benutzerkonten“; keine Sitzungs-, Rollen-
  oder Kopplungsverfahren auf Vorrat.
- Daten: Fehlendes bleibt unbekannt; ein autoritativ leerer Stand ist nicht
  dasselbe wie ein fehlender.
- Neue technische Bezeichner englisch; vorhandene deutsche Bezeichner nur in
  getesteten Überarbeitungen umbenennen, keine Massenumbenennung.
- Ausgabe, Ziel: Unveränderte Eingangsdaten und wiederholte Zustandsmeldungen
  unterbrechen keine laufende Grafik oder Anzeige und bauen sie nicht neu
  auf. Vorgesehene Wechsel, Zeitsteuerungen und die Wiederherstellung nach
  einer Unterbrechung bleiben zulässig; ein Regressionstest sichert das.
- Einfachheit: die kleinste robuste Lösung, nichts auf Vorrat. Keine zweite
  Implementierung derselben Funktion und kein Sonderweg je Sportart, Marke
  oder Quelle, wo ein allgemeiner Mechanismus reicht. Vor einer größeren
  Erweiterung früh und produktübergreifend klären: fachliches Ziel,
  wiederverwendbare Mechanismen, einfachere Lösung, unvermeidbare
  Komplexität, was dadurch entfallen kann.
- Prüfen: Vorhandene Simulatoren und Datenbestände aktiv nutzen, für Module,
  Adapter, automatische und zeitliche Abläufe, Zustandswechsel und
  Zusammenspiel. Weitere Wettkampfdaten erst anfordern, wenn eine konkrete
  Prüfsituation nicht abgedeckt ist; nicht auf einen Feldtest warten, wo
  Simulation reicht. Keine neuen Simulatoren und keine neue
  Testinfrastruktur. Eine Simulation belegt Verhalten, kein Quellformat:
  Was keine Quelle belegt liefert, bleibt unbekannt.

## Dokumentationsregel

Dokumentation ist Teil jeder Änderung und beschreibt den Stand des Branches,
auf dem sie liegt.

- Jede verbindliche Aussage hat einen Ort; andere Stellen verweisen darauf.
  Einen Inhalt erst durch einen Verweis ersetzen, wenn er am Zielort
  vollständig dokumentiert ist. Überholte Aussagen ersetzen oder entfernen;
  gültige Informationen nicht ohne fachlichen Grund entfernen.
- **Vor jedem Commit:** betroffene Dokumentation im selben Commit
  nachführen. Ist keine betroffen, entsteht keine künstliche Änderung.
- **Vor jedem Merge:** Umsetzung gegen die betroffene Dokumentation und die
  geltenden Regeln prüfen; Folgen für andere Repositories und gemeinsame
  Schnittstellen nennen; Roadmap (`docs/PRIORITAETEN.md`) und Aufgabenstatus
  auf den tatsächlichen Stand bringen.
- **Vor jedem Release:** Installation, Konfiguration, Schnittstellen,
  Bedienung und bekannte Einschränkungen gegen den Release-Stand abgleichen.
  Nichts Erledigtes steht als offen, nichts Ungeprüftes als abgenommen. Der
  Release-PR nennt, was fachlich hinzukam, und den Umfang seit dem letzten
  Release (`git diff --shortstat <letzter Tag>..HEAD`).
- README.md und `docs/` sind deutsch. Etablierte Fachbegriffe bleiben
  unverändert.
- Jedes Thema gehört an seinen primären Ort nach
  [docs/INDEX.md](docs/INDEX.md). Andere Seiten verlinken dorthin.
- Verhaltensänderungen werden in der zuständigen Dokumentation nachgeführt und
  durch geeignete vorhandene oder neue Tests abgesichert, soweit der geänderte
  Bereich dies erfordert.
- `CLAUDE.md` verweist nur auf AGENTS.md. Weitere agentenspezifische Skill-
  oder Instruktionsstrukturen werden nicht angelegt.

## README

Die README ist die öffentliche Einstiegsseite für Anwender, Interessenten und
Mitwirkende; Vertiefendes steht in `docs/`.

- Nutzen vor technischen Details. Gliederung und Überschriften passend zum
  Produkt wählen.
- Nur tatsächlich vorhandene Funktionen und Befehle als verfügbar
  beschreiben; Erprobtes und Geplantes getrennt kennzeichnen.
- Einen Entwicklungsstatus nur nennen, wenn er nachvollziehbar belegt ist:
  **Konzept** (keine nutzbare Implementierung), **In Entwicklung**
  (begonnen, eventuell noch nicht installierbar; nennen, was funktioniert und
  wie man es ausprobiert), **Im Praxistest** (bei mindestens einem realen
  Einsatz betreut erprobt, datierter Beleg in `docs/`), **Im Einsatz**
  (wiederholt produktiv genutzt, Beleg in `docs/`), **Archiviert**. Fehlen
  Belege, die Aussage weglassen; Installations- und Upgradeprüfungen belegen
  keinen Praxiseinsatz.
- Nur verifizierte Informationen aufnehmen: Aussagen zuerst an Code und
  Dokumentation prüfen; was sich nicht bestätigen lässt, weglassen. Fehlende
  Dokumentation ist kein Beleg für eine fehlende Funktion.
- Einschränkungen nur nennen, wenn sie nachgewiesen und für Anwender relevant
  sind; keine vermuteten Risiken, vorsorglichen Warnhinweise oder künstlich
  offenen Fragen. Lieber kürzer und sachlich richtig als ausführlich mit
  Annahmen.
- Alles Notwendige steht in diesem Repository. Nicht auf nicht öffentliche
  Repositories als notwendige Quelle verweisen.
- Der Leitgedanke von RICHTER.PROJECTS, „Möglichkeiten schaffen.“, ist
  erkennbar, in eigenen Worten passend zum Produkt.
- Natürliches, sachliches Deutsch ohne Werbe- und Füllformeln; vorhandene
  Bilder und Beispiele erhalten.

## Verifikation und Abschluss

- Die Prüfungen ausführen, die zur Änderung passen. Welche es gibt, steht in
  [DEVELOPMENT.md](docs/DEVELOPMENT.md) und
  [SMOKE_TESTS.md](docs/SMOKE_TESTS.md).
- Ergebnisse wahrheitsgemäß berichten: tatsächlich ausgeführte Prüfungen,
  relevante Fehlermeldungen sowie ausgelassene Schritte samt Begründung.
- Vor Abschluss Diff und Repository-Status prüfen.
