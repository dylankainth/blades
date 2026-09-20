#include "shake.h"

#include <math.h>

#include "bsp/esp-bsp.h"
#include "config.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "icm42670.h"
#include "net_poll.h"

#define SAMPLE_PERIOD_MS 10

static const char *TAG = "shake";
static icm42670_handle_t s_imu;

static void shake_task(void *arg)
{
    TickType_t last_fire = 0;
    for (;;) {
        vTaskDelay(pdMS_TO_TICKS(SAMPLE_PERIOD_MS));
        icm42670_value_t a;
        if (icm42670_get_acce_value(s_imu, &a) != ESP_OK) {
            continue;
        }
        const float magnitude_g = sqrtf(a.x * a.x + a.y * a.y + a.z * a.z);
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
    esp_err_t err = bsp_i2c_init();   // no-op if the display already did it
    if (err == ESP_OK) {
        err = icm42670_create(bsp_i2c_get_handle(), ICM42670_I2C_ADDRESS, &s_imu);
    }
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "IMU not found: %s", esp_err_to_name(err));
        return err;
    }
    const icm42670_cfg_t cfg = {
        .acce_fs = ACCE_FS_8G,
        .acce_odr = ACCE_ODR_200HZ,
        .gyro_fs = GYRO_FS_2000DPS,
        .gyro_odr = GYRO_ODR_200HZ,
    };
    err = icm42670_config(s_imu, &cfg);
    if (err == ESP_OK) {
        err = icm42670_acce_set_pwr(s_imu, ACCE_PWR_LOWNOISE);
    }
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "IMU config failed: %s", esp_err_to_name(err));
        return err;
    }
    xTaskCreate(shake_task, "shake", 4096, NULL, 3, NULL);
    return ESP_OK;
}
