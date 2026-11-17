package com.photo.gallery.ui.screens

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import com.photo.gallery.ui.theme.BentoTextTertiary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photo.gallery.ui.components.AuthenticationDialog
import com.photo.gallery.ui.components.ExifBottomSheet
import com.photo.gallery.ui.theme.BentoAccent
import com.photo.gallery.ui.theme.BentoAccentContainer
import com.photo.gallery.ui.theme.BentoAccentSubtle
import com.photo.gallery.ui.theme.BentoBg
import com.photo.gallery.ui.theme.BentoBorder
import com.photo.gallery.ui.theme.BentoSurface
import com.photo.gallery.ui.theme.BentoSurfaceCard
import com.photo.gallery.ui.theme.BentoSurfaceElevated
import com.photo.gallery.ui.theme.BentoTextPrimary
import com.photo.gallery.ui.theme.BentoTextSecondary
import com.photo.gallery.ui.theme.BentoTextTertiary
import com.photo.gallery.ui.viewmodel.GalleryTab
import com.photo.gallery.ui.viewmodel.GalleryViewModel

@Composable
fun GalleryAppRoot(viewModel: GalleryViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Scoped Storage Deletion IntentSender launcher (Android 11+)
    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onIntentSenderHandled(result.resultCode == Activity.RESULT_OK)
    }

    // Launch IntentSender if ViewModel requests it
    LaunchedEffect(uiState.pendingDeleteRequest) {
        uiState.pendingDeleteRequest?.let { request ->
            intentSenderLauncher.launch(request)
        }
    }

    // Query storage permission state
    fun queryCurrentPermissionState(): com.photo.gallery.ui.viewmodel.StoragePermissionState {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val hasImages = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val hasVideos = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val hasSelected = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            if (hasImages && hasVideos) {
                com.photo.gallery.ui.viewmodel.StoragePermissionState.GRANTED
            } else if (hasSelected) {
                com.photo.gallery.ui.viewmodel.StoragePermissionState.PARTIAL
            } else {
                com.photo.gallery.ui.viewmodel.StoragePermissionState.DENIED
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasImages = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val hasVideos = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            if (hasImages && hasVideos) com.photo.gallery.ui.viewmodel.StoragePermissionState.GRANTED else com.photo.gallery.ui.viewmodel.StoragePermissionState.DENIED
        } else {
            val hasStorage = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            if (hasStorage) com.photo.gallery.ui.viewmodel.StoragePermissionState.GRANTED else com.photo.gallery.ui.viewmodel.StoragePermissionState.DENIED
        }
    }

    // Storage Permissions Request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val state = queryCurrentPermissionState()
        viewModel.updatePermissionState(state)
    }

    // Check & request permissions on startup
    LaunchedEffect(Unit) {
        val state = queryCurrentPermissionState()
        viewModel.updatePermissionState(state)
        if (state == com.photo.gallery.ui.viewmodel.StoragePermissionState.DENIED) {
            val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                listOf(
                    android.Manifest.permission.READ_MEDIA_IMAGES,
                    android.Manifest.permission.READ_MEDIA_VIDEO,
                    android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(
                    android.Manifest.permission.READ_MEDIA_IMAGES,
                    android.Manifest.permission.READ_MEDIA_VIDEO
                )
            } else {
                listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // Snackbar notifications
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToastMessage()
        }
    }

    // SYSTEM BACK BUTTON HANDLING (Problem 8)
    // Back from Albums, Setup or the vault returns to Photos tab first.
    // Leaving the vault with back must lock it.
    BackHandler(enabled = uiState.activeViewerItem != null) {
        viewModel.closeViewer()
    }
    BackHandler(enabled = uiState.activeViewerItem == null && uiState.isSlideshowActive) {
        viewModel.stopSlideshow()
    }
    BackHandler(enabled = uiState.activeViewerItem == null && !uiState.isSlideshowActive && uiState.isSelectionMode) {
        viewModel.exitSelectionMode()
    }
    BackHandler(enabled = uiState.currentTab != GalleryTab.TIMELINE && uiState.activeViewerItem == null && !uiState.isSlideshowActive && !uiState.isSelectionMode) {
        if (uiState.currentTab == GalleryTab.VAULT) {
            viewModel.lockVault()
        }
        viewModel.selectTab(GalleryTab.TIMELINE)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(BentoBg),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.activeViewerItem == null && !uiState.isSlideshowActive && !uiState.isSelectionMode) {
                BentoHeader(
                    onSearchClick = {
                        viewModel.selectTab(GalleryTab.TIMELINE)
                        viewModel.toggleSearchExpanded()
                    },
                    onSecurityClick = {
                        viewModel.selectTab(GalleryTab.SETTINGS)
                    },
                    isLocked = uiState.securitySettings.deletionLockEnabled
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            when (uiState.currentTab) {
                GalleryTab.TIMELINE -> {
                    MainGridScreen(
                        mediaItems = uiState.mediaItems,
                        selectedAlbum = uiState.selectedAlbum,
                        filter = uiState.filter,
                        searchQuery = uiState.searchQuery,
                        isSearchExpanded = uiState.isSearchExpanded,
                        dateFilter = uiState.dateFilter,
                        folderFilter = uiState.folderFilter,
                        availableFolders = uiState.availableFolders,
                        isSelectionMode = uiState.isSelectionMode,
                        selectedItemIds = uiState.selectedItemIds,
                        isLoading = uiState.isLoading,
                        permissionState = uiState.permissionState,
                        onRequestPermission = {
                            val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                listOf(
                                    android.Manifest.permission.READ_MEDIA_IMAGES,
                                    android.Manifest.permission.READ_MEDIA_VIDEO,
                                    android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                                )
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                listOf(
                                    android.Manifest.permission.READ_MEDIA_IMAGES,
                                    android.Manifest.permission.READ_MEDIA_VIDEO
                                )
                            } else {
                                listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            permissionLauncher.launch(permissionsToRequest.toTypedArray())
                        },
                        onOpenSettings = {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                        },
                        onFilterChange = { viewModel.setFilter(it) },
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        onToggleSearch = { viewModel.toggleSearchExpanded() },
                        onDateFilterChange = { viewModel.setDateFilter(it) },
                        onFolderFilterChange = { viewModel.setFolderFilter(it) },
                        onClearAlbumFilter = { viewModel.selectAlbum(null) },
                        onItemClick = { viewModel.openViewer(it) },
                        onItemLongClick = { viewModel.enterSelectionMode(it.id) },
                        onToggleSelectionMode = { if (it) viewModel.enterSelectionMode() else viewModel.exitSelectionMode() },
                        onToggleItemSelection = { viewModel.toggleItemSelection(it) },
                        onSelectAll = { viewModel.selectAll() },
                        onDeselectAll = { viewModel.deselectAll() },
                        onBulkLock = { viewModel.bulkLockSelected() },
                        onBulkVault = { viewModel.bulkMoveToVault() },
                        onBulkDelete = { viewModel.bulkDeleteSelected() },
                        onBulkShare = {
                            val selectedItems = uiState.mediaItems.filter { m -> uiState.selectedItemIds.contains(m.id) }
                            com.photo.gallery.util.ShareHelper.shareMedia(context, selectedItems)
                        },
                        onStartSlideshow = { viewModel.startSlideshow() }
                    )
                }
                GalleryTab.ALBUMS -> {
                    AlbumScreen(
                        albums = uiState.albums,
                        onAlbumClick = { albumName ->
                            viewModel.selectAlbum(albumName)
                            viewModel.selectTab(GalleryTab.TIMELINE)
                        },
                        onToggleAlbumLock = { albumName ->
                            viewModel.toggleAlbumLock(albumName)
                        }
                    )
                }
                GalleryTab.VAULT -> {
                    HiddenVaultScreen(
                        isVaultUnlocked = uiState.isVaultUnlocked,
                        hiddenItems = uiState.hiddenVaultItems,
                        isMonitoringActive = uiState.isVaultEncrypted,
                        onRequestUnlock = { viewModel.requestVaultUnlock() },
                        onLockVault = { viewModel.lockVault() },
                        onRestoreItem = { viewModel.restoreFromVault(it) },
                        onDeleteItem = { viewModel.deleteFromVault(it) }
                    )
                }
                GalleryTab.SETTINGS -> {
                    SettingsScreen(
                        securitySettings = uiState.securitySettings,
                        isBiometricAvailable = uiState.isBiometricAvailable,
                        onSetPinClick = { viewModel.requestChangePin() },
                        onToggleBiometrics = { viewModel.setBiometricsEnabled(it) },
                        onToggleDeletionLock = { viewModel.requestToggleDeletionEngine(it) },
                        onRequestChangeVaultCode = { viewModel.requestChangeVaultCode() },
                        onToggleSecureModeAppWide = { viewModel.setSecureModeAppWide(it) },
                        onSelectAutoLockDelay = { viewModel.setAutoLockDelay(it) }
                    )
                }
            }

            // Floating Pill Navigation Bar (truly floating with transparent backdrop over content)
            if (uiState.activeViewerItem == null && !uiState.isSlideshowActive && !uiState.isSelectionMode) {
                PillNavigationBar(
                    currentTab = uiState.currentTab,
                    onTabSelected = { viewModel.selectTab(it) },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // Full Screen Slideshow
        AnimatedVisibility(
            visible = uiState.isSlideshowActive,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SlideshowScreen(
                items = uiState.slideshowItems,
                startIndex = uiState.slideshowStartIndex,
                onClose = { viewModel.stopSlideshow() }
            )
        }

        // Full Screen Image Viewer
        AnimatedVisibility(
            visible = uiState.activeViewerItem != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            uiState.activeViewerItem?.let { item ->
                val startIndex = uiState.mediaItems.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                ImageViewerScreen(
                    items = uiState.mediaItems,
                    initialIndex = startIndex,
                    onBack = { viewModel.closeViewer() },
                    onToggleLock = { viewModel.toggleMediaLock(it) },
                    onMoveToVault = { viewModel.moveToVault(it) },
                    onShowExif = { viewModel.toggleExifDialog(true) },
                    onDeleteRequest = { viewModel.requestDeleteMedia(it) },
                    onPageChanged = { viewModel.onViewerPageChanged(it) }
                )
            }
        }

        // EXIF Bottom Sheet
        if (uiState.showExifDialog && uiState.activeViewerItem != null) {
            ExifBottomSheet(
                item = uiState.activeViewerItem!!,
                exifData = uiState.activeExifData,
                onDismiss = { viewModel.toggleExifDialog(false) }
            )
        }

        // One-time Vault-access tutorial: lets user set their own secret search-bar code (Problem 9)
        if (!uiState.securitySettings.hasSeenVaultTutorial) {
            VaultTutorialDialog(
                onSaveSecretCode = { code ->
                    viewModel.setVaultSecretCode(code)
                },
                onDismiss = { viewModel.dismissVaultTutorial() }
            )
        }

        // Authentication Dialog (Flagship Deletion Lock & Vault Unlock)
        if (uiState.authDialogState != null) {
            val auth = uiState.authDialogState!!
            AuthenticationDialog(
                reason = auth.reason,
                title = auth.title,
                description = auth.description,
                hasPinSet = uiState.securitySettings.hasPinSet,
                configuredPinLength = uiState.securitySettings.pinLength,
                isBiometricAvailable = uiState.isBiometricAvailable,
                biometricsEnabled = uiState.securitySettings.biometricsEnabled,
                biometricCryptoObject = auth.cryptoObject,
                biometricAuthManager = viewModel.biometricAuthManager,
                onVerifyPin = { pin, callback -> viewModel.verifyPin(pin, callback) },
                onSetPin = { pin -> viewModel.setMasterPin(pin) },
                onBiometricSuccess = { result -> viewModel.onBiometricAuthSuccess(result) },
                onSuccess = { viewModel.onAuthSuccess(auth.reason, auth.targetItem) },
                onDismiss = { viewModel.dismissAuthDialog() }
            )
        }

        // Vault Access Code editor - only reachable AFTER requestChangeVaultCode()'s auth succeeds
        if (uiState.showVaultCodeEditor) {
            VaultCodeEditDialog(
                onConfirm = { newCode ->
                    viewModel.setVaultSecretCode(newCode)
                    viewModel.dismissVaultCodeEditor()
                },
                onDismiss = { viewModel.dismissVaultCodeEditor() }
            )
        }
    }
}

/**
 * Bento Header matching Design HTML:
 * Header with "Gallery" title, "PROTECTED BY VAULTCORE" uppercase tracking subtitle,
 * 48x48 rounded-full search button, and 48x48 rounded-full lock button.
 */
@Composable
fun BentoHeader(
    onSearchClick: () -> Unit,
    onSecurityClick: () -> Unit,
    isLocked: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BentoBg)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Gallery",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = BentoTextPrimary,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "PROTECTED BY VAULTCORE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = BentoTextSecondary,
                letterSpacing = 2.sp,
                fontSize = 10.sp
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Search button: 48x48 rounded-full bg-[#1C1B1F] border border-[#44474E]
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(BentoSurface)
                    .border(1.dp, BentoBorder, CircleShape)
                    .clickable { onSearchClick() }
                    .testTag("bento_header_search_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = BentoTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Security Lock button: 48x48 rounded-full bg-[#D0BCFF] text-[#381E72]
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(BentoAccent)
                    .clickable { onSecurityClick() }
                    .testTag("bento_header_lock_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Security Settings",
                    tint = BentoAccentContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}



/**
 * One-time setup tutorial (Problem 9):
 * Lets the user choose their own secret phrase to trigger the Hidden Vault from search.
 * Never displays default or previous code in plaintext.
 */
@Composable
private fun VaultTutorialDialog(
    onSaveSecretCode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var codeInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BentoSurface,
        icon = {
            Icon(
                imageVector = Icons.Default.VisibilityOff,
                contentDescription = null,
                tint = BentoAccent
            )
        },
        title = {
            Text(
                text = "Setup Hidden Vault",
                color = BentoTextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "For maximum privacy, the Vault is hidden behind the search bar. Choose your own secret access phrase:",
                    color = BentoTextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    singleLine = true,
                    placeholder = { Text("e.g. myvault, private123", color = BentoTextTertiary) },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = BentoTextPrimary,
                        unfocusedTextColor = BentoTextPrimary,
                        focusedIndicatorColor = BentoAccent,
                        unfocusedIndicatorColor = BentoTextTertiary,
                        cursorColor = BentoAccent
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("vault_tutorial_code_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Typing this phrase in search opens the vault. Stored securely as a cryptographic hash.",
                    color = BentoTextSecondary,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = codeInput.trim()
                    if (trimmed.isNotEmpty()) {
                        onSaveSecretCode(trimmed)
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text(
                    text = if (codeInput.isNotBlank()) "Save Phrase" else "Skip",
                    color = BentoAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}
