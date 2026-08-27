#!/usr/bin/env bash

repository_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
application_jar="$repository_root/target/winlaufen-web.jar"
if [[ -n "${XDG_RUNTIME_DIR:-}" && -d "$XDG_RUNTIME_DIR" && -w "$XDG_RUNTIME_DIR" ]]; then
    runtime_parent=$XDG_RUNTIME_DIR
else
    runtime_parent=/tmp
fi
runtime_root="$runtime_parent/winlaufen-web-${UID}"
pid_file="$runtime_root/winlaufen-web.pid"
log_file="$runtime_root/winlaufen-web.log"

managed_pid() {
    local pid=$1
    [[ "$pid" =~ ^[1-9][0-9]*$ ]] || return 1
    kill -0 "$pid" 2>/dev/null || return 1
    [[ -r "/proc/$pid/cmdline" ]] || return 1
    tr '\0' '\n' < "/proc/$pid/cmdline" | grep -Fxq -- "$application_jar"
}

read_pid() {
    [[ -f "$pid_file" ]] || return 1
    IFS= read -r local_pid < "$pid_file"
    [[ -n "$local_pid" ]] || return 1
    printf '%s\n' "$local_pid"
}

port_listening() {
    local port=$1
    ss -ltnH "sport = :$port" 2>/dev/null | grep -q .
}
