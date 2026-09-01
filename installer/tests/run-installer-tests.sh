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
uninstaller_windows="$repository_root/installer/windows/Uninstall-WinLaufenWeb.ps1"
uninstaller_linux="$repository_root/installer/linux/uninstall.sh"
manifest="$repository_root/installer/common/dist-manifest.env"
windows_legacy_fixture="$repository_root/installer/tests/fixtures/windows-legacy-java-crlf.properties"
java_version_fixtures="$repository_root/installer/tests/fixtures/java-version"

# shellcheck source=../common/dist-manifest.env
source "$manifest"

passed=0
failed=0
work=$(mktemp -d /tmp/winlaufen-installer-tests.XXXXXX) || exit 1
listener_pids=()
cleanup() {
    local pid
    for pid in "${listener_pids[@]:-}"; do
        [[ -n "$pid" ]] && kill "$pid" 2>/dev/null || true
    done
    rm -rf -- "$work"
}
trap cleanup EXIT HUP INT TERM

ok()   { printf 'PASS  %s\n' "$1"; passed=$((passed + 1)); }
bad()  { printf 'FAIL  %s\n        %s\n' "$1" "${2:-}" >&2; failed=$((failed + 1)); }

assert_file()      { [[ -f "$1" ]] && ok "$2" || bad "$2" "Datei fehlt: $1"; }
assert_no_file()   { [[ ! -e "$1" ]] && ok "$2" || bad "$2" "Datei sollte fehlen: $1"; }
assert_contains()  { grep -qF -- "$2" "$1" 2>/dev/null && ok "$3" || bad "$3" "'$2' fehlt in $1"; }
assert_absent()    { ! grep -qF -- "$2" "$1" 2>/dev/null && ok "$3" || bad "$3" "'$2' unerwartet in $1"; }
assert_equals()    { [[ "$1" == "$2" ]] && ok "$3" || bad "$3" "erwartet '$2', erhalten '$1'"; }

start_listener() {
    local port=$1
    # Ein bereits belegter Port erfüllt die Testvorbedingung ebenfalls. Das
    # hält die Suite wiederholbar, ohne einen fremden Listener anzufassen.
    if python3 - "$port" <<'PY'
import socket
import sys

probe = socket.socket()
probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
try:
    probe.bind(("0.0.0.0", int(sys.argv[1])))
except OSError:
    raise SystemExit(0)
finally:
    probe.close()
raise SystemExit(1)
PY
    then
        started_listener_pid=""
        return 0
    fi

    python3 - "$port" <<'PY' &
import socket
import sys

listener = socket.socket()
listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
listener.bind(("0.0.0.0", int(sys.argv[1])))
listener.listen()
while True:
    connection, _ = listener.accept()
    connection.close()
PY
    started_listener_pid=$!
    listener_pids+=("$started_listener_pid")
    local attempt
    for attempt in $(seq 1 30); do
        kill -0 "$started_listener_pid" 2>/dev/null || return 1
        (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null \
            && { exec 3<&-; exec 3>&-; return 0; }
        sleep .1
    done
    return 1
}

stop_listener() {
    local pid=$1
    [[ -n "$pid" ]] || return 0
    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
}

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

assert_port_conflict() {
    local profile=$1 port=$2 purpose=$3 label=$4
    local root="$work/conflict-$port"
    start_listener "$port" || { bad "$label" "Test-Listener auf TCP $port konnte nicht starten"; return; }
    local pid=$started_listener_pid log="$work/conflict-$port.log"
    bash "$installer_linux" --profile "$profile" --staging-root "$root" --no-systemd \
        --dist "$fake_dist" > "$log" 2>&1
    local status=$?
    stop_listener "$pid"

    ((status != 0)) && ok "$label: Exit-Code ungleich 0" \
        || bad "$label: Exit-Code ungleich 0" "Installer war trotz Portkonflikt erfolgreich"
    assert_contains "$log" "TCP-Port $port ist bereits belegt" "$label: Portnummer wird genannt"
    assert_contains "$log" "Benötigt für: $purpose" "$label: Portzweck wird genannt"
    assert_contains "$log" "Die Installation wurde nicht erfolgreich abgeschlossen" \
        "$label: Fehlschlag wird eindeutig gemeldet"
    assert_absent "$log" "Installation erfolgreich" "$label: keine Erfolgsmeldung"
}

assert_failed_service_start() {
    local fake_bin="$work/fake-systemd-bin"
    local root="$work/service-start-failure"
    local log="$work/service-start-failure.log"
    mkdir -p "$fake_bin"
    cat > "$fake_bin/systemctl" <<'EOF'
#!/usr/bin/env bash
case "$1" in
    is-active)
        echo failed
        exit 1
        ;;
    status)
        echo "simulierter systemd-Status: failed"
        exit 3
        ;;
    *)
        exit 0
        ;;
esac
EOF
    chmod +x "$fake_bin/systemctl"

    PATH="$fake_bin:$PATH" WINLAUFEN_INSTALL_TEST_SYSTEMD=1 \
        bash "$installer_linux" --profile bridge-only --staging-root "$root" \
        --dist "$fake_dist" > "$log" 2>&1
    local status=$?

    ((status != 0)) && ok "Fehlgeschlagener Service-Start: Exit-Code ungleich 0" \
        || bad "Fehlgeschlagener Service-Start: Exit-Code ungleich 0" \
            "Installer war trotz inaktivem Dienst erfolgreich"
    assert_contains "$log" "winlaufen-bridge.service ist nach der Startphase nicht active" \
        "Fehlgeschlagener Service-Start nennt den betroffenen Dienst"
    assert_contains "$log" "Die Installation wurde nicht erfolgreich abgeschlossen" \
        "Fehlgeschlagener Service-Start wird eindeutig gemeldet"
    assert_absent "$log" "Installation erfolgreich" \
        "Fehlgeschlagener Service-Start erzeugt keine Erfolgsmeldung"
}

assert_unreachable_local_http() {
    local fake_bin="$work/fake-unreachable-http"
    local root="$work/unreachable-http"
    local log="$work/unreachable-http.log"
    mkdir -p "$fake_bin"
    cat > "$fake_bin/systemctl" <<'EOF'
#!/usr/bin/env bash
case "$1" in
    is-active) [[ " $* " == *" --quiet "* ]] || echo active; exit 0 ;;
    show) echo 0; exit 0 ;;
    status) echo "simulierter systemd-Status: active"; exit 0 ;;
    *) exit 0 ;;
esac
EOF
    cat > "$fake_bin/curl" <<'EOF'
#!/usr/bin/env bash
exit 7
EOF
    chmod +x "$fake_bin/systemctl" "$fake_bin/curl"

    PATH="$fake_bin:$PATH" WINLAUFEN_INSTALL_TEST_SYSTEMD=1 \
        WINLAUFEN_INSTALL_TEST_START_ATTEMPTS=1 \
        bash "$installer_linux" --profile bridge-only --staging-root "$root" \
        --dist "$fake_dist" > "$log" 2>&1
    local status=$?

    ((status != 0)) && ok "Nicht erreichbares lokales Bridge Control: Exit-Code ungleich 0" \
        || bad "Nicht erreichbares lokales Bridge Control: Exit-Code ungleich 0"
    assert_contains "$log" "Bridge Control ist lokal auf TCP-Port" \
        "Nicht erreichbares lokales Bridge Control bleibt ein Installationsfehler"
    assert_absent "$log" "Installation erfolgreich" \
        "Lokaler HTTP-Fehler erzeugt keine Erfolgsmeldung"
}

assert_restart_loop() {
    local fake_bin="$work/fake-restart-loop"
    local root="$work/restart-loop"
    local log="$work/restart-loop.log"
    mkdir -p "$fake_bin"
    cat > "$fake_bin/systemctl" <<'EOF'
#!/usr/bin/env bash
case "$1" in
    is-active) [[ " $* " == *" --quiet "* ]] || echo active; exit 0 ;;
    show) echo 1; exit 0 ;;
    status) echo "simulierter systemd-Status: active, Neustart erkannt"; exit 0 ;;
    *) exit 0 ;;
esac
EOF
    cat > "$fake_bin/curl" <<'EOF'
#!/usr/bin/env bash
printf '<html>ok</html>'
EOF
    chmod +x "$fake_bin/systemctl" "$fake_bin/curl"

    PATH="$fake_bin:$PATH" WINLAUFEN_INSTALL_TEST_SYSTEMD=1 \
        WINLAUFEN_INSTALL_TEST_START_ATTEMPTS=1 \
        WINLAUFEN_INSTALL_TEST_STABILITY_SECONDS=0 \
        bash "$installer_linux" --profile bridge-only --staging-root "$root" \
        --dist "$fake_dist" > "$log" 2>&1
    local status=$?

    ((status != 0)) && ok "Restart-Loop: Exit-Code ungleich 0" \
        || bad "Restart-Loop: Exit-Code ungleich 0"
    assert_contains "$log" "während der Startphase neu gestartet" \
        "Restart-Loop bleibt ein Installationsfehler"
    assert_absent "$log" "Installation erfolgreich" \
        "Restart-Loop erzeugt keine Erfolgsmeldung"
}

