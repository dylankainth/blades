// Board bring-up for the Waveshare ESP32-S3-Touch-AMOLED-1.8.
//
// Official sources for the pin map, the TCA9554 sequence and the init tables:
//   V1 ESP-IDF demo (SH8601 + FT3168), pins, expander pulse, SH8601 init table:
//     https://github.com/waveshareteam/ESP32-display-support/blob/master/AMOLED-Products/ESP32-S3-Touch-AMOLED-1.8/examples/ESP-IDF-v5.3.2/05_LVGL_WITH_RAM/main/example_qspi_with_ram.c
//   Current examples (both revisions): expander bit names, V2 detection by the
//   touch address, CO5300 init table, x gap 0x10 on V2, QMI8658, AXP2101:
//     https://github.com/waveshareteam/ESP32-S3-Touch-AMOLED-1.8/tree/main/examples/esp-idf
//   Registry BSP waveshare/esp32_s3_touch_amoled_1_8 v2.0.3 (same pins). Not
//   used: it adds this QSPI panel with lvgl_port_add_disp_rgb(), which writes
//   RGB-panel callbacks into a non-RGB panel struct, and never pulses EXIO0-2.
//   BOOT = GPIO0, PWR = EXIO4: https://www.waveshare.com/wiki/ESP32-S3-Touch-AMOLED-1.8 (FAQ)
//
// The AXP2101 is only ever READ. Its power-on defaults already feed the panel,
// touch and IMU (none of Waveshare's display demos configure it), and leaving
// it alone keeps battery operation, charging and the PWR key exactly as shipped.
#include "board.h"

#include "button_gpio.h"
#include "config.h"
#include "driver/i2c_master.h"
#include "driver/spi_master.h"
#include "esp_heap_caps.h"
#include "esp_lcd_panel_io.h"
#include "esp_lcd_panel_ops.h"
#include "esp_lcd_sh8601.h"
#include "esp_lcd_touch_cst816s.h"
#include "esp_lcd_touch_ft5x06.h"
#include "esp_log.h"
#include "esp_lvgl_port.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "qmi8658.h"

#define PIN_LCD_CS          12
#define PIN_LCD_SCLK        11
#define PIN_LCD_D0          4
#define PIN_LCD_D1          5
#define PIN_LCD_D2          6
#define PIN_LCD_D3          7
#define PIN_I2C_SDA         15
#define PIN_I2C_SCL         14
#define PIN_BOOT_BUTTON     0
#define LCD_SPI_HOST        SPI2_HOST
#define I2C_SPEED_HZ        400000
#define I2C_TIMEOUT_MS      50

#define EXPANDER_ADDR       0x20
#define EXPANDER_REG_OUTPUT 0x01
#define EXPANDER_REG_CONFIG 0x03      // bit = 1: input (power-on default), 0: output
#define EXIO_LCD_RESET      0x01      // EXIO0
#define EXIO_LCD_POWER      0x02      // EXIO1
#define EXIO_TOUCH_RESET    0x04      // EXIO2
#define EXIO_PANEL_MASK     (EXIO_LCD_RESET | EXIO_LCD_POWER | EXIO_TOUCH_RESET)
#define PANEL_OFF_MS        200       // as in Waveshare's V1 demo
#define PANEL_WAKE_MS       150       // as in Waveshare's current examples

#define PMU_ADDR            0x34
#define PMU_REG_STATUS1     0x00      // bit 3: battery present
#define PMU_REG_STATUS2     0x01      // bits 7..5 == 001: charging
#define PMU_REG_BAT_PERCENT 0xA4
#define PMU_BATTERY_PRESENT 0x08

