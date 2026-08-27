#!/usr/bin/env bash

set -u

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
# shellcheck source=local-common.sh
source "$script_dir/local-common.sh"

if [[ ! -f "$application_jar" ]]; then
    echo "WinLaufen Web JAR fehlt: $application_jar" >&2
    echo "Bitte zuerst 'mvn package' ausführen." >&2
    exit 1
fi
for command_name in java ss grep; do
    command -v "$command_name" >/dev/null 2>&1 || { echo "Benötigtes Kommando fehlt: $command_name" >&2; exit 1; }
done

umask 077
mkdir -p -- "$runtime_root"

existing_pid=$(read_pid 2>/dev/null || true)
if [[ -n "$existing_pid" ]] && managed_pid "$existing_pid"; then
    echo "WinLaufen Web läuft bereits (PID $existing_pid)."
    exit 0
fi
if [[ -f "$pid_file" ]]; then rm -f -- "$pid_file"; fi

nohup java -jar "$application_jar" >> "$log_file" 2>&1 &
started_pid=$!
temporary_pid="$pid_file.$started_pid"
printf '%s\n' "$started_pid" > "$temporary_pid"
mv -f -- "$temporary_pid" "$pid_file"

for ((attempt = 0; attempt < 50; attempt++)); do
    if ! managed_pid "$started_pid"; then
        echo "WinLaufen Web konnte nicht gestartet werden. Log: $log_file" >&2
        tail -n 8 "$log_file" >&2
        rm -f -- "$pid_file"
        exit 1
    fi
    if port_listening 8080 && port_listening 8081; then
        echo "WinLaufen Web gestartet."
        echo "PID: $started_pid"
        echo "Dashboard: http://localhost:8080/"
        echo "Renderer:  http://localhost:8080/renderer"
        echo "Log: $log_file"
        exit 0
    fi
    sleep 0.1
done

echo "WinLaufen Web läuft, aber 8080/8081 wurden nicht rechtzeitig bereit. Log: $log_file" >&2
kill -TERM "$started_pid" 2>/dev/null || true
rm -f -- "$pid_file"
exit 1
