# WinLaufen Web — Produktspezifikation

## 1. Zweck

WinLaufen Web ist eine kleine, quelloffene Bridge für WinLaufen.

Sie verbindet sich read-only mit der bestehenden LAN-Schnittstelle des
WinLaufen-Sprecher-PCs und stellt eine moderne, responsive Browseroberfläche für
Wettkampfergebnisse bereit.

Die Open-Source-Version soll einfach genug sein, dass Sportvereine sie ohne
zusätzliche Serverinfrastruktur und ohne laufende Kosten einsetzen können.

## 2. Produktprinzip

WinLaufen bleibt das autoritative Wettkampfsystem.

WinLaufen Web:

- liest Daten aus WinLaufen,
- normalisiert den empfangenen State,
- verteilt vollständige kanonische Snapshots an konfigurierte Live Server,
- veröffentlicht den Published State über HTTP und WebSocket des Live Servers,
- stellt die Daten im Browser dar,
- schreibt niemals Wettkampfdaten nach WinLaufen zurück.

Alle dokumentierten Wettkampfwerte aus WinLaufen sind autoritativ und bleiben
Strings, wo die Wire-Daten Strings liefern. Die Bridge validiert, korrigiert,
normalisiert, interpretiert, sortiert und ersetzt keine Uhrzeiten, Ränge,
Startnummern, Laufzeiten, Rückstände, Schießergebnisse, Namen, Vereine,
Verbände, Tabellenheader, Zellen oder Current-Finish-Werte anhand fachlicher
Plausibilität. Protokollstruktur, Java-Typen, Marker und technische
Ressourcensicherheit werden weiterhin geprüft.

## 3. Unterstützte Deployment-Topologie

Die Bridge muss beide normalen Deployment-Varianten unterstützen.

### 3.1 Separater Bridge-Rechner

Bevorzugter Betriebsaufbau:

WinLaufen läuft auf dem Zeitnahme-Rechner.

WinLaufen Web läuft auf einem anderen Windows- oder Linux-Rechner im selben LAN
und verbindet sich über TCP-Port 4444 mit dem WinLaufen-Rechner.

Auf dem Zeitnahme-Rechner muss keine WinLaufen-Web-Software installiert werden.

### 3.2 Gleicher Rechner

Für Vereine mit nur einem Rechner darf WinLaufen Web direkt auf dem
Windows-Rechner laufen, auf dem auch WinLaufen läuft.

In diesem Fall ist die Quelle normalerweise `localhost:4444`.

Beide Varianten sind gleichwertig unterstützte Konfigurationen. Sie entsprechen
den Installationsprofilen **All-in-One** (gleicher Rechner) und **Bridge only**
bzw. **Presentation Node** (getrennte Rechner); siehe
[INSTALLATION.md](INSTALLATION.md).

## 4. Unterstützte Plattformen

v0.1:

- Windows x64
- Linux amd64 und arm64 (Debian, Ubuntu 24.04/26.04, Raspberry Pi OS)

Der Code muss plattformneutral bleiben, wo immer das technisch möglich ist.

## 5. Unterstützte Sportarten in v0.1

Verifiziert und unterstützt:

- Laufwettkämpfe
- Biathlon

Nicht unterstützt in v0.1:

- WinSpringen / Skispringen

WinSpringen darf nicht aus Annahmen implementiert werden, weil derzeit kein
verifizierter Sprecher-PC-Mitschnitt vorliegt.

## 6. Lokale Webanwendung

Die lokale Webanwendung ist ein vollwertiges Produktmerkmal.

Sie muss funktionieren:

- auf localhost,
- von anderen Geräten im selben LAN,
- ohne Internetzugang,
- mit mehreren Browser-Clients gleichzeitig.

Die modulare lokale Installation stellt dafür bereit:

- eingebetteten HTTP-Server,
- WebSocket-Live-Updates,
- vollständigen Initialsnapshot über WebSocket,
- State-Abruf über HTTP für Diagnose und Fallback,
- Bridge Control auf der Bridge,
- Web View auf dem Live Server.

Lokale Standard-Endpunkte:

- HTTP: `0.0.0.0:8080`
- WebSocket: `0.0.0.0:8081`