#define QSPI_OPCODE_WRITE_CMD 0x02    // SH8601 / CO5300 QSPI framing: 0x02, 0x00, cmd, 0x00
#define LCD_CMD_BRIGHTNESS    0x51
#define V2_PANEL_X_GAP        0x10
#define DRAW_BUF_LINES        64      // 368 * 64 * 2 = 46 KB of internal DMA RAM
#define DRAW_BUF_PIXELS       (BOARD_LCD_H_RES * DRAW_BUF_LINES)
#define TOUCH_FAILS_TO_MUTE   3
#define VARIANT_PROBE_TRIES   3
#define VARIANT_PROBE_GAP_MS  20
#define BRIGHTNESS_REG(pct)   ((uint8_t)((pct) * 255 / 100))

typedef enum { BOARD_V1_SH8601_FT3168, BOARD_V2_CO5300_CST820, BOARD_TOUCH_MISSING } board_variant_t;

static const char *TAG = "board";
static i2c_master_bus_handle_t s_bus;
static i2c_master_dev_handle_t s_pmu;
static bool s_pmu_absent;
static esp_lcd_panel_io_handle_t s_lcd_io;
static esp_lcd_panel_handle_t s_panel;
static esp_lcd_touch_handle_t s_touch;
static lv_display_t *s_display;
static lv_indev_t *s_indev;
static int s_touch_failures;

// Waveshare's V1 table, unchanged except that the final brightness is ours.
static const sh8601_lcd_init_cmd_t INIT_V1_SH8601[] = {
    { 0x11, (uint8_t[]){ 0x00 }, 0, 120 },                        // sleep out
    { 0x44, (uint8_t[]){ 0x01, 0xD1 }, 2, 0 },                    // tear scanline
    { 0x35, (uint8_t[]){ 0x00 }, 1, 0 },                          // tearing effect on
    { 0x53, (uint8_t[]){ 0x20 }, 1, 10 },                         // brightness control on
    { 0x2A, (uint8_t[]){ 0x00, 0x00, 0x01, 0x6F }, 4, 0 },        // columns 0..367
    { 0x2B, (uint8_t[]){ 0x00, 0x00, 0x01, 0xBF }, 4, 0 },        // rows 0..447
    { 0x51, (uint8_t[]){ 0x00 }, 1, 10 },
    { 0x29, (uint8_t[]){ 0x00 }, 0, 10 },                         // display on
    { 0x51, (uint8_t[]){ BRIGHTNESS_REG(DISPLAY_BRIGHTNESS_PCT) }, 1, 0 },
};

// Waveshare's table for the V2 (CO5300) panel, same brightness change.
static const sh8601_lcd_init_cmd_t INIT_V2_CO5300[] = {
    { 0xFE, (uint8_t[]){ 0x00 }, 1, 0 },
    { 0xC4, (uint8_t[]){ 0x80 }, 1, 0 },
    { 0x3A, (uint8_t[]){ 0x55 }, 1, 0 },
    { 0x35, (uint8_t[]){ 0x00 }, 1, 0 },
    { 0x53, (uint8_t[]){ 0x20 }, 1, 0 },
    { 0x51, (uint8_t[]){ BRIGHTNESS_REG(DISPLAY_BRIGHTNESS_PCT) }, 1, 0 },
    { 0x63, (uint8_t[]){ 0xFF }, 1, 0 },
    { 0x2A, (uint8_t[]){ 0x00, 0x00, 0x01, 0x6F }, 4, 0 },
    { 0x2B, (uint8_t[]){ 0x00, 0x00, 0x01, 0xBF }, 4, 0 },
    { 0x11, (uint8_t[]){ 0x00 }, 0, 100 },
    { 0x29, (uint8_t[]){ 0x00 }, 0, 0 },
};

// ---------------------------------------------------------------- I2C helpers

static esp_err_t i2c_bus_init(void)
{
    if (s_bus != NULL) {
        return ESP_OK;
    }
    const i2c_master_bus_config_t cfg = {
        .i2c_port = I2C_NUM_0,
        .sda_io_num = PIN_I2C_SDA,
        .scl_io_num = PIN_I2C_SCL,
        .clk_source = I2C_CLK_SRC_DEFAULT,
        .glitch_ignore_cnt = 7,
        .flags.enable_internal_pullup = true,
    };
    return i2c_new_master_bus(&cfg, &s_bus);
}

