#!/usr/bin/env bash
set -eu
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
"$script_dir/component.sh" status bridge
"$script_dir/component.sh" status live-server
