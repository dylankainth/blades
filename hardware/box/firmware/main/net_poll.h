// Polls STATE_URL once per POLL_INTERVAL_MS, feeds the UI + BLE token, and
// POSTs gesture events to EVENTS_URL.
#pragma once

void net_poll_start(const char *box_id);

// Queue a "shake" event. It is only sent while the backend state is `match`
// (otherwise dropped). Safe from any task; returns immediately.
void net_poll_request_shake(void);
