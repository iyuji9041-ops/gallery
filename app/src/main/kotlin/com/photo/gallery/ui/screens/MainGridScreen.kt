package com.photo.gallery.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Scale
import com.photo.gallery.data.model.MediaItem
import com.photo.gallery.ui.theme.BentoAccent
import com.photo.gallery.ui.theme.BentoAccentContainer
import com.photo.gallery.ui.theme.BentoAccentSubtle
import com.photo.gallery.ui.theme.BentoBg
import com.photo.gallery.ui.theme.BentoBorder
import com.photo.gallery.ui.theme.BentoRed
import com.photo.gallery.ui.theme.BentoRedContainer
import com.photo.gallery.ui.theme.BentoSurface
import com.photo.gallery.ui.theme.BentoSurfaceCard
import com.photo.gallery.ui.theme.BentoSurfaceElevated
import com.photo.gallery.ui.theme.BentoTextPrimary
import com.photo.gallery.ui.theme.BentoTextSecondary
import com.photo.gallery.ui.theme.BentoTextTertiary
import com.photo.gallery.ui.viewmodel.DateFilterPeriod
import com.photo.gallery.ui.viewmodel.MediaFilter
import com.photo.gallery.ui.viewmodel.StoragePermissionState

/**
 * Main media grid adhering strictly to the Bento Grid design theme:
 * Supports dynamic local search (date, file name, folder), multi-select bulk actions
 * (lock/unlock, vault, delete, share, slideshow), and full-screen slideshow mode.
 */