diagnostic_case=0
diagnostic_log=""

run_simulated_operational_report() {
    local profile=$1 source_state=$2 targets_json=$3 outputs_json=$4 label=$5
    diagnostic_case=$((diagnostic_case + 1))
    local fake_bin="$work/fake-operational-$diagnostic_case"
    local root="$work/operational-$diagnostic_case"
    local log="$work/operational-$diagnostic_case.log"
    mkdir -p "$fake_bin"
    cat > "$fake_bin/systemctl" <<'EOF'
#!/usr/bin/env bash
case "$1" in
    is-active)
        [[ " $* " == *" --quiet "* ]] || echo active
        exit 0
        ;;
    show)
        [[ " $* " == *" NRestarts "* || " $* " == *"property=NRestarts"* ]] && echo 0
        exit 0
        ;;
    status)
        echo "simulierter systemd-Status: active"
        exit 0
        ;;
    *)
        exit 0
        ;;
esac
EOF
    cat > "$fake_bin/curl" <<'EOF'
#!/usr/bin/env bash
url=${!#}
if [[ "$url" == */api/v1/status ]]; then
    printf '{"sourceHealth":"%s","outputs":%s}' \
        "${WINLAUFEN_INSTALL_TEST_SOURCE_STATE:-DISCONNECTED}" \
        "${WINLAUFEN_INSTALL_TEST_OUTPUTS_JSON:-[]}"
elif [[ "$url" == */api/v1/config ]]; then
    printf '{"sourceType":"WINLAUFEN","sourceHost":"%s","sourcePort":4444,"targets":%s,"presentation":{}}' \
        "${WINLAUFEN_INSTALL_TEST_SOURCE_HOST:-192.168.95.10}" \
        "${WINLAUFEN_INSTALL_TEST_TARGETS_JSON:-[]}"
else
    printf '<html>ok</html>'
fi
EOF
    chmod +x "$fake_bin/systemctl" "$fake_bin/curl"

    PATH="$fake_bin:$PATH" \
        WINLAUFEN_INSTALL_TEST_SYSTEMD=1 \
        WINLAUFEN_INSTALL_TEST_START_ATTEMPTS=1 \
        WINLAUFEN_INSTALL_TEST_DIAGNOSTIC_ATTEMPTS=1 \
        WINLAUFEN_INSTALL_TEST_STABILITY_SECONDS=0 \
        WINLAUFEN_INSTALL_TEST_SOURCE_STATE="$source_state" \
        WINLAUFEN_INSTALL_TEST_TARGETS_JSON="$targets_json" \
        WINLAUFEN_INSTALL_TEST_OUTPUTS_JSON="$outputs_json" \
        bash "$installer_linux" --profile "$profile" --staging-root "$root" \
        --dist "$fake_dist" > "$log" 2>&1
    local status=$?

    ((status == 0)) && ok "$label: Exit-Code 0" \
        || bad "$label: Exit-Code 0" "Exitcode $status"
    assert_contains "$log" "Installation erfolgreich" "$label: Erfolgsmeldung"
    assert_absent "$log" "Die Installation wurde nicht erfolgreich abgeschlossen" \
        "$label: Verbindungsstatus erzeugt keinen Installationsfehler"
    diagnostic_log=$log
}

echo "=== Syntaxprüfung ==="
for script in "$installer_linux" "$uninstaller_linux" \
              "$repository_root/installer/common/build-dist.sh" \
              "$repository_root/installer/tests/run-installer-tests.sh"; do
    bash -n "$script" 2>/dev/null && ok "bash -n $(basename "$script")" \
        || bad "bash -n $(basename "$script")" "Syntaxfehler"
done

echo
echo "=== Profilabhängiger Port-Preflight ==="
assert_port_conflict all-in-one "$WINLAUFEN_LIVE_HTTP_PORT" "WinLaufen Web View / HTTP" \
    "All-in-One erkennt belegten HTTP-Port"
assert_port_conflict presentation-node "$WINLAUFEN_LIVE_WS_PORT" "Live WebSocket / Bridge Ingest" \
    "Presentation Node erkennt belegten WebSocket-Port"
assert_port_conflict bridge-only "$WINLAUFEN_CONTROL_PORT" "Bridge Control" \
    "Bridge only erkennt belegten Bridge-Control-Port"

start_listener "$WINLAUFEN_LIVE_HTTP_PORT"
http_listener=$started_listener_pid
start_listener "$WINLAUFEN_LIVE_WS_PORT"
ws_listener=$started_listener_pid
root="$work/bridge-ignores-live-ports"
if run_install bridge-only "$root"; then
    ok "Bridge only prüft keine Live-Server-Ports"
fi
stop_listener "$http_listener"
stop_listener "$ws_listener"

start_listener "$WINLAUFEN_CONTROL_PORT"
control_listener=$started_listener_pid
root="$work/presentation-ignores-control-port"
if run_install presentation-node "$root"; then
    ok "Presentation Node prüft keinen Bridge-Control-Port"
fi
stop_listener "$control_listener"

start_listener "$WINLAUFEN_SOURCE_PORT"
source_listener=$started_listener_pid
root="$work/source-port-is-remote"
if run_install all-in-one "$root"; then
    ok "TCP 4444 wird nicht als lokaler Listener von WinLaufen Web geprüft"
fi
stop_listener "$source_listener"

echo
echo "=== Fehlgeschlagener Service-Start ==="
assert_failed_service_start
assert_unreachable_local_http
assert_restart_loop

echo
echo "=== Installation und Betriebsbereitschaft sind getrennt ==="
local_target='[{"id":"local","type":"LOCAL","enabled":true,"endpoint":"ws://127.0.0.1:44441/bridge/v1/channels/local","channelId":"local","secretConfigured":true}]'
local_connected='[{"targetId":"local","state":"CONNECTED"}]'
local_disconnected='[{"targetId":"local","state":"DISCONNECTED"}]'
external_target='[{"id":"presentation-1","type":"SELFHOST","enabled":true,"endpoint":"ws://192.168.95.30:44441/bridge/v1/channels/race","channelId":"race","secretConfigured":true}]'
external_disconnected='[{"targetId":"presentation-1","state":"RETRY_WAIT"}]'
disabled_target='[{"id":"backup","type":"SELFHOST","enabled":false,"endpoint":"ws://192.168.95.31:44441/bridge/v1/channels/backup","channelId":"backup","secretConfigured":true}]'
disabled_runtime='[{"targetId":"backup","state":"DISABLED"}]'
# Temporärer Selfhost-Presentation-Node auf einer Cloud-VM mit öffentlicher IPv4.
cloud_target='[{"id":"selfhost-203-0-113-7-local","type":"SELFHOST","enabled":true,"endpoint":"ws://203.0.113.7:44441/bridge/v1/channels/local","channelId":"local","secretConfigured":true}]'
cloud_connected='[{"targetId":"selfhost-203-0-113-7-local","state":"CONNECTED"}]'

run_simulated_operational_report all-in-one DISCONNECTED "$local_target" "$local_connected" \
    "All-in-One: Source DISCONNECTED, local CONNECTED"
assert_contains "$diagnostic_log" "WARNUNG: DISCONNECTED" \
    "All-in-One meldet die getrennte WinLaufen-Quelle als Warnung"
assert_contains "$diagnostic_log" "ID: local" "All-in-One meldet das lokale Target"
assert_contains "$diagnostic_log" "Ziel: 127.0.0.1:44441" \
    "All-in-One leitet Host und Port des lokalen Targets ab"
assert_contains "$diagnostic_log" "OK: CONNECTED" "All-in-One meldet local CONNECTED"

run_simulated_operational_report all-in-one DISCONNECTED "$local_target" "$local_disconnected" \
    "All-in-One: Source und local DISCONNECTED"
assert_contains "$diagnostic_log" "lokale Datenpfad Bridge -> Live Server ist noch nicht verbunden" \
    "All-in-One hebt den getrennten lokalen Datenpfad deutlich hervor"
assert_contains "$diagnostic_log" "Bridge und Live Server wurden erfolgreich installiert" \
    "All-in-One trennt Local-Target-Warnung vom Installationserfolg"

run_simulated_operational_report all-in-one CONNECTED "$local_target" "$local_connected" \
    "All-in-One: Source und local CONNECTED"
assert_contains "$diagnostic_log" "WinLaufen-Quelle:" "All-in-One meldet die Quelle"
assert_contains "$diagnostic_log" "Ziel: 192.168.95.10:4444" \
    "All-in-One meldet konfigurierten WinLaufen-Host und Port"
[[ $(grep -cF 'OK: CONNECTED' "$diagnostic_log") -ge 2 ]] \
    && ok "All-in-One meldet Quelle und local positiv" \
    || bad "All-in-One meldet Quelle und local positiv"

run_simulated_operational_report bridge-only DISCONNECTED "$external_target" "$external_disconnected" \
    "Bridge only: Source und externes Target getrennt"
assert_contains "$diagnostic_log" "WARNUNG: RETRY_WAIT" \
    "Bridge only meldet das nicht erreichbare Target"
