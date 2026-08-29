#!/usr/bin/env bash
# WinLaufen Web Setup für Linux (Debian, Ubuntu 24.04/26.04, Raspberry Pi OS).
#
# Der Installer fragt ausschließlich das Installationsprofil ab. Es werden zu
# keinem Zeitpunkt WinLaufen-Adressen, Target-Adressen, Hostnamen, URLs oder
# WSS-Ziele abgefragt: diese Werte gehören in die spätere Runtime-Konfiguration
# über Bridge Control.
#
#   sudo ./install.sh                       interaktive Profilauswahl
#   sudo ./install.sh --profile all-in-one
#   sudo ./install.sh --profile bridge-only
#   sudo ./install.sh --profile presentation-node
#
# Testmodus ohne root und ohne systemd:
#   ./install.sh --profile all-in-one --staging-root /tmp/x --no-systemd
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
# shellcheck source=../common/dist-manifest.env
source "$script_dir/../common/dist-manifest.env"

PROFILE=""
STAGING_ROOT=""
USE_SYSTEMD=1
DIST_DIR=""
ASSUME_YES=0

INSTALL_PREFIX="/opt/winlaufen-web"
CONFIG_DIR="/etc/winlaufen-web"
STATE_DIR="/var/lib/winlaufen-web"
SERVICE_USER="winlaufen"
SERVICE_GROUP="winlaufen"
BRIDGE_UNIT="winlaufen-bridge.service"
LIVE_UNIT="winlaufen-live-server.service"
SYSTEMD_DIR="/etc/systemd/system"

usage() {
    sed -n '2,18p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit "${1:-0}"
}

while (($#)); do
    case "$1" in
        --profile) PROFILE=$2; shift 2 ;;
        --staging-root) STAGING_ROOT=$2; shift 2 ;;
        --no-systemd) USE_SYSTEMD=0; shift ;;
        --dist) DIST_DIR=$2; shift 2 ;;
        --yes|-y) ASSUME_YES=1; shift ;;
        -h|--help) usage 0 ;;
        *) echo "Unbekannte Option: $1" >&2; usage 2 ;;
    esac
done

# ---------------------------------------------------------------- Hilfsmittel

fail() { echo "FEHLER: $*" >&2; exit 1; }
note() { printf '  %s\n' "$*"; }

# Alle Pfade laufen über staged(), damit der Installer ohne root in ein
# Testverzeichnis installieren kann. Ohne --staging-root ist es die Identität.
staged() { printf '%s' "${STAGING_ROOT}$1"; }

systemd_operations_enabled() {
    ((USE_SYSTEMD)) || return 1
    [[ -z "$STAGING_ROOT" || "${WINLAUFEN_INSTALL_TEST_SYSTEMD:-0}" == "1" ]]
}

supported_platform() {
    [[ -r /etc/os-release ]] || return 1
    # shellcheck disable=SC1091
    . /etc/os-release
    case "${ID:-}:${VERSION_ID:-}" in
        debian:*|raspbian:*) return 0 ;;
        ubuntu:24.04|ubuntu:26.04) return 0 ;;
    esac
    case " ${ID_LIKE:-} " in
        *" debian "*) return 0 ;;
    esac
    return 1
}

detect_java() {
    # Bevorzugt eine mitgelieferte jlink-Runtime, sonst System-Java.
    if [[ -x "$DIST_DIR/runtime/bin/java" ]]; then
        printf '%s' "$INSTALL_PREFIX/runtime/bin/java"
        return 0
    fi
    local candidate
    candidate=$(command -v java 2>/dev/null) || return 1
    local major
    major=$("$candidate" -XshowSettings:properties -version 2>&1 \
        | sed -n 's/.*java\.specification\.version = \([0-9][0-9]*\).*/\1/p' | head -1)
    [[ -n "$major" ]] || return 1
    ((major >= WINLAUFEN_JAVA_RELEASE)) || return 1
    printf '%s' "$candidate"
}

local_addresses() {
    if command -v ip >/dev/null 2>&1; then
        ip -4 -o addr show scope global 2>/dev/null | awk '{split($4,a,"/"); print a[1]}'
    elif command -v hostname >/dev/null 2>&1; then
        hostname -I 2>/dev/null | tr ' ' '\n' | grep -v '^$' || true
    fi
}

active_firewalls() {
    [[ -z "$STAGING_ROOT" ]] || return 0
    if command -v ufw >/dev/null 2>&1 \
            && ufw status 2>/dev/null | grep -q '^Status: active'; then
        echo "UFW"
    fi
    if command -v firewall-cmd >/dev/null 2>&1 \
            && [[ "$(firewall-cmd --state 2>/dev/null || true)" == "running" ]]; then
        echo "firewalld"
    fi
    if systemctl is-active --quiet nftables.service 2>/dev/null; then
        echo "nftables"
    elif command -v nft >/dev/null 2>&1 \
            && [[ -n "$(nft list ruleset 2>/dev/null || true)" ]]; then
        echo "nftables (Regelsatz erkannt)"
    fi
}

show_firewall_notice() {
    local detected
    detected=$(active_firewalls | sort -u | paste -sd ', ' - || true)
    cat <<EOF

Firewall-Hinweis:
Der Linux-Installer hat keine Firewall aktiviert und keine Firewallregel
angelegt oder geändert.
EOF
    if [[ -n "$detected" ]]; then
        echo "Erkannte aktive lokale Firewall: $detected"
    else
        echo "Es wurde keine bekannte aktive lokale Firewall erkannt; externe Firewalls"
        echo "oder anderweitig verwaltete Regeln können trotzdem vorhanden sein."
    fi
    echo
    case "$PROFILE" in
        all-in-one)
            cat <<EOF