static esp_err_t i2c_attach(uint8_t address, i2c_master_dev_handle_t *out_dev)
{
    const i2c_device_config_t cfg = {
        .dev_addr_length = I2C_ADDR_BIT_LEN_7,
        .device_address = address,
        .scl_speed_hz = I2C_SPEED_HZ,
    };
    return i2c_master_bus_add_device(s_bus, &cfg, out_dev);
}

static esp_err_t reg_read(i2c_master_dev_handle_t dev, uint8_t reg, uint8_t *out_value)
{
    return i2c_master_transmit_receive(dev, &reg, 1, out_value, 1, I2C_TIMEOUT_MS);
}

// Read-modify-write, so the expander pins this firmware does not own are untouched.
static esp_err_t reg_update(i2c_master_dev_handle_t dev, uint8_t reg, uint8_t clear_mask, uint8_t set_mask)
{
    uint8_t value = 0;
    const esp_err_t err = reg_read(dev, reg, &value);
    if (err != ESP_OK) {
        return err;
    }
    const uint8_t frame[] = { reg, (uint8_t)((value & ~clear_mask) | set_mask) };
    return i2c_master_transmit(dev, frame, sizeof(frame), I2C_TIMEOUT_MS);
}

// ------------------------------------------------- panel power, reset, variant

// Panel power off + both resets asserted, wait, then everything released.
static esp_err_t panel_power_cycle(void)
{
    i2c_master_dev_handle_t expander = NULL;
    esp_err_t err = i2c_attach(EXPANDER_ADDR, &expander);
    if (err != ESP_OK) {
        return err;
    }
    err = reg_update(expander, EXPANDER_REG_OUTPUT, EXIO_PANEL_MASK, 0);
    if (err == ESP_OK) {
        err = reg_update(expander, EXPANDER_REG_CONFIG, EXIO_PANEL_MASK, 0);
    }
    vTaskDelay(pdMS_TO_TICKS(PANEL_OFF_MS));
    if (err == ESP_OK) {
        err = reg_update(expander, EXPANDER_REG_OUTPUT, 0, EXIO_PANEL_MASK);
    }
    vTaskDelay(pdMS_TO_TICKS(PANEL_WAKE_MS));
    i2c_master_bus_rm_device(expander);
    return err;
}

static board_variant_t detect_variant(void)
{
    // A touch controller fresh out of reset can miss one probe: try a few times.
    for (int attempt = 0; attempt < VARIANT_PROBE_TRIES; attempt++) {
        if (i2c_master_probe(s_bus, ESP_LCD_TOUCH_IO_I2C_CST816S_ADDRESS, I2C_TIMEOUT_MS) == ESP_OK) {
            ESP_LOGI(TAG, "board V2: CO5300 panel + CST820 touch (0x15)");
            return BOARD_V2_CO5300_CST820;
        }
        if (i2c_master_probe(s_bus, ESP_LCD_TOUCH_IO_I2C_FT5x06_ADDRESS, I2C_TIMEOUT_MS) == ESP_OK) {
            ESP_LOGI(TAG, "board V1: SH8601 panel + FT3168 touch (0x38)");
            return BOARD_V1_SH8601_FT3168;
        }
        vTaskDelay(pdMS_TO_TICKS(VARIANT_PROBE_GAP_MS));
    }
    ESP_LOGE(TAG, "no touch controller at 0x15 or 0x38: assuming a V1 panel, touch disabled");
    return BOARD_TOUCH_MISSING;
}

// ---------------------------------------------------------------------- panel