assert_contains "$diagnostic_log" "Ziel: 192.168.95.30:44441" \
    "Bridge only leitet Host und Port des externen Targets ab"
assert_contains "$diagnostic_log" "Presentation Node installiert und gestartet?" \
    "Bridge only nennt neutrale nächste Schritte"

run_simulated_operational_report bridge-only CONNECTED "$external_target" "$external_disconnected" \
    "Bridge only: Source verbunden, externes Target getrennt"
assert_contains "$diagnostic_log" "OK: CONNECTED" "Bridge only meldet die verbundene Quelle"
assert_contains "$diagnostic_log" "WARNUNG: RETRY_WAIT" \
    "Bridge only lässt ein getrenntes Target den Erfolg nicht blockieren"

run_simulated_operational_report bridge-only CONNECTED "$disabled_target" "$disabled_runtime" \
    "Bridge only: deaktiviertes Target"
assert_contains "$diagnostic_log" "Status: deaktiviert" \
    "Ein deaktiviertes Target wird neutral gemeldet"
assert_absent "$diagnostic_log" "WARNUNG: DISABLED" \
    "Ein deaktiviertes Target erzeugt keine Warnung"

run_simulated_operational_report bridge-only CONNECTED "$cloud_target" "$cloud_connected" \
    "Bridge only: temporärer Selfhost-Node an öffentlicher IPv4"
assert_contains "$diagnostic_log" "ID: selfhost-203-0-113-7-local" \
    "Die aus Adresse und Channel abgeleitete Target-ID erscheint unverändert im Bericht"
assert_contains "$diagnostic_log" "Ziel: 203.0.113.7:44441" \
    "Ein Selfhost-Target an öffentlicher IPv4 wird als gültiges Ziel gemeldet"

run_simulated_operational_report presentation-node DISCONNECTED '[]' '[]' \
    "Presentation Node ohne verbundene Bridge"
assert_contains "$diagnostic_log" "Bridge-Ingest wartet auf eine Bridge" \
    "Presentation Node meldet den wartenden Ingest neutral"
assert_contains "$diagnostic_log" "TCP 44440 erreichbar" \
    "Presentation Node meldet den lokalen HTTP-Endpunkt"
assert_contains "$diagnostic_log" "TCP 44441 lauscht" \
    "Presentation Node meldet den lokalen WebSocket-Listener"

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
    assert_contains "$config" "bridge.control.bind=$WINLAUFEN_CONTROL_BIND" \
        "All-in-One: Bridge Control bindet für den LAN-Zugriff"
    assert_equals "$(stat -c '%a' "$root/etc/winlaufen-web")" "770" \
        "Konfigurationsverzeichnis erlaubt atomare Updates durch die Dienstgruppe"
    assert_equals "$(stat -c '%a' "$config")" "640" \
        "Bridge-Konfiguration bleibt restriktiv"
    atomic_update=$(mktemp "$root/etc/winlaufen-web/config.properties.XXXXXX")
    cp -- "$config" "$atomic_update"
    printf 'presentation.showNation=true\n' >> "$atomic_update"
    mv -f -- "$atomic_update" "$config"
    assert_contains "$config" "presentation.showNation=true" \
        "Atomare Konfigurationsaktualisierung im geschützten Verzeichnis funktioniert"
    assert_absent "$config" "44443" "All-in-One wählt keinen Ersatzport"
    assert_absent "$config" "44444" "All-in-One wählt keinen weiteren Ersatzport"

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
    assert_contains "$root/etc/winlaufen-web/live-server.env" "WINLAUFEN_LIVE_WS_PORT=$WINLAUFEN_LIVE_WS_PORT" \
        "Live-Server-Konfiguration enthält den gemeinsamen WebSocket-Port"
    assert_contains "$install_log" "keine Firewall aktiviert und keine Firewallregel" \
        "Linux All-in-One erklärt, dass keine Firewall verändert wurde"
    assert_contains "$install_log" "TCP $WINLAUFEN_LIVE_HTTP_PORT – Web View / HTTP" \
        "Linux All-in-One nennt den eingehenden HTTP-Port"
    assert_contains "$install_log" "TCP $WINLAUFEN_LIVE_WS_PORT – Live WebSocket / Bridge Ingest" \
        "Linux All-in-One nennt den eingehenden WebSocket-Port"
    assert_contains "$install_log" "TCP $WINLAUFEN_CONTROL_PORT – Bridge Control" \
        "Linux All-in-One nennt den eingehenden Bridge-Control-Port"
    assert_contains "$install_log" "TCP $WINLAUFEN_SOURCE_PORT zum WinLaufen-PC" \
        "Linux All-in-One nennt die ausgehende WinLaufen-Verbindung"
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
    assert_contains "$install_log" "TCP $WINLAUFEN_CONTROL_PORT – Bridge Control" \
        "Linux Bridge only nennt nur seinen eingehenden Listener"
    assert_absent "$install_log" "TCP $WINLAUFEN_LIVE_HTTP_PORT – Web View / HTTP" \
        "Linux Bridge only nennt keinen Live-Server-Port"
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
    assert_contains "$install_log" "TCP $WINLAUFEN_LIVE_HTTP_PORT – Web View / HTTP" \
        "Linux Presentation Node nennt den HTTP-Port"
    assert_contains "$install_log" "TCP $WINLAUFEN_LIVE_WS_PORT – Live WebSocket / Bridge Ingest" \
        "Linux Presentation Node nennt den WebSocket-Port"
    assert_absent "$install_log" "TCP $WINLAUFEN_CONTROL_PORT – Bridge Control" \
        "Linux Presentation Node nennt keinen Bridge-Control-Port"
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
echo "=== Konfiguration: frühere Installer-Netzwerkdefaults werden migriert ==="
root="$work/network-migration"
if run_install all-in-one "$root"; then
    config="$root/etc/winlaufen-web/bridge.properties"
    live_config="$root/etc/winlaufen-web/live-server.env"
    sed -i \
        -e 's/^bridge.control.bind=.*/bridge.control.bind=127.0.0.1/' \
        -e 's/^bridge.control.port=.*/bridge.control.port=8090/' \
        -e 's#^outputs.0.endpoint=.*#outputs.0.endpoint=ws://127.0.0.1:8081/bridge/v1/channels/local#' \
        "$config"
    sed -i \
        -e 's/^WINLAUFEN_LIVE_HTTP_PORT=.*/WINLAUFEN_LIVE_HTTP_PORT=8080/' \
        -e 's/^WINLAUFEN_LIVE_WS_PORT=.*/WINLAUFEN_LIVE_WS_PORT=8081/' \
        "$live_config"
    run_install all-in-one "$root"
    assert_contains "$config" "bridge.control.bind=$WINLAUFEN_CONTROL_BIND" \
        "Alter Bridge-Control-Bind wird auf den Netzwerkvertrag migriert"
    assert_contains "$config" "bridge.control.port=$WINLAUFEN_CONTROL_PORT" \
        "Alter Bridge-Control-Port wird auf den Netzwerkvertrag migriert"
    assert_contains "$config" "outputs.0.endpoint=ws://127.0.0.1:$WINLAUFEN_LIVE_WS_PORT/bridge/v1/channels/local" \
        "Alter lokaler Ingest-Port wird auf den Netzwerkvertrag migriert"
    assert_contains "$live_config" "WINLAUFEN_LIVE_HTTP_PORT=$WINLAUFEN_LIVE_HTTP_PORT" \
        "Alter HTTP-Default wird auf den Netzwerkvertrag migriert"
    assert_contains "$live_config" "WINLAUFEN_LIVE_WS_PORT=$WINLAUFEN_LIVE_WS_PORT" \
        "Alter WebSocket-Default wird auf den Netzwerkvertrag migriert"

    sed -i \
        's#^outputs\.0\.endpoint=.*#outputs.0.endpoint=ws\\://127.0.0.1\\:8081/bridge/v1/channels/local#' \
        "$config"
    run_install all-in-one "$root"
    assert_contains "$config" \
        "outputs.0.endpoint=ws\\://127.0.0.1\\:$WINLAUFEN_LIVE_WS_PORT/bridge/v1/channels/local" \
        "Von Properties.store escapeter alter Ingest-Default wird migriert"

    for unchanged_endpoint in \
            'ws\://127.0.0.1\:9081/bridge/v1/channels/local' \
            'ws\://192.168.1.20\:8081/bridge/v1/channels/local' \
            "ws\\://127.0.0.1\\:$WINLAUFEN_LIVE_WS_PORT/bridge/v1/channels/local"; do
        sed -i "s#^outputs\.0\.endpoint=.*#outputs.0.endpoint=$unchanged_endpoint#" "$config"
        before=$(sha256sum "$config" | cut -d' ' -f1)
        run_install all-in-one "$root"
        after=$(sha256sum "$config" | cut -d' ' -f1)
        assert_equals "$after" "$before" \
            "Benutzerdefinierter oder aktueller Endpunkt bleibt unverändert: $unchanged_endpoint"
        assert_absent "$install_log" "Frühere Installer-Netzwerkdefaults auf den festen Portblock migriert" \
            "Ohne passende Legacy-Zeile erscheint keine Migrationsmeldung"
    done
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
echo "=== Distributionslayout: Installer finden ihre Artefakte ==="
# Eine echte Distribution mit dem echten build-dist.sh erzeugen, aber ohne
# Maven-Build: das Skript leitet seine Repository-Wurzel aus der eigenen Lage ab,
# deshalb genügt eine Kopie mit gefälschten target-Artefakten.
dist_source="$work/dist-source"
mkdir -p "$dist_source/installer" "$dist_source/bridge/target" "$dist_source/live-server/target"
cp -R "$repository_root/installer/common" "$repository_root/installer/linux" \
      "$repository_root/installer/windows" "$dist_source/installer/"
