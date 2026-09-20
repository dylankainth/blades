package com.hackmit.twins.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The twin's ears and mouth, backed by Deepgram through two Cloud Functions
 * (functions/src/voice.ts). Muse Spark is still the brain: a spoken turn is
 * transcribed and then sent through the normal typed-chat path.
 */
object VoiceRepository {

    private const val TAG = "Voice"
    private const val RECORDING_MIME = "audio/mp4"
    private const val MAX_RECORDING_MS = 60_000

    private val functions get() = Firebase.functions

    /** Outlives any one screen so a whisper still plays after the UI is gone. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var player: MediaPlayer? = null

    val isRecording: Boolean get() = recorder != null

    /** Starts capturing one utterance. Caller must already hold RECORD_AUDIO. */
    fun startRecording(context: Context) {
        stopPlayback()
        val file = File.createTempFile("utterance", ".m4a", context.cacheDir)
        val newRecorder = MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(32_000)
            setMaxDuration(MAX_RECORDING_MS)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = newRecorder
        recordingFile = file
    }

    /** Stops capturing and returns what was said ("" if nothing intelligible). */
    suspend fun stopAndTranscribe(): String = withContext(Dispatchers.IO) {
        val file = recordingFile
        try {
            recorder?.apply { stop(); release() }
        } catch (e: RuntimeException) {
            // MediaRecorder throws if stop() follows start() too quickly.
            Log.w(TAG, "Recording too short to keep", e)
            return@withContext ""
        } finally {
            recorder = null
            recordingFile = null
        }
        if (file == null || file.length() == 0L) return@withContext ""

        try {
            val audioBase64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            val result = functions.getHttpsCallable("transcribeSpeech")
                .call(mapOf("audioBase64" to audioBase64, "mimeType" to RECORDING_MIME))
                .await()
            ((result.getData() as? Map<*, *>)?.get("text") as? String).orEmpty()
        } finally {
            file.delete()
        }
    }

    /** Speaks [text] in the twin's voice through whatever output is active. */
    suspend fun speak(context: Context, text: String) = withContext(Dispatchers.IO) {
        val result = functions.getHttpsCallable("speakText").call(mapOf("text" to text)).await()
        val audioBase64 = (result.getData() as? Map<*, *>)?.get("audioBase64") as? String ?: return@withContext
        val file = File.createTempFile("twin", ".mp3", context.cacheDir)
        file.writeBytes(Base64.decode(audioBase64, Base64.DEFAULT))

        stopPlayback()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            setDataSource(file.absolutePath)
            setOnCompletionListener { finished ->
                finished.release()
                if (player === finished) player = null
                file.delete()
            }
            prepare()
            start()
        }
    }

    fun stopPlayback() {
        player?.apply { runCatching { stop() }; release() }
        player = null
    }

    /**
     * Your twin telling you, privately, why someone nearby is worth meeting.
     * Only ever plays into headphones: the phone speaker would announce it to
     * the very person it is about. Fire-and-forget; never blocks the caller.
     */
    fun whisperIfListening(context: Context, text: String) {
        val appContext = context.applicationContext
        if (!headphonesConnected(appContext)) return
        appScope.launch {
            try {
                speak(appContext, text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Whisper failed", e)
            }
        }
    }

    private fun headphonesConnected(context: Context): Boolean {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return false
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADPHONE_TYPES }
    }

    private val HEADPHONE_TYPES = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_HEARING_AID,
    )
}