static esp_err_t panel_start(board_variant_t variant)
{
    const bool is_v2 = (variant == BOARD_V2_CO5300_CST820);
    const spi_bus_config_t bus_cfg = SH8601_PANEL_BUS_QSPI_CONFIG(
        PIN_LCD_SCLK, PIN_LCD_D0, PIN_LCD_D1, PIN_LCD_D2, PIN_LCD_D3, DRAW_BUF_PIXELS * sizeof(uint16_t));
    esp_err_t err = spi_bus_initialize(LCD_SPI_HOST, &bus_cfg, SPI_DMA_CH_AUTO);
    if (err != ESP_OK) {
        return err;
    }
    const esp_lcd_panel_io_spi_config_t io_cfg = SH8601_PANEL_IO_QSPI_CONFIG(PIN_LCD_CS, NULL, NULL);
    err = esp_lcd_new_panel_io_spi((esp_lcd_spi_bus_handle_t)LCD_SPI_HOST, &io_cfg, &s_lcd_io);
    if (err != ESP_OK) {
        return err;
    }
    // The CO5300 speaks the same QSPI framing and command set, so one driver serves both.
    sh8601_vendor_config_t vendor = {
        .init_cmds = is_v2 ? INIT_V2_CO5300 : INIT_V1_SH8601,
        .init_cmds_size = is_v2 ? sizeof(INIT_V2_CO5300) / sizeof(INIT_V2_CO5300[0])
                                : sizeof(INIT_V1_SH8601) / sizeof(INIT_V1_SH8601[0]),
        .flags.use_qspi_interface = 1,
    };
    const esp_lcd_panel_dev_config_t panel_cfg = {
        .reset_gpio_num = GPIO_NUM_NC,            // reset is EXIO0, already pulsed
        .rgb_ele_order = LCD_RGB_ELEMENT_ORDER_RGB,
        .bits_per_pixel = 16,
        .vendor_config = &vendor,
    };
    err = esp_lcd_new_panel_sh8601(s_lcd_io, &panel_cfg, &s_panel);
    if (err == ESP_OK) {
        err = esp_lcd_panel_reset(s_panel);       // software reset
    }
    if (err == ESP_OK) {
        err = esp_lcd_panel_init(s_panel);
    }
    if (err == ESP_OK) {
        err = esp_lcd_panel_set_gap(s_panel, is_v2 ? V2_PANEL_X_GAP : 0, 0);
    }
    if (err == ESP_OK) {
        err = esp_lcd_panel_disp_on_off(s_panel, true);
    }
    return err;
}

// The panel only accepts windows that start on even coordinates and have even
// sizes. LVGL 9 has no rounder callback; this event is its replacement (LVGL
// also uses it to keep the row count of each partial-render chunk even).
static void on_invalidate_area(lv_event_t *event)
{
    lv_area_t *area = lv_event_get_param(event);
    area->x1 &= ~1;
    area->y1 &= ~1;
    area->x2 |= 1;
    area->y2 |= 1;
}

static esp_err_t lvgl_start(void)
{
    const lvgl_port_cfg_t port_cfg = ESP_LVGL_PORT_INIT_CONFIG();
    const esp_err_t err = lvgl_port_init(&port_cfg);
    if (err != ESP_OK) {
        return err;
    }
    // Without sw_rotate the port asks the panel for swap_xy(false) once and the
    // SH8601 driver logs "swap_xy is not supported": harmless, nothing is swapped.
    const lvgl_port_display_cfg_t disp_cfg = {
        .io_handle = s_lcd_io,                    // flush-ready comes from the SPI done callback
        .panel_handle = s_panel,
        .buffer_size = DRAW_BUF_PIXELS,
        .double_buffer = false,
        .hres = BOARD_LCD_H_RES,
        .vres = BOARD_LCD_V_RES,
        .monochrome = false,
        .color_format = LV_COLOR_FORMAT_RGB565,
        .flags = {
            .buff_dma = true,
            .swap_bytes = true,
            .sw_rotate = DISPLAY_ROTATE_180,      // the panel cannot flip vertically by itself
        },
    };
    s_display = lvgl_port_add_disp(&disp_cfg);
    if (s_display == NULL) {
        return ESP_ERR_NO_MEM;
    }
    lvgl_port_lock(0);
    lv_display_add_event_cb(s_display, on_invalidate_area, LV_EVENT_INVALIDATE_AREA, NULL);
#if DISPLAY_ROTATE_180
    lv_display_set_rotation(s_display, LV_DISPLAY_ROTATION_180);
#endif
    lvgl_port_unlock();
    return ESP_OK;
}

