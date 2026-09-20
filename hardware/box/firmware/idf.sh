#!/usr/bin/env bash
# Thin wrapper around idf.py for this project. Usage:
#   ./idf.sh build
#   ./idf.sh -p /dev/ttyACM0 flash monitor      (Ctrl+] quits the monitor)
#   ./idf.sh fullclean                          (after editing sdkconfig.defaults, also rm sdkconfig)
# /usr/bin goes first in PATH because the ESP-IDF venv on this machine was
# created with the system Python (3.14); the pyenv 3.11 shim breaks export.sh.
set -euo pipefail
HERE="$(dirname "$(readlink -f "$0")")"
export PATH="/usr/bin:$PATH"
export IDF_PATH="${IDF_PATH:-$HOME/esp/esp-idf}"
# shellcheck disable=SC1091
. "$IDF_PATH/export.sh" >/dev/null
exec idf.py -C "$HERE" "$@"
