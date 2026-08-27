#!/usr/bin/env bash

set -u

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
# shellcheck source=local-common.sh
source "$script_dir/local-common.sh"

pid=$(read_pid 2>/dev/null || true)
if [[ -z "$pid" ]] || ! managed_pid "$pid"; then
    if [[ -f "$pid_file" ]]; then rm -f -- "$pid_file"; fi
    echo "WinLaufen Web läuft nicht."
    exit 0
fi

kill -TERM "$pid"
for ((attempt = 0; attempt < 100; attempt++)); do
    if ! managed_pid "$pid"; then
        rm -f -- "$pid_file"
        echo "WinLaufen Web gestoppt (PID $pid)."
        exit 0
    fi
    sleep 0.1
done

echo "WinLaufen Web wurde nicht innerhalb des Zeitlimits beendet (PID $pid)." >&2
exit 1
