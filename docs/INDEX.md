# Sprecher-Web — Dokumentationsübersicht

Jedes Thema hat einen primären Ort. Die README bietet nur den Produkteinstieg
und eine Schnellinstallation; weiterführende Anleitungen verlinken auf die
jeweilige Referenz.

## Endanwender: Vereine, Veranstalter und Sprecher

| Dokument | Primärer Inhalt |
|---|---|
| [README](../README.md) | Produkt, Möglichkeiten, Variantenwahl, Schnellinstallation fertiger Pakete und erster Start |
| [Bedienerhandbuch](BEDIENERHANDBUCH.md) | WinLaufen verbinden, Bridge Control, Startlistenimport, Anzeigen, Ausfälle, Wettkampftag und schnelle Hilfe |
| [Installation, Abschnitte 1 und 3](INSTALLATION.md) | vollständiger Installationsweg für Releasepakete und Upgrade |

## Administratoren und technisch versierte Anwender

| Dokument | Primärer Inhalt |
|---|---|
| [Installation und Administration](INSTALLATION.md) | Profile, Plattformen, Pfade, Dienste, Netzwerk, Firewall, Zeitzone, Upgrade, Fehlerdiagnose, Deinstallation und verbindliche Einsatzgrenzen |
| [Cloud-Kurzanleitung](QUICKSTART_CLOUD.md) | zusätzlicher temporärer Internetserver; verweist für allgemeine Betriebsdetails auf die Installation |

## Entwickler und Integratoren

| Dokument | Primärer Inhalt |
|---|---|
| [API](API.md) | HTTP-/WebSocket-Endpunkte, Datenformate, Zeitmodell und Integrationsvertrag |
| [Entwicklung](DEVELOPMENT.md) | Voraussetzungen, Quellcode-Build, Entwicklungsinstallation, Git-Update, Entwicklungsbetrieb, Tests und technische Namen |
| [Architektur: Ist-Stand](ARCHITECTURE.md) | Umsetzung und Modulgrenzen; zusammen mit den Architekturentscheidungen lesen |
| [Architekturentscheidungen](MODULAR_ARCHITECTURE.md) | verbindliche Architektur, interne Zustands- und Transportkonzepte sowie Begründungen |
| [WinLaufen-Protokoll](WINLAUFEN_PROTOCOL.md) | Quellprotokoll und reale Telegrammbelege |
| [Produktspezifikation](PRODUCT_SPEC.md) | fachlicher Umfang, Anforderungen und technische Rahmenbedingungen |
| [Abnahmetests](SMOKE_TESTS.md) | manuelle Tests, historische Testprotokolle und reale Nachweise |
| [Technischer Projektstatus](STATUS.md) | Versionshistorie, offene Punkte und Grenzen des Abnahmestands |
| [Release](RELEASE.md) | Paketerzeugung und Veröffentlichung für Maintainer |
| [Installerentwicklung](../installer/README.md) | Aufbau und Tests der Installerskripte |
| [Installer-UX-Befund](INSTALLER_UX.md) | verbleibende Bedienhürden und Vorschläge für einen separaten Auftrag |

Beim Ergänzen der Dokumentation ausführliche Inhalte an diesem primären Ort
pflegen und von anderen Seiten verlinken. Nutzeranleitungen enthalten keine
Entwicklerinstallation; technische Integrationsdetails stehen in API.md.
