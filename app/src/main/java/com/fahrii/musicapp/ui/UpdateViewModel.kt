package com.fahrii.musicapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fahrii.musicapp.data.ReleaseInfo
import com.fahrii.musicapp.data.UpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Mengecek rilis terbaru di GitHub saat aplikasi dibuka; [update] terisi kalau ada versi yang lebih baru. */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val _update = MutableStateFlow<ReleaseInfo?>(null)
    val update: StateFlow<ReleaseInfo?> = _update.asStateFlow()

    init {
        check()
    }

    private fun check() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val local = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: return@launch
            // Gagal koneksi atau belum ada rilis: diam saja, tidak mengganggu
            val release = runCatching { UpdateRepository.latestRelease() }.getOrNull() ?: return@launch
            if (UpdateRepository.isNewer(release.tag, local)) _update.value = release
        }
    }

    fun dismiss() {
        _update.value = null
    }
}
