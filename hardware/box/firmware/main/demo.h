// Physical buttons: offline demo + a manual fallback for the shake gesture.
//   BOOT/CONFIG (side): step through every badge state without a backend;
//                       after the last one the screen returns to the backend.
//   MUTE (top) and the red circle under the LCD: send the same event as a shake.
#pragma once
#include <stdbool.h>

void demo_start(void);
bool demo_is_active(void);