Bridge Control verwendet standardmäßig `127.0.0.1:8090`. Ist einer der eigenen
Ports einer Runtime bereits belegt, bricht diese Runtime mit einer klaren
Fehlermeldung ab und wählt keinen Ersatzport.

Beim Upgrade einer vormodularen Konfiguration bleiben WinLaufen-Host,
Presentation-Werte und der frühere LOCAL-Output erhalten; der alte
Browser-WebSocket-Port wird zum Port des lokalen Ingest-Endpunkts. Der alte
HTTP-Port gehört nun zum separaten Live-Server-Prozess und kann nicht in die
Bridge-Konfiguration migriert werden. Die Bridge meldet ihn deshalb beim Start
als Hinweis mit der passenden Live-Server-Option.

## 7. Installation und Runtime-Konfiguration

Installation und Netzwerk-/Runtime-Konfiguration sind strikt getrennt.

Der Installer fragt ausschließlich das Installationsprofil ab: **All-in-One**,
**Bridge only** oder **Presentation Node**. Er fragt zu keinem Zeitpunkt nach
WinLaufen-IP-Adressen, Target-IP-Adressen, Hostnamen, URLs oder WSS-Zielen und
blockiert die Installation nicht, wenn diese Angaben noch unbekannt sind.

All-in-One ist der Standard und muss auf dem WinLaufen-PC ohne weitere
Konfiguration funktionieren: die Bridge erwartet WinLaufen unter `127.0.0.1:4444`
und der lokale Live Server ist als reguläres Output Target vorkonfiguriert.

Eine vorhandene Konfiguration wird bei einer Neuinstallation oder einem Upgrade
nicht überschrieben.

## 8. Bridge Control

Bridge Control konfiguriert und zeigt den Bridge-Zustand.

Mindestens vorhanden:

- WinLaufen-Host/IP,
- WinLaufen-Verbindungszustand,
- aktuelle WinLaufen-Uhr,
- unabhängig aktivierbare Output Targets und deren Runtime-Zustand,
- die eine Presentation Config.

TCP-Port 4444 ist der Protokollstandard und muss in der normalen Oberfläche
nicht prominent sein.

## 9. Output Targets

Jedes Target hat einen dieser Typen:

- LOCAL
- SELFHOST
- RICHTER_PROJECTS

Targets sind nicht exklusiv. Eine Bridge darf mehrere Targets bedienen,
einschließlich mehrerer Targets desselben Typs. Jedes aktivierte Target
verwendet eine eigene ausgehende WebSocket-Verbindung, einen eigenen
Retry-Zustand und eine eigene Full-Snapshot-Resynchronisation. LOCAL nutzt
denselben Adapter und denselben Vertrag wie entfernte Ziele.

## 10. Web View

Die Web View ist eine öffentliche Zuschaueransicht für Telefon, Tablet und
Desktop, kein Sprecher-PC-Arbeitsplatz. Ein kompakter Header und eine kompakte
Navigation lassen die verfügbare Fläche genau einer Wettkampfansicht.

Hauptnavigation:

- Startliste
- LIVE
- Ergebnisse

### LIVE

LIVE reagiert auf WinLaufen-Ergebnissnapshots.

Wenn ein neuer Finish-/Ergebnissnapshot eintrifft:

- den übermittelten Klassenindex verwenden, bei **jedem** Snapshot und nicht nur
  beim ersten, damit LIVE einem Klassenwechsel in WinLaufen folgt,
- das vollständige aktuelle Klassenergebnis anzeigen,
- den übermittelten Current-Finish-Index verwenden, um den aktuellen Sportler zu
  kennzeichnen,
- diese Zeile temporär hervorheben; die Hervorhebung klingt aus und wird
  unterdrückt, wenn der Betrachter reduzierte Bewegung bevorzugt.

Der Current-Finish-Index ist nicht der Rang des Sportlers.

### Ergebnisse

Die Klasse wird manuell ausgewählt.

Die ausgewählte Klasse bleibt ausgewählt, auch wenn andere Klassen neue
Ergebnisse erhalten.

### Startliste

Die Position in der Oberfläche existiert von Anfang an.

Solange kein verifiziertes Startlistenprotokoll existiert, zeigt die Ansicht
einen klaren Hinweis, dass Teilnehmerdaten noch nicht verfügbar sind.