// ---------------------------------------------------------------------- touch

// An idle touch controller may NACK (the CST820 sleeps between touches, and
// Waveshare's FT3168 demo mutes the same errors). Mute the drivers once, keep going.
static void note_touch_failure(esp_err_t err)
{
    s_touch_failures++;
    if (s_touch_failures != TOUCH_FAILS_TO_MUTE) {
        return;
    }
    ESP_LOGW(TAG, "touch reads failing (%s): treated as 'not touched', driver logs muted",
             esp_err_to_name(err));
    esp_log_level_set("FT5x06", ESP_LOG_NONE);
    esp_log_level_set("CST816S", ESP_LOG_NONE);
    esp_log_level_set("lcd_panel.io.i2c", ESP_LOG_NONE);
    esp_log_level_set("i2c.master", ESP_LOG_NONE);
}

// Own read callback instead of lvgl_port_add_touch(): that one wraps the I2C
// read in ESP_ERROR_CHECK, so a single NACK would reboot the badge.
static void on_touch_read(lv_indev_t *indev, lv_indev_data_t *data)
{
    (void)indev;
    data->state = LV_INDEV_STATE_RELEASED;
    const esp_err_t err = esp_lcd_touch_read_data(s_touch);
    if (err != ESP_OK) {
        note_touch_failure(err);
        return;
    }
    s_touch_failures = 0;
    esp_lcd_touch_point_data_t point = { 0 };
    uint8_t count = 0;
    if (esp_lcd_touch_get_data(s_touch, &point, &count, 1) == ESP_OK && count > 0) {
        data->point.x = point.x;
        data->point.y = point.y;
        data->state = LV_INDEV_STATE_PRESSED;
    }
}

static esp_err_t touch_start(board_variant_t variant)
{
    const bool is_v2 = (variant == BOARD_V2_CO5300_CST820);
    esp_lcd_panel_io_i2c_config_t io_cfg_v1 = ESP_LCD_TOUCH_IO_I2C_FT5x06_CONFIG();
    esp_lcd_panel_io_i2c_config_t io_cfg_v2 = ESP_LCD_TOUCH_IO_I2C_CST816S_CONFIG();
    esp_lcd_panel_io_i2c_config_t *io_cfg = is_v2 ? &io_cfg_v2 : &io_cfg_v1;
    io_cfg->scl_speed_hz = I2C_SPEED_HZ;
    esp_lcd_panel_io_handle_t io = NULL;
    esp_err_t err = esp_lcd_new_panel_io_i2c(s_bus, io_cfg, &io);
    if (err != ESP_OK) {
        return err;
    }
    const esp_lcd_touch_config_t touch_cfg = {
        .x_max = BOARD_LCD_H_RES,
        .y_max = BOARD_LCD_V_RES,
        .rst_gpio_num = GPIO_NUM_NC,              // reset is EXIO2, already pulsed
        .int_gpio_num = GPIO_NUM_NC,              // polled every LVGL input period
    };
    err = is_v2 ? esp_lcd_touch_new_i2c_cst816s(io, &touch_cfg, &s_touch)
                : esp_lcd_touch_new_i2c_ft5x06(io, &touch_cfg, &s_touch);
    if (err != ESP_OK) {
        s_touch = NULL;
        esp_lcd_panel_io_del(io);
        return err;
    }
    lvgl_port_lock(0);
    s_indev = lv_indev_create();
    lv_indev_set_type(s_indev, LV_INDEV_TYPE_POINTER);
    lv_indev_set_read_cb(s_indev, on_touch_read);
    lv_indev_set_display(s_indev, s_display);
    lvgl_port_unlock();
    return ESP_OK;
}

// ----------------------------------------------------------------- public API

