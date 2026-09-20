// The Klick mascot, matching the phone app's ListeningAvatar: a rounded-square
// face with two pill eyes that look around and blink, a mouth that murmurs,
// rounded-square rings pulsing outward, and three orbiting dots (two neutral,
// one amber). Poke it and it squishes, then beams: closed happy eyes, a smile
// and a blush. Fully procedural: one LVGL timer, no images.
#include "creature.h"
#include <math.h>

#define CENTER          (CREATURE_SIZE / 2)
#define FRAME_MS        33
#define DOT_COUNT       3
#define RING_COUNT      2

#define BODY            84                 // face edge length
#define CORNER_PCT      30                 // corner radius, % of edge (as in the app)
#define EYE_W           8
#define EYE_H           19
#define EYE_DX          8                  // eye centre offset from face centre
#define EYE_Y           (-4)
#define MOUTH_H         4
#define MOUTH_Y         13
#define DOT_D           8
#define AMBER_HEX       0xC97B45           // KlickColors.Accent

#define RING_PERIOD_MS  2400
#define BLINK_EVERY_MS  3400
#define BLINK_LEN_MS    150
#define LOOK_EVERY_MS   1300
#define LOOK_RANGE      4                  // px the eyes wander

// Poke: squash down for SQUISH_IN_MS, then ring like a soft spring.
#define SQUISH_IN_MS    90
#define SQUISH_DECAY_MS 170.0f
#define SQUISH_WOBBLE_MS 280.0f
#define SQUISH_WIDEN    0.12f
#define SQUISH_FLATTEN  0.16f
#define DELIGHT_MS      1100               // how long the happy face stays

static const int32_t ORBIT_R[DOT_COUNT] = { 44, 50, 56 };          // app: 1.05 / 1.19 / 1.33 x face
static const uint32_t ORBIT_PERIOD_MS[DOT_COUNT] = { 3000, 4000, 5000 };
static const int32_t MOUTH_TALK_W[] = { 9, 21, 13, 24 };           // app's talk frames

// Everything that can move in one animation frame.
typedef struct {
    int32_t dot_deg[DOT_COUNT];
    int32_t dot_r[DOT_COUNT];
    int32_t dot_d[DOT_COUNT];
    int32_t dot_dy[DOT_COUNT];
    uint32_t ring_period_ms;
    int32_t eye_h;
    int32_t eye_dx;
    int32_t eye_dy;
    int32_t mouth_w;
    int32_t body_dy;
    bool happy;
    bool arc_on;
    int32_t arc_rot;
    bool rainbow;
} frame_t;

static lv_obj_t *s_root, *s_body, *s_mouth, *s_chat_arc, *s_smile;
static lv_obj_t *s_rings[RING_COUNT];
static lv_obj_t *s_eyes[2], *s_happy_eyes[2], *s_blush[2];
static lv_obj_t *s_dots[DOT_COUNT];

static creature_mood_t s_mood;
static uint32_t s_mood_start;
static lv_color_t s_ink;
static bool s_accents;

static int32_t s_look_x16, s_look_y16, s_look_tx, s_look_ty;   // eased eye wander (x16 fixed point)
static uint32_t s_next_look_ms;
static int32_t s_mouth_w16 = 12 * 16;
static uint32_t s_poke_ms;
static bool s_poked;

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
    return (t_ms % BLINK_EVERY_MS) < BLINK_LEN_MS ? 2 : EYE_H;
}

// Squish amount: 0 at rest, 1 fully pressed, negative on the rebound stretch.
static float squish_now(uint32_t now_ms)
{
    if (!s_poked) {
        return 0.0f;
    }
    const uint32_t t = now_ms - s_poke_ms;
    if (t < SQUISH_IN_MS) {
        return (float)t / SQUISH_IN_MS;
    }
    const float after = (float)(t - SQUISH_IN_MS);
    return expf(-after / SQUISH_DECAY_MS) * cosf(6.2832f * after / SQUISH_WOBBLE_MS);
}

static bool delighted_now(uint32_t now_ms)
{
    if (s_poked && now_ms - s_poke_ms >= DELIGHT_MS) {
        s_poked = false;
    }
    return s_poked;
}