printf 'fake bridge jar\n' > "$dist_source/bridge/target/$WINLAUFEN_BRIDGE_JAR"
printf 'fake live jar\n' > "$dist_source/live-server/target/$WINLAUFEN_LIVE_JAR"
dist_root="$work/dist"
if bash "$dist_source/installer/common/build-dist.sh" --skip-build --output "$dist_root" \
        > "$work/build-dist.log" 2>&1; then
    ok "build-dist.sh erzeugt eine Distribution ohne Maven-Build"
else
    bad "build-dist.sh erzeugt eine Distribution ohne Maven-Build" \
        "$(tail -3 "$work/build-dist.log" | tr '\n' ' ')"
fi

assert_file "$dist_root/lib/$WINLAUFEN_BRIDGE_JAR" "Distribution enthält das Bridge-Artefakt in lib/"
assert_file "$dist_root/lib/$WINLAUFEN_LIVE_JAR" "Distribution enthält das Live-Server-Artefakt in lib/"
assert_file "$dist_root/VERSION" "Distribution enthält VERSION in der Wurzel"
assert_file "$dist_root/installer/linux/install.sh" "Distribution enthält den Linux-Installer"
assert_file "$dist_root/installer/windows/Install-WinLaufenWeb.ps1" "Distribution enthält den Windows-Installer"
assert_file "$dist_root/installer/common/dist-manifest.env" "Distribution enthält das gemeinsame Manifest"

# Beide Installer liegen in <Wurzel>/installer/<os>/ und lösen ihre Wurzel zwei
# Ebenen darüber auf. Genau diese Tiefe wird hier am real erzeugten Baum geprüft.
expected_root=$(cd -- "$dist_root" && pwd -P)
windows_root=$(cd -- "$dist_root/installer/windows/../.." && pwd -P)
linux_root=$(cd -- "$dist_root/installer/linux/../.." && pwd -P)
assert_equals "$windows_root" "$expected_root" \
    "Windows-Installer löst aus installer/windows die Distributionswurzel auf"
assert_equals "$linux_root" "$expected_root" \
    "Linux-Installer löst dieselbe Distributionswurzel auf"
assert_file "$windows_root/lib/$WINLAUFEN_BRIDGE_JAR" \
    "Aus der Windows-Installerlage ist lib/ mit dem Bridge-Artefakt erreichbar"
assert_file "$windows_root/lib/$WINLAUFEN_LIVE_JAR" \
    "Aus der Windows-Installerlage ist lib/ mit dem Live-Server-Artefakt erreichbar"
# Regression: die Wurzel darf nicht eine Ebene zu hoch bei installer/ stehen
# bleiben, sonst sucht der Installer in installer/bridge/target/.
assert_no_file "$dist_root/installer/lib" "Eine Distribution hat kein installer/lib"
assert_no_file "$dist_root/installer/bridge/target/$WINLAUFEN_BRIDGE_JAR" \
    "Eine Distribution hat kein installer/bridge/target; dieses Maven-Layout darf nicht erwartet werden"
assert_no_file "$dist_root/bridge/target/$WINLAUFEN_BRIDGE_JAR" \
    "Eine Distribution setzt überhaupt kein Maven-target-Verzeichnis voraus"

# Ausführbarer Nachweis desselben Vertrags: der Linux-Installer wird direkt aus
# der Distribution gestartet und muss seine Artefakte in lib/ finden.
root="$work/dist-install"
dist_log="$work/dist-install.log"
if bash "$dist_root/installer/linux/install.sh" --profile all-in-one \
        --staging-root "$root" --no-systemd > "$dist_log" 2>&1; then
    ok "Installer läuft direkt aus der Distribution heraus"
    assert_file "$root/opt/winlaufen-web/lib/$WINLAUFEN_BRIDGE_JAR" \
        "Bridge-Artefakt stammt aus der Distribution, nicht aus einem Source-Baum"
    assert_file "$root/opt/winlaufen-web/lib/$WINLAUFEN_LIVE_JAR" \
        "Live-Server-Artefakt stammt aus der Distribution"
    assert_absent "$dist_log" "target/$WINLAUFEN_BRIDGE_JAR" \
        "Der Installer verlangt in der Distribution kein Maven-target-Verzeichnis"
else
    bad "Installer läuft direkt aus der Distribution heraus" \
        "$(tail -3 "$dist_log" | tr '\n' ' ')"
fi

# Source-Checkout: dieselbe Tiefe zeigt auf die Repository-Wurzel mit dem Root-POM.
source_root=$(cd -- "$repository_root/installer/windows/../.." && pwd -P)
assert_equals "$source_root" "$repository_root" \
    "Aus installer/windows führen zwei Ebenen auf die Repository-Wurzel"
assert_file "$source_root/pom.xml" "Die Repository-Wurzel trägt das Root-POM als Source-Layout-Marke"

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
assert_contains "$installer_windows" 'Split-Path -Parent (Split-Path -Parent $PSScriptRoot)' \
    "Windows-Installer löst seine Wurzel zwei Ebenen über installer\\windows auf"
assert_absent "$installer_windows" '$DistPath = Split-Path -Parent $PSScriptRoot' \
    "Windows-Installer bleibt nicht eine Ebene zu hoch bei installer\\ stehen"
assert_contains "$installer_windows" "Resolve-ArtifactLayout" \
    "Windows-Installer trennt Distribution und Source-Checkout in einer eigenen Auflösung"
assert_contains "$installer_windows" "Test-Path -LiteralPath (Join-Path \$Root 'pom.xml') -PathType Leaf" \
    "Windows-Installer erkennt das Source-Layout am Root-POM statt es zu raten"
assert_contains "$installer_windows" "Test-Path -LiteralPath \$libDir -PathType Container" \
    "Windows-Installer erkennt das Distributionslayout an lib\\"
assert_contains "$installer_windows" 'Join-Path $Root "bridge\target\$BridgeJar"' \
    "Das Maven-Layout hängt an derselben Wurzel wie lib\\ und nicht an installer\\"
assert_contains "$installer_windows" "\$runtimeSource = Join-Path \$DistPath 'runtime'" \
    "Die gebündelte Runtime wird aus der Distributionswurzel installiert"
assert_contains "$installer_windows" "Invoke-PortPreflight" \
    "Windows führt vor der Installation einen Port-Preflight aus"
assert_contains "$installer_windows" "Assert-ListenerPortAvailable -Port \$LiveHttpPort" \
    "Windows prüft den HTTP-Port profilabhängig"
assert_contains "$installer_windows" "Assert-ListenerPortAvailable -Port \$LiveWsPort" \
    "Windows prüft den WebSocket-Port profilabhängig"
assert_contains "$installer_windows" "Assert-ListenerPortAvailable -Port \$ControlPort" \
    "Windows prüft den Bridge-Control-Port profilabhängig"
assert_contains "$installer_windows" "Wait-InstalledRuntime" \
    "Windows validiert gestartete Hintergrunddienste und Listener"
assert_contains "$installer_windows" "Get-BridgeOperationalDiagnostic" \
    "Windows diagnostiziert Quellen und Targets nach dem lokalen Start"
assert_contains "$installer_windows" "Get-LocalOutputState" \
    "Windows beobachtet den lokalen All-in-One-Datenpfad"
assert_contains "$installer_windows" "Verbindungszustand ist niemals ein" \
    "Windows behandelt Verbindungen ausdrücklich nicht als Installationsfehler"
assert_absent "$installer_windows" "Wait-LocalOutputConnected" \
    "Windows besitzt keine harte Local-Target-Startbedingung mehr"
assert_contains "$installer_windows" "api/v1/status" \
    "Windows liest den bestehenden Bridge-Control-Statusvertrag"
assert_contains "$installer_windows" "Test-ListenerOwnedByThisInstallation" \
    "Windows erkennt Listener der bestehenden eigenen Installation"
assert_contains "$installer_windows" "Assert-ConfigurationPrerequisites" \
    "Windows prüft bestehende Konfiguration vor dem Stop"
assert_contains "$installer_windows" "Restore-PreviouslyRunningTasks -TaskNames \$previouslyRunningTasks" \
    "Windows versucht nach einem Fehler den vorherigen Taskzustand wiederherzustellen"
assert_contains "$installer_windows" "Update-PropertiesLines" \
    "Windows migriert Properties zeilenweise"