Eingehend für die vorgesehenen LAN-Clients erforderlich:
  TCP $WINLAUFEN_LIVE_HTTP_PORT – Web View / HTTP
  TCP $WINLAUFEN_LIVE_WS_PORT – Live WebSocket / Bridge Ingest
  TCP $WINLAUFEN_CONTROL_PORT – Bridge Control

Ausgehend:
  TCP $WINLAUFEN_SOURCE_PORT zum WinLaufen-PC
EOF
            ;;
        bridge-only)
            cat <<EOF
Eingehend für die vorgesehenen LAN-Clients erforderlich:
  TCP $WINLAUFEN_CONTROL_PORT – Bridge Control

Ausgehend:
  TCP $WINLAUFEN_SOURCE_PORT zum WinLaufen-PC
EOF
            ;;
        presentation-node)
            cat <<EOF
Eingehend für die vorgesehenen LAN-Clients erforderlich:
  TCP $WINLAUFEN_LIVE_HTTP_PORT – Web View / HTTP
  TCP $WINLAUFEN_LIVE_WS_PORT – Live WebSocket / Bridge Ingest
EOF
            ;;
    esac
    cat <<EOF

Falls eine lokale oder externe Firewall aktiv ist, müssen die oben genannten
Ports für die vorgesehenen LAN-Clients freigegeben werden. Ein lokaler Listener
belegt nicht, dass ein Port aus dem LAN oder Internet erreichbar ist.
EOF
}

listener_details=""
listener_pid=""
listener_process=""
listener_service=""

find_listener() {
    local port=$1 output=""
    listener_details=""
    listener_pid=""
    listener_process=""
    listener_service=""

    if command -v ss >/dev/null 2>&1; then
        output=$(ss -H -ltnp "sport = :$port" 2>/dev/null || true)
    elif command -v netstat >/dev/null 2>&1; then
        output=$(netstat -ltnp 2>/dev/null | awk -v suffix=":$port" \
            '$4 ~ suffix "$" { print }')
    else
        local hex_port
        printf -v hex_port '%04X' "$port"
        output=$(awk -v port="$hex_port" \
            'NR > 1 && $2 ~ (":" port "$") && $4 == "0A" { print }' \
            /proc/net/tcp /proc/net/tcp6 2>/dev/null || true)
    fi
    [[ -n "$output" ]] || return 1

    listener_details=$(printf '%s\n' "$output" | head -1)
    listener_pid=$(printf '%s\n' "$output" \
        | sed -nE 's/.*pid=([0-9]+).*/\1/p' | head -1)
    if [[ -n "$listener_pid" && -r "/proc/$listener_pid/comm" ]]; then
        IFS= read -r listener_process < "/proc/$listener_pid/comm" || true
    fi
    if [[ -n "$listener_pid" && -r "/proc/$listener_pid/cgroup" ]]; then
        listener_service=$(sed -nE 's#^.*/([^/]+\.service)(/.*)?$#\1#p' \
            "/proc/$listener_pid/cgroup" | head -1)
    fi
    return 0
}

own_unit_for_port() {
    case "$1" in
        "$WINLAUFEN_CONTROL_PORT") printf '%s' "$BRIDGE_UNIT" ;;
        "$WINLAUFEN_LIVE_HTTP_PORT"|"$WINLAUFEN_LIVE_WS_PORT") printf '%s' "$LIVE_UNIT" ;;
    esac
}

port_owned_by_existing_installation() {
    local port=$1 unit main_pid
    [[ -z "$STAGING_ROOT" ]] && ((USE_SYSTEMD)) || return 1
    unit=$(own_unit_for_port "$port")
    [[ -n "$unit" ]] || return 1
    systemctl is-active --quiet "$unit" 2>/dev/null || return 1

    # Ein lediglich aktiver WinLaufen-Web-Dienst genügt nicht: Der gefundene
    # Listener muss tatsächlich zu genau dieser Unit gehören. Sonst könnte ein
    # fremder Prozess bei einem beschädigten Reinstall versehentlich passieren.
    main_pid=$(systemctl show "$unit" --property=MainPID --value 2>/dev/null || true)
    [[ -n "$listener_pid" && "$listener_pid" == "$main_pid" ]] \
        || [[ -n "$listener_service" && "$listener_service" == "$unit" ]]
}

check_listener_port() {
    local port=$1 purpose=$2
    if ! find_listener "$port"; then
        return 0
    fi
    if port_owned_by_existing_installation "$port"; then
        note "TCP-Port $port wird von der bestehenden WinLaufen-Web-Installation übernommen"
        return 0
    fi

    echo >&2
    echo "FEHLER: TCP-Port $port ist bereits belegt." >&2
    echo >&2
    echo "Benötigt für: $purpose" >&2
    echo >&2
    if [[ -n "$listener_process" || -n "$listener_pid" ]]; then
        echo "Prozess:" >&2
        printf '  %s' "${listener_process:-unbekannt}" >&2
        [[ -n "$listener_pid" ]] && printf ' (PID %s)' "$listener_pid" >&2
        echo >&2
    else
        echo "Prozess: nicht ermittelbar" >&2
        [[ -n "$listener_details" ]] && echo "  Listener: $listener_details" >&2
    fi
    if [[ -n "$listener_service" ]]; then
        echo >&2
        echo "Dienst:" >&2
        echo "  $listener_service" >&2
    fi
    echo >&2
    echo "Die Installation wurde nicht erfolgreich abgeschlossen." >&2
    exit 1
}

preflight_ports() {
    if ((install_live)); then
        check_listener_port "$WINLAUFEN_LIVE_HTTP_PORT" "WinLaufen Web View / HTTP"
        check_listener_port "$WINLAUFEN_LIVE_WS_PORT" "Live WebSocket / Bridge Ingest"
    fi
    if ((install_bridge)); then
        check_listener_port "$WINLAUFEN_CONTROL_PORT" "Bridge Control"
    fi
}

