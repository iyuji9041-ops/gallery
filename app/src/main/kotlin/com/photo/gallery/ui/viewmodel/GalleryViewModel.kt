package com.photo.gallery.ui.viewmodel

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.photo.gallery.GalleryApplication
import com.photo.gallery.data.local.AppDatabase
import com.photo.gallery.data.local.AutoLockDelay
import com.photo.gallery.data.local.PinVerifyResult
import com.photo.gallery.data.local.SecurityPreferences
import com.photo.gallery.data.local.SecuritySettings
import com.photo.gallery.data.local.model.HiddenMediaEntity
import com.photo.gallery.data.local.model.LockedMediaEntity
import com.photo.gallery.data.mediastore.DeletionResult
import com.photo.gallery.data.mediastore.ExifHelper
import com.photo.gallery.data.mediastore.MediaDeletionHandler
import com.photo.gallery.data.mediastore.MediaStoreFetcher
import com.photo.gallery.data.model.AlbumItem
import com.photo.gallery.data.model.ExifData
import com.photo.gallery.data.model.MediaItem
import com.photo.gallery.data.repository.SecurityRepository
import com.photo.gallery.data.vault.VaultManager
import com.photo.gallery.data.vault.VaultMoveResult
import com.photo.gallery.security.BiometricAuthManager
import com.photo.gallery.security.crypto.VaultCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class StoragePermissionState {
    GRANTED,
    PARTIAL, // Android 14+ READ_MEDIA_VISUAL_USER_SELECTED
    DENIED
}

sealed class PendingIntentAction {
    data class DeleteSingle(val item: MediaItem) : PendingIntentAction()
    data class DeleteBulk(val items: List<MediaItem>) : PendingIntentAction()
    data class MoveToVaultSingle(val item: MediaItem, val entity: HiddenMediaEntity) : PendingIntentAction()
    data class MoveToVaultBulk(val items: List<MediaItem>, val entities: List<HiddenMediaEntity>) : PendingIntentAction()
}

enum class GalleryTab {
    TIMELINE, ALBUMS, VAULT, SETTINGS
}

enum class MediaFilter {
    ALL, PHOTOS, VIDEOS, LOCKED
}

enum class DateFilterPeriod(val label: String) {
    ALL("All Dates"),
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year")
}

enum class AuthReason {
    DELETION_LOCK, UNLOCK_VAULT, SET_PIN, CHANGE_PIN, BULK_DELETE,
    TOGGLE_DELETION_LOCK,
    TOGGLE_ALBUM_DELETION_LOCK,
    TOGGLE_BULK_DELETION_LOCK,
    DISABLE_DELETION_ENGINE,
    CHANGE_VAULT_CODE
}

data class AuthDialogState(
    val reason: AuthReason,
    val title: String,
    val description: String,
    val targetItem: MediaItem? = null,
    val targetAlbum: String? = null,
    val targetItems: List<MediaItem> = emptyList(),
    val cryptoObject: BiometricPrompt.CryptoObject? = null
)

