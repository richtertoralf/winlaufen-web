# WinLaufen Web

WinLaufen Web ist eine kleine read-only Bridge für die WinLaufen
Sprecher-PC-Schnittstelle. Sie liest TCP/4444, hält den aktuellen Wettkampfstand
im Speicher und stellt ihn als responsives Dashboard und Ergebnis-Renderer im
lokalen Netzwerk bereit.

## Voraussetzungen

- Java 25
- zum Bauen: Maven 3.9+
- WinLaufen mit aktivierter Sprecher-PC-Schnittstelle im selben LAN

Es werden keine Datenbank, kein Node.js, kein Docker und keine Internetverbindung
zur Laufzeit benötigt.

## Bauen und starten

```sh
mvn test
mvn package
java -jar target/winlaufen-web.jar
```

Danach öffnen:

- Dashboard: `http://localhost:8080/`
- Renderer: `http://localhost:8080/renderer`

Von einem anderen Gerät im LAN wird `localhost` durch die IP oder den Hostnamen
des Bridge-Rechners ersetzt, zum Beispiel
`http://192.168.1.30:8080/renderer`.

HTTP bindet standardmäßig an `0.0.0.0:8080`, WebSocket an `0.0.0.0:8081`.
Ein belegter Port ist ein Startfehler; es wird kein Ersatzport gewählt.

## Betriebsarten

- Gleicher Windows-PC: im Dashboard `localhost` als WinLaufen-Quelle eintragen.
- Separater Bridge-PC (Windows oder Linux): IP/Hostname des WinLaufen-PCs
  eintragen. Auf dem WinLaufen-PC muss keine Bridge installiert werden.

`LOCAL` ist in v0.1 aktiv. `SELFHOST` und `RICHTER_PROJECTS` sind bereits im
Modell und Dashboard sichtbar, bleiben aber deaktiviert und öffnen keine
Netzwerkverbindungen.

## Konfiguration

Die Konfiguration liegt unter
`${user.home}/.winlaufen-web/config.properties` und kann über das Dashboard
geändert werden. Eine geänderte WinLaufen-Adresse löst einen kontrollierten
Reconnect aus. Der WinLaufen-Port ist fest `4444`.

Optional können vor dem Start in der Properties-Datei `http.port` und
`websocket.port` gesetzt werden. Werden diese geändert, veröffentlicht
`GET /api/v1/config` den WebSocket-Port an die Browseroberfläche.

Unter „Öffentliche Darstellung“ lässt sich festlegen, ob die exakt von
WinLaufen gelieferten Spalten `Verein`, `Vbd`, `Nation` und `Schießen` im
Publikumsrenderer erscheinen. WinLaufen-Sprecher-PC-Nachrichten können für eine
kompakte öffentliche Hinweisleiste freigeschaltet werden; standardmäßig sind sie
ausgeblendet. Diese Optionen filtern nur die Darstellung. Der interne State
bleibt vollständig erhalten.

## Live connection smoke test

The development smoke test checks a live WinLaufen connection on TCP port 4444,
the Java Serialization stream header, and at least five structurally recognizable
WinLaufen clock telegrams. Equal, backward or unusual numeric values are accepted:
the test checks receipt, not progression. It is strictly read-only and sends no
application data to WinLaufen.

```sh
./devtools/smoke-winlaufen-clock.sh HOST [PORT]
```

For example, use `./devtools/smoke-winlaufen-clock.sh 192.168.1.20`; the port
defaults to 4444.

## Unterstützt und bekannte Grenzen

v0.1 rendert die vom Protokoll gelieferten Tabellen ohne fest verdrahtetes
Sportartschema. Verifiziert sind Lauf und Biathlon einschließlich späterer
Änderungen vorhandener Zeilen und Biathlon-Schießwerten. Ein separates
Sportartfeld existiert nicht zuverlässig und wird daher nicht geraten.

Noch nicht unterstützt sind WinSpringen, Startlisten/Teilnehmerdaten,
unverifizierte Runden-/Zwischenstandslogik und Remote-Outputs. Der
Startlistenbereich erklärt diese Protokollgrenze ausdrücklich. Wettkampfdaten
werden nur im Arbeitsspeicher gehalten.

Der Renderer richtet sich an Zuschauer auf Smartphone, Tablet und Desktop und
zeigt immer genau eine kompakte Hauptansicht: `Startliste`, `LIVE` oder
`Ergebnisse`.
