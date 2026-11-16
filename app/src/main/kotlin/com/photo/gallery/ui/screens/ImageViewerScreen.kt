package com.photo.gallery.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.photo.gallery.data.model.MediaItem
import com.photo.gallery.ui.theme.BentoAccent
import com.photo.gallery.ui.theme.BentoAccentContainer
import com.photo.gallery.ui.theme.BentoBg
import com.photo.gallery.ui.theme.BentoBorder
import com.photo.gallery.ui.theme.BentoRose
import com.photo.gallery.ui.theme.BentoSurface
import com.photo.gallery.ui.theme.BentoTextPrimary
import com.photo.gallery.ui.theme.BentoTextSecondary
import androidx.media3.common.MediaItem as Media3MediaItem

/**
 * Full-screen media viewer with Bento Grid styling.
 *
 * BUG FIXES applied here vs the original single-item version:
 * 1. SWIPE NAVIGATION: the old version only ever received a single [MediaItem] with
 *    no concept of "the next/previous item" - swiping did nothing because there was
 *    no page/list structure at all, only pinch-zoom-pan gestures on one static image.
 *    Now the whole surrounding [items] list + a starting index drive a HorizontalPager,
 *    so a left/right swipe genuinely moves between adjacent photos/videos.
 * 2. VIDEO PLAYBACK: the old version always rendered Coil's AsyncImage regardless of
 *    mimeType, which can only ever show a static frame (if at all) - it has no player,
 *    no play/pause, no audio. Video items now render an ExoPlayer-backed PlayerView
 *    with standard playback controls instead.
 *
 * [onPageChanged] keeps the ViewModel's `activeViewerItem` in sync as the user swipes,
 * so downstream UI (lock badge, EXIF sheet, delete/vault actions) keeps working exactly
 * as before without every one of those call sites needing to know about paging.
 */
@Composable
fun ImageViewerScreen(
    items: List<MediaItem>,
    initialIndex: Int,
    onBack: () -> Unit,
    onToggleLock: (MediaItem) -> Unit,
    onMoveToVault: (MediaItem) -> Unit,
    onShowExif: () -> Unit,
    onDeleteRequest: (MediaItem) -> Unit,
    onPageChanged: (MediaItem) -> Unit
) {
    if (items.isEmpty()) return
    val safeInitialIndex = initialIndex.coerceIn(0, items.lastIndex)
    val pagerState = rememberPagerState(initialPage = safeInitialIndex) { items.size }
    var showControls by remember { mutableStateOf(true) }
    val context = LocalContext.current

    // Keep the ViewModel's activeViewerItem in sync as the user swipes between pages.
    LaunchedEffect(pagerState.currentPage) {
        items.getOrNull(pagerState.currentPage)?.let(onPageChanged)
    }

    val currentItem = items.getOrNull(pagerState.currentPage) ?: items[safeInitialIndex]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("image_viewer_screen")
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pageItem = items[page]
            val isVideo = pageItem.mimeType.startsWith("video/")
            if (isVideo) {
                // Video pages sync `showControls` FROM ExoPlayer's own controller visibility
                // (see VideoPage) instead of a separate Compose tap detector, since
                // PlayerView consumes taps itself before Compose ever sees them.
                VideoPage(
                    uri = pageItem.uri,
                    onControlsVisibilityChanged = { visible -> showControls = visible }
                )
            } else {
                PhotoPage(
                    item = pageItem,
                    onTap = { showControls = !showControls },
                    onSwipeDownDismiss = onBack
                )
            }
        }

        // Top App Bar Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(BentoSurface.copy(alpha = 0.8f))
                        .border(1.dp, BentoBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("viewer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = BentoTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentItem.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = BentoTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${currentItem.albumName} • ${currentItem.formattedSize} • ${pagerState.currentPage + 1}/${items.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = BentoTextSecondary
                    )
                }

                if (currentItem.isLocked) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(CircleShape)
                            .background(BentoAccent)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = BentoAccentContainer,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "LOCKED",
                                color = BentoAccentContainer,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Bottom Action Bar Controls
        //
        // BUG FIX: on video pages, ExoPlayer's own PlayerView draws its built-in seek
        // bar + time codes anchored to the very bottom of the FULL-SCREEN PlayerView
        // (since we set its layoutParams to MATCH_PARENT). Our custom action bar was
        // also anchored to the screen's bottom edge, so the two visually collided -
        // Exo's scrub bar/timecodes were rendered right underneath (and hidden by) our
        // Share/Lock/Vault/Info/Delete row. Reserving ~56dp of empty space below our
        // bar ONLY on video pages pushes it up just enough to sit above Exo's seek bar
        // instead of on top of it, without affecting the photo-page layout at all.
        val isCurrentVideo = currentItem.mimeType.startsWith("video/")
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isCurrentVideo) 56.dp else 0.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = BentoSurface.copy(alpha = 0.9f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BentoBorder)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ViewerActionButton(
                        icon = Icons.Default.Share,
                        label = "Share",
                        tint = BentoTextPrimary,
                        tag = "viewer_share_button",
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = currentItem.mimeType
                                putExtra(Intent.EXTRA_STREAM, currentItem.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share via"))
                        }
                    )
                    ViewerActionButton(
                        icon = if (currentItem.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        label = if (currentItem.isLocked) "Locked" else "Lock",
                        tint = if (currentItem.isLocked) BentoAccent else BentoTextSecondary,
                        tag = "viewer_lock_toggle_button",
                        onClick = { onToggleLock(currentItem) }
                    )
                    ViewerActionButton(
                        icon = Icons.Default.VisibilityOff,
                        label = "Vault",
                        tint = BentoAccent,
                        tag = "viewer_vault_button",
                        onClick = { onMoveToVault(currentItem) }
                    )
                    ViewerActionButton(
                        icon = Icons.Default.Info,
                        label = "Info",
                        tint = BentoTextPrimary,
                        tag = "viewer_info_button",
                        onClick = onShowExif
                    )
                    ViewerActionButton(
                        icon = Icons.Default.Delete,
                        label = "Delete",
                        tint = BentoRose,
                        tag = "viewer_delete_button",
                        onClick = { onDeleteRequest(currentItem) }
                    )
                }
            }
        }
    }
}

