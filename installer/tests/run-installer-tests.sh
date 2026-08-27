#!/usr/bin/env bash
# Automatisierte Prüfungen für den rollenbasierten Installer.
#
#   ./installer/tests/run-installer-tests.sh
#
# Der Linux-Installer wird dazu mit --staging-root in ein Temporärverzeichnis
# ausgeführt: ohne root, ohne systemd, ohne Netzwerkzugriff. Der
# Windows-Installer wird statisch geprüft, weil auf dieser Plattform kein echter
# Windows-Lauf möglich ist.
set -uo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
repository_root=$(cd -- "$script_dir/../.." && pwd -P)
installer_linux="$repository_root/installer/linux/install.sh"
installer_windows="$repository_root/installer/windows/Install-WinLaufenWeb.ps1"
uninstaller_linux="$repository_root/installer/linux/uninstall.sh"
manifest="$repository_root/installer/common/dist-manifest.env"

# shellcheck source=../common/dist-manifest.env
source "$manifest"

passed=0
failed=0
work=$(mktemp -d /tmp/winlaufen-installer-tests.XXXXXX) || exit 1
trap 'rm -rf -- "$work"' EXIT HUP INT TERM

ok()   { printf 'PASS  %s\n' "$1"; passed=$((passed + 1)); }
bad()  { printf 'FAIL  %s\n        %s\n' "$1" "${2:-}" >&2; failed=$((failed + 1)); }

assert_file()      { [[ -f "$1" ]] && ok "$2" || bad "$2" "Datei fehlt: $1"; }
assert_no_file()   { [[ ! -e "$1" ]] && ok "$2" || bad "$2" "Datei sollte fehlen: $1"; }
assert_contains()  { grep -qF -- "$2" "$1" 2>/dev/null && ok "$3" || bad "$3" "'$2' fehlt in $1"; }
assert_absent()    { ! grep -qF -- "$2" "$1" 2>/dev/null && ok "$3" || bad "$3" "'$2' unerwartet in $1"; }
assert_equals()    { [[ "$1" == "$2" ]] && ok "$3" || bad "$3" "erwartet '$2', erhalten '$1'"; }

# Fake-Artefakte, damit die Tests keinen Maven-Build benötigen.
fake_dist="$work/dist"
mkdir -p "$fake_dist/lib"
printf 'fake bridge jar\n' > "$fake_dist/lib/$WINLAUFEN_BRIDGE_JAR"
printf 'fake live jar\n' > "$fake_dist/lib/$WINLAUFEN_LIVE_JAR"

install_log=""

run_install() {
    local profile=$1 root=$2
    shift 2
    install_log="$work/$(basename "$root").log"
    bash "$installer_linux" --profile "$profile" --staging-root "$root" --no-systemd \
        --dist "$fake_dist" "$@" > "$install_log" 2>&1
    local status=$?
    if ((status != 0)); then
        bad "Installer-Lauf ($profile)" "Exitcode $status, Log: $(tail -3 "$install_log" | tr '\n' ' ')"
    fi
    return $status
}

echo "=== Syntaxprüfung ==="
for script in "$installer_linux" "$uninstaller_linux" \
              "$repository_root/installer/common/build-dist.sh" \
              "$repository_root/installer/tests/run-installer-tests.sh"; do
    bash -n "$script" 2>/dev/null && ok "bash -n $(basename "$script")" \
        || bad "bash -n $(basename "$script")" "Syntaxfehler"
done

