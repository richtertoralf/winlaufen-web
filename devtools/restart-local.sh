#!/usr/bin/env bash
set -eu
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
"$script_dir/stop-local.sh"
exec "$script_dir/start-local.sh"
