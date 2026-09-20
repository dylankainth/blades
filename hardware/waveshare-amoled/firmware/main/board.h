// Everything specific to the Waveshare ESP32-S3-Touch-AMOLED-1.8 lives behind
// this header (the BOX-3 build used the esp-box-3 BSP for the same jobs):
// AMOLED panel + touch + LVGL task, brightness, IMU, BOOT button, battery gauge.
//
// Pin map (all from Waveshare's official sources, see board.c for the URLs):
//   QSPI AMOLED   CS 12, SCLK 11, D0..D3 = 4, 5, 6, 7        (SPI2, 40 MHz)
//   I2C bus       SDA 15, SCL 14   touch 0x38/0x15, IMU 0x6B, TCA9554 0x20,
//                                  AXP2101 0x34 (also RTC 0x51, ES8311 0x18)
//   TCA9554       EXIO0 panel reset, EXIO1 panel power enable, EXIO2 touch reset
//   Touch INT     GPIO 21 (unused here: the touch controller is polled)
//   BOOT button   GPIO 0, active low
#pragma once
#include <stdbool.h>
#include <stdint.h>

#include "esp_err.h"
#include "iot_button.h"
#include "lvgl.h"

#define BOARD_LCD_H_RES 368
#define BOARD_LCD_V_RES 448

// Powers up and resets the panel through the TCA9554, detects the board
// revision (V1 SH8601 + FT3168, V2 CO5300 + CST820), starts the panel, the
// LVGL task and the touch input. A missing touch controller is not fatal.
// Returns NULL only when the display itself could not be started.
lv_display_t *board_display_start(void);

// LVGL is not thread safe: hold this lock around every lv_... call made
// outside LVGL callbacks. timeout_ms = 0 blocks until the lock is taken.
bool board_display_lock(uint32_t timeout_ms);
void board_display_unlock(void);

// AMOLED: no backlight pin, brightness is a panel register. 0..100.
esp_err_t board_display_brightness_set(int percent);

// Touch input device, or NULL when no touch controller answered.
lv_indev_t *board_touch_indev(void);

// QMI8658 accelerometer, shares the I2C bus with the touch controller.
esp_err_t board_imu_init(void);
esp_err_t board_imu_read_g(float *x, float *y, float *z);

// BOOT button (GPIO0). The PWR button belongs to the AXP2101 and is left alone.
esp_err_t board_boot_button_create(button_handle_t *out_button);

// Battery gauge, read-only from the AXP2101 (nothing is ever written to the
// PMU, so rails, charging and the power key keep their power-on defaults).
// Returns 0..100, or -1 when no battery is attached or the PMU did not answer.
int board_battery_percent(bool *out_charging);
