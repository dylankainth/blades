// Non-connectable BLE legacy advertising of an 8-byte token as 128-bit Service Data.
#pragma once
#include <stdbool.h>
#include <stdint.h>

// Boots the NimBLE host. Nothing is advertised until a token is set.
void ble_adv_start(void);

// token = 8 bytes to advertise, or NULL to stop advertising. Safe from any task.
void ble_adv_set_token(const uint8_t *token);
