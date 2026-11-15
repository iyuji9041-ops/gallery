package com.photo.gallery

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.photo.gallery.service.VaultMonitorService
import com.photo.gallery.ui.screens.GalleryAppRoot
import com.photo.gallery.ui.theme.MyApplicationTheme
import com.photo.gallery.ui.viewmodel.GalleryViewModel

class MainActivity : FragmentActivity() {
    private val viewModel: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start background file system listener for Hidden Vault directory
        VaultMonitorService.start(this)

        setContent {
            MyApplicationTheme(darkTheme = true) {
                GalleryAppRoot(viewModel = viewModel)
            }
        }
    }
}

