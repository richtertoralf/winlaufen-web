# Release-Prozess

Ein Release wird ausschließlich aus einem Git-Tag im Format `vX.Y.Z` gebaut.
Die Version im Root-POM und die Parent-Versionen aller dort aufgeführten Module
müssen dabei exakt `X.Y.Z` lauten; eine `-SNAPSHOT`-Version wird abgelehnt.

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
