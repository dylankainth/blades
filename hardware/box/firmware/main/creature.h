// The Klick mascot, matching the phone app: a rounded-square face with pill
// eyes and a murmuring mouth, pulsing rounded-square rings and three orbiting
// dots. Tap it and it squishes and beams. Fully procedural (one LVGL timer).
#pragma once
#include "lvgl.h"

#define CREATURE_SIZE 170

typedef enum {
    MOOD_IDLE = 0,    // eyes wander and blink, mouth murmurs, calm orbit
    MOOD_CHAT,        // negotiating: chattering mouth, spinning arc, eyes dart
    MOOD_WAVE,        // happy face; the amber dot becomes a waving hand
    MOOD_SHRUG,       // eyes half closed, flat mouth, two dots lift like shoulders
    MOOD_CELEBRATE,   // happy face, little hops, rainbow
} creature_mood_t;

// Call with the LVGL lock held. Single instance.
lv_obj_t *creature_create(lv_obj_t *parent);
void creature_set_mood(creature_mood_t mood);
// ink = face/rings/dots, eye = eyes and mouth (use the screen background).
// accents = true makes one dot amber and sets the other two back, as in the
// app; false paints all three in ink (for a flooded colour screen).
void creature_set_colors(lv_color_t ink, lv_color_t eye, bool accents);
// Squish, then a happy face for about a second. Also wired to a touch on the
// creature itself. Call with the LVGL lock held.
void creature_poke(void);
