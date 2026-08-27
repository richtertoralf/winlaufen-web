#!/usr/bin/env bash
set -u
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
# shellcheck source=local-common.sh
source "$script_dir/local-common.sh"
[[ $# == 2 ]]||{ echo "Usage: $0 start|stop|restart|status bridge|live-server" >&2;exit 2; }
action=$1;component=$2;jar=$(component_jar "$component")||exit 2;pid_path=$(pid_file "$component");log_path=$(log_file "$component")
start_component(){ [[ -f "$jar" ]]||{ echo "$jar fehlt; zuerst mvn package ausführen." >&2;return 1;};mkdir -p -- "$runtime_root";local pid;pid=$(read_pid "$component" 2>/dev/null||true);if [[ -n "$pid" ]]&&managed_pid "$component" "$pid";then echo "$component läuft bereits (PID $pid).";return 0;fi;rm -f -- "$pid_path";nohup java -jar "$jar" >>"$log_path" 2>&1 & pid=$!;printf '%s\n' "$pid" >"$pid_path.$pid";mv -f -- "$pid_path.$pid" "$pid_path";for ((i=0;i<80;i++));do managed_pid "$component" "$pid"||{ tail -n 8 "$log_path" >&2;rm -f -- "$pid_path";return 1;};local ready=1;for port in $(component_port "$component");do port_listening "$port"||ready=0;done;((ready))&&{ echo "$component gestartet (PID $pid).";return 0;};sleep .1;done;echo "$component Ports nicht bereit; Log: $log_path" >&2;return 1;}
stop_component(){ local pid;pid=$(read_pid "$component" 2>/dev/null||true);if [[ -z "$pid" ]]||! managed_pid "$component" "$pid";then rm -f -- "$pid_path";echo "$component läuft nicht.";return 0;fi;kill -TERM "$pid";for ((i=0;i<100;i++));do if ! managed_pid "$component" "$pid";then rm -f -- "$pid_path";echo "$component gestoppt.";return 0;fi;sleep .1;done;echo "$component stoppte nicht rechtzeitig." >&2;return 1;}
status_component(){ local pid;pid=$(read_pid "$component" 2>/dev/null||true);if [[ -z "$pid" ]]||! managed_pid "$component" "$pid";then echo "$component: STOPPED";return 0;fi;echo "$component: RUNNING (PID $pid)";for port in $(component_port "$component");do echo "  Port $port: $(port_listening "$port"&&echo LISTENING||echo NOT_LISTENING)";done;}
case "$action" in start)start_component;;stop)stop_component;;restart)stop_component&&start_component;;status)status_component;;*)exit 2;;esac
