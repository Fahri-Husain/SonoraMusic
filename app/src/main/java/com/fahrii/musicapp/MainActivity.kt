package com.fahrii.musicapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fahrii.musicapp.data.ArtistNames
import com.fahrii.musicapp.data.SettingsRepository
import com.fahrii.musicapp.ui.AppRoot
import com.fahrii.musicapp.ui.AppTypography
import com.fahrii.musicapp.ui.LibraryViewModel
import com.fahrii.musicapp.ui.PlayerViewModel
import com.fahrii.musicapp.ui.SonoraColorScheme
import com.fahrii.musicapp.ui.UpdatePrompt
import com.fahrii.musicapp.ui.UpdateViewModel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.fahrii.musicapp.data.getInstance

class MainActivity : ComponentActivity() {

    // Instance yang sama dengan yang dipakai Compose, karena pemiliknya sama-sama Activity ini
    private val playerViewModel: PlayerViewModel by viewModels()

    private val shakeDetector by lazy {
        ShakeDetector(this) {
            val s = playerViewModel.state.value
            // Hanya saat musik sedang diputar dan masih ada lagu berikutnya; goyang tidak boleh
            // menyalakan musik yang sedang dijeda
            if (s.isPlaying && s.hasNext) {
                playerViewModel.next()
                window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        requestNotificationPermissionIfNeeded()
        // Muat nama artis kolaborasi dari file sebelum layar pertama tampil, supaya tidak ada delay
        ArtistNames.init(applicationContext.filesDir)

        // Sensor hanya aktif selagi aplikasi terbuka (RESUMED) dan pengaturannya menyala.
        // collectLatest membatalkan blok lama saat pengaturan berubah, jadi sensor ikut berhenti.
        val settings = SettingsRepository.getInstance(applicationContext)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                settings.shakeToSkip.collectLatest { enabled ->
                    if (enabled && shakeDetector.isSupported) {
                        shakeDetector.start()
                        try {
                            awaitCancellation()
                        } finally {
                            shakeDetector.stop()
                        }
                    }
                }
            }
        }

        setContent {
            MaterialTheme(
                colorScheme = SonoraColorScheme,
                typography = AppTypography
            ) {
                val libraryViewModel: LibraryViewModel = viewModel()
                AppRoot(playerViewModel, libraryViewModel)
                // Muncul kalau ada rilis baru di GitHub yang lebih baru dari versi terpasang
                val updateViewModel: UpdateViewModel = viewModel()
                UpdatePrompt(updateViewModel)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}