#!/usr/bin/env bash
# Reproducible two-process / multi-endpoint smoke test.
#
#   bridge  --WS-->  live server A
#           --WS-->  live server B
#
# Verifies: snapshot + ACK on both targets, failure isolation when one target dies, the surviving
# target staying current, target restart with full resynchronisation, presentation config being
# resynchronised as well, and a clean shutdown with free ports.
#
# It does NOT need a real WinLaufen installation: the bridge's source stays disconnected and the
# canonical revisions are driven by Bridge Control presentation changes.
#
# Usage: ./devtools/smoke-fanout.sh
set -u

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
repository_root=$(cd -- "$script_dir/.." && pwd -P)

bridge_jar="$repository_root/bridge/target/winlaufen-web-bridge.jar"
live_jar="$repository_root/live-server/target/winlaufen-web-live-server.jar"

for jar in "$bridge_jar" "$live_jar"; do
    [[ -f "$jar" ]] || { echo "ERROR: $jar fehlt; zuerst 'mvn package' ausführen." >&2; exit 1; }
done
for command_name in curl python3 java; do
    command -v "$command_name" >/dev/null 2>&1 \
        || { echo "ERROR: benötigtes Kommando fehlt: $command_name" >&2; exit 1; }
done

work=$(mktemp -d /tmp/winlaufen-smoke-fanout.XXXXXX) || exit 1
pids=()
failures=0

cleanup() {
    local pid
    for pid in "${pids[@]:-}"; do
        [[ -n "$pid" ]] && kill -TERM "$pid" 2>/dev/null
    done
    for pid in "${pids[@]:-}"; do
        [[ -n "$pid" ]] || continue
        for _ in $(seq 1 50); do kill -0 "$pid" 2>/dev/null || break; sleep .1; done
        kill -0 "$pid" 2>/dev/null && kill -KILL "$pid" 2>/dev/null
    done
    rm -rf -- "$work"
}
trap cleanup EXIT HUP INT TERM

check() {
    if [[ "$2" == "$3" ]]; then
        printf 'PASS  %s\n' "$1"
    else
        printf 'FAIL  %s\n        erwartet: %s\n        erhalten: %s\n' "$1" "$3" "$2" >&2
        failures=$((failures + 1))
    fi
}

free_port() {
    python3 - <<'PY'
import socket
with socket.socket() as s:
    s.bind(("127.0.0.1", 0))
    print(s.getsockname()[1])
PY
}

