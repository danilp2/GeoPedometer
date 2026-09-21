package com.geopedometer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.geopedometer.R
import com.geopedometer.data.sensor.processor.GravityRemover
import com.geopedometer.data.sensor.validator.AdaptivePeakStepValidator
import com.geopedometer.domain.model.StepDataState
import com.geopedometer.domain.model.StepEvent
import com.geopedometer.domain.model.UserProfile
import com.geopedometer.domain.repository.StepRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class PedometerService : Service(), SensorEventListener {

    @Inject lateinit var repository: StepRepository

    companion object {
        const val CHANNEL_ID = "pedometer_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.geopedometer.START"
        const val ACTION_STOP = "com.geopedometer.STOP"

        private val _serviceState = MutableStateFlow<StepDataState>(StepDataState.Idle)
        val serviceState: StateFlow<StepDataState> = _serviceState.asStateFlow()

        @Volatile private var sessionSteps: Long = 0
        @Volatile private var sessionDistanceMeters: Double = 0.0
        @Volatile private var sessionStartTimeMs: Long = 0
    }

    private var sensorManager: SensorManager? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gravityRemover = GravityRemover()
    private val stepValidator = AdaptivePeakStepValidator()
    private val linearAccel = FloatArray(3)
    private var userProfile = UserProfile()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        serviceScope.launch {
            userProfile = repository.getProfile()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startSensors()
        sessionStartTimeMs = SystemClock.elapsedRealtime()
        _serviceState.value = StepDataState.Counting(
            steps = 0,
            distanceMeters = 0.0,
            distanceKm = 0.0,
            activeTimeSeconds = 0,
            stepLengthMeters = userProfile.stepLengthMeters,
            todayDate = LocalDate.now().toString()
        )

        serviceScope.launch {
            while (isActive) {
                delay(30_000)
            }
        }
        serviceScope.launch { repository.purgeOldRawEvents() }

        return START_STICKY
    }

    private fun startSensors() {
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            _serviceState.value = StepDataState.Error("Акселерометр недоступен", false)
            return
        }
        sensorManager?.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL,
            500_000
        )
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        linearAccel[0] = event.values[0]
        linearAccel[1] = event.values[1]
        linearAccel[2] = event.values[2]

        gravityRemover.removeGravity(linearAccel)
        val magnitude = gravityRemover.computeMagnitude(linearAccel)

        val stepEvent: StepEvent? = stepValidator.validate(magnitude, event.timestamp)
        if (stepEvent != null) onStepDetected(stepEvent)
    }

    private fun onStepDetected(event: StepEvent) {
        sessionSteps++
        sessionDistanceMeters += userProfile.stepLengthMeters

        val activeSec = (SystemClock.elapsedRealtime() - sessionStartTimeMs) / 1000
        _serviceState.value = StepDataState.Counting(
            steps = sessionSteps,
            distanceMeters = sessionDistanceMeters,
            distanceKm = sessionDistanceMeters / 1000.0,
            activeTimeSeconds = activeSec,
            stepLengthMeters = userProfile.stepLengthMeters,
            todayDate = LocalDate.now().toString()
        )

        serviceScope.launch(Dispatchers.IO) {
            try {
                repository.recordStep(event.timestampMs, event.confidence)
            } catch (e: SecurityException) {
                _serviceState.value = StepDataState.Error("Разрешение отозвано", true)
            }
        }

        if (sessionSteps % 10 == 0L) updateNotification()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        serviceScope.cancel()
        gravityRemover.reset()
        stepValidator.reset()
        _serviceState.value = StepDataState.Idle
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val state = _serviceState.value
        val text = when (state) {
            is StepDataState.Counting ->
                getString(R.string.notification_steps, state.steps.toInt(), String.format("%.2f", state.distanceKm))
            else -> getString(R.string.notification_idle)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }
}