// Shake / bump detection on the BOX-3's ICM-42670 IMU.
#pragma once
#include "esp_err.h"

// Starts a task that calls net_poll_request_shake() on a sharp acceleration
// spike (debounced). Call after bsp_display_start() (shares its I2C bus).
esp_err_t shake_start(void);