echo
echo "=== Linux: All-in-One erzeugt Bridge + Live Server ==="
root="$work/all-in-one"
if run_install all-in-one "$root"; then
    assert_file "$root/opt/winlaufen-web/lib/$WINLAUFEN_BRIDGE_JAR" "All-in-One installiert Bridge-Artefakt"
    assert_file "$root/opt/winlaufen-web/lib/$WINLAUFEN_LIVE_JAR" "All-in-One installiert Live-Server-Artefakt"
    assert_file "$root/etc/systemd/system/winlaufen-bridge.service" "All-in-One erzeugt Bridge-Service"
    assert_file "$root/etc/systemd/system/winlaufen-live-server.service" "All-in-One erzeugt Live-Server-Service"

    config="$root/etc/winlaufen-web/bridge.properties"
    assert_file "$config" "All-in-One erzeugt Bridge-Konfiguration"
    assert_contains "$config" "source.host=$WINLAUFEN_DEFAULT_SOURCE_HOST" \
        "All-in-One: lokaler WinLaufen-Endpunkt als Default"
    assert_contains "$config" "outputs.count=1" "All-in-One: genau ein Output Target"
    assert_contains "$config" "outputs.0.type=LOCAL" "All-in-One: Target-Typ LOCAL"
    assert_contains "$config" \
        "outputs.0.endpoint=ws://127.0.0.1:$WINLAUFEN_LIVE_WS_PORT$WINLAUFEN_INGEST_PATH_PREFIX$WINLAUFEN_LIVE_CHANNEL" \
        "All-in-One: lokales Target nutzt den regulären Bridge->Live-Server-Pfad"
    assert_contains "$config" "bridge.control.port=$WINLAUFEN_CONTROL_PORT" "All-in-One: Bridge-Control-Port"

    unit="$root/etc/systemd/system/winlaufen-bridge.service"
    assert_contains "$unit" "/opt/winlaufen-web/lib/$WINLAUFEN_BRIDGE_JAR" "Bridge-Service zeigt auf das Bridge-Artefakt"
    assert_contains "$unit" "-D$WINLAUFEN_BRIDGE_CONFIG_PROPERTY=/etc/winlaufen-web/bridge.properties" \
        "Bridge-Service verweist auf die systemweite Konfiguration"
    assert_contains "$unit" "WantedBy=multi-user.target" "Bridge-Service startet beim Boot"
    assert_contains "$unit" "User=winlaufen" "Bridge-Service läuft nicht als root"
    assert_absent "$unit" "User=root" "Bridge-Service fordert keine Root-Rechte an"

    live_unit="$root/etc/systemd/system/winlaufen-live-server.service"
    assert_contains "$live_unit" "/opt/winlaufen-web/lib/$WINLAUFEN_LIVE_JAR" "Live-Service zeigt auf das Live-Server-Artefakt"
    assert_contains "$live_unit" "WantedBy=multi-user.target" "Live-Service startet beim Boot"
    assert_contains "$live_unit" "User=winlaufen" "Live-Service läuft nicht als root"

    assert_file "$root/etc/winlaufen-web/live-server.env" "All-in-One erzeugt Live-Server-Konfiguration"
    assert_contains "$root/etc/winlaufen-web/live-server.env" "WINLAUFEN_LIVE_HTTP_PORT=$WINLAUFEN_LIVE_HTTP_PORT" \
        "Live-Server-Konfiguration enthält den HTTP-Port aus dem Code"
fi

echo
echo "=== Linux: Bridge only installiert keinen Live Server ==="
root="$work/bridge-only"
if run_install bridge-only "$root"; then
    assert_file "$root/opt/winlaufen-web/lib/$WINLAUFEN_BRIDGE_JAR" "Bridge only installiert Bridge-Artefakt"
    assert_no_file "$root/opt/winlaufen-web/lib/$WINLAUFEN_LIVE_JAR" "Bridge only installiert kein Live-Server-Artefakt"
    assert_file "$root/etc/systemd/system/winlaufen-bridge.service" "Bridge only erzeugt Bridge-Service"
    assert_no_file "$root/etc/systemd/system/winlaufen-live-server.service" "Bridge only erzeugt keinen Live-Server-Service"

    config="$root/etc/winlaufen-web/bridge.properties"
    assert_contains "$config" "outputs.count=0" "Bridge only ist ohne Output Target installierbar"
    assert_contains "$config" "source.host=$WINLAUFEN_DEFAULT_SOURCE_HOST" "Bridge only: WinLaufen-Default-Host"
    assert_no_file "$root/etc/winlaufen-web/live-server.env" "Bridge only erzeugt keine Live-Server-Konfiguration"
    assert_contains "$install_log" "mindestens ein Output Target eintragen" \
        "Bridge only weist auf die noch offene Target-Konfiguration hin"
    assert_absent "$install_log" "fehlgeschlagen" "Bridge only meldet keinen Fehler"
fi

echo
echo "=== Linux: Presentation Node installiert keine Bridge ==="
root="$work/presentation-node"
if run_install presentation-node "$root"; then
    assert_file "$root/opt/winlaufen-web/lib/$WINLAUFEN_LIVE_JAR" "Presentation Node installiert Live-Server-Artefakt"
    assert_no_file "$root/opt/winlaufen-web/lib/$WINLAUFEN_BRIDGE_JAR" "Presentation Node installiert kein Bridge-Artefakt"
    assert_file "$root/etc/systemd/system/winlaufen-live-server.service" "Presentation Node erzeugt Live-Server-Service"
    assert_no_file "$root/etc/systemd/system/winlaufen-bridge.service" "Presentation Node erzeugt keinen Bridge-Service"
    assert_no_file "$root/etc/winlaufen-web/bridge.properties" "Presentation Node erzeugt keine Bridge-Konfiguration"
    assert_contains "$install_log" "als Output Target ein" \
        "Presentation Node erklärt den nächsten Schritt auf der Bridge"
