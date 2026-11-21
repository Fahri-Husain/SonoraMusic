package com.fahrii.musicapp

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Mendeteksi goyangan ponsel lewat akselerometer. [onShake] dipanggil kalau ada beberapa hentakan
 * kuat berturut-turut dalam waktu singkat, lalu ada jeda [COOLDOWN_MS] supaya satu goyangan
 * tidak memicu beberapa kali. Berjalan hanya antara [start] dan [stop]; pemanggil bertanggung
 * jawab menghentikannya (mis. saat aplikasi keluar dari layar) supaya baterai tidak terkuras.
 */
class ShakeDetector(
    context: Context,
    private val onShake: () -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var windowStartMs = 0L
    private var lastSpikeMs = 0L
    private var lastTriggerMs = 0L
    private var spikeCount = 0

    /** False kalau perangkat tidak punya akselerometer. */
    val isSupported: Boolean get() = accelerometer != null

    fun start() {
        val sensor = accelerometer ?: return
        resetCounters()
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        resetCounters()
    }

    private fun resetCounters() {
        windowStartMs = 0L
        lastSpikeMs = 0L
        spikeCount = 0
    }

    override fun onSensorChanged(event: SensorEvent) {
        val gX = event.values[0] / SensorManager.GRAVITY_EARTH
        val gY = event.values[1] / SensorManager.GRAVITY_EARTH
        val gZ = event.values[2] / SensorManager.GRAVITY_EARTH
        // Diam = sekitar 1 g (gravitasi). Hanya hentakan yang jauh di atas itu yang dihitung.
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
        if (gForce < SHAKE_THRESHOLD_G) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerMs < COOLDOWN_MS) return
        // Satu ayunan menghasilkan beberapa sampel kuat berurutan; hitung sebagai satu hentakan
        if (now - lastSpikeMs < MIN_SPIKE_GAP_MS) return
        lastSpikeMs = now

        if (spikeCount == 0 || now - windowStartMs > SHAKE_WINDOW_MS) {
            windowStartMs = now
            spikeCount = 0
        }
        spikeCount++

        if (spikeCount >= REQUIRED_SPIKES) {
            lastTriggerMs = now
            resetCounters()
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        /** Kekuatan minimum satu hentakan (kelipatan gravitasi). Jalan kaki biasa jauh di bawah ini. */
        const val SHAKE_THRESHOLD_G = 2.7f

        /** Jumlah hentakan yang dibutuhkan dalam [SHAKE_WINDOW_MS]. */
        const val REQUIRED_SPIKES = 3

        const val SHAKE_WINDOW_MS = 1_000L
        const val MIN_SPIKE_GAP_MS = 120L
        const val COOLDOWN_MS = 1_500L
    }
}
