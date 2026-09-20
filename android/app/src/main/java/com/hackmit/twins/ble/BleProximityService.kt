package com.hackmit.twins.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hackmit.twins.MainActivity
import com.hackmit.twins.R
import com.hackmit.twins.auth.AuthManager
import com.hackmit.twins.checkin.CheckinRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Foreground service that is BOTH a BLE peripheral (advertises this device's
 * twinId) and a BLE central (scans for other twins' advertisements) at the
 * same time. No GATT server/client is used — everything rides in the
 * advertisement's service data payload, which is enough for a one-way
 * "I saw you" proximity signal and keeps this dramatically simpler than a
 * GATT connection handshake between every pair of phones in the room.
 *
 * On detecting another twin, writes a checkin doc to
 * checkins/{locationId}/people/{twinId} for BOTH twinIds involved (see
 * [recordMutualCheckin]), which a backend Cloud Function watches to kick off
 * matching.
 */
class BleProximityService : Service() {

    // SupervisorJob, not Job: a single failed child (e.g. one Firestore
    // token-resolution call hitting a transient error) must not cancel the
    // whole scope — that would silently kill advertising/scanning for the
    // rest of the service's life with no crash or log, since this is a
    // long-running foreground service and BLE scan results keep arriving
    // as ordinary system callbacks regardless of our coroutine state.
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private var myTwinId: String? = null

    // Short random per-session identifier actually broadcast over BLE (see
    // class doc + BleSessionRepository for why: a raw Firebase uid is far
    // too large for a legacy BLE advertisement packet's 31-byte budget).
    private var myToken: String = ""

    // Once a token is resolved it's cached here (token -> twinId), so later
    // scan results for the SAME token can update the live RSSI map below
    // without a repeat Firestore round-trip — that's what RadarScreen needs
    // (continuous "getting closer/farther"). The check-in write is rate
    // limited separately: see markSeen() in the companion object.
    private val resolvedTokens = ConcurrentHashMap<String, String>()

    override fun onCreate() {
        super.onCreate()
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager?.adapter
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        scanner = bluetoothAdapter?.bluetoothLeScanner

        startForeground(NOTIFICATION_ID, buildNotification())

        // This service is only ever started (from MainActivity) after a
        // successful sign-in/up, so a twinId is guaranteed to exist here.
        val twinId = AuthManager.currentTwinIdOrNull()
        if (twinId == null) {
            stopSelf()
            return
        }
        myTwinId = twinId
        myToken = generateToken()
        serviceScope.launch {
            BleSessionRepository.registerToken(myToken, twinId)
            startAdvertising(myToken)
            startScanning()
            // Android quietly downgrades a long-running scan to opportunistic
            // (after 5 min on Samsung One UI, 30 min on AOSP) and results stop
            // arriving. Restarting before that deadline keeps detection live.
            while (isActive) {
                delay(SCAN_RESTART_MS)
                stopScanning()
                startScanning()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: if the system kills this process under memory
        // pressure, restart the service (without redelivering the last
        // intent) so proximity detection resumes automatically. There's no
        // meaningful intent payload to redeliver here anyway.
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        super.onDestroy()
        stopAdvertising()
        stopScanning()
        serviceScope.cancel()
        _nearbyRssi.value = emptyMap()
    }

    /**
     * Called when the user swipes the app away from Recents. Deliberate
     * choice, not an oversight: we let the foreground service (and BLE
     * advertising/scanning) keep running, because the entire pitch depends
     * on proximity detection working in the background without the app
     * being in the foreground — that's the point of a foreground service
     * with a persistent notification instead of a plain background task.
     *
     * TODO(demo-day): verify on a real device. OEM battery-management
     * layers (esp. non-Pixel Android skins) are notorious for killing
     * foreground services on task-removal despite START_STICKY /
     * onTaskRemoved being a no-op here — this needs to be confirmed on the
     * actual hardware used for the demo, not just the emulator.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Intentionally does nothing extra — see comment above.
    }

    // ---- Advertising ----------------------------------------------------

    private fun startAdvertising(token: String) {
        val adv = advertiser ?: run {
            Log.w(TAG, "No BLE advertiser available on this device; cannot advertise")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false) // broadcast-only, no GATT server backing this
            .build()

        // Deliberately NOT calling addServiceUuid() here (a separate
        // "Service UUID List" AD structure) — it costs another 18 bytes of
        // a legacy advertisement packet's 31-byte budget for no benefit,
        // since ScanFilter.setServiceData() below already filters on the
        // service data's embedded UUID. Only the Service Data AD structure
        // (2-byte header + 16-byte UUID + token bytes) is broadcast.
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceData(ParcelUuid(BleConstants.SERVICE_UUID), tokenToBytes(token))
            .build()

        try {
            adv.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            // BLUETOOTH_ADVERTISE not granted; the launching Activity is
            // responsible for requesting it before starting this service.
            Log.e(TAG, "Missing BLUETOOTH_ADVERTISE permission", e)
        }
    }

    private fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLUETOOTH_ADVERTISE permission while stopping", e)
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE advertise failed to start, errorCode=$errorCode")
        }
    }

    // ---- Scanning ---------------------------------------------------------

    private fun startScanning() {
        val scn = scanner ?: run {
            Log.w(TAG, "No BLE scanner available on this device; cannot scan for twins")
            return
        }

        // Matches on the Service Data AD structure's embedded UUID (see
        // startAdvertising's comment on why we don't also broadcast a
        // separate Service UUID List). A null data/mask matches any
        // payload carrying this service UUID, regardless of the token
        // bytes that follow it.
        val filter = ScanFilter.Builder()
            .setServiceData(ParcelUuid(BleConstants.SERVICE_UUID), null)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        try {
            scn.startScan(listOf(filter), settings, scanCallback)
        } catch (e: IllegalStateException) {
            // Bluetooth was switched off since the scanner was obtained. The
            // periodic restart picks scanning back up once it is on again.
            Log.w(TAG, "Bluetooth is off; scan not started", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLUETOOTH_SCAN permission", e)
        }
    }

    private fun stopScanning() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Bluetooth is off; nothing to stop", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLUETOOTH_SCAN permission while stopping", e)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val serviceData = result.scanRecord
                ?.getServiceData(ParcelUuid(BleConstants.SERVICE_UUID))
                ?: return
            val token = bytesToToken(serviceData) ?: return
            if (token == myToken) return // shouldn't happen, but guard anyway
            val myId = myTwinId ?: return

            val cachedTwinId = resolvedTokens[token]
            if (cachedTwinId != null) {
                // Already resolved on an earlier scan — just update live
                // RSSI, no repeat Firestore call. This is what drives
                // RadarScreen's "getting closer/farther" feedback.
                _nearbyRssi.update { it + (cachedTwinId to result.rssi) }
                maybeCheckin(myId, cachedTwinId, token, result.rssi)
                return
            }

            // First time seeing this token: resolve it once via Firestore
            // (see BleSessionRepository for why this indirection exists —
            // a raw Firebase uid doesn't fit a BLE advertisement packet).
            // Caught explicitly (not left to propagate) so one transient
            // Firestore error — offline blip, not-yet-registered token —
            // only drops this single scan result instead of tearing down
            // the coroutine tree, and so it shows up in logcat instead of
            // vanishing silently.
            serviceScope.launch {
                val otherTwinId = try {
                    BleSessionRepository.resolveToken(token)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to resolve BLE token $token", e)
                    null
                } ?: return@launch
                if (otherTwinId == myId) return@launch
                resolvedTokens[token] = otherTwinId
                _nearbyRssi.update { it + (otherTwinId to result.rssi) }
                maybeCheckin(myId, otherTwinId, token, result.rssi)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed, errorCode=$errorCode")
        }
    }

    /**
     * The radar wants every reading; a check-in (which kicks off a
     * negotiation) should only happen when someone has actually walked up,
     * and at most once per SEEN_TTL_MS per token. BLE adverts carry 20 m+
     * indoors, so "detected" alone is not "nearby".
     */
    private fun maybeCheckin(myId: String, otherTwinId: String, token: String, rssi: Int) {
        if (rssi < MIN_RSSI_DBM) return
        if (!markSeen(token)) return
        Log.i(TAG, "Token $token in range at $rssi dBm")
        onTwinDetected(myId, otherTwinId)
    }

    private fun onTwinDetected(myId: String, otherTwinId: String) {
        Log.i(TAG, "Detected nearby twin: $otherTwinId")
        // BLE proximity has no natural "venue" locationId the way a QR
        // check-in does (see CheckinScreen.kt) — two phones just came near
        // each other. We use a synthetic, order-independent id derived from
        // both twinIds so the *same pair* of twins consistently lands in
        // the same location document (useful for de-duping on the backend
        // too), rather than a single flat "ble-proximity" bucket that would
        // mix every pair in the room together under one location.
        // Only our own doc: Firestore rules reject writing someone else's
        // check-in. Naming otherTwinId on it lets onCheckin negotiate straight
        // away instead of waiting for the other side to detect us too — which
        // never happens when the other person is wearing a badge and their
        // phone is not advertising.
        val locationId = syntheticPairLocationId(myId, otherTwinId)
        CheckinRepository.recordCheckin(locationId, myId, otherTwinId)
    }

    // ---- Notification -----------------------------------------------------

    private fun buildNotification(): Notification {
        val channelId = getString(R.string.ble_notification_channel_id)
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    getString(R.string.ble_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW, // silent, persistent
                ),
            )
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.ble_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    /**
     * 8 random bytes — comfortably fits a legacy BLE advertisement packet's
     * 31-byte budget alongside the mandatory flags + service-data-UUID
     * overhead (2 + 16 = 18 bytes), unlike a raw ~28-byte Firebase uid.
     * 2^64 possible values is more than enough collision safety for a
     * single event's concurrent advertisers.
     */
    private fun generateToken(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "BleProximityService"
        private const val NOTIFICATION_ID = 42

        /** Roughly "within a couple of metres". Tune at the venue via logcat. */
        private const val MIN_RSSI_DBM = -72

        /** Re-report a token we keep seeing at most this often. */
        private const val SEEN_TTL_MS = 60_000L

        /** Under Samsung's 5 min long-scan limit, the shortest we know of. */
        private const val SCAN_RESTART_MS = 4 * 60_000L

        // Rate limit for the check-in write (NOT the radar, which wants every
        // reading). Static so the Home screen's "Reset demo" can clear it
        // without restarting the service. Re-reports are cheap server-side:
        // runNegotiation skips a pair that already has a match doc.
        private val lastSeenAtMs = ConcurrentHashMap<String, Long>()

        private fun markSeen(token: String): Boolean {
            val now = System.currentTimeMillis()
            val previous = lastSeenAtMs[token]
            if (previous != null && now - previous < SEEN_TTL_MS) return false
            lastSeenAtMs[token] = now
            return true
        }

        fun forgetSeenTokens() = lastSeenAtMs.clear()

        // Latest RSSI (dBm) per resolved nearby twinId — a rougher-but-
        // real stand-in for "distance" (no UWB on these phones, just BLE
        // signal strength, which is noisy: affected by orientation,
        // obstacles, and crowd density — good enough for a "warmer/
        // colder" radar affordance, not precise distance). Written from
        // this Service's scanCallback above; RadarScreen collects it.
        private val _nearbyRssi = MutableStateFlow<Map<String, Int>>(emptyMap())
        val nearbyRssi: StateFlow<Map<String, Int>> = _nearbyRssi

        private fun tokenToBytes(token: String): ByteArray =
            ByteArray(token.length / 2) { i ->
                token.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }

        private fun bytesToToken(bytes: ByteArray): String? =
            runCatching { bytes.joinToString("") { "%02x".format(it) } }.getOrNull()

        /** Order-independent so both devices in a pair compute the same id. */
        private fun syntheticPairLocationId(a: String, b: String): String {
            val (first, second) = if (a < b) a to b else b to a
            return "ble-pair-${first.take(8)}-${second.take(8)}"
        }
    }
}
