# WinLaufen Sprecher Web

WinLaufen Sprecher Web ist keine Web-Version der Wettkampfsoftware WinLaufen.
Das Projekt nutzt die von WinLaufen bereitgestellte **Sprecher-PC-Schnittstelle**
und stellt die dort gelieferten Live-Ergebnisdaten **zusätzlich zum "Sprecher-PC" 
webbasiert** bereit. **WinLaufen (http://www.winlaufen.de/)** selbst ist eine Windows-Anwendung und läuft unabhängig
von dieser Anwendung; im Browser erscheinen nur die Ergebnisse, die WinLaufen über
diese Schnittstelle veröffentlicht.

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

Die beiden sichtbaren Oberflächen heißen:

- **WinLaufen Sprecher Web – Bridge Control** — die Veranstalter-Oberfläche
- **WinLaufen Sprecher Web – Live-Ergebnisse** — die Ansicht für alle Zuschauer

> **Status: Prototype Baseline, Entwicklungsversion**,
> kein Release, keine Freigabe. Für ausgewählte Vereine in **kontrollierten
> Netzen** gedacht, nicht für offenen Internetbetrieb. Vor dem Einsatz den
> Abschnitt [Known prototype security limitation](#known-prototype-security-limitation)
> lesen.

## Hauptanwendungsfall

Ein Windows-PC mit WinLaufen im Veranstaltungsnetz (LAN/WLAN) trägt alles; weitere Geräte
lesen nur mit. winlaufen-web ergänzt die Sprecher-PC im LAN um weitere Geräte
wie Smartphones oder "nicht-Windows Tablets".

```text
Windows-PC
├─ WinLaufen
├─ WinLaufen Sprecher Web Bridge
├─ Live Server
└─ Bridge Control

LAN
├─ Notebook
├─ Tablet
├─ Smartphone
└─ weitere Browser
       |
       v
http://<Windows-PC>:44440/
```

## Architektur in Kürze

| Baustein | Aufgabe |
|---|---|
| **`winlaufen-web-bridge`** | liest WinLaufen strikt read-only, hält den kanonischen Live-State, verteilt ihn an 0..n Ziele, stellt Bridge Control bereit |
| **`winlaufen-web-live-server`** | nimmt den Bridge-Ingest authentifiziert an, hält den veröffentlichten State, liefert die Live-Ergebnisse an Browser |
| `winlaufen-web-contract` | kleiner versionierter Snapshot-/ACK-Vertrag zwischen beiden |

Beide Runtimes sind getrennte Prozesse. Der Live Server enthält keinen
WinLaufen-Protokollcode. Auch die lokale Ansicht im All-in-One-Betrieb läuft
über eine echte ausgehende WebSocket-Verbindung der Bridge.

### Netzwerkvertrag

| Port | Richtung | Funktion |
|---|---|---|
| TCP 4444 | Bridge → WinLaufen-PC, **ausgehend** | WinLaufen Sprecher-PC-Quelle |
| TCP 44440 | eingehend | Live-Ergebnisse / HTTP Web Viewer |
| TCP 44441 | eingehend | Live WebSocket und Bridge-Ingest auf einem Listener |
| TCP 44442 | eingehend | Bridge Control |

**TCP 4444 ist keine eingehende Freigabe dieses Projekts.** winlaufen-web-bridge lauscht auf
4444, die Bridge verbindet sich dorthin ausgehend; WinLaufen Sprecher Web öffnet
dafür keinen eigenen Listener. 4444 gehört deshalb nicht in die eingehenden
Firewallregeln.

44440 und 44441 müssen für die vorgesehenen Zuschauergeräte erreichbar sein,
44442 für die vorgesehenen Administrationsgeräte.

## Schnellstart

Ausführliche Anleitung: [docs/INSTALLATION.md](docs/INSTALLATION.md).

### Installieren

Sobald Release-Pakete veröffentlicht sind: Archiv herunterladen, entpacken,
Installer starten, Profil wählen. Git, Maven und ein eigener Build sind dafür
nicht nötig; ein Paket mit gebündelter `jlink`-Runtime braucht auch kein
separat installiertes JDK.

Aus dem Source Checkout — Voraussetzungen sind Git und JDK 25, Maven liefert der
Wrapper mit:

#### Linux Ubuntu
```sh
git clone https://github.com/richtertoralf/winlaufen-web.git
cd winlaufen-web
./mvnw clean package
sudo ./installer/linux/install.sh
```

#### Windows 11
```powershell
git clone https://github.com/richtertoralf/winlaufen-web.git
Set-Location winlaufen-web
.\mvnw.cmd clean package
# Danach PowerShell als Administrator:
.\installer\windows\Install-WinLaufenWeb.ps1
```

Unter Linux laufen die Dienste über `systemd`, unter Windows als geplante
Aufgaben mit dem Trigger „Beim Systemstart". Beide starten ohne offenes
Konsolenfenster nach einem Neustart automatisch.

### WinLaufen verbinden

Die Bridge kann die Sprecher-PC-Schnittstelle **nicht selbst aktivieren**. Sie
muss in WinLaufen freigegeben werden:

1. WinLaufen starten
2. Wettkampf öffnen
3. in WinLaufen: **Abwicklung → Sprecher-PC… → Verbinden**
4. erst jetzt stellt WinLaufen die Schnittstelle auf TCP 4444 bereit
5. die Bridge verbindet sich automatisch dorthin
6. Bridge Control wechselt auf **Verbunden**
7. die Ergebnisse werden an den Live Server übertragen
8. Browser zeigen die Live-Ergebnisse

Solange Schritt 3 nicht erfolgt ist, zeigt Bridge Control **Nicht verbunden**.
Das ist ein erwarteter Betriebszustand und kein Installationsfehler.

## Browseradressen

Bridge Control:

```text
http://localhost:44442/                 auf dem Bridge-PC
http://<IP-des-Bridge-PCs>:44442/       aus dem LAN
```

Live-Ergebnisse:

```text
http://localhost:44440/                 auf dem Bridge-PC
http://<IP-des-Bridge-PCs>:44440/       aus dem LAN
```

Notebooks, Tablets und Smartphones im selben Netz rufen die Live-Ergebnisse
über die LAN-Adresse auf; Voraussetzung ist allein die Netzwerkerreichbarkeit.
Der Windows-Installer richtet die eingehenden Defender-Firewallregeln für die
Netzwerkprofile **Private** und **Domain** ein und bewusst **nicht** für
**Public**. Unter Linux verändert der Installer keine Firewall, sondern nennt
nur die je Profil benötigten Regeln.

In der realen Abnahme vom 30.08.2026 war das zum Beispiel
`http://192.168.95.198:44440/` — diese Adresse ist ein Beispiel aus jener
Umgebung und kein Vorgabewert.

## Bridge Control

Bridge Control ist die einzige Veranstalter-Oberfläche. Sie fragt nur nach dem,
was wirklich entschieden werden muss.

### WinLaufen

```text
Wo läuft WinLaufen?
  ( ) Auf diesem Computer
  ( ) Auf einem anderen Computer
```

„Auf diesem Computer" verwendet intern `127.0.0.1`; das Eingabefeld für den Host
bleibt verborgen. „Auf einem anderen Computer" blendet ein Feld für **IPv4-Adresse
oder Hostname** ein, zum Beispiel `192.168.95.20` oder `WINLAUFEN-PC` — keine URL
mit `http://`. TCP 4444 ist fest und nicht konfigurierbar.

### Live-Ergebnisse im Browser

Im All-in-One-Betrieb erscheint die lokale Ansicht nicht mehr als technisches
Ausgabeziel, sondern unter diesem Namen samt Verbindungszustand und den
Browseradressen. Die internen Werte des eingebauten lokalen Ziels — Target-ID,
Typ `LOCAL`, Endpoint, Channel und Secret — sind dort verborgen und nicht
bearbeitbar, damit die lokale Ansicht nicht versehentlich abgeschaltet wird.

Intern bleibt diese Ansicht ein ganz normales Output Target; die technische
Sicht steht in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

### Weitere Übertragung

Zusätzliche externe Live Server werden unter **Weitere Übertragung** über
**Weiteren Live-Server verbinden** angelegt. Die Target-Typen `SELFHOST` und
`RICHTER_PROJECTS` existieren technisch, ihre Konfiguration ist aber noch
technisch: Endpoint, Channel und Secret werden von Hand eingetragen. Ein
komfortables Pairing über einen kurzen Verbindungscode ist **nicht
implementiert**.

## Installationsprofile

Bei der Installation wird genau eine Sache ausgewählt: die Rolle des Rechners.
Adressen, Ziele und TLS gehören ausschließlich in die spätere Konfiguration über
Bridge Control. Ein Rechner kann deshalb Tage vor der Veranstaltung vollständig
installiert werden.

| Profil | Installiert | Linux | Windows 11 |
|---|---|---|---|
| All-in-One | Bridge + Live Server | `--profile all-in-one` | `-Profile AllInOne` |
| Bridge only | nur Bridge | `--profile bridge-only` | `-Profile BridgeOnly` |
| Presentation Node | nur Live Server | `--profile presentation-node` | nicht unterstützt |

Presentation Node auf Windows ist bewusst nicht implementiert; dafür Linux
verwenden. Unterstützt sind Debian, Ubuntu 24.04/26.04 und Raspberry Pi OS für
alle drei Profile sowie Windows 11 für All-in-One und Bridge only.

Nicht erreichbare Quellen und Output Targets verhindern die Installation nicht.
Der Installer prüft die lokale Installation — eigene Dienste, eigene Listener,
lokale HTTP-Endpunkte — und meldet erst danach den Verbindungszustand als
Betriebsdiagnose. Fehler der lokalen Installation bleiben harte
Installationsfehler.

## Projektstatus

Entwicklungsversion `0.2.0-SNAPSHOT`. Kein Tag, kein Release, keine
Releasefreigabe.

### Real bestätigt

- Windows-11-All-in-One-Installation aus dem Source Checkout
- reale WinLaufen-Kopplung über die Sprecher-PC-Schnittstelle
- Bridge und Live Server laufen dauerhaft als geplante Aufgaben
- Live-Ergebnisse lokal und aus dem LAN im Browser
- Bridge Control lokal und aus dem LAN
- lokale WebSocket-Übertragung Bridge → Live Server
- automatischer Reconnect zur WinLaufen-Quelle
- Windows-Firewallregeln für Private und Domain, keine Public-Freigabe

Der Nachweis ist in [docs/SMOKE_TESTS.md](docs/SMOKE_TESTS.md) protokolliert.

### Noch offen

- endgültiges Windows-x64-Releasepaket mit gebündelter Runtime
- echte Fresh Installation ohne Git, Maven und JDK
- reale Linux-Endabnahme
- Linux-Releasepakete für AMD64 und ARM64
- vollständige Reboot-, Reinstall- und Profilwechsel-Abnahmen
- Vereinfachung der Self-hosted-Konfiguration
- Richter-Projects-Pairing
- bekannte P2-/P3-Punkte aus den Reviews

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

- Einsatz **nur** in kontrollierten Vereins- bzw. Veranstaltungsnetzen.
- Die Ports 44441 und 44442 dürfen **nicht** unkontrolliert aus nicht
  vertrauenswürdigen Netzen erreichbar sein.
- **Keine Portweiterleitung** ins öffentliche Internet.
- Kein Betrieb in offenen Gäste-WLANs oder gemeinsam genutzten Netzen.
- Die Windows-Firewallregeln bleiben bewusst auf Private und Domain beschränkt.
- Wo möglich, `winlaufen.live.secret` und das zugehörige Target-Secret in Bridge
  Control auf einen eigenen Wert setzen; das reduziert das Risiko, ersetzt aber
  keine echte Provisionierung.

Für produktiven Betrieb über WAN oder eine Anbindung an Richter-Projects sind
**WSS sowie individuell provisionierte Secrets pro Target** erforderlich. Das
ist bewusst nicht Teil dieser Prototype Baseline und bleibt ein offenes
Production-Hardening-Thema.

Der Live Server weist beim Start ausdrücklich auf das aktive Default-Secret hin.

## Unterstützte Funktion

Verifiziert sind Lauf und Biathlon. Tabellenheader, Zeilen, Indizes, Clock,
Current Finish, Schießen und Nachrichten werden ohne fachliche Korrektur
transportiert. Die Live-Ergebnisse bieten Startlisten-Platzhalter, LIVE und
Ergebnisse. WinSpringen, ein erfundenes Startlistenprotokoll, Datenbank und
Broker bleiben ausdrücklich außerhalb von v0.1.

## Technische Namen

Sichtbar heißt das Produkt **WinLaufen Sprecher Web**. Technische Bezeichner
bleiben aus Kompatibilitätsgründen zunächst unverändert, damit bestehende
Befehle, Upgrade-Pfade und Deinstallationen weiter funktionieren:

- Maven-Artefakte und JARs `winlaufen-web-*`
- Java-Packages `de.winlaufen.web.*`
- Installationspfade `/opt/winlaufen-web`, `/etc/winlaufen-web`,
  `C:\Program Files\WinLaufen Web`
- systemd-Units `winlaufen-bridge.service`, `winlaufen-live-server.service`
- geplante Aufgaben `WinLaufen Web Bridge`, `WinLaufen Web Live Server`
- Firewall-Regel-IDs `WinLaufenWeb-*`

## Dokumentation

| Dokument | Inhalt |
|---|---|
| [docs/INSTALLATION.md](docs/INSTALLATION.md) | Installationsprofile, Linux, Windows, Dienste, Deinstallation |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | Build, Entwicklungsbetrieb, Konfigurationsorte, Transportregel |
| [docs/SMOKE_TESTS.md](docs/SMOKE_TESTS.md) | manuelle Abnahmetests und protokollierte reale Nachweise |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | technischer IST-Stand der modularen Architektur |
| [docs/MODULAR_ARCHITECTURE.md](docs/MODULAR_ARCHITECTURE.md) | vollständige verbindliche Architekturentscheidungen |
| [docs/PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md) | Produktspezifikation |
| [docs/WINLAUFEN_PROTOCOL.md](docs/WINLAUFEN_PROTOCOL.md) | WinLaufen-Protokoll und reale Evidenz |
| [docs/RELEASE.md](docs/RELEASE.md) | tag-basierter Release-Ablauf für Maintainer |

## Lizenz

WinLaufen Sprecher Web steht unter der [GNU Affero General Public License
v3.0](LICENSE) (AGPL-3.0). Kurz gefasst: Der Quellcode ist frei nutzbar,
veränderbar und weitergebbar. Wer eine veränderte Version über ein Netzwerk
zugänglich macht — auch als gehosteten Dienst, ohne den Code selbst
weiterzugeben — muss den vollständigen, veränderten Quellcode ebenfalls
unter der AGPL-3.0 verfügbar machen (§13 der Lizenz).

Für Sportvereine, die die Software unverändert oder mit eigenen Anpassungen
ausschließlich für ihre eigene Veranstaltung betreiben, entstehen daraus
keine Pflichten — die Offenlegungspflicht greift erst, wenn Dritte über ein
Netzwerk auf eine veränderte Version zugreifen können.