data class GalleryUiState(
    val mediaItems: List<MediaItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val availableFolders: List<String> = emptyList(),
    val selectedAlbum: String? = null,
    val currentTab: GalleryTab = GalleryTab.TIMELINE,
    val filter: MediaFilter = MediaFilter.ALL,
    val searchQuery: String = "",
    val isSearchExpanded: Boolean = false,
    val dateFilter: DateFilterPeriod = DateFilterPeriod.ALL,
    val folderFilter: String? = null,
    val isSelectionMode: Boolean = false,
    val selectedItemIds: Set<Long> = emptySet(),
    val isSlideshowActive: Boolean = false,
    val slideshowItems: List<MediaItem> = emptyList(),
    val slideshowStartIndex: Int = 0,
    val isLoading: Boolean = false,
    val activeViewerItem: MediaItem? = null,
    val activeExifData: ExifData? = null,
    val showExifDialog: Boolean = false,
    val showVaultCodeEditor: Boolean = false,
    val isVaultUnlocked: Boolean = false,
    val hiddenVaultItems: List<HiddenMediaEntity> = emptyList(),
    val securitySettings: SecuritySettings = SecuritySettings(),
    val authDialogState: AuthDialogState? = null,
    val pendingDeleteRequest: IntentSenderRequest? = null,
    val toastMessage: String? = null,
    val isBiometricAvailable: Boolean = false,
    val isVaultEncrypted: Boolean = true,
    val permissionState: StoragePermissionState = StoragePermissionState.GRANTED
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as? GalleryApplication
    val vaultCryptoManager: VaultCryptoManager = app?.cryptoManager ?: VaultCryptoManager(application)
    private val database = AppDatabase.getInstance(application)
    private val securityDao = database.securityDao()
    private val securityPreferences = SecurityPreferences(application)
    private val vaultManager = VaultManager(application, securityDao, vaultCryptoManager)
    val securityRepository = SecurityRepository(securityDao, securityPreferences, vaultManager)
    private val mediaFetcher = MediaStoreFetcher(application)
    private val deletionHandler = MediaDeletionHandler(application)
    val biometricAuthManager = BiometricAuthManager(application)

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private var allMediaCache: List<MediaItem> = emptyList()
    private var autoLockJob: Job? = null
    private var mediaObserver: ContentObserver? = null
    private var debounceRefreshJob: Job? = null
    private var pendingIntentSenderAction: PendingIntentAction? = null

    init {
        try {
            _uiState.update {
                it.copy(isBiometricAvailable = biometricAuthManager.isBiometricAvailable())
            }
        } catch (_: Throwable) {}

        observeDataSources()
        registerMediaStoreObserver()
        refreshMedia()

        // Reconcile vault on startup (Problem 8)
        viewModelScope.launch(Dispatchers.IO) {
            securityRepository.reconcileVault()
        }

        // ProcessLifecycleOwner auto-lock observer (Problem 4)
        setupAutoLockLifecycleObserver()
    }

    private fun setupAutoLockLifecycleObserver() {
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    val delaySetting = _uiState.value.securitySettings.autoLockDelay
                    if (delaySetting == AutoLockDelay.IMMEDIATELY) {
                        lockVault()
                    } else {
                        autoLockJob?.cancel()
                        autoLockJob = viewModelScope.launch {
                            delay(delaySetting.seconds * 1000L)
                            lockVault()
                        }
                    }
                }

                override fun onStart(owner: LifecycleOwner) {
                    autoLockJob?.cancel()
                    autoLockJob = null
                }

                override fun onResume(owner: LifecycleOwner) {
                    refreshMedia()
                }
            })
        } catch (_: Exception) {}
    }

    private fun registerMediaStoreObserver() {
        if (mediaObserver != null) return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                debounceRefreshJob?.cancel()
                debounceRefreshJob = viewModelScope.launch {
                    delay(500)
                    refreshMedia()
                }
            }
        }
        mediaObserver = observer
        val resolver = getApplication<Application>().contentResolver
        try {
            resolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            resolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        mediaObserver?.let {
            try {
                getApplication<Application>().contentResolver.unregisterContentObserver(it)
            } catch (_: Exception) {}
            mediaObserver = null
        }
    }

    fun updatePermissionState(state: StoragePermissionState) {
        _uiState.update { it.copy(permissionState = state) }
        if (state != StoragePermissionState.DENIED) {
            refreshMedia()
        }
    }

    private fun observeDataSources() {
        // Observe Security Settings
        viewModelScope.launch {
            securityRepository.securitySettings.collect { settings ->
                _uiState.update { it.copy(securitySettings = settings) }
            }
        }

        // Observe Hidden Vault Items
        viewModelScope.launch {
            securityRepository.allHiddenMedia.collect { hiddenList ->
                _uiState.update { it.copy(hiddenVaultItems = hiddenList) }
            }
        }

        // Observe Locked Items to update lock flags reactively
        viewModelScope.launch {
            combine(
                securityRepository.allLockedMedia,
                securityRepository.lockedAlbumNames
            ) { lockedList, lockedAlbums ->
                Pair(lockedList.map { it.mediaPath }.toSet(), lockedAlbums.toSet())
            }.collect { (lockedPaths, lockedAlbums) ->
                updateLockStates(lockedPaths, lockedAlbums)
            }
        }
    }

    fun refreshMedia() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val hiddenPaths = securityDao.getAllHiddenList().map { it.vaultPath }.toSet()
                val media = mediaFetcher.fetchMedia(emptySet(), emptySet(), hiddenPaths)
                allMediaCache = media
                filterAndPublishMedia()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, toastMessage = "Failed to load media: ${e.message}") }
            }
        }
    }

    private fun updateLockStates(lockedPaths: Set<String>, lockedAlbums: Set<String>) {
        if (allMediaCache.isEmpty()) return
        allMediaCache = allMediaCache.map { item ->
            val locked = lockedPaths.contains(item.path) || lockedAlbums.contains(item.albumName)
            item.copy(isLocked = locked)
        }
        filterAndPublishMedia()
    }

    fun selectTab(tab: GalleryTab) {
        val previousTab = _uiState.value.currentTab
        _uiState.update { it.copy(currentTab = tab) }

        // Leaving the vault tab locks the vault (Problem 4)
        if (previousTab == GalleryTab.VAULT && tab != GalleryTab.VAULT) {
            lockVault()
        }

        if (tab == GalleryTab.VAULT) {
            // Reconcile pass when opening vault (Problem 8)
            viewModelScope.launch(Dispatchers.IO) {
                securityRepository.reconcileVault()
            }
            if (!_uiState.value.isVaultUnlocked) {
                requestVaultUnlock()
            }
        }
    }

    fun selectAlbum(albumName: String?) {
        _uiState.update { it.copy(selectedAlbum = albumName) }
        filterAndPublishMedia()
    }

    fun setFilter(filter: MediaFilter) {
        _uiState.update { it.copy(filter = filter) }
        filterAndPublishMedia()
    }

    fun setSearchQuery(query: String) {
        viewModelScope.launch {
            // Check secret code hash match (Problem 9)
            val isSecretCode = securityRepository.verifyVaultSecretCode(query)
            if (isSecretCode) {
                _uiState.update { it.copy(searchQuery = "", isSearchExpanded = false) }
                selectTab(GalleryTab.VAULT)
                return@launch
            }
            _uiState.update { it.copy(searchQuery = query) }
            filterAndPublishMedia()
        }
    }

    fun setVaultSecretCode(code: String) {
        viewModelScope.launch { securityRepository.setVaultSecretCode(code) }
    }

    fun dismissVaultTutorial() {
        viewModelScope.launch { securityRepository.markVaultTutorialSeen() }
    }

    fun toggleSearchExpanded(expanded: Boolean? = null) {
        _uiState.update {
            val newExpanded = expanded ?: !it.isSearchExpanded
            it.copy(isSearchExpanded = newExpanded)
        }
    }

    fun setDateFilter(period: DateFilterPeriod) {
        _uiState.update { it.copy(dateFilter = period) }
        filterAndPublishMedia()
    }

    fun setFolderFilter(folder: String?) {
        _uiState.update { it.copy(folderFilter = folder) }
        filterAndPublishMedia()
    }

    private fun filterAndPublishMedia() {
        val state = _uiState.value
        val query = state.searchQuery.trim().lowercase()
        val selectedAlbum = state.selectedAlbum
        val filter = state.filter
        val datePeriod = state.dateFilter
        val folder = state.folderFilter

        val filtered = allMediaCache.filter { item ->
            val matchesAlbum = selectedAlbum == null || item.albumName.equals(selectedAlbum, ignoreCase = true)
            val matchesFolder = folder == null || item.albumName.equals(folder, ignoreCase = true)
            val matchesQuery = query.isEmpty() ||
                item.displayName.lowercase().contains(query) ||
                item.albumName.lowercase().contains(query) ||
                item.mimeType.lowercase().contains(query)
            val matchesFilter = when (filter) {
                MediaFilter.ALL -> true
                MediaFilter.PHOTOS -> !item.mimeType.startsWith("video")
                MediaFilter.VIDEOS -> item.mimeType.startsWith("video")
                MediaFilter.LOCKED -> item.isLocked
            }
            val matchesDate = when (datePeriod) {
                DateFilterPeriod.ALL -> true
                DateFilterPeriod.TODAY -> isWithinDays(item.dateAdded, 1)
                DateFilterPeriod.THIS_WEEK -> isWithinDays(item.dateAdded, 7)
                DateFilterPeriod.THIS_MONTH -> isWithinDays(item.dateAdded, 30)
                DateFilterPeriod.THIS_YEAR -> isWithinDays(item.dateAdded, 365)
            }
            matchesAlbum && matchesFolder && matchesQuery && matchesFilter && matchesDate
        }

        val albumsMap = allMediaCache.groupBy { it.albumName }
        val albumsList = albumsMap.map { (name, items) ->
            AlbumItem(
                name = name,
                itemCount = items.size,
                coverUri = items.firstOrNull()?.uri,
                isLocked = items.all { it.isLocked }
            )
        }

        val folders = allMediaCache.map { it.albumName }.distinct().sorted()

        _uiState.update {
            it.copy(
                mediaItems = filtered,
                albums = albumsList,
                availableFolders = folders,
                isLoading = false
            )
        }
    }

    private fun isWithinDays(timestampSec: Long, days: Int): Boolean {
        val itemMillis = timestampSec * 1000L
        val cutoff = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000L)
        return itemMillis >= cutoff
    }

    // --- Media Viewer & EXIF ---

    fun openViewer(item: MediaItem) {
        _uiState.update { it.copy(activeViewerItem = item) }
        loadExifData(item)
    }

    fun closeViewer() {
        _uiState.update { it.copy(activeViewerItem = null, activeExifData = null, showExifDialog = false) }
    }

    fun onViewerPageChanged(item: MediaItem) {
        _uiState.update { it.copy(activeViewerItem = item) }
        loadExifData(item)
    }

    fun toggleMediaLock(item: MediaItem) {
        requestToggleMediaLock(item)
    }

    fun toggleExifDialog(show: Boolean? = null) {
        _uiState.update {
            val newShow = show ?: !it.showExifDialog
            it.copy(showExifDialog = newShow)
        }
    }

    private fun loadExifData(item: MediaItem) {
        viewModelScope.launch {
            val exif = ExifHelper.extractExif(getApplication(), item.uri, item.path)
            _uiState.update { it.copy(activeExifData = exif) }
        }
    }

    // --- Deletion Lock Management ---

    fun requestToggleMediaLock(item: MediaItem) {
        val settings = _uiState.value.securitySettings
        if (item.isLocked && settings.deletionLockEnabled) {
            _uiState.update {
                it.copy(
                    authDialogState = AuthDialogState(
                        reason = AuthReason.TOGGLE_DELETION_LOCK,
                        title = "Unlock Item",
                        description = "Authenticate with PIN or Biometrics to unlock this item.",
                        targetItem = item
                    )
                )
            }
        } else {
            performToggleMediaLock(item)
        }
    }

    private fun performToggleMediaLock(item: MediaItem) {
        viewModelScope.launch {
            val locked = securityRepository.togglePhotoLock(item)
            val msg = if (locked) "Locked: Protected by Deletion Lock" else "Unlocked: Protection removed"
            _uiState.update { it.copy(toastMessage = msg) }
        }
    }

    fun toggleAlbumLock(albumName: String) {
        val settings = _uiState.value.securitySettings
        val albums = _uiState.value.albums
        val album = albums.find { it.name == albumName }
        val isCurrentlyLocked = album?.isLocked ?: false

        if (isCurrentlyLocked && settings.deletionLockEnabled) {
            _uiState.update {
                it.copy(
                    authDialogState = AuthDialogState(
                        reason = AuthReason.TOGGLE_ALBUM_DELETION_LOCK,
                        title = "Unlock Album",
                        description = "Authenticate to unlock album \"$albumName\".",
                        targetAlbum = albumName
                    )
                )
            }
        } else {
            performToggleAlbumLock(albumName)
        }
    }

    private fun performToggleAlbumLock(albumName: String) {
        viewModelScope.launch {
            val locked = securityRepository.toggleAlbumLock(albumName)
            val msg = if (locked) "Album locked ($albumName)" else "Album unlocked ($albumName)"
            _uiState.update { it.copy(toastMessage = msg) }
        }
    }

    fun requestDeleteMedia(item: MediaItem) {
        val settings = _uiState.value.securitySettings
        if (item.isLocked && settings.deletionLockEnabled) {
            _uiState.update {
                it.copy(
                    authDialogState = AuthDialogState(
                        reason = AuthReason.DELETION_LOCK,
                        title = "Protected Item Deletion",
                        description = "This item is locked. Verify identity to permanently delete it.",
                        targetItem = item
                    )
                )
            }
        } else {
            executeDeleteMedia(item)
        }
    }

    private fun executeDeleteMedia(item: MediaItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = deletionHandler.deleteMedia(item.uri, item.path)) {
                is DeletionResult.Success -> {
                    allMediaCache = allMediaCache.filterNot { it.id == item.id }
                    securityDao.deleteLockedByPath(item.path)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeViewerItem = null,
                            toastMessage = "Item deleted"
                        )
                    }
                    filterAndPublishMedia()
                }
                is DeletionResult.RequiresIntentSender -> {
                    pendingIntentSenderAction = PendingIntentAction.DeleteSingle(item)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pendingDeleteRequest = result.intentSenderRequest
                        )
                    }
                }
                is DeletionResult.PartialFailure -> {
                    allMediaCache = allMediaCache.filterNot { it.id == item.id }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeViewerItem = null,
                            toastMessage = result.message
                        )
                    }
                    filterAndPublishMedia()
                }
                is DeletionResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, toastMessage = "Delete failed: ${result.message}")
                    }
                }
            }
        }
    }

    fun onIntentSenderHandled(success: Boolean) {
        _uiState.update { it.copy(pendingDeleteRequest = null) }
        val action = pendingIntentSenderAction
        pendingIntentSenderAction = null

        if (success) {
            when (action) {
                is PendingIntentAction.DeleteSingle -> {
                    allMediaCache = allMediaCache.filterNot { it.id == action.item.id }
                    viewModelScope.launch { securityDao.deleteLockedByPath(action.item.path) }
                    _uiState.update { it.copy(activeViewerItem = null, toastMessage = "Item deleted") }
                    filterAndPublishMedia()
                }
                is PendingIntentAction.DeleteBulk -> {
                    val ids = action.items.map { it.id }.toSet()
                    allMediaCache = allMediaCache.filterNot { ids.contains(it.id) }
                    viewModelScope.launch {
                        action.items.forEach { securityDao.deleteLockedByPath(it.path) }
                    }
                    _uiState.update {
                        it.copy(
                            isSelectionMode = false,
                            selectedItemIds = emptySet(),
                            toastMessage = "Deleted ${action.items.size} items"
                        )
                    }
                    filterAndPublishMedia()
                }
                is PendingIntentAction.MoveToVaultSingle -> {
                    allMediaCache = allMediaCache.filterNot { it.id == action.item.id }
                    _uiState.update {
                        it.copy(
                            activeViewerItem = null,
                            toastMessage = "Moved to Hidden Vault (AES-256-GCM Encrypted)"
                        )
                    }
                    filterAndPublishMedia()
                }
                is PendingIntentAction.MoveToVaultBulk -> {
                    val ids = action.items.map { it.id }.toSet()
                    allMediaCache = allMediaCache.filterNot { ids.contains(it.id) }
                    _uiState.update {
                        it.copy(
                            isSelectionMode = false,
                            selectedItemIds = emptySet(),
                            toastMessage = "Moved ${action.items.size} items to Hidden Vault"
                        )
                    }
                    filterAndPublishMedia()
                }
                null -> {
                    refreshMedia()
                }
            }
        } else {
            when (action) {
                is PendingIntentAction.MoveToVaultSingle -> {
                    _uiState.update {
                        it.copy(toastMessage = "Item copied to vault, but original remains visible (delete permission declined).")
                    }
                }
                is PendingIntentAction.MoveToVaultBulk -> {
                    _uiState.update {
                        it.copy(toastMessage = "Items copied to vault, but originals remain visible (delete permission declined).")
                    }
                }
                is PendingIntentAction.DeleteSingle, is PendingIntentAction.DeleteBulk -> {
                    _uiState.update {
                        it.copy(toastMessage = "Deletion canceled by user.")
                    }
                }
                null -> {}
            }
        }
    }

    // --- Hidden Vault Management ---

    fun requestVaultUnlock() {
        val settings = _uiState.value.securitySettings
        if (!settings.hasPinSet) {
            _uiState.update {
                it.copy(
                    authDialogState = AuthDialogState(
                        reason = AuthReason.SET_PIN,
                        title = "Create Vault PIN",
                        description = "Set a secure Master PIN to protect your Hidden Vault and Deletion Locks."
                    )
                )
            }
        } else {
            val cryptoObj = vaultCryptoManager.getBiometricCryptoObject()
            _uiState.update {
                it.copy(
                    authDialogState = AuthDialogState(
                        reason = AuthReason.UNLOCK_VAULT,
                        title = "Unlock Hidden Vault",
                        description = "Enter PIN or use Biometrics to access private files.",
                        cryptoObject = cryptoObj
                    )
                )
            }
        }
    }

    fun lockVault() {
        vaultCryptoManager.lock()
        try {
            SingletonImageLoader.get(getApplication()).memoryCache?.clear()
        } catch (_: Exception) {}
        _uiState.update {
            it.copy(
                isVaultUnlocked = false,
                activeViewerItem = if (it.activeViewerItem?.isLocked == true) null else it.activeViewerItem
            )
        }
    }

    fun moveToVault(item: MediaItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = securityRepository.moveToVault(
                uri = item.uri,
                originalPath = item.path,
                displayName = item.displayName,
                mimeType = item.mimeType,
                size = item.size,
                dateAdded = item.dateAdded,
                dateTaken = item.dateTaken,
                relativeFolder = item.relativeFolder
            )
            _uiState.update { it.copy(isLoading = false) }
            when (result) {
                is VaultMoveResult.Success -> {
                    allMediaCache = allMediaCache.filterNot { m -> m.id == item.id }
                    _uiState.update { state ->
                        state.copy(
                            activeViewerItem = null,
                            toastMessage = "Moved to Hidden Vault (AES-256-GCM Encrypted)"
                        )
                    }
                    filterAndPublishMedia()
                }
                is VaultMoveResult.RequiresDeleteConsent -> {
                    pendingIntentSenderAction = PendingIntentAction.MoveToVaultSingle(item, result.entity)
                    _uiState.update {
                        it.copy(pendingDeleteRequest = result.intentSenderRequest)
                    }
                }
                is VaultMoveResult.CopiedOriginalVisible -> {
                    _uiState.update {
                        it.copy(toastMessage = result.message)
                    }
                }
                is VaultMoveResult.Error -> {
                    _uiState.update {
                        it.copy(toastMessage = "Failed to encrypt and hide: ${result.message}")
                    }
                }
            }
        }
    }

    fun restoreFromVault(hiddenEntity: HiddenMediaEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = securityRepository.restoreFromVault(hiddenEntity)
            result.onSuccess {
                _uiState.update { state ->
                    state.copy(isLoading = false, toastMessage = "Restored to public gallery")
                }
                refreshMedia()
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(isLoading = false, toastMessage = "Restore failed: ${error.message}")
                }
            }
        }
    }

    fun deleteFromVault(hiddenEntity: HiddenMediaEntity) {
        viewModelScope.launch {
            val success = securityRepository.deletePermanentlyFromVault(hiddenEntity)
            val msg = if (success) "Permanently deleted from vault" else "Deletion failed"
            _uiState.update { it.copy(toastMessage = msg) }
        }
    }

    // --- Authentication Flow ---

    fun verifyPin(pin: String, onResult: (PinVerifyResult) -> Unit) {
        viewModelScope.launch {
            val result = securityRepository.verifyPin(pin)
            if (result is PinVerifyResult.Success) {
                vaultCryptoManager.unlockWithKeystore()
            }
            onResult(result)
        }
    }

    fun setMasterPin(pin: String) {
        viewModelScope.launch {
            securityRepository.setPin(pin)
            vaultCryptoManager.unlockWithKeystore()
            vaultCryptoManager.setupBiometricKey()
            _uiState.update {
                it.copy(
                    authDialogState = null,
                    isVaultUnlocked = true,
                    toastMessage = "Master PIN configured with hardware-bound encryption"
                )
            }
            // Trigger migration of any plaintext vault files
            securityRepository.migrateLegacyVaultFiles()
        }
    }

    fun onBiometricAuthSuccess(result: BiometricPrompt.AuthenticationResult) {
        val cipher = result.cryptoObject?.cipher
        if (cipher != null) {
            vaultCryptoManager.unlockWithBiometricCipher(cipher)
        } else {
            vaultCryptoManager.unlockWithKeystore()
        }
        _uiState.update { it.copy(isVaultUnlocked = true, authDialogState = null) }
        viewModelScope.launch {
            securityRepository.migrateLegacyVaultFiles()
        }
    }

    fun onAuthSuccess(reason: AuthReason, targetItem: MediaItem?) {
        val authState = _uiState.value.authDialogState
        val targetItems = authState?.targetItems ?: emptyList()
        val targetAlbum = authState?.targetAlbum
        _uiState.update { it.copy(authDialogState = null) }
        when (reason) {
            AuthReason.DELETION_LOCK -> {
                if (targetItem != null) {
                    executeDeleteMedia(targetItem)
                }
            }
            AuthReason.BULK_DELETE -> {
                if (targetItems.isNotEmpty()) {
                    executeBulkDelete(targetItems)
                }
            }
            AuthReason.UNLOCK_VAULT -> {
                vaultCryptoManager.unlockWithKeystore()
                _uiState.update { it.copy(isVaultUnlocked = true) }
                viewModelScope.launch {
                    securityRepository.migrateLegacyVaultFiles()
                }
            }
            AuthReason.SET_PIN -> {
                // Handled in setMasterPin
            }
            AuthReason.CHANGE_PIN -> {
                _uiState.update {
                    it.copy(
                        authDialogState = AuthDialogState(
                            reason = AuthReason.SET_PIN,
                            title = "Create New PIN",
                            description = "Enter a new PIN for your Vault and Deletion Locks."
                        )
                    )
                }
            }
            AuthReason.TOGGLE_DELETION_LOCK -> {
                if (targetItem != null) performToggleMediaLock(targetItem)
            }
            AuthReason.TOGGLE_ALBUM_DELETION_LOCK -> {
                targetAlbum?.let { performToggleAlbumLock(it) }
            }
            AuthReason.TOGGLE_BULK_DELETION_LOCK -> {
                if (targetItems.isNotEmpty()) performBulkLock(targetItems, targetLockState = false)
            }
            AuthReason.DISABLE_DELETION_ENGINE -> {
                performSetDeletionLockEnabled(false)
            }
            AuthReason.CHANGE_VAULT_CODE -> {
                _uiState.update { it.copy(showVaultCodeEditor = true) }
            }
        }
    }

    // --- Bulk Action Methods ---

    fun enterSelectionMode(initialItemId: Long? = null) {
        val set = if (initialItemId != null) setOf(initialItemId) else emptySet()
        _uiState.update { it.copy(isSelectionMode = true, selectedItemIds = set) }
    }

    fun exitSelectionMode() {
        _uiState.update { it.copy(isSelectionMode = false, selectedItemIds = emptySet()) }
    }

    fun toggleItemSelection(itemId: Long) {
        _uiState.update { state ->
            val current = state.selectedItemIds.toMutableSet()
            if (current.contains(itemId)) {
                current.remove(itemId)
            } else {
                current.add(itemId)
            }
            state.copy(
                selectedItemIds = current,
                isSelectionMode = current.isNotEmpty()
            )
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedItemIds = state.mediaItems.map { it.id }.toSet())
        }
    }

    fun deselectAll() {
        _uiState.update { it.copy(selectedItemIds = emptySet(), isSelectionMode = false) }
    }

    fun bulkLockSelected() {
        val selectedIds = _uiState.value.selectedItemIds
        val selectedItems = _uiState.value.mediaItems.filter { selectedIds.contains(it.id) }
        val allAreLocked = selectedItems.all { it.isLocked }
        val targetLockState = !allAreLocked
        val settings = _uiState.value.securitySettings

        if (!targetLockState && settings.deletionLockEnabled) {
            _uiState.update {
                it.copy(
                    authDialogState = AuthDialogState(
                        reason = AuthReason.TOGGLE_BULK_DELETION_LOCK,
                        title = "Unlock Selected Items",
                        description = "Authenticate to remove Deletion Lock from ${selectedItems.size} items.",
                        targetItems = selectedItems
                    )
                )
            }
        } else {
            performBulkLock(selectedItems, targetLockState)
        }
    }

    private fun performBulkLock(items: List<MediaItem>, targetLockState: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            for (item in items) {
                if (targetLockState) {
                    val entity = LockedMediaEntity(
                        mediaPath = item.path,
                        mediaUriString = item.uri.toString(),
                        albumName = item.albumName,
                        isLocked = true,
                        isAlbumLocked = false
                    )
                    securityDao.insertOrUpdateLocked(entity)
                } else {
                    securityDao.deleteLockedByPath(item.path)
                }
            }
            val msg = if (targetLockState) "Locked ${items.size} items" else "Unlocked ${items.size} items"
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isSelectionMode = false,
                    selectedItemIds = emptySet(),
                    toastMessage = msg
                )
            }
        }
    }

    fun bulkMoveToVault() {
        val selectedIds = _uiState.value.selectedItemIds
        val selectedItems = _uiState.value.mediaItems.filter { selectedIds.contains(it.id) }
        if (selectedItems.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val successEntities = mutableListOf<HiddenMediaEntity>()
            val successItems = mutableListOf<MediaItem>()
            val consentUris = mutableListOf<Uri>()

            for (item in selectedItems) {
                val res = securityRepository.moveToVault(
                    uri = item.uri,
                    originalPath = item.path,
                    displayName = item.displayName,
                    mimeType = item.mimeType,
                    size = item.size,
                    dateAdded = item.dateAdded,
                    dateTaken = item.dateTaken,
                    relativeFolder = item.relativeFolder
                )
                when (res) {
                    is VaultMoveResult.Success -> {
                        successEntities.add(res.entity)
                        successItems.add(item)
                    }
                    is VaultMoveResult.RequiresDeleteConsent -> {
                        successEntities.add(res.entity)
                        successItems.add(item)
                        consentUris.add(item.uri)
                    }
                    is VaultMoveResult.CopiedOriginalVisible -> {
                        successEntities.add(res.entity)
                    }
                    is VaultMoveResult.Error -> {}
                }
            }

            _uiState.update { it.copy(isLoading = false) }

            if (consentUris.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val pendingIntent = MediaStore.createDeleteRequest(
                        getApplication<Application>().contentResolver,
                        consentUris
                    )
                    val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    pendingIntentSenderAction = PendingIntentAction.MoveToVaultBulk(successItems, successEntities)
                    _uiState.update { it.copy(pendingDeleteRequest = request) }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isSelectionMode = false,
                            selectedItemIds = emptySet(),
                            toastMessage = "Copied ${successEntities.size} items to vault; originals remain visible."
                        )
                    }
                }
            } else if (successItems.isNotEmpty()) {
                val movedIds = successItems.map { it.id }.toSet()
                allMediaCache = allMediaCache.filterNot { movedIds.contains(it.id) }
                _uiState.update {
                    it.copy(
                        isSelectionMode = false,
                        selectedItemIds = emptySet(),
                        toastMessage = "Moved ${successItems.size} items to Hidden Vault"
                    )
                }
                filterAndPublishMedia()
            } else {
                _uiState.update {
                    it.copy(
                        isSelectionMode = false,
                        selectedItemIds = emptySet(),
                        toastMessage = "Failed to move items to vault"
                    )
                }
            }
        }
    }

    fun bulkDeleteSelected() {
        val selectedIds = _uiState.value.selectedItemIds
        val selectedItems = _uiState.value.mediaItems.filter { selectedIds.contains(it.id) }
        val hasLockedItems = selectedItems.any { it.isLocked }
        val settings = _uiState.value.securitySettings

        if (hasLockedItems && settings.deletionLockEnabled) {
            _uiState.update {
                it.copy(
                    authDialogState = AuthDialogState(
                        reason = AuthReason.BULK_DELETE,
                        title = "Protected Bulk Deletion",
                        description = "Selection includes locked items. Verify identity to permanently delete ${selectedItems.size} items.",
                        targetItems = selectedItems
                    )
                )
            }
        } else {
            executeBulkDelete(selectedItems)
        }
    }

    private fun executeBulkDelete(items: List<MediaItem>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = deletionHandler.deleteMultipleMedia(items)) {
                is DeletionResult.Success -> {
                    val deletedIds = items.map { it.id }.toSet()
                    allMediaCache = allMediaCache.filterNot { deletedIds.contains(it.id) }
                    for (item in items) {
                        securityDao.deleteLockedByPath(item.path)
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSelectionMode = false,
                            selectedItemIds = emptySet(),
                            toastMessage = "Deleted ${result.deletedCount} items"
                        )
                    }
                    filterAndPublishMedia()
                }
                is DeletionResult.RequiresIntentSender -> {
                    pendingIntentSenderAction = PendingIntentAction.DeleteBulk(items)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pendingDeleteRequest = result.intentSenderRequest
                        )
                    }
                }
                is DeletionResult.PartialFailure -> {
                    refreshMedia()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSelectionMode = false,
                            selectedItemIds = emptySet(),
                            toastMessage = result.message
                        )
                    }
                }
                is DeletionResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSelectionMode = false,
                            selectedItemIds = emptySet(),
                            toastMessage = "Delete failed: ${result.message}"
                        )
                    }
                }
            }
        }
    }

    // --- Slideshow Methods ---

    fun startSlideshow(items: List<MediaItem>? = null, startIndex: Int = 0) {
        val list = items ?: run {
            val selectedIds = _uiState.value.selectedItemIds
            if (selectedIds.isNotEmpty()) {
                _uiState.value.mediaItems.filter { selectedIds.contains(it.id) }
            } else {
                _uiState.value.mediaItems
            }
        }
        if (list.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    isSlideshowActive = true,
                    slideshowItems = list,
                    slideshowStartIndex = startIndex
                )
            }
        } else {
            _uiState.update { it.copy(toastMessage = "No photos available for slideshow") }
        }
    }

    fun stopSlideshow() {
        _uiState.update { it.copy(isSlideshowActive = false, slideshowItems = emptyList()) }
    }

    fun dismissAuthDialog() {
        _uiState.update { it.copy(authDialogState = null) }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun setBiometricsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            securityRepository.setBiometricsEnabled(enabled)
            if (enabled) {
                vaultCryptoManager.setupBiometricKey()
            }
        }
    }

    fun requestToggleDeletionEngine(enable: Boolean) {
        if (!enable) {
            _uiState.update {
                it.copy(
                    authDialogState = AuthDialogState(
                        reason = AuthReason.DISABLE_DELETION_ENGINE,
                        title = "Disable Deletion Lock Engine",
                        description = "Enter PIN or use biometrics to turn off protection for ALL locked items."
                    )
                )
            }
        } else {
            performSetDeletionLockEnabled(true)
        }
    }

    private fun performSetDeletionLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            securityRepository.setDeletionLockEnabled(enabled)
            _uiState.update { it.copy(toastMessage = "Security settings updated") }
        }
    }

    fun requestChangePin() {
        val settings = _uiState.value.securitySettings
        if (!settings.hasPinSet) {
            _uiState.update {
                it.copy(
                    authDialogState = AuthDialogState(
                        reason = AuthReason.SET_PIN,
                        title = "Create Vault PIN",
                        description = "Set a Master PIN to secure your Hidden Vault and Deletion Locks."
                    )
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    authDialogState = AuthDialogState(
                        reason = AuthReason.CHANGE_PIN,
                        title = "Verify Current PIN",
                        description = "Enter your current PIN to set a new one."
                    )
                )
            }
        }
    }

    fun requestChangeVaultCode() {
        _uiState.update {
            it.copy(
                authDialogState = AuthDialogState(
                    reason = AuthReason.CHANGE_VAULT_CODE,
                    title = "Vault Access Code",
                    description = "Enter PIN or use biometrics to view or change the vault access code."
                )
            )
        }
    }

    fun dismissVaultCodeEditor() {
        _uiState.update { it.copy(showVaultCodeEditor = false) }
    }

    fun setSecureModeAppWide(enabled: Boolean) {
        viewModelScope.launch {
            securityRepository.setSecureModeAppWide(enabled)
        }
    }

    fun setAutoLockDelay(delay: AutoLockDelay) {
        viewModelScope.launch {
            securityRepository.setAutoLockDelay(delay)
        }
    }
}
