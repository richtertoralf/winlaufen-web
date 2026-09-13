# Sprecher-Web — Technischer Projektstatus

Zielgruppe: Entwickler, Maintainer und Administratoren.
Stand: 13.09.2026. [Dokumentationsübersicht](INDEX.md).

## Versionsstand

**Version `0.4.2`.** Dieses Release erweitert die WinLaufen-Kompatibilität der
Bridge. Neben dem aktuellen Sprecher-PC-Protokoll werden ältere
Legacy-Protokollvarianten unterstützt. Legacy-Daten werden ausschließlich am
Eingang der Bridge normalisiert und anschließend über dieselbe kanonische
Datenpipeline verarbeitet.

Bridge Control erkennt lokal, ob ein aktuelles oder ein Legacy-Protokoll
verwendet wird. Bei Legacy erscheint eine Update-Empfehlung. Diese Diagnose
bleibt vollständig in Bridge Control und wird weder an den Live Server noch an
externe Verbraucher übertragen.

Die Legacy-Unterstützung wurde am 13.09.2026 bei einem realen Wettkampf in
Oederan mit Uhr- und Ergebnisdaten im End-to-End-Betrieb erfolgreich getestet.

`0.4.1` führte zuvor die generische Read API des Live Servers sowie die
Zeitmesspunkte für WinLaufen-Uhrtelegramme ein. Dazu kamen Korrekturen für den
Windows-Betrieb und den Installer.

`0.4.0` brachte zuvor den Startlistenweg — Import in Bridge Control, persistenter
Bestand in der Bridge, Übertragung zum Live Server und klassenweise Anzeige im
Web Viewer — sowie die ersten fertigen Releasepakete für Linux amd64 und
Windows x64.

Die Prototyp-Grenzen aus
[Einsatzgrenzen](INSTALLATION.md#einsatzgrenzen)
gelten unverändert: Bridge Control hat keine Anmeldung, und der Bridge-Ingest
verwendet weiterhin ein bekanntes Default-Secret. Das Release ist deshalb keine
Freigabe für offenen Internetbetrieb.

## Abnahmestand

Die realen Installations- und Wettkampftests mit ihren jeweiligen Grenzen stehen
in [SMOKE_TESTS.md](SMOKE_TESTS.md). Ein Test einer älteren Version ist keine
vollständige Abnahme der aktuellen Version.

## Geplant, noch nicht vorhanden

- **Nativer Windows-Installer** (`.exe`, gegebenenfalls `.msi`) und später
  optional eine Installation über `winget`. Beides existiert **noch nicht**.
  Unter Windows gibt es heute ausschließlich das Release-ZIP mit dem
  enthaltenen PowerShell-Installer.

### Noch offen

- Linux-Releasepaket für ARM64 (Raspberry Pi); veröffentlicht wird derzeit nur
  AMD64
- Installation aus dem Releasepaket auf einem völlig neu aufgesetzten
  Windows-PC ohne vorhandenes Java; geprüft ist bisher der reale WinLaufen-PC,
  auf dem System-Java aus anderen Gründen installiert bleiben musste
- vollständige Reboot-, Reinstall- und Profilwechsel-Abnahmen
- Richter-Projects-Pairing
- bekannte P2-/P3-Punkte aus den Reviews

### Bekannte technische Punkte für den nächsten Arbeitsblock

| Punkt | Auswirkung heute |
|---|---|
| Der `WinLaufenClient`-Test belegt lokal TCP 4444. | `./mvnw clean package` kann auf einem Rechner scheitern, auf dem WinLaufen mit aktiver Sprecher-PC-Verbindung läuft. Vor dem Bauen dort **Trennen** wählen. |
| Windows PowerShell 5.1 stellte Umlaute in den Installerausgaben von **`v0.4.0`** falsch dar. | **Behoben in 0.4.1** ([Issue #5](https://github.com/richtertoralf/winlaufen-web/issues/5)). Das veröffentlichte `v0.4.0`-ZIP zeigt es weiterhin; nur die Anzeige war betroffen, Installation und Konfiguration waren korrekt. |
| Ob Installation und Upgrade auch bei laufender und verbundener Sprecher-PC-Schnittstelle zuverlässig funktionieren, ist noch nicht geprüft. | Bis dahin gilt verbindlich: vorher **Trennen**, danach **Verbinden**. |