# ------------------------------------------------------------ Profilauswahl

choose_profile() {
    cat <<EOF

$WINLAUFEN_PRODUCT_NAME Setup

Installationsprofil:

  [1] All-in-One
      Bridge + Live Server
      Für einen Rechner im lokalen Netz, z. B. WinLaufen-PC,
      Sprecher-PC, separater LAN-PC oder Raspberry Pi

  [2] Bridge only
      Nur WinLaufen Bridge

  [3] Presentation Node
      Nur Live Server / Web View

EOF
    local answer
    read -r -p "Auswahl [1]: " answer || answer=""
    case "${answer:-1}" in
        1|"") PROFILE="all-in-one" ;;
        2) PROFILE="bridge-only" ;;
        3) PROFILE="presentation-node" ;;
        *) fail "Ungültige Auswahl: $answer" ;;
    esac
}

install_bridge=0
install_live=0

case "$PROFILE" in
    all-in-one) install_bridge=1; install_live=1 ;;
    bridge-only) install_bridge=1 ;;
    presentation-node) install_live=1 ;;
    "") ;;
    *) fail "Unbekanntes Profil: $PROFILE (all-in-one | bridge-only | presentation-node)" ;;
esac

if [[ -z "$PROFILE" ]]; then
    if ((ASSUME_YES)); then
        PROFILE="all-in-one"
    else
        choose_profile
    fi
    case "$PROFILE" in
        all-in-one) install_bridge=1; install_live=1 ;;
        bridge-only) install_bridge=1 ;;
        presentation-node) install_live=1 ;;
    esac
fi

# ------------------------------------------------------------ Vorbedingungen

if [[ -z "$DIST_DIR" ]]; then
    if [[ -d "$script_dir/../../lib" ]]; then
        DIST_DIR=$(cd -- "$script_dir/../.." && pwd -P)      # entpackte Distribution
    else
        DIST_DIR=$(cd -- "$script_dir/../.." && pwd -P)      # Repository
    fi
fi

if [[ -f "$DIST_DIR/lib/$WINLAUFEN_BRIDGE_JAR" ]]; then
    LIB_DIR="$DIST_DIR/lib"
else
    LIB_DIR=""
    [[ -f "$DIST_DIR/bridge/target/$WINLAUFEN_BRIDGE_JAR" ]] && LIB_DIR="$DIST_DIR"
fi

bridge_source=""
live_source=""
if [[ -n "$LIB_DIR" && "$LIB_DIR" != "$DIST_DIR" ]]; then
    bridge_source="$LIB_DIR/$WINLAUFEN_BRIDGE_JAR"
    live_source="$LIB_DIR/$WINLAUFEN_LIVE_JAR"
else
    bridge_source="$DIST_DIR/bridge/target/$WINLAUFEN_BRIDGE_JAR"
    live_source="$DIST_DIR/live-server/target/$WINLAUFEN_LIVE_JAR"
fi

((install_bridge)) && [[ -f "$bridge_source" ]] \
    || ((install_bridge == 0)) \
    || fail "$bridge_source fehlt. Zuerst './mvnw package' oder installer/common/build-dist.sh ausführen."
((install_live)) && [[ -f "$live_source" ]] \
    || ((install_live == 0)) \
    || fail "$live_source fehlt. Zuerst './mvnw package' oder installer/common/build-dist.sh ausführen."

if [[ -z "$STAGING_ROOT" ]]; then
    [[ $EUID -eq 0 ]] || fail "Bitte mit sudo ausführen."
    supported_platform || echo "WARNUNG: nicht getestete Distribution. Unterstützt sind Debian, Ubuntu 24.04/26.04 und Raspberry Pi OS." >&2
fi

JAVA_BIN=$(detect_java) || fail "Keine passende Java-Runtime gefunden (benötigt Java >= $WINLAUFEN_JAVA_RELEASE).
Entweder ein JDK/JRE >= $WINLAUFEN_JAVA_RELEASE installieren oder eine Distribution mit gebündelter
Runtime verwenden: installer/common/build-dist.sh --with-runtime"

# Nur lokale Listener von WinLaufen Web prüfen. TCP 4444 ist der entfernte
# WinLaufen-Zielport und gehört ausdrücklich nicht in diesen Preflight.
preflight_ports

echo
echo "== Installiere Profil: $PROFILE =="
note "Java:        $JAVA_BIN"
note "Programm:    $(staged "$INSTALL_PREFIX")"
note "Konfiguration: $(staged "$CONFIG_DIR")"

# ------------------------------------------------------------ Systembenutzer

if [[ -z "$STAGING_ROOT" ]]; then
    if ! getent group "$SERVICE_GROUP" >/dev/null 2>&1; then
        groupadd --system "$SERVICE_GROUP"
    fi
    if ! getent passwd "$SERVICE_USER" >/dev/null 2>&1; then
        # Dienstkonto ohne Login-Shell und ohne Home-Verzeichnis-Sonderrechte.
        useradd --system --gid "$SERVICE_GROUP" --home-dir "$STATE_DIR" \
                --shell /usr/sbin/nologin --comment "$WINLAUFEN_PRODUCT_NAME" "$SERVICE_USER"
    fi
fi

# ------------------------------------------------------------ Dateien

install_dirs=("$INSTALL_PREFIX/lib" "$CONFIG_DIR" "$STATE_DIR")
for dir in "${install_dirs[@]}"; do
    mkdir -p "$(staged "$dir")"
done

