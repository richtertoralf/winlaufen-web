#!/usr/bin/env bash
# Baut ein Distributionsverzeichnis für WinLaufen Web.
#
#   installer/common/build-dist.sh [--output DIR] [--with-runtime] [--skip-build]
#
# Ergebnis:
#
#   dist/
#     lib/winlaufen-web-bridge.jar
#     lib/winlaufen-web-live-server.jar
#     runtime/                     (optional, per jlink erzeugt)
#     installer/                   (Installer-Dateien für beide Plattformen)
#     VERSION
#
# Die erzeugte Runtime ist immer plattformspezifisch: ein Linux-Build erzeugt
# eine Linux-Runtime, ein Windows-Build eine Windows-Runtime. Ein Cross-Build
# wird bewusst nicht versucht.
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
repository_root=$(cd -- "$script_dir/../.." && pwd -P)
# shellcheck source=dist-manifest.env
source "$script_dir/dist-manifest.env"

output="$repository_root/dist"
with_runtime=0
skip_build=0

while (($#)); do
    case "$1" in
        --output) output=$2; shift 2 ;;
        --with-runtime) with_runtime=1; shift ;;
        --skip-build) skip_build=1; shift ;;
        -h|--help) sed -n '2,20p' "${BASH_SOURCE[0]}"; exit 0 ;;
        *) echo "Unbekannte Option: $1" >&2; exit 2 ;;
    esac
done

if ((skip_build == 0)); then
    echo "== Baue Artefakte (mvn package) =="
    (cd "$repository_root" && mvn -B -q package)
fi

bridge_jar="$repository_root/bridge/target/$WINLAUFEN_BRIDGE_JAR"
live_jar="$repository_root/live-server/target/$WINLAUFEN_LIVE_JAR"
for jar in "$bridge_jar" "$live_jar"; do
    [[ -f "$jar" ]] || { echo "FEHLER: $jar fehlt. Zuerst 'mvn package' ausführen." >&2; exit 1; }
done

echo "== Erzeuge Distribution in $output =="
rm -rf -- "$output"
mkdir -p "$output/lib" "$output/installer"
cp -- "$bridge_jar" "$live_jar" "$output/lib/"
cp -R -- "$repository_root/installer/linux" "$repository_root/installer/windows" \
         "$repository_root/installer/common" "$output/installer/"

version=$(cd "$repository_root" && git describe --always --dirty 2>/dev/null || echo "unbekannt")
printf '%s\n' "$version" > "$output/VERSION"

if ((with_runtime)); then
    echo "== Erzeuge reduzierte Java-Runtime (jlink) =="
    command -v jlink >/dev/null 2>&1 || { echo "FEHLER: jlink nicht gefunden." >&2; exit 1; }
    # Nur die tatsächlich benötigten Module. jdk.crypto.ec wird für TLS/WSS
    # gebraucht, java.net.http nur von den Tests, daher hier nicht enthalten.
    jlink --add-modules java.base,java.logging,java.naming,java.xml,jdk.httpserver,jdk.crypto.ec \
          --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
          --output "$output/runtime"
    echo "   Runtime: $("$output/runtime/bin/java" -version 2>&1 | head -1)"
fi

echo
echo "Distribution fertig: $output"
echo "  Version:  $version"
echo "  Runtime:  $( ((with_runtime)) && echo "gebündelt" || echo "System-Java erforderlich (>= $WINLAUFEN_JAVA_RELEASE)")"
