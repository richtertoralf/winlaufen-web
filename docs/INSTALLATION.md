# Sprecher-Web — Installation und Administration

**Für Anwender:** Abschnitt 1 führt durch die Installation fertiger Releasepakete,
Abschnitt 3 durch ein Upgrade. Für Einrichtung und Wettkampfbetrieb lesen Sie
anschließend das [Bedienerhandbuch](BEDIENERHANDBUCH.md).

**Für Administratoren und technisch versierte Anwender:** Hier stehen die
verbindlichen Betriebsdetails. [Dokumentationsübersicht](INDEX.md).

- [Releasepakete installieren](#1-installation-aus-dem-releasepaket)
- [Upgrade](#3-upgrade)
- [Profile und Plattformen](#4-installationsprofile)
- [Verzeichnisse und Zeitzone](#5-verzeichnisse)
- [Dienste](#6-dienste)
- [Netzwerk und Firewall](#7-ports-netzwerkvertrag-und-firewall)
- [Prüfung und Fehlerdiagnose](#8-prüfung-nach-der-installation)
- [Deinstallation](#9-deinstallation)
- [Einsatzgrenzen](#einsatzgrenzen)

Installation und Einrichtung sind getrennt: Der Installer fragt nur nach der
Rolle des Rechners. Adressen tragen Sie später in Bridge Control ein.
Eine nicht erreichbare WinLaufen-Quelle oder ein entferntes Ziel verhindert
die Installation nicht; lokale Dienst- und Startfehler tun dies dagegen schon.

## 1. Installation aus dem Releasepaket

Laden Sie auf der [Releases-Seite](https://github.com/richtertoralf/winlaufen-web/releases)
unter **Assets** das Paket für Ihr System herunter, nicht die Anhänge
**Source code**. Die passende Java-Umgebung ist enthalten; eine zusätzliche
Java-Installation ist nicht erforderlich. `<version>` steht in den Dateinamen
für die gewählte Versionsnummer.

Laden Sie für eine Prüfsummenkontrolle zusätzlich `SHA256SUMS` aus demselben
Release herunter. Der berechnete SHA256-Wert muss mit dem Eintrag für genau
Ihre Paketdatei übereinstimmen.

Vor Installation oder Upgrade eines Profils mit Bridge (**All-in-One** oder
**Bridge only**) eine aktive Sprecher-PC-Verbindung in WinLaufen mit
**Abwicklung → Sprecher-PC… → Trennen** lösen. WinLaufen selbst bleibt geöffnet.
Nach erfolgreicher Installation wieder **Verbinden** wählen. Ob ein Upgrade
bei aktiver Verbindung zuverlässig funktioniert, ist noch nicht vollständig
abgenommen. Für einen **Presentation Node** entfällt dieser Schritt.

### 1.1 Linux

Voraussetzung ist ein Linux-Rechner mit systemd und Administratorrechten über
`sudo`; unterstützte Systeme stehen unter [Plattformen](#unterstützte-plattformen).
Das fertige Paket `winlaufen-web-<version>-linux-amd64.tar.gz` ist für
64-Bit-PCs mit Intel- oder AMD-Prozessor bestimmt. Für Raspberry Pi/ARM gibt es
noch kein fertiges Paket. Eine Installation dort erfordert den
[Entwicklerweg](DEVELOPMENT.md#installation-aus-dem-quellcode).

1. Öffnen Sie im Browser die Downloadliste und lassen Sie die heruntergeladene
   Datei im Dateimanager anzeigen.
2. Entpacken Sie das Archiv mit der Archivverwaltung.
3. Öffnen Sie den entpackten Ordner, in dem `installer`, `lib` und `runtime`
   liegen, und dort über das Kontextmenü ein Terminal (oft „Im Terminal öffnen“).
4. Führen Sie für den üblichen Betrieb aus:

```sh
sudo ./installer/linux/install.sh --profile all-in-one
```

Bei der Passwortabfrage erscheinen keine Zeichen. Weitere Profilwerte sind
`bridge-only` und `presentation-node`; ohne `--profile` fragt der Installer
interaktiv. Wählen Sie genau das Profil für die Rolle dieses Rechners.

Für Administratoren, alternativ im Downloadordner mit den tatsächlichen
Dateinamen anstelle von `<version>`:

```sh
sha256sum -c --ignore-missing SHA256SUMS
tar -xzf winlaufen-web-<version>-linux-amd64.tar.gz
cd winlaufen-web-<version>-linux-amd64
sudo ./installer/linux/install.sh --profile all-in-one
```

Kontrollieren Sie, dass die Prüfung ausdrücklich Ihr Paket als `OK` meldet.
Der Installer verändert keine Firewall; bei Zugriff von anderen Rechnern
beachten Sie [Netzwerk und Firewall](#7-ports-netzwerkvertrag-und-firewall).

### 1.2 Windows 11

Sie brauchen Windows 11 auf einem x64-PC und ein Administratorkonto.
Das Paket heißt `winlaufen-web-<version>-windows-x64.zip`.
Es enthält ein PowerShell-Skript; ein grafischer `.exe`-/`.msi`-Installer
und eine Installation über `winget` stehen noch nicht zur Verfügung.

#### Download finden und entpacken

1. Öffnen Sie nach dem Herunterladen die Downloadliste Ihres Browsers mit
   **Strg+J**. Wählen Sie bei der ZIP-Datei **In Ordner anzeigen** oder das
   Ordnersymbol. So finden Sie die Datei auch bei einem abweichenden Downloadpfad.
2. Klicken Sie im Explorer mit der rechten Maustaste auf die ZIP-Datei.
   Wählen Sie **Alle extrahieren…** und anschließend **Extrahieren**.
3. Öffnen Sie den entpackten Ordner. Wenn darin nur ein weiterer Ordner mit dem
   Paketnamen liegt, öffnen Sie auch diesen. Richtig sind Sie, wenn Sie
   **installer**, **lib** und **runtime** sehen. Arbeiten Sie nicht im ZIP selbst.
4. Klicken Sie in die Adressleiste des Explorers. Mit **Strg+C** kopieren Sie den
   vollständigen Pfad dieses Ordners.

#### Installer starten

1. Suchen Sie im Windows-Startmenü nach **Windows PowerShell** und wählen Sie
   **Als Administrator ausführen**. Bestätigen Sie die Windows-Abfrage und
   geben Sie gegebenenfalls die Zugangsdaten des Administratorkontos ein.
2. Die neue PowerShell mit Administratorrechten öffnet sich nicht automatisch
   im Paketordner. Geben Sie `Set-Location -LiteralPath '` ein, fügen Sie den
   kopierten Pfad mit **Strg+V** ein und ergänzen Sie ein abschließendes `'`.
   Drücken Sie **Enter**. Die vollständige Zeile sieht so aus, wobei Sie den
   Platzhalter vollständig durch Ihren Pfad ersetzen:

```powershell
Set-Location -LiteralPath 'HIER DEN KOPIERTEN ORDNERPFAD EINFÜGEN'
```

Die Anführungszeichen sorgen dafür, dass Leerzeichen im Pfad funktionieren.
Falls Ihr Ordnername selbst ein einfaches Anführungszeichen enthält, schreiben
Sie dieses innerhalb des Pfads zweimal (`O''Brien`).

3. Erlauben Sie die Skriptausführung für dieses Fenster:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

Bestätigen Sie eine Nachfrage mit **J**. Die Freigabe endet beim Schließen
des Fensters; es wird keine dauerhafte systemweite Richtlinie geändert.

4. Starten Sie das gewählte Profil, für den Normalfall:

```powershell
.\installer\windows\Install-WinLaufenWeb.ps1 -Profile AllInOne
```

Für eine reine Bridge verwenden Sie stattdessen `-Profile BridgeOnly`.
Ohne `-Profile` fragt der Installer nach der Rolle.
**Presentation Node wird unter Windows nicht unterstützt.**

5. Warten Sie auf die Erfolgsmeldung. Eine Warnung zur noch nicht verbundenen
   WinLaufen-Quelle bedeutet, dass die lokale Installation gelungen ist, aber
   die Einrichtung noch fehlt. Bei einem Abbruch beachten Sie die Fehlermeldung
   und [Fehlerdiagnose](#fehlerdiagnose).
6. Verbinden Sie WinLaufen wieder und fahren Sie mit
   [WinLaufen verbinden](BEDIENERHANDBUCH.md#6-winlaufen-verbinden) fort.

**Optionale Prüfsummenkontrolle vor dem Entpacken:** Öffnen Sie im Ordner der
ZIP-Datei über die Explorer-Adressleiste mit `powershell` und **Enter** ein
normales PowerShell-Fenster. Ersetzen Sie `<version>` durch die Nummer Ihrer Datei:

```powershell
Get-FileHash .\winlaufen-web-<version>-windows-x64.zip -Algorithm SHA256
```

Vergleichen Sie den Wert mit `SHA256SUMS` aus demselben Release.

**Historischer Hinweis zu v0.4.0:** Dieses Paket stellte Umlaute unter Windows
PowerShell 5.1 falsch dar. Ab 0.4.1 ist die Ausgabe korrigiert. Bestehende
Konfigurationskommentare werden beim Upgrade beibehalten.

## 2. Entwicklungsstände

Installation aus dem Quellcode und deren Voraussetzungen stehen ausschließlich
in [DEVELOPMENT.md](DEVELOPMENT.md#installation-aus-dem-quellcode).
Die folgenden Betriebsdetails gelten auch für solche Installationen.

## 3. Upgrade

### 3.0 Erstinstallation und Upgrade

Der Installer erkennt eine bestehende Installation automatisch. Bei einer
Erstinstallation richtet er Programmdateien, Konfiguration und Dienste ein;
bei einem Upgrade aktualisiert er die Programmdateien und Dienste und erhält
die Veranstaltungsdaten. Die WinLaufen-Installation wird nicht verändert.

### 3.1 Wie der Installer den Fall erkennt

Beide Installer prüfen **drei unabhängige Spuren**. Trifft eine davon zu, gilt
der Lauf als Upgrade:

1. ein installiertes Programmartefakt (`winlaufen-web-bridge.jar` oder
   `winlaufen-web-live-server.jar` im Programmverzeichnis),
2. eine vorhandene Konfigurationsdatei (`bridge.properties` bzw.
   `live-server.env` / `live-server.properties`),
3. eine registrierte `systemd`-Unit bzw. geplante Aufgabe.

Ein leeres Verzeichnis allein zählt bewusst **nicht**: Es entsteht auch durch
eine abgebrochene Deinstallation und wäre kein Beleg für eine Installation.

### 3.2 Was der Installer anzeigt

Vor der ersten Änderung nennt er den erkannten Modus:

```text
============================================================
Sprecher-Web – Upgrade
============================================================

Bestehende Sprecher-Web-Installation gefunden.

  Profil:        all-in-one
  Java:          /opt/winlaufen-web/runtime/bin/java
  Programm:      /opt/winlaufen-web
  Konfiguration: /etc/winlaufen-web

Programmdateien werden aktualisiert.
Bestehende Konfiguration und Veranstaltungsdaten bleiben erhalten.
Die vorhandene WinLaufen-Installation wird nicht verändert.
```

Während des Laufs ist jeder Schritt gekennzeichnet:

```text
  AKTUALISIERT: Bridge-Programm
  AKTUALISIERT: Live-Server-Programm
  BEIBEHALTEN: bestehende bridge.properties (/etc/winlaufen-web/bridge.properties)
  BEIBEHALTEN: importierte Startliste (/etc/winlaufen-web/startlist.properties)
  AKTUALISIERT: systemd-Unit winlaufen-bridge.service
```

Am Ende steht eine Zusammenfassung mit `AKTUALISIERT`, `BEIBEHALTEN` und
`UNVERÄNDERT` — Letzteres nennt ausdrücklich WinLaufen — gefolgt vom
Betriebsbereitschaftsbericht. Bei einer Erstinstallation lautet der Block
`NEU ANGELEGT`; das Wort „beibehalten" kommt dort nicht vor, weil es nichts zu
behalten gab.

Das ist auf Linux und Windows dieselbe Bedienlogik, auch wenn die technische
Umsetzung sich unterscheidet.

### 3.3 Der eigentliche Ablauf

Es gibt keinen separaten Upgrade-Pfad: **derselbe Installer** führt auch das
Upgrade durch. Der Weg bleibt dabei derselbe wie bei der Installation — eine
aus einem Releasepaket installierte Anlage wird mit dem nächsten Releasepaket
aktualisiert, eine Entwicklerinstallation über den Git-Stand.

Das Profil muss dem bereits installierten entsprechen; ein anderer Wert ist ein
bewusster Profilwechsel. Läuft auf diesem Rechner WinLaufen mit aktiver
Sprecher-PC-Verbindung, diese vorher **trennen** und danach wieder
**verbinden**.

### 3.4 Upgrade aus einem neuen Releasepaket

Laden Sie das neue Paket herunter und folgen Sie erneut der
[Linux-Anleitung](#11-linux) oder [Windows-Anleitung](#12-windows-11).
Verwenden Sie dasselbe Profil wie bisher. So ist auch der Wechsel in den
richtigen entpackten Ordner beschrieben.

Das alte entpackte Paket kann nach erfolgreichem Upgrade entfernt werden.
Bewahren Sie das neue Paket für eine spätere Deinstallation auf.
Für Updates eines Entwicklungsstands siehe
[Entwicklerinstallation aktualisieren](DEVELOPMENT.md#entwicklerinstallation-aktualisieren).

### 3.5 Getrennte Rechner: erst Live Server, dann Bridge

Stehen Bridge und Live Server auf **verschiedenen** Rechnern — Profile
**Bridge only** und **Presentation Node** —, laufen sie beim Upgrade kurz in
verschiedenen Versionen. Für den Übergang von 0.4.0 auf die Versionen mit
Zeitmesspunkten kann eine ältere Bridge an einen neueren Live Server liefern, umgekehrt nicht. Daraus folgt
keine allgemeine Kompatibilitätszusage für beliebige künftige Versionen.

**Deshalb zuerst den Live Server aktualisieren, danach die Bridge.** In der
Zwischenzeit läuft alles weiter; es fehlen höchstens Angaben, die die ältere
Bridge noch nicht liefert.

Bei **All-in-One** ersetzt derselbe Installerlauf beide Prozesse gemeinsam; dort
stellt sich die Frage nicht.

Hintergrund und Nachweis: [RELEASE.md](RELEASE.md#upgrade-reihenfolge-bei-getrennten-rechnern).

### 3.6 Was das Upgrade erhält und was es ersetzt

Für beide Installationswege identisch. Die Pfade in der Tabelle sind die von
Linux; unter Windows gilt dasselbe Verhalten für `C:\Program Files\WinLaufen Web\`
und `C:\ProgramData\WinLaufen Web\` sowie für die geplanten Aufgaben anstelle der
systemd-Units.

| Gegenstand | Verhalten |
|---|---|
| `bridge.properties` | individuelle Einstellungen bleiben erhalten; Ausnahme: Migration früherer Installer-Netzwerkdefaults (siehe unten) |
| `live-server.env` / `live-server.properties` | individuelle Einstellungen bleiben erhalten; Ausnahme: Migration früherer Installer-Netzwerkdefaults |
| `startlist.properties` | bleibt unverändert — die importierte Startliste überlebt das Upgrade, ein erneuter Import ist nicht nötig |
| `/var/lib/winlaufen-web/` | bleibt unverändert |
| JARs unter `/opt/winlaufen-web/lib/` | werden ersetzt |
| gebündelte Runtime unter `/opt/winlaufen-web/runtime/` | wird ersetzt, wenn die Distribution eine mitbringt |
| systemd-Units | werden ersetzt, nicht dupliziert |
| Dienste des Profils | werden aktiviert und neu gestartet |
| Dienst eines nicht gewählten Profils | wird deaktiviert und entfernt, statt verwaist zurückzubleiben |

Zusätzlich migriert der Installer einmalig die **exakten** früheren
Installer-Netzwerkdefaults auf den festen Portblock 44440–44442: Control-Port
8090, Control-Bind `127.0.0.1`, der lokale Ingest-Endpunkt auf Port 8081 sowie
die Live-Server-Ports 8080/8081. Individuell gepflegte Werte, andere Hosts und
andere Ports bleiben unverändert; eine Meldung erscheint nur bei einer
tatsächlichen Änderung. Diese Portnummern sind **historisch** und keine
aktuellen Standardwerte.

Der Wettkampfstand selbst wird nicht übernommen — er ist bewusst nur im
Speicher. Nach dem Neustart der Dienste füllt WinLaufen ihn wieder, sobald es
den nächsten Klassensnapshot liefert.

Ein Wechsel des Installationsweges — etwa von einer Entwicklerinstallation auf
das Releasepaket — ist möglich: Beide schreiben in dieselben Pfade und
ersetzen Programmdateien und Units. Konfiguration und Startliste bleiben dabei
ebenfalls erhalten.

## 4. Installationsprofile

Der Installer fragt genau eine Sache ab: das Profil.

| Profil | Installiert | Typischer Einsatz |
|---|---|---|
| **All-in-One** | Bridge + Live Server | Standard; ein Rechner im lokalen Netz |
| **Bridge only** | nur Bridge | eigener Rechner nur für die WinLaufen-Anbindung |
| **Presentation Node** | nur Live Server / Web View | eigener Webserver im LAN oder WAN |

### All-in-One — empfohlener Standard

```text
WinLaufen-PC
      |
      | TCP 4444
      v
All-in-One-Rechner
 Bridge + Live Server
      |
      | LAN/WLAN
      v
Tablet / Handy / Notebook
```

All-in-One ist für einen einzelnen Rechner im lokalen Netz gedacht, etwa den
WinLaufen-PC, einen Sprecher-PC, einen separaten LAN-PC oder einen Raspberry Pi.
Der Browser darf auf beliebigen vorgesehenen LAN-/WLAN-Geräten laufen.

Der Installer legt eine Konfiguration an, die WinLaufen zunächst lokal erwartet
(`127.0.0.1:4444`) und den lokalen Live Server als reguläres Output Target
einträgt. Läuft WinLaufen auf demselben Rechner, ist das ein Zero-Config-Fall.
Andernfalls wird nach der Installation nur der WinLaufen-Host in Bridge Control
angepasst.

### Bridge only

```text
WinLaufen
   |
   v
Bridge
   |
   +----> Presentation Node A
   |
   +----> Presentation Node B
```

Wird vollständig installiert, auch wenn noch kein Ziel bekannt ist. Der
Ausgangszustand ist:

```text
WinLaufen-Host:  127.0.0.1:4444
Output Targets:  leer
```

Eine Bridge ohne Output Target ist **kein Installationsfehler**, sondern ein
gültiger Zwischenzustand. Vor dem produktiven Einsatz in Bridge Control:

1. WinLaufen-Adresse anpassen, falls WinLaufen auf einem anderen Rechner läuft,
2. mindestens ein Output Target eintragen.

### Presentation Node

```text
Bridge
    |
    v
Presentation Node
    |
    v
Web View
```

Benötigt während der Installation keine Bridge-Adresse. Nach der Installation
wird dieser Node auf der zuständigen Bridge als Output Target eingetragen.

Der Node darf im LAN stehen oder eine für einige Stunden gemietete Cloud-VM mit
öffentlicher IPv4-Adresse sein. Für den Cloud-Fall gibt es eine eigene
Kurzanleitung: [QUICKSTART_CLOUD.md](QUICKSTART_CLOUD.md). Dort werden nur
TCP 44440 und TCP 44441 freigegeben; TCP 44442 gehört nicht dazu. Die
verbindlichen Grenzen dieses Betriebs stehen in [Einsatzgrenzen](#einsatzgrenzen).

Der Installer zeigt am Ende die aktuell erkannten lokalen IP-Adressen als
Hinweis an. Diese Adressen werden **nicht** dauerhaft als Konfiguration
gespeichert.

### Unterstützte Plattformen

| Plattform | All-in-One | Bridge only | Presentation Node |
|---|---|---|---|
| Debian (aktuell) | ja | ja | ja |
| Ubuntu 24.04 LTS | ja | ja | ja |
| Ubuntu 26.04 LTS | ja | ja | ja |
| Raspberry Pi OS (aktuell) | ja | ja | ja |
| Windows 11 | ja | ja | **nein** |

Presentation Node auf Windows wird bewusst nicht unterstützt, weil dieses
Szenario nicht getestet und gepflegt wird. Für einen eigenständigen
Presentation Node bitte Linux verwenden.

Die Tabelle nennt die unterstützten Plattformen, nicht die veröffentlichten
Releasepakete: Als fertiges Paket erscheinen derzeit Linux amd64 und
Windows x64. Für Raspberry Pi OS auf ARM ist der [Entwicklerweg](DEVELOPMENT.md#installation-aus-dem-quellcode) erforderlich.

## 5. Verzeichnisse

### Linux

```text
/opt/winlaufen-web/lib/          Programmartefakte (JARs)
/opt/winlaufen-web/runtime/      gebündelte Java-Runtime, falls die Quelle eine mitbringt
/etc/winlaufen-web/              Konfiguration und persistente Veranstalterdaten
    bridge.properties            Veranstalter-Konfiguration der Bridge
                                 (competition.timezone nur für das Ausland)
    startlist.properties         importierte Startliste (nur bei Profilen mit Bridge)
    live-server.env              technische Live-Server-Parameter
/var/lib/winlaufen-web/          Arbeitsverzeichnis des Dienstkontos
```

### Windows

```text
C:\Program Files\WinLaufen Web\lib\        Programmartefakte
C:\Program Files\WinLaufen Web\runtime\    gebündelte Java-Runtime, falls die Quelle eine mitbringt
C:\ProgramData\WinLaufen Web\
    bridge.properties                      Veranstalter-Konfiguration
    live-server.properties                 technische Live-Server-Parameter
    startlist.properties                   importierte Startliste (nach Import)
```

### Wettkampf-Zeitzone

**Für Veranstaltungen in Deutschland ist hier nichts zu tun.** Sprecher-Web
verwendet standardmäßig `Europe/Berlin`; die Zeitzone des Rechners spielt keine
Rolle, auch nicht auf einem Server, der auf UTC steht.

Die Angabe betrifft ausschließlich die Zeitmessungen der Read API: Sie legt fest,
in welcher Zone die WinLaufen-Wettkampfzeit gelesen wird, wenn die Differenz zur
Systemzeit bestimmt wird. Für Uhr, Ergebnisse, Startliste und Web Viewer ist sie
ohne Bedeutung. Bridge Control zeigt die verwendete Zone im Abschnitt WinLaufen
an.

**Nur für eine Veranstaltung in einer anderen Zeitzone** wird sie in
`bridge.properties` eingetragen:

Linux:

```sh
sudo nano /etc/winlaufen-web/bridge.properties
```

Windows:

```powershell
notepad "C:\ProgramData\WinLaufen Web\bridge.properties"
```

Eintrag:

```properties
competition.timezone=America/New_York
```

Weitere Beispiele: `Europe/Prague`, `Australia/Sydney`. Nach der Änderung die
Bridge neu starten.

Ist der Wert **unbrauchbar** — etwa `America/New_Yrok` —, startet die Bridge
trotzdem, verwendet wieder `Europe/Berlin` und zeigt in Bridge Control eine
Warnung mit dem falschen Wert und beiden Konfigurationspfaden. Es wird **nicht**
auf die Zeitzone des Rechners zurückgefallen.

Sprecher-Web funktioniert auch ohne Internet oder eingerichteten Zeitserver.
Die Bedeutung für externe Anwendungen beschreibt das [Zeitmodell](API.md#5-zeitmodell).
Weitere Details:
[API.md](API.md#5-zeitmodell).

`startlist.properties` entsteht erst beim ersten erfolgreichen
Startlistenimport in Bridge Control. Der Installer legt diese Datei
nicht an und fasst sie nicht an; sie überlebt jedes Upgrade.

Der Live Server hält weder den Wettkampfstand noch die Startliste auf Platte.
Nach seinem Neustart liefert die Bridge beides beim Reconnect erneut.

## 6. Dienste

### 6.1 Linux — systemd

Betrieb über `systemd`. Je nach Profil:

| Profil | Units |
|---|---|
| All-in-One | `winlaufen-bridge.service`, `winlaufen-live-server.service` |
| Bridge only | `winlaufen-bridge.service` |
| Presentation Node | `winlaufen-live-server.service` |

Die Dienste starten automatisch beim Boot (`WantedBy=multi-user.target`),
laufen unter dem eigenen Systembenutzer `winlaufen` ohne Login-Shell und ohne
Root-Rechte, und protokollieren in das Journal.

```sh
systemctl status winlaufen-bridge
systemctl restart winlaufen-live-server
journalctl -u winlaufen-bridge -f
```

Die Bridge schreibt Änderungen aus Bridge Control in
`/etc/winlaufen-web/bridge.properties` zurück; diese Datei gehört daher dem
Dienstkonto. Der Pfad wird der Bridge über die Systemproperty
`winlaufen.bridge.config` mitgegeben.

### 6.2 Windows — geplante Aufgaben

Bridge und Live Server laufen als **geplante Aufgaben (Scheduled Tasks)** mit
dem Trigger „Beim Systemstart" unter dem Konto `LocalSystem`:

| Aufgabe | Profil |
|---|---|
| `WinLaufen Web Bridge` | All-in-One, Bridge only |
| `WinLaufen Web Live Server` | All-in-One |

Diese Methode ist bewusst gewählt:

* vollständig in Windows enthalten, kein zusätzlicher Service-Wrapper mit
  eigener Lizenz- und Distributionsfrage,
* startet ohne angemeldeten Benutzer und ohne offenes Konsolenfenster
  (`javaw.exe`),
* über `Start-ScheduledTask` / `Stop-ScheduledTask` bzw. die Aufgabenplanung
  bedienbar,
* idempotent: eine erneute Installation ersetzt die Aufgabe, statt sie zu
  duplizieren.

```powershell
Get-ScheduledTask     -TaskName 'WinLaufen Web Bridge'
Get-ScheduledTaskInfo -TaskName 'WinLaufen Web Bridge'
Stop-ScheduledTask    -TaskName 'WinLaufen Web Bridge'
Start-ScheduledTask   -TaskName 'WinLaufen Web Bridge'
```

## 7. Ports, Netzwerkvertrag und Firewall

### 7.1 Netzwerkvertrag

| Quelle | Ziel | Protokoll/Port | Zweck |
|---|---|---|---|
| Bridge | WinLaufen-PC | TCP 4444 | WinLaufen Sprecher-PC-Protokoll |
| Viewer | Live Server | TCP 44440 | Web View / Public HTTP / API |
| Browser | Live Server | TCP 44441 | Live-Aktualisierung im Browser |
| Bridge | Live Server | TCP 44441 | Datenübertragung der Bridge |
| Admin | Bridge | TCP 44442 | Bridge Control |

Welche Endpunkte auf welchem Port liegen — HTTP wie WebSocket —, steht
vollständig in [API.md](API.md#2-ports-und-endpunkte).

Typische URLs:

- Live-Ergebnisse: `http://<live-server-ip>:44440/`, lokal `http://localhost:44440/`
- Bridge Control: `http://<bridge-ip>:44442/`, lokal `http://localhost:44442/`

Notebooks, Tablets und Smartphones im selben Netz rufen die Live-Ergebnisse über
die LAN-Adresse des Rechners auf. In der realen Abnahme vom 30.08.2026 war das
zum Beispiel `http://192.168.95.198:44440/` — ein Beispiel aus jener Umgebung,
kein Vorgabewert.

44440/44441 müssen für Viewer im gewünschten LAN/WLAN erreichbar sein,
44442 für die vorgesehenen Administrationsgeräte. TCP 4444 ist die ausgehende
Verbindung der Bridge zum WinLaufen-PC. Der gemeinsame Port 44441 bleibt über
Pfade, Browser-Originprüfung und Ingest-Authentifizierung getrennt.

Vor dem Start prüft der Installer ausschließlich die Listenerports des gewählten
Profils. Ein Konflikt nennt Port, Zweck und soweit ermittelbar Prozess/PID sowie
den systemd-Dienst; sind mehrere Ports belegt, werden **alle** in einem Lauf
gemeldet. Es wird kein Ersatzport gewählt. TCP 4444 wird nicht geprüft, weil die
Bridge sich dorthin ausgehend verbindet. Ports, die bereits der bestehenden
Sprecher-Web-Installation gehören, sind kein Konflikt — sie werden übernommen.

Ein Lauf mit `--staging-root` installiert in ein Testverzeichnis und startet
nichts; dort wird die Portprüfung des Rechners übersprungen, weil kein Dienst
entsteht, der einen Port binden könnte. Der Installer sagt das in seiner Ausgabe.
Für eine produktive Installation bleibt die Prüfung unverändert. `--check-ports`
erzwingt sie auch im Testmodus.

Der bekannte Prototyp-Ingest-Secret bleibt eine Sicherheitsbegrenzung. Port
44441 darf nicht unkontrolliert ins Internet weitergeleitet werden; Details
stehen in [Einsatzgrenzen](#einsatzgrenzen).

TCP 44442 ist der Administrationsport der Bridge. Bridge Control besitzt in
dieser Prototypversion bewusst keine Benutzer- oder Login-Authentifizierung. Jeder Teilnehmer im
erreichbaren Netz kann die Oberfläche grundsätzlich öffnen und Konfigurationen
ändern. Der Port gehört daher nur in ein vertrauenswürdiges LAN, nicht in ein
Gäste-WLAN, hinter eine unkontrollierte Portweiterleitung oder direkt ins
öffentliche Internet. Die Control-API gibt Target-Secrets nicht aus; das ersetzt
keine Netzgrenze für den Administrationszugriff.

### 7.2 Firewall unter Linux

Der Installer aktiviert weder UFW noch firewalld und ändert keine UFW-,
nftables- oder firewalld-Regeln. Er erkennt bekannte aktive lokale Firewalls
soweit möglich und gibt nur Hinweise aus. Je nach Profil müssen lokale oder
externe Firewalls folgende Verbindungen für die vorgesehenen LAN-Clients
zulassen:

| Profil | Eingehend | Ausgehend |
|---|---|---|
| All-in-One | TCP 44440, 44441, 44442 | TCP 4444 zum WinLaufen-PC |
| Bridge only | TCP 44442 | TCP 4444 zum WinLaufen-PC |
| Presentation Node | TCP 44440, 44441 | keine WinLaufen-Verbindung |

Ein lokal laufender Listener beweist nicht, dass eine lokale, externe,
Router-, VLAN- oder Cloud-Firewall die Verbindung aus dem LAN zulässt.

Läuft ein Presentation Node auf einer Cloud-VM, werden dieselben zwei Ports in
der Firewall des Anbieters freigegeben: TCP 44440 für die Web View und TCP 44441
für den Bridge-Ingest. **TCP 44442 wird dort nicht freigegeben**; Bridge Control
besitzt keine Anmeldung und läuft ohnehin auf der Bridge, nicht auf dem Node.

### 7.3 Windows Defender Firewall

Der Installer legt nur die für das Profil erforderlichen eingehenden TCP-Regeln
an. Sie sind auf die Netzwerkprofile `Private` und `Domain` beschränkt; für
`Public` wird keine Freigabe erzeugt. Bridge only erhält nur TCP 44442,
All-in-One TCP 44440, 44441 und 44442. Der Uninstaller entfernt ausschließlich
die von diesem Projekt selbst benannten Regeln.

## 8. Prüfung nach der Installation

Nach dem Start validiert der Installer die installierten Dienste, ihre
Stabilität, die eigenen Listener und die lokalen HTTP-Endpunkte. Erst wenn diese
lokale Installationsintegrität gegeben ist, folgt ein separater
Betriebsbereitschaftsbericht. `DISCONNECTED` oder `RETRY_WAIT` bei WinLaufen und
Output Targets sind dort Hinweise und führen nicht zu Exit-Code ungleich 0.
Unter Windows synchronisiert er davor noch die Firewallregeln.

Eigene Prüfung unter Linux:

```sh
systemctl status winlaufen-bridge winlaufen-live-server --no-pager
sudo ss -ltnp | grep -E ':(44440|44441|44442)\b'
```

Bei `bridge-only` ist nur 44442 zu erwarten, bei `presentation-node` nur 44440
und 44441.

Danach:

| Profil | Nächster Schritt |
|---|---|
| All-in-One | Nichts, wenn WinLaufen lokal läuft. Sonst WinLaufen-Adresse in Bridge Control ändern. |
| Bridge only | WinLaufen-Adresse prüfen und mindestens ein Output Target eintragen. |
| Presentation Node | Diesen Node auf der gewünschten Bridge als Output Target eintragen: Bridge Control → Weiteren Live-Server verbinden → IP-Adresse eintragen. |

Der vollständige Bedienablauf — WinLaufen verbinden, Bridge Control einrichten,
Browseradressen, Statusanzeigen, Verhalten bei Ausfällen — steht im
[Bedienerhandbuch](BEDIENERHANDBUCH.md). Kurz zusammengefasst:

1. In WinLaufen **Abwicklung → Sprecher-PC… → Verbinden** wählen. Erst dann
   stellt WinLaufen die Schnittstelle auf TCP 4444 bereit; die Bridge verbindet
   sich von selbst dorthin. Solange das fehlt, meldet Bridge Control **Nicht
   verbunden** — ein erwarteter Betriebszustand, kein Installationsfehler.
2. Bridge Control unter `http://<bridge-ip>:44442/` öffnen und, falls WinLaufen
   auf einem anderen Rechner läuft, dessen Adresse eintragen.
3. Live-Ergebnisse unter `http://<live-server-ip>:44440/` prüfen.

Für einen zusätzlichen Live-Server im Internet siehe
[QUICKSTART_CLOUD.md](QUICKSTART_CLOUD.md).

### Fehlerdiagnose

| Beobachtung | Prüfung und nächster Schritt |
|---|---|
| PowerShell findet den Installer nicht | Im Explorer den entpackten Ordner mit `installer`, `lib`, `runtime` öffnen und den Pfad wie in Abschnitt 1.2 erneut übernehmen. |
| `PSSecurityException` | Skriptausführung im selben Fenster wie in Abschnitt 1.2 freigeben. Erzwingt eine Organisationsrichtlinie die Sperre, muss die zuständige Administration helfen. |
| Fehlende Administratorrechte | Windows PowerShell ausdrücklich über „Als Administrator ausführen“ starten. |
| Ein Port ist belegt | Genannten Prozess/Dienst prüfen; keinen fremden Dienst ungeprüft beenden. Sprecher-Web wechselt nicht selbst auf andere Ports. |
| Dienste starten nicht | Unter Linux `journalctl -u winlaufen-bridge -u winlaufen-live-server` prüfen; unter Windows Aufgabenstatus aus Abschnitt 6.2 und Installerfehlermeldung prüfen. |
| Lokal erreichbar, aus dem WLAN nicht | Adressen, Routing und Firewall prüfen. Unter Windows gelten Freigaben nur für Privat/Domäne; nur ein tatsächlich vertrauenswürdiges Veranstaltungsnetz als Privat einstufen. |
| Installation erfolgreich, Quelle oder Ziel nicht verbunden | Adresse in Bridge Control, WinLaufen-Sprecher-PC-Funktion und Netzwerkfreigaben prüfen; Bedienablauf im Handbuch. |

## 9. Deinstallation

Öffnen Sie das entpackte Releasepaket wie in Abschnitt 1 beschrieben. Unter
Windows benötigen Sie wieder eine PowerShell mit Administratorrechten und
die Freigabe der Skriptausführung. Wählen Sie jeweils **einen** der Befehle:
Ohne Löschoption bleiben Ihre Einstellungen und die Startliste erhalten,
mit `--purge` bzw. `-Purge` werden sie ebenfalls gelöscht.

Linux:

```sh
sudo ./installer/linux/uninstall.sh            # Dienste und Programmdateien
sudo ./installer/linux/uninstall.sh --purge    # zusätzlich Konfiguration
```

Ohne `--purge` bleiben `/etc/winlaufen-web` und `/var/lib/winlaufen-web`
erhalten, damit eine gepflegte WinLaufen-Adresse, die Target-Liste und die
importierte Startliste eine Neuinstallation überleben. `--purge` entfernt auch
`startlist.properties`; danach ist ein erneuter Import nötig.

Windows:

```powershell
.\installer\windows\Uninstall-WinLaufenWeb.ps1
.\installer\windows\Uninstall-WinLaufenWeb.ps1 -Purge
```

Die Deinstallationsskripte liegen in beiden Installationswegen an derselben
Stelle: im entpackten Releasepaket unter `installer/` und im Checkout ebenso.

## 10. Was der Installer bewusst nicht tut

* Er fragt **keine** WinLaufen-IP, Target-IP, Hostnamen, URL, Domain oder
  WSS-Adresse ab.
* Er blockiert die Installation **nicht**, wenn der spätere WinLaufen-PC, die
  LAN-IP, der Presentation Node oder ein WAN-Ziel noch unbekannt sind.
* Er behandelt eine nicht erreichbare Quelle, ein nicht verbundenes Output
  Target oder einen noch nicht verbundenen lokalen All-in-One-Datenpfad nicht
  als lokalen Installationsfehler. Diese Zustände erscheinen im
  Betriebsbereitschaftsbericht als Hinweis oder Warnung.
* Er speichert erkannte lokale IP-Adressen **nicht** als dauerhafte
  Konfiguration.
* Er erzeugt **kein** eigenes Ingest-Secret. Es bleibt beim dokumentierten
  Prototyp-Default, damit Bridge und Presentation Node auf getrennten Rechnern
  ohne zusätzlichen Abgleich zusammenarbeiten. Siehe [Einsatzgrenzen](#einsatzgrenzen).

## 11. Installationsstatus

Der aktuelle Abnahmestand steht im [technischen Projektstatus](STATUS.md);
die manuellen Abnahmetests und ihre protokollierten Nachweise in
[SMOKE_TESTS.md](SMOKE_TESTS.md).

## Einsatzgrenzen

**Diese Einschränkungen sind bekannt, bewusst akzeptiert und noch nicht behoben.**

### Bridge Control auf TCP 44442 hat keine Anmeldung

Diese Oberfläche besitzt bewusst keine Benutzer- oder Login-Authentifizierung.
Wer den Port erreicht, kann Bridge Control öffnen und die Konfiguration ändern —
WinLaufen-Quelle, Output Targets und die öffentliche Darstellung. Target-Secrets
gibt die Control-API nicht aus; das ersetzt jedoch keine Zugriffsbeschränkung.
44442 darf deshalb nur in einem vertrauenswürdigen LAN erreichbar sein: nicht im
Gäste-WLAN, nicht über unkontrollierte Portweiterleitungen, nicht direkt aus dem
Internet.

### Die Read API ist unauthentifiziert und zeigt Teilnehmerdaten

Die Read API auf TCP 44440 ist **read-only** — niemand kann darüber etwas
ändern. Das sagt aber nichts darüber, wer sie **lesen** darf, und
die Schnittstelle liefert reale Teilnehmerdaten: Vorname, Nachname,
Jahrgang, Verein, Verband, Nation, Startnummer, Klasse, Startzeit und Strecke.

Das ist ein vollständiger Teilnehmerbestand, nicht nur der öffentlich angezeigte
Wettkampfstand — auf demselben unauthentifizierten Port wie der Web Viewer. Port
44440 gehört deshalb nur in Netze oder hinter Zugänge, in denen diese Daten
gelesen werden dürfen. Wer einen Presentation Node öffentlich betreibt,
veröffentlicht damit auch die Startliste.

### Der Bridge-Ingest verwendet ein bekanntes Secret

Der Ingest des Live Servers ist authentifiziert, verwendet aber weiterhin ein
**bekanntes Development-Secret**
(`local-development-secret`), solange `winlaufen.live.secret` nicht gesetzt ist.
Der Live Server bindet seinen WebSocket-Port standardmäßig auf `0.0.0.0`, und
auch der Installer erzeugt bewusst kein eigenes Secret.

**Jeder Teilnehmer, der den Ingest-WebSocket auf Port 44441 erreichen kann und
das bekannte Secret kennt, kann sich gegenüber dem Live Server als Bridge
ausgeben.** Er kann damit den kompletten veröffentlichten Stand ersetzen und
insbesondere fälschen:

- Wettkampfdaten und Wettkampfstruktur,
- die angezeigte Uhrzeit,
- Ergebnisse und Ranglisten,
- Klassenstände und Current-Finish-Markierung,
- öffentliche WinLaufen-Nachrichten.

Gefälschte Daten werden angenommen, bestätigt und sofort an **alle** verbundenen
Browser ausgeliefert. Die echte Bridge bemerkt das nicht.

### Verbindliche Einsatzgrenzen dieser Prototypversion

Es werden drei Betriebsarten unterschieden. Sie haben unterschiedliche Grenzen.

**1. Kontrolliertes LAN — der Normalfall**

- Einsatz in kontrollierten Vereins- bzw. Veranstaltungsnetzen.
- Bridge und Live Server sind nur im vertrauenswürdigen Netz erreichbar.
- Kein Betrieb in offenen Gäste-WLANs oder gemeinsam genutzten Netzen.
- **Keine Portweiterleitung** der LAN-Installation ins öffentliche Internet.
- Die Windows-Firewallregeln bleiben bewusst auf Private und Domain beschränkt.

**2. Temporärer Selfhost-Presentation-Node mit öffentlicher IPv4**

Ein Verein mietet für einige Stunden eine Cloud-VM, installiert dort das Profil
Presentation Node und verbindet die eigene Bridge über die öffentliche
IP-Adresse. Das ist ausdrücklich vorgesehen — siehe
[QUICKSTART_CLOUD.md](QUICKSTART_CLOUD.md) — und unterliegt diesen
Grenzen:

- Öffentlich freigegeben werden **nur** TCP 44440 (Web View) und TCP 44441
  (Bridge-Ingest) **des gemieteten Nodes**.
- **TCP 44442 gehört dort nicht hin.** Bridge Control hat keine Anmeldung und
  darf niemals öffentlich erreichbar sein — weder auf dem Node noch über eine
  Portweiterleitung zur Bridge im Vereinsnetz.
- Die Bridge im Vereinsnetz bleibt unverändert unerreichbar von außen; sie
  verbindet ausgehend.
- Die Übertragung ist **unverschlüsselt**. Mitgelesen werden können deshalb
  sowohl die übertragenen Daten als auch der **Verbindungsschlüssel**.
- Wer den Verbindungsschlüssel kennt oder mitliest und 44441 erreicht, kann
  unerwünschte Daten einspeisen und damit den kompletten veröffentlichten Stand
  ersetzen (siehe oben). Solange der bekannte Standardschlüssel aktiv ist,
  genügt dafür die Kenntnis der IP-Adresse.
- Für einen temporären Selfhost-/Testserver ist dieser bewusst einfache Betrieb
  vertretbar. Für einen dauerhaften oder zentral betriebenen Dienst ist
  verschlüsselte Übertragung vorgesehen (siehe Punkt 3).
- Deshalb: nur für die Dauer der Veranstaltung betreiben, danach die VM
  **abschalten oder löschen**, und wo möglich `winlaufen.live.secret` auf dem
  Node und den Verbindungsschlüssel des Targets in Bridge Control auf einen
  eigenen Wert setzen.
- Keine Eignung für Anmeldungen oder vertrauliche Daten. Auch die vollständige
  importierte Startliste wird öffentlich lesbar. Importieren Sie hier nur
  Teilnehmerdaten, die für diese Veröffentlichung vorgesehen sind; das Ausblenden
  von Spalten in der Browseransicht beschränkt den Schnittstellenzugriff nicht.

**3. Dauerhafter abgesicherter WAN-Betrieb**

Für produktiven Dauerbetrieb über WAN
sind **WSS mit gültigem Zertifikat sowie individuell provisionierte Secrets pro
Target** erforderlich. Das ist bewusst nicht Teil dieser Prototype Baseline und
bleibt ein offenes Production-Hardening-Thema.

Der Live Server weist beim Start ausdrücklich auf das aktive Default-Secret hin.