if ((install_bridge)); then
    install -m 0644 "$bridge_source" "$(staged "$INSTALL_PREFIX/lib/$WINLAUFEN_BRIDGE_JAR")"
    note "Bridge-Artefakt installiert"
fi
if ((install_live)); then
    install -m 0644 "$live_source" "$(staged "$INSTALL_PREFIX/lib/$WINLAUFEN_LIVE_JAR")"
    note "Live-Server-Artefakt installiert"
fi

if [[ -d "$DIST_DIR/runtime" ]]; then
    rm -rf -- "$(staged "$INSTALL_PREFIX/runtime")"
    cp -R -- "$DIST_DIR/runtime" "$(staged "$INSTALL_PREFIX/runtime")"
    note "Gebündelte Java-Runtime installiert"
fi

# ------------------------------------------------------------ Konfiguration
#
# Vorhandene Konfiguration wird niemals überschrieben. Defaults entstehen nur
# bei einer echten Erstinstallation, damit ein Upgrade eine bereits gepflegte
# WinLaufen-Adresse oder Target-Liste nicht zurücksetzt.

bridge_config="$CONFIG_DIR/bridge.properties"
live_config="$CONFIG_DIR/live-server.env"

migrate_bridge_network_defaults() {
    local file=$1 changed=0
    grep -q '^bridge\.control\.bind=127\.0\.0\.1$' "$file" \
        && sed -i 's/^bridge\.control\.bind=127\.0\.0\.1$/bridge.control.bind=0.0.0.0/' "$file" \
        && changed=1
    grep -q '^bridge\.control\.port=8090$' "$file" \
        && sed -i "s/^bridge\.control\.port=8090$/bridge.control.port=$WINLAUFEN_CONTROL_PORT/" "$file" \
        && changed=1
    grep -q '^outputs\.[0-9][0-9]*\.endpoint=ws://127\.0\.0\.1:8081/bridge/v1/channels/local$' "$file" \
        && sed -i "s#^\(outputs\.[0-9][0-9]*\.endpoint=ws://127\.0\.0\.1:\)8081\(/bridge/v1/channels/local\)\$#\1$WINLAUFEN_LIVE_WS_PORT\2#" "$file" \
        && changed=1
    grep -q '^outputs\.[0-9][0-9]*\.endpoint=ws\\://127\.0\.0\.1\\:8081/bridge/v1/channels/local$' "$file" \
        && sed -i "s#^\(outputs\.[0-9][0-9]*\.endpoint=ws\\\\://127\.0\.0\.1\\\\:\)8081\(/bridge/v1/channels/local\)\$#\1$WINLAUFEN_LIVE_WS_PORT\2#" "$file" \
        && changed=1
    ((changed == 0)) || note "Frühere Installer-Netzwerkdefaults auf den festen Portblock migriert: $bridge_config"
}

migrate_live_network_defaults() {
    local file=$1 changed=0
    grep -q '^WINLAUFEN_LIVE_HTTP_PORT=8080$' "$file" \
        && sed -i "s/^WINLAUFEN_LIVE_HTTP_PORT=8080$/WINLAUFEN_LIVE_HTTP_PORT=$WINLAUFEN_LIVE_HTTP_PORT/" "$file" \
        && changed=1
    grep -q '^WINLAUFEN_LIVE_WS_PORT=8081$' "$file" \
        && sed -i "s/^WINLAUFEN_LIVE_WS_PORT=8081$/WINLAUFEN_LIVE_WS_PORT=$WINLAUFEN_LIVE_WS_PORT/" "$file" \
        && changed=1
    ((changed == 0)) || note "Frühere Installer-Netzwerkdefaults auf den festen Portblock migriert: $live_config"
}

if ((install_bridge)); then
    if [[ -f "$(staged "$bridge_config")" ]]; then
        migrate_bridge_network_defaults "$(staged "$bridge_config")"
        note "Bestehende Bridge-Konfiguration beibehalten: $bridge_config"
    else
        if [[ "$PROFILE" == "all-in-one" ]]; then
            # All-in-One: lokaler Live Server ist als reguläres Output Target
            # vorkonfiguriert und nutzt denselben Bridge->Live-Server-Pfad wie
            # ein entferntes Ziel.
            cat > "$(staged "$bridge_config")" <<EOF
# $WINLAUFEN_PRODUCT_NAME - Bridge (Profil: All-in-One)
# Erzeugt bei der Erstinstallation. Änderungen bitte über Bridge Control
# vornehmen: http://<bridge-ip>:$WINLAUFEN_CONTROL_PORT/
config.version=2
source.type=WINLAUFEN
source.host=$WINLAUFEN_DEFAULT_SOURCE_HOST
bridge.control.bind=$WINLAUFEN_CONTROL_BIND
bridge.control.port=$WINLAUFEN_CONTROL_PORT
outputs.count=1
outputs.0.id=local
outputs.0.type=LOCAL
outputs.0.enabled=true
outputs.0.endpoint=ws://127.0.0.1:$WINLAUFEN_LIVE_WS_PORT$WINLAUFEN_INGEST_PATH_PREFIX$WINLAUFEN_LIVE_CHANNEL
outputs.0.channelId=$WINLAUFEN_LIVE_CHANNEL
outputs.0.secret=$WINLAUFEN_DEFAULT_SECRET
presentation.showClub=true
presentation.showAssociation=true
presentation.showNation=false
presentation.showShooting=true
presentation.showMessages=false
EOF
        else
            # Bridge only: vollständig installiert, aber ohne Output Target.
            # Das ist ein gültiger Zustand, kein Installationsfehler.
            cat > "$(staged "$bridge_config")" <<EOF