static frame_t frame_idle(uint32_t t)
{
    frame_t f = {
        .ring_period_ms = RING_PERIOD_MS,
        .eye_h = blink_height(t),
        .eye_dx = s_look_x16 / 16,
        .eye_dy = s_look_y16 / 16,
        .mouth_w = MOUTH_TALK_W[(t / 360) % 4],
    };
    for (int i = 0; i < DOT_COUNT; i++) {
        f.dot_deg[i] = spin(t, ORBIT_PERIOD_MS[i]) + i * 120;
        f.dot_r[i] = ORBIT_R[i];
        f.dot_d[i] = DOT_D;
    }
    return f;
}

static frame_t frame_chat(uint32_t t)
{
    frame_t f = frame_idle(t);
    f.ring_period_ms = 900;
    f.eye_dx = osc(t, 1200, 5);
    f.eye_dy = 0;
    f.mouth_w = MOUTH_TALK_W[(t / 170) % 4];      // chattering
    f.arc_on = true;
    f.arc_rot = spin(t, 900);
    for (int i = 0; i < DOT_COUNT; i++) {
        f.dot_deg[i] = spin(t, 1400) + i * 120;
        f.dot_r[i] = ORBIT_R[i] + osc(t + i * 230, 700, 5);
    }
    return f;
}

static frame_t frame_wave(uint32_t t)
{
    frame_t f = frame_idle(t);
    f.happy = true;
    f.body_dy = osc(t, 900, 3);
    f.dot_deg[2] = 315 + osc(t, 450, 28);         // the amber dot is the waving hand
    f.dot_r[2] = ORBIT_R[2] + 12;
    f.dot_d[2] = DOT_D + 8;
    f.dot_deg[0] = 150 + osc(t, 1800, 8);
    f.dot_deg[1] = 205 + osc(t + 400, 1800, 8);
    return f;
}

static frame_t frame_shrug(uint32_t t)
{
    frame_t f = frame_idle(t);
    const int32_t lift = -LV_ABS(osc(t, 1200, 12));
    f.eye_h = 6;
    f.eye_dx = osc(t, 1600, 5);
    f.eye_dy = 1;
    f.mouth_w = 8;
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
    f.happy = true;
    f.rainbow = true;
    f.ring_period_ms = 600;
    f.body_dy = -LV_ABS(osc(t, 520, 9));          // little hops
    for (int i = 0; i < DOT_COUNT; i++) {
        f.dot_deg[i] = spin(t, 800) + i * 120;
        f.dot_r[i] = ORBIT_R[i] + osc(t, 600, 10);
        f.dot_d[i] = DOT_D + 2 + osc(t + i * 100, 300, 3);
    }
    return f;
}

static void place(lv_obj_t *obj, int32_t cx, int32_t cy, int32_t w, int32_t h)
{
    lv_obj_set_size(obj, w, h);
    lv_obj_set_pos(obj, cx - w / 2, cy - h / 2);
}

static void paint(lv_color_t ink)
{
    lv_obj_set_style_bg_color(s_body, ink, 0);
    lv_obj_set_style_arc_color(s_chat_arc, ink, LV_PART_INDICATOR);
    for (int i = 0; i < RING_COUNT; i++) {
        lv_obj_set_style_border_color(s_rings[i], ink, 0);
    }
    for (int i = 0; i < DOT_COUNT; i++) {
        const bool amber = s_accents && i == DOT_COUNT - 1;
        lv_obj_set_style_bg_color(s_dots[i], amber ? lv_color_hex(AMBER_HEX) : ink, 0);
        // The two neutral dots sit back, as the grey ones do in the app.
        lv_obj_set_style_bg_opa(s_dots[i], (s_accents && !amber) ? LV_OPA_50 : LV_OPA_COVER, 0);
    }
}

