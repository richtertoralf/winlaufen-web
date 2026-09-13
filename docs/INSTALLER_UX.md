# Sprecher-Web — Installer-UX: Befund und separate Vorschläge

Zielgruppe: Maintainer. Stand: 13.09.2026.
Geprüft wurden Installerskripte, Paketerzeugung und Release-Workflow im Repository.
Dies ist eine statische Prüfung, keine neue interaktive Windows-Abnahme.
[Dokumentationsübersicht](INDEX.md).

## Verbleibende Hürden

| Befund | Auswirkung | Vorschlag für einen separaten Auftrag |
|---|---|---|
| Windows liefert ein ZIP mit PowerShell-Skript, keinen grafischen Installer. | Entpacken, Ordnerwechsel, Administratorstart und Skriptfreigabe bleiben manuelle Schritte. | Einen grafischen Setup-Einstieg mit Rechteanforderung, Profilwahl und verständlichem Ergebnisdialog entwerfen. |
| Der erhöhte PowerShell-Prozess übernimmt den Explorer-Ordner nicht automatisch. Das Archiv enthält außerdem einen Paket-Unterordner. | Ein angenommener Downloadpfad oder die erste entpackte Ordnerebene reicht nicht. | Setup aus dem Paketordner starten lassen und Paketpfad automatisch bestimmen. Die Dokumentation verwendet bis dahin den im Explorer kopierten tatsächlichen Pfad. |
| README und docs werden von den Distributionsskripten nicht mit ins Paket kopiert. | Offline fehlt die Anleitung; die Linux-Dienstmetadaten verweisen auf eine nicht mitgelieferte README. | Eine passende Kurzanleitung und die Betriebsdokumentation ins Releasepaket und gegebenenfalls ins Installationsverzeichnis aufnehmen. |
| Die Installer nennen in der Abschlussmeldung den alten README-Abschnitt „Known prototype security limitation“. | Der Abschnitt ist nur noch über einen Kompatibilitätsanker auffindbar und verweist auf die Installationsreferenz. | Meldung und Hilfelink auf die neue Dokumentationsadresse umstellen, zusammen mit der Paketdokumentation. |
| Windows konfiguriert Privat/Domäne, Linux verändert keine Firewall. | Eine lokal erfolgreiche Installation garantiert keinen Zugriff der Zuschauergeräte. | Eine verständliche Prüfung der Erreichbarkeit und Erklärung zum Netzwerkprofil anbieten, ohne fremde Regeln automatisch zu ändern. |
| Die Deinstallation erfolgt ebenfalls per Skript aus dem entpackten Paket. | Ohne aufbewahrtes Paket muss dieses erneut beschafft werden. | Einen auffindbaren Deinstallationseinstieg mit klarer Wahl zum Erhalt der Veranstaltungsdaten schaffen. |
| Fertige Linux-Pakete sind auf amd64 beschränkt. | Raspberry-Pi-Nutzer haben keinen Installationsweg ohne eigenen Build. | ARM64-Releasepaket mit eigener Abnahme ergänzen. |

## Abnahme für die separate Verbesserung

Auf einem Windows-11-Rechner den gesamten Weg durchspielen: Download in einen
abweichenden Ordner mit Leerzeichen, Entpacken, Installation mit Rechteabfrage,
Neustart, Zugriff eines zweiten Geräts, Upgrade und Deinstallation. Auch den
Stand ohne separat installiertes Java prüfen; dieser Nachweis ist bisher offen.
Bestehende Nachweise und weitere offene Szenarien stehen in
[SMOKE_TESTS.md](SMOKE_TESTS.md) und [STATUS.md](STATUS.md).

In diesem Dokumentationsauftrag wurden weder Installerlogik noch Paketinhalt
oder technische Funktionalität verändert.
