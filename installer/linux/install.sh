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

# ------------------------------------------------------------ Profilauswahl

choose_profile() {
    cat <<EOF

$WINLAUFEN_PRODUCT_NAME Setup

Installationsprofil:

  [1] All-in-One
      Bridge + Live Server
      Empfohlen für Installation direkt auf dem WinLaufen-PC

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
    || fail "$bridge_source fehlt. Zuerst 'mvn package' oder installer/common/build-dist.sh ausführen."
((install_live)) && [[ -f "$live_source" ]] \
    || ((install_live == 0)) \
    || fail "$live_source fehlt. Zuerst 'mvn package' oder installer/common/build-dist.sh ausführen."

if [[ -z "$STAGING_ROOT" ]]; then
    [[ $EUID -eq 0 ]] || fail "Bitte mit sudo ausführen."
    supported_platform || echo "WARNUNG: nicht getestete Distribution. Unterstützt sind Debian, Ubuntu 24.04/26.04 und Raspberry Pi OS." >&2
fi

JAVA_BIN=$(detect_java) || fail "Keine passende Java-Runtime gefunden (benötigt Java >= $WINLAUFEN_JAVA_RELEASE).
Entweder ein JDK/JRE >= $WINLAUFEN_JAVA_RELEASE installieren oder eine Distribution mit gebündelter
Runtime verwenden: installer/common/build-dist.sh --with-runtime"

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

if ((install_bridge)); then
    if [[ -f "$(staged "$bridge_config")" ]]; then
        note "Bestehende Bridge-Konfiguration beibehalten: $bridge_config"
    else
        if [[ "$PROFILE" == "all-in-one" ]]; then
            # All-in-One: lokaler Live Server ist als reguläres Output Target
            # vorkonfiguriert und nutzt denselben Bridge->Live-Server-Pfad wie
            # ein entferntes Ziel.
            cat > "$(staged "$bridge_config")" <<EOF
# $WINLAUFEN_PRODUCT_NAME - Bridge (Profil: All-in-One)
# Erzeugt bei der Erstinstallation. Änderungen bitte über Bridge Control
# vornehmen: http://localhost:$WINLAUFEN_CONTROL_PORT/
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
# anschließend über Bridge Control pflegen: http://localhost:$WINLAUFEN_CONTROL_PORT/
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

if [[ -z "$STAGING_ROOT" ]]; then
    chown -R root:root "$INSTALL_PREFIX"
    chown -R "$SERVICE_USER:$SERVICE_GROUP" "$STATE_DIR"
    chown root:"$SERVICE_GROUP" "$CONFIG_DIR"
    chmod 0750 "$CONFIG_DIR"
    # Die Bridge schreibt ihre Konfiguration aus Bridge Control zurück.
    [[ -f "$bridge_config" ]] && chown "$SERVICE_USER:$SERVICE_GROUP" "$bridge_config" && chmod 0640 "$bridge_config"
    [[ -f "$live_config" ]] && chown root:"$SERVICE_GROUP" "$live_config" && chmod 0640 "$live_config"
fi

# ------------------------------------------------------------ Dienste starten

if [[ -z "$STAGING_ROOT" ]] && ((USE_SYSTEMD)); then
    systemctl daemon-reload
    if ((install_bridge)); then
        systemctl enable --now "$BRIDGE_UNIT"
        note "Dienst aktiviert und gestartet: $BRIDGE_UNIT"
    fi
    if ((install_live)); then
        systemctl enable --now "$LIVE_UNIT"
        note "Dienst aktiviert und gestartet: $LIVE_UNIT"
    fi
fi

# ------------------------------------------------------------ Abschlussmeldung

echo
case "$PROFILE" in
    all-in-one)
        cat <<EOF
Installation erfolgreich.

$WINLAUFEN_PRODUCT_NAME wurde als All-in-One-System installiert.

Standardmäßig wird WinLaufen auf diesem Computer unter
localhost:$WINLAUFEN_SOURCE_PORT erwartet.

Wenn WinLaufen auf diesem Computer läuft, ist keine weitere
Netzwerkkonfiguration erforderlich.

Falls WinLaufen auf einem anderen Rechner läuft, ändern Sie
anschließend die WinLaufen-Adresse in Bridge Control.

  Bridge Control: http://localhost:$WINLAUFEN_CONTROL_PORT/
  Web View:       http://localhost:$WINLAUFEN_LIVE_HTTP_PORT/
EOF
        ;;
    bridge-only)
        cat <<EOF
Installation erfolgreich.

Die WinLaufen Bridge wurde installiert.

Vor dem produktiven Einsatz in Bridge Control prüfen:

- WinLaufen-Adresse, falls WinLaufen auf einem anderen Rechner läuft
- mindestens ein Output Target eintragen

  Bridge Control: http://localhost:$WINLAUFEN_CONTROL_PORT/
EOF
        ;;
    presentation-node)
        cat <<EOF
Installation erfolgreich.

Der Presentation Node / Live Server wurde installiert.

Tragen Sie diesen Server anschließend auf der gewünschten
WinLaufen Bridge als Output Target ein.

  Web View lokal: http://localhost:$WINLAUFEN_LIVE_HTTP_PORT/
EOF
        addresses=$(local_addresses || true)
        if [[ -n "${addresses:-}" ]]; then
            echo
            echo "Aktuell erkannte lokale IP-Adressen dieses Rechners (nur Hinweis,"
            echo "es wird nichts davon dauerhaft gespeichert):"
            while read -r address; do
                [[ -n "$address" ]] || continue
                echo "  Web View:      http://$address:$WINLAUFEN_LIVE_HTTP_PORT/"
                echo "  Output Target: ws://$address:$WINLAUFEN_LIVE_WS_PORT$WINLAUFEN_INGEST_PATH_PREFIX$WINLAUFEN_LIVE_CHANNEL"
            done <<< "$addresses"
        fi
        ;;
esac

if ((install_live)); then
    cat <<EOF

Hinweis zur Sicherheit: Diese Version ist ein Prototyp für kontrollierte Netze
und verwendet ein bekanntes Ingest-Secret. Port $WINLAUFEN_LIVE_WS_PORT darf nicht aus nicht
vertrauenswürdigen Netzen erreichbar sein. Details in README.md, Abschnitt
"Known prototype security limitation".
EOF
fi