// Eyes ease toward a fresh random spot every LOOK_EVERY_MS; the mouth eases
// toward its target width so the murmuring is soft rather than steppy.
static void ease_features(uint32_t now_ms, int32_t mouth_target)
{
    if (now_ms >= s_next_look_ms) {
        s_look_tx = lv_rand(0, 2 * LOOK_RANGE * 16) - LOOK_RANGE * 16;
        s_look_ty = lv_rand(0, 2 * LOOK_RANGE * 16) - LOOK_RANGE * 16;
        s_next_look_ms = now_ms + LOOK_EVERY_MS;
    }
    s_look_x16 += (s_look_tx - s_look_x16) / 4;
    s_look_y16 += (s_look_ty - s_look_y16) / 4;
    s_mouth_w16 += (mouth_target * 16 - s_mouth_w16) / 3;
}

static void set_hidden(lv_obj_t *obj, bool hidden)
{
    if (hidden) {
        lv_obj_add_flag(obj, LV_OBJ_FLAG_HIDDEN);
    } else {
        lv_obj_remove_flag(obj, LV_OBJ_FLAG_HIDDEN);
    }
}

static void render(const frame_t *f, uint32_t t, uint32_t now_ms)
{
    const float squish = squish_now(now_ms);
    const bool happy = f->happy || delighted_now(now_ms);

    // Squash toward the ground: the bottom edge stays put, like something soft.
    const int32_t body_w = (int32_t)(BODY * (1.0f + SQUISH_WIDEN * squish));
    const int32_t body_h = (int32_t)(BODY * (1.0f - SQUISH_FLATTEN * squish));
    const int32_t base_cy = CENTER + f->body_dy;
    const int32_t body_cy = base_cy + (BODY - body_h) / 2;

    for (int i = 0; i < RING_COUNT; i++) {
        const uint32_t phase = (t + i * f->ring_period_ms / RING_COUNT) % f->ring_period_ms;
        const int32_t pct = (int32_t)(phase * 100 / f->ring_period_ms);      // 0..99
        const int32_t d = BODY * (92 + 48 * pct / 100) / 100;                  // 0.92x -> 1.40x
        place(s_rings[i], CENTER, base_cy, d, d);
        lv_obj_set_style_radius(s_rings[i], d * CORNER_PCT / 100, 0);
        lv_obj_set_style_border_opa(s_rings[i], (lv_opa_t)(115 * (100 - pct) / 100), 0);
    }

    for (int i = 0; i < DOT_COUNT; i++) {
        const int32_t x = CENTER + cos_amp(f->dot_deg[i], f->dot_r[i]);
        const int32_t y = base_cy + sin_amp(f->dot_deg[i], f->dot_r[i]) + f->dot_dy[i];
        place(s_dots[i], x, y, f->dot_d[i], f->dot_d[i]);
    }

    place(s_body, CENTER, body_cy, body_w, body_h);
    lv_obj_set_style_radius(s_body, LV_MIN(body_w, body_h) * CORNER_PCT / 100, 0);

    for (int i = 0; i < 2; i++) {
        const int32_t side = (i == 0) ? -1 : 1;
        set_hidden(s_eyes[i], happy);
        set_hidden(s_happy_eyes[i], !happy);
        set_hidden(s_blush[i], !happy);
        lv_obj_set_size(s_eyes[i], EYE_W, f->eye_h);
        lv_obj_align(s_eyes[i], LV_ALIGN_CENTER, side * EYE_DX + f->eye_dx, EYE_Y + f->eye_dy);
    }
    set_hidden(s_mouth, happy);
    set_hidden(s_smile, !happy);
    lv_obj_set_size(s_mouth, s_mouth_w16 / 16, MOUTH_H);
    lv_obj_align(s_mouth, LV_ALIGN_CENTER, 0, MOUTH_Y);

    set_hidden(s_chat_arc, !f->arc_on);
    if (f->arc_on) {
        lv_arc_set_rotation(s_chat_arc, f->arc_rot);
    }
    if (f->rainbow) {
        paint(lv_color_hsv_to_rgb((uint16_t)((t / 6) % 360), 70, 100));
    }
}

static void on_tick(lv_timer_t *timer)
{
    (void)timer;
    const uint32_t now_ms = lv_tick_get();
    const uint32_t t = lv_tick_elaps(s_mood_start);
    frame_t f;
    switch (s_mood) {
    case MOOD_CHAT:      f = frame_chat(t);      break;
    case MOOD_WAVE:      f = frame_wave(t);      break;
    case MOOD_SHRUG:     f = frame_shrug(t);     break;
    case MOOD_CELEBRATE: f = frame_celebrate(t); break;
    default:             f = frame_idle(t);      break;
    }
    ease_features(now_ms, f.mouth_w);
    render(&f, t, now_ms);
}

