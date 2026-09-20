#include "demo.h"

#include <stdatomic.h>
#include <string.h>

#include "app_state.h"
#include "board.h"
#include "config.h"
#include "creature.h"
#include "esp_log.h"
#include "iot_button.h"
#include "net_poll.h"
#include "ui.h"

#define DEMO_OFF (-1)

static const char *TAG = "demo";
static atomic_int s_step = DEMO_OFF;

static badge_state_t demo_state(badge_state_id_t id)
{
    badge_state_t st = { .state = id, .color_rgb = 0xff5a36 };
    strlcpy(st.owner_name, "Keanu", sizeof(st.owner_name));
    strlcpy(st.other_name, "Ada", sizeof(st.other_name));
    return st;
}

static void on_demo_button(void *button, void *user)
{
    const int next = atomic_load(&s_step) + 1;
    const bool leaving = (next >= STATE_COUNT);
    atomic_store(&s_step, leaving ? DEMO_OFF : next);
    ESP_LOGI(TAG, "%s", leaving ? "demo off, backend owns the screen"
                                : badge_state_name((badge_state_id_t)next));
    if (!board_display_lock(0)) {
        return;
    }
    if (leaving) {
        ui_invalidate();   // next poll repaints whatever the backend says
    } else {
        const badge_state_t st = demo_state((badge_state_id_t)next);
        ui_apply_state(&st);
    }
    board_display_unlock();
}

static void on_shake_button(void *button, void *user)
{
    ESP_LOGI(TAG, "manual shake (BOOT long press)");
    net_poll_request_shake();
}

// Runs inside the LVGL task, so the LVGL lock is already held.
static void on_screen_held(lv_event_t *event)
{
    (void)event;
    ESP_LOGI(TAG, "manual shake (screen held)");
    creature_poke();   // visible acknowledgement while the request is in flight
    net_poll_request_shake();
}

static void start_touch_fallback(void)
{
    lv_indev_t *touch = board_touch_indev();
    if (touch == NULL || !board_display_lock(0)) {
        ESP_LOGW(TAG, "no touch: only BOOT long press sends the shake event");
        return;
    }
    // On the input device, not on a widget: it fires wherever the finger is.
    lv_indev_set_long_press_time(touch, TOUCH_SHAKE_HOLD_MS);
    lv_indev_add_event_cb(touch, on_screen_held, LV_EVENT_LONG_PRESSED, NULL);
    board_display_unlock();
}

void demo_start(void)
{
    start_touch_fallback();
    button_handle_t boot = NULL;
    if (board_boot_button_create(&boot) != ESP_OK || boot == NULL) {
        ESP_LOGW(TAG, "BOOT button failed to init: no demo mode");
        return;
    }
    iot_button_register_cb(boot, BUTTON_SINGLE_CLICK, NULL, on_demo_button, NULL);
    iot_button_register_cb(boot, BUTTON_LONG_PRESS_START, NULL, on_shake_button, NULL);
}

bool demo_is_active(void)
{
    return atomic_load(&s_step) != DEMO_OFF;
}
