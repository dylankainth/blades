// Offline demo + manual fallbacks for the shake gesture. This board has one
// usable button (BOOT, on the side) and a touch screen:
//   BOOT click:        step through every badge state without a backend; after
//                      the last one the screen returns to the backend.
//   BOOT long press:   send the same event as a shake.
//   Hold the screen:   (TOUCH_SHAKE_HOLD_MS) also sends the shake event.
// The PWR button is wired to the power chip (hold 6 s = power off): not used here.
#pragma once
#include <stdbool.h>

void demo_start(void);
bool demo_is_active(void);
