# Sprecher-Web — Release-Prozess

Aktuelle Version: `0.4.0`.

Ein Release wird ausschließlich aus einem Git-Tag im Format `vX.Y.Z` gebaut.
Die Version im Root-POM und die Parent-Versionen aller dort aufgeführten Module
müssen dabei exakt `X.Y.Z` lauten; eine `-SNAPSHOT`-Version wird abgelehnt.

## Versionsquellen

Es gibt zwei getrennte Versionsangaben, die nicht vermischt werden dürfen:

- Die **Maven-Projektversion** im Root-POM, identisch als Parent-Version in
  allen Modulen, ist die einzige Quelle der Produktversion.
- Die **Version-Badge** oben in `README.md` zeigt dieselbe Version für Besucher
  der Repository-Hauptseite. Sie ist bewusst statisch und kein von GitHub
  abgeleiteter `latest`-Wert, damit sie immer genau den Stand dieses Repositorys
  nennt. Sie wird deshalb bei jedem Release mitgeführt (siehe Vorbereitung).
- Die **Build-ID** ist die Git-Commit-ID des gebauten Standes, bewusst kein
  Release-Tag. Sie kommt vom `git-commit-id-maven-plugin`, das die Angabe mit
  JGit direkt aus `.git` liest; ein Git-Programm auf dem Pfad ist dafür nicht
  nötig, der Weg ist unter Linux und Windows identisch. Die Konfiguration im
  Root-POM schließt jeden Tag vom Treffer aus, sodass immer die abgekürzte
  Commit-ID entsteht, bei nicht committierten Änderungen mit dem Zusatz
  `-dirty`.

Beide Werte setzt der Build per Maven Resource Filtering in `viewer.html` bzw.
`index.html` ein; die Fußzeile beider Oberflächen lautet damit
`Sprecher-Web · Version X.Y.Z · Build <commit>`. Von Hand hinterlegt ist keiner
der beiden Werte, und die UI-Vertragstests prüfen beide in der ausgelieferten
Fußzeile.

Ohne Git-Metadaten — etwa beim Build aus einem entpackten Source-Archiv —
schlägt der Build **nicht** fehl. Das Plugin liefert dann keine Angabe, und es
bleibt der im Root-POM hinterlegte Rückfallwert `unbekannt` stehen.

`dist/VERSION` ist von alledem unberührt und bleibt unverändert: Es enthält
weiterhin `git describe --always --dirty` als Kennzeichnung des Build-Standes
der Distribution und ist **keine** Produktversion. Für die Oberfläche ist diese
Angabe bewusst nicht geeignet, weil `git describe` auf einem getaggten Commit
den Tag statt der Commit-ID liefert.

## Vorbereitung

1. Die Version im gesamten Maven-Reaktor mit der gepinnten Version des Maven
   Versions Plugin aktualisieren:

   ```sh
   ./mvnw org.codehaus.mojo:versions-maven-plugin:2.21.0:set \
     -DnewVersion=X.Y.Z -DprocessAllModules=true -DgenerateBackupPoms=false
   ```

2. Die Version-Badge oben in `README.md` auf `X.Y.Z` setzen und die
   Versionsangaben in `README.md` (Statuszeile, Abschnitt Projektstatus) sowie
   in diesem Dokument nachziehen:

   ```text
   ![Version](https://img.shields.io/badge/version-X.Y.Z-blue)
   ```

3. Den vollständigen Versionsvertrag und Build prüfen:

   ```sh
   ./installer/common/verify-release-tag.sh vX.Y.Z
   ./mvnw clean package
   ```

4. Den vollständigen Stand reviewen und committen.
5. Den freigegebenen Commit mit `vX.Y.Z` taggen.
6. Den Tag zu GitHub übertragen. Der Push des Tags erzeugt das öffentliche
   GitHub Release samt Distributionen; es gibt keinen separaten Freigabeschritt.

Der Workflow `.github/workflows/release.yml` checkt exakt den vom Tag
referenzierten Commit aus. Danach laufen mit JDK 25 und dem Maven Wrapper der
vollständige Build, alle Maven-Tests, die Installer-Tests und der Fan-out-Smoke-
Test. Ein Fehler in einem dieser Schritte verhindert die Veröffentlichung.

Linux amd64 und Windows x64 werden auf den jeweiligen GitHub-Runnern separat
gebaut. Beide Distributionen verwenden die vorhandenen `build-dist`-Skripte
und enthalten eine plattformspezifische `jlink`-Runtime. Erst wenn beide Pakete
erfolgreich vorliegen, erzeugt der Workflow `SHA256SUMS` und das GitHub Release.

## Erzeugte Artefakte

Erwartete Assets für `v1.2.3`:

| Asset | Was es ist |
| ----- | ----------- |
| `winlaufen-web-1.2.3-linux-amd64.tar.gz` | Binary Distribution für Linux amd64 |
| `winlaufen-web-1.2.3-windows-x64.zip` | Binary Distribution für Windows x64 |
| `SHA256SUMS` | Prüfsummen beider Archive |

Beide Archive sind **Binary Distributions**, keine plattformnativen
Installationsprogramme. Ein Archiv enthält ein einziges Wurzelverzeichnis mit
`lib/` (die Anwendungs-JARs), `runtime/` (die plattformspezifische
`jlink`-Runtime), `installer/` (dieselben Installer- und Deinstallationsskripte
wie im Repository) und `VERSION`. Installiert wird daraus mit
`installer/linux/install.sh` bzw. `installer\windows\Install-WinLaufenWeb.ps1`;
Git, Maven und ein JDK werden dafür nicht benötigt.

Ausdrücklich **nicht** erzeugt werden ein nativer Windows-Installer (`.exe`,
`.msi`), ein Linux-Paket (`.deb`, `.rpm`) oder ein `winget`-Manifest. Ein
nativer Windows-Installer ist geplant; solange er fehlt, ist das ZIP mit dem
enthaltenen PowerShell-Skript der Windows-Weg. Auch ein ARM64-Paket für den
Raspberry Pi wird derzeit nicht gebaut; dort gilt der Weg über den Quellcode.

Die beiden Installationswege für Anwender und Entwickler sind in
[INSTALLATION.md](INSTALLATION.md) beschrieben.

### Abnahmestand der Pakete von `v0.4.0`

Beide Archive wurden nach der Veröffentlichung real installiert; das Protokoll
steht in [SMOKE_TESTS.md](SMOKE_TESTS.md#protokoll-installation-aus-dem-releasepaket-v040).

| Paket | Abnahme |
| ----- | ------- |
| Linux amd64 | Fresh Installation auf Ubuntu 24.04.4 LTS ohne System-Java, ohne Maven und ohne Source-Checkout; gebündelte Runtime verwendet; reale WinLaufen-Verbindung, Web Viewer und Startliste geprüft |
| Windows x64 | Clean Installation von Sprecher-Web auf einem Windows-11-PC in einer PowerShell als Administrator; gebündelte Runtime trotz vorhandenem System-Java verwendet; originales WinLaufen lief parallel weiter |

Bekannt und in `v0.4.0` enthalten: Der Windows-Installer stellt in Windows
PowerShell 5.1 deutsche Umlaute falsch dar
([Issue #5](https://github.com/richtertoralf/winlaufen-web/issues/5)). Nur die
Konsolenausgabe ist betroffen. Der Fix liegt in `main` und wird erstmals mit dem
nächsten Release ausgeliefert; `v0.4.0` bleibt unverändert.

Build- oder Distributionsergebnisse werden nicht im Repository versioniert.