# $WINLAUFEN_PRODUCT_NAME - Bridge (Profil: Bridge only)
# Erzeugt bei der Erstinstallation. WinLaufen-Adresse und Output Targets
# anschließend über Bridge Control pflegen: http://<bridge-ip>:$WINLAUFEN_CONTROL_PORT/
config.version=2
source.type=WINLAUFEN
source.host=$WINLAUFEN_DEFAULT_SOURCE_HOST
bridge.control.bind=$WINLAUFEN_CONTROL_BIND
bridge.control.port=$WINLAUFEN_CONTROL_PORT
outputs.count=0
presentation.showClub=true
presentation.showAssociation=true
presentation.showNation=false
presentation.showShooting=true
presentation.showMessages=false
EOF
        fi
        note "Bridge-Standardkonfiguration erzeugt: $bridge_config"
    fi
fi

if ((install_live)); then
    if [[ -f "$(staged "$live_config")" ]]; then
        migrate_live_network_defaults "$(staged "$live_config")"
        note "Bestehende Live-Server-Konfiguration beibehalten: $live_config"
    else
        cat > "$(staged "$live_config")" <<EOF
# $WINLAUFEN_PRODUCT_NAME - Live Server
# Rein technische Deployment-Parameter. Keine Veranstalter-Konfiguration.
WINLAUFEN_LIVE_HTTP_BIND=$WINLAUFEN_LIVE_HTTP_BIND
WINLAUFEN_LIVE_HTTP_PORT=$WINLAUFEN_LIVE_HTTP_PORT
WINLAUFEN_LIVE_WS_BIND=$WINLAUFEN_LIVE_WS_BIND
WINLAUFEN_LIVE_WS_PORT=$WINLAUFEN_LIVE_WS_PORT
WINLAUFEN_LIVE_CHANNEL=$WINLAUFEN_LIVE_CHANNEL
WINLAUFEN_LIVE_SECRET=$WINLAUFEN_DEFAULT_SECRET
EOF
        note "Live-Server-Standardkonfiguration erzeugt: $live_config"
    fi
fi

# ------------------------------------------------------------ systemd-Units

write_bridge_unit() {
    cat > "$(staged "$SYSTEMD_DIR/$BRIDGE_UNIT")" <<EOF
[Unit]
Description=$WINLAUFEN_PRODUCT_NAME Bridge
Documentation=file://$INSTALL_PREFIX/README.md
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$SERVICE_USER
Group=$SERVICE_GROUP
WorkingDirectory=$STATE_DIR
ExecStart=$JAVA_BIN -D$WINLAUFEN_BRIDGE_CONFIG_PROPERTY=$bridge_config -jar $INSTALL_PREFIX/lib/$WINLAUFEN_BRIDGE_JAR
Restart=on-failure
RestartSec=5
StandardOutput=journal
StandardError=journal
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=$STATE_DIR $CONFIG_DIR

[Install]
WantedBy=multi-user.target
EOF
}

write_live_unit() {
    cat > "$(staged "$SYSTEMD_DIR/$LIVE_UNIT")" <<EOF
[Unit]
Description=$WINLAUFEN_PRODUCT_NAME Live Server
Documentation=file://$INSTALL_PREFIX/README.md
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$SERVICE_USER
Group=$SERVICE_GROUP
WorkingDirectory=$STATE_DIR
EnvironmentFile=$live_config
ExecStart=$JAVA_BIN \\
  -Dwinlaufen.live.http.bind=\${WINLAUFEN_LIVE_HTTP_BIND} \\
  -Dwinlaufen.live.http.port=\${WINLAUFEN_LIVE_HTTP_PORT} \\
  -Dwinlaufen.live.websocket.bind=\${WINLAUFEN_LIVE_WS_BIND} \\
  -Dwinlaufen.live.websocket.port=\${WINLAUFEN_LIVE_WS_PORT} \\
  -Dwinlaufen.live.channel=\${WINLAUFEN_LIVE_CHANNEL} \\
  -Dwinlaufen.live.secret=\${WINLAUFEN_LIVE_SECRET} \\
  -jar $INSTALL_PREFIX/lib/$WINLAUFEN_LIVE_JAR
Restart=on-failure
RestartSec=5
StandardOutput=journal
StandardError=journal
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=$STATE_DIR

[Install]
WantedBy=multi-user.target
EOF
}

mkdir -p "$(staged "$SYSTEMD_DIR")"
((install_bridge)) && write_bridge_unit && note "systemd-Unit geschrieben: $BRIDGE_UNIT"
((install_live)) && write_live_unit && note "systemd-Unit geschrieben: $LIVE_UNIT"

# Units eines nicht gewählten Profils aus einer früheren Installation entfernen,
# damit eine Profiländerung keine verwaisten Dienste hinterlässt.
remove_unit() {
    local unit=$1
    [[ -f "$(staged "$SYSTEMD_DIR/$unit")" ]] || return 0
    if [[ -z "$STAGING_ROOT" ]] && ((USE_SYSTEMD)); then
        systemctl disable --now "$unit" >/dev/null 2>&1 || true
    fi
    rm -f -- "$(staged "$SYSTEMD_DIR/$unit")"
    note "Nicht zum Profil gehörenden Dienst entfernt: $unit"
}
((install_bridge)) || remove_unit "$BRIDGE_UNIT"
((install_live)) || remove_unit "$LIVE_UNIT"

# ------------------------------------------------------------ Rechte

# BridgeConfigStore schreibt atomar über eine temporäre Datei im Parent-
# Verzeichnis. Daher braucht die Dienstgruppe Schreibrecht auf CONFIG_DIR;
# die einzelnen Konfigurationsdateien bleiben restriktiv.
chmod 0770 "$(staged "$CONFIG_DIR")"
[[ -f "$(staged "$bridge_config")" ]] && chmod 0640 "$(staged "$bridge_config")"
[[ -f "$(staged "$live_config")" ]] && chmod 0640 "$(staged "$live_config")"

