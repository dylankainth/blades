#include "shake.h"

#include <math.h>

#include "board.h"
#include "config.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "net_poll.h"

#define SAMPLE_PERIOD_MS 10

static const char *TAG = "shake";

static void shake_task(void *arg)
{
    TickType_t last_fire = 0;
    for (;;) {
        vTaskDelay(pdMS_TO_TICKS(SAMPLE_PERIOD_MS));
        float x, y, z;
        if (board_imu_read_g(&x, &y, &z) != ESP_OK) {
            continue;
        }
        const float magnitude_g = sqrtf(x * x + y * y + z * z);
        const bool spike = fabsf(magnitude_g - 1.0f) > SHAKE_THRESHOLD_G;
        const TickType_t now = xTaskGetTickCount();
        if (spike && (now - last_fire) > pdMS_TO_TICKS(SHAKE_DEBOUNCE_MS)) {
            last_fire = now;
            ESP_LOGI(TAG, "shake! |a| = %.2f g", magnitude_g);
            net_poll_request_shake();
        }
    }
}

esp_err_t shake_start(void)
{
    const esp_err_t err = board_imu_init();   // reuses the display's I2C bus
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "IMU not available: %s", esp_err_to_name(err));
        return err;
    }
    xTaskCreate(shake_task, "shake", 4096, NULL, 3, NULL);
    return ESP_OK;
}
