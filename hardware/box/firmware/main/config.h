// Build-time configuration for the Kindred badge. Anything here can be
// overridden from secrets.h (it is included first).
#pragma once

#if __has_include("secrets.h")
#include "secrets.h"
#else
#warning "main/secrets.h not found - building with secrets.example.h placeholders"
#include "secrets.example.h"
#endif

// GET  <STATE_URL>?boxId=ABCDEF  ->  {state, ownerName, otherName, colorHex, bleToken}
#ifndef STATE_URL
#define STATE_URL "https://us-central1-blades-a38f5.cloudfunctions.net/boxState"
#endif
// POST <EVENTS_URL>  body: {"boxId":"ABCDEF","event":"shake"}
#ifndef EVENTS_URL
#define EVENTS_URL "https://us-central1-blades-a38f5.cloudfunctions.net/boxEvent"
#endif

#define POLL_INTERVAL_MS     1000
#define HTTP_TIMEOUT_MS      5000
#define QR_PREFIX            "kindred-box:"

// 128-bit service UUID the phone app scans for, in normal (big-endian) text
// order. Matches android/.../ble/BleConstants.kt (0000f00d-cafe-4dad-8000-00805f9b34fb). Sent little-endian on air, as BLE requires.
#define BLE_SERVICE_UUID_BYTES                                                  \
    0x00, 0x00, 0xf0, 0x0d, 0xca, 0xfe, 0x4d, 0xad,                             \
    0x80, 0x00, 0x00, 0x80, 0x5f, 0x9b, 0x34, 0xfb
#define BLE_ADV_INTERVAL_MS  150
// 0 = advert holds ONLY the Service Data AD (26 bytes). 1 = also a 3-byte Flags AD (29).
#define BLE_ADV_INCLUDE_FLAGS 0

// Shake / bump: |accel| must exceed 1 g by this much. Debounced.
#define SHAKE_THRESHOLD_G    1.5f
#define SHAKE_DEBOUNCE_MS    2000

// How long one-off animations run before the badge falls back to the nametag.
#define TRANSIENT_ANIM_MS    3500

// Set to 1 if the badge hangs upside-down on the lanyard.
#define DISPLAY_ROTATE_180   0