if [[ -z "$STAGING_ROOT" ]]; then
    chown -R root:root "$INSTALL_PREFIX"
    chown -R "$SERVICE_USER:$SERVICE_GROUP" "$STATE_DIR"
    chown root:"$SERVICE_GROUP" "$CONFIG_DIR"
    # Die Bridge schreibt ihre Konfiguration aus Bridge Control zurück.
    [[ -f "$bridge_config" ]] && chown "$SERVICE_USER:$SERVICE_GROUP" "$bridge_config"
    [[ -f "$live_config" ]] && chown root:"$SERVICE_GROUP" "$live_config"
fi

# ------------------------------------------------------------ Dienste starten

installation_failed() {
    echo >&2
    echo "Die Installation wurde nicht erfolgreich abgeschlossen." >&2
    exit 1
}

show_service_diagnostics() {
    local unit=$1
    echo >&2
    echo "Status von $unit:" >&2
    systemctl status "$unit" --no-pager --lines=12 >&2 || true
}

wait_for_active_unit() {
    local unit=$1 state="" attempt attempts=${WINLAUFEN_INSTALL_TEST_START_ATTEMPTS:-15}
    for attempt in $(seq 1 "$attempts"); do
        state=$(systemctl is-active "$unit" 2>/dev/null || true)
        [[ "$state" == "active" ]] && return 0
        [[ "$state" == "failed" ]] && break
        sleep 1
    done
    echo "FEHLER: Dienst $unit ist nach der Startphase nicht active (Status: ${state:-unbekannt})." >&2
    show_service_diagnostics "$unit"
    installation_failed
}