Es darf kein nicht unterstütztes WinLaufen-Startlistenprotokoll erfunden werden.

### Öffentliche Darstellungskonfiguration

Die Instanzkonfiguration steuert die Darstellung genau der Header `Verein`,
`Vbd`, `Nation` und `Schießen`. Verein, Verband und Schießen sind standardmäßig
sichtbar, Nation standardmäßig ausgeblendet. WinLaufen-Servernachrichten werden
intern vorgehalten und erscheinen nur dann als kompakter Hinweis, wenn
`showPublicMessages` aktiviert ist; der Default ist `false`. Diese Optionen
ändern ausschließlich die öffentliche Darstellung. Sie entfernen oder verändern
niemals Daten im normalisierten State.

## 11. Lauftabelle

Verifizierte Ergebnisspalten für Laufwettkämpfe:

- Rang
- StNr
- Name, Vorname
- Verein
- Vbd
- Laufzeit
- Rückstand

## 12. Biathlontabelle

Verifizierte Ergebnisspalten für Biathlon:

- Rang
- StNr
- Name, Vorname
- Verein
- Vbd
- Schießen
- Gesamtzeit
- Rückstand

Die Web View darf deshalb nicht auf ein einziges Lauf-Tabellenschema
festgeschrieben werden.

## 13. Verbindungszustand

Die WinLaufen-Uhr ist autoritativ.

Sie ist zugleich der Heartbeat der Anwendung.

Mindestens zu unterscheiden:

- verbunden und Uhrentelegramme werden empfangen,
- stale / keine Uhrentelegramme,
- getrennt.

Die v0.1-Regel lautet:

- TCP-Port 4444 und 5 Sekunden Verbindungstimeout,
- die erste syntaktisch gültige `UhrHH:MM:SS`-Nachricht setzt den Zustand auf
  verbunden,
- Syntax bedeutet das Präfix `Uhr` gefolgt von drei Feldern mit exakt zwei
  Dezimalziffern; es wird kein Wertebereich erzwungen, `Uhr99:99:99` bleibt also
  erhalten,
- jedes weitere gültige Uhrentelegramm bestätigt die Verbindung, unabhängig
  davon, ob sein Wert gleich, kleiner oder größer als der vorherige ist,
- WinLaufen Web validiert, korrigiert und plausibilisiert den Uhrenverlauf
  nicht,
- stale, wenn länger als 4 Sekunden kein gültiges Uhrentelegramm eingetroffen
  ist,
- dieselbe Frist gilt ab einer frischen Verbindung und beendet einen Stream, der
  sein erstes Uhrentelegramm nie liefert,
- eine stale Verbindung wird geschlossen und der Reconnect beginnt,
- Reconnect sofort, dann nach 2 Sekunden, dann nach 5 Sekunden, danach alle 10
  Sekunden,
- nach jedem Reconnect werden Socket, `ObjectInputStream` und
  Java-Serialisierungskontext neu erzeugt.

Der letzte gültige Wettkampfstand darf während des Reconnects sichtbar bleiben,
sein Verbindungszustand muss aber als stale oder getrennt angezeigt werden.

Lokale monotone Zeit darf ausschließlich zum Messen des 4-Sekunden-Intervalls
verwendet werden. Sie darf die angezeigte WinLaufen-Uhr niemals ersetzen oder
verändern.

Die API-Health-Werte sind exakt `DISCONNECTED`, `CONNECTED` und `STALE`.
`CONNECTED` beschreibt ausschließlich fortgesetzten Telegrammempfang, nicht den
Uhrenverlauf.

## 14. Einfachheit

Das Projekt muss klein und verständlich bleiben.

Bevorzugte Technik:

- Java
- Maven
- reines HTML
- reines CSS
- reines JavaScript
- eingebettete HTTP-/WebSocket-Funktion

Es ist keine Frontend-Build-Pipeline erforderlich.

Unnötige Infrastruktur vermeiden.

Nicht Teil von v0.1:

- Datenbank
- Redis
- Message Broker
- Docker als Laufzeitvoraussetzung
- nginx als Laufzeitvoraussetzung
- Node.js-Backend
- Spring
- Spring Boot
- Quarkus
- Micronaut
- React
- Vue
- Angular
- Electron
- Benutzerkonten
- Abrechnung
- Bezahlung
- SaaS-Administration