static void on_poke(lv_event_t *event)
{
    (void)event;
    creature_poke();
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

// An open arc with round ends: the spinner, the closed happy eyes, the smile.
static lv_obj_t *make_arc(lv_obj_t *parent, int32_t size, int32_t width, int32_t start_deg, int32_t end_deg)
{
    lv_obj_t *arc = lv_arc_create(parent);
    lv_obj_remove_style(arc, NULL, LV_PART_KNOB);
    lv_obj_remove_flag(arc, LV_OBJ_FLAG_CLICKABLE);
    lv_obj_set_style_arc_opa(arc, LV_OPA_TRANSP, LV_PART_MAIN);
    lv_obj_set_style_arc_width(arc, width, LV_PART_INDICATOR);
    lv_obj_set_style_arc_rounded(arc, true, LV_PART_INDICATOR);
    lv_arc_set_bg_angles(arc, 0, 360);
    lv_arc_set_angles(arc, start_deg, end_deg);
    lv_obj_set_size(arc, size, size);
    lv_obj_add_flag(arc, LV_OBJ_FLAG_HIDDEN);
    return arc;
}

lv_obj_t *creature_create(lv_obj_t *parent)
{
    s_root = lv_obj_create(parent);
    lv_obj_remove_style_all(s_root);
    lv_obj_remove_flag(s_root, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_set_size(s_root, CREATURE_SIZE, CREATURE_SIZE);
    // The whole creature area is the poke target, not just the face.
    lv_obj_add_flag(s_root, LV_OBJ_FLAG_CLICKABLE);
    lv_obj_add_event_cb(s_root, on_poke, LV_EVENT_PRESSED, NULL);

    // Back to front: rings, dots (they pass behind the face's corners), face.
    for (int i = 0; i < RING_COUNT; i++) {
        s_rings[i] = make_shape(s_root, 2, false);
    }
    for (int i = 0; i < DOT_COUNT; i++) {
        s_dots[i] = make_shape(s_root, 0, true);
    }
    s_chat_arc = make_arc(s_root, 116, 5, 0, 80);
    lv_obj_center(s_chat_arc);
    s_body = make_shape(s_root, 0, true);

    for (int i = 0; i < 2; i++) {
        const int32_t side = (i == 0) ? -1 : 1;
        s_eyes[i] = make_shape(s_body, 0, true);
        s_happy_eyes[i] = make_arc(s_body, 16, 4, 180, 360);         // upward arc: closed, smiling
        lv_obj_align(s_happy_eyes[i], LV_ALIGN_CENTER, side * (EYE_DX + 3), EYE_Y + 5);
        s_blush[i] = make_shape(s_body, 0, true);
        lv_obj_set_size(s_blush[i], 10, 10);
        lv_obj_align(s_blush[i], LV_ALIGN_CENTER, side * 25, MOUTH_Y - 1);
        lv_obj_set_style_bg_color(s_blush[i], lv_color_hex(AMBER_HEX), 0);
        lv_obj_set_style_bg_opa(s_blush[i], LV_OPA_60, 0);
        lv_obj_add_flag(s_blush[i], LV_OBJ_FLAG_HIDDEN);
    }
    s_mouth = make_shape(s_body, 0, true);
    s_smile = make_arc(s_body, 26, 4, 25, 155);                      // lower arc: a smile
    lv_obj_align(s_smile, LV_ALIGN_CENTER, 0, MOUTH_Y - 9);

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
        lv_obj_set_style_arc_color(s_happy_eyes[i], eye, LV_PART_INDICATOR);
    }
    lv_obj_set_style_bg_color(s_mouth, eye, 0);
    lv_obj_set_style_arc_color(s_smile, eye, LV_PART_INDICATOR);
}

void creature_poke(void)
{
    s_poke_ms = lv_tick_get();
    s_poked = true;
}
