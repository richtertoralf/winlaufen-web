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

`common/dist-manifest.env` ist die einzige Stelle, an der Ports, Pfade,
Artefaktnamen und die Java-Version stehen. Die Werte stammen aus dem
Anwendungscode; `tests/run-installer-tests.sh` prüft laufend, dass sie nicht
auseinanderlaufen.

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

## Tests

```sh
./tests/run-installer-tests.sh
```

Der Linux-Installer wird dabei mit `--staging-root` in ein Temporärverzeichnis
ausgeführt: ohne root, ohne systemd, ohne Netzwerkzugriff. Der Windows-Installer
wird statisch geprüft, weil auf einem Linux-Rechner kein echter Windows-Lauf
möglich ist. Die realen Windows- und Mehrmaschinen-Szenarien stehen in
[../docs/SMOKE_TESTS.md](../docs/SMOKE_TESTS.md).