wait_for_port() {
    local port=$1
    for _ in $(seq 1 100); do
        (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null && { exec 3<&-; exec 3>&-; return 0; }
        sleep .1
    done
    return 1
}

port_free() {
    python3 - "$1" <<'PROBE'
import socket, sys
with socket.socket() as probe:
    probe.settimeout(1)
    raise SystemExit(1 if probe.connect_ex(("127.0.0.1", int(sys.argv[1]))) == 0 else 0)
PROBE
}

# Reads one JSON field from a URL via python, so no jq dependency is required.
field() {
    curl -s --max-time 5 "$1" 2>/dev/null | python3 -c "
import json,sys
try:
    data = json.load(sys.stdin)
except Exception:
    print('<no-json>'); raise SystemExit
for key in sys.argv[1].split('.'):
    if isinstance(data, list):
        data = data[int(key)]
    else:
        data = data.get(key) if isinstance(data, dict) else None
    if data is None:
        print('null'); raise SystemExit
print(data)
" "$2"
}

target_state() {
    curl -s --max-time 5 "http://127.0.0.1:$control_port/api/v1/status" 2>/dev/null | python3 -c "
import json,sys
data = json.load(sys.stdin)
for output in data['outputs']:
    if output['targetId'] == sys.argv[1]:
        print(output['state'], output['lastAckedSourceRevision'])
        raise SystemExit
print('MISSING -1')
" "$1"
}

await() {
    local description=$1 expected=$2 timeout=$3
    shift 3
    local deadline=$((SECONDS + timeout))
    local actual=""
    while ((SECONDS < deadline)); do
        actual=$("$@")
        [[ "$actual" == "$expected" ]] && { check "$description" "$actual" "$expected"; return 0; }
        sleep .2
    done
    check "$description" "$actual" "$expected"
    return 1
}

http_a=$(free_port); ws_a=$(free_port)
http_b=$(free_port); ws_b=$(free_port)
control_port=$(free_port)

mkdir -p "$work/home/.winlaufen-web"
cat > "$work/home/.winlaufen-web/config.properties" <<EOF
config.version=2
source.type=WINLAUFEN
source.host=127.0.0.1
bridge.control.bind=127.0.0.1
bridge.control.port=$control_port
outputs.count=2
outputs.0.id=alpha
outputs.0.type=LOCAL
outputs.0.enabled=true
outputs.0.endpoint=ws://127.0.0.1:$ws_a/bridge/v1/channels/local
outputs.0.channelId=local
outputs.0.secret=alpha-smoke-secret
outputs.1.id=beta
outputs.1.type=LOCAL
outputs.1.enabled=true
outputs.1.endpoint=ws://127.0.0.1:$ws_b/bridge/v1/channels/local
outputs.1.channelId=local
outputs.1.secret=beta-smoke-secret
presentation.showClub=true
presentation.showAssociation=true
presentation.showNation=false
presentation.showShooting=true
presentation.showMessages=false
EOF

# Sets the global "started_pid". Must not be called in a command substitution, because a
# subshell could not append to the parent's cleanup list.
start_live_server() {
    local name=$1 http=$2 ws=$3 secret=$4
    java -Dwinlaufen.live.http.bind=127.0.0.1 -Dwinlaufen.live.http.port="$http" \
         -Dwinlaufen.live.websocket.bind=127.0.0.1 -Dwinlaufen.live.websocket.port="$ws" \
         -Dwinlaufen.live.secret="$secret" \
         -jar "$live_jar" >"$work/$name.log" 2>&1 &
    started_pid=$!
    pids+=("$started_pid")
}

echo "== Start: zwei Live Server und eine Bridge, drei getrennte Prozesse =="
start_live_server live-a "$http_a" "$ws_a" alpha-smoke-secret; pid_a=$started_pid
start_live_server live-b "$http_b" "$ws_b" beta-smoke-secret; pid_b=$started_pid
wait_for_port "$http_a" || { echo "Live Server A startete nicht" >&2; exit 1; }
wait_for_port "$http_b" || { echo "Live Server B startete nicht" >&2; exit 1; }

java -Duser.home="$work/home" -jar "$bridge_jar" >"$work/bridge.log" 2>&1 &
pid_bridge=$!
pids+=("$pid_bridge")
wait_for_port "$control_port" || { echo "Bridge startete nicht" >&2; exit 1; }

echo
echo "== 1. Fan-out: beide Targets bestaetigen denselben Vollsnapshot =="
await "Target alpha ist verbunden und hat bestaetigt" "CONNECTED 0" 15 target_state alpha
await "Target beta ist verbunden und hat bestaetigt" "CONNECTED 0" 15 target_state beta
check "Live Server A hat den Snapshot veroeffentlicht" \
    "$(field "http://127.0.0.1:$http_a/api/v1/state" publicationRevision)" "1"
check "Live Server B hat den Snapshot veroeffentlicht" \
    "$(field "http://127.0.0.1:$http_b/api/v1/state" publicationRevision)" "1"
check "Nation ist zunaechst ausgeblendet (A)" \
    "$(field "http://127.0.0.1:$http_a/api/v1/state" presentation.showNation)" "False"

echo
echo "== 2. Ausfall von Target A isoliert; B bleibt aktuell =="
kill -TERM "$pid_a"
for _ in $(seq 1 50); do kill -0 "$pid_a" 2>/dev/null || break; sleep .1; done
await "Target alpha meldet einen Verbindungsfehler" "RETRY_WAIT 0" 15 target_state alpha
check "Bridge laeuft weiter" "$(kill -0 "$pid_bridge" 2>/dev/null && echo up)" "up"
check "Target beta bleibt verbunden" "$(target_state beta | cut -d' ' -f1)" "CONNECTED"

echo
echo "== 3. Presentation-Aenderung erreicht das verbleibende Target =="
curl -s --max-time 5 -o /dev/null -X POST "http://127.0.0.1:$control_port/api/v1/config" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    -H "Origin: http://127.0.0.1:$control_port" \
    --data "sourceHost=127.0.0.1&targetCount=2\
&target.0.id=alpha&target.0.type=LOCAL&target.0.enabled=on&target.0.endpoint=ws://127.0.0.1:$ws_a/bridge/v1/channels/local&target.0.channelId=local&target.0.secret=\
&target.1.id=beta&target.1.type=LOCAL&target.1.enabled=on&target.1.endpoint=ws://127.0.0.1:$ws_b/bridge/v1/channels/local&target.1.channelId=local&target.1.secret=\
&showClub=on&showAssociation=on&showNation=on&showShooting=on"
await "Nation ist auf Live Server B sichtbar" "True" 15 \
    field "http://127.0.0.1:$http_b/api/v1/state" presentation.showNation

echo
echo "== 4. Target A kehrt zurueck und erhaelt einen Vollresync =="
start_live_server live-a2 "$http_a" "$ws_a" alpha-smoke-secret; pid_a=$started_pid
wait_for_port "$http_a" || { echo "Live Server A startete nicht neu" >&2; exit 1; }
await "Target alpha ist wieder verbunden" "CONNECTED" 20 \
    bash -c "curl -s --max-time 5 http://127.0.0.1:$control_port/api/v1/status | python3 -c \"
import json,sys
print([o for o in json.load(sys.stdin)['outputs'] if o['targetId']=='alpha'][0]['state'])\""
await "Vollresync: Nation ist auch auf Live Server A sichtbar" "True" 20 \
    field "http://127.0.0.1:$http_a/api/v1/state" presentation.showNation
check "Resync ohne Delta-Historie: A startete bei publicationRevision 1" \
    "$(field "http://127.0.0.1:$http_a/api/v1/state" publicationRevision)" "1"
check "Beide Targets haben dieselbe Revision bestaetigt" \
    "$(target_state alpha | cut -d' ' -f2)" "$(target_state beta | cut -d' ' -f2)"

echo
echo "== 5. Keine Bridge-Interna auf der oeffentlichen API =="
public_state=$(curl -s --max-time 5 "http://127.0.0.1:$http_a/api/v1/state")
check "Kein Secret im Public State" \
    "$(printf '%s' "$public_state" | grep -c 'smoke-secret')" "0"
check "Keine Bridge-Konfiguration im Public State" \
    "$(printf '%s' "$public_state" | grep -c 'sourceHost\|endpoint\|targets')" "0"
check "Kein Secret im Bridge-Control-Config-Endpunkt" \
    "$(curl -s --max-time 5 "http://127.0.0.1:$control_port/api/v1/config" | grep -c 'smoke-secret')" "0"
check "Kein Secret in den Logs" \
    "$(cat "$work"/*.log | grep -c 'smoke-secret')" "0"

echo
echo "== 6. Sauberes Shutdown, danach freie Ports =="
cleanup_ports=("$http_a" "$ws_a" "$http_b" "$ws_b" "$control_port")
for pid in "${pids[@]}"; do kill -TERM "$pid" 2>/dev/null; done
for pid in "${pids[@]}"; do
    for _ in $(seq 1 50); do kill -0 "$pid" 2>/dev/null || break; sleep .1; done
done
still_running=0
for pid in "${pids[@]}"; do kill -0 "$pid" 2>/dev/null && still_running=$((still_running + 1)); done
check "Keine Restprozesse" "$still_running" "0"
sleep 1
busy=0
for port in "${cleanup_ports[@]}"; do port_free "$port" || busy=$((busy + 1)); done
check "Alle Ports wieder frei" "$busy" "0"
pids=()

echo
if ((failures == 0)); then
    echo "RESULT: PASS"
    exit 0
fi
echo "RESULT: FAIL ($failures Pruefungen fehlgeschlagen)" >&2
exit 1
