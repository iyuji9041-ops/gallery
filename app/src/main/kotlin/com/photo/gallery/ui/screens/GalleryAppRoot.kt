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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

    // Storage Permissions Request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val anyGranted = permissions.values.any { it }
        if (anyGranted) {
            viewModel.refreshMedia()
        }
    }

    // Check & request permissions on startup
    LaunchedEffect(Unit) {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val needed = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    // Snackbar notifications
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToastMessage()
        }
    }

    // SYSTEM BACK BUTTON HANDLING (bug fix).
    //
    // This screen has no NavController back stack - "screens" are just state flags
    // (activeViewerItem, isSlideshowActive, isSelectionMode) toggled on the single
    // root Composable. Without an explicit BackHandler, Compose has nothing to
    // intercept the system back press/gesture with, so it falls through to the
    // Activity's default behavior and finishes the Activity entirely - which is
    // exactly the reported bug ("back from photo viewer closes the whole app
    // instead of returning to the gallery grid").
    //
    // Each BackHandler below is only `enabled` while its matching state is true, and
    // they're declared most-specific-first so exactly one applies at a time:
    // closing the photo viewer takes priority over exiting selection mode, etc.
    BackHandler(enabled = uiState.activeViewerItem != null) {
        viewModel.closeViewer()
    }
    BackHandler(enabled = uiState.activeViewerItem == null && uiState.isSlideshowActive) {
        viewModel.stopSlideshow()
    }
    BackHandler(enabled = uiState.activeViewerItem == null && !uiState.isSlideshowActive && uiState.isSelectionMode) {
        viewModel.exitSelectionMode()
    }
    // NOTE: no BackHandler is registered for "vault unlocked" on purpose here -
    // leaving the vault unlocked when backing out of the app entirely is a separate
    // security-UX decision (auto-lock-on-background) tracked in the security-features
    // work, not part of this navigation-crash fix.

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
        },
        bottomBar = {
            if (uiState.activeViewerItem == null && !uiState.isSlideshowActive && !uiState.isSelectionMode) {
                BentoNavigationBar(
                    currentTab = uiState.currentTab,
                    onTabSelected = { viewModel.selectTab(it) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                        isMonitoringActive = uiState.isVaultMonitorActive,
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
                        onRequestChangeVaultCode = { viewModel.requestChangeVaultCode() }
                    )
                }
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

        // One-time Vault-access tutorial: shown once (tracked via
        // securitySettings.hasSeenVaultTutorial in DataStore) to explain that the
        // hidden vault - now that it has no labeled bottom-nav tab - is opened by
        // typing the secret code into the search bar instead.
        if (!uiState.securitySettings.hasSeenVaultTutorial) {
            VaultTutorialDialog(
                secretCode = uiState.securitySettings.vaultSecretCode,
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
                isBiometricAvailable = uiState.isBiometricAvailable,
                biometricsEnabled = uiState.securitySettings.biometricsEnabled,
                biometricAuthManager = viewModel.biometricAuthManager,
                onVerifyPin = { pin, callback -> viewModel.verifyPin(pin, callback) },
                onSetPin = { pin -> viewModel.setMasterPin(pin) },
                onSuccess = { viewModel.onAuthSuccess(auth.reason, auth.targetItem) },
                onDismiss = { viewModel.dismissAuthDialog() }
            )
        }

        // Vault Access Code editor - only reachable AFTER requestChangeVaultCode()'s
        // auth dialog succeeds (see onAuthSuccess -> AuthReason.CHANGE_VAULT_CODE).
        if (uiState.showVaultCodeEditor) {
            VaultCodeEditDialog(
                currentCode = uiState.securitySettings.vaultSecretCode,
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
 * Bento Navigation Bar matching Design HTML:
 * Height 80dp (h-20), bg-[#1C1B1F], border-t border-[#44474E],
 * active pill bg-[#D0BCFF20] text-[#D0BCFF], uppercase tracking-widest labels.
 */
@Composable
fun BentoNavigationBar(
    currentTab: GalleryTab,
    onTabSelected: (GalleryTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BentoSurface)
            .border(width = 1.dp, color = BentoBorder)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(72.dp)
            .testTag("gallery_bottom_nav"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BentoNavItem(
                icon = Icons.Default.PhotoLibrary,
                label = "Photos",
                isSelected = currentTab == GalleryTab.TIMELINE,
                onClick = { onTabSelected(GalleryTab.TIMELINE) },
                tag = "nav_tab_photos"
            )

            BentoNavItem(
                icon = Icons.Default.Folder,
                label = "Albums",
                isSelected = currentTab == GalleryTab.ALBUMS,
                onClick = { onTabSelected(GalleryTab.ALBUMS) },
                tag = "nav_tab_albums"
            )

            // Vault tab intentionally REMOVED from here (user request: a labeled
            // "VAULT" tab makes the hidden vault's existence obvious to anyone glancing
            // at the app). It's still fully reachable - by typing the secret code
            // (default "\vault", changeable in Settings) into the search bar. See
            // GalleryViewModel.setSearchQuery() and the VaultTutorialDialog below.

            BentoNavItem(
                icon = Icons.Default.Security,
                label = "Setup",
                isSelected = currentTab == GalleryTab.SETTINGS,
                onClick = { onTabSelected(GalleryTab.SETTINGS) },
                tag = "nav_tab_settings"
            )
        }
    }
}

@Composable
private fun BentoNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Active pill container
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (isSelected) BentoAccentSubtle else androidx.compose.ui.graphics.Color.Transparent)
                .padding(horizontal = if (isSelected) 18.dp else 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) BentoAccent else BentoTextSecondary.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = label.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = if (isSelected) BentoAccent else BentoTextSecondary.copy(alpha = 0.7f)
        )
    }
}

/**
 * One-time explainer shown the first time the app is opened after the Vault tab was
 * removed from bottom-nav. Explains the new access method (typing the secret code
 * into search) and where to change that code later. Dismissing it permanently marks
 * securitySettings.hasSeenVaultTutorial = true via onDismiss, so it never reappears.
 */
@Composable
private fun VaultTutorialDialog(secretCode: String, onDismiss: () -> Unit) {
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
                text = "Your Hidden Vault moved",
                color = BentoTextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "For extra privacy, the Vault no longer has its own tab at the bottom of the screen.",
                    color = BentoTextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "To open it, type this into the search bar:",
                    color = BentoTextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BentoSurfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = secretCode,
                        color = BentoAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "You can change this code anytime in Setup \u2192 Vault Access Code.",
                    color = BentoTextSecondary,
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Got it", color = BentoAccent, fontWeight = FontWeight.Bold)
            }
        }
    )
}
