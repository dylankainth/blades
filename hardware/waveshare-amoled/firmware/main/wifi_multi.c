#include "wifi_multi.h"

#include <string.h>

#include "config.h"
#include "esp_event.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_wifi.h"
#include "freertos/FreeRTOS.h"
#include "freertos/event_groups.h"
#include "freertos/task.h"

#define BIT_GOT_IP        BIT0
#define BIT_DISCONNECTED  BIT1
#define CONNECT_TIMEOUT_MS 12000
#define BACKOFF_START_MS   1000
#define BACKOFF_MAX_MS     15000

typedef struct {
    const char *ssid;
    const char *password;   // "" = open network
} wifi_cred_t;

static const char *TAG = "wifi";
static const wifi_cred_t NETWORKS[] = { WIFI_NETWORKS };
static const size_t NETWORK_COUNT = sizeof(NETWORKS) / sizeof(NETWORKS[0]);

static EventGroupHandle_t s_events;
static portMUX_TYPE s_ssid_lock = portMUX_INITIALIZER_UNLOCKED;
static char s_ssid[33];
static volatile bool s_connected;

static void set_connected_ssid(const char *ssid)
{
    portENTER_CRITICAL(&s_ssid_lock);
    strlcpy(s_ssid, ssid, sizeof(s_ssid));
    portEXIT_CRITICAL(&s_ssid_lock);
    s_connected = (ssid[0] != '\0');
}

static void on_event(void *arg, esp_event_base_t base, int32_t id, void *data)
{
    if (base == WIFI_EVENT && id == WIFI_EVENT_STA_DISCONNECTED) {
        const wifi_event_sta_disconnected_t *info = data;
        ESP_LOGW(TAG, "disconnected (reason %d)", info->reason);
        set_connected_ssid("");
        xEventGroupSetBits(s_events, BIT_DISCONNECTED);
    } else if (base == IP_EVENT && id == IP_EVENT_STA_GOT_IP) {
        xEventGroupSetBits(s_events, BIT_GOT_IP);
    }
}

static bool try_network(const wifi_cred_t *cred)
{
    wifi_config_t cfg = { 0 };
    strlcpy((char *)cfg.sta.ssid, cred->ssid, sizeof(cfg.sta.ssid));
    strlcpy((char *)cfg.sta.password, cred->password, sizeof(cfg.sta.password));
    const bool is_open = (cred->password[0] == '\0');
    cfg.sta.threshold.authmode = is_open ? WIFI_AUTH_OPEN : WIFI_AUTH_WPA2_PSK;
    // Venues have many APs per SSID: scan all channels and pick the strongest.
    cfg.sta.scan_method = WIFI_ALL_CHANNEL_SCAN;
    cfg.sta.sort_method = WIFI_CONNECT_AP_BY_SIGNAL;

    ESP_LOGI(TAG, "trying \"%s\" (%s)", cred->ssid, is_open ? "open" : "psk");
    xEventGroupClearBits(s_events, BIT_GOT_IP | BIT_DISCONNECTED);
    if (esp_wifi_set_config(WIFI_IF_STA, &cfg) != ESP_OK || esp_wifi_connect() != ESP_OK) {
        ESP_LOGE(TAG, "could not start connecting to \"%s\"", cred->ssid);
        return false;
    }
    const EventBits_t bits = xEventGroupWaitBits(s_events, BIT_GOT_IP | BIT_DISCONNECTED,
                                                 pdFALSE, pdFALSE,
                                                 pdMS_TO_TICKS(CONNECT_TIMEOUT_MS));
    if (bits & BIT_GOT_IP) {
        return true;
    }
    esp_wifi_disconnect();            // stop any association still in flight
    vTaskDelay(pdMS_TO_TICKS(300));   // let its DISCONNECTED event drain first
    return false;
}

static void wifi_task(void *arg)
{
    size_t index = 0;
    uint32_t backoff_ms = BACKOFF_START_MS;
    for (;;) {
        if (try_network(&NETWORKS[index])) {
            ESP_LOGI(TAG, "connected to \"%s\"", NETWORKS[index].ssid);
            set_connected_ssid(NETWORKS[index].ssid);
            backoff_ms = BACKOFF_START_MS;
            xEventGroupClearBits(s_events, BIT_DISCONNECTED);
            xEventGroupWaitBits(s_events, BIT_DISCONNECTED, pdTRUE, pdFALSE, portMAX_DELAY);
            continue;   // link dropped: retry the same network first
        }
        index = (index + 1) % NETWORK_COUNT;
        if (index == 0) {   // whole list failed: back off before the next sweep
            ESP_LOGW(TAG, "no network reachable, retrying in %u ms", (unsigned)backoff_ms);
            vTaskDelay(pdMS_TO_TICKS(backoff_ms));
            backoff_ms = (backoff_ms * 2 > BACKOFF_MAX_MS) ? BACKOFF_MAX_MS : backoff_ms * 2;
        }
    }
}

void wifi_multi_start(void)
{
    s_events = xEventGroupCreate();
    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    esp_netif_create_default_wifi_sta();

    const wifi_init_config_t init_cfg = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&init_cfg));
    ESP_ERROR_CHECK(esp_event_handler_register(WIFI_EVENT, ESP_EVENT_ANY_ID, on_event, NULL));
    ESP_ERROR_CHECK(esp_event_handler_register(IP_EVENT, IP_EVENT_STA_GOT_IP, on_event, NULL));
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
    // NOTE: leave power save at the default (WIFI_PS_MIN_MODEM). Wi-Fi + BLE
    // coexistence requires modem sleep; WIFI_PS_NONE aborts at runtime.
    ESP_ERROR_CHECK(esp_wifi_start());

    if (NETWORK_COUNT == 0) {
        ESP_LOGE(TAG, "WIFI_NETWORKS is empty, staying offline");
        return;
    }
    xTaskCreate(wifi_task, "wifi_multi", 4096, NULL, 5, NULL);
}

bool wifi_multi_is_connected(void)
{
    return s_connected;
}

void wifi_multi_get_ssid(char *out, size_t out_len)
{
    portENTER_CRITICAL(&s_ssid_lock);
    strlcpy(out, s_ssid, out_len);
    portEXIT_CRITICAL(&s_ssid_lock);
}
