#include "creature.h"

#define CENTER        (CREATURE_SIZE / 2)
#define DOT_COUNT     3
#define FRAME_MS      33
#define OUTER_D       112
#define MID_D         80
#define BODY_D        48
#define ORBIT_R       64
#define DOT_D         12
#define EYE_W         7
#define EYE_H         14
#define EYE_GAP       9
#define BLINK_EVERY_MS 3600
#define BLINK_LEN_MS   240

// Everything that can move in one animation frame.
typedef struct {
    int32_t dot_deg[DOT_COUNT];
    int32_t dot_r[DOT_COUNT];
    int32_t dot_d[DOT_COUNT];
    int32_t dot_dy[DOT_COUNT];
    int32_t outer_d;
    int32_t mid_d;
    int32_t eye_h;
    int32_t eye_dx;
    int32_t body_dy;
    bool arc_on;
    int32_t arc_rot;
    bool rainbow;
} frame_t;

static const uint32_t ACCENT_HEX[DOT_COUNT] = { 0x2dd4bf, 0xfbbf24, 0xf472b6 };

static lv_obj_t *s_root, *s_outer, *s_mid, *s_body, *s_arc;
static lv_obj_t *s_eyes[2];
static lv_obj_t *s_dots[DOT_COUNT];
static creature_mood_t s_mood;
static uint32_t s_mood_start;
static lv_color_t s_ink;
static bool s_accents;

static int32_t norm_deg(int32_t deg)
{
    return ((deg % 360) + 360) % 360;
}

static int32_t sin_amp(int32_t deg, int32_t amp)
{
    return (lv_trigo_sin((int16_t)norm_deg(deg)) * amp) >> LV_TRIGO_SHIFT;
}

static int32_t cos_amp(int32_t deg, int32_t amp)
{
    return sin_amp(deg + 90, amp);
}

// Sine oscillation of +-amp with the given period.
static int32_t osc(uint32_t t_ms, uint32_t period_ms, int32_t amp)
{
    return sin_amp((int32_t)((t_ms % period_ms) * 360 / period_ms), amp);
}

static int32_t spin(uint32_t t_ms, uint32_t period_ms)
{
    return (int32_t)((t_ms % period_ms) * 360 / period_ms);
}

static int32_t blink_height(uint32_t t_ms)
{
    const int32_t phase = (int32_t)(t_ms % BLINK_EVERY_MS);
    if (phase >= BLINK_LEN_MS) {
        return EYE_H;
    }
    const int32_t half = BLINK_LEN_MS / 2;
    const int32_t h = EYE_H * LV_ABS(phase - half) / half;
    return LV_MAX(h, 2);
}

static frame_t frame_idle(uint32_t t)
{
    frame_t f = {
        .outer_d = OUTER_D + osc(t, 3200, 4),
        .mid_d = MID_D + osc(t + 800, 3200, 3),
        .eye_h = blink_height(t),
    };
    for (int i = 0; i < DOT_COUNT; i++) {
        f.dot_deg[i] = spin(t, 6000) + i * 120;
        f.dot_r[i] = ORBIT_R;
        f.dot_d[i] = DOT_D;
    }
    return f;
}

static frame_t frame_chat(uint32_t t)
{
    frame_t f = frame_idle(t);
    f.outer_d = OUTER_D + osc(t, 500, 3);
    f.eye_dx = osc(t, 1200, 5);
    f.arc_on = true;
    f.arc_rot = spin(t, 900);
    for (int i = 0; i < DOT_COUNT; i++) {
        f.dot_deg[i] = spin(t, 1400) + i * 120;
        f.dot_r[i] = ORBIT_R + osc(t + i * 230, 700, 7);
    }
    return f;
}

static frame_t frame_wave(uint32_t t)
{
    frame_t f = frame_idle(t);
    f.eye_h = 8;                                // happy squint
    f.body_dy = osc(t, 900, 3);
    f.dot_deg[0] = 315 + osc(t, 450, 28);       // the hand, up and to the right
    f.dot_r[0] = ORBIT_R + 10;
    f.dot_d[0] = DOT_D + 6;
    f.dot_deg[1] = 150 + osc(t, 1800, 8);
    f.dot_deg[2] = 205 + osc(t + 400, 1800, 8);
    return f;
}

static frame_t frame_shrug(uint32_t t)
{
    frame_t f = frame_idle(t);
    const int32_t lift = -LV_ABS(osc(t, 1200, 14));
    f.eye_h = 5;
    f.eye_dx = osc(t, 1600, 6);
    f.body_dy = 2;
    f.dot_deg[0] = 180;
    f.dot_deg[1] = 0;
    f.dot_deg[2] = 90;
    f.dot_dy[0] = lift;
    f.dot_dy[1] = lift;
    return f;
}

static frame_t frame_celebrate(uint32_t t)
{
    frame_t f = frame_idle(t);
    f.outer_d = OUTER_D + osc(t, 400, 10);
    f.mid_d = MID_D + osc(t + 100, 400, 6);
    f.eye_h = 8;
    f.rainbow = true;
    for (int i = 0; i < DOT_COUNT; i++) {
        f.dot_deg[i] = spin(t, 800) + i * 120;
        f.dot_r[i] = ORBIT_R - 2 + osc(t, 600, 14);
        f.dot_d[i] = DOT_D + 2 + osc(t + i * 100, 300, 4);
    }
    return f;
}

static void place_circle(lv_obj_t *obj, int32_t cx, int32_t cy, int32_t d)
{
    lv_obj_set_size(obj, d, d);
    lv_obj_set_pos(obj, cx - d / 2, cy - d / 2);
}

