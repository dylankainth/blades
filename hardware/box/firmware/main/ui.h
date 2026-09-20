// Screens for the badge. All functions must be called with the LVGL lock held
// (bsp_display_lock / bsp_display_unlock).
#pragma once
#include "app_state.h"

void ui_init(const char *box_id);

// Renders a backend state. Cheap no-op when nothing changed. One-off states
// (paired_wave, no_match, met) animate once, then fall back to the nametag.
void ui_apply_state(const badge_state_t *state);

// Forget what is on screen so the next ui_apply_state() always re-renders.
void ui_invalidate(void);
