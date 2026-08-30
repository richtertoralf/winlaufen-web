#!/usr/bin/env bash
# Prüft vor einem Release, dass Tag, Root-POM und alle im Root-POM aufgeführten
# Modul-Parent-Versionen exakt dieselbe Release-Version tragen.
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
repository_root=$(cd -- "$script_dir/../.." && pwd -P)

tag=${1:-${GITHUB_REF_NAME:-}}
reactor_root=${2:-$repository_root}
pom_file="$reactor_root/pom.xml"

[[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || {
    echo "FEHLER: Release-Tag '$tag' entspricht nicht vX.Y.Z." >&2
    exit 1
}
[[ -f "$pom_file" ]] || {
    echo "FEHLER: Root-POM nicht gefunden: $pom_file" >&2
    exit 1
}

pom_version=$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' "$pom_file" | head -1)
[[ -n "$pom_version" ]] || {
    echo "FEHLER: Projektversion konnte nicht aus $pom_file gelesen werden." >&2
    exit 1
}
[[ "$pom_version" != *-SNAPSHOT ]] || {
    echo "FEHLER: Snapshot-Version '$pom_version' darf nicht veröffentlicht werden." >&2
    exit 1
}
[[ "$tag" == "v$pom_version" ]] || {
    echo "FEHLER: Tag '$tag' und POM-Version '$pom_version' stimmen nicht überein." >&2
    exit 1
}

modules=$(sed -n 's:.*<module>\([^<]*\)</module>.*:\1:p' "$pom_file")
[[ -n "$modules" ]] || {
    echo "FEHLER: Keine Maven-Module im Root-POM gefunden." >&2
    exit 1
}
while IFS= read -r module; do
    [[ -n "$module" ]] || continue
    module_pom="$reactor_root/$module/pom.xml"
    [[ -f "$module_pom" ]] || {
        echo "FEHLER: Modul-POM fehlt: $module_pom" >&2
        exit 1
    }
    parent_version=$(sed -n '/<parent>/,/<\/parent>/s:.*<version>\([^<]*\)</version>.*:\1:p' \
        "$module_pom")
    [[ -n "$parent_version" ]] || {
        echo "FEHLER: Parent-Version fehlt im Modul-POM: $module_pom" >&2
        exit 1
    }
    [[ "$parent_version" != *-SNAPSHOT ]] || {
        echo "FEHLER: Snapshot-Parent '$parent_version' im Modul $module." >&2
        exit 1
    }
    [[ "$parent_version" == "$pom_version" ]] || {
        echo "FEHLER: Parent-Version '$parent_version' im Modul $module stimmt nicht mit '$pom_version' überein." >&2
        exit 1
    }
done <<< "$modules"

echo "Release-Version für Root-POM und alle Module bestätigt: $tag"
