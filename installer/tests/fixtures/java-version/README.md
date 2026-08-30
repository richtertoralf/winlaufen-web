Fixtures für die Java-Versionsprobe des Windows-Installers.

`temurin-25.txt` stammt aus einem echten `java -XshowSettings:properties -version`.
Die übrigen Dateien bilden davon abgeleitete Fälle ab, die auf diesem System
nicht real erzeugt werden können. `javaw-leer.txt` bildet den unter Windows 11
beobachteten Fehlerfall ab: `javaw.exe` liefert beim Einsammeln in eine Variable
keine Property-Ausgabe, weshalb es nie als Versionsprobe verwendet werden darf.

`installer/tests/run-installer-tests.sh` prüft mit diesen Fixtures das aus
`Install-WinLaufenWeb.ps1` extrahierte Suchmuster und die dort gepinnte
Mindestversion.