assert_contains "$installer_windows" "Regex]::Split(\$content, '\\r\\n|\\n')" \
    "Windows verarbeitet CRLF und LF explizit"
assert_contains "$installer_windows" 'endpoint=ws\\://127\.0\.0\.1\\:8081/bridge/v1/channels/local$' \
    "Windows erkennt Properties.store-Escaping nur für den exakten alten Default"
assert_contains "$installer_windows" "PowerShell als Administrator starten" \
    "Windows fordert bei fehlenden Rechten eine Administrator-PowerShell"
assert_contains "$installer_windows" "New-NetFirewallRule -Name \$rule.Name" \
    "Windows legt eigene benannte Firewallregeln an"
assert_contains "$installer_windows" "-Profile Private,Domain" \
    "Windows beschränkt Freigaben auf Private-/Domain-Netze"
assert_absent "$installer_windows" "-Profile Public" \
    "Windows legt keine Public-Firewallfreigabe an"

windows_java_line=$(grep -n '^\$javaExe = Resolve-JavaExecutable' "$installer_windows" | cut -d: -f1)
windows_config_check_line=$(grep -n '^Assert-ConfigurationPrerequisites$' "$installer_windows" | cut -d: -f1)
windows_first_preflight_line=$(grep -n '^Invoke-PortPreflight -AllowInstalledListeners$' "$installer_windows" | cut -d: -f1)
windows_snapshot_line=$(grep -n '^\$previouslyRunningTasks = ' "$installer_windows" | cut -d: -f1)
windows_stop_line=$(grep -n '^    Stop-ExistingWinLaufenProcesses$' "$installer_windows" | cut -d: -f1)
windows_validation_line=$(grep -n '^    \$localRuntimeValidated = \$true$' "$installer_windows" | cut -d: -f1)
windows_firewall_line=$(grep -n '^    Sync-WindowsFirewallRules$' "$installer_windows" | cut -d: -f1)
windows_profile_cleanup_line=$(grep -n '^        Remove-BackgroundTask -TaskName \$LiveTaskName$' "$installer_windows" | cut -d: -f1)
windows_diagnostic_line=$(grep -n '^        \$bridgeDiagnostic = Get-BridgeOperationalDiagnostic$' "$installer_windows" | cut -d: -f1)

if [[ -n "$windows_java_line" && -n "$windows_config_check_line" &&
      -n "$windows_first_preflight_line" && -n "$windows_snapshot_line" &&
      -n "$windows_stop_line" && "$windows_java_line" -lt "$windows_stop_line" &&
      "$windows_config_check_line" -lt "$windows_stop_line" &&
      "$windows_first_preflight_line" -lt "$windows_stop_line" &&
      "$windows_snapshot_line" -lt "$windows_stop_line" ]]; then
    ok "Windows erledigt fehleranfällige Preflights vor dem Stop einer laufenden Installation"
else
    bad "Windows erledigt fehleranfällige Preflights vor dem Stop einer laufenden Installation" \
        "Java=$windows_java_line Config=$windows_config_check_line Port=$windows_first_preflight_line Snapshot=$windows_snapshot_line Stop=$windows_stop_line"
fi

if [[ -n "$windows_firewall_line" && -n "$windows_diagnostic_line" &&
      "$windows_firewall_line" -lt "$windows_diagnostic_line" ]]; then
    ok "Windows diagnostiziert externe Verbindungen erst nach der Firewall-Synchronisierung"
else
    bad "Windows diagnostiziert externe Verbindungen erst nach der Firewall-Synchronisierung" \
        "Firewall=$windows_firewall_line Diagnose=$windows_diagnostic_line"
fi

windows_operational_diagnostic=$(sed -n \
    '/^function Get-BridgeOperationalDiagnostic {/,/^}$/p' "$installer_windows")
[[ "$windows_operational_diagnostic" != *'throw '* ]] \
    && ok "Windows-Source-/Target-Diagnose kann die Installation nicht per throw abbrechen" \
    || bad "Windows-Source-/Target-Diagnose kann die Installation nicht per throw abbrechen" \
        "$windows_operational_diagnostic"

if [[ -n "$windows_validation_line" && -n "$windows_firewall_line" &&
      "$windows_validation_line" -lt "$windows_firewall_line" ]]; then
    ok "Windows synchronisiert Firewallregeln erst nach erfolgreicher Runtime-Validierung"
else
    bad "Windows synchronisiert Firewallregeln erst nach erfolgreicher Runtime-Validierung" \
        "Validierung=$windows_validation_line Firewall=$windows_firewall_line"
fi

if [[ -n "$windows_firewall_line" && -n "$windows_profile_cleanup_line" &&
      "$windows_firewall_line" -lt "$windows_profile_cleanup_line" ]]; then
    ok "Windows entfernt bei Profilwechsel alte Tasks erst nach erfolgreichem Abschluss"
else
    bad "Windows entfernt bei Profilwechsel alte Tasks erst nach erfolgreichem Abschluss" \
        "Firewall=$windows_firewall_line Profilbereinigung=$windows_profile_cleanup_line"
fi

assert_contains "$installer_windows" '$previouslyRunningTasks = @(Get-RunningWinLaufenTasks)' \
    "Windows behandelt einen Fresh Install ohne vorhandene Tasks als leeren Ausgangszustand"
assert_contains "$installer_windows" 'if ($installationWasStopped -and $previouslyRunningTasks.Count -gt 0)' \
    "Windows startet beim Fresh Install keine nicht vorhandenen Tasks zurück"
for firewall_rule in WinLaufenWeb-HTTP-44440 WinLaufenWeb-WebSocket-44441 \
                     WinLaufenWeb-BridgeControl-44442; do
    assert_contains "$uninstaller_windows" "'$firewall_rule'" \
        "Windows-Uninstaller kennt nur eigene Regel $firewall_rule"
done
assert_contains "$uninstaller_windows" "Get-NetFirewallRule -Name \$ruleName" \
    "Windows-Uninstaller entfernt Firewallregeln über exakte Namen"
assert_absent "$uninstaller_windows" "Get-NetFirewallRule -Group" \
    "Windows-Uninstaller entfernt keine fremden Regeln über eine breite Gruppe"
assert_contains "$installer_linux" "validate_started_services" \
    "Linux validiert gestartete systemd-Dienste"
assert_contains "$installer_linux" "NRestarts" \
    "Linux erkennt Restart-Schleifen in der Startphase"
assert_contains "$installer_linux" "--property=MainPID" \
    "Linux übernimmt bei Reinstall nur Listener des eigenen Dienstprozesses"
assert_contains "$installer_linux" "wait_for_http" \
    "Linux prüft Bridge Control und Web View funktional"
assert_absent "$installer_linux" "ufw allow" \
    "Linux-Installer legt keine UFW-Regel an"
assert_absent "$installer_linux" "firewall-cmd --add" \
    "Linux-Installer legt keine firewalld-Regel an"
assert_absent "$installer_linux" "nft add" \
    "Linux-Installer legt keine nftables-Regel an"
windows_preflight=$(sed -n '/^function Invoke-PortPreflight {/,/^}/p' "$installer_windows")
[[ "$windows_preflight" != *'$SourcePort'* ]] \
    && ok "Windows behandelt TCP 4444 nicht als lokalen Listener" \
    || bad "Windows behandelt TCP 4444 nicht als lokalen Listener" "$windows_preflight"

assert_file "$windows_legacy_fixture" "Windows-CRLF-Legacy-Fixture vorhanden"
grep -q $'\r$' "$windows_legacy_fixture" \
    && ok "Windows-Legacy-Fixture verwendet CRLF" \
    || bad "Windows-Legacy-Fixture verwendet CRLF"
assert_contains "$windows_legacy_fixture" \
    'outputs.0.endpoint=ws\://127.0.0.1\:8081/bridge/v1/channels/local' \
    "Windows-Fixture enthält echtes Properties.store-Escaping"
assert_contains "$windows_legacy_fixture" \
    'outputs.1.endpoint=ws\://127.0.0.1\:9081/bridge/v1/channels/local' \
    "Windows-Fixture enthält einen unverändert zu lassenden Port"
assert_contains "$windows_legacy_fixture" \
    'outputs.2.endpoint=ws\://192.168.1.20\:8081/bridge/v1/channels/local' \
    "Windows-Fixture enthält einen unverändert zu lassenden Host"
assert_contains "$windows_legacy_fixture" \
    'outputs.3.endpoint=ws\://127.0.0.1\:44441/bridge/v1/channels/local' \
    "Windows-Fixture enthält den bereits aktuellen Endpunkt"

echo
echo "=== Windows: Java-Erkennung über den Konsolen-Launcher ==="
# Suchmuster und Mindestversion werden aus dem Installer selbst gelesen, damit die
# Fixtures den real verwendeten Vertrag prüfen und nicht eine Kopie davon.
java_pattern=$(grep -oE "java\\\\[.]specification\\\\[.]version[^']*" "$installer_windows" | head -1)
windows_java_release=$(grep -oP '^\$JavaRelease\s*=\s*\K[0-9]+' "$installer_windows")
[[ -n "$java_pattern" ]] \
    && ok "Suchmuster der Versionsprobe ist im Installer auffindbar" \
    || bad "Suchmuster der Versionsprobe ist im Installer auffindbar"
