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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
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

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private var myTwinId: String? = null

    // De-dupe: avoid spamming Firestore with a write every single time we
    // re-see the same nearby twin (BLE scan results can fire many times a
    // second for one physical device). Simple in-memory set is fine for a
    // hackathon demo; it resets when the service restarts.
    private val recentlySeenTwinIds = ConcurrentHashMap.newKeySet<String>()

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
        serviceScope.launch {
            startAdvertising(twinId)
            startScanning()
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

    private fun startAdvertising(twinId: String) {
        val adv = advertiser ?: run {
            Log.w(TAG, "No BLE advertiser available on this device; cannot advertise twinId")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false) // broadcast-only, no GATT server backing this
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .addServiceData(ParcelUuid(BleConstants.SERVICE_UUID), twinIdToBytes(twinId))
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

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        try {
            scn.startScan(listOf(filter), settings, scanCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLUETOOTH_SCAN permission", e)
        }
    }

    private fun stopScanning() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLUETOOTH_SCAN permission while stopping", e)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val serviceData = result.scanRecord
                ?.getServiceData(ParcelUuid(BleConstants.SERVICE_UUID))
                ?: return
            val otherTwinId = bytesToTwinId(serviceData) ?: return
            val myId = myTwinId ?: return
            if (otherTwinId == myId) return // shouldn't happen, but guard anyway

            if (recentlySeenTwinIds.add(otherTwinId)) {
                onTwinDetected(myId, otherTwinId)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed, errorCode=$errorCode")
        }
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
        val locationId = syntheticPairLocationId(myId, otherTwinId)
        CheckinRepository.recordCheckin(locationId, myId)
        CheckinRepository.recordCheckin(locationId, otherTwinId)
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

    companion object {
        private const val TAG = "BleProximityService"
        private const val NOTIFICATION_ID = 42

        private fun twinIdToBytes(twinId: String): ByteArray =
            twinId.toByteArray(StandardCharsets.UTF_8)

        private fun bytesToTwinId(bytes: ByteArray): String? =
            runCatching { String(bytes, StandardCharsets.UTF_8) }.getOrNull()

        /** Order-independent so both devices in a pair compute the same id. */
        private fun syntheticPairLocationId(a: String, b: String): String {
            val (first, second) = if (a < b) a to b else b to a
            return "ble-pair-${first.take(8)}-${second.take(8)}"
        }
    }
}
