package com.example.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

object AntiTheftManager : SensorEventListener {

    private const val TAG = "AntiTheftManager"

    private var appContext: Context? = null
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var alarmRingtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    private val _isChargerShieldArmed = MutableStateFlow(false)
    val isChargerShieldArmed: StateFlow<Boolean> = _isChargerShieldArmed.asStateFlow()

    private val _isMotionShieldArmed = MutableStateFlow(false)
    val isMotionShieldArmed: StateFlow<Boolean> = _isMotionShieldArmed.asStateFlow()

    private val _isAlarmSounding = MutableStateFlow(false)
    val isAlarmSounding: StateFlow<Boolean> = _isAlarmSounding.asStateFlow()

    private var lastAcceleration = 0f
    private var currentAcceleration = 0f
    private var accelerationThreshold = 3.5f // Sensitivity for motion

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_POWER_DISCONNECTED) {
                if (_isChargerShieldArmed.value) {
                    Log.w(TAG, "Charger disconnected while shield armed! Sounding alarm.")
                    triggerAlarm("Charger was disconnected!")
                }
            }
        }
    }

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        sensorManager = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        vibrator = app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        val filter = IntentFilter(Intent.ACTION_POWER_DISCONNECTED)
        try {
            app.registerReceiver(powerReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering power receiver", e)
        }
    }

    fun toggleChargerShield(enable: Boolean? = null): String {
        val newState = enable ?: !_isChargerShieldArmed.value
        _isChargerShieldArmed.value = newState
        return if (newState) {
            "Anti-theft Charger Disconnect Shield ARMED. If anyone unplugs this phone, a loud alarm will sound."
        } else {
            stopAlarm()
            "Charger Disconnect Shield DISARMED."
        }
    }

    fun toggleMotionShield(enable: Boolean? = null): String {
        val newState = enable ?: !_isMotionShieldArmed.value
        _isMotionShieldArmed.value = newState
        val ctx = appContext ?: return "Not initialized."
        if (newState) {
            lastAcceleration = SensorManager.GRAVITY_EARTH
            currentAcceleration = SensorManager.GRAVITY_EARTH
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            return "Anti-theft Touch & Motion Alert ARMED. Keep phone still on surface. Alarm sounds if picked up or moved."
        } else {
            sensorManager?.unregisterListener(this)
            stopAlarm()
            return "Touch & Motion Alert DISARMED."
        }
    }

    fun triggerAlarm(reason: String) {
        _isAlarmSounding.value = true
        val ctx = appContext ?: return
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            alarmRingtone = RingtoneManager.getRingtone(ctx, alarmUri).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                play()
            }

            // Vibrate pattern
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 500, 200, 500, 200, 500)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 200, 500), 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alarm", e)
        }
    }

    fun stopAlarm(): String {
        _isAlarmSounding.value = false
        try {
            alarmRingtone?.stop()
            alarmRingtone = null
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping alarm", e)
        }
        return "Alarm stopped."
    }

    fun getStatus(): String {
        return "Anti-Theft Status: Charger Shield = ${if (_isChargerShieldArmed.value) "ACTIVE" else "OFF"}, Motion Guard = ${if (_isMotionShieldArmed.value) "ACTIVE" else "OFF"}, Alarm = ${if (_isAlarmSounding.value) "RINGING!" else "Quiet"}."
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        if (!_isMotionShieldArmed.value) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        lastAcceleration = currentAcceleration
        currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val delta = kotlin.math.abs(currentAcceleration - lastAcceleration)

        if (delta > accelerationThreshold) {
            Log.w(TAG, "Motion detected! delta=$delta. Sounding alarm.")
            triggerAlarm("Phone motion detected!")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
