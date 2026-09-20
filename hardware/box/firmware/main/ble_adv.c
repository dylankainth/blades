#include "ble_adv.h"

#include <string.h>

#include "app_state.h"
#include "config.h"
#include "esp_log.h"
#include "host/ble_hs.h"
#include "host/util/util.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"

#define AD_TYPE_FLAGS            0x01
#define AD_TYPE_SERVICE_DATA_128 0x21
#define UUID128_LEN              16
#define LEGACY_ADV_MAX           31
#define ADV_ITVL_UNITS(ms)       ((ms) * 1000 / 625)

static const char *TAG = "ble_adv";
static const uint8_t SERVICE_UUID_BE[UUID128_LEN] = { BLE_SERVICE_UUID_BYTES };

static SemaphoreHandle_t s_lock;
static bool s_synced;
static bool s_has_token;
static uint8_t s_token[BLE_TOKEN_LEN];
static uint8_t s_own_addr_type;

// Payload: [Flags AD (optional, 3 bytes)] + [len][0x21][UUID little-endian][token] = 26 bytes.
static size_t build_payload(uint8_t out[LEGACY_ADV_MAX])
{
    size_t n = 0;
#if BLE_ADV_INCLUDE_FLAGS
    out[n++] = 2;
    out[n++] = AD_TYPE_FLAGS;
    out[n++] = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
#endif
    out[n++] = 1 + UUID128_LEN + BLE_TOKEN_LEN;
    out[n++] = AD_TYPE_SERVICE_DATA_128;
    for (int i = 0; i < UUID128_LEN; i++) {
        out[n++] = SERVICE_UUID_BE[UUID128_LEN - 1 - i];
    }
    memcpy(&out[n], s_token, BLE_TOKEN_LEN);
    return n + BLE_TOKEN_LEN;
}

// Caller holds s_lock.
static void apply_locked(void)
{
    if (!s_synced) {
        return;
    }
    if (ble_gap_adv_active()) {
        ble_gap_adv_stop();
    }
    if (!s_has_token) {
        ESP_LOGI(TAG, "no token, advertising stopped");
        return;
    }
    uint8_t payload[LEGACY_ADV_MAX];
    const size_t len = build_payload(payload);
    int rc = ble_gap_adv_set_data(payload, (int)len);
    if (rc != 0) {
        ESP_LOGE(TAG, "adv_set_data failed rc=%d", rc);
        return;
    }
    struct ble_gap_adv_params params = {
        .conn_mode = BLE_GAP_CONN_MODE_NON,
        .disc_mode = BLE_ADV_INCLUDE_FLAGS ? BLE_GAP_DISC_MODE_GEN : BLE_GAP_DISC_MODE_NON,
        .itvl_min = ADV_ITVL_UNITS(BLE_ADV_INTERVAL_MS),
        .itvl_max = ADV_ITVL_UNITS(BLE_ADV_INTERVAL_MS + 20),
    };
    rc = ble_gap_adv_start(s_own_addr_type, NULL, BLE_HS_FOREVER, &params, NULL, NULL);
    if (rc != 0) {
        ESP_LOGE(TAG, "adv_start failed rc=%d", rc);
        return;
    }
    ESP_LOGI(TAG, "advertising %u bytes", (unsigned)len);
    ESP_LOG_BUFFER_HEX(TAG, payload, len);
}

static void on_sync(void)
{
    int rc = ble_hs_util_ensure_addr(0);
    if (rc == 0) {
        rc = ble_hs_id_infer_auto(0, &s_own_addr_type);
    }
    if (rc != 0) {
        ESP_LOGE(TAG, "no usable BLE address rc=%d", rc);
        return;
    }
    xSemaphoreTake(s_lock, portMAX_DELAY);
    s_synced = true;
    apply_locked();
    xSemaphoreGive(s_lock);
}

static void on_reset(int reason)
{
    ESP_LOGW(TAG, "host reset, reason=%d", reason);
    s_synced = false;
}

static void host_task(void *arg)
{
    nimble_port_run();
    nimble_port_freertos_deinit();
}

void ble_adv_start(void)
{
    s_lock = xSemaphoreCreateMutex();
    const esp_err_t err = nimble_port_init();
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "nimble_port_init failed: %s", esp_err_to_name(err));
        return;
    }
    ble_hs_cfg.sync_cb = on_sync;
    ble_hs_cfg.reset_cb = on_reset;
    nimble_port_freertos_init(host_task);
}

void ble_adv_set_token(const uint8_t *token)
{
    if (s_lock == NULL) {
        return;
    }
    xSemaphoreTake(s_lock, portMAX_DELAY);
    const bool has_token = (token != NULL);
    const bool changed = (has_token != s_has_token) ||
                         (has_token && memcmp(token, s_token, BLE_TOKEN_LEN) != 0);
    if (changed) {
        s_has_token = has_token;
        if (has_token) {
            memcpy(s_token, token, BLE_TOKEN_LEN);
        }
        apply_locked();
    }
    xSemaphoreGive(s_lock);
}
