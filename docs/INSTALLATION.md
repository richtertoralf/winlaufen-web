# Sprecher-Web — Installation

Dieses Dokument ist die **technische Installationsreferenz** für Linux und
Windows 11: Profile, Plattformen, Pfade, Dienste, Firewall und Deinstallation.

> Wer Sprecher-Web als Veranstalter installieren und betreiben möchte, findet
> die durchgehende Schritt-für-Schritt-Anleitung im
> **[Bedienerhandbuch](BEDIENERHANDBUCH.md)**.

Sprecher-Web ist keine Web-Version der Wettkampfsoftware WinLaufen.
Es nutzt deren Sprecher-PC-Schnittstelle auf TCP 4444 und stellt die gelieferten
Live-Ergebnisdaten webbasiert bereit.

Der zentrale Grundsatz:

> Installation und Netzwerk-/Runtime-Konfiguration sind getrennt. Während der
> Installation werden keine WinLaufen-IP-Adressen, Target-IP-Adressen,
> Hostnamen oder URLs benötigt.

Ein Rechner kann damit Tage vor der Veranstaltung vollständig installiert
werden, auch wenn das spätere Veranstaltungsnetz noch unbekannt ist.

Nicht erreichbare externe Quellen und Output Targets verhindern die
Installation nicht. Der Installer prüft nach erfolgreicher lokaler Installation
den aktuellen Verbindungszustand und gibt Hinweise zur weiteren Konfiguration
aus. Ein nicht verbundener lokaler All-in-One-Datenpfad wird als Warnung
gemeldet. Lokale Dienst-, Listener- und HTTP-Fehler bleiben dagegen
Installationsfehler.

## 1. Installationsprofile

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

Das lokale Target verwendet denselben Bridge→Live-Server-Pfad wie ein entferntes
Ziel, inklusive Snapshot, ACK, Retry und Full Resync. Es gibt keinen zweiten
„local shortcut".

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
verbindlichen Grenzen dieses Betriebs stehen in README.md, Abschnitt
„Known prototype security limitation".

Der Installer zeigt am Ende die aktuell erkannten lokalen IP-Adressen als
Hinweis an. Diese Adressen werden **nicht** dauerhaft als Konfiguration
gespeichert.

## 2. Unterstützte Plattformen

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

## 3. Release- und Developer-Installation

### Release-Installation für Endanwender

Für ein veröffentlichtes Release gilt:

1. passendes Linux- oder Windows-Archiv von GitHub Releases herunterladen,
2. Archiv entpacken,
3. enthaltenen Installer starten und nur das Profil wählen.

Endanwender benötigen dafür weder Git noch Maven, keinen Source Checkout und
keinen eigenen Maven-Build. Enthält das Paket eine `jlink`-Runtime, ist auch
kein separat installiertes JDK für den Betrieb erforderlich.

### Developer-Installation aus dem Source Checkout

Voraussetzungen:

- Git
- JDK 25

System-Maven ist keine Voraussetzung. Der Maven Wrapper verwendet die im
Repository festgelegte Maven-Version.

Linux:

```sh
git clone https://github.com/richtertoralf/winlaufen-web.git
cd winlaufen-web
./mvnw clean package
sudo ./installer/linux/install.sh
```

Windows:

```powershell
git clone https://github.com/richtertoralf/winlaufen-web.git
Set-Location winlaufen-web
.\mvnw.cmd clean package
# Danach PowerShell als Administrator starten:
.\installer\windows\Install-WinLaufenWeb.ps1
```

## 4. Java Runtime

Der Developer-Build benötigt **JDK 25** (`maven.compiler.release=25` im
Root-POM). Für die Installation gilt:

Der Installer geht in dieser Reihenfolge vor:

1. Ist eine mitgelieferte Runtime in der Distribution vorhanden
   (`dist/runtime/`), wird diese verwendet. Der Rechner braucht dann kein
   eigenes Java.
2. Sonst wird das System-Java geprüft. Ist es älter als Java 25, bricht der
   Installer mit einer klaren Meldung ab und nennt beide Auswege.

Eine reduzierte Runtime wird beim Bauen der Distribution per `jlink` erzeugt:

```sh
./installer/common/build-dist.sh --with-runtime
```

```powershell
.\installer\common\build-dist.ps1 -WithRuntime
```

Die Runtime ist immer plattformspezifisch: ein Linux-Build erzeugt eine
Linux-Runtime, ein Windows-Build eine Windows-Runtime. Ein Cross-Build wird
bewusst nicht versucht.

## 5. Linux-Installation

