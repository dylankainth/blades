// Minimal QMI8658 accelerometer driver (I2C, new i2c_master API). Only what
// shake detection needs: identify the chip, run the accelerometer, read XYZ in g.
// Register values cross-checked against Waveshare's own `waveshare/qmi8658`
// component and the QMI8658C datasheet.
#pragma once
#include "driver/i2c_master.h"
#include "esp_err.h"

// Probes 0x6B then 0x6A, checks WHO_AM_I, enables the accelerometer at
// +-8 g / 250 Hz (gyro stays off) and reads the enable bit back.
// ESP_ERR_NOT_FOUND if absent.
esp_err_t qmi8658_start(i2c_master_bus_handle_t bus);

// Latest acceleration sample, in g. Call qmi8658_start() first.
esp_err_t qmi8658_read_g(float *x, float *y, float *z);
