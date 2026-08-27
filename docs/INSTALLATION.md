# WinLaufen Web — Installation

Dieses Dokument beschreibt die rollenbasierte Installation für Linux und
Windows 11.

Der zentrale Grundsatz:

> Installation und Netzwerk-/Runtime-Konfiguration sind getrennt. Während der
> Installation werden keine WinLaufen-IP-Adressen, Target-IP-Adressen,
> Hostnamen oder URLs benötigt.

Ein Rechner kann damit Tage vor der Veranstaltung vollständig installiert
werden, auch wenn das spätere Veranstaltungsnetz noch unbekannt ist.

## 1. Installationsprofile

Der Installer fragt genau eine Sache ab: das Profil.

| Profil | Installiert | Typischer Einsatz |
|---|---|---|
| **All-in-One** | Bridge + Live Server | Standard; direkt auf dem WinLaufen-PC |
| **Bridge only** | nur Bridge | eigener Rechner nur für die WinLaufen-Anbindung |
| **Presentation Node** | nur Live Server / Web View | eigener Webserver im LAN oder WAN |

### All-in-One — empfohlener Standard

```text
WinLaufen
    |
    v
Bridge
    |
    v
lokaler Live Server
    |
    v
Web View
```

Der Installer legt eine Konfiguration an, die WinLaufen lokal erwartet
(`127.0.0.1:4444`) und den lokalen Live Server als reguläres Output Target
einträgt. Läuft WinLaufen auf demselben Rechner, ist das ein Zero-Config-Fall:
nach der Installation ist keine weitere Netzwerkkonfiguration nötig.

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

## 3. Java Runtime

Die Anwendung benötigt **Java 25** (`maven.compiler.release=25` im Root-POM).

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

## 4. Linux-Installation

### Ablauf

```sh
# 1. Distribution bauen (auf einem Rechner mit JDK 25 und Maven)
./installer/common/build-dist.sh --with-runtime

# 2. dist/ auf den Zielrechner kopieren, dort:
sudo ./installer/linux/install.sh
```

Der Installer fragt das Profil ab und installiert dann ohne weitere Rückfragen.
Nicht-interaktiv:

```sh
sudo ./installer/linux/install.sh --profile all-in-one
sudo ./installer/linux/install.sh --profile bridge-only
sudo ./installer/linux/install.sh --profile presentation-node
```

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

### Deinstallation

```sh
sudo ./installer/linux/uninstall.sh            # Dienste und Programmdateien
sudo ./installer/linux/uninstall.sh --purge    # zusätzlich Konfiguration
```

Ohne `--purge` bleiben `/etc/winlaufen-web` und `/var/lib/winlaufen-web`
erhalten, damit eine gepflegte WinLaufen-Adresse und Target-Liste eine
Neuinstallation überleben.

## 5. Windows-11-Installation

### Ablauf

```powershell
# 1. Distribution bauen (Rechner mit JDK 25 und Maven)
.\installer\common\build-dist.ps1 -WithRuntime

# 2. dist\ auf den Zielrechner kopieren, PowerShell als Administrator:
.\installer\windows\Install-WinLaufenWeb.ps1
```

Nicht-interaktiv:

```powershell
.\installer\windows\Install-WinLaufenWeb.ps1 -Profile AllInOne
.\installer\windows\Install-WinLaufenWeb.ps1 -Profile BridgeOnly
```

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

## 6. Was der Installer bewusst nicht tut

* Er fragt **keine** WinLaufen-IP, Target-IP, Hostnamen, URL, Domain oder
  WSS-Adresse ab.
* Er blockiert die Installation **nicht**, wenn der spätere WinLaufen-PC, die
  LAN-IP, der Presentation Node oder ein WAN-Ziel noch unbekannt sind.
* Er speichert erkannte lokale IP-Adressen **nicht** als dauerhafte
  Konfiguration.
* Er erzeugt **kein** eigenes Ingest-Secret. Es bleibt beim dokumentierten
  Prototyp-Default, damit Bridge und Presentation Node auf getrennten Rechnern
  ohne zusätzlichen Abgleich zusammenarbeiten. Siehe README.md, Abschnitt
  „Known prototype security limitation".

## 7. Nach der Installation

| Profil | Nächster Schritt |
|---|---|
| All-in-One | Nichts, wenn WinLaufen lokal läuft. Sonst WinLaufen-Adresse in Bridge Control ändern. |
| Bridge only | WinLaufen-Adresse prüfen und mindestens ein Output Target eintragen. |
| Presentation Node | Diesen Node auf der gewünschten Bridge als Output Target eintragen. |

Erreichbarkeit nach der Installation:

```text
Bridge Control:  http://localhost:8090/
Web View:        http://<live-server>:8080/
Browser-Live:    ws://<live-server>:8081/live/v1
Bridge-Ingest:   ws://<live-server>:8081/bridge/v1/channels/local
```

Die manuellen Abnahmetests stehen in [SMOKE_TESTS.md](SMOKE_TESTS.md).
