// Kindred badge for the ESP32-S3-BOX-3. See config.h for everything tunable.
#include "app_state.h"
#include "ble_adv.h"
#include "bsp/esp-bsp.h"
#include "config.h"
#include "demo.h"
#include "esp_log.h"
#include "net_poll.h"
#include "nvs_flash.h"
#include "shake.h"
#include "ui.h"
#include "wifi_multi.h"

static const char *TAG = "main";

static void init_nvs(void)
{
    esp_err_t err = nvs_flash_init();
    if (err == ESP_ERR_NVS_NO_FREE_PAGES || err == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        err = nvs_flash_init();
    }
    ESP_ERROR_CHECK(err);   // Wi-Fi and BLE both need NVS
}

void app_main(void)
{
    char box_id[7];
    init_nvs();
    box_id_read(box_id);
    ESP_LOGI(TAG, "Kindred badge, boxId=%s", box_id);

    lv_display_t *display = bsp_display_start();   // LCD + touch + LVGL task
    if (display == NULL) {
        ESP_LOGE(TAG, "display init failed");
        return;
    }
#if DISPLAY_ROTATE_180
    bsp_display_rotate(display, LV_DISPLAY_ROTATION_180);
#endif
    bsp_display_lock(0);
    ui_init(box_id);
    bsp_display_unlock();
    bsp_display_backlight_on();

    wifi_multi_start();
    ble_adv_start();
    net_poll_start(box_id);
    demo_start();
    if (shake_start() != ESP_OK) {
        ESP_LOGW(TAG, "no IMU: use the MUTE button to send the shake event");
    }
}
