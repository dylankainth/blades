#include "ui.h"

#include <stdio.h>
#include <string.h>

#include "config.h"
#include "creature.h"
#include "lvgl.h"
#include "wifi_multi.h"

#define COLOR_BG        0x101418
#define COLOR_INK       0xffffff
#define QR_SIZE         150
#define BIG_NAME_MAX    9      // longer titles drop to the smaller font
#define WIFI_REFRESH_MS 1000
#define LUMA_DARK_INK   150    // backgrounds brighter than this get black ink

static lv_obj_t *s_title;
static lv_obj_t *s_creature;
static lv_obj_t *s_pair_panel;
static lv_obj_t *s_wifi_label;
static lv_timer_t *s_transient_timer;
static badge_state_t s_shown;
static bool s_has_shown;

static bool is_transient(badge_state_id_t id)
{
    return id == STATE_PAIRED_WAVE || id == STATE_NO_MATCH || id == STATE_MET;
}

static bool is_bright(uint32_t rgb)
{
    const uint32_t r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;
    return (299 * r + 587 * g + 114 * b) / 1000 > LUMA_DARK_INK;
}

static void set_title(const char *text, lv_color_t color)
{
    const bool big = strlen(text) <= BIG_NAME_MAX;
    lv_obj_set_style_text_font(s_title, big ? &lv_font_montserrat_48 : &lv_font_montserrat_28, 0);
    lv_obj_set_style_text_color(s_title, color, 0);
    lv_label_set_text(s_title, text);
    lv_obj_align(s_title, LV_ALIGN_TOP_MID, 0, big ? 6 : 18);
}

// Paints the badge (non-pairing) layout: flood colour, title, creature mood.
static void show_badge(uint32_t bg_rgb, const char *title, creature_mood_t mood, bool accents)
{
    const lv_color_t bg = lv_color_hex(bg_rgb);
    const lv_color_t ink = is_bright(bg_rgb) ? lv_color_black() : lv_color_hex(COLOR_INK);
    lv_obj_set_style_bg_color(lv_screen_active(), bg, 0);
    lv_obj_add_flag(s_pair_panel, LV_OBJ_FLAG_HIDDEN);
    lv_obj_remove_flag(s_title, LV_OBJ_FLAG_HIDDEN);
    lv_obj_remove_flag(s_creature, LV_OBJ_FLAG_HIDDEN);
    set_title(title, ink);
    creature_set_colors(ink, bg, accents);
    creature_set_mood(mood);
}

static void show_pairing(void)
{
    lv_obj_set_style_bg_color(lv_screen_active(), lv_color_hex(COLOR_BG), 0);
    lv_obj_add_flag(s_title, LV_OBJ_FLAG_HIDDEN);
    lv_obj_add_flag(s_creature, LV_OBJ_FLAG_HIDDEN);
    lv_obj_remove_flag(s_pair_panel, LV_OBJ_FLAG_HIDDEN);
}

static void show_nametag(const badge_state_t *st)
{
    show_badge(COLOR_BG, st->owner_name[0] ? st->owner_name : "Kindred", MOOD_IDLE, true);
}

static void on_transient_done(lv_timer_t *timer)
{
    (void)timer;              // one-shot: LVGL deletes it after this callback
    s_transient_timer = NULL;
    show_nametag(&s_shown);
}

static void cancel_transient(void)
{
    if (s_transient_timer != NULL) {
        lv_timer_delete(s_transient_timer);
        s_transient_timer = NULL;
    }
}

static void render(const badge_state_t *st)
{
    char text[NAME_MAX_LEN + 8];
    switch (st->state) {
    case STATE_UNPAIRED:
        show_pairing();
        break;
    case STATE_PAIRED_WAVE:
        snprintf(text, sizeof(text), "Hi %s!", st->owner_name);
        show_badge(COLOR_BG, text, MOOD_WAVE, true);
        break;
    case STATE_NEGOTIATING:
        show_badge(COLOR_BG, st->owner_name, MOOD_CHAT, true);
        break;
    case STATE_MATCH:
        // TODO: Montserrat has no emoji glyphs; the waving creature stands in for the wave emoji.
        snprintf(text, sizeof(text), "Hi %s", st->other_name);
        show_badge(st->color_rgb, text, MOOD_WAVE, false);
        break;
    case STATE_NO_MATCH:
        show_badge(COLOR_BG, st->owner_name, MOOD_SHRUG, true);
        break;
    case STATE_MET:
        show_badge(COLOR_BG, st->owner_name, MOOD_CELEBRATE, true);
        break;
    case STATE_IDLE:
    default:
        show_nametag(st);
        break;
    }
}

void ui_apply_state(const badge_state_t *state)
{
    if (s_has_shown && badge_state_equal(&s_shown, state)) {
        return;
    }
    const bool state_changed = !s_has_shown || s_shown.state != state->state;
    s_shown = *state;
    s_has_shown = true;
    if (!state_changed && is_transient(state->state)) {
        return;   // same one-off state, only details changed: do not replay it
    }
    cancel_transient();
    render(state);
    if (is_transient(state->state)) {
        s_transient_timer = lv_timer_create(on_transient_done, TRANSIENT_ANIM_MS, NULL);
        lv_timer_set_repeat_count(s_transient_timer, 1);
    }
}

