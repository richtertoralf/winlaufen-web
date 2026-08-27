#!/usr/bin/env bash

set -u

usage() {
    echo "Usage: $0 HOST [PORT]" >&2
    exit 2
}

fail() {
    echo >&2
    echo "ERROR: $*" >&2
    echo "Client application data sent: NO" >&2
    echo "RESULT: FAIL" >&2
    exit 1
}

[[ $# -ge 1 && $# -le 2 ]] || usage

host=$1
port=${2:-4444}
[[ -n "$host" ]] || usage
[[ "$port" =~ ^[0-9]+$ ]] && ((port >= 1 && port <= 65535)) || {
    echo "ERROR: PORT must be an integer from 1 to 65535" >&2
    exit 2
}

for command_name in nc timeout mktemp od tr grep wc rm; do
    command -v "$command_name" >/dev/null 2>&1 || {
        echo "ERROR: required command not found: $command_name" >&2
        exit 1
    }
done

nc -h 2>&1 | grep -q -- '-d' || {
    echo "ERROR: nc does not support the required passive -d option" >&2
    exit 1
}

capture_file=$(mktemp /tmp/winlaufen-clock-smoke.XXXXXX) || exit 1
trap 'rm -f -- "$capture_file"' EXIT HUP INT TERM

echo "WinLaufen Live Smoke Test"
echo "Host: $host"
echo "Port: $port"
echo

probe_output=$(timeout -k 1 4 nc -d -v -z -w 3 "$host" "$port" 2>&1)
probe_status=$?
if ((probe_status != 0)); then
    if [[ "$probe_output" == *"refused"* || "$probe_output" == *"timed out"* ]]; then
        fail "Port $port not reachable on host $host"
    fi
    fail "Host not reachable: $host ($probe_output)"
fi
echo "TCP $port reachable: OK"

timeout -k 1 11 nc -d "$host" "$port" >"$capture_file"
receive_status=$?
if ((receive_status != 0 && receive_status != 124)); then
    fail "Connection ended with an error while receiving data"
fi

byte_count=$(wc -c <"$capture_file")
((byte_count >= 4)) || fail "Fewer than 4 bytes received"

header=$(od -An -tx1 -N4 "$capture_file" | tr -d ' \n')
[[ "$header" == "aced0005" ]] || fail "No Java Serialization Header (expected AC ED 00 05)"
echo "Java Serialization: OK (AC ED 00 05)"
echo

mapfile -t clocks < <(LC_ALL=C grep -aoE 'Uhr[0-9]{2}:[0-9]{2}:[0-9]{2}' "$capture_file" || true)
((${#clocks[@]} >= 5)) || fail "Fewer than five WinLaufen clock values received"

echo "WinLaufen clock:"
printf '%s\n' "${clocks[@]}"

echo
echo "Clock telegrams received: OK"
echo "Client application data sent: NO"
echo
echo "RESULT: PASS"