/**
 * One pager page for a photo: pinch-to-zoom, pan (while zoomed), and swipe-down-to-dismiss.
 *
 * BUG FIX: the previous version wrapped the whole page in
 * androidx.compose.foundation.gestures.detectTransformGestures, which consumes EVERY
 * pointer movement it sees - including a plain single-finger horizontal swipe. That
 * meant the touch never reached the parent HorizontalPager at all, so swiping to the
 * next/previous photo silently did nothing (the pager's own drag detector was starved
 * of events). The fix uses a hand-rolled awaitEachGesture instead, which only
 * consumes touches when there are genuinely 2+ pointers down (an actual pinch) or the
 * photo is already zoomed in (so a one-finger drag should pan the zoomed image rather
 * than page-swipe). A one-finger drag while NOT zoomed in is left completely
 * un-consumed, so the pager sees it and swipes normally.
 */
@Composable
private fun PhotoPage(item: MediaItem, onTap: () -> Unit, onSwipeDownDismiss: () -> Unit) {
    val context = LocalContext.current
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(item.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(item.id) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .pointerInput(item.id) {
                awaitEachGesture {
                    // Wait for the first finger down, then track the gesture manually so
                    // we can decide per-event whether to consume it (see doc comment above).
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoomedIn = scale > 1f
                        val isPinch = event.changes.size > 1

                        if (isPinch || zoomedIn) {
                            // Real pinch, or already zoomed in - this gesture belongs to
                            // US, not the pager. Compute pan/zoom and consume the events.
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            scale = (scale * zoomChange).coerceIn(0.8f, 5f)
                            if (scale > 1f) {
                                offsetX += panChange.x
                                offsetY += panChange.y
                            } else {
                                offsetY += panChange.y
                                if (offsetY > 300f) onSwipeDownDismiss()
                            }
                            event.changes.forEach { it.consume() }
                        }
                        // else: single finger, not zoomed in - deliberately DON'T consume,
                        // so the ancestor HorizontalPager receives this drag and can swipe.
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.uri)
                .crossfade(true)
                .build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}

/**
 * One pager page for a video: an ExoPlayer-backed PlayerView with standard controls.
 *
 * BUG FIX: previously this had its OWN Compose-level tap detector calling a separate
 * onTap toggle - but PlayerView is a native View that consumes touch events for its
 * own built-in controller (play/pause/seek/skip buttons), so that Compose tap detector
 * never actually fired. The result was our custom Share/Lock/Vault/Info/Delete bar
 * staying visible forever, overlapping ExoPlayer's own seek bar at the bottom.
 * Fix: listen directly to PlayerView's controller visibility via
 * setControllerVisibilityListener and mirror that into our own showControls state,
 * so both bars appear/disappear together, in sync, exactly when the user taps the
 * video (which PlayerView already detects correctly on its own).
 */
@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(UnstableApi::class)
@Composable
private fun VideoPage(uri: Uri, onControlsVisibilityChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(Media3MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    // Release the player when this page leaves composition (swiped away, or viewer closed)
    // to avoid leaking decoder resources - a very common source of playback bugs/crashes
    // when a video viewer doesn't clean up after itself.
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BentoBg),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            onControlsVisibilityChanged(visibility == android.view.View.VISIBLE)
                        }
                    )
                }
            }
        )
    }
}

@Composable
private fun ViewerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    tag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(tag)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(42.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontSize = 11.sp
        )
    }
}