Kleine, fokussierte Abhängigkeiten sind nur zulässig, wenn sie Komplexität oder
Sicherheitsrisiko gegenüber einer manuellen Protokollimplementierung senken.

## 15. Konfiguration und Web-Sicherheit

Die Konfiguration wird als `java.util.Properties` gespeichert. Ort je nach
Installationsart: `/etc/winlaufen-web/bridge.properties` (Linux-Dienst),
`C:\ProgramData\WinLaufen Web\bridge.properties` (Windows-Dienst) oder
`${user.home}/.winlaufen-web/config.properties` (Entwicklungsbetrieb). Der Pfad
wird über die Systemproperty `winlaufen.bridge.config` gesetzt; das Dateiformat
ist in allen Fällen identisch.

Es wird keine Datenbank verwendet. Eine fokussierte JSON-Abhängigkeit ist auf
den versionierten Bridge-Live-Server-Vertrag beschränkt. Der WinLaufen-Zielhost
muss validiert werden. Sein Port ist fest auf 4444.

Bridge Control und der öffentliche Webdienst aktivieren kein CORS.
Konfigurationsänderungen in Bridge Control verwenden ausschließlich `POST` mit
`application/x-www-form-urlencoded` und erfordern einen gültigen Origin.
Browser-WebSocket-Verbindungen erfordern ebenfalls einen gültigen Origin.

HTTP und WebSocket verwenden bewusst unterschiedliche Ports. Eine Seite, die von
`http://<live-server>:8080` geladen wird, verbindet sich zu
`ws://<live-server>:8081/live/v1`; ihr Browser-Origin ist damit
`http://<live-server>:8080`. Der Origin-Hostname bzw. die IP muss dem Host der
WebSocket-Anfrage entsprechen. Origin-Port 8080 wird für den WebSocket auf Port
8081 akzeptiert; Gleichheit mit dem WebSocket-Port ist nicht erforderlich.
Fremde Origins und Anfragen ohne Origin werden abgelehnt. Der Bridge-Ingest
verwendet den eigenen Pfad `/bridge/v1/channels/<channel>` und
Bearer-Authentifizierung statt eines Browser-Origins.

Klartext-`ws` wird nur für `localhost` und für Loopback-, Link-Local- und
private IP-Adressliterale akzeptiert. Jeder andere Host, insbesondere jeder
DNS-Name, erfordert `wss`; `RICHTER_PROJECTS` erfordert immer `wss`.

Eingehende WebSocket-Nachrichten werden begrenzt, bevor sie im Speicher
zusammengesetzt werden: Ingest auf höchstens einen Vertragssnapshot,
Browser-Verbindungen auf eine kleine Nutzlast.

Diese Prototypversion hält ein bekanntes Default-Ingest-Secret funktionsfähig.
Das konkrete Manipulationsrisiko und die verbindlichen Einsatzgrenzen sind in
README.md unter „Known prototype security limitation" dokumentiert.

## 16. Browser-Synchronisation

Der normale Start läuft so ab:

1. HTML laden,
2. WebSocket verbinden,
3. unmittelbar nach der Verbindung einen vollständigen Snapshot erhalten,
4. Live-Updates empfangen.

`GET /api/v1/state` liefert den vollständigen Initial-/Fallback-State. Jeder
veröffentlichte State besitzt eine monoton steigende `publicationRevision`;
Live-WebSocket-Nachrichten sind vollständige autoritative Snapshots.

Ein allgemeiner Event-Bus oder ein zusätzliches Delta-Protokoll ist nicht
erforderlich.

Jeder WebSocket-`snapshot`, auch nach einem Reconnect, ist autoritativ und
synchronisiert die Browsertabellen vollständig. An einen einzelnen Client
ausgelieferte Revisionen dürfen niemals sinken.

## 17. Installationsziel

Der Endanwender soll keine Entwicklungsumgebung installieren müssen.

Ausgelieferte Pakete sollen die benötigte Java-Runtime enthalten können; die
Distribution kann dafür eine per `jlink` reduzierte Runtime bündeln.

Zielerfahrung:

WinLaufen Web installieren und Profil wählen,
gegebenenfalls WinLaufen-Host in Bridge Control anpassen,
Web View öffnen,
im LAN verwenden.
