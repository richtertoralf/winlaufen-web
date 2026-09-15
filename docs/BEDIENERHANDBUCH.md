# Sprecher-Web — Bedienerhandbuch

**Live-Ergebnisse aus WinLaufen**

Diese Anleitung richtet sich an Vereine und Veranstalter. Sie führt von der
Einrichtung bis zum Betrieb an einem Wettkampftag; die Installation ist verlinkt. Sie
brauchen dafür keine Programmierkenntnisse und müssen keine Architektur- oder
Entwicklerdokumente lesen.

Für Installation, Updates und technische Störungen verwenden Sie
[INSTALLATION.md](INSTALLATION.md). Alle Zielgruppen finden ihren Einstieg in der
[Dokumentationsübersicht](INDEX.md).

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

WinLaufen bleibt auf dem Windows-PC. Für Sprecher-Web auf einem separaten
Linux-PC benötigen Sie eines der [unterstützten Systeme](INSTALLATION.md#unterstützte-plattformen).
Fertige Pakete gibt es für Windows x64 und Linux amd64; die benötigte
Java-Umgebung ist enthalten. Für Raspberry Pi gibt es noch kein fertiges Paket.


---

## 3. Welche Installation passt zu Ihnen?

Für die meisten Vereine passt **All-in-One auf dem WinLaufen-PC**.
All-in-One kann auch auf einem separaten Rechner im selben Netz laufen.
Die [Variantenübersicht](../README.md#welche-variante-brauche-ich) hilft bei der
Auswahl; die [Installationsprofile](INSTALLATION.md#4-installationsprofile)
beschreiben die Rollen für technisch versierte Anwender.

## 4. Windows: All-in-One installieren

Folgen Sie der [Windows-Installationsanleitung](INSTALLATION.md#12-windows-11).
Sie erklärt den gesamten Weg vom Download über das Entpacken und Kopieren
des Ordnerpfads bis zum Starten des Installers mit Administratorrechten.
Wählen Sie **AllInOne**. Danach geht es hier mit Kapitel 6 weiter.

Für eine neuere Version folgen Sie dem [Upgrade-Ablauf](INSTALLATION.md#3-upgrade).
Sprecher-Web startet nach erfolgreicher Installation automatisch mit Windows.

## 5. Linux: All-in-One oder Bridge only installieren

Folgen Sie der [Linux-Installationsanleitung](INSTALLATION.md#11-linux).
Wählen Sie **all-in-one**, wenn dieser Rechner auch die Ergebnisseite
bereitstellen soll, oder **bridge-only**, wenn bereits ein separater
Live-Server vorgesehen ist. Danach geht es hier mit Kapitel 6 weiter.

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

Für die Vorbereitung darf **Auf einem anderen Computer** mit leerer Adresse
bereits mit **Speichern** übernommen werden. Dieser Zustand bleibt nach Neuladen
und Neustart erhalten und zeigt **WinLaufen noch nicht konfiguriert**. Die Bridge
startet dabei keine WinLaufen-Verbindung. Importierte Startlisten und konfigurierte
Live-Server funktionieren unabhängig davon; die Startliste wird auch an später
hinzugefügte Server übertragen. Ergänzen Sie die WinLaufen-Adresse später und
speichern Sie erneut.

Mit **Übertragungsziele speichern** speichern Sie nur die Live-Server-Targets.
Quelle und öffentliche Darstellung werden dabei nicht geändert. Targets zeigen
**Nicht gespeichert**, solange sie nur im Formular stehen oder geändert wurden.
**Gespeichert · Noch kein Status** bedeutet, dass das Target gespeichert ist,
aber noch kein Laufzeitstatus vorliegt.

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
| **Der Browser wird geschlossen** | — | Nichts. Andere Browser und WinLaufen laufen unverändert weiter. |

Der Neustart der Bridge verdient eine Erklärung: Nach einem Neustart der Bridge kennt
Sprecher-Web zuerst nur die Wettkampfzeit wieder, noch nicht die Ergebnisliste
— WinLaufen sendet Ergebnisse nur, wenn sich etwas ändert. Die zuletzt
bekannten Ergebnisse bleiben deshalb bewusst stehen, bis der nächste
Zieleinlauf sie ersetzt.

---

## 12. Zusätzlicher Live-Server im Internet

Ein temporärer Internetserver kann die Ergebnisse auch Zuschauern außerhalb
des Veranstaltungsnetzes zugänglich machen. Das erfordert Netzwerkkenntnisse.
Die vollständige Einrichtung steht ausschließlich in der
[Cloud-Kurzanleitung](QUICKSTART_CLOUD.md).

Dabei sind auch importierte Teilnehmerdaten öffentlich lesbar. Vor dem Einsatz
müssen die [Einsatzgrenzen](INSTALLATION.md#einsatzgrenzen) geprüft werden.


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
| Installer bricht mit `PSSecurityException` ab | [Skriptausführung prüfen](INSTALLATION.md#installer-starten). |
| Installer meldet fehlende Administratorrechte | PowerShell nicht über „Als Administrator ausführen" gestartet. |
| Bridge Control zeigt dauerhaft „Nicht verbunden" | In WinLaufen fehlt **Abwicklung → Sprecher-PC… → Verbinden**, oder die eingetragene Adresse des WinLaufen-PCs stimmt nicht. |
| Ergebnisseite lokal erreichbar, aus dem WLAN nicht | Netzwerkprofil oder Firewall prüfen; nur ein vertrauenswürdiges Netz als „Privat“ einstufen. [Netzwerkhilfe](INSTALLATION.md#fehlerdiagnose). |
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
[Installationsreferenz](INSTALLATION.md#einsatzgrenzen).

---

## Andere Systeme anbinden

Die dokumentierte Schnittstelle steht zusätzlich zur Browseranzeige bereit.
Für deren Nutzung wenden sich Entwickler und Integratoren an [API.md](API.md).

Für Veranstaltungen in Deutschland müssen Sie keine Zeitzone einstellen:
`Europe/Berlin` und die Anzeige „Standard für WinLaufen“ sind der Normalfall.
Für Veranstaltungen im Ausland beschreibt die
[Installationsreferenz](INSTALLATION.md#wettkampf-zeitzone) die Einstellung.

## Protokollanzeige in Bridge Control

Im WinLaufen-Verbindungsstatus zeigt Bridge Control „Protokoll: Erkennung läuft“,
bis ein eindeutiges Telegramm eingetroffen ist. Danach steht dort „Protokoll:
Aktuell“ oder „Protokoll: Legacy“. Bei Legacy erscheint zusätzlich:
„Legacy-Protokoll erkannt – Update auf WinLaufen 18+ empfohlen.“
Die unterstützten Wettkampfdaten werden weiterhin verarbeitet. Die Anzeige
bezeichnet das Protokollformat, keine zuverlässig erkannte Versionsnummer.
Nach einem Verbindungsneuaufbau beginnt die Erkennung erneut.
Diese Diagnose bleibt in Bridge Control; die Live-Ergebnisseite und externe
Verbraucher erhalten sie nicht.

## Weiterführende Dokumentation

| Dokument | Inhalt |
|---|---|
| [API.md](API.md) | Schnittstellen für andere Systeme, für Entwickler |
| [QUICKSTART_CLOUD.md](QUICKSTART_CLOUD.md) | Server im Internet Schritt für Schritt |
| [INSTALLATION.md](INSTALLATION.md) | technische Installationsreferenz, Pfade, Dienste, Deinstallation |
| [DEVELOPMENT.md](DEVELOPMENT.md) | Build und Entwicklungsbetrieb |
| [../README.md](../README.md) | Produkteinstieg, Varianten, Schnellinstallation, Lizenz |
