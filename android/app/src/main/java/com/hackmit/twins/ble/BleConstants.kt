package com.hackmit.twins.ble

import java.util.UUID

/**
 * Shared BLE identifiers for the proximity-matching feature.
 *
 * TODO(demo-day): generate a real random 128-bit UUID for this app
 * (e.g. `uuidgen`) and replace the placeholder below. Every install of the
 * app must use the SAME UUID — it's how devices recognize "this is another
 * Digital Twins user" during scanning versus random BLE noise from other
 * apps/devices in the room.
 */
object BleConstants {
    val SERVICE_UUID: UUID = UUID.fromString("0000f00d-cafe-4dad-8000-00805f9b34fb")
}
