# Kurzanleitung: Live-Ergebnisse über einen Cloud-Server

Diese Anleitung beschreibt einen bewusst einfachen Fall: Ein Verein mietet für
einige Stunden einen kleinen Server im Internet und zeigt darüber die
Live-Ergebnisse. Zuschauer brauchen dann kein WLAN vor Ort, sondern rufen die
Ergebnisse von überall auf.

Eine eigene Domain ist dafür **nicht** nötig. Es genügt die öffentliche
IPv4-Adresse des gemieteten Servers.

Vorher lesen: [Verbindliche Einsatzgrenzen dieser Prototypversion](../README.md#known-prototype-security-limitation).
Die Übertragung ist in diesem einfachen Betrieb unverschlüsselt.

Diese Anleitung ergänzt das [Bedienerhandbuch](BEDIENERHANDBUCH.md); die
Installation im Vereinsnetz und die Bedienung von Bridge Control stehen dort.

## Was wo läuft

```text
WinLaufen-PC  ->  Bridge im Vereinsnetz  ->  Cloud-Server  ->  Zuschauer
                  (Ihr Notebook)             (gemietet)        (Browser)
```

Der Cloud-Server bekommt das Profil **Presentation Node**. Ihre Bridge im
Vereinsnetz bleibt, wie sie ist, und schickt die Ergebnisse von sich aus dorthin.

## Schritt für Schritt

**1. Cloud-Server erstellen**

Beim Anbieter Ihrer Wahl eine kleine VM mit **Ubuntu 24.04** und öffentlicher
IPv4-Adresse anlegen. Die kleinste Größe genügt. Notieren Sie die IP-Adresse,
zum Beispiel `203.0.113.7`.

**2. Auf dem Cloud-Server anmelden und Git und Java installieren**

```sh
sudo apt update
sudo apt install git openjdk-25-jdk
```

**3. Sprecher-Web holen und bauen**

```sh
git clone https://github.com/richtertoralf/winlaufen-web.git
cd winlaufen-web
./mvnw clean package
```

**4. Installieren**

```sh
sudo ./installer/linux/install.sh
```

Bei der Frage nach der Rolle des Rechners das Profil **[3] Presentation Node**
wählen. Es wird nach keiner Adresse gefragt.

**5. Firewall des Anbieters öffnen**

In der Firewall Ihres Cloud-Anbieters eingehend freigeben:

| Port | Wofür |
|---|---|
| TCP 44440 | Live-Ergebnisse im Browser |
| TCP 44441 | Verbindung Ihrer Bridge zum Server |
| TCP 22 | nur, soweit Sie SSH zur Administration brauchen |

**TCP 44442 wird nicht freigegeben.** Das ist Bridge Control; es hat keine
Anmeldung und läuft ohnehin nur auf Ihrem eigenen Rechner im Vereinsnetz.

**6. Bridge im Vereinsnetz mit dem Cloud-Server verbinden**

Bridge Control im Browser öffnen: `http://<ihre-bridge-ip>:44442/`

Dann:

1. **Weiteren Live-Server verbinden** anklicken,
2. die IP-Adresse des Cloud-Servers eintragen, zum Beispiel `203.0.113.7`,
3. **Speichern**.

Mehr ist nicht nötig. Alles Weitere wird automatisch gesetzt.

Kurz darauf sollte am neuen Ziel **Verbunden** stehen. Darunter erscheint der
Hinweis, dass unverschlüsselt übertragen wird — das ist in diesem Betrieb so
gewollt.

**7. Live-Ergebnisse öffnen und weitergeben**

```text
http://203.0.113.7:44440/
```

Diese Adresse können Sie an Zuschauer weitergeben, zum Beispiel als Link oder
QR-Code am Zielbereich.

**8. Nach der Veranstaltung**

Den gemieteten Server **abschalten oder löschen**, wenn er nur für diesen Tag
gebraucht wurde. Das beendet die Kosten und den offenen Zugang zugleich.

## Was Sie wissen sollten

Die Verbindung zwischen Ihrer Bridge und dem Cloud-Server ist in diesem
einfachen Betrieb **unverschlüsselt**, und der Verbindungsschlüssel ist ein
öffentlich bekannter Standardwert.

Daraus folgt zweierlei:

* Auf dem Weg können sowohl die **übertragenen Daten** als auch der
  **Verbindungsschlüssel** mitgelesen werden.
* Wer den Verbindungsschlüssel kennt oder mitliest und Port 44441 erreicht, kann
  unter Umständen **unerwünschte Daten einspeisen** — eingespielte Ergebnisse
  sähen für alle Zuschauer echt aus.

Für einen temporären Selfhost- oder Testserver, der nur für die Dauer einer
Veranstaltung läuft, ist dieser bewusst einfache Betrieb vertretbar. Für einen
dauerhaften oder zentral betriebenen Dienst ist verschlüsselte Übertragung
vorgesehen; dafür sind eine Domain, `wss://` mit Zertifikat und ein eigener
Verbindungsschlüssel erforderlich.

Wenn Sie das Risiko verringern möchten, setzen Sie auf dem Cloud-Server in
`/etc/winlaufen-web/live-server.env` einen eigenen Wert für
`WINLAUFEN_LIVE_SECRET`, starten den Dienst neu und tragen denselben Wert in
Bridge Control unter **Erweiterte Einstellungen** als Verbindungsschlüssel ein.

## Wenn etwas nicht funktioniert

| Beobachtung | Woran es meist liegt |
|---|---|
| Web View nicht erreichbar | TCP 44440 in der Firewall des Anbieters nicht freigegeben |
| Ziel bleibt „Nicht erreichbar" | TCP 44441 in der Firewall des Anbieters nicht freigegeben |
| Ziel lässt sich nicht speichern | Adresse mit `http://` oder Port eingetragen — nur die IP-Adresse eintragen |
| Seite bleibt leer | Die Bridge hat noch keine Verbindung zu WinLaufen; siehe [Bedienerhandbuch, Kapitel 6](BEDIENERHANDBUCH.md#6-winlaufen-verbinden) |

Weitere Hilfe: [BEDIENERHANDBUCH.md](BEDIENERHANDBUCH.md),
[INSTALLATION.md](INSTALLATION.md) und [DEVELOPMENT.md](DEVELOPMENT.md).
