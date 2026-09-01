# Sprecher-Web — Manuelle Abnahmetests

Diese Szenarien lassen sich nicht sinnvoll automatisieren, weil sie echte
Windows-Rechner, mehrere Maschinen und eine reale WinLaufen-Installation
benötigen. Sie sind vor dem ersten produktiven Einsatz durchzuführen.

Was bereits automatisiert geprüft ist, steht in
[INSTALLATION.md](INSTALLATION.md) und wird durch
`installer/tests/run-installer-tests.sh` sowie `./mvnw test` abgedeckt.

Ein Szenario mit WinLaufen setzt voraus, dass die Sprecher-PC-Schnittstelle in
WinLaufen unter **Abwicklung → Sprecher-PC… → Verbinden** aktiviert wurde. Vorher
ist `Nicht verbunden` der erwartete Zustand und kein Fehler.

Erwartete Endpunkte in allen Szenarien:

```text
Bridge Control:  http://<bridge>:44442/
Live-Ergebnisse: http://<live-server>:44440/
Browser-Live:    ws://<live-server>:44441/live/v1
Bridge-Ingest:   ws://<live-server>:44441/bridge/v1/channels/local
WinLaufen:       <winlaufen-host>:4444   (read-only)
```

TCP 44440/44441 müssen für die vorgesehenen Viewer im LAN/WLAN erreichbar
sein, TCP 44442 für die vorgesehenen Administrationsgeräte. TCP 4444 ist nur
die ausgehende Bridge-Verbindung zum WinLaufen-PC.

Für alle Installationsszenarien gilt: Nur Fehler der lokalen Installation
(Dienst/Task, eigener Listener, lokaler HTTP-Endpunkt oder benötigte Datei)
müssen den Installer scheitern lassen. Eine nicht verbundene WinLaufen-Quelle
oder ein nicht erreichbares Output Target wird im Abschlussbericht diagnostiziert
und darf weder Exit-Code 0 noch die Erfolgsmeldung verhindern. Beim lokalen
All-in-One-Target ist `DISCONNECTED` eine deutlich sichtbare Warnung, aber kein
Installationsfehler.

---

## A — Windows All-in-One

```text
WinLaufen + Bridge + Live Server
auf demselben Windows-11-PC
```

1. `.\installer\windows\Install-WinLaufenWeb.ps1` als Administrator, Profil
   `All-in-One` (Standardauswahl bestätigen).
2. Prüfen, dass der Installer **keine** Netzwerkadresse abfragt.
3. Prüfen, dass die drei Regeln `WinLaufenWeb-HTTP-44440`,
   `WinLaufenWeb-WebSocket-44441` und `WinLaufenWeb-BridgeControl-44442` nur für
   Private-/Domain-Netze existieren. Keine Public-Regel darf angelegt sein.
4. WinLaufen starten.
5. `http://localhost:44442/` öffnen: Source-Health wechselt auf `CONNECTED`,
   die WinLaufen-Uhr läuft, das Target `local` steht auf `CONNECTED` mit
   fortschreitender ACK-Revision.
6. `http://localhost:44440/` öffnen: LIVE zeigt die aktuelle Klasse, die
   Current-Finish-Zeile ist hervorgehoben.
7. Web View und Bridge Control zusätzlich von einem vorgesehenen LAN-Gerät über
   die Host-IP aufrufen.
8. **Ohne jede weitere Konfiguration** muss dieser Zustand erreicht werden.
9. PC neu starten. Nach dem Boot und **ohne Anmeldung an einer Konsole** müssen
   beide Aufgaben wieder laufen:
   `Get-ScheduledTaskInfo -TaskName 'WinLaufen Web Bridge'`.
10. Live-Ergebnisse erneut öffnen: Daten laufen wieder.

### Protokoll: realer Windows-E2E-Nachweis vom 30.08.2026

Dies ist ein realer **Developer-/Source-E2E-Test**, **keine** abschließende
Endanwender-Fresh-Install-Abnahme.

Umgebung:

```text
Windows 11 x64
WinLaufen
Sprecher-Web 0.2.0-SNAPSHOT
Profil AllInOne
System-Java 25 (Developer-/Source-Test, keine gebündelte Runtime)
```

Real bestätigt:

