package com.photo.gallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.photo.gallery.data.model.AlbumItem
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

/**
 * Albums Screen rendered with the Bento Grid design theme:
 * Distinct rounded-28dp cards, #252429 background, subtle #44474E borders,
 * lavender indicators, and Deletion Lock controls.
 */
@Composable
fun AlbumScreen(
    albums: List<AlbumItem>,
    onAlbumClick: (String) -> Unit,
    onToggleAlbumLock: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BentoBg)
            .testTag("album_screen")
    ) {
        // Top Subtitle Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(BentoAccentSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = BentoAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Albums & Collections",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BentoTextPrimary
                )
                Text(
                    text = "${albums.size} organized directories",
                    style = MaterialTheme.typography.bodySmall,
                    color = BentoTextSecondary
                )
            }
        }

        if (albums.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No folders found", color = BentoTextSecondary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3), // was 2 - user requested Xiaomi-Gallery-style 3-column density
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(albums, key = { it.name }) { album ->
                    BentoAlbumCard(
                        album = album,
                        onClick = { onAlbumClick(album.name) },
                        onToggleLock = { onToggleAlbumLock(album.name) }
                    )
                }
            }
        }
    }
}

/**
 * Bento Album Card matching Design HTML:
 * rounded-[28px] bg-[#252429] border border-[#44474E] p-4 flex flex-col justify-between
 */
@Composable
private fun BentoAlbumCard(
    album: AlbumItem,
    onClick: () -> Unit,
    onToggleLock: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .border(
                width = 1.dp,
                color = if (album.isLocked) BentoAccent else BentoBorder,
                shape = RoundedCornerShape(28.dp)
            )
            .clickable { onClick() }
            .testTag("album_card_${album.name}"),
        colors = CardDefaults.cardColors(containerColor = BentoSurfaceCard)
    ) {
        Column {
            // Album Cover
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .background(BentoSurface)
            ) {
                if (album.coverUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(album.coverUri)
                            .size(320, 320)
                            .scale(Scale.FILL)
                            .precision(Precision.INEXACT)
                            .crossfade(true)
                            .build(),
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                album.name.contains("Camera", ignoreCase = true) -> Icons.Default.CameraAlt
                                album.name.contains("Screenshot", ignoreCase = true) -> Icons.Default.Screenshot
                                album.name.contains("Download", ignoreCase = true) -> Icons.Default.Download
                                else -> Icons.Default.Folder
                            },
                            contentDescription = null,
                            tint = BentoTextTertiary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // Top right Lock Toggle Pill
                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (album.isLocked) BentoAccent else Color.Black.copy(alpha = 0.6f))
                        .testTag("album_lock_toggle_${album.name}")
                ) {
                    Icon(
                        imageVector = if (album.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (album.isLocked) "Unlock Album" else "Lock Album",
                        tint = if (album.isLocked) BentoAccentContainer else BentoTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                )
            }

            // Album Info
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = BentoTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${album.itemCount} Photos",
                        style = MaterialTheme.typography.bodySmall,
                        color = BentoTextSecondary
                    )
                    if (album.isLocked) {
                        Text(
                            text = "LOCKED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoAccent,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}
