// Badge state as reported by the backend, plus JSON parsing.
#pragma once
#include <stdbool.h>
#include <stdint.h>

#define BLE_TOKEN_LEN 8
#define NAME_MAX_LEN  32

typedef enum {
    STATE_UNPAIRED = 0,
    STATE_PAIRED_WAVE,
    STATE_IDLE,
    STATE_NEGOTIATING,
    STATE_MATCH,
    STATE_NO_MATCH,
    STATE_MET,
    STATE_COUNT
} badge_state_id_t;

typedef struct {
    badge_state_id_t state;
    char owner_name[NAME_MAX_LEN];
    char other_name[NAME_MAX_LEN];
    uint32_t color_rgb;              // 0xRRGGBB, only meaningful in STATE_MATCH
    bool has_token;
    uint8_t token[BLE_TOKEN_LEN];
} badge_state_t;

const char *badge_state_name(badge_state_id_t id);

// Parses {state, ownerName, otherName, colorHex, bleToken}. Returns false (and
// leaves *out untouched) if the body is not JSON or has no known "state".
bool badge_state_parse(const char *json, badge_state_t *out);

bool badge_state_equal(const badge_state_t *a, const badge_state_t *b);

// Fills out[7] with the last 6 hex chars of the Wi-Fi MAC, uppercase.
void box_id_read(char out[7]);
