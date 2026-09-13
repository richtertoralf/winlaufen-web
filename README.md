# Sprecher-Web

[![Version](https://img.shields.io/badge/version-0.4.2-blue)](https://github.com/richtertoralf/winlaufen-web/releases)
[![Lizenz](https://img.shields.io/badge/Lizenz-AGPL--3.0-blue)](LICENSE)

**Live-Ergebnisse aus WinLaufen auf Notebook, Tablet und Smartphone.**
Sprecher-Web macht die Ergebnisse Ihrer Zeitnahme im Browser zugänglich:
Sprecher, Trainer und Zuschauer können im Veranstaltungsnetz mitlesen,
ohne eine zusätzliche App zu installieren. WinLaufen bleibt Ihre
Wettkampfsoftware; Sprecher-Web liest die bereitgestellten Daten mit.

`WinLaufen → Sprecher-Web → Browser`

<img src="WinLaufenSprecherWEB.png" alt="Beispiel vom Wettkampfbetrieb: WinLaufen, Sprecher-PC, Live-Ergebnisse im Browser und die Einrichtung von Sprecher-Web" width="100%">

## Was kann Sprecher-Web?

- **Live-Ergebnisse anzeigen:** Ergebnistabellen, laufende Wettkampfzeit und
  aktuelle Zieleinläufe; erprobt für Lauf und Biathlon.
- **Startlisten bereitstellen:** Export aus WinLaufen als CSV, TXT oder XLSX
  importieren und im Browser nach Klassen ansehen.
- **Die Anzeige anpassen:** Als Veranstalter wählen Sie etwa, ob Verein,
  Verband, Nation, Schießen und WinLaufen-Nachrichten angezeigt werden.
- **Verbindungsprobleme sichtbar machen:** Die Anzeige kennzeichnet veraltete
  Daten und verbindet nach einer Unterbrechung automatisch neu.
- **Im lokalen Netz arbeiten:** Für den Betrieb vor Ort ist kein Internet nötig.
  Weitere Live-Server können zusätzlich Ergebnisse bereitstellen.

## Welche Variante brauche ich?

### All-in-One — Empfehlung für die meisten Vereine

WinLaufen und Sprecher-Web laufen auf demselben Windows-PC.
Sie installieren Sprecher-Web einmal und öffnen die Ergebnisse im Browser.

`WinLaufen + Sprecher-Web auf einem PC → Browser im Vereinsnetz`

### Getrennte Rechner

WinLaufen läuft auf dem Zeitnahme-PC, Sprecher-Web auf einem anderen
Windows- oder Linux-Rechner im selben Netzwerk. Das passt, wenn Sie auf dem
Zeitnahme-PC keine zusätzliche Software installieren möchten.
Auch hier wählen Sie **All-in-One** für den Sprecher-Web-Rechner.

`WinLaufen-PC → Sprecher-Web auf zweitem Rechner → Browser`

### Zentraler Live-Server

Die **Bridge** leitet die Daten vom WinLaufen-PC weiter; ein separater
Linux-Rechner oder Server stellt sie für die Browser bereit.
Dafür installieren Sie **Bridge only** auf dem WinLaufen-PC und
**Presentation Node** auf dem Server. Diese Variante erfordert Erfahrung
mit der Einrichtung von Netzwerken.

`WinLaufen-PC mit Bridge → Live-Server → Browser`

Für einen zeitweise gemieteten Internetserver gibt es eine
[eigene Anleitung mit Einsatzgrenzen](docs/QUICKSTART_CLOUD.md).
Ein dauerhaft öffentlich betriebener Dienst ist noch kein fertiger Standardweg.

## Schnellinstallation

Laden Sie auf der [Releases-Seite](https://github.com/richtertoralf/winlaufen-web/releases)
unter **Assets** das fertige Paket für Ihren Rechner herunter.
Die benötigte Java-Umgebung ist enthalten. Wählen Sie die unten genannte
Paketdatei; die Anhänge **Source code** sind nicht zur Installation gedacht.

Falls die Sprecher-PC-Verbindung in WinLaufen bereits aktiv ist, wählen Sie
vor der Installation **Abwicklung → Sprecher-PC… → Trennen**.
WinLaufen selbst kann geöffnet bleiben.

### Windows 11

1. Laden Sie `winlaufen-web-<version>-windows-x64.zip` herunter.
   `<version>` steht für die Versionsnummer des ausgewählten Pakets.
2. Öffnen Sie im Browser die Downloadliste mit **Strg+J** und wählen Sie bei
   der ZIP-Datei **In Ordner anzeigen** (je nach Browser ein Ordnersymbol).
3. Klicken Sie im Explorer mit der rechten Maustaste auf die ZIP-Datei und
   wählen Sie **Alle extrahieren… → Extrahieren**. Öffnen Sie den entpackten
   Ordner und gegebenenfalls den darin liegenden gleichnamigen Unterordner,
   bis Sie **installer**, **lib** und **runtime** sehen.
4. Klicken Sie in die Adressleiste des Explorers und kopieren Sie den
   vollständigen Ordnerpfad mit **Strg+C**.
5. Suchen Sie im Startmenü nach **Windows PowerShell**, wählen Sie
   **Als Administrator ausführen** und bestätigen Sie die Windows-Abfrage.
6. Führen Sie die folgenden Zeilen einzeln aus. Ersetzen Sie in der ersten
   Zeile `HIER DEN KOPIERTEN ORDNERPFAD EINFÜGEN` durch den Pfad aus Schritt 4;
   die einfachen Anführungszeichen bleiben stehen.

```powershell
Set-Location -LiteralPath 'HIER DEN KOPIERTEN ORDNERPFAD EINFÜGEN'
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\installer\windows\Install-WinLaufenWeb.ps1 -Profile AllInOne
```

Bestätigen Sie gegebenenfalls die Nachfrage zur Skriptausführung mit **J**.
Diese Einstellung gilt nur für das geöffnete PowerShell-Fenster.
Warten Sie auf die Erfolgsmeldung. Das ZIP enthält derzeit ein
PowerShell-Installationsskript; einen grafischen Setup-Assistenten gibt es noch nicht.
Bei Problemen hilft die [ausführliche Windows-Anleitung](docs/INSTALLATION.md#12-windows-11).

### Linux

Für einen üblichen 64-Bit-PC mit Intel- oder AMD-Prozessor laden Sie
`winlaufen-web-<version>-linux-amd64.tar.gz` herunter.
Öffnen Sie den Downloadordner über die Downloadliste Ihres Browsers,
entpacken Sie das Archiv mit der Archivverwaltung und öffnen Sie den Ordner,
in dem **installer**, **lib** und **runtime** liegen.
Öffnen Sie dort über die Dateiverwaltung ein Terminal und führen Sie aus:

```sh
sudo ./installer/linux/install.sh --profile all-in-one
```

Geben Sie bei der Nachfrage Ihr Linux-Passwort ein; dabei sind keine Zeichen
sichtbar. WinLaufen läuft weiterhin auf dem Windows-PC. Dessen Adresse
tragen Sie beim ersten Start ein.
Unterstützte Systeme, andere Profile und die nötigen Netzwerkfreigaben stehen
in der [Installationsanleitung](docs/INSTALLATION.md).
Für Raspberry Pi ist noch kein fertiges Releasepaket vorhanden.

## Erster Start

1. Öffnen Sie auf dem Sprecher-Web-Rechner
   [Bridge Control](http://localhost:44442/) — die Einrichtung für Veranstalter.
   Läuft WinLaufen auf einem anderen Rechner, tragen Sie dort dessen Adresse ein
   und speichern Sie.
2. Öffnen Sie den Wettkampf in WinLaufen und wählen Sie
   **Abwicklung → Sprecher-PC… → Verbinden**.
3. Öffnen Sie die [Live-Ergebnisse](http://localhost:44440/).
   Prüfen Sie, ob die Wettkampfzeit aktualisiert wird. Ergebnisse erscheinen,
   sobald WinLaufen einen Ergebnisstand liefert.
4. Geben Sie die in Bridge Control unter **Live-Ergebnisse im Browser**
   angezeigte Netzwerkadresse an die Zuschauer im Veranstaltungsnetz weiter.

Sprecher-Web startet künftig automatisch mit dem Rechner.
Die Schritte gelten für All-in-One; Einrichtung weiterer Server,
Startlistenimport und Hilfe am Wettkampftag stehen im
[Bedienerhandbuch](docs/BEDIENERHANDBUCH.md).

## Schnittstelle für weitere Anwendungen

Sprecher-Web stellt die empfangenen Live-Daten zusätzlich über eine
dokumentierte HTTP-/WebSocket-Schnittstelle für die Anbindung weiterer
Anwendungen bereit. Die [Schnittstellenbeschreibung](docs/API.md) richtet sich
an Entwickler und Integratoren.

## Weitere Dokumentation

### Dokumentation für Anwender

- [Bedienerhandbuch](docs/BEDIENERHANDBUCH.md): Einrichtung, Anzeigen,
  Startlisten, Wettkampftag und schnelle Hilfe.
- [Installation](docs/INSTALLATION.md): fertige Pakete installieren und
  aktualisieren; für Administratoren außerdem Profile, Netzwerk, Firewall,
  Dienste, Fehlerdiagnose und Deinstallation.
- [Temporärer Internetserver](docs/QUICKSTART_CLOUD.md): für Administratoren
  und technisch versierte Anwender.

### Dokumentation für Entwickler und Integratoren

- [API](docs/API.md): dokumentierte Schnittstelle für weitere Anwendungen.
- [Entwicklung](docs/DEVELOPMENT.md): Einstieg für die Mitarbeit am Projekt.
- [Dokumentationsübersicht](docs/INDEX.md): Architektur, Protokoll,
  Abnahmetests und weitere technische Referenzen.

## Projektstatus und Lizenz

**Version 0.4.2 — Entwicklungsversion für kontrollierte Vereins- und
Veranstaltungsnetze.** Testen Sie Ihren Aufbau vor dem Wettkampftag.

<a id="known-prototype-security-limitation"></a>
Die Veranstalter-Oberfläche hat keine Anmeldung; die Datenübertragung nutzt
einen bekannten Standardschlüssel. Die Installation gehört deshalb in ein
vertrauenswürdiges Netz. Über die Ergebnisadresse sind auch importierte
Teilnehmerdaten zugänglich. Für Internetbetrieb beachten Sie die
[verbindlichen Einsatzgrenzen](docs/INSTALLATION.md#einsatzgrenzen).

Sprecher-Web steht unter der [GNU AGPL-3.0](LICENSE).