assert_equals "$windows_java_release" "$WINLAUFEN_JAVA_RELEASE" \
    "Windows-Installer verlangt dieselbe Java-Version wie das Manifest"

java_major_of() {
    python3 - "$java_pattern" "$1" <<'PY'
import re
import sys

pattern, path = sys.argv[1], sys.argv[2]
with open(path, encoding='utf-8') as handle:
    match = re.search(pattern, handle.read())
print(match.group(1) if match else '0')
PY
}

assert_java_major() {
    local fixture="$java_version_fixtures/$1"
    assert_file "$fixture" "Java-Fixture vorhanden: $1"
    assert_equals "$(java_major_of "$fixture")" "$2" "$3"
}

assert_java_decision() {
    local fixture="$java_version_fixtures/$1" major decision
    major=$(java_major_of "$fixture")
    decision="abgelehnt"
    ((major >= windows_java_release)) && decision="akzeptiert"
    assert_equals "$decision" "$2" "$3"
}

assert_java_major temurin-25.txt 25 "Echte JDK-25-Ausgabe ergibt Major 25"
assert_java_major java-26.txt 26 "Neuere Java-Version wird korrekt gelesen"
assert_java_major java-21.txt 21 "Ältere Java-Version wird korrekt gelesen"
assert_java_major java-8.txt 1 "Legacy-Schema 1.8 ergibt Major 1"
assert_java_major unparsebar.txt 0 "Nicht parsebare Ausgabe ergibt keine Version"
assert_java_major javaw-leer.txt 0 "Leere Ausgabe ergibt keine Version"
# java.vm.specification.version steht in echter Ausgabe daneben und trägt eine
# andere Zahl; ein zu weites Muster würde die falsche Zeile lesen.
assert_java_major vm-distraktor.txt 25 \
    "java.vm.specification.version wird nicht mit java.specification.version verwechselt"

assert_java_decision temurin-25.txt akzeptiert "Java 25 wird akzeptiert"
assert_java_decision java-26.txt akzeptiert "Java > 25 wird akzeptiert"
assert_java_decision java-21.txt abgelehnt "Java < 25 wird abgelehnt"
assert_java_decision java-8.txt abgelehnt "Java 1.8 wird abgelehnt"
assert_java_decision unparsebar.txt abgelehnt "Nicht parsebare Ausgabe wird abgelehnt"
# Regression: genau so verhielt sich javaw.exe unter Windows 11 - die Probe lieferte
# nichts und eine korrekt installierte Java-25-Runtime wurde abgelehnt.
assert_java_decision javaw-leer.txt abgelehnt \
    "Eine leere Probenausgabe darf nie als gültige Runtime durchgehen"

# Java schreibt -XshowSettings auf stderr. stdout ist leer, der Informationsträger
# ist der Fehlerstrom - genau deshalb muss der Installer beide Ströme zusammenführen.
java_major_of_streams() {
    python3 - "$java_pattern" "$1" "$2" <<'PY'
import re
import sys

pattern, stdout_path, stderr_path = sys.argv[1], sys.argv[2], sys.argv[3]


def read(path):
    with open(path, encoding='utf-8') as handle:
        return handle.read()


match = re.search(pattern, read(stdout_path) + '\n' + read(stderr_path))
print(match.group(1) if match else '0')
PY
}

assert_file "$java_version_fixtures/stdout-leer.txt" "Leerer stdout-Strom als Fixture vorhanden"
assert_equals \
    "$(java_major_of_streams "$java_version_fixtures/stdout-leer.txt" "$java_version_fixtures/temurin-25.txt")" \
    "25" "Version wird gelesen, wenn sie ausschließlich auf stderr steht"
assert_equals \
    "$(java_major_of_streams "$java_version_fixtures/temurin-25.txt" "$java_version_fixtures/stdout-leer.txt")" \
    "25" "Version wird auch gelesen, wenn sie auf stdout steht"
assert_equals \
    "$(java_major_of_streams "$java_version_fixtures/stdout-leer.txt" "$java_version_fixtures/javaw-leer.txt")" \
    "0" "Zwei leere Ströme ergeben keine Version"

windows_java_probe=$(sed -n '/^function Get-JavaMajorVersion {/,/^}$/p' "$installer_windows")
[[ "$windows_java_probe" == *'-XshowSettings:properties'* ]] \
    && ok "Versionsprobe fragt die Java-Properties ab" \
    || bad "Versionsprobe fragt die Java-Properties ab"
[[ "$windows_java_probe" != *javaw* ]] \
    && ok "Versionsprobe verwendet nie javaw.exe" \
    || bad "Versionsprobe verwendet nie javaw.exe" "$windows_java_probe"

# Regression: unter Windows PowerShell 5.1 werden native stderr-Zeilen zu
# ErrorRecords. Mit dem global gesetzten $ErrorActionPreference = 'Stop' bricht
# "& $JavaExe ... 2>&1" deshalb schon bei der ersten Java-Ausgabezeile ab. Die
# Probe muss daher einen eigenen Prozess mit umgeleiteten Strömen verwenden.
[[ "$windows_java_probe" == *'System.Diagnostics.ProcessStartInfo'* ]] \
    && ok "Versionsprobe startet einen eigenen Prozess statt eines PowerShell-Aufrufs" \
    || bad "Versionsprobe startet einen eigenen Prozess statt eines PowerShell-Aufrufs" \
        "$windows_java_probe"
[[ "$windows_java_probe" == *'[System.Diagnostics.Process]::Start($startInfo)'* ]] \
    && ok "Versionsprobe startet den Prozess über System.Diagnostics.Process" \
    || bad "Versionsprobe startet den Prozess über System.Diagnostics.Process"
[[ "$windows_java_probe" == *'$startInfo.UseShellExecute = $false'* ]] \
    && ok "Versionsprobe läuft ohne Shell" \
    || bad "Versionsprobe läuft ohne Shell"
[[ "$windows_java_probe" == *'$startInfo.RedirectStandardOutput = $true'* ]] \
    && ok "Versionsprobe leitet stdout selbst um" \
    || bad "Versionsprobe leitet stdout selbst um"
[[ "$windows_java_probe" == *'$startInfo.RedirectStandardError = $true'* ]] \
    && ok "Versionsprobe leitet stderr selbst um" \
    || bad "Versionsprobe leitet stderr selbst um"
[[ "$windows_java_probe" == *'$process.StandardOutput.ReadToEndAsync()'* ]] \
    && ok "Versionsprobe liest stdout vollständig" \
    || bad "Versionsprobe liest stdout vollständig"
[[ "$windows_java_probe" == *'$process.StandardError.ReadToEndAsync()'* ]] \
    && ok "Versionsprobe liest stderr vollständig" \
    || bad "Versionsprobe liest stderr vollständig"
[[ "$windows_java_probe" == *'$process.WaitForExit()'* ]] \
    && ok "Versionsprobe wartet auf das Prozessende" \
    || bad "Versionsprobe wartet auf das Prozessende"
[[ "$windows_java_probe" == *'[regex]::Match('* ]] \
    && ok "Version wird über einen .NET-Regex statt über Select-String gelesen" \
    || bad "Version wird über einen .NET-Regex statt über Select-String gelesen"
[[ "$windows_java_probe" == *'"$standardOutput`n$standardError"'* ]] \
    && ok "Version wird aus stdout und stderr zusammen geparst" \
    || bad "Version wird aus stdout und stderr zusammen geparst"
[[ "$windows_java_probe" != *'2>&1'* ]] \
    && ok "Versionsprobe hängt nicht mehr am PowerShell-Fehlerstrom" \
    || bad "Versionsprobe hängt nicht mehr am PowerShell-Fehlerstrom" "$windows_java_probe"
[[ "$windows_java_probe" != *'& $JavaExe'* ]] \
    && ok "Der alte direkte Native-Aufruf als Versionsprobe ist entfernt" \
    || bad "Der alte direkte Native-Aufruf als Versionsprobe ist entfernt" "$windows_java_probe"
assert_contains "$installer_windows" "\$ErrorActionPreference = 'Stop'" \
    "Der Installer bleibt global auf ErrorActionPreference Stop"
[[ "$windows_java_probe" != *'ErrorActionPreference'* ]] \
    && ok "Die Probe verstellt ErrorActionPreference nicht als Ersatzlösung" \
    || bad "Die Probe verstellt ErrorActionPreference nicht als Ersatzlösung"
assert_absent "$installer_windows" "Get-Command 'javaw.exe'" \
    "javaw.exe wird nicht mehr als Java-Kandidat gesucht"
assert_contains "$installer_windows" "Get-Command 'java.exe' -All" \
    "System-Java wird über den Konsolen-Launcher im PATH gesucht"
assert_contains "$installer_windows" "Join-Path \$env:JAVA_HOME 'bin\\java.exe'" \
    "JAVA_HOME wird berücksichtigt und über Join-Path zusammengesetzt"