fi

echo
echo "=== Konfiguration: Reinstall überschreibt bestehende Werte nicht ==="
root="$work/reinstall"
if run_install all-in-one "$root"; then
    config="$root/etc/winlaufen-web/bridge.properties"
    # Runtime-Konfiguration simulieren, wie sie Bridge Control zurückschreibt.
    sed -i 's/^source.host=.*/source.host=10.77.0.1/' "$config"
    printf 'outputs.1.id=club\n' >> "$config"
    before=$(sha256sum "$config" | cut -d' ' -f1)

    run_install all-in-one "$root"
    after=$(sha256sum "$config" | cut -d' ' -f1)
    assert_equals "$after" "$before" "Reinstall lässt bestehende Bridge-Konfiguration unverändert"
    assert_contains "$config" "source.host=10.77.0.1" "Gepflegte WinLaufen-Adresse überlebt den Reinstall"
    assert_contains "$config" "outputs.1.id=club" "Gepflegte Target-Liste überlebt den Reinstall"
    assert_contains "$install_log" "Bestehende Bridge-Konfiguration beibehalten" \
        "Reinstall meldet den Schutz der bestehenden Konfiguration"
fi

echo
echo "=== Profilwechsel entfernt verwaiste Dienste ==="
root="$work/switch"
if run_install all-in-one "$root"; then
    assert_file "$root/etc/systemd/system/winlaufen-live-server.service" "Ausgangszustand hat beide Dienste"
    run_install bridge-only "$root"
    assert_no_file "$root/etc/systemd/system/winlaufen-live-server.service" \
        "Wechsel auf Bridge only entfernt den Live-Server-Dienst"
    assert_file "$root/etc/systemd/system/winlaufen-bridge.service" "Wechsel auf Bridge only behält den Bridge-Dienst"
fi

echo
echo "=== Deinstallation ==="
root="$work/uninstall"
if run_install all-in-one "$root"; then
    bash "$uninstaller_linux" --staging-root "$root" --no-systemd > "$work/uninstall.log" 2>&1
    assert_no_file "$root/opt/winlaufen-web" "Deinstallation entfernt die Programmdateien"
    assert_no_file "$root/etc/systemd/system/winlaufen-bridge.service" "Deinstallation entfernt den Bridge-Dienst"
    assert_no_file "$root/etc/systemd/system/winlaufen-live-server.service" "Deinstallation entfernt den Live-Server-Dienst"
    assert_file "$root/etc/winlaufen-web/bridge.properties" "Deinstallation ohne --purge behält die Konfiguration"

    bash "$uninstaller_linux" --staging-root "$root" --no-systemd --purge >> "$work/uninstall.log" 2>&1
    assert_no_file "$root/etc/winlaufen-web" "Deinstallation mit --purge entfernt die Konfiguration"
fi

echo
echo "=== Installer fragt keine Netzwerkadressen ab ==="
# Ein Netzwerkpflichtfeld würde sich als interaktive Leseanweisung zeigen.
network_prompts=$(grep -nE '(read[^|]*-p|Read-Host)[^\n]*(IP|Adresse|address|Host|host|URL|url|Ziel|[Tt]arget|Domain|WSS|wss)' \
    "$installer_linux" | grep -v '^[0-9]*:\s*#' || true)
[[ -z "$network_prompts" ]] && ok "Linux-Installer hat keine Netzwerkabfrage" \
    || bad "Linux-Installer hat keine Netzwerkabfrage" "$network_prompts"

read_calls=$(grep -cE '^\s*read -r -p' "$installer_linux")
assert_equals "$read_calls" "1" "Linux-Installer stellt genau eine interaktive Frage (Profilauswahl)"
assert_contains "$installer_linux" 'read -r -p "Auswahl [1]: "' "Die einzige Frage ist die Profilauswahl"

ps_prompts=$(grep -nE 'Read-Host' "$installer_windows" | grep -viE 'Auswahl' || true)
[[ -z "$ps_prompts" ]] && ok "Windows-Installer fragt nur das Profil ab" \
    || bad "Windows-Installer fragt nur das Profil ab" "$ps_prompts"

echo
echo "=== Windows-Installer: statische Prüfung ==="
assert_file "$installer_windows" "Windows-Installer vorhanden"
assert_contains "$installer_windows" "ValidateSet('AllInOne', 'BridgeOnly')" \
    "Windows unterstützt genau All-in-One und Bridge only"
