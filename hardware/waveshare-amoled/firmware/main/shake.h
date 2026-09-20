// Shake / bump detection on the board's QMI8658 IMU.
#pragma once
#include "esp_err.h"

// Starts a task that calls net_poll_request_shake() on a sharp acceleration
// spike (debounced). Call after board_display_start() (shares its I2C bus).
esp_err_t shake_start(void);
