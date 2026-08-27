#!/usr/bin/env bash
set -eu
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
"$script_dir/component.sh" start live-server
if ! "$script_dir/component.sh" start bridge; then
  "$script_dir/component.sh" stop live-server
  exit 1
fi