| # | Nachweis |
|---|---|
| 1 | Installer läuft erfolgreich durch |
| 2 | geplante Aufgabe `WinLaufen Web Bridge` läuft |
| 3 | geplante Aufgabe `WinLaufen Web Live Server` läuft |
| 4 | TCP 44440 lauscht |
| 5 | TCP 44441 lauscht |
| 6 | TCP 44442 lauscht |
| 7 | HTTP auf 44440 liefert 200 |
| 8 | HTTP auf 44442 liefert 200 |
| 9 | das lokale interne Target ist verbunden |
| 10 | WinLaufen ohne aktivierte Sprecher-PC-Verbindung wird nur als Warnung behandelt, nicht als Installationsfehler |
| 11 | nach **Abwicklung → Sprecher-PC… → Verbinden** verbindet sich die Bridge |
| 12 | reale Ergebnisdaten aus WinLaufen erscheinen in den Live-Ergebnissen |
| 13 | Zugriff von einem zweiten Linux-Rechner im LAN erfolgreich |
| 14 | TCP 44440 und 44442 waren vom zweiten Rechner erreichbar |
| 15 | Live-Ergebnisse waren im Browser über die LAN-IP sichtbar |

Die in jenem Netz verwendete Adresse `http://192.168.95.198:44440/` ist ein
Beispiel aus dieser Umgebung und kein Vorgabewert.

Damit ist der Windows-AllInOne-Pfad von WinLaufen bis in den Browser eines
anderen Geräts im LAN praktisch bestätigt.

### Protokoll: Public-IPv4-Presentation-Node und Ausfallverhalten, 01.09.2026

Realer Test des Meilensteins `a3ba398` auf Branch `feat/public-ip-selfhost`.
Ebenfalls ein Developer-/Source-Test, keine Endanwender-Fresh-Install-Abnahme.

Umgebung:

```text
<winlaufen-pc>            Windows 11, WinLaufen mit Sprecher-PC-Schnittstelle
<bridge-host>             Linux, Profil All-in-One, im selben LAN
<presentation-node>       Ubuntu 24.04 Cloud-VM, oeffentliche IPv4, Profil [3]
<browser>                 Zuschauergeraet ausserhalb des Veranstaltungsnetzes
```

Die tatsächlich verwendeten Adressen sind bewusst nicht protokolliert: Die
gemietete öffentliche IPv4 gehört nach der Rückgabe einem anderen Server und
wäre als dauerhafte Angabe in der Projektdokumentation irreführend.

Real bestätigt:

| # | Nachweis |
|---|---|
| 1 | Windows-All-in-One-Installation und -Upgrade aus dem Source Checkout |
| 2 | Verbinden und Trennen der Sprecher-PC-Schnittstelle wird korrekt erkannt |
| 3 | Presentation Node mit öffentlicher IPv4 installiert; Web View und Ingest erreichbar |
| 4 | SELFHOST-Target in Bridge Control allein über die IP-Adresse angelegt |
| 5 | Live Server stop/start: Browser meldet den Ausfall und verbindet ohne Reload neu |
| 6 | kompletter Reboot des Presentation Node: gleiches Verhalten, Erkennung über das ausbleibende Lebenszeichen |
| 7 | Browser-Verbindung und WinLaufen-Quellenlage werden getrennt angezeigt |
| 8 | Wettkampfzeit bleibt bei jedem Ausfall stehen und wird nie lokal weitergezählt |
| 9 | nach dem Reconnect erscheint exakt der neu von WinLaufen gelieferte Zeitwert |
| 10 | Bridge stop/start: Quelle wird `DISCONNECTED`, der letzte Ergebnisstand bleibt sichtbar |
| 11 | nach dem Bridge-Neustart bleiben die bisherigen Ergebnisse sichtbar, bis WinLaufen einen neuen Klassensnapshot liefert |

Offen aus diesem Testlauf, siehe README, Abschnitt „Bekannte technische Punkte
für den nächsten Arbeitsblock": belegter TCP-Port 4444 im `WinLaufenClient`-Test,
Umlautdarstellung des Installers unter Windows PowerShell 5.1 und die Frage, ob
ein Upgrade bei aktiver Sprecher-PC-Verbindung zuverlässig funktioniert.

### Windows-Reinstall, Legacy-Migration und Fehlerfall (manuell offen)