windows_java_launcher=$(sed -n '/^function Select-RuntimeLauncher {/,/^}$/p' "$installer_windows")
[[ "$windows_java_launcher" == *'Split-Path -Parent $JavaExe'* ]] \
    && ok "Startprogramm stammt aus derselben Java-Installation wie die Prüfung" \
    || bad "Startprogramm stammt aus derselben Java-Installation wie die Prüfung"
[[ "$windows_java_launcher" == *"'javaw.exe'"* ]] \
    && ok "javaw.exe bleibt das bevorzugte Startprogramm ohne Konsolenfenster" \
    || bad "javaw.exe bleibt das bevorzugte Startprogramm ohne Konsolenfenster"
[[ "$windows_java_launcher" == *'return $JavaExe'* ]] \
    && ok "Ohne javaw.exe wird die geprüfte java.exe gestartet" \
    || bad "Ohne javaw.exe wird die geprüfte java.exe gestartet"

windows_java_resolve=$(sed -n '/^function Resolve-JavaExecutable {/,/^}$/p' "$installer_windows")
[[ "$windows_java_resolve" == *'runtime\bin\java.exe'* ]] \
    && ok "Gebündelte Runtime wird über ihre java.exe geprüft" \
    || bad "Gebündelte Runtime wird über ihre java.exe geprüft"
[[ "$windows_java_resolve" == *'Get-JavaMajorVersion -JavaExe $bundledJava'* ]] \
    && ok "Gebündelte Runtime wird nicht ungeprüft akzeptiert" \
    || bad "Gebündelte Runtime wird nicht ungeprüft akzeptiert"
[[ "$windows_java_resolve" == *'Join-Path $InstalledPrefix "runtime\bin\$bundledLauncher"'* ]] \
    && ok "Gebündelte Runtime wird nach der Installation aus dem Zielpfad gestartet" \
    || bad "Gebündelte Runtime wird nach der Installation aus dem Zielpfad gestartet"
assert_contains "$installer_windows" '"$javaExe" "-D$BridgeConfigProperty=$bridgeConfig"' \
    "Der Bridge-Starter zitiert den Java-Pfad, damit Leerzeichen zulässig bleiben"
assert_contains "$installer_windows" "\`\$startInfo.FileName = '\$javaExe'" \
    "Der Live-Server-Starter übergibt den Java-Pfad als eigenständigen Dateinamen"

echo
echo "=== Windows: Live-Server-Starter bleibt am Java-Prozess ==="
# Der erzeugte Starter wird aus der Installer-Vorlage gerendert und als
# PowerShell-Quelltext geprüft. Das deckt zugleich Escaping-Fehler in der
# Here-String-Vorlage auf, die sonst erst auf Windows sichtbar würden.
live_launcher_rendered="$work/start-live-server.ps1"
python3 - "$installer_windows" "$live_launcher_rendered" <<'PY'
import re
import sys

source = open(sys.argv[1], encoding='utf-8').read()
block = re.search(r'\$launcherScript = @"\n(.*?)\n"@\n', source, re.S).group(1)
values = {
    'ProductName': 'WinLaufen Web',
    'liveConfig': r'C:\ProgramData\WinLaufen Web\live-server.properties',
    'InstallPrefix': r'C:\Program Files\WinLaufen Web',
    'LiveJar': 'winlaufen-web-live-server.jar',
    'javaExe': r'C:\Program Files\Eclipse Adoptium\jdk-25\bin\javaw.exe',
}
out, index = [], 0
while index < len(block):
    if block[index] == '`' and index + 1 < len(block) and block[index + 1] == '$':
        out.append('$')
        index += 2
        continue
    if block[index] == '$':
        name = re.match(r'\$([A-Za-z_][A-Za-z0-9_]*)', block[index:])
        if name and name.group(1) in values:
            out.append(values[name.group(1)])
            index += name.end()
            continue
    out.append(block[index])
    index += 1
open(sys.argv[2], 'w', encoding='utf-8').write(''.join(out))
PY
assert_file "$live_launcher_rendered" "Live-Server-Starter lässt sich aus der Vorlage rendern"
assert_contains "$live_launcher_rendered" '[System.Diagnostics.Process]::Start($startInfo)' \
    "Der Starter startet den Java-Prozess selbst"
assert_contains "$live_launcher_rendered" '$process.WaitForExit()' \
    "Der Starter wartet explizit auf das Ende des Java-Prozesses"
assert_contains "$live_launcher_rendered" 'exit $process.ExitCode' \
    "Der Starter reicht den Exit-Code des Java-Prozesses weiter"
assert_contains "$live_launcher_rendered" '$startInfo.UseShellExecute = $false' \
    "Der Starter läuft ohne Shell"
assert_contains "$live_launcher_rendered" '$startInfo.CreateNoWindow = $true' \
    "Der Starter zeigt keine zusätzliche Konsole"
assert_contains "$live_launcher_rendered" \
    "\$startInfo.FileName = 'C:\Program Files\Eclipse Adoptium\jdk-25\bin\javaw.exe'" \
    "javaw.exe bleibt der Launcher und sein Pfad mit Leerzeichen bleibt unversehrt"
assert_contains "$live_launcher_rendered" \
    "\$arguments += 'C:\Program Files\WinLaufen Web\lib\winlaufen-web-live-server.jar'" \
    "Der JAR-Pfad mit Leerzeichen wird als eigenes Argument übergeben"
assert_contains "$live_launcher_rendered" 'ConvertTo-CommandLineArgument $_' \
    "Jedes Argument läuft einzeln durch die Quotierung"
assert_contains "$live_launcher_rendered" '$arguments += "-D$key=$($config[$key])"' \
    "Die -D-Argumente entstehen weiterhin einzeln aus der Properties-Datei"
# Regression: genau dieser fire-and-forget-Aufruf liess die geplante Aufgabe
# auf Ready zurückfallen, obwohl javaw.exe weiterlief.
assert_absent "$live_launcher_rendered" '@arguments' \
    "Der alte fire-and-forget-Aufruf mit @arguments ist entfernt"
assert_absent "$live_launcher_rendered" 'exit $LASTEXITCODE' \
    "Der Starter verlässt sich nicht mehr auf LASTEXITCODE eines nicht abgewarteten Prozesses"
assert_absent "$live_launcher_rendered" '`$' \
    "Die gerenderte Vorlage enthält keine unaufgelösten Escapes"
for unresolved in '$ProductName' '$liveConfig' '$InstallPrefix' '$LiveJar' '$javaExe'; do
    assert_absent "$live_launcher_rendered" "$unresolved" \
        "Die gerenderte Vorlage enthält keine unaufgelöste Installer-Variable $unresolved"
done
assert_contains "$installer_windows" 'powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "$ps1"' \
    "Die geplante Aufgabe ruft den Starter weiterhin ohne Konsolenfenster auf"
assert_contains "$installer_windows" 'exit /b %errorlevel%' \
    "Die cmd-Hülle reicht den Exit-Code an die geplante Aufgabe weiter"

# Die Quotierungsregeln von CommandLineToArgvW werden hier als Vertrag geprüft.
# Die PowerShell-Umsetzung wird zusätzlich strukturell festgenagelt, damit eine
# Änderung dort nicht unbemerkt an diesem Modell vorbeigeht.
if python3 - "$live_launcher_rendered" > "$work/argument-quoting.log" 2>&1 <<'PY'
import re
import sys


def quote(value):
    if len(value) > 0 and not re.search(r'[\s"]', value):
        return value
    out, index = ['"'], 0
    while index < len(value):
        backslashes = 0
        while index < len(value) and value[index] == '\\':
            backslashes += 1
            index += 1
        if index == len(value):
            out.append('\\' * (backslashes * 2))
        elif value[index] == '"':
            out.append('\\' * (backslashes * 2 + 1))
            out.append('"')
            index += 1
        else:
            out.append('\\' * backslashes)
            out.append(value[index])
            index += 1
    out.append('"')
    return ''.join(out)


