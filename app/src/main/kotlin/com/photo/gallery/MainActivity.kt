package com.photo.gallery

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.photo.gallery.ui.screens.GalleryAppRoot
import com.photo.gallery.ui.theme.MyApplicationTheme
import com.photo.gallery.ui.viewmodel.GalleryTab
import com.photo.gallery.ui.viewmodel.GalleryViewModel
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val viewModel: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Dynamic FLAG_SECURE management (Problem 5):
        // Automatically applies FLAG_SECURE when the vault or a locked item is on-screen,
        // or app-wide when the user enables the setup toggle.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val isVaultActive = state.currentTab == GalleryTab.VAULT ||
                        state.activeViewerItem?.isLocked == true ||
                        (state.isVaultUnlocked && state.currentTab == GalleryTab.VAULT)
                    val shouldSecure = state.securitySettings.secureModeAppWide || isVaultActive

                    if (shouldSecure) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }

        setContent {
            MyApplicationTheme(darkTheme = true) {
                GalleryAppRoot(viewModel = viewModel)
            }
        }
    }
}
