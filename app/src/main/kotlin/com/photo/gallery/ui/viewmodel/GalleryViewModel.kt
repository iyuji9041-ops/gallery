package com.photo.gallery.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photo.gallery.data.local.AppDatabase
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
import com.photo.gallery.security.BiometricAuthManager
import com.photo.gallery.service.VaultMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
    // BUG FIXES (security-review pass before publishing):
    TOGGLE_DELETION_LOCK,        // unlocking (removing) a single item's deletion lock
    TOGGLE_ALBUM_DELETION_LOCK,  // unlocking an album's deletion lock
    TOGGLE_BULK_DELETION_LOCK,   // bulk-unlocking selected items' deletion lock
    DISABLE_DELETION_ENGINE,     // turning OFF the global Deletion Lock Engine switch
    CHANGE_VAULT_CODE            // viewing/changing the vault search-bar access code
}

data class AuthDialogState(
    val reason: AuthReason,
    val title: String,
    val description: String,
    val targetItem: MediaItem? = null,
    val targetAlbum: String? = null,
    val targetItems: List<MediaItem> = emptyList()
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
    // Only true once the CHANGE_VAULT_CODE auth reason succeeds - prevents the vault
    // access code editor from being reachable without proving identity first.
    val showVaultCodeEditor: Boolean = false,
    val isVaultUnlocked: Boolean = false,
    val hiddenVaultItems: List<HiddenMediaEntity> = emptyList(),
    val securitySettings: SecuritySettings = SecuritySettings(),
    val authDialogState: AuthDialogState? = null,
    val pendingDeleteRequest: IntentSenderRequest? = null,
    val toastMessage: String? = null,
    val isBiometricAvailable: Boolean = false,
    val isVaultMonitorActive: Boolean = true
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val securityDao = database.securityDao()
    private val securityPreferences = SecurityPreferences(application)
    private val vaultManager = VaultManager(application, securityDao)
    val securityRepository = SecurityRepository(securityDao, securityPreferences, vaultManager)
    private val mediaFetcher = MediaStoreFetcher(application)
    private val deletionHandler = MediaDeletionHandler(application)
    val biometricAuthManager = BiometricAuthManager(application)

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private var allMediaCache: List<MediaItem> = emptyList()

    init {
        try {
            _uiState.update {
                it.copy(isBiometricAvailable = biometricAuthManager.isBiometricAvailable())
            }
        } catch (e: Throwable) {
            // Guard against any biometric initialization errors
        }
        // Ensure background vault directory monitor listener is running
        VaultMonitorService.start(application)
        observeDataSources()
        refreshMedia()
    }

    private fun observeDataSources() {
        // Observe Security Settings
        viewModelScope.launch {
            securityRepository.securitySettings.collect { settings ->
                _uiState.update { it.copy(securitySettings = settings) }
            }
        }

        // Observe Hidden Vault
        viewModelScope.launch {
            securityRepository.allHiddenMedia.collect { hiddenList ->
                _uiState.update { it.copy(hiddenVaultItems = hiddenList) }
            }
        }

        // Observe Vault Monitor Service status
        viewModelScope.launch {
            VaultMonitorService.isMonitoringActive.collect { active ->
                _uiState.update { it.copy(isVaultMonitorActive = active) }
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
                // Fetch latest media excluding hidden vault files
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
        _uiState.update { it.copy(currentTab = tab) }
        if (tab == GalleryTab.VAULT) {
            VaultMonitorService.triggerReconciliation(getApplication())
            if (!_uiState.value.isVaultUnlocked) {
                // Prompt unlock if vault is locked
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
        // Hidden vault trigger: typing the exact configured secret phrase (default
        // "\vault", user-changeable in Settings) opens the vault instead of running a
        // normal text search. This is what replaces the old always-visible "VAULT"
        // bottom-nav tab - the vault still requires the real PIN/biometric unlock via
        // selectTab()'s existing logic, this only changes HOW you navigate to it.
        val secretCode = _uiState.value.securitySettings.vaultSecretCode
        if (secretCode.isNotBlank() && query.trim().equals(secretCode.trim(), ignoreCase = true)) {
            _uiState.update { it.copy(searchQuery = "", isSearchExpanded = false) }
            selectTab(GalleryTab.VAULT)
            return
        }
        _uiState.update { it.copy(searchQuery = query) }
        filterAndPublishMedia()
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
        val folderFilter = state.folderFilter

        val filtered = allMediaCache.filter { item ->
            // 1. Check album navigation
            val matchesSelectedAlbum = selectedAlbum == null || item.albumName.equals(selectedAlbum, ignoreCase = true)
            // 2. Check dedicated folder filter
            val matchesFolder = folderFilter == null || item.albumName.equals(folderFilter, ignoreCase = true)
            // 3. Check search query across name, folder, path, and date
            val matchesSearch = matchesQuery(item, query)
            // 4. Check date period filter
            val matchesDate = matchesDateFilter(item, datePeriod)
            // 5. Check media type
            val matchesType = when (filter) {
                MediaFilter.ALL -> true
                MediaFilter.PHOTOS -> !item.isVideo
                MediaFilter.VIDEOS -> item.isVideo
                MediaFilter.LOCKED -> item.isLocked
            }
            matchesSelectedAlbum && matchesFolder && matchesSearch && matchesDate && matchesType
        }

        val albums = mediaFetcher.extractAlbums(allMediaCache, emptySet())
        val folders = allMediaCache.map { it.albumName }.filter { it.isNotBlank() }.distinct().sorted()

        _uiState.update {
            it.copy(
                mediaItems = filtered,
                albums = albums,
                availableFolders = folders,
                isLoading = false
            )
        }
    }

    private fun matchesQuery(item: MediaItem, query: String): Boolean {
        if (query.isEmpty()) return true
        // 1. Match file name
        if (item.displayName.lowercase().contains(query)) return true
        // 2. Match folder/album name
        if (item.albumName.lowercase().contains(query)) return true
        // 3. Match folder path
        if (item.path.lowercase().contains(query)) return true
        // 4. Match date representations (e.g. 2026, September, Sep, 06, etc.)
        val itemMillis = if (item.dateAdded < 100_000_000_000L) item.dateAdded * 1000L else item.dateAdded
        val dateObj = Date(itemMillis)
        val formattedDate = SimpleDateFormat("yyyy MMMM MMM dd EEEE yyyy-MM-dd", Locale.getDefault()).format(dateObj).lowercase()
        return formattedDate.contains(query)
    }

    private fun matchesDateFilter(item: MediaItem, period: DateFilterPeriod): Boolean {
        if (period == DateFilterPeriod.ALL) return true
        val itemMillis = if (item.dateAdded < 100_000_000_000L) item.dateAdded * 1000L else item.dateAdded
        val cal = Calendar.getInstance()

        return when (period) {
            DateFilterPeriod.ALL -> true
            DateFilterPeriod.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                itemMillis >= cal.timeInMillis
            }
            DateFilterPeriod.THIS_WEEK -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                itemMillis >= cal.timeInMillis
            }
            DateFilterPeriod.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                itemMillis >= cal.timeInMillis
            }
            DateFilterPeriod.THIS_YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                itemMillis >= cal.timeInMillis
            }
        }
    }

    // --- Media Viewer & EXIF ---

    fun openViewer(item: MediaItem) {
        _uiState.update { it.copy(activeViewerItem = item, activeExifData = null, showExifDialog = false) }
        loadExifForItem(item)
    }

    fun closeViewer() {
        _uiState.update { it.copy(activeViewerItem = null, activeExifData = null, showExifDialog = false) }
    }

    /**
     * Called by ImageViewerScreen's HorizontalPager whenever the user swipes to a
     * different photo/video, keeping activeViewerItem (and therefore the lock badge,
     * EXIF sheet, and delete/vault actions, which all read activeViewerItem) in sync
     * with whichever item is actually on screen.
     */
    fun onViewerPageChanged(item: MediaItem) {
        _uiState.update { it.copy(activeViewerItem = item, activeExifData = null, showExifDialog = false) }
        loadExifForItem(item)
    }

    fun toggleExifDialog(show: Boolean) {
        _uiState.update { it.copy(showExifDialog = show) }
    }

    private fun loadExifForItem(item: MediaItem) {
        viewModelScope.launch {
            val exif = ExifHelper.extractExif(getApplication(), item.uri, item.path)
            _uiState.update { it.copy(activeExifData = exif) }
        }
    }

    // --- Deletion Lock (The Flagship Feature) ---

    /**
     * BUG FIX: previously toggled the deletion lock instantly in BOTH directions with
     * zero authentication. Adding protection (locking) is safe to allow instantly, but
     * REMOVING protection (unlocking) is the security-relevant direction - anyone with
     * the phone in hand could freely strip protection off any item. Unlocking now
     * requires PIN/biometric first via the normal AuthenticationDialog flow.
     */
    fun toggleMediaLock(item: MediaItem) {
        if (item.isLocked) {
            _uiState.update {
                it.copy(
                    authDialogState = AuthDialogState(
                        reason = AuthReason.TOGGLE_DELETION_LOCK,
                        title = "Remove Deletion Lock",
                        description = "Enter PIN or use biometrics to unlock \"${item.displayName}\".",
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
            val isNowLocked = securityRepository.togglePhotoLock(item)
            val msg = if (isNowLocked) "Deletion Lock enabled for ${item.displayName}" else "Deletion Lock removed"
            _uiState.update { it.copy(toastMessage = msg) }
            // Update cache item
            allMediaCache = allMediaCache.map { if (it.id == item.id) it.copy(isLocked = isNowLocked) else it }
            if (_uiState.value.activeViewerItem?.id == item.id) {
                _uiState.update { it.copy(activeViewerItem = item.copy(isLocked = isNowLocked)) }
            }
            filterAndPublishMedia()
        }
    }

    /** Same unlock-requires-auth pattern as toggleMediaLock, applied to whole albums. */
    fun toggleAlbumLock(albumName: String) {
        viewModelScope.launch {
            val isCurrentlyLocked = allMediaCache.any { it.albumName == albumName && it.isLocked }
            if (isCurrentlyLocked) {
                _uiState.update {
                    it.copy(
                        authDialogState = AuthDialogState(
                            reason = AuthReason.TOGGLE_ALBUM_DELETION_LOCK,
                            title = "Remove Album Deletion Lock",
                            description = "Enter PIN or use biometrics to unlock \"$albumName\".",
                            targetAlbum = albumName
                        )
                    )
                }
            } else {
                performToggleAlbumLock(albumName)
            }
        }
    }

    private fun performToggleAlbumLock(albumName: String) {
        viewModelScope.launch {
            val isNowLocked = securityRepository.toggleAlbumLock(albumName)
            val msg = if (isNowLocked) "Locked album '$albumName' with Deletion Lock" else "Unlocked album '$albumName'"
            _uiState.update { it.copy(toastMessage = msg) }
            allMediaCache = allMediaCache.map {
                if (it.albumName == albumName) it.copy(isLocked = isNowLocked) else it
            }
            filterAndPublishMedia()
        }
    }

    /**
     * Primary entry point when user requests to delete a photo.
     * If the photo is protected by Deletion Lock, triggers authentication prompt!
     */
    fun requestDeleteMedia(item: MediaItem) {
        viewModelScope.launch {
            val isLocked = item.isLocked || securityRepository.isMediaLocked(item.path, item.albumName)
            val settings = _uiState.value.securitySettings

            if (isLocked && settings.deletionLockEnabled) {
                // Trigger Authentication Dialog!
                _uiState.update {
                    it.copy(
                        authDialogState = AuthDialogState(
                            reason = AuthReason.DELETION_LOCK,
                            title = "Deletion Lock Protected",
                            description = "This photo is locked to prevent accidental or unauthorized deletion. Authenticate with PIN or Biometrics to proceed.",
                            targetItem = item
                        )
                    )
                }
            } else {
                // Not locked or Deletion Lock disabled globally, proceed directly
                executeDeleteMedia(item)
            }
        }
    }

    /**
     * Executes deletion after security check or authentication pass.
     */
    fun executeDeleteMedia(item: MediaItem) {
        viewModelScope.launch {
            when (val result = deletionHandler.deleteMedia(item.uri, item.path)) {
                is DeletionResult.Success -> {
                    // Remove from cache
                    allMediaCache = allMediaCache.filterNot { it.id == item.id }
                    // If locked in Room, remove lock record
                    securityDao.deleteLockedByPath(item.path)
                    _uiState.update {
                        it.copy(
                            activeViewerItem = null,
                            toastMessage = "Item deleted successfully"
                        )
                    }
                    filterAndPublishMedia()
                }
                is DeletionResult.RequiresIntentSender -> {
                    _uiState.update { it.copy(pendingDeleteRequest = result.intentSenderRequest) }
                }
                is DeletionResult.Error -> {
                    _uiState.update { it.copy(toastMessage = "Delete error: ${result.message}") }
                }
            }
        }
    }

    fun onIntentSenderHandled(success: Boolean) {
        _uiState.update { it.copy(pendingDeleteRequest = null) }
        if (success) {
            refreshMedia()
            _uiState.update { it.copy(activeViewerItem = null, toastMessage = "Item deleted") }
        }
    }

    // --- Hidden Vault Management ---

    fun requestVaultUnlock() {
        val settings = _uiState.value.securitySettings
        if (!settings.hasPinSet) {
            // First time: prompt to set PIN
            _uiState.update {
                it.copy(
                    authDialogState = AuthDialogState(
                        reason = AuthReason.SET_PIN,
                        title = "Create Vault PIN",
                        description = "Set a 4-digit Master PIN to secure your Hidden Vault and Deletion Locks."
                    )
                )
            }
        } else {
            // Prompt authentication
            _uiState.update {
                it.copy(
                    authDialogState = AuthDialogState(
                        reason = AuthReason.UNLOCK_VAULT,
                        title = "Unlock Hidden Vault",
                        description = "Enter PIN or use Biometrics to access private photos."
                    )
                )
            }
        }
    }

    fun lockVault() {
        _uiState.update { it.copy(isVaultUnlocked = false) }
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
                dateAdded = item.dateAdded
            )
            result.onSuccess {
                allMediaCache = allMediaCache.filterNot { m -> m.id == item.id }
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        activeViewerItem = null,
                        toastMessage = "Moved to Hidden Vault (.nomedia protected)"
                    )
                }
                filterAndPublishMedia()
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(isLoading = false, toastMessage = "Failed to hide photo: ${error.message}")
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

    fun verifyPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val valid = securityRepository.verifyPin(pin)
            onResult(valid)
        }
    }

    fun setMasterPin(pin: String) {
        viewModelScope.launch {
            securityRepository.setPin(pin)
            _uiState.update {
                it.copy(
                    authDialogState = null,
                    isVaultUnlocked = true,
                    toastMessage = "Master PIN configured successfully"
                )
            }
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
                _uiState.update { it.copy(isVaultUnlocked = true) }
            }
            AuthReason.SET_PIN -> {
                // Handled in setMasterPin (called directly from AuthenticationDialog's
                // onSetPin callback once the new PIN is entered + confirmed).
            }
            AuthReason.CHANGE_PIN -> {
                // Current PIN just verified - now chain straight into the real
                // "create a new PIN" screen (reusing the existing SET_PIN flow).
                _uiState.update {
                    it.copy(
                        authDialogState = AuthDialogState(
                            reason = AuthReason.SET_PIN,
                            title = "Create New PIN",
                            description = "Enter a new 4-digit PIN for your Vault and Deletion Locks."
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
                isSelectionMode = current.isNotEmpty(),
                selectedItemIds = current
            )
        }
    }

    fun selectAll() {
        val allIds = _uiState.value.mediaItems.map { it.id }.toSet()
        _uiState.update { it.copy(selectedItemIds = allIds) }
    }

    fun deselectAll() {
        _uiState.update { it.copy(selectedItemIds = emptySet()) }
    }

    fun bulkLockSelected() {
        val selectedIds = _uiState.value.selectedItemIds
        if (selectedIds.isEmpty()) return

        val selectedItems = allMediaCache.filter { selectedIds.contains(it.id) }
        val anyUnlocked = selectedItems.any { !it.isLocked }
        val targetLockState = anyUnlocked

        if (!targetLockState) {
            // Every selected item is currently locked, so this action UNLOCKS them all
            // - the security-relevant direction - gate it behind auth first.
            _uiState.update {
                it.copy(
                    authDialogState = AuthDialogState(
                        reason = AuthReason.TOGGLE_BULK_DELETION_LOCK,
                        title = "Remove Deletion Lock",
                        description = "Enter PIN or use biometrics to unlock ${selectedItems.size} items.",
                        targetItems = selectedItems
                    )
                )
            }
        } else {
            performBulkLock(selectedItems, targetLockState = true)
        }
    }

    private fun performBulkLock(selectedItems: List<MediaItem>, targetLockState: Boolean) {
        val selectedIds = selectedItems.map { it.id }.toSet()
        viewModelScope.launch {
            for (item in selectedItems) {
                securityRepository.togglePhotoLock(item.copy(isLocked = !targetLockState))
            }

            allMediaCache = allMediaCache.map { item ->
                if (selectedIds.contains(item.id)) item.copy(isLocked = targetLockState) else item
            }
            val msg = if (targetLockState) {
                "Applied Deletion Lock to ${selectedItems.size} items"
            } else {
                "Removed Deletion Lock from ${selectedItems.size} items"
            }
            _uiState.update {
                it.copy(
                    toastMessage = msg,
                    isSelectionMode = false,
                    selectedItemIds = emptySet()
                )
            }
            filterAndPublishMedia()
        }
    }

    fun bulkMoveToVault() {
        val selectedIds = _uiState.value.selectedItemIds
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val selectedItems = allMediaCache.filter { selectedIds.contains(it.id) }
            var movedCount = 0

            for (item in selectedItems) {
                val result = securityRepository.moveToVault(
                    uri = item.uri,
                    originalPath = item.path,
                    displayName = item.displayName,
                    mimeType = item.mimeType,
                    size = item.size,
                    dateAdded = item.dateAdded
                )
                if (result.isSuccess) movedCount++
            }

            allMediaCache = allMediaCache.filterNot { selectedIds.contains(it.id) }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isSelectionMode = false,
                    selectedItemIds = emptySet(),
                    toastMessage = "Moved $movedCount items to Hidden Vault"
                )
            }
            filterAndPublishMedia()
        }
    }

    fun bulkDeleteSelected() {
        val selectedIds = _uiState.value.selectedItemIds
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            val selectedItems = allMediaCache.filter { selectedIds.contains(it.id) }
            val hasLocked = selectedItems.any { it.isLocked || securityRepository.isMediaLocked(it.path, it.albumName) }
            val settings = _uiState.value.securitySettings

            if (hasLocked && settings.deletionLockEnabled) {
                _uiState.update {
                    it.copy(
                        authDialogState = AuthDialogState(
                            reason = AuthReason.BULK_DELETE,
                            title = "Locked Photos Selected",
                            description = "One or more selected photos are protected by Deletion Lock. Please authenticate to authorize deletion.",
                            targetItems = selectedItems
                        )
                    )
                }
            } else {
                executeBulkDelete(selectedItems)
            }
        }
    }

    fun executeBulkDelete(items: List<MediaItem>) {
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
                            toastMessage = "Deleted ${items.size} items"
                        )
                    }
                    filterAndPublishMedia()
                }
                is DeletionResult.RequiresIntentSender -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pendingDeleteRequest = result.intentSenderRequest
                        )
                    }
                }
                is DeletionResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, toastMessage = "Delete failed: ${result.message}")
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

    /**
     * BUG FIX: biometrics toggle stays instant (enabling/disabling how you authenticate
     * is not itself a security hole), but the Switch previously let anyone flip the
     * WHOLE Deletion Lock Engine off with a single tap and no verification - defeating
     * every individual item/album lock in the app at once. Turning it OFF now requires
     * auth; turning it back ON is left instant (re-enabling protection is always safe).
     */
    fun setBiometricsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            securityRepository.setBiometricsEnabled(enabled)
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
                        description = "Set a 4-digit Master PIN to secure your Hidden Vault and Deletion Locks."
                    )
                )
            }
        } else {
            // BUG FIX: this used to call requestVaultUnlock(), which - since a PIN was
            // already set - only ever asked to VERIFY the pin and then silently
            // unlocked the vault. No "create a new PIN" screen was ever shown, which is
            // why "PIN change nahi ho raha" - the button did something, just not the
            // thing it was supposed to. Now it verifies the CURRENT pin first (this
            // reason), and onAuthSuccess chains straight into the real SET_PIN flow.
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

    /**
     * BUG FIX: the "Change" button for the vault's search-bar access code previously
     * opened the edit dialog with NO authentication at all - anyone holding the phone
     * could read or silently change the phrase that opens the hidden vault. Now it's
     * gated the same way as every other security-sensitive action.
     */
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
}