Diese Prüfung muss auf einem echten Windows-11-System mit Windows PowerShell
und Scheduled Tasks erfolgen; die Linux-CI führt den Windows-Installer nicht
aus.

1. Von einer funktionierenden All-in-One-Installation aus die Konfiguration
   einmal über Bridge Control speichern. Prüfen, dass die Datei CRLF-Zeilenenden
   und das von `java.util.Properties.store()` erzeugte Escaping wie
   `ws\://127.0.0.1\:8081/bridge/v1/channels/local` besitzt.
2. Mit einer Sicherungskopie testen, dass beim Reinstall nur dieser exakte alte
   lokale Default auf 44441 migriert wird. Ein anderer Host, ein anderer Port
   und ein bereits auf 44441 stehender Endpunkt müssen unverändert bleiben.
3. Den Reinstall mit LF- und mit CRLF-Zeilenenden wiederholen. Eine
   Migrationsmeldung darf nur bei einer tatsächlichen Änderung erscheinen.
4. Aus der laufenden, funktionierenden Installation einen reversiblen Fehler
   einmal vor dem Stop der Tasks provozieren: die bisherigen Tasks müssen
   unverändert weiterlaufen.
5. Einen kontrollierten, reversiblen Fehler nach dem Stop provozieren. Prüfen,
   dass der Installer den Neustart der zuvor laufenden Tasks versucht und keine
   Erfolgsmeldung ausgibt.
6. Prüfen, dass Firewallregeln erst nach erfolgreicher Runtime-Validierung
   synchronisiert werden und ein Profilwechsel All-in-One → Bridge only
   weiterhin funktioniert.

---

## B — Linux All-in-One

```text
WinLaufen-PC
      |
      v
Linux Bridge + Live Server
```

1. `sudo ./installer/linux/install.sh --profile all-in-one`.
2. Ohne laufenden WinLaufen-PC muss der Installer Exit-Code 0 liefern und im
   Abschlussbericht die erfolgreiche lokale Installation getrennt von
   `WinLaufen-Quelle: DISCONNECTED` melden.
3. `systemctl status winlaufen-bridge winlaufen-live-server`: beide `active`.
   Falls das lokale Target noch nicht `CONNECTED` ist, muss der Installer dies
   deutlich warnend, aber nicht als Installationsfehler melden.
4. Bridge Control öffnen. Source-Health steht zunächst auf `DISCONNECTED`,
   weil WinLaufen nicht lokal läuft — das ist der erwartete Zustand.
5. **Nur** den WinLaufen-Host in Bridge Control auf die Adresse des
   WinLaufen-PCs ändern und speichern.
6. Source-Health wechselt auf `CONNECTED`, ohne Neustart eines Dienstes.
7. Live-Ergebnisse auf `http://<linux-host>:44440/` prüfen, auch von einem zweiten
   Gerät im LAN.
8. Bridge Control auf `http://<linux-host>:44442/` von einem vorgesehenen
   Administrationsgerät im LAN prüfen.
9. Rechner neu starten, Schritte 3, 7 und 8 wiederholen.

---

## C — Linux Bridge only

```text
WinLaufen
   |
   v
Linux Bridge
   |
   v
separater Presentation Node
```

1. Auf Rechner 1: `sudo ./installer/linux/install.sh --profile bridge-only`.
   Der Installer darf **nicht** nach einem Ziel fragen und die Installation
   nicht als fehlerhaft melden, obwohl WinLaufen und ein vorhandenes Target
   noch nicht verbunden sind. Beide Zustände müssen nur diagnostisch erscheinen.
2. Prüfen: `systemctl status winlaufen-live-server` meldet „not found" — auf
   diesem Rechner läuft bewusst kein Live Server.
3. Auf Rechner 2: `sudo ./installer/linux/install.sh --profile presentation-node`.
   Der Installer darf **nicht** nach einer Bridge-Adresse fragen und zeigt am
   Ende die lokalen IP-Adressen sowie den Hinweis „wartet auf eine Bridge" oder
   eine gleichwertige Aussage.
4. In Bridge Control auf Rechner 1:
   * WinLaufen-Host auf den WinLaufen-PC setzen,
   * über **Weiteren Live-Server verbinden** die IP-Adresse von Rechner 2
     eintragen. Daraus müssen
     `ws://<rechner-2>:44441/bridge/v1/channels/local`, Channel `local` und
     eine deterministische ID entstehen; der Verbindungsschlüssel entspricht
     dem Standardwert aus `/etc/winlaufen-web/live-server.env` auf Rechner 2.
     Ein abweichender Schlüssel wird unter Erweiterte Einstellungen eingetragen.
