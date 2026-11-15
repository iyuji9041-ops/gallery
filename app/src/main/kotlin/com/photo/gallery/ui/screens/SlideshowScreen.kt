package com.photo.gallery.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.photo.gallery.data.model.MediaItem
import com.photo.gallery.ui.theme.BentoAccent
import com.photo.gallery.ui.theme.BentoAccentContainer
import com.photo.gallery.ui.theme.BentoAccentSubtle
import com.photo.gallery.ui.theme.BentoBorder
import com.photo.gallery.ui.theme.BentoSurface
import com.photo.gallery.ui.theme.BentoSurfaceCard
import com.photo.gallery.ui.theme.BentoTextPrimary
import com.photo.gallery.ui.theme.BentoTextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen Slideshow Screen with Bento Grid styled controls.
 * Features automated playback, timer progression, speed controls,
 * shuffle mode, and manual navigation.
 */
@Composable
fun SlideshowScreen(
    items: List<MediaItem>,
    startIndex: Int = 0,
    onClose: () -> Unit
) {
    if (items.isEmpty()) {
        onClose()
        return
    }

    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var isShuffle by remember { mutableStateOf(false) }
    var delayMillis by remember { mutableLongStateOf(3000L) }
    var currentIndex by remember { mutableIntStateOf(startIndex.coerceIn(0, items.size - 1)) }
    var showControls by remember { mutableStateOf(true) }
    var slideProgress by remember { mutableFloatStateOf(0f) }

    // Sequence list (standard or shuffled)
    val playList = remember(isShuffle, items) {
        if (isShuffle) items.shuffled() else items
    }

    val currentItem = playList.getOrNull(currentIndex) ?: items.first()

    // Slideshow auto-advance timer
    LaunchedEffect(isPlaying, currentIndex, delayMillis, playList.size) {
        if (isPlaying && playList.size > 1) {
            slideProgress = 0f
            val stepTime = 50L
            val steps = (delayMillis / stepTime).toInt()
            for (i in 1..steps) {
                delay(stepTime)
                slideProgress = i.toFloat() / steps
            }
            // Advance to next slide
            currentIndex = (currentIndex + 1) % playList.size
            slideProgress = 0f
        }
    }

    val formattedDate = remember(currentItem.dateAdded) {
        val millis = if (currentItem.dateAdded < 100_000_000_000L) currentItem.dateAdded * 1000L else currentItem.dateAdded
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("slideshow_screen")
            .pointerInput(Unit) {
                detectTapGestures(onTap = { showControls = !showControls })
            }
    ) {
        // Crossfade Image Display
        Crossfade(
            targetState = currentItem,
            animationSpec = tween(durationMillis = 600),
            label = "slideshow_crossfade"
        ) { item ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Top Slide Progress Bar
        LinearProgressIndicator(
            progress = { if (isPlaying) slideProgress else 1f },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.TopCenter),
            color = BentoAccent,
            trackColor = Color.Transparent
        )

        // Top Bar Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Close button
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(BentoSurface.copy(alpha = 0.85f))
                        .border(1.dp, BentoBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("slideshow_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Slideshow",
                            tint = BentoTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title and details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentItem.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = BentoTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${currentItem.albumName} • $formattedDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = BentoTextSecondary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Index Badge
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(BentoSurfaceCard)
                        .border(1.dp, BentoBorder, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${currentIndex + 1} / ${playList.size}",
                        color = BentoAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Bottom Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = BentoSurface.copy(alpha = 0.88f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BentoBorder)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Speed Selector Pills
                    Row(
                        modifier = Modifier.padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Speed:",
                            style = MaterialTheme.typography.labelSmall,
                            color = BentoTextSecondary,
                            fontSize = 11.sp
                        )
                        listOf(2000L to "2s", 3000L to "3s", 5000L to "5s").forEach { (duration, label) ->
                            val isSelected = delayMillis == duration
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSelected) BentoAccent else BentoSurfaceCard)
                                    .border(1.dp, if (isSelected) BentoAccent else BentoBorder, CircleShape)
                                    .clickable { delayMillis = duration }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) BentoAccentContainer else BentoTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Playback Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shuffle toggle
                        IconButton(
                            onClick = { isShuffle = !isShuffle },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isShuffle) BentoAccentSubtle else Color.Transparent)
                                .testTag("slideshow_shuffle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (isShuffle) BentoAccent else BentoTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Previous
                        IconButton(
                            onClick = {
                                currentIndex = if (currentIndex > 0) currentIndex - 1 else playList.size - 1
                                slideProgress = 0f
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(BentoSurfaceCard)
                                .border(1.dp, BentoBorder, CircleShape)
                                .testTag("slideshow_prev_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Slide",
                                tint = BentoTextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Play/Pause Main Button (Accent filled circle)
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(BentoAccent)
                                .clickable { isPlaying = !isPlaying }
                                .testTag("slideshow_play_pause_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = BentoAccentContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Next
                        IconButton(
                            onClick = {
                                currentIndex = (currentIndex + 1) % playList.size
                                slideProgress = 0f
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(BentoSurfaceCard)
                                .border(1.dp, BentoBorder, CircleShape)
                                .testTag("slideshow_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Slide",
                                tint = BentoTextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Lock indicator if item is locked
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (currentItem.isLocked) BentoAccentSubtle else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentItem.isLocked) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = BentoAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
