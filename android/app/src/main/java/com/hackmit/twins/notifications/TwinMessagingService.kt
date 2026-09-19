package com.hackmit.twins.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.hackmit.twins.MainActivity
import com.hackmit.twins.R

/**
 * Receives the match notification pushed by the backend once the matching
 * engine (running over the shortlist from a Firestore checkin, per
 * CLAUDE.md) decides two twins should meet.
 *
 * Tone matters here per CLAUDE.md: this reads as a message from *your own
 * agent*, not a corporate "you have a new match!" alert, and it always
 * surfaces one concrete plain-language reason — never a score/percentage.
 * Tapping it opens MatchScreen for a human-in-the-loop "say hi" — it never
 * auto-sends a message or auto-books anything on the user's behalf.
 *
 * Expected RemoteMessage.data payload (set by the backend):
 *   matchedTwinId, matchedName, matchedPhotoUrl, reason
 */
class TwinMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // TODO: send `token` up to Firestore/backend, keyed by twinId, so
        // the matching Cloud Function knows where to deliver match pushes
        // for this device. (Needs twinId, which requires the anonymous-auth
        // sign-in from AuthManager to have already happened — wire this up
        // once MainActivity's startup sequencing is finalized.)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val matchedTwinId = data["matchedTwinId"] ?: return
        val matchedName = data["matchedName"] ?: "Someone nearby"
        val matchedPhotoUrl = data["matchedPhotoUrl"]
        val reason = data["reason"]
            ?: "Your twin thinks you two should talk."

        showMatchNotification(matchedTwinId, matchedName, matchedPhotoUrl, reason)
    }

    private fun showMatchNotification(
        matchedTwinId: String,
        matchedName: String,
        matchedPhotoUrl: String?,
        reason: String,
    ) {
        val channelId = getString(R.string.match_notification_channel_id)
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    getString(R.string.match_notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH, // this is the whole product; should heads-up
                ),
            )
        }

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_MATCH
            putExtra(MainActivity.EXTRA_MATCHED_TWIN_ID, matchedTwinId)
            putExtra(MainActivity.EXTRA_MATCHED_NAME, matchedName)
            putExtra(MainActivity.EXTRA_MATCHED_PHOTO_URL, matchedPhotoUrl)
            putExtra(MainActivity.EXTRA_MATCH_REASON, reason)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            matchedTwinId.hashCode(),
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // "Sarah's twin and I think you two should talk — she's stuck on the
        // same devops problem you solved." Personal, specific, not a score.
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Your twin found someone: $matchedName")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            // Deliberately no auto-send / auto-schedule action button here —
            // tapping only opens MatchScreen for a human to decide. See
            // CLAUDE.md guardrails.
            .addAction(0, "Say hi", pendingIntent)
            .build()

        // TODO(photo): load matchedPhotoUrl into a large-icon bitmap (e.g.
        // via Coil's ImageLoader) before building the notification above —
        // left as a follow-up since notification building here is
        // synchronous and bitmap loading is not.

        NotificationManagerCompat.from(this).notify(matchedTwinId.hashCode(), notification)
    }
}
