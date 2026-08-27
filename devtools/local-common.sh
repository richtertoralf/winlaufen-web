#!/usr/bin/env bash
repository_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
if [[ -n "${XDG_RUNTIME_DIR:-}" && -d "$XDG_RUNTIME_DIR" && -w "$XDG_RUNTIME_DIR" ]]; then runtime_parent=$XDG_RUNTIME_DIR; else runtime_parent=/tmp; fi
runtime_root="$runtime_parent/winlaufen-web-${UID}"
component_jar(){ case "$1" in bridge) printf '%s\n' "$repository_root/bridge/target/winlaufen-web-bridge.jar";; live-server) printf '%s\n' "$repository_root/live-server/target/winlaufen-web-live-server.jar";; *) return 2;; esac; }
component_port(){ case "$1" in bridge) printf '8090\n';; live-server) printf '8080 8081\n';; *) return 2;; esac; }
pid_file(){ printf '%s/%s.pid\n' "$runtime_root" "$1"; }
log_file(){ printf '%s/%s.log\n' "$runtime_root" "$1"; }
read_pid(){ local file;file=$(pid_file "$1");[[ -f "$file" ]]||return 1;IFS= read -r value < "$file";[[ "$value" =~ ^[1-9][0-9]*$ ]]||return 1;printf '%s\n' "$value"; }
managed_pid(){ local component=$1 pid=$2 jar;jar=$(component_jar "$component");kill -0 "$pid" 2>/dev/null||return 1;[[ -r "/proc/$pid/cmdline" ]]||return 1;tr '\0' '\n' < "/proc/$pid/cmdline" | grep -Fxq -- "$jar"; }
port_listening(){ ss -ltnH "sport = :$1" 2>/dev/null | grep -q .; }
