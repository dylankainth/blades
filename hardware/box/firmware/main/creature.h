// The Kindred creature: concentric rings, a body with two blinking eyes and
// three orbiting dots. Fully procedural (one LVGL timer, no images/GIFs).
#pragma once
#include "lvgl.h"

#define CREATURE_SIZE 170

typedef enum {
    MOOD_IDLE = 0,    // calm orbit, slow breathing, blinks
    MOOD_CHAT,        // negotiating: fast orbit, spinning arc, eyes dart
    MOOD_WAVE,        // one dot becomes a waving hand
    MOOD_SHRUG,       // shoulders up, eyes half closed
    MOOD_CELEBRATE,   // fast, bouncy, rainbow
} creature_mood_t;

// Call with the LVGL lock held. Single instance.
lv_obj_t *creature_create(lv_obj_t *parent);
void creature_set_mood(creature_mood_t mood);
// ink = rings/body/dots, eye = eye colour (use the screen background).
// accents = true paints the three dots in their own colours instead of ink.
void creature_set_colors(lv_color_t ink, lv_color_t eye, bool accents);
