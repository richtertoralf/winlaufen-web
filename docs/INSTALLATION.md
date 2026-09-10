# Sprecher-Web — Installation

Dieses Dokument ist die **technische Installationsreferenz** für Linux und
Windows 11: die beiden Installationswege, Upgrade, Profile, Plattformen, Pfade,
Dienste, Ports, Konfiguration, Firewall, Prüfung nach der Installation und
Deinstallation.

Die Kurzfassung der Befehle steht in der
[README](../README.md#installation-und-upgrade); hier stehen die Details und die
Begründungen.

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

## 0. Die zwei Installationswege

Es gibt zwei Wege. Sie unterscheiden sich in den Voraussetzungen und im
installierten Versionsstand.

**Releasepaket.** Ein Releasepaket ist eine bereits fertig gebaute Binary
Distribution einer veröffentlichten Version. Es wird auf der
[Releases-Seite](https://github.com/richtertoralf/winlaufen-web/releases) als
Dateianhang veröffentlicht und enthält:

```text
winlaufen-web-<version>-<plattform>/
    lib/        winlaufen-web-bridge.jar, winlaufen-web-live-server.jar
    runtime/    plattformspezifische Java-Runtime (jlink)
    installer/  linux/, windows/, common/
    VERSION     Build-Kennung des Standes, aus dem gebaut wurde
```

**Ein Releasepaket ist nicht das Git-Repository.** `git clone` lädt
ausschließlich den Quelltext und **keine** Release-Artefakte herunter; ein
Checkout enthält weder gebaute JARs noch eine Java-Runtime. Wer ein
Releasepaket haben will, lädt die Archivdatei von der Releases-Seite.

**Quellcode.** Der Source-Weg klont das Repository, baut die JARs lokal mit dem
Maven Wrapper und ruft denselben Installer aus dem Checkout auf.

| | Releasepaket | Source-Build |
|---|---|---|
| Zielgruppe | Anwender und Administratoren | Entwickler |
| Git nötig | nein | ja |
| Maven-Build nötig | nein | ja |
| JDK 25 nötig | nein | ja |
| Java-Runtime enthalten | ja, im Paket unter `runtime/` | nein — System-Java ≥ 25 erforderlich |
| Versionsstand | genau die veröffentlichte Version | der ausgecheckte Git-Stand |
| Für Produktivrechner empfohlen | ja | eher nicht |
| Feature-Branches testbar | nein | ja |

Beide Wege verwenden **dieselben Installerskripte** und erzeugen dieselben
Pfade, Dienste und Ports. Der Installer erkennt selbst, in welchem Layout er
liegt: Ein entpacktes Releasepaket besitzt `lib/`, ein Source-Checkout hat
stattdessen `bridge/target/` und `live-server/target/`.

Die eine sichtbare Folge des Unterschieds ist die Java-Runtime. Liegt neben dem
Installer ein `runtime/`-Verzeichnis — also im Releasepaket oder in einer
selbst mit `--with-runtime` gebauten Distribution —, installiert der Installer
diese Runtime mit und die Dienste verwenden sie. Andernfalls sucht er ein
System-Java und verlangt mindestens Version 25.

## 1. Installation aus dem Releasepaket

Empfohlen für alle Rechner, auf denen Sprecher-Web nur betrieben und nicht
entwickelt wird.

### 1.1 Linux

Auf der Releases-Seite `winlaufen-web-<version>-linux-amd64.tar.gz`
herunterladen, dann:

```sh
tar -xzf winlaufen-web-<version>-linux-amd64.tar.gz
cd winlaufen-web-<version>-linux-amd64
sudo ./installer/linux/install.sh --profile all-in-one
```

Ohne `--profile` fragt der Installer das Profil interaktiv ab; die weiteren
Werte sind `bridge-only` und `presentation-node`.

Es wird dafür **kein** `git clone`, **kein** `./mvnw clean package` und **kein**
installiertes JDK benötigt. Die JARs aus `lib/` werden nach
`/opt/winlaufen-web/lib/` installiert, die mitgelieferte Runtime nach
`/opt/winlaufen-web/runtime/`; die systemd-Units starten Java aus genau diesem
Pfad.

Veröffentlicht wird derzeit nur **amd64**. Auf einem Raspberry Pi ist deshalb
bis auf Weiteres der Source-Weg (Abschnitt 2.1) zu verwenden.

Dieser Weg ist real abgenommen: Das Paket von `v0.4.0` wurde auf einer
bereinigten Ubuntu 24.04.4 LTS ohne System-Java, ohne Maven und ohne
Source-Checkout installiert, und der Installer verwendete die gebündelte
Runtime. Das Protokoll steht in [SMOKE_TESTS.md](SMOKE_TESTS.md#protokoll-installation-aus-dem-releasepaket-v040).
Prüfen Sie vor dem Entpacken die Prüfsumme gegen das ebenfalls veröffentlichte
`SHA256SUMS`:

```sh
sha256sum -c --ignore-missing SHA256SUMS
```

Läuft auf dem Zielrechner bereits WinLaufen mit aktiver
Sprecher-PC-Verbindung, diese vor Installation und Upgrade eines Profils mit
Bridge trennen (**Abwicklung → Sprecher-PC… → Trennen**) und danach wieder
verbinden. Für einen Presentation Node entfällt das.

### 1.2 Windows 11

`winlaufen-web-<version>-windows-x64.zip` herunterladen und entpacken. Das ZIP
ist eine fertige Binary Distribution mit gebündelter Java-Runtime. Es ist
**kein** `.exe`- oder `.msi`-Installer und wird nicht über `winget`
bereitgestellt; installiert wird mit dem enthaltenen PowerShell-Skript. Ein
nativer Windows-Installer ist geplant, existiert aber noch nicht.

PowerShell **als Administrator** öffnen, in das entpackte Verzeichnis wechseln:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\installer\windows\Install-WinLaufenWeb.ps1 -Profile AllInOne
```

Ohne `-Profile` fragt der Installer interaktiv; der zweite gültige Wert ist
`BridgeOnly`. Presentation Node wird unter Windows nicht unterstützt.

Auch hier sind weder Git noch ein JDK erforderlich: Die Runtime aus `runtime\`
wird nach `C:\Program Files\WinLaufen Web\runtime\` installiert und von den
geplanten Aufgaben verwendet.

Zwei Windows-Besonderheiten gelten für beide Installationswege und sind in
Abschnitt 2.2 ausführlich beschrieben: die Freigabe der **Skriptausführung**
(sonst bricht der Installer mit `PSSecurityException` ab) und das **Trennen der
WinLaufen-Sprecher-PC-Verbindung** vor Installation und Upgrade eines Profils
mit Bridge.

Auch dieser Weg ist real abgenommen: Das ZIP von `v0.4.0` wurde auf einem
Windows-11-PC installiert, auf dem das originale WinLaufen weiterlief und
System-Java aus anderen Gründen installiert blieb. Die Sprecher-Web-Dienste
verwendeten dennoch die mitgelieferte Runtime aus dem Paket. Das Protokoll steht
in [SMOKE_TESTS.md](SMOKE_TESTS.md#protokoll-installation-aus-dem-releasepaket-v040).
Die Prüfsumme lässt sich vor dem Entpacken vergleichen:

```powershell
Get-FileHash .\winlaufen-web-<version>-windows-x64.zip -Algorithm SHA256
```

> **Bekannte Anzeigeabweichung in `v0.4.0`.** Der Windows-Installer aus dem
> veröffentlichten `v0.4.0`-ZIP stellt deutsche Umlaute in Windows PowerShell 5.1
> falsch dar. Betroffen ist ausschließlich die Konsolenausgabe; Installation,
> Dienste und Konfiguration sind korrekt. Ursache und Behebung stehen in
> [Issue #5](https://github.com/richtertoralf/winlaufen-web/issues/5); der Fix ist
> in `main` und erscheint erstmals im nächsten Release.

## 2. Installation aus dem Quellcode

Für Entwicklung, Tests, Feature-Branches und noch nicht veröffentlichte Stände.
Dieser Weg installiert den **ausgecheckten Git-Stand** und damit nicht
notwendigerweise eine veröffentlichte Version.

Voraussetzungen:

- Git
- JDK 25

System-Maven ist keine Voraussetzung. Der Maven Wrapper verwendet die im
Repository festgelegte Maven-Version.

### 2.1 Linux

```sh
sudo apt install git openjdk-25-jdk
git clone https://github.com/richtertoralf/winlaufen-web.git
cd winlaufen-web
./mvnw clean package
sudo ./installer/linux/install.sh --profile all-in-one
```

Nicht-interaktiv stehen dieselben drei Profilwerte zur Verfügung:

```sh
sudo ./installer/linux/install.sh --profile all-in-one
sudo ./installer/linux/install.sh --profile bridge-only
sudo ./installer/linux/install.sh --profile presentation-node
```

Ein Source-Checkout enthält keine Java-Runtime; die Dienste verwenden dann das
System-Java, das mindestens Version 25 sein muss. Wer aus dem Quellcode eine
Distribution mit gebündelter Runtime erzeugen und auf einen Zielrechner
kopieren will:

```sh
# auf dem Entwicklerrechner
./installer/common/build-dist.sh --with-runtime

# dist/ auf den Zielrechner kopieren, dort:
sudo ./installer/linux/install.sh --profile all-in-one
```

Die Runtime ist immer plattformspezifisch: Ein Linux-Build erzeugt eine
Linux-Runtime, ein Windows-Build eine Windows-Runtime. Ein Cross-Build wird
bewusst nicht versucht.

### 2.2 Windows 11

```powershell
winget install --id Git.Git --exact --source winget
winget install --id Microsoft.OpenJDK.25 --exact --source winget
# PowerShell neu öffnen, dann:
git clone https://github.com/richtertoralf/winlaufen-web.git
Set-Location winlaufen-web
.\mvnw.cmd clean package
```

Anschließend PowerShell **als Administrator** starten:

```powershell
.\installer\windows\Install-WinLaufenWeb.ps1 -Profile AllInOne
.\installer\windows\Install-WinLaufenWeb.ps1 -Profile BridgeOnly
```

`winget` dient hier ausschließlich der Installation von Git und dem JDK.
Sprecher-Web selbst wird **nicht** über `winget` verteilt.

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

Die Installation muss in einer PowerShell mit Administratorrechten erfolgen.
Ohne diese Rechte bricht der Installer vor Änderungen mit einem entsprechenden
Hinweis ab.

**Vor Installation und Upgrade eines Profils mit Bridge** (All-in-One, Bridge
only) muss in WinLaufen die Sprecher-PC-Verbindung getrennt werden:
**Abwicklung → Sprecher-PC… → Trennen**. WinLaufen selbst muss nicht beendet
werden. Nach der Installation wieder **Verbinden**. Für einen Presentation Node
entfällt dieser Schritt. Ob ein Upgrade auch bei aktiver Verbindung zuverlässig
funktioniert, ist noch nicht geprüft; bis dahin gilt dieser Ablauf verbindlich.

### 2.3 Java-Runtime im Detail

Der Build aus dem Quellcode benötigt **JDK 25** (`maven.compiler.release=25` im
Root-POM). Für die Installation gilt unabhängig vom Weg diese Reihenfolge:

1. Liegt in der Quelle eine mitgelieferte Runtime (`runtime/` neben `lib/`,
   also im Releasepaket oder in einer selbst gebauten Distribution), wird diese
   installiert und verwendet. Der Rechner braucht dann kein eigenes Java.
2. Sonst wird das System-Java geprüft. Ist es älter als Java 25, bricht der
   Installer mit einer klaren Meldung ab und nennt beide Auswege.

Eine reduzierte Runtime entsteht beim Bauen der Distribution per `jlink`:

```sh
./installer/common/build-dist.sh --with-runtime
```

```powershell
.\installer\common\build-dist.ps1 -WithRuntime
```

Die Releasepakete werden mit genau diesen Skripten und dieser Option gebaut;
deshalb enthalten sie immer eine Runtime.

## 3. Upgrade

Es gibt keinen separaten Upgrade-Pfad: **derselbe Installer** führt auch das
Upgrade durch. Der Weg bleibt dabei derselbe wie bei der Installation — eine
aus einem Releasepaket installierte Anlage wird mit dem nächsten Releasepaket
aktualisiert, eine Entwicklerinstallation über den Git-Stand.

Das Profil muss dem bereits installierten entsprechen; ein anderer Wert ist ein
bewusster Profilwechsel. Läuft auf diesem Rechner WinLaufen mit aktiver
Sprecher-PC-Verbindung, diese vorher **trennen** und danach wieder
**verbinden**.

### 3.1 Upgrade einer Installation aus dem Releasepaket

Linux:

```sh
tar -xzf winlaufen-web-<neue-version>-linux-amd64.tar.gz
cd winlaufen-web-<neue-version>-linux-amd64
sudo ./installer/linux/install.sh --profile all-in-one
```

Windows, in einer PowerShell mit Administratorrechten im entpackten neuen ZIP:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\installer\windows\Install-WinLaufenWeb.ps1 -Profile AllInOne
```

Eine Git-Arbeitskopie wird dafür nicht benötigt. Das alte entpackte Paket kann
nach erfolgreichem Upgrade gelöscht werden; die installierten Dateien liegen
unter `/opt/winlaufen-web` bzw. `C:\Program Files\WinLaufen Web`.

### 3.2 Upgrade einer Entwicklerinstallation

```sh
cd ~/winlaufen-web
git status                       # keine ungesicherten eigenen Änderungen?
git pull --ff-only
./mvnw clean package
sudo ./installer/linux/install.sh --profile all-in-one
```

Unter Windows entsprechend mit `git pull --ff-only`, `.\mvnw.cmd clean package`
und dem Installer aus dem Checkout.

Dieser Weg aktualisiert auf den ausgecheckten Git-Stand und nicht
notwendigerweise auf einen veröffentlichten Release.

### 3.3 Was das Upgrade erhält und was es ersetzt

Für beide Installationswege identisch. Die Pfade in der Tabelle sind die von
Linux; unter Windows gilt dasselbe Verhalten für `C:\Program Files\WinLaufen Web\`
und `C:\ProgramData\WinLaufen Web\` sowie für die geplanten Aufgaben anstelle der
systemd-Units.

| Gegenstand | Verhalten |
|---|---|
| `bridge.properties` | bleibt unverändert; Defaults entstehen nur bei einer echten Erstinstallation |
| `live-server.env` | bleibt unverändert |
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
Windows x64. Raspberry Pi OS auf ARM wird über den Source-Weg installiert.

## 5. Verzeichnisse

### Linux

```text
/opt/winlaufen-web/lib/          Programmartefakte (JARs)
/opt/winlaufen-web/runtime/      gebündelte Java-Runtime, falls die Quelle eine mitbringt
/etc/winlaufen-web/              Konfiguration und persistente Veranstalterdaten
    bridge.properties            Veranstalter-Konfiguration der Bridge
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
```

`startlist.properties` entsteht erst beim ersten erfolgreichen
Startlistenimport in Bridge Control. Die Bridge schreibt sie über eine
temporäre Datei im selben Verzeichnis und ersetzt sie dann in einem Zug, damit
nie ein halb geschriebener Stand gelesen wird. Der Installer legt diese Datei
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

Vor dem Start prüft der Installer ausschließlich die Listenerports des gewählten
Profils. Ein Konflikt nennt Port, Zweck und soweit ermittelbar Prozess/PID sowie
den systemd-Dienst. Es wird kein Ersatzport gewählt. TCP 4444 wird nicht
geprüft, weil die Bridge sich dorthin ausgehend verbindet.

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

## 9. Deinstallation

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
  ohne zusätzlichen Abgleich zusammenarbeiten. Siehe README.md, Abschnitt
  „Known prototype security limitation".

## 11. Installationsstatus

Der aktuelle Abnahmestand steht in der [README](../README.md#projektstatus);
die manuellen Abnahmetests und ihre protokollierten Nachweise in
[SMOKE_TESTS.md](SMOKE_TESTS.md).
