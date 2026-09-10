# Sprecher-Web — Bedienerhandbuch

**Live-Ergebnisse aus WinLaufen**

Diese Anleitung richtet sich an Vereine und Veranstalter. Sie führt von der
Installation über die Einrichtung bis zum Betrieb an einem Wettkampftag. Sie
brauchen dafür keine Programmierkenntnisse und müssen keine Architektur- oder
Entwicklerdokumente lesen.

Wer die technischen Hintergründe sucht, findet sie in
[INSTALLATION.md](INSTALLATION.md) und [DEVELOPMENT.md](DEVELOPMENT.md).

---

## Inhalt

1. [Was ist Sprecher-Web?](#1-was-ist-sprecher-web)
2. [Was Sie brauchen](#2-was-sie-brauchen)
3. [Welche Installation passt zu Ihnen?](#3-welche-installation-passt-zu-ihnen)
4. [Windows: All-in-One installieren](#4-windows-all-in-one-installieren)
5. [Linux: All-in-One oder Bridge only installieren](#5-linux-all-in-one-oder-bridge-only-installieren)
6. [WinLaufen verbinden](#6-winlaufen-verbinden)
7. [Bridge Control bedienen](#7-bridge-control-bedienen)
8. [Live-Ergebnisse im Browser](#8-live-ergebnisse-im-browser)
9. [Die angezeigte Wettkampfzeit](#9-die-angezeigte-wettkampfzeit)
10. [Statusanzeigen verstehen](#10-statusanzeigen-verstehen)
11. [Was bei Ausfällen passiert](#11-was-bei-ausfällen-passiert)
12. [Zusätzlicher Live-Server im Internet](#12-zusätzlicher-live-server-im-internet)
13. [Ablauf an einem Wettkampftag](#13-ablauf-an-einem-wettkampftag)
14. [Wenn etwas nicht funktioniert](#14-wenn-etwas-nicht-funktioniert)
15. [Was Sie beachten müssen](#15-was-sie-beachten-müssen)

---

## 1. Was ist Sprecher-Web?

WinLaufen kann seine Live-Ergebnisse über die eingebaute Funktion
**Sprecher-PC** an ein zweites Programm weitergeben. Genau dort setzt
Sprecher-Web an: Es liest diese Daten mit und zeigt sie zusätzlich in jedem
Browser an — auf Notebooks, Tablets und Smartphones.

```text
WinLaufen  ──►  Sprecher-Web  ──►  Browser der Zuschauer
```

Wichtig zum Verständnis:

* Sprecher-Web ist **keine** Web-Version von WinLaufen. WinLaufen bleibt Ihre
  Wettkampfsoftware und läuft unverändert weiter.
* Sprecher-Web liest **nur mit**. Es schreibt nichts nach WinLaufen zurück und
  kann Ihre Wettkampfdaten nicht verändern.
* Im Browser erscheint genau das, was WinLaufen über die Sprecher-PC-Funktion
  herausgibt — nicht mehr und nicht weniger.

Der Name lehnt sich an den WinLaufen-Begriff „Sprecher-PC" an. **Sprecher-PC**
meint immer die WinLaufen-Funktion, **Sprecher-Web** immer dieses Programm.

Sie bedienen zwei Oberflächen:

| Oberfläche | Wofür | Wer |
|---|---|---|
| **Bridge Control** | Einrichtung und Statuskontrolle | nur Sie als Veranstalter |
| **Live-Ergebnisse** | die Ergebnisanzeige | alle Zuschauer |

---

## 2. Was Sie brauchen

### Für den Normalfall (Windows)

* Windows 11
* WinLaufen (getestet mit Version 16 und 19)
* ein Netzwerk (LAN oder WLAN), in dem die Zuschauergeräte den Windows-PC
  erreichen

### Für einen Linux-Rechner

* Debian, Ubuntu 24.04 LTS, Ubuntu 26.04 LTS oder Raspberry Pi OS

Andere Distributionen werden vom Installer nicht abgelehnt, aber als nicht
getestet gemeldet.

### Kein Java, kein Git — wenn Sie das Releasepaket nehmen

Es gibt **zwei Wege**, Sprecher-Web zu installieren. Für Veranstalter ist nur
der erste gedacht:

| Weg | Für wen | Was Sie brauchen |
|---|---|---|
| **Releasepaket** herunterladen und entpacken | **Sie als Veranstalter** | nur einen Browser zum Herunterladen |
| **Quellcode** holen und selbst bauen | Entwickler | zusätzlich Git und JDK 25 |

Ein **Releasepaket** ist eine fertig gebaute Version zum Herunterladen. Es
enthält bereits das fertige Programm **und** die passende Java-Umgebung, es
muss also nichts übersetzt und kein Java installiert werden. Die Pakete liegen
unter
[github.com/richtertoralf/winlaufen-web/releases](https://github.com/richtertoralf/winlaufen-web/releases):

* Windows: `winlaufen-web-<version>-windows-x64.zip`
* Linux: `winlaufen-web-<version>-linux-amd64.tar.gz`

Für einen **Raspberry Pi** gibt es noch kein fertiges Paket; dort ist derzeit
der Weg über den Quellcode nötig.

Dieses Handbuch beschreibt in den Kapiteln 4 und 5 zuerst den Weg über das
Releasepaket und nennt den Entwicklerweg jeweils darunter als Alternative.

---

## 3. Welche Installation passt zu Ihnen?

Bei der Installation wählen Sie genau **eine** Sache: die Rolle des Rechners.
Adressen und Ziele tragen Sie erst danach in Bridge Control ein. Sie können
einen Rechner deshalb schon Tage vor der Veranstaltung fertig einrichten.

| | Profil | Wann Sie es wählen |
|---|---|---|
| **[1]** | **All-in-One** | **Der Normalfall.** Alles auf einem Rechner — am einfachsten direkt auf dem PC, auf dem WinLaufen läuft. |
| **[2]** | **Bridge only** | Nur, wenn die Ergebnisanzeige bewusst auf einem anderen Rechner laufen soll. |
| **[3]** | **Presentation Node** | Nur Ergebnisanzeige, ohne WinLaufen-Anbindung — typisch ein gemieteter Server im Internet. **Nur unter Linux.** |

### Empfohlener Einstieg: All-in-One auf dem WinLaufen-PC

```text
Windows-PC
├─ WinLaufen
└─ Sprecher-Web (All-in-One)
        │
        ▼  http://<IP-des-PCs>:44440/
   Notebooks, Tablets, Smartphones im WLAN
```

Vorteile: eine einzige Installation, keine zusätzliche Hardware, sofort
testbar, und die Zuschauergeräte im Veranstaltungsnetz sehen die Ergebnisse.

### Variante: WinLaufen auf einem anderen Rechner

Sprecher-Web muss **nicht** auf dem WinLaufen-PC laufen. Sie können All-in-One
auch auf einem anderen Rechner im selben Netz installieren und dort in Bridge
Control die Adresse des WinLaufen-PCs eintragen. Sprecher-Web verbindet sich
dann über TCP 4444 dorthin.

### Variante: zusätzlicher Server im Internet

Zusätzlich zum lokalen Betrieb können Sie einen **Presentation Node** auf einem
gemieteten Ubuntu-Server betreiben, damit auch Zuschauer außerhalb des
Veranstaltungsnetzes mitlesen können. Siehe Kapitel 12.

---

## 4. Windows: All-in-One installieren

### Schritt 1 — Releasepaket herunterladen und entpacken

Öffnen Sie im Browser
[github.com/richtertoralf/winlaufen-web/releases](https://github.com/richtertoralf/winlaufen-web/releases)
und laden Sie beim obersten Eintrag die Datei

```text
winlaufen-web-<version>-windows-x64.zip
```

herunter. Entpacken Sie die ZIP-Datei, zum Beispiel im Ordner „Downloads".
Dabei entsteht ein Ordner mit demselben Namen.

Es ist **kein** Setup-Programm im gewohnten Sinn: Sprecher-Web bringt heute
noch keinen `.exe`-Installer mit. Die Installation erfolgt gleich mit einem
mitgelieferten PowerShell-Skript.

Sie müssen dafür **weder Git noch Java installieren**: Die passende
Java-Umgebung liegt im Paket, und Git wird nur für den Entwicklerweg gebraucht.

### Schritt 2 — Alternative: aus dem Quellcode bauen

Diesen Schritt brauchen Sie **nur**, wenn Sie bewusst einen
Entwicklungsstand testen wollen. Sonst weiter mit Schritt 3.

```powershell
winget install --id Git.Git --exact --source winget
winget install --id Microsoft.OpenJDK.25 --exact --source winget
```

Danach **PowerShell schließen und neu öffnen**, damit die geänderten
Umgebungsvariablen wirksam werden. Anschließend prüfen:

```powershell
git --version
java -version
javac -version
```

```powershell
git clone https://github.com/richtertoralf/winlaufen-web.git
Set-Location winlaufen-web
.\mvnw.cmd clean package
```

Der erste Build lädt einmalig Abhängigkeiten aus dem Internet und dauert
einige Minuten. Am Ende muss `BUILD SUCCESS` stehen. Dieser Weg installiert
den aktuellen Entwicklungsstand — nicht unbedingt eine veröffentlichte
Version.

### Schritt 3 — WinLaufen-Verbindung trennen

> ### ⚠ Vor der Installation unbedingt ausführen
>
> Trennen Sie in WinLaufen die Sprecher-PC-Verbindung:
>
> **WinLaufen → Abwicklung → Sprecher-PC… → Trennen**
>
> **WinLaufen selbst müssen Sie nicht beenden** — nur die Verbindung trennen.
>
> Das gilt für die Profile **All-in-One** und **Bridge only**, weil dort die
> Bridge installiert oder ersetzt wird. Für einen **Presentation Node** ist es
> nicht nötig.

### Schritt 4 — Installer als Administrator ausführen

PowerShell über das Startmenü mit **„Als Administrator ausführen"** neu
starten und in den entpackten Ordner wechseln — beim Releasepaket ist das der
Ordner aus Schritt 1, beim Quellcode-Weg das Repository:

```powershell
Set-Location $HOME\Downloads\winlaufen-web-<version>-windows-x64
```

**Skriptausführung für dieses Fenster erlauben.** Windows blockiert das
Ausführen von PowerShell-Skripten standardmäßig. Ohne diesen Schritt bricht
der Installer mit einer Meldung wie *„Die Datei … kann nicht geladen werden, da
die Ausführung von Skripts auf diesem System deaktiviert ist"*
(`PSSecurityException`) ab.

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

Das gilt **nur für dieses eine PowerShell-Fenster**. Sobald Sie es schließen,
gilt wieder die vorherige Einstellung Ihres Systems. Es wird keine dauerhafte
systemweite Richtlinie geändert. Sie brauchen die Einstellung bei jeder
Installation erneut — das ist beabsichtigt.

Danach den Installer starten:

```powershell
.\installer\windows\Install-WinLaufenWeb.ps1
```

Alternativ als einmaliger Aufruf ohne vorherige Umstellung, falls Ihnen das
lieber ist:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\installer\windows\Install-WinLaufenWeb.ps1"
```

Der Installer fragt nur nach dem Profil. Wählen Sie **[1] All-in-One**. Er
fragt **keine** IP-Adresse, keinen Hostnamen und keine Internetadresse ab —
das kommt später in Bridge Control.

> **Falsche Umlaute in der Installerausgabe (Version 0.4.0).** In der
> Windows-PowerShell erscheinen deutsche Umlaute des Installers verstümmelt,
> zum Beispiel „lÃ¤uft" statt „läuft". Das ist eine reine Anzeigefrage: Die
> Installation, die Dienste und Ihre Konfiguration sind davon nicht betroffen.
> Der Fehler ist behoben und verschwindet mit der nächsten Version.

### Schritt 5 — WinLaufen wieder verbinden

Nach erfolgreicher Installation in WinLaufen:

**WinLaufen → Abwicklung → Sprecher-PC… → Verbinden**

### Was der Installer eingerichtet hat

Bridge und Live Server laufen als **geplante Aufgaben** mit dem Trigger „Beim
Systemstart". Sie starten nach einem Neustart automatisch, ohne dass sich
jemand anmelden muss und ohne offenes Konsolenfenster.

Die Windows-Firewall wird für die Netzwerkprofile **Privat** und **Domäne**
freigegeben, bewusst **nicht** für **Öffentlich**. Falls Ihr Veranstaltungs-WLAN
als „Öffentliches Netzwerk" eingestuft ist, erreichen die Zuschauergeräte den
PC nicht — stellen Sie das Netzwerk in den Windows-Einstellungen auf „Privates
Netzwerk" um.

### Upgrade auf eine neuere Version

Genau derselbe Ablauf wie die Installation, mit demselben Profil.

* **Releasepaket:** neues ZIP herunterladen, entpacken, Verbindung trennen
  (Schritt 3), Installer aus dem **neuen** Ordner ausführen, Verbindung wieder
  herstellen.
* **Quellcode:** Verbindung trennen, `git pull --ff-only`,
  `.\mvnw.cmd clean package`, Installer erneut ausführen, Verbindung wieder
  herstellen.

Ihre Konfiguration bleibt in beiden Fällen erhalten — ebenso die importierte
Startliste. Bleiben Sie bei dem Weg, mit dem Sie installiert haben.

---

## 5. Linux: All-in-One oder Bridge only installieren

**Mit dem Releasepaket — der normale Weg.** Von der
[Releases-Seite](https://github.com/richtertoralf/winlaufen-web/releases) das
Archiv `winlaufen-web-<version>-linux-amd64.tar.gz` herunterladen, dann:

```sh
tar -xzf winlaufen-web-<version>-linux-amd64.tar.gz
cd winlaufen-web-<version>-linux-amd64
sudo ./installer/linux/install.sh
```

Git, Maven und Java müssen dafür nicht installiert sein; die Java-Umgebung
liegt im Paket. Für ein Upgrade laden Sie später einfach das neue Paket
herunter und führen den Installer daraus erneut mit demselben Profil aus.

**Aus dem Quellcode — nur für Entwicklungsstände.**

```sh
sudo apt install git openjdk-25-jdk
git clone https://github.com/richtertoralf/winlaufen-web.git
cd winlaufen-web
./mvnw clean package
sudo ./installer/linux/install.sh
```

Auf einem Raspberry Pi ist dies derzeit der einzige Weg, weil noch kein
ARM-Paket veröffentlicht wird.

> Auch hier gilt: Läuft WinLaufen bereits und ist die Sprecher-PC-Verbindung
> aktiv, vorher **WinLaufen → Abwicklung → Sprecher-PC… → Trennen** und nach der
> Installation wieder **Verbinden**.

Der Installer fragt nach dem Profil. Bridge und Live Server laufen anschließend
als `systemd`-Dienste und starten nach einem Neustart automatisch:

```sh
systemctl status winlaufen-bridge
systemctl status winlaufen-live-server
```

Der Linux-Installer verändert **keine** Firewall. Er nennt nur die Ports, die
Sie gegebenenfalls selbst freigeben müssen.

---

## 6. WinLaufen verbinden

Sprecher-Web kann die Sprecher-PC-Schnittstelle **nicht selbst einschalten**.
Sie müssen sie in WinLaufen freigeben:

1. WinLaufen starten
2. Wettkampf öffnen
3. **Abwicklung → Sprecher-PC… → Verbinden**
4. WinLaufen stellt die Schnittstelle jetzt auf TCP 4444 bereit
5. Sprecher-Web verbindet sich von selbst dorthin
6. Bridge Control wechselt auf **Verbunden**
7. die Ergebnisse erscheinen im Browser

Solange Schritt 3 fehlt, zeigt Bridge Control **Nicht verbunden**. Das ist ein
normaler Betriebszustand und kein Installationsfehler.

---

## 7. Bridge Control bedienen

Bridge Control öffnen Sie im Browser:

```text
http://localhost:44442/              auf dem Rechner selbst
http://<IP-des-Rechners>:44442/      aus dem Netzwerk
```

> Bridge Control besitzt bewusst **keine Anmeldung**. Wer die Seite erreicht,
> kann die Einrichtung ändern. Der Port 44442 darf deshalb nur in Ihrem
> vertrauenswürdigen Netz erreichbar sein — niemals im Gäste-WLAN und niemals
> aus dem Internet.

### Abschnitt „WinLaufen"

Die Frage lautet: **Wo läuft WinLaufen?**

* **Auf diesem Computer** — der Normalfall, wenn Sie All-in-One auf dem
  WinLaufen-PC installiert haben. Es ist nichts weiter einzutragen.
* **Auf einem anderen Computer** — dann erscheint ein Feld für die Adresse.
  Tragen Sie dort die IPv4-Adresse oder den Rechnernamen ein, zum Beispiel
  `192.168.95.20` oder `WINLAUFEN-PC`. **Ohne** `http://` und **ohne** Port.

Darunter sehen Sie den aktuellen Verbindungszustand. Ist er „Nicht verbunden",
erinnert Sie ein Hinweis an **Abwicklung → Sprecher-PC… → Verbinden**.

### Abschnitt „Live-Ergebnisse im Browser"

Zeigt die Adressen, unter denen die Ergebnisanzeige erreichbar ist, und ob sie
gerade Daten bekommt. Hier gibt es nichts einzustellen.

### Abschnitt „Weitere Übertragung"

Nur nötig, wenn Sie zusätzlich einen weiteren Live-Server beliefern wollen —
etwa den gemieteten Server aus Kapitel 12. Im Normalfall tragen Sie dort
ausschließlich die **IP-Adresse** des Live-Servers ein, zum Beispiel:

```text
xxx.xxx.xxx.xxx
```

Alles Weitere setzt Sprecher-Web selbst. Unter **Erweiterte Einstellungen**
liegen technische Felder (ID, Typ, Endpoint, Channel, Verbindungsschlüssel).
Die brauchen Sie im Normalfall nicht anzufassen; sie sind für spätere
Betriebsarten mit mehreren Veranstaltungen auf einem Server vorgesehen.

### Abschnitt „Öffentliche Darstellung"

Hier legen Sie fest, welche Spalten die Zuschauer sehen: Verein, Verband,
Nation, Schießen sowie WinLaufen-Nachrichten. Änderungen wirken sofort auf
allen verbundenen Browsern.

Zum Schluss **Speichern**.

### Abschnitt „Startliste"

So importieren Sie eine Startliste:

1. **In WinLaufen exportieren.** Unterstützt werden **CSV**, **TXT** und
   **XLSX**. Das alte binäre Format `.xls` wird abgelehnt — exportieren Sie in
   diesem Fall neu als CSV oder XLSX.
2. **Bridge Control öffnen** unter `http://<bridge-ip>:44442/` und zum
   Abschnitt *Startliste* blättern.
3. **Datei auswählen.**
4. **Startliste importieren** anklicken.
5. **Status prüfen.** Bei Erfolg erscheint eine Bestätigung mit Teilnehmerzahl,
   Klassenzahl und Generation; darüber steht der neue Bestand.

Nach dem Import steht dort zum Beispiel:

```text
Datei:       Startliste.xlsx
Quelle:      IMPORT_XLSX
Generation:  3
Teilnehmer:  147
Klassen:     16
```

Solange nichts importiert wurde, steht dort **Keine Startliste importiert**.

Drei Dinge sind wichtig:

- **Jeder Import ersetzt die bisherige Startliste vollständig.** Es wird
  nichts ergänzt und nichts zusammengeführt. Wer in der neuen Datei fehlt,
  ist danach nicht mehr vorhanden. Das ist beabsichtigt: dieselbe Startnummer
  kann im Prolog und im Lauf zu verschiedenen Personen gehören.
- **Die Generation zählt nur Ihre Importe.** Sie erhöht sich bei jedem
  erfolgreichen Import um genau eins, auch wenn Sie dieselbe Datei erneut
  importieren. Sie sagt nichts darüber aus, ob ein neuer Wettkampf begonnen
  hat.
- **Ein abgelehnter Import ändert nichts.** Meldet Sprecher-Web einen Fehler,
  gilt weiterhin die zuletzt erfolgreich importierte Startliste.

Die Startliste bleibt gespeichert. Nach einem Neustart der Bridge oder des
Rechners ist sie mit derselben Generation wieder da; der Neustart selbst zählt
nicht als Import.

Die importierte Startliste erscheint automatisch in den Live-Ergebnissen im
Browser — siehe [Live-Ergebnisse im Browser](#8-live-ergebnisse-im-browser).
Ein Neustart von Bridge oder Live Server ändert daran nichts; die Startliste
wird von selbst wieder übertragen, ohne erneuten Import.

---

## 8. Live-Ergebnisse im Browser

```text
http://localhost:44440/              auf dem Rechner selbst
http://<IP-des-Rechners>:44440/      aus dem Netzwerk
```

Die IP-Adresse Ihres Rechners zeigt Ihnen Bridge Control im Abschnitt
„Live-Ergebnisse im Browser" an. Diese Adresse geben Sie an die Zuschauer
weiter — als Aushang, als Link oder als QR-Code am Zielbereich.

Die Seite hat drei Ansichten: **Startliste**, **LIVE** und **Ergebnisse**.
LIVE folgt automatisch der Klasse, aus der WinLaufen zuletzt ein Ergebnis
gemeldet hat; unter Ergebnisse wählen Sie eine Klasse selbst aus.

### Ansicht „Startliste"

Hier steht die Startliste, die Sie in Bridge Control importiert haben. Weil
eine Veranstaltung leicht 2 000 Teilnehmer in mehreren Dutzend Klassen hat,
zeigt die Ansicht **eine Klasse auf einmal**:

```text
Startliste                              Klasse [ Schüler U12 m    ▾ ]

   [ ← ]        Klasse 1 von 46        [ → ]

Startzeit   StNr   Name                Verein
10:00:00       1   Albert ALBRECHT     ATSV Geb. Gelobtland
10:00:15       2   Anna BAUER          Blau Weiß Zwenkau
```

Blättern Sie mit **←** und **→** oder wählen Sie eine Klasse direkt aus der
Liste. An der ersten und letzten Klasse ist die jeweilige Pfeiltaste
abgeschaltet — es wird nicht im Kreis geblättert.

Klassen und Teilnehmer stehen **genau in der Reihenfolge Ihrer Startliste**.
Es wird nichts umsortiert, weder alphabetisch noch nach Startnummer: Sie und
WinLaufen haben die Reihenfolge festgelegt.

Welche Spalten erscheinen, hängt von zwei Dingen ab: von Ihren Einstellungen
unter *Öffentliche Darstellung* (Verein, Verband, Nation) und davon, ob die
Startliste die Angabe überhaupt enthält. Eine Spalte, zu der es keine Daten
gibt, wird nicht angezeigt.

Solange keine Startliste importiert ist, steht dort **Keine Startliste
verfügbar**.

Nach einem neuen Import erscheint die neue Startliste von selbst — Sie müssen
den Browser nicht neu laden und keinen Dienst neu starten.

---

## 9. Die angezeigte Wettkampfzeit

Oben rechts steht eine Zeit. Das ist die **Wettkampfzeit aus WinLaufen**.

Sie ist **nicht**:

* die Uhr Ihres Browsers,
* die Systemzeit des WinLaufen-PCs, der Bridge oder des Live-Servers,
* eine von Sprecher-Web erzeugte laufende Uhr.

Sprecher-Web reicht den Wert, den WinLaufen liefert, unverändert durch und
erzeugt niemals eine eigene Uhr.

**Kommen keine neuen Zeitwerte aus WinLaufen mehr an, bleibt die Zeit stehen.**
Sie wird nicht künstlich weitergezählt. Das ist ausdrücklich so gewollt.

> ### Für Sprecher die wichtigste Anzeige überhaupt
>
> Läuft die Wettkampfzeit sichtbar weiter, dann kommen in genau diesem Moment
> aktuelle Daten von WinLaufen bis zu Ihrer Anzeige durch — über die gesamte
> Kette hinweg.
>
> Steht die Wettkampfzeit still, kommen gerade keine frischen Daten an.
> Prüfen Sie dann die Verbindung zu WinLaufen.

---

## 10. Statusanzeigen verstehen

Auf der Ergebnisseite stehen oben rechts eine farbige Statusanzeige und die
Wettkampfzeit. Es gibt zwei voneinander unabhängige Ebenen.

### Roter Hinweis „Keine Verbindung zum Live-Server"

> Keine Verbindung zum Live-Server. Die angezeigten Daten sind nicht aktuell.
> Es wird automatisch neu verbunden.

**Bedeutung:** Dieser Browser erreicht den Live-Server gerade nicht — Netzwerk,
WLAN oder der Server selbst. Die letzten Ergebnisse bleiben lesbar, werden aber
abgeblendet dargestellt, damit niemand sie für aktuell hält.

**Was Sie tun:** nichts. Die Seite verbindet sich selbständig wieder, sobald der
Server erreichbar ist. Ein manueller Reload ist **nicht** nötig.

### Statusanzeige ohne roten Hinweis

Erscheint **kein** roter Hinweis, ist der Live-Server erreichbar. Die
Statusanzeige beschreibt dann die Verbindung von Sprecher-Web zu **WinLaufen**:

| Anzeige | Bedeutung | Was zu tun ist |
|---|---|---|
| `CONNECTED` | WinLaufen liefert aktuelle Daten. | Nichts. Normalbetrieb. |
| `STALE` | Seit einigen Sekunden kein Lebenszeichen aus WinLaufen. | Kurz beobachten. |
| `DISCONNECTED` | Keine Verbindung zu WinLaufen. | In WinLaufen **Abwicklung → Sprecher-PC… → Verbinden** prüfen. |

Bei `STALE` und `DISCONNECTED` bleiben die letzten Ergebnisse und die letzte
Wettkampfzeit sichtbar stehen.

**Die beiden Ebenen nicht verwechseln:** Der rote Hinweis betrifft *Ihren
Browser*. Die Statusanzeige betrifft *WinLaufen*. Beides kann unabhängig
voneinander in Ordnung oder gestört sein.

---

## 11. Was bei Ausfällen passiert

Sprecher-Web ist darauf ausgelegt, dass niemand veraltete Daten für aktuell
hält und dass Sie nichts von Hand nachstarten müssen.

| Was ausfällt | Was Sie sehen | Was passiert |
|---|---|---|
| **Live-Server hält an oder startet neu** | roter Hinweis, Daten abgeblendet, Wettkampfzeit steht | Der Browser verbindet automatisch neu und zeigt danach die aktuelle Wettkampfzeit. Kein Reload nötig. |
| **Der ganze Server-Rechner startet neu** | nach wenigen Sekunden roter Hinweis | Sobald der Rechner zurück ist, verbindet der Browser von selbst. Kein Reload nötig. |
| **WinLaufen wird getrennt** | kein roter Hinweis, Status `STALE`/`DISCONNECTED`, Wettkampfzeit steht | Ergebnisse bleiben sichtbar. Nach **Verbinden** in WinLaufen läuft alles von selbst weiter. |
| **Sprecher-Web-Bridge hält an oder startet neu** | Status `DISCONNECTED`, Wettkampfzeit steht, Ergebnisse bleiben sichtbar | Nach dem Neustart läuft die Wettkampfzeit wieder. Die bisherigen Ergebnisse bleiben stehen, bis WinLaufen den nächsten Ergebnisstand liefert. |
| **Der Browser wird geschlossen** | — | Nichts. Andere Browser und die Aufzeichnung laufen unverändert weiter. |

Der letzte Fall verdient eine Erklärung: Nach einem Neustart der Bridge kennt
Sprecher-Web zuerst nur die Wettkampfzeit wieder, noch nicht die Ergebnisliste
— WinLaufen sendet Ergebnisse nur, wenn sich etwas ändert. Die zuletzt
bekannten Ergebnisse bleiben deshalb bewusst stehen, bis der nächste
Zieleinlauf sie ersetzt.

---

## 12. Zusätzlicher Live-Server im Internet

Wenn auch Zuschauer außerhalb des Veranstaltungsnetzes mitlesen sollen, können
Sie zusätzlich einen kleinen Server im Internet betreiben — typischerweise für
die Dauer der Veranstaltung gemietet.

```text
WinLaufen-PC ──► Sprecher-Web Bridge ──► gemieteter Server ──► Zuschauer weltweit
   (Ihr Netz)         (Ihr Netz)          (öffentliche IPv4)
```

Sie brauchen dafür **keine eigene Domain**. Die öffentliche IPv4-Adresse
genügt.

Kurz zusammengefasst:

1. Ubuntu-Server mieten, dort Sprecher-Web mit Profil **[3] Presentation Node**
   installieren.
2. In der Firewall des Anbieters **TCP 44440** und **TCP 44441** freigeben.
   **TCP 44442 gehört dort nicht hin.**
3. In Bridge Control auf Ihrem Rechner unter „Weitere Übertragung" die
   **IP-Adresse** des gemieteten Servers eintragen und speichern.
4. Die Adresse `http://<öffentliche-IP>:44440/` an die Zuschauer weitergeben.
5. Nach der Veranstaltung den Server abschalten oder löschen.

Die vollständige Schritt-für-Schritt-Anleitung steht in
[QUICKSTART_CLOUD.md](QUICKSTART_CLOUD.md).

> **Was Sie dabei wissen müssen:** In diesem bewusst einfachen Betrieb ist die
> Übertragung unverschlüsselt. Mitgelesen werden können deshalb sowohl die
> übertragenen Daten als auch der Verbindungsschlüssel — und wer den
> Verbindungsschlüssel kennt oder mitliest, kann unter Umständen unerwünschte
> Daten einspeisen. Für einen temporären Selfhost- oder Testserver an einem
> Wettkampftag ist dieser einfache Betrieb vertretbar. Für einen dauerhaften
> oder zentral betriebenen Dienst ist verschlüsselte Übertragung vorgesehen.
> Die verbindlichen Grenzen stehen in der
> [README](../README.md#known-prototype-security-limitation).

---

## 13. Ablauf an einem Wettkampftag

### Am Vortag

- [ ] Sprecher-Web installiert und Bridge Control erreichbar
- [ ] WinLaufen probeweise verbunden, Ergebnisse im Browser sichtbar
- [ ] Öffentliche Darstellung eingestellt (welche Spalten sollen erscheinen)
- [ ] Zuschaueradresse `http://<IP>:44440/` notiert, QR-Code vorbereitet
- [ ] WLAN geprüft: Erreichen Tablets und Smartphones den Rechner?

### Am Wettkampftag

- [ ] Rechner starten — Sprecher-Web startet automatisch mit
- [ ] WinLaufen starten, Wettkampf öffnen
- [ ] **Abwicklung → Sprecher-PC… → Verbinden**
- [ ] Bridge Control zeigt **Verbunden**
- [ ] Ergebnisseite öffnen und prüfen: **Läuft die Wettkampfzeit?**

### Während der Veranstaltung

Die laufende Wettkampfzeit ist Ihre Kontrollanzeige. Steht sie still, prüfen
Sie WinLaufen. Alles Weitere erledigt Sprecher-Web selbst — auch nach
Netzwerkstörungen.

### Nach der Veranstaltung

- [ ] Falls ein gemieteter Server im Internet lief: abschalten oder löschen
- [ ] Sprecher-Web kann auf dem Rechner installiert bleiben

---

## 14. Wenn etwas nicht funktioniert

| Beobachtung | Wahrscheinliche Ursache |
|---|---|
| Installer bricht mit `PSSecurityException` ab | `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass` im selben Fenster vergessen (Kapitel 4). |
| Installer meldet fehlende Administratorrechte | PowerShell nicht über „Als Administrator ausführen" gestartet. |
| Bridge Control zeigt dauerhaft „Nicht verbunden" | In WinLaufen fehlt **Abwicklung → Sprecher-PC… → Verbinden**, oder die eingetragene Adresse des WinLaufen-PCs stimmt nicht. |
| Ergebnisseite lokal erreichbar, aus dem WLAN nicht | Windows-Netzwerkprofil steht auf „Öffentlich". Auf „Privat" umstellen. |
| Zeit läuft, aber keine Ergebnisse | WinLaufen hat noch keinen Ergebnisstand gesendet. Erscheint mit dem ersten Zieleinlauf. |
| Wettkampfzeit steht still, Status `CONNECTED` | Sollte nicht auftreten. Ergebnisseite einmal neu laden und den Zustand melden. |
| Seite zeigt roten Verbindungshinweis | Der Browser erreicht den Live-Server nicht. Nichts tun — die Seite verbindet selbst neu. |
| Nach einem Upgrade verhält sich eine offene Seite seltsam | Diese eine Seite einmal neu laden; sie führt noch das alte Skript aus. |
| Adresse mit Port eingetragen und Speichern schlägt fehl | Im Adressfeld gehört nur die IP-Adresse, ohne `http://` und ohne `:44441`. |

---

## 15. Was Sie beachten müssen

Sprecher-Web ist eine **Entwicklungsversion für den Einsatz in kontrollierten
Vereins- und Veranstaltungsnetzen**. Zwei Punkte sind bewusst offen und für
Sie als Betreiber wichtig:

* **Bridge Control (TCP 44442) hat keine Anmeldung.** Wer die Seite erreicht,
  kann die Einrichtung ändern. Der Port darf nur in Ihrem eigenen,
  vertrauenswürdigen Netz erreichbar sein — nicht im Gäste-WLAN, nicht über
  eine Portweiterleitung, nicht aus dem Internet.
* **Die Datenübertragung nutzt einen bekannten Standardschlüssel.** Wer den
  Übertragungsport erreicht, könnte gefälschte Ergebnisse einspielen. Deshalb
  gehört Sprecher-Web in ein kontrolliertes Netz — mit der ausdrücklichen
  Ausnahme des temporären Servers aus Kapitel 12.

Die vollständigen und verbindlichen Einsatzgrenzen stehen in der
[README](../README.md#known-prototype-security-limitation).

---

## Andere Systeme anbinden

Sprecher-Web kann seinen aktuellen Stand zusätzlich **maschinenlesbar**
bereitstellen, damit andere Anwendungen ihn verwenden können — zum Beispiel eine
Grafikeinblendung im Livestream. Für Sie als Veranstalter ändert sich dadurch
nichts: Sie müssen nichts einschalten und nichts einstellen.

Ein Punkt kann für Sie relevant werden, wenn ein solches System die Wettkampfzeit
mit einer echten Uhrzeit in Beziehung setzen soll. Dann sollte in der
Konfigurationsdatei der Bridge die Zeitzone der Veranstaltung stehen:

```properties
competition.timezone=Europe/Berlin
```

Das ist **optional** und nur für diesen Fall gedacht. Für Uhr, Ergebnisse,
Startliste und die Anzeige im Browser spielt es keine Rolle. Wer das einrichtet,
findet die Einzelheiten in [INSTALLATION.md](INSTALLATION.md) und
[API.md](API.md).

## Weiterführende Dokumentation

| Dokument | Inhalt |
|---|---|
| [API.md](API.md) | Schnittstellen für andere Systeme, für Entwickler |
| [QUICKSTART_CLOUD.md](QUICKSTART_CLOUD.md) | Server im Internet Schritt für Schritt |
| [INSTALLATION.md](INSTALLATION.md) | technische Installationsreferenz, Pfade, Dienste, Deinstallation |
| [DEVELOPMENT.md](DEVELOPMENT.md) | Build und Entwicklungsbetrieb |
| [../README.md](../README.md) | Überblick, Projektstatus, Sicherheitshinweise, Lizenz |
