// Wi-Fi station that walks an ordered list of networks (secrets.h) forever.
#pragma once
#include <stdbool.h>
#include <stddef.h>

// Starts Wi-Fi and the background connect/retry task. Needs nvs_flash_init() first.
void wifi_multi_start(void);

bool wifi_multi_is_connected(void);

// Copies the connected SSID into out ("" when offline). Safe from any task.
void wifi_multi_get_ssid(char *out, size_t out_len);
