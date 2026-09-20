#include "net_poll.h"

#include <stdatomic.h>
#include <stdio.h>
#include <string.h>

#include "app_state.h"
#include "ble_adv.h"
#include "bsp/esp-bsp.h"
#include "config.h"
#include "demo.h"
#include "esp_crt_bundle.h"
#include "esp_http_client.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "ui.h"
#include "wifi_multi.h"

#define BODY_MAX   1024
#define TASK_STACK 10240

static const char *TAG = "net";
static char s_state_url[256];
static char s_box_id[7];
static char s_body[BODY_MAX];
static size_t s_body_len;
static TaskHandle_t s_task;
static atomic_bool s_shake_pending;
static badge_state_t s_current = { .state = STATE_UNPAIRED };

static esp_err_t on_http_event(esp_http_client_event_t *evt)
{
    if (evt->event_id == HTTP_EVENT_ON_DATA) {
        const size_t room = BODY_MAX - 1 - s_body_len;
        const size_t take = ((size_t)evt->data_len < room) ? (size_t)evt->data_len : room;
        memcpy(&s_body[s_body_len], evt->data, take);
        s_body_len += take;
    }
    return ESP_OK;
}

// One client for the task's lifetime: keep-alive avoids a TLS handshake per poll.
static esp_http_client_handle_t make_client(void)
{
    const esp_http_client_config_t cfg = {
        .url = s_state_url,
        .event_handler = on_http_event,
        .timeout_ms = HTTP_TIMEOUT_MS,
        .crt_bundle_attach = esp_crt_bundle_attach,   // ignored for http:// URLs
        .keep_alive_enable = true,
    };
    return esp_http_client_init(&cfg);
}

// Returns the HTTP status, or -1 on transport failure. Body lands in s_body.
static int perform(esp_http_client_handle_t client)
{
    s_body_len = 0;
    const esp_err_t err = esp_http_client_perform(client);
    s_body[s_body_len] = '\0';
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "request failed: %s", esp_err_to_name(err));
        return -1;
    }
    return esp_http_client_get_status_code(client);
}

static void apply_state(const badge_state_t *st)
{
    if (st->state != s_current.state) {
        ESP_LOGI(TAG, "state %s -> %s", badge_state_name(s_current.state), badge_state_name(st->state));
    }
    s_current = *st;
    ble_adv_set_token(st->has_token ? st->token : NULL);
    if (demo_is_active()) {
        return;   // a button-driven demo owns the screen
    }
    if (bsp_display_lock(0)) {
        ui_apply_state(st);
        bsp_display_unlock();
    }
}

static bool poll_state(esp_http_client_handle_t client)
{
    const int status = perform(client);
    if (status < 0) {
        return false;
    }
    badge_state_t parsed;
    if (status == 200 && badge_state_parse(s_body, &parsed)) {
        apply_state(&parsed);
    } else {
        ESP_LOGW(TAG, "bad response: HTTP %d, body \"%.80s\"", status, s_body);
    }
    return true;
}

static bool post_shake(esp_http_client_handle_t client)
{
    char body[64];
    snprintf(body, sizeof(body), "{\"boxId\":\"%s\",\"event\":\"shake\"}", s_box_id);
    esp_http_client_set_url(client, EVENTS_URL);
    esp_http_client_set_method(client, HTTP_METHOD_POST);
    esp_http_client_set_header(client, "Content-Type", "application/json");
    esp_http_client_set_post_field(client, body, (int)strlen(body));
    const int status = perform(client);
    ESP_LOGI(TAG, "shake event -> HTTP %d", status);

    // Put the shared client back into polling shape.
    esp_http_client_set_url(client, s_state_url);
    esp_http_client_set_method(client, HTTP_METHOD_GET);
    esp_http_client_set_post_field(client, NULL, 0);
    esp_http_client_delete_header(client, "Content-Type");
    return status >= 0;
}

static void drop_client(esp_http_client_handle_t *client)
{
    if (*client != NULL) {
        esp_http_client_cleanup(*client);
        *client = NULL;
    }
}

static void poll_task(void *arg)
{
    esp_http_client_handle_t client = NULL;
    for (;;) {
        ulTaskNotifyTake(pdTRUE, pdMS_TO_TICKS(POLL_INTERVAL_MS));   // shakes wake us early
        if (!wifi_multi_is_connected()) {
            drop_client(&client);
            continue;
        }
        if (client == NULL && (client = make_client()) == NULL) {
            ESP_LOGE(TAG, "could not create HTTP client");
            continue;
        }
        bool ok = true;
        if (atomic_exchange(&s_shake_pending, false)) {
            if (s_current.state == STATE_MATCH) {
                ok = post_shake(client);
            } else {
                ESP_LOGI(TAG, "shake ignored (state is %s)", badge_state_name(s_current.state));
            }
        }
        ok = ok && poll_state(client);
        if (!ok) {
            drop_client(&client);   // start from a clean connection next time
        }
    }
}

void net_poll_start(const char *box_id)
{
    strlcpy(s_box_id, box_id, sizeof(s_box_id));
    snprintf(s_state_url, sizeof(s_state_url), "%s?boxId=%s", STATE_URL, box_id);
    ESP_LOGI(TAG, "polling %s", s_state_url);
    xTaskCreate(poll_task, "net_poll", TASK_STACK, NULL, 4, &s_task);
}

void net_poll_request_shake(void)
{
    atomic_store(&s_shake_pending, true);
    if (s_task != NULL) {
        xTaskNotifyGive(s_task);
    }
}