5. Target wechselt auf `CONNECTED`, ACK-Revision steigt.
6. Live-Ergebnisse auf `http://<rechner-2>:44440/` zeigen die Daten.

---

## D — Windows Bridge only

```text
WinLaufen / Windows
       |
       v
Windows Bridge
       |
       v
Linux Presentation Node
```

1. Auf dem Windows-PC:
   `.\installer\windows\Install-WinLaufenWeb.ps1 -Profile BridgeOnly`.
2. Prüfen, dass nur die Aufgabe `WinLaufen Web Bridge` existiert und **keine**
   Aufgabe `WinLaufen Web Live Server`.
3. Auf dem Linux-Rechner: Presentation Node installieren (wie in C).
4. In Bridge Control auf dem Windows-PC das Linux-Ziel als Output Target
   eintragen.
5. Web View auf dem Linux-Rechner prüfen.
6. Windows-PC neu starten: die Bridge-Aufgabe muss ohne Anmeldung wieder
   laufen und das Target erneut verbinden.

---

## E — Fan-out

```text
Bridge
   |
   +----> Presentation Node LAN
   |
   +----> zweiter Presentation Node
```

1. Zwei Presentation Nodes installieren.
2. Beide auf einer Bridge als Output Targets eintragen.
3. Beide Targets müssen `CONNECTED` melden und dieselbe ACK-Revision erreichen.
4. Beide Web Views zeigen denselben Wettkampfstand.

---

## Reconnect, Neustart und Full Resync

Diese Prüfungen ergänzen die Szenarien C, D und E.

### Ausfall eines Targets isoliert

1. Presentation Node A stoppen (`sudo systemctl stop winlaufen-live-server`).
2. In Bridge Control muss **nur** Target A nach `RETRY_WAIT` wechseln.
3. Source-Health bleibt `CONNECTED`, Target B bleibt `CONNECTED` und seine
   ACK-Revision steigt weiter.
4. Web View auf Node B zeigt weiterhin aktuelle Daten.

### Retry-Verhalten

Der Retryzähler von Target A darf nur etwa alle 10 Sekunden steigen, nicht im
Takt der WinLaufen-Uhr. Die Kurve ist: sofort, nach 2 s, nach 5 s, danach alle
10 s.

### Full Resync nach Neustart

1. Während Node A gestoppt ist, in Bridge Control die Presentation Config
   ändern (zum Beispiel „Nation anzeigen" umschalten).
2. Node A wieder starten.
3. Node A muss ohne Delta-Historie den **aktuellen Vollsnapshot** erhalten:
   gleicher Wettkampfstand wie Node B **und** die zwischenzeitlich geänderte
   Presentation Config.
4. Beide Targets zeigen anschließend dieselbe ACK-Revision.

### Ausfall der Quelle

1. WinLaufen beenden.
2. Source-Health wechselt nach spätestens 4 Sekunden auf `STALE`, danach auf
   `DISCONNECTED`.
3. Der zuletzt bekannte Wettkampfstand bleibt in der Web View sichtbar, der
   Status wird aber als nicht verbunden angezeigt.
4. WinLaufen wieder starten: die Bridge verbindet sich selbständig neu.

### Stummes Target

1. Ein Ziel so blockieren, dass die TCP-Verbindung besteht, aber keine ACKs mehr
   kommen (zum Beispiel den Live-Server-Prozess anhalten:
   `sudo systemctl kill -s STOP winlaufen-live-server`).
2. Nach etwa 15 Sekunden muss das Target in Bridge Control auf `STALE` wechseln
   und darf nicht weiter als gesund erscheinen.
3. Fortsetzen (`sudo systemctl kill -s CONT winlaufen-live-server`): das Target
   kehrt nach `CONNECTED` zurück.

---

## Sicherheitshinweis für alle Szenarien

Diese Version ist ein Prototyp für kontrollierte Netze und verwendet ein
bekanntes Ingest-Secret. Port 44441 darf während der Tests nicht aus einem nicht
vertrauenswürdigen Netz erreichbar sein. Details in README.md, Abschnitt
„Known prototype security limitation".
