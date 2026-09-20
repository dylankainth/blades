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
import com.hackmit.twins.voice.VoiceRepository
import com.hackmit.twins.R

/**
 * Receives the two push notification types the backend sends around a
 * match:
 *  - "open_teaser" (notifyMatch.ts): the negotiation confirmed a pair is
 *    worth surfacing. Deliberately doesn't name-drop who it is in the
 *    notification text — tapping opens MatchTeaserScreen, which shows a
 *    real photo but a *blurred* name, for a human to approve/disapprove.
 *  - "open_radar" (submitMatchApproval.ts): both people approved. Opens
 *    RadarScreen, where identity is fully revealed and a live BLE signal
 *    helps the two people find each other.
 *
 * Tone matters per CLAUDE.md: this reads as a message from *your own
 * agent*, never a corporate "you have a new match!" alert, and it never
 * auto-sends a message or auto-books anything — every step here just opens
 * a screen for a human to decide on.
 */
class TwinMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // No-op while signed out; MainActivity syncs the current token again
        // as soon as a twin is signed in (see PushTokenRepository).
        PushTokenRepository.register(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        when (data["action"]) {
            "open_teaser" -> showTeaserNotification(data)
            "open_radar" -> showRadarNotification(data)
        }
    }

    private fun showTeaserNotification(data: Map<String, String>) {
        val matchId = data["matchId"] ?: return
        val reason = data["reason"] ?: "Your twin thinks you two should talk."
        val photoUrl = data["otherPhotoUrl"]

        // The twin says it in your ear as well as on screen — but only if you
        // are wearing headphones. Same wording as the notification, so it
        // names nobody before both people approve the reveal.
        VoiceRepository.whisperIfListening(this, "Your twin found someone. $reason")

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_TEASER
            putExtra(MainActivity.EXTRA_MATCH_ID, matchId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        notify(
            channelId = getString(R.string.match_notification_channel_id),
            channelName = getString(R.string.match_notification_channel_name),
            requestCode = matchId.hashCode(),
            title = "Your twin found someone",
            body = reason,
            photoUrl = photoUrl,
            contentIntent = contentIntent,
        )
    }

    private fun showRadarNotification(data: Map<String, String>) {
        val otherTwinId = data["otherTwinId"] ?: return
        val otherName = data["otherName"] ?: "Someone nearby"
        val photoUrl = data["otherPhotoUrl"]

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_RADAR
            putExtra(MainActivity.EXTRA_MATCHED_TWIN_ID, otherTwinId)
            putExtra(MainActivity.EXTRA_MATCHED_NAME, otherName)
            putExtra(MainActivity.EXTRA_MATCHED_PHOTO_URL, photoUrl)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        notify(
            channelId = getString(R.string.match_notification_channel_id),
            channelName = getString(R.string.match_notification_channel_name),
            requestCode = otherTwinId.hashCode() xor 1, // distinct from the teaser notification for the same pair
            title = "You're both in!",
            body = "$otherName said yes too — go find each other.",
            photoUrl = photoUrl,
            contentIntent = contentIntent,
        )
    }

    private fun notify(
        channelId: String,
        channelName: String,
        requestCode: Int,
        title: String,
        body: String,
        photoUrl: String?,
        contentIntent: Intent,
    ) {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH),
            )
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // TODO(photo): load photoUrl into a large-icon bitmap (e.g. via
        // Coil's ImageLoader) before building the notification above — left
        // as a follow-up since notification building here is synchronous
        // and bitmap loading is not.

        NotificationManagerCompat.from(this).notify(requestCode, notification)
    }
}