@Composable
fun MainGridScreen(
    mediaItems: List<MediaItem>,
    selectedAlbum: String?,
    filter: MediaFilter,
    searchQuery: String,
    isSearchExpanded: Boolean = false,
    dateFilter: DateFilterPeriod = DateFilterPeriod.ALL,
    folderFilter: String? = null,
    availableFolders: List<String> = emptyList(),
    isSelectionMode: Boolean = false,
    selectedItemIds: Set<Long> = emptySet(),
    isLoading: Boolean,
    permissionState: StoragePermissionState = StoragePermissionState.GRANTED,
    onRequestPermission: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onFilterChange: (MediaFilter) -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleSearch: () -> Unit = {},
    onDateFilterChange: (DateFilterPeriod) -> Unit = {},
    onFolderFilterChange: (String?) -> Unit = {},
    onClearAlbumFilter: () -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onItemLongClick: (MediaItem) -> Unit = {},
    onToggleSelectionMode: (Boolean) -> Unit = {},
    onToggleItemSelection: (Long) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onDeselectAll: () -> Unit = {},
    onBulkLock: () -> Unit = {},
    onBulkVault: () -> Unit = {},
    onBulkDelete: () -> Unit = {},
    onBulkShare: () -> Unit = {},
    onStartSlideshow: () -> Unit = {}
) {
    var isFilterPanelExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BentoBg)
            .testTag("main_grid_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Selection Bar (when in selection mode)
            if (isSelectionMode) {
                BentoSelectionHeader(
                    selectedCount = selectedItemIds.size,
                    totalCount = mediaItems.size,
                    onSelectAll = onSelectAll,
                    onDeselectAll = onDeselectAll,
                    onCloseSelection = { onToggleSelectionMode(false) }
                )
            } else {
                // Search & Filter Controls Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BentoBg)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    // Search Bar
                    if (isSearchExpanded) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gallery_search_input"),
                            placeholder = {
                                Text(
                                    "Search by file name, folder, or date (e.g. 2026, Sept)...",
                                    color = BentoTextTertiary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = BentoAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (searchQuery.isNotEmpty()) {
                                        onSearchChange("")
                                    } else {
                                        onToggleSearch()
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close Search",
                                        tint = BentoTextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BentoAccent,
                                unfocusedBorderColor = BentoBorder,
                                focusedContainerColor = BentoSurface,
                                unfocusedContainerColor = BentoSurface,
                                focusedTextColor = BentoTextPrimary,
                                unfocusedTextColor = BentoTextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Media Type Chips + Date/Folder Filter Toggle + Slideshow launch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Media Type Chips (Pill Bento Style)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            FilterChipItem(
                                label = "All",
                                selected = filter == MediaFilter.ALL,
                                onClick = { onFilterChange(MediaFilter.ALL) }
                            )
                            FilterChipItem(
                                label = "Photos",
                                selected = filter == MediaFilter.PHOTOS,
                                onClick = { onFilterChange(MediaFilter.PHOTOS) }
                            )
                            FilterChipItem(
                                label = "Videos",
                                selected = filter == MediaFilter.VIDEOS,
                                onClick = { onFilterChange(MediaFilter.VIDEOS) }
                            )
                            FilterChipItem(
                                label = "Locked",
                                selected = filter == MediaFilter.LOCKED,
                                onClick = { onFilterChange(MediaFilter.LOCKED) }
                            )
                        }

                        // Right Action Buttons: Filter expander & Slideshow
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Date/Folder Filter Toggle Button
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isFilterPanelExpanded || dateFilter != DateFilterPeriod.ALL || folderFilter != null) BentoAccentSubtle else BentoSurface)
                                    .border(1.dp, if (dateFilter != DateFilterPeriod.ALL || folderFilter != null) BentoAccent else BentoBorder, CircleShape)
                                    .clickable { isFilterPanelExpanded = !isFilterPanelExpanded }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Date & Folder Filter",
                                        tint = if (dateFilter != DateFilterPeriod.ALL || folderFilter != null) BentoAccent else BentoTextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (dateFilter != DateFilterPeriod.ALL) dateFilter.label else "Filter",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (dateFilter != DateFilterPeriod.ALL || folderFilter != null) BentoAccent else BentoTextSecondary
                                    )
                                }
                            }

                            // Start Slideshow Button
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(BentoSurfaceCard)
                                    .border(1.dp, BentoBorder, CircleShape)
                                    .clickable { onStartSlideshow() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("gallery_slideshow_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Slideshow,
                                        contentDescription = "Slideshow",
                                        tint = BentoAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Slideshow",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BentoTextPrimary
                                    )
                                }
                            }
                        }
                    }

                    // Expanded Date & Folder Filter Rows
                    AnimatedVisibility(visible = isFilterPanelExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            // Date Period Filter Chips
                            Text(
                                text = "FILTER BY DATE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = BentoTextTertiary,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(DateFilterPeriod.values()) { period ->
                                    FilterChipItem(
                                        label = period.label,
                                        selected = dateFilter == period,
                                        onClick = { onDateFilterChange(period) }
                                    )
                                }
                            }

                            // Folder Filter Chips
                            if (availableFolders.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "FILTER BY FOLDER",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = BentoTextTertiary,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    item {
                                        FilterChipItem(
                                            label = "All Folders",
                                            selected = folderFilter == null,
                                            onClick = { onFolderFilterChange(null) }
                                        )
                                    }
                                    items(availableFolders) { folder ->
                                        FilterChipItem(
                                            label = folder,
                                            selected = folderFilter == folder,
                                            onClick = {
                                                onFolderFilterChange(if (folderFilter == folder) null else folder)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Partial Access Banner (Android 14+ Scoped Storage)
            if (permissionState == StoragePermissionState.PARTIAL) {
                Surface(
                    color = BentoAccentSubtle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onRequestPermission() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Limited photo access granted",
                            style = MaterialTheme.typography.bodySmall,
                            color = BentoAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Select more...",
                            style = MaterialTheme.typography.labelMedium,
                            color = BentoAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Permission Denied State
            if (permissionState == StoragePermissionState.DENIED) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(28.dp))
                            .background(BentoSurface)
                            .border(1.dp, BentoBorder, RoundedCornerShape(28.dp))
                            .padding(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(BentoSurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = BentoAccent,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Storage Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BentoTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Private Gallery needs storage access to show your photos and videos. No files leave your device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BentoTextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(BentoAccent)
                                    .clickable { onRequestPermission() }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text("Grant Access", color = BentoAccentContainer, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(BentoSurfaceElevated)
                                    .border(1.dp, BentoBorder, RoundedCornerShape(16.dp))
                                    .clickable { onOpenSettings() }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text("App Settings", color = BentoTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BentoAccent, modifier = Modifier.size(36.dp))
                }
            } else if (mediaItems.isEmpty()) {
                // Empty State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(28.dp))
                            .background(BentoSurface)
                            .border(1.dp, BentoBorder, RoundedCornerShape(28.dp))
                            .padding(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(BentoSurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = BentoAccent,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (filter == MediaFilter.LOCKED) "No Locked Media" else "No Media Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BentoTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (filter == MediaFilter.LOCKED)
                                "Apply Deletion Lock to photos to protect them with your Master PIN or biometrics."
                            else "No photos match your current search, date, or folder filters.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BentoTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Bento Vertical Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = if (isSelectionMode) 100.dp else 96.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("media_lazy_grid")
                ) {
                    // Bento Hero Card (Full Width Span, shown when not in selection mode)
                    if (!isSelectionMode) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            BentoHeroCard(
                                mediaCount = mediaItems.size,
                                selectedAlbum = selectedAlbum,
                                coverUri = mediaItems.firstOrNull()?.uri,
                                onClearAlbumFilter = onClearAlbumFilter,
                                onStartSlideshow = onStartSlideshow,
                                onEnterSelection = { onToggleSelectionMode(true) }
                            )
                        }
                    }

                    // Grid of Media Thumbnails
                    items(
                        items = mediaItems,
                        key = { it.id },
                        contentType = { if (it.isVideo) "video" else "photo" }
                    ) { item ->
                        val isSelected = selectedItemIds.contains(item.id)
                        BentoMediaThumbnailItem(
                            item = item,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelectionMode) {
                                    onToggleItemSelection(item.id)
                                } else {
                                    onItemClick(item)
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    onItemLongClick(item)
                                } else {
                                    onToggleItemSelection(item.id)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Floating Bulk Actions Bar (Sticky at bottom during Selection Mode)
        AnimatedVisibility(
            visible = isSelectionMode && selectedItemIds.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            BentoBulkActionsBar(
                selectedCount = selectedItemIds.size,
                onBulkLock = onBulkLock,
                onBulkVault = onBulkVault,
                onBulkDelete = onBulkDelete,
                onBulkShare = onBulkShare,
                onBulkSlideshow = onStartSlideshow
            )
        }
    }
}

/**
 * Top selection header showing count, select all, deselect, and close.
 */
@Composable
fun BentoSelectionHeader(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onCloseSelection: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BentoSurface)
            .border(1.dp, BentoBorder)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = onCloseSelection,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(BentoSurfaceCard)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit Selection",
                    tint = BentoTextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = BentoTextPrimary
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selectedCount < totalCount) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(BentoAccentSubtle)
                        .border(1.dp, BentoAccent, CircleShape)
                        .clickable { onSelectAll() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Select All", color = BentoAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(BentoSurfaceCard)
                        .border(1.dp, BentoBorder, CircleShape)
                        .clickable { onDeselectAll() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Deselect", color = BentoTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/**
 * Sticky floating Bento Bulk Actions bottom toolbar.
 */
@Composable
fun BentoBulkActionsBar(
    selectedCount: Int,
    onBulkLock: () -> Unit,
    onBulkVault: () -> Unit,
    onBulkDelete: () -> Unit,
    onBulkShare: () -> Unit,
    onBulkSlideshow: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = BentoSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BulkActionButton(
                icon = Icons.Default.Lock,
                label = "Lock",
                tint = BentoAccent,
                onClick = onBulkLock
            )
            BulkActionButton(
                icon = Icons.Default.VisibilityOff,
                label = "Vault",
                tint = BentoTextPrimary,
                onClick = onBulkVault
            )
            BulkActionButton(
                icon = Icons.Default.Slideshow,
                label = "Slideshow",
                tint = BentoTextPrimary,
                onClick = onBulkSlideshow
            )
            BulkActionButton(
                icon = Icons.Default.Share,
                label = "Share",
                tint = BentoTextPrimary,
                onClick = onBulkShare
            )
            BulkActionButton(
                icon = Icons.Default.Delete,
                label = "Delete",
                tint = BentoRed,
                onClick = onBulkDelete
            )
        }
    }
}

@Composable
private fun BulkActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint
        )
    }
}

/**
 * Bento Grid Featured Hero Card
 */
@Composable
fun BentoHeroCard(
    mediaCount: Int,
    selectedAlbum: String?,
    coverUri: android.net.Uri?,
    onClearAlbumFilter: () -> Unit,
    onStartSlideshow: () -> Unit,
    onEnterSelection: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(BentoSurface)
            .border(1.dp, BentoBorder, RoundedCornerShape(28.dp))
            .testTag("bento_hero_card")
    ) {
        // Background photo preview with dark gradient
        if (coverUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverUri)
                    .size(600, 300)
                    .scale(Scale.FILL)
                    .precision(Precision.INEXACT)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x88111318),
                            Color(0xEE111318)
                        )
                    )
                )
        )

        // Top Actions Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "Select" button to enter multi-select
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(BentoSurface.copy(alpha = 0.85f))
                    .border(1.dp, BentoBorder, CircleShape)
                    .clickable { onEnterSelection() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Select items",
                        tint = BentoTextPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text("Select", color = BentoTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // DELETION LOCK badge (Bento Design Pill)
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(BentoAccent)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = BentoAccentContainer,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "DELETION LOCK",
                        color = BentoAccentContainer,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Bottom Details & Quick Slideshow Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedAlbum ?: "Recent Memories",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$mediaCount items • Protected by VaultCore",
                        style = MaterialTheme.typography.bodySmall,
                        color = BentoTextSecondary
                    )
                    if (selectedAlbum != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(BentoAccentSubtle)
                                .clickable { onClearAlbumFilter() }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Clear", color = BentoAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Quick Slideshow Play FAB on Hero card
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(BentoAccent)
                    .clickable { onStartSlideshow() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Start Slideshow",
                    tint = BentoAccentContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = BentoAccentSubtle,
            selectedLabelColor = BentoAccent,
            containerColor = BentoSurface,
            labelColor = BentoTextSecondary
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = BentoBorder,
            selectedBorderColor = BentoAccent,
            enabled = true,
            selected = selected
        )
    )
}

/**
 * Thumbnail cell with Bento rounded-[24dp] styling, Coil downsampling,
 * long-press multi-select, and Deletion Lock badges.
 */
@Composable
fun BentoMediaThumbnailItem(
    item: MediaItem,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(BentoSurfaceCard)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) BentoAccent else BentoBorder,
                shape = RoundedCornerShape(24.dp)
            )
            .pointerInput(isSelectionMode, isSelected) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            }
            .testTag("media_item_${item.id}")
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.uri)
                // Memory safe downsampling
                .size(280, 280)
                .scale(Scale.FILL)
                .precision(Precision.INEXACT)
                .crossfade(true)
                .build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Bottom subtle gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
        )

        // Selection Checkbox Badge (Top-Start)
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) BentoAccent else Color.Black.copy(alpha = 0.45f))
                    .border(
                        1.5.dp,
                        if (isSelected) BentoAccent else Color.White.copy(alpha = 0.8f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = BentoAccentContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Deletion Lock Indicator (Bento Theme Pill Badge)
        if (item.isLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(BentoAccent)
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = BentoAccentContainer,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        // Video Indicator
        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        tint = BentoTextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
