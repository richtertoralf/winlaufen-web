# WinLaufen Web — Installer

Rollenbasierter Installer für Linux und Windows 11. Die ausführliche Anleitung
steht in [../docs/INSTALLATION.md](../docs/INSTALLATION.md).

## Einstiegspunkte

Pro Plattform gibt es genau einen Einstiegspunkt:

| Plattform | Installation | Deinstallation |
|---|---|---|
| Linux | `linux/install.sh` | `linux/uninstall.sh` |
| Windows 11 | `windows/Install-WinLaufenWeb.ps1` | `windows/Uninstall-WinLaufenWeb.ps1` |

Vorher wird die Distribution gebaut:

| Plattform | Build |
|---|---|
| Linux | `common/build-dist.sh [--with-runtime]` |
| Windows | `common/build-dist.ps1 [-WithRuntime]` |

Die Build-Skripte verwenden `mvnw`/`mvnw.cmd`; ein separat installiertes Maven
ist nicht erforderlich. Developer benötigen Git und JDK 25. Endanwender laden
später das passende Release-Archiv herunter und benötigen weder Git noch Maven
oder einen Source Checkout. Bei gebündelter `jlink`-Runtime ist auch kein
separates Laufzeit-JDK nötig.

## Struktur

```text
installer/
    common/
        dist-manifest.env      gemeinsame Kenngrößen aus dem Anwendungscode
        build-dist.sh          Distribution bauen (Linux)
        build-dist.ps1         Distribution bauen (Windows)
    linux/
        install.sh             Profilauswahl, systemd-Units, Konfiguration
        uninstall.sh
    windows/
        Install-WinLaufenWeb.ps1
        Uninstall-WinLaufenWeb.ps1
    tests/
        run-installer-tests.sh automatisierte Prüfungen ohne root und systemd
```

`common/dist-manifest.env` ist die gemeinsame Referenz der Shell-Installer für
Ports, Pfade, Artefaktnamen und Java-Version. Der Windows-Installer enthält die
entsprechenden PowerShell-Konstanten. `tests/run-installer-tests.sh` prüft, dass
beide mit dem Anwendungscode übereinstimmen.

## Profile

| Profil | Linux | Windows 11 | Installiert |
|---|---|---|---|
| All-in-One | ja | ja | Bridge + Live Server |
| Bridge only | ja | ja | nur Bridge |
| Presentation Node | ja | nein | nur Live Server / Web View |

All-in-One ist die Default-Auswahl.

## Grundsatz

Der Installer fragt ausschließlich das Profil ab. Er fragt **niemals** nach
WinLaufen-IP, Target-IP, Hostnamen, URL, Domain oder WSS-Adresse und blockiert
die Installation nicht, wenn diese Angaben noch unbekannt sind. Sie gehören in
die spätere Runtime-Konfiguration über Bridge Control.

Vorhandene Konfiguration wird nie überschrieben. Defaults entstehen nur bei
einer echten Erstinstallation.

Vor dem Service-Start werden nur die profilabhängigen lokalen Listener 44440,
44441 und/oder 44442 geprüft. TCP 4444 ist das ausgehende Ziel der Bridge und
kein lokaler Preflight-Port. Linux verändert keine Firewall. Windows erfordert
Administratorrechte und legt nur eigene Private-/Domain-Regeln an; der
Uninstaller entfernt nur diese Regeln.

## Tests

```sh
./tests/run-installer-tests.sh
```

Der Linux-Installer wird dabei mit `--staging-root` in ein Temporärverzeichnis
ausgeführt: ohne root, ohne systemd, ohne Netzwerkzugriff. Der Windows-Installer
wird statisch geprüft, weil auf einem Linux-Rechner kein echter Windows-Lauf
möglich ist. Die realen Windows- und Mehrmaschinen-Szenarien stehen in
[../docs/SMOKE_TESTS.md](../docs/SMOKE_TESTS.md).
