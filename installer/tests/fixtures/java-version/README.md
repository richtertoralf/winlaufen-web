Fixtures für die Java-Versionsprobe des Windows-Installers.

`temurin-25.txt` stammt aus einem echten `java -XshowSettings:properties -version`.
Die übrigen Versionsdateien bilden davon abgeleitete Fälle ab, die auf diesem
System nicht real erzeugt werden können.

Java schreibt `-XshowSettings:properties` auf **stderr**. Diese Dateien
modellieren deshalb den stderr-Strom des Prozesses; `stdout-leer.txt` ist der
zugehörige leere stdout-Strom. Der Installer führt beide Ströme zusammen und
wertet erst den zusammengesetzten Text aus, weil stderr hier der eigentliche
Informationsträger und kein Fehlersignal ist.

`vm-distraktor.txt` stellt `java.vm.specification.version` neben
`java.specification.version` und gibt beiden bewusst verschiedene Zahlen. Ein zu
weit gefasstes Suchmuster würde hier die falsche Zeile lesen.

`javaw-leer.txt` bildet den Fall ab, in dem die Probe überhaupt keine
verwertbare Ausgabe liefert. Genau das trat unter Windows 11 zweimal auf: erst
mit `javaw.exe` als Probe, danach mit dem PowerShell-Operator `&` unter
`$ErrorActionPreference = 'Stop'`, weil dort schon die erste native
stderr-Zeile einen abbrechenden Fehler auslöste. Eine leere Probenausgabe darf
nie als gültige Runtime durchgehen.

`installer/tests/run-installer-tests.sh` prüft mit diesen Fixtures das aus
`Install-WinLaufenWeb.ps1` extrahierte Suchmuster und die dort gepinnte
Mindestversion.
