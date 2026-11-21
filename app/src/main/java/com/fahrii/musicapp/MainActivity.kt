package com.fahrii.musicapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fahrii.musicapp.ui.AppRoot
import com.fahrii.musicapp.ui.AppTypography
import com.fahrii.musicapp.ui.LibraryViewModel
import com.fahrii.musicapp.ui.PlayerViewModel
import com.fahrii.musicapp.ui.SonoraColorScheme
import com.fahrii.musicapp.ui.UpdatePrompt
import com.fahrii.musicapp.ui.UpdateViewModel

class MainActivity : ComponentActivity() {

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
        com.fahrii.musicapp.data.ArtistNames.init(applicationContext)

        setContent {
            MaterialTheme(
                colorScheme = SonoraColorScheme,
                typography = AppTypography
            ) {
                val playerViewModel: PlayerViewModel = viewModel()
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