### Ablauf

```sh
# 1. Optional eine Distribution auf dem Developer-Rechner bauen
./installer/common/build-dist.sh --with-runtime

# 2. dist/ auf den Zielrechner kopieren, dort:
sudo ./installer/linux/install.sh
```

Läuft auf dem Zielrechner bereits WinLaufen mit aktiver
Sprecher-PC-Verbindung, diese vor Installation und Upgrade eines Profils mit
Bridge trennen (**Abwicklung → Sprecher-PC… → Trennen**) und danach wieder
verbinden. Für einen Presentation Node entfällt das.

Der Installer fragt das Profil ab und installiert dann ohne weitere Rückfragen.
Nicht-interaktiv:

```sh
sudo ./installer/linux/install.sh --profile all-in-one
sudo ./installer/linux/install.sh --profile bridge-only
sudo ./installer/linux/install.sh --profile presentation-node
```

Vor dem Start prüft er ausschließlich die Listenerports des gewählten Profils.
Ein Konflikt nennt Port, Zweck und soweit ermittelbar Prozess/PID sowie den
systemd-Dienst. Es wird kein Ersatzport gewählt. TCP 4444 wird nicht geprüft,
weil die Bridge sich dorthin ausgehend verbindet.

Nach dem Start validiert der Installer die installierten systemd-Units, ihre
Stabilität, die eigenen Listener und die lokalen HTTP-Endpunkte. Erst wenn diese
lokale Installationsintegrität gegeben ist, folgt ein separater
Betriebsbereitschaftsbericht. `DISCONNECTED` oder `RETRY_WAIT` bei WinLaufen und
Output Targets sind dort Hinweise und führen nicht zu Exit-Code ungleich 0.

### Verzeichnisstruktur

```text
/opt/winlaufen-web/lib/          Programmartefakte (JARs)
/opt/winlaufen-web/runtime/      optionale gebündelte Java-Runtime
/etc/winlaufen-web/              Konfiguration
    bridge.properties            Veranstalter-Konfiguration der Bridge
    live-server.env              technische Live-Server-Parameter
/var/lib/winlaufen-web/          Arbeitsverzeichnis des Dienstkontos
```

### Dienste

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

### Wiederholte Installation und Upgrade

Der Installer ist upgrade-fähig:

* Vorhandene Konfigurationsdateien werden **nie** überschrieben. Defaults
  entstehen nur bei einer echten Erstinstallation.
* Units werden ersetzt, nicht dupliziert.
* Ein Profilwechsel entfernt den nicht mehr benötigten Dienst, statt ihn
  verwaist zurückzulassen.
* Die exakten früheren Installer-Netzwerkdefaults werden auf 44440–44442
  migriert; gepflegte Veranstalterwerte und Target-Listen bleiben erhalten.

### Firewall unter Linux

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

### Deinstallation

```sh
sudo ./installer/linux/uninstall.sh            # Dienste und Programmdateien
sudo ./installer/linux/uninstall.sh --purge    # zusätzlich Konfiguration
```

Ohne `--purge` bleiben `/etc/winlaufen-web` und `/var/lib/winlaufen-web`
erhalten, damit eine gepflegte WinLaufen-Adresse und Target-Liste eine
Neuinstallation überleben.

## 6. Windows-11-Installation

### Ablauf

```powershell
# 1. Optional eine Distribution auf dem Developer-Rechner bauen
.\installer\common\build-dist.ps1 -WithRuntime

# 2. dist\ auf den Zielrechner kopieren, PowerShell als Administrator:
.\installer\windows\Install-WinLaufenWeb.ps1
```

Nicht-interaktiv:

```powershell
.\installer\windows\Install-WinLaufenWeb.ps1 -Profile AllInOne
.\installer\windows\Install-WinLaufenWeb.ps1 -Profile BridgeOnly
```

Die Installation muss in einer PowerShell mit Administratorrechten erfolgen.
Ohne diese Rechte bricht der Installer vor Änderungen mit einem entsprechenden
Hinweis ab. Er prüft die profilabhängigen Listenerports und wählt bei Konflikten
keinen Ersatzport.

