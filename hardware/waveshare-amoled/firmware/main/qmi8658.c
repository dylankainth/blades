#include "qmi8658.h"

#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#define ADDR_SA0_HIGH       0x6B      // what Waveshare's demos use on this board
#define ADDR_SA0_LOW        0x6A
#define I2C_SPEED_HZ        400000
#define I2C_TIMEOUT_MS      50

#define REG_WHO_AM_I        0x00
#define REG_CTRL1           0x02      // serial interface
#define REG_CTRL2           0x03      // accelerometer range + output data rate
#define REG_CTRL7           0x08      // sensor enable
#define REG_AX_L            0x35      // AX_L, AX_H, AY_L, AY_H, AZ_L, AZ_H

#define WHO_AM_I_VALUE      0x05
#define CTRL7_ALL_OFF       0x00
#define SETTLE_MS           20
#define CTRL1_ADDR_AUTO_INC 0x60      // address auto-increment (value used by Waveshare + SensorLib)
#define CTRL2_8G_250HZ      0x25      // aFS = 010 (+-8 g), aODR = 0101 (250 Hz)
#define CTRL7_ACCEL_ONLY    0x01
#define LSB_PER_G_8G        4096.0f

static const char *TAG = "qmi8658";
static i2c_master_dev_handle_t s_dev;

static esp_err_t write_reg(uint8_t reg, uint8_t value)
{
    const uint8_t frame[] = { reg, value };
    return i2c_master_transmit(s_dev, frame, sizeof(frame), I2C_TIMEOUT_MS);
}

static esp_err_t read_regs(uint8_t reg, uint8_t *out, size_t len)
{
    return i2c_master_transmit_receive(s_dev, &reg, 1, out, len, I2C_TIMEOUT_MS);
}

static esp_err_t attach(i2c_master_bus_handle_t bus)
{
    const uint8_t candidates[] = { ADDR_SA0_HIGH, ADDR_SA0_LOW };
    for (size_t i = 0; i < sizeof(candidates); i++) {
        if (i2c_master_probe(bus, candidates[i], I2C_TIMEOUT_MS) != ESP_OK) {
            continue;
        }
        const i2c_device_config_t cfg = {
            .dev_addr_length = I2C_ADDR_BIT_LEN_7,
            .device_address = candidates[i],
            .scl_speed_hz = I2C_SPEED_HZ,
        };
        ESP_LOGI(TAG, "found at 0x%02x", candidates[i]);
        return i2c_master_bus_add_device(bus, &cfg, &s_dev);
    }
    return ESP_ERR_NOT_FOUND;
}

static esp_err_t configure(void)
{
    uint8_t who = 0;
    esp_err_t err = read_regs(REG_WHO_AM_I, &who, 1);
    if (err != ESP_OK) {
        return err;
    }
    if (who != WHO_AM_I_VALUE) {
        ESP_LOGE(TAG, "WHO_AM_I = 0x%02x, expected 0x%02x", who, WHO_AM_I_VALUE);
        return ESP_ERR_NOT_FOUND;
    }
    // No soft reset, like Waveshare's own driver: the reset takes an unknown
    // time and silently discards register writes that arrive too early. Sensors
    // off first so a warm reboot reconfigures a stopped part.
    err = write_reg(REG_CTRL7, CTRL7_ALL_OFF);
    if (err == ESP_OK) {
        err = write_reg(REG_CTRL1, CTRL1_ADDR_AUTO_INC);
    }
    if (err == ESP_OK) {
        err = write_reg(REG_CTRL2, CTRL2_8G_250HZ);
    }
    if (err == ESP_OK) {
        err = write_reg(REG_CTRL7, CTRL7_ACCEL_ONLY);
    }
    if (err != ESP_OK) {
        return err;
    }
    vTaskDelay(pdMS_TO_TICKS(SETTLE_MS));
    uint8_t enabled = 0;
    err = read_regs(REG_CTRL7, &enabled, 1);
    if (err == ESP_OK && (enabled & CTRL7_ACCEL_ONLY) == 0) {
        ESP_LOGE(TAG, "accelerometer did not enable (CTRL7 = 0x%02x)", enabled);
        return ESP_ERR_INVALID_RESPONSE;
    }
    return err;
}

esp_err_t qmi8658_start(i2c_master_bus_handle_t bus)
{
    if (bus == NULL) {
        return ESP_ERR_INVALID_ARG;
    }
    esp_err_t err = attach(bus);
    if (err == ESP_OK) {
        err = configure();
    }
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "start failed: %s", esp_err_to_name(err));
        if (s_dev != NULL) {
            i2c_master_bus_rm_device(s_dev);
            s_dev = NULL;
        }
    }
    return err;
}

esp_err_t qmi8658_read_g(float *x, float *y, float *z)
{
    if (s_dev == NULL) {
        return ESP_ERR_INVALID_STATE;
    }
    uint8_t raw[6];
    const esp_err_t err = read_regs(REG_AX_L, raw, sizeof(raw));
    if (err != ESP_OK) {
        return err;
    }
    *x = (int16_t)((raw[1] << 8) | raw[0]) / LSB_PER_G_8G;
    *y = (int16_t)((raw[3] << 8) | raw[2]) / LSB_PER_G_8G;
    *z = (int16_t)((raw[5] << 8) | raw[4]) / LSB_PER_G_8G;
    return ESP_OK;
}
