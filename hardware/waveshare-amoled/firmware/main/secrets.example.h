// Copy this file to secrets.h (gitignored) and edit. Never commit secrets.h.
#pragma once

// Ordered list of Wi-Fi networks, tried top to bottom, forever, with backoff.
// Empty password = open network. 2.4 GHz only: the ESP32-S3 cannot see 5 GHz.
#define WIFI_NETWORKS                              \
    { "MIT GUEST", "" },                           \
    { "my-phone-hotspot", "hotspot-password" },

// Optional: override the endpoints from config.h without touching tracked files.
// #define STATE_URL  "https://us-central1-YOUR_PROJECT.cloudfunctions.net/boxState"
// #define EVENTS_URL "https://us-central1-YOUR_PROJECT.cloudfunctions.net/boxEvent"
