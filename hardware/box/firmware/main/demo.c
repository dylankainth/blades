#include "demo.h"

#include <stdatomic.h>
#include <string.h>

#include "app_state.h"
#include "bsp/esp-bsp.h"
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
    if (!bsp_display_lock(0)) {
        return;
    }
    if (leaving) {
        ui_invalidate();   // next poll repaints whatever the backend says
    } else {
        const badge_state_t st = demo_state((badge_state_id_t)next);
        ui_apply_state(&st);
    }
    bsp_display_unlock();
}

static void on_shake_button(void *button, void *user)
{
    ESP_LOGI(TAG, "manual shake");
    net_poll_request_shake();
}

void demo_start(void)
{
    button_handle_t buttons[BSP_BUTTON_NUM] = { 0 };
    int count = 0;
    if (bsp_iot_button_create(buttons, &count, BSP_BUTTON_NUM) != ESP_OK) {
        ESP_LOGW(TAG, "some buttons failed to init");
    }
    for (int i = 0; i < count; i++) {
        if (buttons[i] == NULL) {
            continue;
        }
        const button_cb_t cb = (i == BSP_BUTTON_CONFIG) ? on_demo_button : on_shake_button;
        iot_button_register_cb(buttons[i], BUTTON_PRESS_DOWN, NULL, cb, NULL);
    }
}

bool demo_is_active(void)
{
    return atomic_load(&s_step) != DEMO_OFF;
}
