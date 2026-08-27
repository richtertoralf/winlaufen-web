#!/usr/bin/env bash
# Deinstalliert WinLaufen Web unter Linux.
#
#   sudo ./uninstall.sh                 Dienste und Programmdateien entfernen
#   sudo ./uninstall.sh --purge         zusätzlich Konfiguration und Zustand entfernen
#
# Ohne --purge bleiben /etc/winlaufen-web und /var/lib/winlaufen-web erhalten,
# damit eine gepflegte WinLaufen-Adresse und Target-Liste eine Neuinstallation
# überleben.
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
# shellcheck source=../common/dist-manifest.env
source "$script_dir/../common/dist-manifest.env"

PURGE=0
STAGING_ROOT=""
USE_SYSTEMD=1

while (($#)); do
    case "$1" in
        --purge) PURGE=1; shift ;;
        --staging-root) STAGING_ROOT=$2; shift 2 ;;
        --no-systemd) USE_SYSTEMD=0; shift ;;
        -h|--help) sed -n '2,12p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Unbekannte Option: $1" >&2; exit 2 ;;
    esac
done

INSTALL_PREFIX="/opt/winlaufen-web"
CONFIG_DIR="/etc/winlaufen-web"
STATE_DIR="/var/lib/winlaufen-web"
SERVICE_USER="winlaufen"
SYSTEMD_DIR="/etc/systemd/system"
UNITS=("winlaufen-bridge.service" "winlaufen-live-server.service")

staged() { printf '%s' "${STAGING_ROOT}$1"; }

[[ -n "$STAGING_ROOT" || $EUID -eq 0 ]] || { echo "FEHLER: Bitte mit sudo ausführen." >&2; exit 1; }

echo "== Dienste stoppen und entfernen =="
for unit in "${UNITS[@]}"; do
    if [[ -z "$STAGING_ROOT" ]] && ((USE_SYSTEMD)); then
        systemctl disable --now "$unit" >/dev/null 2>&1 || true
    fi
    if [[ -f "$(staged "$SYSTEMD_DIR/$unit")" ]]; then
        rm -f -- "$(staged "$SYSTEMD_DIR/$unit")"
        echo "  entfernt: $unit"
    fi
done
if [[ -z "$STAGING_ROOT" ]] && ((USE_SYSTEMD)); then
    systemctl daemon-reload
fi

echo "== Programmdateien entfernen =="
rm -rf -- "$(staged "$INSTALL_PREFIX")"
echo "  entfernt: $INSTALL_PREFIX"

if ((PURGE)); then
    echo "== Konfiguration und Zustand entfernen (--purge) =="
    rm -rf -- "$(staged "$CONFIG_DIR")" "$(staged "$STATE_DIR")"
    echo "  entfernt: $CONFIG_DIR"
    echo "  entfernt: $STATE_DIR"
    if [[ -z "$STAGING_ROOT" ]] && getent passwd "$SERVICE_USER" >/dev/null 2>&1; then
        userdel "$SERVICE_USER" >/dev/null 2>&1 || true
        echo "  entfernt: Dienstkonto $SERVICE_USER"
    fi
else
    echo
    echo "Konfiguration und Zustand wurden bewusst nicht entfernt:"
    echo "  $CONFIG_DIR"
    echo "  $STATE_DIR"
    echo "Zum vollständigen Entfernen: sudo ./uninstall.sh --purge"
fi

echo
echo "Deinstallation abgeschlossen."
