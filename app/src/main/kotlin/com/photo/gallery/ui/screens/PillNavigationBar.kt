package com.photo.gallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photo.gallery.ui.theme.BentoAccent
import com.photo.gallery.ui.theme.BentoAccentSubtle
import com.photo.gallery.ui.theme.BentoBorder
import com.photo.gallery.ui.theme.BentoSurface
import com.photo.gallery.ui.theme.BentoTextSecondary
import com.photo.gallery.ui.viewmodel.GalleryTab

/**
 * Floating Pill Navigation Bar (Phase 4):
 * - Elevated capsule shape floating above bottom system navigation.
 * - Supports gesture navigation and 3-button navigation seamlessly.
 * - Fully accessible with Role.Tab semantics for TalkBack.
 * - Adapts cleanly to small screens, tablets, and font scale 1.3+.
 * - Hidden Vault tab kept hidden (accessible via secret code in search bar).
 */
@Composable
fun PillNavigationBar(
    currentTab: GalleryTab,
    onTabSelected: (GalleryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .testTag("gallery_bottom_nav"),
        contentAlignment = Alignment.Center
    ) {
        // Floating pill capsule
        Box(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .shadow(elevation = 16.dp, shape = CircleShape, spotColor = BentoAccent.copy(alpha = 0.25f))
                .clip(CircleShape)
                .background(BentoSurface.copy(alpha = 0.95f))
                .border(width = 1.dp, color = BentoBorder, shape = CircleShape)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PillNavItem(
                    icon = Icons.Default.PhotoLibrary,
                    label = "Photos",
                    isSelected = currentTab == GalleryTab.TIMELINE,
                    onClick = { onTabSelected(GalleryTab.TIMELINE) },
                    testTag = "nav_tab_photos",
                    contentDesc = "Photos tab, shows all images and videos"
                )

                PillNavItem(
                    icon = Icons.Default.Folder,
                    label = "Albums",
                    isSelected = currentTab == GalleryTab.ALBUMS,
                    onClick = { onTabSelected(GalleryTab.ALBUMS) },
                    testTag = "nav_tab_albums",
                    contentDesc = "Albums tab, organized folders"
                )

                // Vault tab remains hidden from nav bar to keep vault secret (Problem 9 / Phase 4)

                PillNavItem(
                    icon = Icons.Default.Security,
                    label = "Setup",
                    isSelected = currentTab == GalleryTab.SETTINGS,
                    onClick = { onTabSelected(GalleryTab.SETTINGS) },
                    testTag = "nav_tab_settings",
                    contentDesc = "Setup tab, security and app preferences"
                )
            }
        }
    }
}

@Composable
private fun PillNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    contentDesc: String
) {
    Column(
        modifier = Modifier
            .semantics {
                this.role = Role.Tab
                this.selected = isSelected
                this.contentDescription = contentDesc
            }
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Active indicator pill
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (isSelected) BentoAccentSubtle else androidx.compose.ui.graphics.Color.Transparent)
                .padding(horizontal = if (isSelected) 16.dp else 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, // Handled by Tab semantics
                tint = if (isSelected) BentoAccent else BentoTextSecondary.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = label.uppercase(),
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
            letterSpacing = 1.2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isSelected) BentoAccent else BentoTextSecondary.copy(alpha = 0.7f)
        )
    }
}