static void paint(lv_color_t ink)
{
    lv_obj_set_style_border_color(s_outer, ink, 0);
    lv_obj_set_style_border_color(s_mid, ink, 0);
    lv_obj_set_style_bg_color(s_body, ink, 0);
    lv_obj_set_style_arc_color(s_arc, ink, LV_PART_INDICATOR);
    for (int i = 0; i < DOT_COUNT; i++) {
        lv_obj_set_style_bg_color(s_dots[i], s_accents ? lv_color_hex(ACCENT_HEX[i]) : ink, 0);
    }
}

static void render(const frame_t *f, uint32_t t)
{
    const int32_t cy = CENTER + f->body_dy;
    place_circle(s_outer, CENTER, cy, f->outer_d);
    place_circle(s_mid, CENTER, cy, f->mid_d);
    place_circle(s_body, CENTER, cy, BODY_D);
    for (int i = 0; i < 2; i++) {
        const int32_t side = (i == 0) ? -1 : 1;
        lv_obj_set_size(s_eyes[i], EYE_W, f->eye_h);
        lv_obj_align(s_eyes[i], LV_ALIGN_CENTER, side * EYE_GAP + f->eye_dx, -2);
    }
    for (int i = 0; i < DOT_COUNT; i++) {
        const int32_t x = CENTER + cos_amp(f->dot_deg[i], f->dot_r[i]);
        const int32_t y = cy + sin_amp(f->dot_deg[i], f->dot_r[i]) + f->dot_dy[i];
        place_circle(s_dots[i], x, y, f->dot_d[i]);
    }
    if (f->arc_on) {
        lv_obj_remove_flag(s_arc, LV_OBJ_FLAG_HIDDEN);
        lv_arc_set_rotation(s_arc, f->arc_rot);
    } else {
        lv_obj_add_flag(s_arc, LV_OBJ_FLAG_HIDDEN);
    }
    if (f->rainbow) {
        paint(lv_color_hsv_to_rgb((uint16_t)((t / 6) % 360), 70, 100));
    }
}

static void on_tick(lv_timer_t *timer)
{
    (void)timer;
    const uint32_t t = lv_tick_elaps(s_mood_start);
    frame_t f;
    switch (s_mood) {
    case MOOD_CHAT:      f = frame_chat(t);      break;
    case MOOD_WAVE:      f = frame_wave(t);      break;
    case MOOD_SHRUG:     f = frame_shrug(t);     break;
    case MOOD_CELEBRATE: f = frame_celebrate(t); break;
    default:             f = frame_idle(t);      break;
    }
    render(&f, t);
}

static lv_obj_t *make_shape(lv_obj_t *parent, int32_t border_w, bool filled)
{
    lv_obj_t *obj = lv_obj_create(parent);
    lv_obj_remove_style_all(obj);
    lv_obj_remove_flag(obj, LV_OBJ_FLAG_SCROLLABLE | LV_OBJ_FLAG_CLICKABLE);
    lv_obj_set_style_radius(obj, LV_RADIUS_CIRCLE, 0);
    lv_obj_set_style_border_width(obj, border_w, 0);
    lv_obj_set_style_bg_opa(obj, filled ? LV_OPA_COVER : LV_OPA_TRANSP, 0);
    return obj;
}

static lv_obj_t *make_chat_arc(lv_obj_t *parent)
{
    lv_obj_t *arc = lv_arc_create(parent);
    lv_obj_remove_style(arc, NULL, LV_PART_KNOB);
    lv_obj_remove_flag(arc, LV_OBJ_FLAG_CLICKABLE);
    lv_obj_set_style_arc_opa(arc, LV_OPA_TRANSP, LV_PART_MAIN);
    lv_obj_set_style_arc_width(arc, 5, LV_PART_INDICATOR);
    lv_arc_set_bg_angles(arc, 0, 360);
    lv_arc_set_angles(arc, 0, 80);
    lv_obj_set_size(arc, 98, 98);
    lv_obj_center(arc);
    lv_obj_add_flag(arc, LV_OBJ_FLAG_HIDDEN);
    return arc;
}

lv_obj_t *creature_create(lv_obj_t *parent)
{
    s_root = lv_obj_create(parent);
    lv_obj_remove_style_all(s_root);
    lv_obj_remove_flag(s_root, LV_OBJ_FLAG_SCROLLABLE | LV_OBJ_FLAG_CLICKABLE);
    lv_obj_set_size(s_root, CREATURE_SIZE, CREATURE_SIZE);

    s_outer = make_shape(s_root, 3, false);
    s_mid = make_shape(s_root, 4, false);
    s_arc = make_chat_arc(s_root);
    s_body = make_shape(s_root, 0, true);
    for (int i = 0; i < 2; i++) {
        s_eyes[i] = make_shape(s_body, 0, true);
    }
    for (int i = 0; i < DOT_COUNT; i++) {
        s_dots[i] = make_shape(s_root, 0, true);
    }
    creature_set_colors(lv_color_white(), lv_color_black(), true);
    creature_set_mood(MOOD_IDLE);
    lv_timer_create(on_tick, FRAME_MS, NULL);
    return s_root;
}

void creature_set_mood(creature_mood_t mood)
{
    s_mood = mood;
    s_mood_start = lv_tick_get();
    paint(s_ink);   // drop any rainbow tint left over from MOOD_CELEBRATE
}

void creature_set_colors(lv_color_t ink, lv_color_t eye, bool accents)
{
    s_ink = ink;
    s_accents = accents;
    paint(ink);
    for (int i = 0; i < 2; i++) {
        lv_obj_set_style_bg_color(s_eyes[i], eye, 0);
    }
}
