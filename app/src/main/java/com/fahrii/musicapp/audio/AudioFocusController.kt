package com.fahrii.musicapp.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Fokus audio level service. Semua ExoPlayer dibuat dengan handleAudioFocus = false, supaya
 * melepas pemutar lama tidak ikut melepas fokus (penyebab "musik berhenti antar lagu").
 */
class AudioFocusController(context: Context, private val callback: Callback) {

    interface Callback {
        fun onFocusLoss(transient: Boolean)
        fun onFocusGain()
        fun onDuck(ducked: Boolean)
    }

    private val am = context.getSystemService(AudioManager::class.java)
    private var held = false

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                held = true
                callback.onDuck(false)
                callback.onFocusGain()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                held = false
                callback.onFocusLoss(transient = false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                held = false
                callback.onFocusLoss(transient = true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> callback.onDuck(true)
        }
    }

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setOnAudioFocusChangeListener(listener)
        .setWillPauseWhenDucked(false)
        .build()

    /** Aman dipanggil berulang. Setelah kehilangan sementara, memanggil ini meminta fokus lagi. */
    fun request(): Boolean {
        if (held) return true
        held = am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return held
    }

    fun abandon() {
        am.abandonAudioFocusRequest(request)
        held = false
    }
}