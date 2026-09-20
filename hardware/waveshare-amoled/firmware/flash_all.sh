#!/usr/bin/env bash
# Flash the already-built Klick badge firmware onto every Espressif board that
# is plugged in, and print each badge's id (last 6 hex digits of its MAC, the
# same id the badge shows under its QR code).
#
#   ./idf.sh build      # once, after any source change
#   ./flash_all.sh      # as many times as you have boards
set -uo pipefail
here="$(dirname "$(readlink -f "$0")")"
found=0
for port in /dev/ttyACM*; do
  [ -e "$port" ] || continue
  props="$(udevadm info -q property -n "$port" 2>/dev/null)"
  grep -q '^ID_VENDOR_ID=303a$' <<<"$props" || continue      # Espressif only, skips phones
  mac="$(grep '^ID_SERIAL_SHORT=' <<<"$props" | cut -d= -f2)"
  box_id="$(tr -d ':' <<<"$mac" | tail -c 7 | tr '[:lower:]' '[:upper:]')"
  found=$((found + 1))
  printf '%s  badge %s  ... ' "$port" "$box_id"
  if "$here/idf.sh" -p "$port" flash >"/tmp/klick-flash-$box_id.log" 2>&1; then
    echo "flashed"
  else
    echo "FAILED (see /tmp/klick-flash-$box_id.log; no Reset button here: unplug, hold BOOT, plug in, retry)"
  fi
done
[ "$found" -gt 0 ] || { echo "No Espressif boards found. Plug one in with a data cable."; exit 1; }
