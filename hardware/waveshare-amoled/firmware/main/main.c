// Kindred badge for the Waveshare ESP32-S3-Touch-AMOLED-1.8. See config.h for
// everything tunable and board.h for everything board-specific.
#include "app_state.h"
#include "ble_adv.h"
#include "board.h"
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

    lv_display_t *display = board_display_start();   // AMOLED + touch + LVGL task (+ rotation)
    if (display == NULL) {
        ESP_LOGE(TAG, "display init failed");
        return;
    }
    board_display_lock(0);
    ui_init(box_id);
    board_display_unlock();
    // Battery powered and always on: one fixed brightness, no dimming, no sleep.
    const esp_err_t dim = board_display_brightness_set(DISPLAY_BRIGHTNESS_PCT);
    if (dim != ESP_OK) {
        ESP_LOGW(TAG, "brightness not set: %s (panel keeps its init level)", esp_err_to_name(dim));
    }

    wifi_multi_start();
    ble_adv_start();
    net_poll_start(box_id);
    demo_start();
    if (shake_start() != ESP_OK) {
        ESP_LOGW(TAG, "no IMU: hold the screen or long-press BOOT to send the shake event");
    }
}