**Skriptausführung.** Windows blockiert PowerShell-Skripte standardmäßig; der
Installer scheitert dann mit `PSSecurityException` („Die Ausführung von Skripts
ist auf diesem System deaktiviert"). Für das aktuelle Fenster freigeben:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\installer\windows\Install-WinLaufenWeb.ps1
```

`-Scope Process` gilt nur für dieses PowerShell-Fenster und ändert keine
systemweite Richtlinie. Alternativ als einmaliger Aufruf:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\installer\windows\Install-WinLaufenWeb.ps1"
```

**Vor Installation und Upgrade eines Profils mit Bridge** (All-in-One, Bridge
only) muss in WinLaufen die Sprecher-PC-Verbindung getrennt werden:
**Abwicklung → Sprecher-PC… → Trennen**. WinLaufen selbst muss nicht beendet
werden. Nach der Installation wieder **Verbinden**. Für einen Presentation Node
entfällt dieser Schritt. Ob ein Upgrade auch bei aktiver Verbindung zuverlässig
funktioniert, ist noch nicht geprüft; bis dahin gilt dieser Ablauf verbindlich.

Nach erfolgreicher lokaler Validierung synchronisiert der Installer die
Windows-Firewallregeln und gibt anschließend die nicht blockierende
Betriebsdiagnose für WinLaufen und Output Targets aus. Deren Verbindungszustand
entscheidet nicht über den Installationserfolg.

### Verzeichnisstruktur

```text
C:\Program Files\WinLaufen Web\lib\        Programmartefakte
C:\Program Files\WinLaufen Web\runtime\    optionale gebündelte Java-Runtime
C:\ProgramData\WinLaufen Web\
    bridge.properties                      Veranstalter-Konfiguration
    live-server.properties                 technische Live-Server-Parameter
```

### Hintergrunddienste

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

### Deinstallation

```powershell
.\installer\windows\Uninstall-WinLaufenWeb.ps1
.\installer\windows\Uninstall-WinLaufenWeb.ps1 -Purge
```

### Windows Defender Firewall

Der Installer legt nur die für das Profil erforderlichen eingehenden TCP-Regeln
an. Sie sind auf die Netzwerkprofile `Private` und `Domain` beschränkt; für
`Public` wird keine Freigabe erzeugt. Bridge only erhält nur TCP 44442,
All-in-One TCP 44440, 44441 und 44442. Der Uninstaller entfernt ausschließlich
die von diesem Projekt selbst benannten Regeln.

## 7. Was der Installer bewusst nicht tut

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
  ohne zusätzlichen Abgleich zusammenarbeiten. Siehe README.md, Abschnitt
  „Known prototype security limitation".

## 8. Netzwerkvertrag und Zugriff nach der Installation

| Profil | Nächster Schritt |
|---|---|
| All-in-One | Nichts, wenn WinLaufen lokal läuft. Sonst WinLaufen-Adresse in Bridge Control ändern. |
| Bridge only | WinLaufen-Adresse prüfen und mindestens ein Output Target eintragen. |
| Presentation Node | Diesen Node auf der gewünschten Bridge als Output Target eintragen: Bridge Control → Weiteren Live-Server verbinden → IP-Adresse eintragen. |

| Quelle | Ziel | Protokoll/Port | Zweck |
|---|---|---|---|
| Bridge | WinLaufen-PC | TCP 4444 | WinLaufen Sprecher-PC-Protokoll |
| Viewer | Live Server | TCP 44440 | Web View / Public HTTP / API |
| Browser | Live Server | TCP 44441 | Live WebSocket auf `/live/v1` |
| Bridge | Live Server | TCP 44441 | authentifizierter Bridge-Ingest auf `/bridge/v1/channels/<channel>` |
| Admin | Bridge | TCP 44442 | Bridge Control |

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

Der bekannte Prototyp-Ingest-Secret bleibt eine Sicherheitsbegrenzung. Port
44441 darf nicht unkontrolliert ins Internet weitergeleitet werden; Details
stehen in README.md unter „Known prototype security limitation".

TCP 44442 ist der Administrationsport der Bridge. Bridge Control besitzt in
v0.1 bewusst keine Benutzer- oder Login-Authentifizierung. Jeder Teilnehmer im
erreichbaren Netz kann die Oberfläche grundsätzlich öffnen und Konfigurationen
ändern. Der Port gehört daher nur in ein vertrauenswürdiges LAN, nicht in ein
Gäste-WLAN, hinter eine unkontrollierte Portweiterleitung oder direkt ins
öffentliche Internet. Die Control-API gibt Target-Secrets nicht aus; das ersetzt
keine Netzgrenze für den Administrationszugriff.

## 9. Nach der Installation

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

## 10. Installationsstatus

Der aktuelle Abnahmestand steht in der [README](../README.md#projektstatus);
die manuellen Abnahmetests und ihre protokollierten Nachweise in
[SMOKE_TESTS.md](SMOKE_TESTS.md).