lv_display_t *board_display_start(void)
{
    esp_err_t err = i2c_bus_init();
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "I2C bus: %s", esp_err_to_name(err));
        return NULL;
    }
    err = panel_power_cycle();
    if (err != ESP_OK) {
        // Keep going: with the expander pins left as inputs their pull-ups hold
        // the panel powered and out of reset.
        ESP_LOGE(TAG, "TCA9554 panel power/reset: %s", esp_err_to_name(err));
    }
    const board_variant_t variant = detect_variant();
    err = panel_start(variant);
    if (err == ESP_OK) {
        err = lvgl_start();
    }
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "display: %s", esp_err_to_name(err));
        return NULL;
    }
    if (variant == BOARD_TOUCH_MISSING) {
        ESP_LOGW(TAG, "running without touch");
    } else if ((err = touch_start(variant)) != ESP_OK) {
        ESP_LOGE(TAG, "touch: %s (running without it)", esp_err_to_name(err));
    }
    ESP_LOGI(TAG, "display up, %u KB internal RAM free",
             (unsigned)(heap_caps_get_free_size(MALLOC_CAP_INTERNAL) / 1024));
    return s_display;
}

bool board_display_lock(uint32_t timeout_ms)
{
    return lvgl_port_lock(timeout_ms);
}

void board_display_unlock(void)
{
    lvgl_port_unlock();
}

esp_err_t board_display_brightness_set(int percent)
{
    if (s_lcd_io == NULL) {
        return ESP_ERR_INVALID_STATE;
    }
    if (percent < 0 || percent > 100) {
        return ESP_ERR_INVALID_ARG;
    }
    const uint8_t level = BRIGHTNESS_REG(percent);
    const int command = (QSPI_OPCODE_WRITE_CMD << 24) | (LCD_CMD_BRIGHTNESS << 8);
    lvgl_port_lock(0);                            // never interleave with a frame flush
    const esp_err_t err = esp_lcd_panel_io_tx_param(s_lcd_io, command, &level, 1);
    lvgl_port_unlock();
    return err;
}

lv_indev_t *board_touch_indev(void)
{
    return s_indev;
}

esp_err_t board_imu_init(void)
{
    const esp_err_t err = i2c_bus_init();
    return (err == ESP_OK) ? qmi8658_start(s_bus) : err;
}

esp_err_t board_imu_read_g(float *x, float *y, float *z)
{
    return qmi8658_read_g(x, y, z);
}

esp_err_t board_boot_button_create(button_handle_t *out_button)
{
    const button_config_t button_cfg = { 0 };
    const button_gpio_config_t gpio_cfg = { .gpio_num = PIN_BOOT_BUTTON, .active_level = 0 };
    return iot_button_new_gpio_device(&button_cfg, &gpio_cfg, out_button);
}

int board_battery_percent(bool *out_charging)
{
    if (out_charging != NULL) {
        *out_charging = false;
    }
    if (s_bus == NULL || s_pmu_absent) {
        return -1;
    }
    if (s_pmu == NULL) {
        // Probe once: a PMU that is not there must not cost an I2C error every refresh.
        if (i2c_master_probe(s_bus, PMU_ADDR, I2C_TIMEOUT_MS) != ESP_OK ||
            i2c_attach(PMU_ADDR, &s_pmu) != ESP_OK) {
            ESP_LOGW(TAG, "AXP2101 not answering: battery level hidden");
            s_pmu = NULL;
            s_pmu_absent = true;
            return -1;
        }
    }
    uint8_t status1 = 0, status2 = 0, percent = 0;
    if (reg_read(s_pmu, PMU_REG_STATUS1, &status1) != ESP_OK ||
        reg_read(s_pmu, PMU_REG_STATUS2, &status2) != ESP_OK ||
        reg_read(s_pmu, PMU_REG_BAT_PERCENT, &percent) != ESP_OK) {
        return -1;
    }
    if (out_charging != NULL) {
        *out_charging = ((status2 >> 5) == 0x01);
    }
    if ((status1 & PMU_BATTERY_PRESENT) == 0) {
        return -1;
    }
    return percent > 100 ? 100 : percent;
}