void ui_invalidate(void)
{
    s_has_shown = false;
}

static void on_wifi_refresh(lv_timer_t *timer)
{
    (void)timer;
    char ssid[33];
    wifi_multi_get_ssid(ssid, sizeof(ssid));
    lv_label_set_text_fmt(s_wifi_label, LV_SYMBOL_WIFI " %s", ssid[0] ? ssid : "no wifi");
}

static lv_obj_t *make_label(lv_obj_t *parent, const lv_font_t *font, uint32_t rgb, const char *text)
{
    lv_obj_t *label = lv_label_create(parent);
    lv_obj_set_style_text_font(label, font, 0);
    lv_obj_set_style_text_color(label, lv_color_hex(rgb), 0);
    lv_label_set_text(label, text);
    return label;
}

// Left: white card with the QR and the boxId under it. Right: instructions + Wi-Fi.
static void build_pair_panel(lv_obj_t *screen, const char *box_id)
{
    s_pair_panel = lv_obj_create(screen);
    lv_obj_remove_style_all(s_pair_panel);
    lv_obj_remove_flag(s_pair_panel, LV_OBJ_FLAG_SCROLLABLE | LV_OBJ_FLAG_CLICKABLE);
    lv_obj_set_size(s_pair_panel, lv_pct(100), lv_pct(100));

    lv_obj_t *card = lv_obj_create(s_pair_panel);
    lv_obj_remove_style_all(card);
    lv_obj_remove_flag(card, LV_OBJ_FLAG_SCROLLABLE | LV_OBJ_FLAG_CLICKABLE);
    lv_obj_set_size(card, QR_SIZE + 20, QR_SIZE + 56);
    lv_obj_set_style_bg_color(card, lv_color_white(), 0);
    lv_obj_set_style_bg_opa(card, LV_OPA_COVER, 0);
    lv_obj_set_style_radius(card, 10, 0);
    lv_obj_align(card, LV_ALIGN_LEFT_MID, 12, 0);

    char payload[40];
    snprintf(payload, sizeof(payload), QR_PREFIX "%s", box_id);
    lv_obj_t *qr = lv_qrcode_create(card);
    lv_qrcode_set_size(qr, QR_SIZE);
    lv_qrcode_set_dark_color(qr, lv_color_black());
    lv_qrcode_set_light_color(qr, lv_color_white());
    lv_qrcode_update(qr, payload, strlen(payload));
    lv_obj_align(qr, LV_ALIGN_TOP_MID, 0, 10);

    lv_obj_t *id_label = make_label(card, &lv_font_montserrat_28, 0x000000, box_id);
    lv_obj_align(id_label, LV_ALIGN_BOTTOM_MID, 0, -6);

    lv_obj_t *brand = make_label(s_pair_panel, &lv_font_montserrat_28, COLOR_INK, "Kindred");
    lv_obj_align(brand, LV_ALIGN_TOP_RIGHT, -14, 40);
    lv_obj_t *hint = make_label(s_pair_panel, &lv_font_montserrat_20, 0x9ca3af, "Scan to\npair");
    lv_obj_set_style_text_align(hint, LV_TEXT_ALIGN_RIGHT, 0);
    lv_obj_align(hint, LV_ALIGN_RIGHT_MID, -14, 6);

    s_wifi_label = make_label(s_pair_panel, &lv_font_montserrat_14, 0x9ca3af, "");
    lv_obj_set_width(s_wifi_label, 118);
    lv_label_set_long_mode(s_wifi_label, LV_LABEL_LONG_WRAP);
    lv_obj_set_style_text_align(s_wifi_label, LV_TEXT_ALIGN_RIGHT, 0);
    lv_obj_align(s_wifi_label, LV_ALIGN_BOTTOM_RIGHT, -14, -12);
}

void ui_init(const char *box_id)
{
    lv_obj_t *screen = lv_screen_active();
    lv_obj_remove_flag(screen, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_set_style_bg_color(screen, lv_color_hex(COLOR_BG), 0);
    lv_obj_set_style_bg_opa(screen, LV_OPA_COVER, 0);

    s_creature = creature_create(screen);
    lv_obj_align(s_creature, LV_ALIGN_BOTTOM_MID, 0, 0);
    s_title = make_label(screen, &lv_font_montserrat_48, COLOR_INK, "");
    build_pair_panel(screen, box_id);

    on_wifi_refresh(NULL);
    lv_timer_create(on_wifi_refresh, WIFI_REFRESH_MS, NULL);

    // Boot into the pairing screen: correct before binding, and it shows the
    // Wi-Fi status even when the backend is unreachable.
    const badge_state_t boot = { .state = STATE_UNPAIRED };
    ui_apply_state(&boot);
}
