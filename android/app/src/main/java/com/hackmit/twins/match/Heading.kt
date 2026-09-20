package com.hackmit.twins.match

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.atan2

private const val TAG = "Heading"

/** Below this the pointing vector is near vertical and its compass direction is noise. */
private const val MIN_HORIZONTAL = 0.2f

/**
 * Which way the user is facing, in degrees clockwise, from a device-to-world
 * rotation matrix (row-major 3x3, as SensorManager fills it).
 *
 * People hold a phone anywhere from flat to upright. Flat, the top edge (+Y)
 * points ahead; upright, the back of the phone (-Z) does. Their sum points
 * ahead across that whole range, so that is the vector projected onto the
 * ground here. Returns null when it has no usable horizontal part.
 */
fun headingFromRotationMatrix(rotation: FloatArray): Float? {
    val east = rotation[1] - rotation[2]
    val north = rotation[4] - rotation[5]
    if (abs(east) < MIN_HORIZONTAL && abs(north) < MIN_HORIZONTAL) return null
    return (Math.toDegrees(atan2(east.toDouble(), north.toDouble())).toFloat() + 360f) % 360f
}

/** "ahead of you", "to your right"... for a bearing relative to where the user faces. */
fun relativeDirectionLabel(bearingDeg: Float, headingDeg: Float): String {
    val relative = (((bearingDeg - headingDeg) % 360f) + 360f) % 360f
    return when {
        relative < 45f || relative >= 315f -> "ahead of you"
        relative < 135f -> "to your right"
        relative < 225f -> "behind you"
        else -> "to your left"
    }
}

/** The shortest signed turn, in degrees, that takes [fromDeg] to [toDeg]. */
fun shortestTurn(fromDeg: Float, toDeg: Float): Float =
    ((((toDeg - fromDeg) % 360f) + 540f) % 360f) - 180f

/**
 * Live heading while the caller is on screen; null until the first reading or
 * if the phone has no rotation sensor.
 *
 * Uses the gyro-only game rotation vector where there is one. Its zero is
 * arbitrary rather than north, which is fine: the radar only compares headings
 * with each other. In exchange it ignores the magnetic mess of a hall full of
 * laptops and steel, which swings the magnetometer-backed sensor by tens of
 * degrees.
 */
@Composable
fun rememberHeadingDeg(): State<Float?> {
    val context = LocalContext.current
    val heading = remember { mutableStateOf<Float?>(null) }
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            private val rotation = FloatArray(9)

            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                headingFromRotationMatrix(rotation)?.let { heading.value = it }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor == null) {
            Log.w(TAG, "No rotation sensor; the radar will show distance only")
        } else {
            sensorManager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager?.unregisterListener(listener) }
    }
    return heading
}
