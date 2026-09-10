# Sprecher-Web

[![Version](https://img.shields.io/badge/version-0.4.0-blue)](https://github.com/richtertoralf/winlaufen-web/releases)
[![Lizenz](https://img.shields.io/badge/Lizenz-AGPL--3.0-blue)](LICENSE)

Sprecher-Web ist keine Web-Version der Wettkampfsoftware WinLaufen.
Das Projekt "Sprecher-Web" nutzt die von WinLaufen bereitgestellte **Sprecher-PC-Schnittstelle**
und stellt die dort gelieferten Live-Ergebnisdaten **zusätzlich zum "Sprecher-PC" 
webbasiert** bereit. **WinLaufen (http://www.winlaufen.de/)** ist eine Windows-Anwendung und läuft unabhängig von dieser Anwendung.

```text
WinLaufen
   |  Sprecher-PC-Schnittstelle / TCP 4444   (read-only, ausgehend)
   v
Bridge
   |  WebSocket / Ingest
   v
Live Server
   |  HTTP / WebSocket
   v
Browser
```

<img src="WinLaufenSprecherWEB.png" alt="Screenshot WinLaufen, Sprecher-PC, Srecher-Web, Bridge Control" width="100%">

**Screenshot (01.09.2026): WinLaufen, Sprecher-PC, Sprecher-Web, Bridge Control**

Die beiden sichtbaren Oberflächen heißen:

- **Sprecher-Web – Bridge Control** — die Veranstalter-Oberfläche
- **Sprecher-Web – Live-Ergebnisse** — die Ansicht für alle Zuschauer

> **Status: Version 0.4.0, Prototype Baseline.** Für ausgewählte Vereine in
> **kontrollierten Netzen** gedacht, nicht für offenen Internetbetrieb. Vor dem
> Einsatz den Abschnitt
> [Known prototype security limitation](#known-prototype-security-limitation)
> lesen.

> 📖 **Sie wollen Sprecher-Web einsetzen?**
> Die vollständige Anleitung von der Installation bis zum Wettkampftag steht im
> **[Bedienerhandbuch](docs/BEDIENERHANDBUCH.md)**.

## Was Sprecher-Web kann

**Live-Ergebnisse aus WinLaufen**

* Verbindung zur **WinLaufen-Sprecher-PC-Schnittstelle** auf TCP 4444, strikt
  read-only und nur ausgehend.
* Laufende **Wettkampfuhr**, Ergebnistabellen, LIVE-Ansicht und Current Finish
  in jedem Browser — Notebook, Tablet, Smartphone, ohne App und ohne Anmeldung.
* Verifiziert für **Lauf und Biathlon**. Tabellen, Uhr, Current Finish,
  Schießen und WinLaufen-Nachrichten werden ohne fachliche Korrektur
  transportiert.
* **Bridge Control** als Veranstalter-Oberfläche: WinLaufen-Adresse, Live
  Server, öffentliche Spalten (Verein, Verband, Nation, Schießen, Nachrichten)
  und Startlistenimport.

**Startlisten**

* **Startlistenimport in Bridge Control** — Datei auswählen, importieren,
  fertig. Unterstützt werden **CSV**, **TXT** und **XLSX** aus WinLaufen; das
  alte binäre `.xls` wird abgelehnt.
* Jeder erfolgreiche Import **ersetzt die bisherige Startliste vollständig**.
  Es wird nichts ergänzt und nichts zusammengeführt, denn dieselbe Startnummer
  kann im Prolog und im Lauf zu verschiedenen Personen gehören. Ein
  fehlgeschlagener Import ändert nichts.
* Die Startliste liegt **persistent in der Bridge** und überlebt einen
  Neustart von Bridge, Live Server und Rechner — ohne erneuten Import.
* Sie wird **automatisch an alle Live Server übertragen**, nach jedem Import
  und nach jedem Verbindungsaufbau. Sie ist nicht Teil der sekündlichen
  Ergebnis-Snapshots.
* Im Web Viewer erscheint sie **klassenweise**: Vor-/Zurück-Navigation,
  direkte Klassenauswahl und die Reihenfolge genau wie im WinLaufen-Export.

**Generische Read API**

* Der Live Server stellt seinen Zustand **maschinenlesbar** über HTTP bereit:
  `GET /api/v1/state` für den laufenden Zustand und `GET /api/v1/startlist`
  für den vollständigen Startlistenbestand.
* Bewusst **generisch** und auf keinen einzelnen Consumer zugeschnitten —
  gedacht für Overlay- und Timing-Systeme wie `finish-stream-overlay` und die
  GFX Engine, für Monitoring und für eigene Integrationen.
* **Jede Antwort** enthält die aktuelle WinLaufen-Wettkampfzeit **und** den
  Verbindungsstatus der Kette WinLaufen → Bridge → Live Server. Ein Consumer
  muss nie aus vorhandenen Daten raten, ob die Quelle noch hängt.
* Bridge und Live Server arbeiten dabei als **Messstellen**: Zu jedem
  WinLaufen-Uhrtelegramm liefern sie Empfangszeitpunkt, gemessene Differenz zur
  Wettkampfzeit und den Status ihrer Zeitreferenz — zwei unabhängige Messungen
  nebeneinander. Sie korrigieren nichts, wählen keinen Offset und kalibrieren
  nicht; das entscheidet der Consumer. Die Differenz ist eine **Messdifferenz**
  gegen die jeweilige Systemuhr, kein ermittelter Uhrenfehler.
* Für die Differenz wird die **Wettkampf-Zeitzone** gebraucht. Sie steht in
  `bridge.properties` unter `competition.timezone`; ohne Eintrag gilt die Zone
  des Rechners, und die API kennzeichnet sie dann als bloßen Rückfall. Auf einem
  Server mit Systemzone UTC sollte sie gesetzt werden.
* Read-only und ohne Rückfrage bei WinLaufen: Jeder Request wird aus dem
  bereits veröffentlichten Zustand beantwortet.
* Details: [docs/API.md](docs/API.md).

**Betrieb**

* Ein Ausfall von Netzwerk, Live Server oder Bridge führt nie zu still
  veralteten Daten: Der Browser erkennt ihn, kennzeichnet die Anzeige und
  verbindet ohne Reload automatisch neu.
* Beliebig viele zusätzliche Live Server parallel — im LAN oder als temporärer
  Server im Internet.

WinSpringen, Datenbank und Broker bleiben ausdrücklich außerhalb dieser
Version. Startlisten kommen als Dateiexport aus WinLaufen; ein
Startlisten-Wireprotokoll auf TCP 4444 gibt es nicht und wird nicht erfunden.

## Installation und Upgrade

> **Technische Referenz:** [docs/INSTALLATION.md](docs/INSTALLATION.md) —
> Profile, Pfade, Dienste, Ports, Firewall, Deinstallation.
> **Für Veranstalter Schritt für Schritt:**
> [docs/BEDIENERHANDBUCH.md](docs/BEDIENERHANDBUCH.md).

Es gibt **zwei verschiedene Installationswege**. Sie führen zu unterschiedlichen
Ständen und haben unterschiedliche Voraussetzungen — bitte einen davon wählen
und nicht mischen.

**Ein Releasepaket** ist eine bereits fertig gebaute Binary Distribution einer
veröffentlichten Version. Es enthält die Anwendungs-JARs (`lib/`), eine
passende Java-Runtime (`runtime/`) und die Installationsskripte (`installer/`).
Releasepakete liegen als Dateianhang unter
[Releases](https://github.com/richtertoralf/winlaufen-web/releases).

**Ein Releasepaket ist nicht das Git-Repository.** `git clone` lädt
ausschließlich den Quelltext herunter und **keine** Release-Artefakte; ein
Checkout enthält weder gebaute JARs noch eine Java-Runtime.

| | Installation aus Releasepaket | Installation aus Quellcode |
|---|---|---|
| Zielgruppe | Anwender und Administratoren | Entwickler |
| Git nötig | nein | ja |
| Maven-Build nötig | nein | ja |
| JDK 25 nötig | nein | ja |
| Java-Runtime enthalten | ja, im Paket | nein — System-Java ≥ 25 erforderlich |
| Versionsstand | genau die veröffentlichte Version | der ausgecheckte Git-Stand |
| Für Produktivrechner empfohlen | ja | eher nicht |
| Feature-Branches testbar | nein | ja |

**Empfehlung:** Wer Sprecher-Web nur einsetzen will, nimmt das Releasepaket.
Der Quellcode-Weg ist für Entwicklung, Tests und noch nicht veröffentlichte
Stände gedacht.

### Installation aus dem Releasepaket — empfohlen

Aktuelle Version: **0.4.0**. Die Dateinamen unten enthalten die Version; in
den Befehlen steht dafür `<version>`.

**Linux**

Auf der [Releases-Seite](https://github.com/richtertoralf/winlaufen-web/releases)
`winlaufen-web-<version>-linux-amd64.tar.gz` herunterladen, dann:

```sh
tar -xzf winlaufen-web-<version>-linux-amd64.tar.gz
cd winlaufen-web-<version>-linux-amd64
sudo ./installer/linux/install.sh --profile all-in-one
```

Weder `git clone` noch `./mvnw clean package` sind dafür nötig: Die JARs sind
gebaut, und die mitgelieferte Java-Runtime wird nach
`/opt/winlaufen-web/runtime` installiert und von den Diensten verwendet. Auf
dem Zielrechner muss deshalb kein Java installiert sein.

**Windows 11**

`winlaufen-web-<version>-windows-x64.zip` herunterladen und entpacken. Das ZIP
ist eine fertige Binary Distribution mit gebündelter Java-Runtime — es ist
**kein** `.exe`- oder `.msi`-Installer; installiert wird mit dem enthaltenen
PowerShell-Skript. Dafür eine **PowerShell mit Administratorrechten** öffnen,
in das entpackte Verzeichnis wechseln und starten:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\installer\windows\Install-WinLaufenWeb.ps1 -Profile AllInOne
```

`Set-ExecutionPolicy` erlaubt das Ausführen von PowerShell-Skripten, die sonst
blockiert würden. Die Änderung gilt nur für dieses eine PowerShell-Fenster;
nach dem Schließen ist die Sperre wieder aktiv.

Ohne `--profile` bzw. `-Profile` fragt der Installer das Profil interaktiv ab.
Die anderen Werte sind `bridge-only` / `BridgeOnly` und unter Linux zusätzlich
`presentation-node`.

### Installation aus dem Quellcode — für Entwickler

Dieser Weg baut Bridge und Live Server lokal aus dem aktuellen
Repository-Stand. Er ist für Entwicklung, Tests, Feature-Branches und noch
nicht veröffentlichte Versionen gedacht — und installiert dementsprechend
nicht notwendigerweise einen veröffentlichten Release. Voraussetzungen sind
Git und JDK 25; Maven liefert der Maven Wrapper mit.

**Linux**

```sh
sudo apt install git openjdk-25-jdk
git clone https://github.com/richtertoralf/winlaufen-web.git
cd winlaufen-web
./mvnw clean package
sudo ./installer/linux/install.sh --profile all-in-one
```

**Windows 11**

```powershell
winget install --id Git.Git --exact --source winget
winget install --id Microsoft.OpenJDK.25 --exact --source winget
# PowerShell neu öffnen, dann:
git clone https://github.com/richtertoralf/winlaufen-web.git
Set-Location winlaufen-web
.\mvnw.cmd clean package
```

Den **Installer** anschließend in einer **PowerShell mit Administratorrechten**
starten. Windows blockiert Skripte standardmäßig; deswegen das Folgende mit ausführen:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\installer\windows\Install-WinLaufenWeb.ps1
```

***Zweck: Der Befehl `Set-ExecutionPolicy...` erlaubt das Ausführen von PowerShell-Skripten, die normalerweise durch Sicherheitsbeschränkungen blockiert würden.Temporär: Die Änderung gilt nur für das aktuelle PowerShell-Fenster. Sobald das Fenster geschlossen wird, ist die Sperre wieder aktiv.***

Ein Source-Checkout bringt keine Java-Runtime mit; die Dienste verwenden dann
das System-Java, das mindestens Version 25 sein muss. Wer aus dem Quellcode ein
Paket mit gebündelter Runtime erzeugen will, nutzt
`./installer/common/build-dist.sh --with-runtime` bzw.
`.\installer\common\build-dist.ps1 -WithRuntime`.

### Upgrade

Es gibt keinen separaten Upgrade-Pfad: Derselbe Installer führt auch das
Upgrade durch. Entscheidend ist, dass der Weg derselbe bleibt wie bei der
Installation.

**Upgrade einer Installation aus dem Releasepaket**

Neues Releasepaket herunterladen, entpacken und den Installer mit demselben
Profil erneut ausführen:

```sh
tar -xzf winlaufen-web-<neue-version>-linux-amd64.tar.gz
cd winlaufen-web-<neue-version>-linux-amd64
sudo ./installer/linux/install.sh --profile all-in-one
```

Eine Git-Arbeitskopie wird dafür nicht benötigt.

**Upgrade einer Entwicklerinstallation**

```sh
cd ~/winlaufen-web
git status
git pull --ff-only
./mvnw clean package
sudo ./installer/linux/install.sh --profile all-in-one
```

Dieser Weg aktualisiert auf den ausgecheckten Git-Stand — nicht
notwendigerweise auf eine veröffentlichte Version.

**In beiden Fällen gilt:**

* Vorhandene Konfiguration wird **nie überschrieben** — die gepflegte
  WinLaufen-Adresse und die Liste der Live Server bleiben erhalten.
* Die **importierte Startliste bleibt erhalten**; sie liegt in
  `/etc/winlaufen-web/startlist.properties` und wird vom Installer nicht
  angefasst.
* Programmdateien und systemd-Units werden ersetzt, nicht dupliziert.
* Die Dienste des gewählten Profils werden aktiviert und neu gestartet.

Prüfung danach:

```sh
systemctl status winlaufen-bridge winlaufen-live-server --no-pager
sudo ss -ltnp | grep -E ':(44440|44441|44442)\b'
```

Bei `bridge-only` gehört nur `winlaufen-bridge` und Port 44442 dazu, bei
`presentation-node` nur `winlaufen-live-server` und die Ports 44440 und 44441.

> **Vor Installation und Upgrade** eines Profils mit Bridge (All-in-One,
> Bridge only) in WinLaufen **Abwicklung → Sprecher-PC… → Trennen** wählen und
> danach wieder **Verbinden**. WinLaufen selbst muss nicht beendet werden.
> Details im [Bedienerhandbuch](docs/BEDIENERHANDBUCH.md#4-windows-all-in-one-installieren).

Danach Bridge Control unter `http://localhost:44442/` öffnen und die
Live-Ergebnisse unter `http://localhost:44440/`.

## Installationsprofile

Bei der Installation wird genau eine Sache ausgewählt: die Rolle des Rechners.
Adressen, Ziele und TLS gehören ausschließlich in die spätere Konfiguration
über Bridge Control.

| Profil | Installiert | Eigene Ports | Linux | Windows 11 |
|---|---|---|---|---|
| All-in-One | Bridge + Live Server | 44440, 44441, 44442 | `--profile all-in-one` | `-Profile AllInOne` |
| Bridge only | nur Bridge | 44442 | `--profile bridge-only` | `-Profile BridgeOnly` |
| Presentation Node | nur Live Server | 44440, 44441 | `--profile presentation-node` | nicht unterstützt |

* **All-in-One** — Bridge und Live Server auf demselben Rechner, am
  einfachsten direkt auf dem WinLaufen-PC. Der Normalfall.
* **Bridge only** — Bridge in der Nähe von WinLaufen, der Live Server läuft
  auf einem anderen Rechner.
* **Presentation Node** — nur Live Server und Web Viewer, ohne
  WinLaufen-Anbindung; typisch ein gemieteter Server im Internet.

Unterstützt sind Debian, Ubuntu 24.04/26.04 und Raspberry Pi OS für alle drei
Profile sowie Windows 11 für All-in-One und Bridge only.

## Empfohlene Betriebsweise

| Variante | Aufbau | Wann |
|---|---|---|
| **A — All-in-One** | alles auf einem Rechner, am einfachsten direkt auf dem WinLaufen-PC | der Normalfall |
| **B — anderer Rechner im LAN** | All-in-One oder Bridge only auf einem zweiten Rechner, Adresse des WinLaufen-PCs in Bridge Control | wenn der WinLaufen-PC frei bleiben soll |
| **C — zusätzlicher Server im Internet** | zusätzlich ein Presentation Node auf einem gemieteten Ubuntu-Server | wenn Zuschauer außerhalb des Veranstaltungsnetzes mitlesen sollen |

Variante C benötigt **keine Domain und kein TLS**: Für den bewusst einfachen,
temporären Selfhost-Betrieb genügt die öffentliche IPv4-Adresse, und in Bridge
Control wird im Normalfall nur diese eine Adresse eingetragen. Die verbindlichen
Grenzen dieses Betriebs stehen unten unter
[Known prototype security limitation](#known-prototype-security-limitation).

## Architektur in Kürze

| Baustein | Aufgabe |
|---|---|
| **`winlaufen-web-bridge`** | liest WinLaufen strikt read-only, hält den kanonischen Live-State, verteilt ihn an 0..n Ziele, stellt Bridge Control bereit |
| **`winlaufen-web-live-server`** | nimmt den Bridge-Ingest authentifiziert an, hält den veröffentlichten State, liefert die Live-Ergebnisse an Browser |
| `winlaufen-web-contract` | kleiner versionierter Vertrag zwischen beiden: Ergebnis-Snapshot, ACK und Startliste |

Beide Runtimes sind getrennte Prozesse. Der Live Server enthält keinen
WinLaufen-Protokollcode. Auch die lokale Ansicht im All-in-One-Betrieb läuft
über eine echte ausgehende WebSocket-Verbindung der Bridge.

### Netzwerkvertrag

| Port | Richtung | Funktion |
|---|---|---|
| TCP 4444 | Bridge → WinLaufen-PC, **ausgehend** | WinLaufen Sprecher-PC-Quelle |
| TCP 44440 | eingehend | Live-Ergebnisse / HTTP Web Viewer / Read API |
| TCP 44441 | eingehend | Live WebSocket und Bridge-Ingest auf einem Listener |
| TCP 44442 | eingehend | Bridge Control |

**TCP 4444 ist keine eingehende Freigabe dieses Projekts.** Diesen Port stellt
WinLaufen selbst bereit, sobald dort die Sprecher-PC-Verbindung aktiviert wurde;
die Bridge verbindet sich nur ausgehend dorthin. 4444 gehört deshalb nicht in
die eingehenden Firewallregeln.

44440 und 44441 müssen für die vorgesehenen Zuschauergeräte erreichbar sein,
44442 nur für die vorgesehenen Administrationsgeräte.

## Die angezeigte Wettkampfzeit

Die im Browser angezeigte Zeit ist die **Wettkampfzeit aus WinLaufen** — nicht
die Uhrzeit des WinLaufen-PCs, der Bridge, des Live Servers oder des Browsers.
Sprecher-Web reicht diesen Wert unverändert weiter und erzeugt keine eigene
laufende Uhr. Bleiben WinLaufen-Zeittelegramme aus, bleibt der zuletzt
gelieferte Wert stehen; er wird nie künstlich weitergezählt.

Damit ist die laufende Wettkampfzeit für den Sprecher ein sichtbares
Lebenszeichen der gesamten Kette vom WinLaufen-PC bis zur Anzeige. Erklärung
für Bediener: [Bedienerhandbuch, Kapitel 9](docs/BEDIENERHANDBUCH.md#9-die-angezeigte-wettkampfzeit).

## Dokumentation

**Für Veranstalter und Bediener**

| Dokument | Inhalt |
|---|---|
| [docs/BEDIENERHANDBUCH.md](docs/BEDIENERHANDBUCH.md) | **Start hier.** Installation, Einrichtung, Betrieb am Wettkampftag, Statusanzeigen, Störungshilfe |
| [docs/QUICKSTART_CLOUD.md](docs/QUICKSTART_CLOUD.md) | temporärer Live-Server auf einer Cloud-VM, Schritt für Schritt |

**Technische Dokumentation**

| Dokument | Inhalt |
|---|---|
| [docs/INSTALLATION.md](docs/INSTALLATION.md) | Installationsreferenz: Profile, Plattformen, Pfade, Dienste, Firewall, Deinstallation |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | Build, Entwicklungsbetrieb, Konfigurationsorte, Transportregel |
| [docs/SMOKE_TESTS.md](docs/SMOKE_TESTS.md) | manuelle Abnahmetests und protokollierte reale Nachweise |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | technischer IST-Stand der modularen Architektur |
| [docs/MODULAR_ARCHITECTURE.md](docs/MODULAR_ARCHITECTURE.md) | vollständige verbindliche Architekturentscheidungen |
| [docs/PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md) | Produktspezifikation |
| [docs/WINLAUFEN_PROTOCOL.md](docs/WINLAUFEN_PROTOCOL.md) | WinLaufen-Protokoll und reale Evidenz |
| [docs/API.md](docs/API.md) | Read API des Live Servers für externe Consumer |
| [docs/RELEASE.md](docs/RELEASE.md) | tag-basierter Release-Ablauf für Maintainer |

## Projektstatus

**Version `0.4.0`.** Dieses Release schließt den Startlistenweg ab: Import in
Bridge Control, persistenter Bestand in der Bridge, eigene Übertragung zum Live
Server und klassenweise Anzeige im Web Viewer — zusätzlich zu Uhr und
Ergebnissen wie bisher.

Es ist zugleich die erste Version mit **fertigen Releasepaketen** für Linux
amd64 und Windows x64, die ohne Git, Maven und JDK installiert werden können.

Die Prototyp-Grenzen aus
[Known prototype security limitation](#known-prototype-security-limitation)
gelten unverändert: Bridge Control hat keine Anmeldung, und der Bridge-Ingest
verwendet weiterhin ein bekanntes Default-Secret. Das Release ist deshalb keine
Freigabe für offenen Internetbetrieb.

### Real bestätigt

**Installation aus den veröffentlichten Releasepaketen von `v0.4.0`**

- **Linux amd64:** Fresh Installation auf Ubuntu 24.04.4 LTS aus
  `winlaufen-web-0.4.0-linux-amd64.tar.gz`. Auf dem Rechner waren vorher eine
  frühere Sprecher-Web-Installation mit `uninstall.sh --purge`, OpenJDK 25,
  Maven, der Source-Checkout und `~/.m2` entfernt; die Ports 44440–44442 waren
  frei. Die SHA256-Summe wurde gegen das veröffentlichte `SHA256SUMS` geprüft.
  Installiert wurde ausschließlich mit dem Installer aus dem Paket — **ohne
  `git clone`, ohne Maven-Build und ohne System-Java**. Der Installer verwendete
  die gebündelte Runtime `/opt/winlaufen-web/runtime/bin/java`. Bridge Control,
  Web View, Live-WebSocket und das lokale Output Target waren anschließend in
  Ordnung; mit der realen WinLaufen-Quelle war die Bridge verbunden, und im
  Viewer erschienen Live-Daten und die Startliste mit 46 Klassen.
- **Windows x64:** Clean Installation auf einem Windows-11-PC aus
  `winlaufen-web-0.4.0-windows-x64.zip`, ebenfalls mit geprüfter SHA256-Summe,
  in einer PowerShell **als Administrator**. Entfernt waren vorher nur
  Sprecher-Web selbst — Programm- und Konfigurationsverzeichnis, beide geplanten
  Aufgaben, die eigenen Firewallregeln und die Listener 44440–44442. Es war
  **kein nacktes Windows**: Das originale WinLaufen lief auf demselben PC
  unverändert weiter, und **System-Java blieb bewusst installiert**, weil andere
  Anwendungen es brauchen. Genau deshalb ist der Nachweis aussagekräftig — die
  beiden Sprecher-Web-Dienste liefen nicht mit dem System-Java, sondern mit
  `C:\Program Files\WinLaufen Web\runtime\bin\javaw.exe` aus dem Paket. Bridge
  Control und Web Viewer arbeiteten, die Bridge war mit dem lokalen WinLaufen
  auf `127.0.0.1:4444` verbunden, die Startliste mit 46 Klassen wurde angezeigt.

**Weiterhin bestätigt**

- Windows-11-All-in-One-Installation und -Upgrade aus dem Source Checkout
- reale WinLaufen-Kopplung; Verbinden und Trennen der Sprecher-PC-Schnittstelle
  wird korrekt erkannt
- Bridge und Live Server laufen dauerhaft als Dienst bzw. geplante Aufgabe
- Live-Ergebnisse und Bridge Control lokal und aus dem LAN
- Presentation Node auf einem Cloud-Server mit öffentlicher IPv4
- Live Server stop/start sowie kompletter Reboot des Presentation Node
- automatischer Browser-Reconnect ohne manuellen Reload
- Browser-Verbindung und WinLaufen-Quellenlage werden getrennt angezeigt
- Wettkampfzeit bleibt bei Ausfällen stehen und zeigt nach dem Reconnect exakt
  den neu gelieferten WinLaufen-Wert
- Bridge stop/start; der letzte Ergebnisstand bleibt auf dem Presentation Node
  erhalten, bis WinLaufen einen neuen Klassensnapshot liefert
- Startlistenimport in Bridge Control und Anzeige im Web Viewer, gleichzeitig
  mit laufender realer WinLaufen-Quelle: 1 999 Teilnehmer in 46 Klassen,
  Klassennavigation, neuer Import ohne Dienstneustart, Live-Server-Restart und
  Bridge-Restart jeweils ohne erneuten Import

Der Nachweis ist in [docs/SMOKE_TESTS.md](docs/SMOKE_TESTS.md) protokolliert.

### Geplant, noch nicht vorhanden

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
| Windows PowerShell 5.1 stellt Umlaute in den Installerausgaben von **`v0.4.0`** falsch dar. | Nur die Anzeige ist betroffen; Installation und Konfiguration sind korrekt. Ursache und Behebung: [Issue #5](https://github.com/richtertoralf/winlaufen-web/issues/5). Der Fix ist in `main`, **nicht** im veröffentlichten `v0.4.0`-ZIP, und erscheint erstmals im nächsten Release. |
| Ob Installation und Upgrade auch bei laufender und verbundener Sprecher-PC-Schnittstelle zuverlässig funktionieren, ist noch nicht geprüft. | Bis dahin gilt verbindlich: vorher **Trennen**, danach **Verbinden**. |

## Known prototype security limitation

**Diese Einschränkungen sind bekannt, bewusst akzeptiert und noch nicht behoben.**

### Bridge Control auf TCP 44442 hat keine Anmeldung

Diese Oberfläche besitzt bewusst keine Benutzer- oder Login-Authentifizierung.
Wer den Port erreicht, kann Bridge Control öffnen und die Konfiguration ändern —
WinLaufen-Quelle, Output Targets und die öffentliche Darstellung. Target-Secrets
gibt die Control-API nicht aus; das ersetzt jedoch keine Zugriffsbeschränkung.
44442 darf deshalb nur in einem vertrauenswürdigen LAN erreichbar sein: nicht im
Gäste-WLAN, nicht über unkontrollierte Portweiterleitungen, nicht direkt aus dem
Internet.

### Der Bridge-Ingest verwendet ein bekanntes Secret

Der Ingest des Live Servers ist authentifiziert, verwendet aber weiterhin ein
**bekanntes, im Quelltext und in dieser README stehendes Development-Secret**
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
[docs/QUICKSTART_CLOUD.md](docs/QUICKSTART_CLOUD.md) — und unterliegt diesen
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
- Keine Eignung für Anmeldungen, personenbezogene Daten oder alles, was über
  die ohnehin öffentlich angezeigten Wettkampfdaten hinausgeht.

**3. Dauerhafter abgesicherter WAN-Betrieb**

Für produktiven Dauerbetrieb über WAN oder eine Anbindung an Richter-Projects
sind **WSS mit gültigem Zertifikat sowie individuell provisionierte Secrets pro
Target** erforderlich. Das ist bewusst nicht Teil dieser Prototype Baseline und
bleibt ein offenes Production-Hardening-Thema.

Der Live Server weist beim Start ausdrücklich auf das aktive Default-Secret hin.

## Technische Namen

Sichtbar heißt das Produkt **Sprecher-Web**. Der Name lehnt sich bewusst an
den etablierten WinLaufen-„Sprecher-PC" an. Die öffentliche Oberfläche trägt den
Untertitel **Live-Ergebnisse aus WinLaufen** — das beschreibt die Herkunft der
angezeigten Daten. Die Browsertitel lauten **Live-Ergebnisse · Sprecher-Web**
und **Bridge Control · Sprecher-Web**.

Technische Bezeichner bleiben aus Kompatibilitätsgründen zunächst unverändert,
damit bestehende Befehle, Upgrade-Pfade und Deinstallationen weiter
funktionieren:

- Maven-Artefakte und JARs `winlaufen-web-*`
- Java-Packages `de.winlaufen.web.*`
- Installationspfade `/opt/winlaufen-web`, `/etc/winlaufen-web`,
  `C:\Program Files\WinLaufen Web`
- systemd-Units `winlaufen-bridge.service`, `winlaufen-live-server.service`
- geplante Aufgaben `WinLaufen Web Bridge`, `WinLaufen Web Live Server`
- Firewall-Regel-IDs `WinLaufenWeb-*`

## Lizenz

Sprecher-Web steht unter der [GNU Affero General Public License
v3.0](LICENSE) (AGPL-3.0). Kurz gefasst: Der Quellcode ist frei nutzbar,
veränderbar und weitergebbar. Wer eine veränderte Version über ein Netzwerk
zugänglich macht — auch als gehosteten Dienst, ohne den Code selbst
weiterzugeben — muss den vollständigen, veränderten Quellcode ebenfalls
unter der AGPL-3.0 verfügbar machen (§13 der Lizenz).

Für Sportvereine, die die Software unverändert oder mit eigenen Anpassungen
ausschließlich für ihre eigene Veranstaltung betreiben, entstehen daraus
keine Pflichten — die Offenlegungspflicht greift erst, wenn Dritte über ein
Netzwerk auf eine veränderte Version zugreifen können.