def parse(commandline):
    args, current, index, quoted, started = [], [], 0, False, False
    while index < len(commandline):
        char = commandline[index]
        if char == '\\':
            backslashes = 0
            while index < len(commandline) and commandline[index] == '\\':
                backslashes += 1
                index += 1
            if index < len(commandline) and commandline[index] == '"':
                current.append('\\' * (backslashes // 2))
                started = True
                if backslashes % 2 == 0:
                    quoted = not quoted
                else:
                    current.append('"')
                index += 1
            else:
                current.append('\\' * backslashes)
                started = True
        elif char == '"':
            quoted = not quoted
            started = True
            index += 1
        elif char in ' \t' and not quoted:
            if started:
                args.append(''.join(current))
                current, started = [], False
            index += 1
        else:
            current.append(char)
            started = True
            index += 1
    if started:
        args.append(''.join(current))
    return args


cases = [
    '-Dwinlaufen.live.http.port=44440',
    '-Dwinlaufen.live.secret=local-development-secret',
    '-jar',
    r'C:\Program Files\WinLaufen Web\lib\winlaufen-web-live-server.jar',
    '-Dwinlaufen.live.secret=geheim mit leerzeichen',
    '-Dkey=C:\\pfad mit\\',
    '-Dkey=hat"anfuehrungszeichen',
    '',
]
for case in cases:
    assert parse(quote(case)) == [case], (case, quote(case), parse(quote(case)))
joined = ' '.join(quote(case) for case in cases if case != '')
assert parse(joined) == [case for case in cases if case != ''], joined

rendered = open(sys.argv[1], encoding='utf-8').read()
for marker in ("[char]'\\'", "[char]'\"'", '$backslashes * 2', '$backslashes * 2 + 1',
               "-notmatch '[\\s\"]'"):
    assert marker in rendered, marker
PY
then
    ok "Argumentquotierung überlebt Leerzeichen, Backslashes und Anführungszeichen"
else
    bad "Argumentquotierung überlebt Leerzeichen, Backslashes und Anführungszeichen" \
        "$(tail -3 "$work/argument-quoting.log" | tr '\n' ' ')"
fi

windows_wait_runtime=$(sed -n '/^function Wait-InstalledRuntime {/,/^}$/p' "$installer_windows")
[[ "$windows_wait_runtime" == *'Test-TaskRunning -TaskName $TaskName'* ]] \
    && ok "Readiness verlangt weiterhin eine laufende geplante Aufgabe" \
    || bad "Readiness verlangt weiterhin eine laufende geplante Aufgabe"
[[ "$windows_wait_runtime" == *'Get-ListenerOwner -Port $port'* ]] \
    && ok "Readiness verlangt weiterhin die eigenen Listener" \
    || bad "Readiness verlangt weiterhin die eigenen Listener"
[[ "$windows_wait_runtime" == *'Test-HttpEndpoint -Port $HttpPort'* ]] \
    && ok "Readiness verlangt weiterhin einen erreichbaren HTTP-Endpunkt" \
    || bad "Readiness verlangt weiterhin einen erreichbaren HTTP-Endpunkt"
[[ "$windows_wait_runtime" == *'Start-Sleep -Seconds 3'* ]] \
    && ok "Readiness durchläuft weiterhin eine Stabilitätsphase" \
    || bad "Readiness durchläuft weiterhin eine Stabilitätsphase"
assert_contains "$installer_windows" '-Ports @($LiveHttpPort, $LiveWsPort) -HttpPort $LiveHttpPort' \
    "Live Server wird weiterhin auf 44440 und 44441 plus HTTP geprüft"
assert_contains "$installer_windows" '-Ports @($ControlPort) -HttpPort $ControlPort' \
    "Bridge wird weiterhin auf 44442 plus HTTP geprüft"
assert_contains "$installer_windows" \
    '"$javaExe" "-D$BridgeConfigProperty=$bridgeConfig" -jar "$InstallPrefix\lib\$BridgeJar"' \
    "Der Bridge-Starter bleibt unverändert; cmd wartet dort bereits auf javaw.exe"

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

code_control_bind=$(grep -oP 'DEFAULT_CONTROL_BIND = "\K[^"]+' \
    "$repository_root/bridge/src/main/java/de/winlaufen/web/bridge/config/BridgeConfigStore.java")
assert_equals "$code_control_bind" "$WINLAUFEN_CONTROL_BIND" \
    "Bridge-Control-Bind stimmt mit dem Code überein"

code_http_bind=$(grep -oP 'winlaufen\.live\.http\.bind", "\K[^"]+' \
    "$repository_root/live-server/src/main/java/de/winlaufen/web/liveserver/config/LiveServerConfig.java")
assert_equals "$code_http_bind" "$WINLAUFEN_LIVE_HTTP_BIND" \
    "Live-Server-HTTP-Bind stimmt mit dem Code überein"

code_ws_bind=$(grep -oP 'winlaufen\.live\.websocket\.bind", "\K[^"]+' \
    "$repository_root/live-server/src/main/java/de/winlaufen/web/liveserver/config/LiveServerConfig.java")
assert_equals "$code_ws_bind" "$WINLAUFEN_LIVE_WS_BIND" \
    "Live-Server-WebSocket-Bind stimmt mit dem Code überein"

windows_control_port=$(grep -oP '^\$ControlPort\s*=\s*\K[0-9]+' "$installer_windows")
windows_http_port=$(grep -oP '^\$LiveHttpPort\s*=\s*\K[0-9]+' "$installer_windows")
windows_ws_port=$(grep -oP '^\$LiveWsPort\s*=\s*\K[0-9]+' "$installer_windows")
windows_control_bind=$(grep -oP '^\$ControlBind\s*=\s*'"'"'\K[^'"'"']+' "$installer_windows")
windows_http_bind=$(grep -oP '^\$LiveHttpBind\s*=\s*'"'"'\K[^'"'"']+' "$installer_windows")
windows_ws_bind=$(grep -oP '^\$LiveWsBind\s*=\s*'"'"'\K[^'"'"']+' "$installer_windows")
assert_equals "$windows_control_port" "$WINLAUFEN_CONTROL_PORT" \
    "Windows-Installer übernimmt den Bridge-Control-Port aus dem Manifestvertrag"
assert_equals "$windows_http_port" "$WINLAUFEN_LIVE_HTTP_PORT" \
    "Windows-Installer übernimmt den HTTP-Port aus dem Manifestvertrag"
assert_equals "$windows_ws_port" "$WINLAUFEN_LIVE_WS_PORT" \
    "Windows-Installer übernimmt den WebSocket-Port aus dem Manifestvertrag"
assert_equals "$windows_control_bind" "$WINLAUFEN_CONTROL_BIND" \
    "Windows-Installer übernimmt den Bridge-Control-Bind aus dem Manifestvertrag"
assert_equals "$windows_http_bind" "$WINLAUFEN_LIVE_HTTP_BIND" \
    "Windows-Installer übernimmt den HTTP-Bind aus dem Manifestvertrag"
assert_equals "$windows_ws_bind" "$WINLAUFEN_LIVE_WS_BIND" \
    "Windows-Installer übernimmt den WebSocket-Bind aus dem Manifestvertrag"

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
echo "=== Reproduzierbarer Developer-Build ==="
assert_file "$repository_root/mvnw" "Maven Wrapper für Unix vorhanden"
assert_file "$repository_root/mvnw.cmd" "Maven Wrapper für Windows vorhanden"
assert_file "$repository_root/.mvn/wrapper/maven-wrapper.properties" \
    "Maven-Wrapper-Konfiguration vorhanden"
assert_contains "$repository_root/.mvn/wrapper/maven-wrapper.properties" \
    "apache-maven-3.9.16-bin.zip" "Maven Wrapper pinnt Maven 3.9.16"
assert_contains "$repository_root/pom.xml" "<artifactId>maven-compiler-plugin</artifactId>" \
    "Compiler-Plugin ist explizit konfiguriert"
assert_contains "$repository_root/pom.xml" "<version>3.15.0</version>" \
    "Compiler-Plugin-Version ist explizit gepinnt"
assert_contains "$repository_root/pom.xml" "<release>\${maven.compiler.release}</release>" \
    "Compiler verwendet den zentralen release-25-Vertrag"
assert_contains "$repository_root/installer/common/build-dist.sh" "./mvnw -B -q package" \
    "Linux-Distribution verwendet den Maven Wrapper"
assert_contains "$repository_root/installer/common/build-dist.ps1" "'mvnw.cmd'" \
    "Windows-Distribution verwendet den Maven Wrapper"

echo
echo "=== Dokumentationsvertrag ==="
assert_contains "$repository_root/README.md" "./mvnw clean package" \
    "README dokumentiert den Wrapper-Build"
assert_contains "$repository_root/docs/INSTALLATION.md" ".\\mvnw.cmd clean package" \
    "Windows-Developer-Build verwendet den Wrapper"
assert_contains "$repository_root/docs/INSTALLATION.md" \
    "| Bridge | WinLaufen-PC | TCP 4444 |" "Installation dokumentiert TCP 4444 als ausgehendes Ziel"
assert_contains "$repository_root/docs/INSTALLATION.md" \
    "| Viewer | Live Server | TCP 44440 |" "Installation dokumentiert Public HTTP"
assert_contains "$repository_root/docs/INSTALLATION.md" \
    "| Browser | Live Server | TCP 44441 |" "Installation dokumentiert Browser-Live"
assert_contains "$repository_root/docs/INSTALLATION.md" \
    "| Admin | Bridge | TCP 44442 |" "Installation dokumentiert Bridge Control"
assert_contains "$repository_root/docs/INSTALLATION.md" \
    "Der Installer aktiviert weder UFW" "Linux-Firewallverhalten ist dokumentiert"
assert_contains "$repository_root/docs/INSTALLATION.md" \
    "PowerShell mit Administratorrechten" "Windows-Adminanforderung ist dokumentiert"
assert_absent "$repository_root/README.md" \
    "empfohlen für die Installation direkt auf dem WinLaufen-PC" \
    "README beschränkt All-in-One nicht auf den WinLaufen-PC"

echo
echo "----------------------------------------"
printf 'Installer-Tests: %s bestanden, %s fehlgeschlagen\n' "$passed" "$failed"
((failed == 0)) || exit 1
