#include "app_state.h"

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "cJSON.h"
#include "esp_mac.h"

static const char *const STATE_NAMES[STATE_COUNT] = {
    [STATE_UNPAIRED] = "unpaired",       [STATE_PAIRED_WAVE] = "paired_wave",
    [STATE_IDLE] = "idle",               [STATE_NEGOTIATING] = "negotiating",
    [STATE_MATCH] = "match",             [STATE_NO_MATCH] = "no_match",
    [STATE_MET] = "met",
};

const char *badge_state_name(badge_state_id_t id)
{
    return (id < STATE_COUNT) ? STATE_NAMES[id] : "?";
}

static bool state_from_name(const char *name, badge_state_id_t *out)
{
    for (int i = 0; i < STATE_COUNT; i++) {
        if (strcmp(name, STATE_NAMES[i]) == 0) {
            *out = (badge_state_id_t)i;
            return true;
        }
    }
    return false;
}

static void copy_string_field(const cJSON *root, const char *key, char *dst, size_t dst_len)
{
    const cJSON *item = cJSON_GetObjectItemCaseSensitive(root, key);
    if (cJSON_IsString(item) && item->valuestring != NULL) {
        strlcpy(dst, item->valuestring, dst_len);
    }
}

// Accepts "RRGGBB" or "#RRGGBB".
static uint32_t parse_color(const cJSON *root)
{
    const cJSON *item = cJSON_GetObjectItemCaseSensitive(root, "colorHex");
    if (!cJSON_IsString(item) || item->valuestring == NULL) {
        return 0;
    }
    const char *hex = item->valuestring;
    if (hex[0] == '#') {
        hex++;
    }
    return (strlen(hex) == 6) ? (uint32_t)strtoul(hex, NULL, 16) : 0;
}

// Accepts exactly 16 hex chars (8 bytes). Anything else = no token.
static bool parse_token(const cJSON *root, uint8_t token[BLE_TOKEN_LEN])
{
    const cJSON *item = cJSON_GetObjectItemCaseSensitive(root, "bleToken");
    if (!cJSON_IsString(item) || item->valuestring == NULL) {
        return false;
    }
    const char *hex = item->valuestring;
    if (strlen(hex) != BLE_TOKEN_LEN * 2) {
        return false;
    }
    for (int i = 0; i < BLE_TOKEN_LEN; i++) {
        if (!isxdigit((unsigned char)hex[2 * i]) || !isxdigit((unsigned char)hex[2 * i + 1])) {
            return false;
        }
        const char byte_str[3] = { hex[2 * i], hex[2 * i + 1], '\0' };
        token[i] = (uint8_t)strtoul(byte_str, NULL, 16);
    }
    return true;
}

bool badge_state_parse(const char *json, badge_state_t *out)
{
    cJSON *root = cJSON_Parse(json);
    if (root == NULL) {
        return false;
    }
    badge_state_t parsed;
    memset(&parsed, 0, sizeof(parsed));

    const cJSON *state = cJSON_GetObjectItemCaseSensitive(root, "state");
    bool ok = cJSON_IsString(state) && state->valuestring != NULL &&
              state_from_name(state->valuestring, &parsed.state);
    if (ok) {
        copy_string_field(root, "ownerName", parsed.owner_name, sizeof(parsed.owner_name));
        copy_string_field(root, "otherName", parsed.other_name, sizeof(parsed.other_name));
        parsed.color_rgb = parse_color(root);
        parsed.has_token = parse_token(root, parsed.token);
        *out = parsed;
    }
    cJSON_Delete(root);
    return ok;
}

bool badge_state_equal(const badge_state_t *a, const badge_state_t *b)
{
    return a->state == b->state && a->color_rgb == b->color_rgb &&
           a->has_token == b->has_token &&
           memcmp(a->token, b->token, BLE_TOKEN_LEN) == 0 &&
           strcmp(a->owner_name, b->owner_name) == 0 &&
           strcmp(a->other_name, b->other_name) == 0;
}

void box_id_read(char out[7])
{
    uint8_t mac[6] = { 0 };
    esp_read_mac(mac, ESP_MAC_WIFI_STA);
    snprintf(out, 7, "%02X%02X%02X", mac[3], mac[4], mac[5]);
}
