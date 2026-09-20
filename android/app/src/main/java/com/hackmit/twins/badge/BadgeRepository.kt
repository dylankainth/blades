package com.hackmit.twins.badge

import android.content.Context
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.tasks.await

/**
 * Talks to the Kindred badge backend (functions/src/boxPairing.ts). A badge
 * is an ESP32-S3-BOX-3 worn on a lanyard; see hardware/box/CONTRACT.md.
 */
object BadgeRepository {

    private const val QR_PREFIX = "kindred-box:"

    private val functions get() = Firebase.functions

    /**
     * Opens Google's code scanner (runs in Play services, so no CAMERA
     * permission or camera code in this app) and returns the badge's QR
     * payload, or null if the user backed out or scanned something else.
     */
    suspend fun scanBadgeCode(context: Context): String? {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        val raw = GmsBarcodeScanning.getClient(context, options).startScan().await().rawValue
        return raw?.takeIf { it.startsWith(QR_PREFIX, ignoreCase = true) }
    }

    /** Binds the badge to the signed-in twin. Returns the owner's first name, if known. */
    suspend fun pair(badgeCode: String): String? {
        val result = functions.getHttpsCallable("pairBox")
            .call(mapOf("boxId" to badgeCode))
            .await()
        return (result.getData() as? Map<*, *>)?.get("ownerName") as? String
    }

    /** Clears this twin's matches so the demo can run again. Returns how many were removed. */
    suspend fun resetDemo(): Int {
        val result = functions.getHttpsCallable("resetDemo").call().await()
        return ((result.getData() as? Map<*, *>)?.get("deletedMatches") as? Number)?.toInt() ?: 0
    }
}