assert_absent "$installer_windows" "PresentationNode" \
    "Presentation Node wird unter Windows bewusst nicht angeboten"
assert_contains "$installer_windows" "New-ScheduledTaskTrigger -AtStartup" \
    "Windows-Dienste starten beim Systemstart"
assert_contains "$installer_windows" "Unregister-ScheduledTask -TaskName \$TaskName -Confirm:\$false -ErrorAction SilentlyContinue" \
    "Windows-Aufgaben werden idempotent ersetzt statt dupliziert"
assert_contains "$installer_windows" "javaw.exe" "Windows startet ohne Konsolenfenster"
assert_contains "$installer_windows" "Bestehende Bridge-Konfiguration beibehalten" \
    "Windows schützt bestehende Konfiguration"
assert_contains "$installer_windows" "outputs.0.endpoint=ws://127.0.0.1:\$LiveWsPort\$IngestPathPrefix\$LiveChannel" \
    "Windows All-in-One nutzt den regulären Bridge->Live-Server-Pfad"

echo
echo "=== Kenngrößen stimmen mit dem Anwendungscode überein ==="
code_control_port=$(grep -oP 'DEFAULT_CONTROL_PORT = \K[0-9]+' \
    "$repository_root/bridge/src/main/java/de/winlaufen/web/bridge/config/BridgeConfigStore.java")
assert_equals "$code_control_port" "$WINLAUFEN_CONTROL_PORT" "Bridge-Control-Port stimmt mit dem Code überein"

code_ws_port=$(grep -oP 'port\("winlaufen.live.websocket.port", \K[0-9]+' \
    "$repository_root/live-server/src/main/java/de/winlaufen/web/liveserver/config/LiveServerConfig.java")
assert_equals "$code_ws_port" "$WINLAUFEN_LIVE_WS_PORT" "Live-Server-WebSocket-Port stimmt mit dem Code überein"

code_http_port=$(grep -oP 'port\("winlaufen.live.http.port", \K[0-9]+' \
    "$repository_root/live-server/src/main/java/de/winlaufen/web/liveserver/config/LiveServerConfig.java")
assert_equals "$code_http_port" "$WINLAUFEN_LIVE_HTTP_PORT" "Live-Server-HTTP-Port stimmt mit dem Code überein"

code_source_port=$(grep -oP 'WINLAUFEN_PORT = \K[0-9]+' \
    "$repository_root/bridge/src/main/java/de/winlaufen/web/bridge/config/BridgeConfig.java")
assert_equals "$code_source_port" "$WINLAUFEN_SOURCE_PORT" "WinLaufen-Port stimmt mit dem Code überein"

code_ingest=$(grep -oP 'return "\K/bridge/v1/channels/' \
    "$repository_root/live-server/src/main/java/de/winlaufen/web/liveserver/web/LiveWebSocketServer.java")
assert_equals "$code_ingest" "$WINLAUFEN_INGEST_PATH_PREFIX" "Ingest-Pfad stimmt mit dem Code überein"

code_release=$(grep -oP '<maven.compiler.release>\K[0-9]+' "$repository_root/pom.xml")
assert_equals "$code_release" "$WINLAUFEN_JAVA_RELEASE" "Java-Version stimmt mit dem Root-POM überein"

echo
echo "=== Packaging: keine Entwicklerpfade, keine Build-Artefakte ==="
dev_paths=$(grep -rnE '/home/[a-z]+/|C:\\Users\\|/Users/[a-z]+/' \
    "$repository_root/installer" || true)
[[ -z "$dev_paths" ]] && ok "Keine hardcodierten Entwicklerpfade im Installer" \
    || bad "Keine hardcodierten Entwicklerpfade im Installer" "$dev_paths"

tracked_artifacts=$( (cd "$repository_root" && git ls-files) \
    | grep -E '(^|/)target/|\.class$|\.jar$|\.log$' || true)
[[ -z "$tracked_artifacts" ]] && ok "Keine Build-Artefakte versioniert" \
    || bad "Keine Build-Artefakte versioniert" "$tracked_artifacts"

for expected in installer/linux/install.sh installer/linux/uninstall.sh \
                installer/windows/Install-WinLaufenWeb.ps1 \
                installer/windows/Uninstall-WinLaufenWeb.ps1 \
                installer/common/build-dist.sh installer/common/build-dist.ps1 \
                installer/common/dist-manifest.env; do
    assert_file "$repository_root/$expected" "Erwartetes Installer-Artefakt: $expected"
done

echo
echo "----------------------------------------"
printf 'Installer-Tests: %s bestanden, %s fehlgeschlagen\n' "$passed" "$failed"
((failed == 0)) || exit 1
