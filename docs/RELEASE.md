# Sprecher-Web — Release-Prozess

Aktuelle Entwicklungsversion: `0.3.0-SNAPSHOT`. Es gibt noch keinen Tag, kein
Release und keine Releasefreigabe.

Ein Release wird ausschließlich aus einem Git-Tag im Format `vX.Y.Z` gebaut.
Die Version im Root-POM und die Parent-Versionen aller dort aufgeführten Module
müssen dabei exakt `X.Y.Z` lauten; eine `-SNAPSHOT`-Version wird abgelehnt.

## Versionsquellen

Es gibt zwei getrennte Versionsangaben, die nicht vermischt werden dürfen:

- Die **Maven-Projektversion** im Root-POM, identisch als Parent-Version in
  allen Modulen, ist die einzige Quelle der Produktversion.
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

2. Den vollständigen Versionsvertrag und Build prüfen:

   ```sh
   ./installer/common/verify-release-tag.sh vX.Y.Z
   ./mvnw clean package
   ```

3. Den vollständigen Stand reviewen und committen.
4. Den freigegebenen Commit mit `vX.Y.Z` taggen.
5. Den Tag zu GitHub übertragen.

Der Workflow `.github/workflows/release.yml` checkt exakt den vom Tag
referenzierten Commit aus. Danach laufen mit JDK 25 und dem Maven Wrapper der
vollständige Build, alle Maven-Tests, die Installer-Tests und der Fan-out-Smoke-
Test. Ein Fehler in einem dieser Schritte verhindert die Veröffentlichung.

Linux amd64 und Windows x64 werden auf den jeweiligen GitHub-Runnern separat
gebaut. Beide Distributionen verwenden die vorhandenen `build-dist`-Skripte
und enthalten eine plattformspezifische `jlink`-Runtime. Erst wenn beide Pakete
erfolgreich vorliegen, erzeugt der Workflow `SHA256SUMS` und das GitHub Release.

Erwartete Assets für `v1.2.3`:

- `winlaufen-web-1.2.3-linux-amd64.tar.gz`
- `winlaufen-web-1.2.3-windows-x64.zip`
- `SHA256SUMS`

Build- oder Distributionsergebnisse werden nicht im Repository versioniert.
