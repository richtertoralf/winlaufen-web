#!/usr/bin/env bash

set -u

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
# shellcheck source=local-common.sh
source "$script_dir/local-common.sh"

pid=$(read_pid 2>/dev/null || true)
if [[ -z "$pid" ]]; then
    if [[ -f "$pid_file" ]]; then
        echo "STALE PID FILE"
        echo "Datei: $pid_file"
    else
        echo "STOPPED"
    fi
    exit 0
fi
if ! managed_pid "$pid"; then
    echo "STALE PID FILE"
    echo "PID: $pid"
    echo "Datei: $pid_file"
    exit 0
fi

echo "RUNNING"
echo "PID: $pid"
echo "HTTP 8080: $(port_listening 8080 && echo LISTENING || echo NOT LISTENING)"
echo "WebSocket 8081: $(port_listening 8081 && echo LISTENING || echo NOT LISTENING)"
