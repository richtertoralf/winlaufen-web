# WinLaufen Web — Manuelle Abnahmetests

Diese Szenarien lassen sich nicht sinnvoll automatisieren, weil sie echte
Windows-Rechner, mehrere Maschinen und eine reale WinLaufen-Installation
benötigen. Sie sind vor dem ersten produktiven Einsatz durchzuführen.

Was bereits automatisiert geprüft ist, steht in
[INSTALLATION.md](INSTALLATION.md) und wird durch
`installer/tests/run-installer-tests.sh` sowie `mvn test` abgedeckt.

Erwartete Endpunkte in allen Szenarien:

```text
Bridge Control:  http://localhost:8090/
Web View:        http://<live-server>:8080/
Browser-Live:    ws://<live-server>:8081/live/v1
Bridge-Ingest:   ws://<live-server>:8081/bridge/v1/channels/local
WinLaufen:       <winlaufen-host>:4444   (read-only)
```

---

## A — Windows All-in-One

```text
WinLaufen + Bridge + Live Server
auf demselben Windows-11-PC
```

1. `.\installer\windows\Install-WinLaufenWeb.ps1` als Administrator, Profil
   `All-in-One` (Standardauswahl bestätigen).
2. Prüfen, dass der Installer **keine** Netzwerkadresse abfragt.
3. WinLaufen starten.
4. `http://localhost:8090/` öffnen: Source-Health wechselt auf `CONNECTED`,
   die WinLaufen-Uhr läuft, das Target `local` steht auf `CONNECTED` mit
   fortschreitender ACK-Revision.
5. `http://localhost:8080/` öffnen: LIVE zeigt die aktuelle Klasse, die
   Current-Finish-Zeile ist hervorgehoben.
6. **Ohne jede weitere Konfiguration** muss dieser Zustand erreicht werden.
7. PC neu starten. Nach dem Boot und **ohne Anmeldung an einer Konsole** müssen
   beide Aufgaben wieder laufen:
   `Get-ScheduledTaskInfo -TaskName 'WinLaufen Web Bridge'`.
8. Web View erneut öffnen: Daten laufen wieder.

---

## B — Linux All-in-One

```text
WinLaufen-PC
      |
      v
Linux Bridge + Live Server
```

1. `sudo ./installer/linux/install.sh --profile all-in-one`.
2. `systemctl status winlaufen-bridge winlaufen-live-server`: beide `active`.
3. Bridge Control öffnen. Source-Health steht zunächst auf `DISCONNECTED`,
   weil WinLaufen nicht lokal läuft — das ist der erwartete Zustand.
4. **Nur** den WinLaufen-Host in Bridge Control auf die Adresse des
   WinLaufen-PCs ändern und speichern.
5. Source-Health wechselt auf `CONNECTED`, ohne Neustart eines Dienstes.
6. Web View auf `http://<linux-host>:8080/` prüfen, auch von einem zweiten
   Gerät im LAN.
7. Rechner neu starten, Schritte 2 und 6 wiederholen.

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
   nicht als fehlerhaft melden, obwohl noch kein Target existiert.
2. Prüfen: `systemctl status winlaufen-live-server` meldet „not found" — auf
   diesem Rechner läuft bewusst kein Live Server.
3. Auf Rechner 2: `sudo ./installer/linux/install.sh --profile presentation-node`.
   Der Installer darf **nicht** nach einer Bridge-Adresse fragen und zeigt am
   Ende die lokalen IP-Adressen als Hinweis.
4. In Bridge Control auf Rechner 1:
   * WinLaufen-Host auf den WinLaufen-PC setzen,
   * ein Output Target anlegen:
     `ws://<rechner-2>:8081/bridge/v1/channels/local`, Channel `local`,
     Secret wie in `/etc/winlaufen-web/live-server.env` auf Rechner 2.
5. Target wechselt auf `CONNECTED`, ACK-Revision steigt.
6. Web View auf `http://<rechner-2>:8080/` zeigt die Daten.

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
bekanntes Ingest-Secret. Port 8081 darf während der Tests nicht aus einem nicht
vertrauenswürdigen Netz erreichbar sein. Details in README.md, Abschnitt
„Known prototype security limitation".
