idf_component_register(
    SRCS "main.c" "app_state.c" "wifi_multi.c" "net_poll.c" "ble_adv.c"
         "creature.c" "ui.c" "shake.c" "demo.c" "board.c" "qmi8658.c"
    INCLUDE_DIRS "."
    PRIV_REQUIRES nvs_flash esp_wifi esp_netif esp_event esp_http_client
                  mbedtls json bt esp_lcd esp_driver_i2c esp_driver_spi
                  esp_driver_gpio
)
