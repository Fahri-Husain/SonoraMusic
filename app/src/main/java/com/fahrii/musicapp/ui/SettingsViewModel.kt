package com.fahrii.musicapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.fahrii.musicapp.data.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository.getInstance(app)

    val crossfadeEnabled: StateFlow<Boolean> = repo.crossfadeEnabled
    val crossfadeSeconds: StateFlow<Int> = repo.crossfadeSeconds

    fun setCrossfadeEnabled(enabled: Boolean) = repo.setCrossfadeEnabled(enabled)

    fun setCrossfadeSeconds(seconds: Int) = repo.setCrossfadeSeconds(seconds)
}
