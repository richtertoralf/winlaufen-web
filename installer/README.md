# WinLaufen Web — Installer

Zielgruppe: Entwickler und Maintainer der Installer.
Anwender installieren fertige Releasepakete nach
[INSTALLATION.md](../docs/INSTALLATION.md).
[Dokumentationsübersicht](../docs/INDEX.md).

## Einstiegspunkte

Pro Plattform gibt es genau einen Einstiegspunkt:

| Plattform | Installation | Deinstallation |
|---|---|---|
| Linux | `linux/install.sh` | `linux/uninstall.sh` |
| Windows 11 | `windows/Install-WinLaufenWeb.ps1` | `windows/Uninstall-WinLaufenWeb.ps1` |

Distributionspakete erstellen und veröffentlichen:
[RELEASE.md](../docs/RELEASE.md). Für Quellcode-Builds und Voraussetzungen siehe
[DEVELOPMENT.md](../docs/DEVELOPMENT.md).

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

Die unterstützten Rollen und Plattformen stehen zentral in
[INSTALLATION.md](../docs/INSTALLATION.md#4-installationsprofile).

## Grundsatz

Der Installer fragt ausschließlich das Profil ab. Er fragt **niemals** nach
WinLaufen-IP, Target-IP, Hostnamen, URL, Domain oder WSS-Adresse und blockiert
die Installation nicht, wenn diese Angaben noch unbekannt sind. Sie gehören in
die spätere Runtime-Konfiguration über Bridge Control.

Individuelle Einstellungen bleiben erhalten. Die einmalige Migration früherer
Installer-Netzwerkdefaults beschreibt [INSTALLATION.md](../docs/INSTALLATION.md#3-upgrade).

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