http_reachable() {
    local port=$1
    if command -v curl >/dev/null 2>&1; then
        curl --fail --silent --show-error --max-time 2 "http://127.0.0.1:$port/" >/dev/null 2>&1
    elif command -v wget >/dev/null 2>&1; then
        wget --quiet --timeout=2 --output-document=/dev/null "http://127.0.0.1:$port/"
    elif command -v timeout >/dev/null 2>&1; then
        timeout 2 bash -c '
            exec 3<>"/dev/tcp/127.0.0.1/$1"
            printf "GET / HTTP/1.0\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n" >&3
            IFS= read -r status <&3
            [[ "$status" == HTTP/*" 200 "* ]]
        ' -- "$port" >/dev/null 2>&1
    else
        return 1
    fi
}

wait_for_http() {
    local port=$1 purpose=$2 unit=$3 attempt attempts=${WINLAUFEN_INSTALL_TEST_START_ATTEMPTS:-15}
    for attempt in $(seq 1 "$attempts"); do
        http_reachable "$port" && return 0
        systemctl is-active --quiet "$unit" 2>/dev/null || break
        sleep 1
    done
    echo "FEHLER: $purpose ist lokal auf TCP-Port $port nicht erreichbar." >&2
    show_service_diagnostics "$unit"
    installation_failed
}

http_body() {
    local port=$1 path=$2
    if command -v curl >/dev/null 2>&1; then
        curl --fail --silent --show-error --max-time 2 "http://127.0.0.1:$port$path"
    elif command -v wget >/dev/null 2>&1; then
        wget --quiet --timeout=2 --output-document=- "http://127.0.0.1:$port$path"
    elif command -v timeout >/dev/null 2>&1; then
        timeout 2 bash -c '
            exec 3<>"/dev/tcp/127.0.0.1/$1"
            printf "GET %s HTTP/1.0\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n" "$2" >&3
            IFS= read -r status <&3
            [[ "$status" == HTTP/*" 200 "* ]] || exit 1
            while IFS= read -r header <&3; do
                [[ "${header%$'\r'}" == "" ]] && break
            done
            cat <&3
        ' -- "$port" "$path"
    else
        return 1
    fi
}

bridge_status_json=""
bridge_config_json=""
observed_local_state="UNBEKANNT"
local_runtime_validated=0

json_string_field() {
    local json=$1 field=$2
    printf '%s' "$json" \
        | sed -n 's/.*"'"$field"'"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
}

json_number_field() {
    local json=$1 field=$2
    printf '%s' "$json" \
        | sed -n 's/.*"'"$field"'"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p'
}

target_runtime_state() {
    local id=$1
    printf '%s' "$bridge_status_json" \
        | sed -n 's/.*"targetId"[[:space:]]*:[[:space:]]*"'"$id"'"[^}]*"state"[[:space:]]*:[[:space:]]*"\([A-Z_]*\)".*/\1/p'
}

endpoint_authority() {
    local endpoint=$1 authority
    [[ "$endpoint" == *://* ]] || return 0
    authority=${endpoint#*://}
    authority=${authority%%/*}
    [[ -n "$authority" ]] && printf '%s' "$authority"
}

local_output_connected() {
    [[ "$(target_runtime_state local)" == "CONNECTED" ]]
}

observe_local_output() {
    local attempts=${WINLAUFEN_INSTALL_TEST_DIAGNOSTIC_ATTEMPTS:-3} attempt
    for attempt in $(seq 1 "$attempts"); do
        bridge_status_json=$(http_body "$WINLAUFEN_CONTROL_PORT" "/api/v1/status" 2>/dev/null || true)
        observed_local_state=$(target_runtime_state local)
        [[ -n "$observed_local_state" ]] || observed_local_state="UNBEKANNT"
        local_output_connected && return 0
        ((attempt < attempts)) && sleep 1
    done
    # Ein nicht verbundener Datenpfad ist Betriebsstatus, kein Installationsfehler.
    return 0
}

collect_bridge_diagnostics() {
    bridge_config_json=$(http_body "$WINLAUFEN_CONTROL_PORT" "/api/v1/config" 2>/dev/null || true)
    if [[ "$PROFILE" == "all-in-one" ]]; then
        observe_local_output
    else
        bridge_status_json=$(http_body "$WINLAUFEN_CONTROL_PORT" "/api/v1/status" 2>/dev/null || true)
    fi
}

report_bridge_diagnostics() {
    local source_host source_port source_state targets target id type enabled endpoint authority state
    if [[ -z "$bridge_config_json" || -z "$bridge_status_json" ]]; then
        cat <<EOF
  WARNUNG: Der aktuelle Bridge-Verbindungsstatus konnte nicht gelesen werden.
  Die lokale Installation ist erfolgreich; Betriebsstatus später in Bridge Control prüfen.
EOF
        return 0
    fi

    source_host=$(json_string_field "$bridge_config_json" sourceHost)
    source_port=$(json_number_field "$bridge_config_json" sourcePort)
    source_state=$(json_string_field "$bridge_status_json" sourceHealth)
    [[ -n "$source_host" ]] || source_host="unbekannt"
    [[ -n "$source_port" ]] || source_port=$WINLAUFEN_SOURCE_PORT
    [[ -n "$source_state" ]] || source_state="UNBEKANNT"

    echo "WinLaufen-Quelle:"
    if [[ "$source_state" == "CONNECTED" ]]; then
        echo "  OK: CONNECTED"
    else
        echo "  WARNUNG: $source_state"
    fi
    echo "  Ziel: $source_host:$source_port"
    if [[ "$source_state" != "CONNECTED" ]]; then
        cat <<EOF

  Die Bridge wurde erfolgreich installiert. WinLaufen ist derzeit nicht verbunden.
  Nächste Schritte:
  - WinLaufen bzw. die Sprecher-PC-Schnittstelle starten.
  - Host/IP in Bridge Control prüfen.
  - TCP $WINLAUFEN_SOURCE_PORT zwischen Bridge und WinLaufen-PC prüfen.
  - Falls WinLaufen auf einem anderen Rechner läuft, dessen Host/IP in Bridge Control eintragen.
EOF
    fi

    echo
    echo "Output Targets:"
    targets=$(printf '%s' "$bridge_config_json" \
        | sed -n 's/.*"targets"[[:space:]]*:[[:space:]]*\[\(.*\)\][[:space:]]*,[[:space:]]*"presentation".*/\1/p')
    if [[ -z "$targets" ]]; then
        echo "  HINWEIS: keine Output Targets konfiguriert."
        echo "  Targets können später in Bridge Control eingetragen werden."
        return 0
    fi

    while IFS= read -r target; do
        [[ -n "$target" ]] || continue
        id=$(json_string_field "$target" id)
        type=$(json_string_field "$target" type)
        enabled=$(printf '%s' "$target" \
            | sed -n 's/.*"enabled"[[:space:]]*:[[:space:]]*\(true\|false\).*/\1/p')
        endpoint=$(json_string_field "$target" endpoint)
        state=$(target_runtime_state "$id")
        [[ -n "$state" ]] || state="UNBEKANNT"

        echo
        echo "  Output Target:"
        echo "    ID: $id"
        echo "    Typ: $type"
        echo "    Endpoint: $endpoint"
        authority=$(endpoint_authority "$endpoint")
        [[ -n "$authority" ]] && echo "    Ziel: $authority"
        if [[ "$enabled" != "true" ]]; then
            echo "    Status: deaktiviert"
        elif [[ "$state" == "CONNECTED" ]]; then
            echo "    OK: CONNECTED"
        else
            echo "    WARNUNG: $state"
            if [[ "$PROFILE" == "all-in-one" && "$id" == "local" ]]; then
                cat <<EOF

    WARNUNG: Der lokale Datenpfad Bridge -> Live Server ist noch nicht verbunden.
    Bridge und Live Server wurden erfolgreich installiert.
    Prüfen Sie Bridge Control und die lokale Target-Konfiguration.
EOF
            else
                cat <<EOF

    Das Target ist derzeit nicht erreichbar. Die Bridge wurde trotzdem erfolgreich installiert.
    Prüfen:
    - Presentation Node installiert und gestartet?
    - Host/IP bzw. URL korrekt?
    - TCP $WINLAUFEN_LIVE_WS_PORT erreichbar?
    - Channel/Secret korrekt?
    - Firewall/VLAN/Router?
EOF
            fi
        fi
    done < <(printf '%s\n' "$targets" | sed 's/},{/}\n{/g')
}

show_installation_report() {
    cat <<EOF

============================================================
$WINLAUFEN_PRODUCT_NAME – Installation erfolgreich
============================================================

Lokale Komponenten:
EOF
    if ((local_runtime_validated)); then
        ((install_bridge)) && cat <<EOF
  OK: Bridge Service             läuft
  OK: Bridge Control             TCP $WINLAUFEN_CONTROL_PORT erreichbar
EOF
        ((install_live)) && cat <<EOF
  OK: Live Server                läuft
  OK: Web View                   TCP $WINLAUFEN_LIVE_HTTP_PORT erreichbar
  OK: Live WebSocket             TCP $WINLAUFEN_LIVE_WS_PORT lauscht
EOF
    else
        ((install_bridge)) && echo "  HINWEIS: Bridge installiert; Startprüfung wurde übersprungen."
        ((install_live)) && echo "  HINWEIS: Live Server installiert; Startprüfung wurde übersprungen."
    fi

    echo
    echo "Verbindungen / Betriebsbereitschaft:"
    if ((install_bridge)) && ((local_runtime_validated)); then
        report_bridge_diagnostics
    elif ((install_bridge)); then
        echo "  HINWEIS: nach dem Dienststart in Bridge Control prüfen."
        if [[ "$PROFILE" == "bridge-only" ]]; then
            echo "  WinLaufen prüfen und mindestens ein Output Target eintragen; dies ist später in Bridge Control möglich."
        fi
    else
        cat <<EOF
  HINWEIS: Bridge-Ingest wartet auf eine Bridge.
  Für entfernte Bridges muss TCP $WINLAUFEN_LIVE_WS_PORT vom Bridge-Rechner erreichbar sein.
  Diesen Presentation Node später auf der Bridge als Output Target eintragen.
EOF
    fi
}

validate_started_services() {
    local units=() unit restarts
    ((install_bridge)) && units+=("$BRIDGE_UNIT")
    ((install_live)) && units+=("$LIVE_UNIT")

    for unit in "${units[@]}"; do
        wait_for_active_unit "$unit"
    done
    ((install_bridge)) && wait_for_http "$WINLAUFEN_CONTROL_PORT" "Bridge Control" "$BRIDGE_UNIT"
    ((install_live)) && wait_for_http "$WINLAUFEN_LIVE_HTTP_PORT" "Web View / HTTP" "$LIVE_UNIT"

    # Definierte Stabilitätsphase: ein kurz auf active springender Restart-Loop
    # darf nicht als erfolgreiche Installation gelten.
    sleep "${WINLAUFEN_INSTALL_TEST_STABILITY_SECONDS:-3}"
    for unit in "${units[@]}"; do
        if ! systemctl is-active --quiet "$unit" 2>/dev/null; then
            echo "FEHLER: Dienst $unit blieb während der Startphase nicht active." >&2
            show_service_diagnostics "$unit"
            installation_failed
        fi
        restarts=$(systemctl show "$unit" --property=NRestarts --value 2>/dev/null || echo 0)
        if [[ "$restarts" =~ ^[0-9]+$ ]] && ((restarts > 0)); then
            echo "FEHLER: Dienst $unit wurde während der Startphase neu gestartet (NRestarts=$restarts)." >&2
            show_service_diagnostics "$unit"
            installation_failed
        fi
    done
    if ((install_bridge)) && ! http_reachable "$WINLAUFEN_CONTROL_PORT"; then
        echo "FEHLER: Bridge Control blieb während der Startphase nicht erreichbar." >&2
        show_service_diagnostics "$BRIDGE_UNIT"
        installation_failed
    fi
    if ((install_live)) && ! http_reachable "$WINLAUFEN_LIVE_HTTP_PORT"; then
        echo "FEHLER: Web View / HTTP blieb während der Startphase nicht erreichbar." >&2
        show_service_diagnostics "$LIVE_UNIT"
        installation_failed
    fi
    local_runtime_validated=1
}

if systemd_operations_enabled; then
    systemctl daemon-reload || { echo "FEHLER: systemd-Konfiguration konnte nicht neu geladen werden." >&2; installation_failed; }
    if ((install_live)); then
        systemctl reset-failed "$LIVE_UNIT" >/dev/null 2>&1 || true
        systemctl enable "$LIVE_UNIT" >/dev/null \
            || { echo "FEHLER: $LIVE_UNIT konnte nicht aktiviert werden." >&2; installation_failed; }
        systemctl restart "$LIVE_UNIT" \
            || { echo "FEHLER: $LIVE_UNIT konnte nicht gestartet werden." >&2; show_service_diagnostics "$LIVE_UNIT"; installation_failed; }
        note "Dienst aktiviert und gestartet: $LIVE_UNIT"
    fi
    if ((install_bridge)); then
        systemctl reset-failed "$BRIDGE_UNIT" >/dev/null 2>&1 || true
        systemctl enable "$BRIDGE_UNIT" >/dev/null \
            || { echo "FEHLER: $BRIDGE_UNIT konnte nicht aktiviert werden." >&2; installation_failed; }
        systemctl restart "$BRIDGE_UNIT" \
            || { echo "FEHLER: $BRIDGE_UNIT konnte nicht gestartet werden." >&2; show_service_diagnostics "$BRIDGE_UNIT"; installation_failed; }
        note "Dienst aktiviert und gestartet: $BRIDGE_UNIT"
    fi
    validate_started_services
    ((install_bridge)) && collect_bridge_diagnostics
fi

# ------------------------------------------------------------ Abschlussmeldung

show_installation_report

addresses=$(local_addresses || true)
if [[ -n "${addresses:-}" ]]; then
    echo
    echo "Aktuell erkannte lokale IP-Adressen dieses Rechners (nur Hinweis,"
    echo "es wird nichts davon dauerhaft gespeichert):"
    while read -r address; do
        [[ -n "$address" ]] || continue
        ((install_bridge)) && echo "  Bridge Control: http://$address:$WINLAUFEN_CONTROL_PORT/"
        if ((install_live)); then
            echo "  Web View:       http://$address:$WINLAUFEN_LIVE_HTTP_PORT/"
            echo "  Output Target:  ws://$address:$WINLAUFEN_LIVE_WS_PORT$WINLAUFEN_INGEST_PATH_PREFIX$WINLAUFEN_LIVE_CHANNEL"
        fi
    done <<< "$addresses"
fi

show_firewall_notice

if ((install_live)); then
    cat <<EOF

Hinweis zur Sicherheit: Diese Version ist ein Prototyp für kontrollierte Netze
und verwendet ein bekanntes Ingest-Secret. Port $WINLAUFEN_LIVE_WS_PORT darf nicht aus nicht
vertrauenswürdigen Netzen erreichbar sein. Details in README.md, Abschnitt
"Known prototype security limitation".
EOF
fi
