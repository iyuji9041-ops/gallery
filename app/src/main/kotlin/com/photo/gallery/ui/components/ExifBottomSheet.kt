package com.photo.gallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoSizeSelectActual
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photo.gallery.data.model.ExifData
import com.photo.gallery.data.model.MediaItem
import com.photo.gallery.ui.theme.BentoAccent
import com.photo.gallery.ui.theme.BentoAccentSubtle
import com.photo.gallery.ui.theme.BentoBorder
import com.photo.gallery.ui.theme.BentoSurface
import com.photo.gallery.ui.theme.BentoSurfaceCard
import com.photo.gallery.ui.theme.BentoTextPrimary
import com.photo.gallery.ui.theme.BentoTextSecondary
import com.photo.gallery.ui.theme.BentoTextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExifBottomSheet(
    item: MediaItem,
    exifData: ExifData?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BentoSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BentoBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("exif_bottom_sheet")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(BentoAccentSubtle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "EXIF Details",
                        tint = BentoAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Media Details & EXIF",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BentoTextPrimary
                    )
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = BentoTextSecondary,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    BentoExifInfoCard(
                        title = "File Information",
                        items = listOf(
                            ExifRowData(Icons.Default.Storage, "File Size", item.formattedSize),
                            ExifRowData(Icons.Default.PhotoSizeSelectActual, "Resolution", exifData?.resolution ?: "${item.width} × ${item.height}"),
                            ExifRowData(Icons.Default.Folder, "Album", item.albumName),
                            ExifRowData(Icons.Default.CalendarToday, "Date", exifData?.dateTaken ?: "Recorded in MediaStore")
                        )
                    )
                }

                if (exifData?.cameraModel != null || exifData?.aperture != null) {
                    item {
                        BentoExifInfoCard(
                            title = "Camera Specifications",
                            items = listOfNotNull(
                                exifData.cameraModel?.let { ExifRowData(Icons.Default.CameraAlt, "Device", "${exifData.cameraMake ?: ""} $it".trim()) },
                                exifData.aperture?.let { ExifRowData(Icons.Default.Camera, "Aperture", it) },
                                exifData.shutterSpeed?.let { ExifRowData(Icons.Default.Speed, "Shutter Speed", it) },
                                exifData.iso?.let { ExifRowData(Icons.Default.Camera, "ISO Rating", it) },
                                exifData.focalLength?.let { ExifRowData(Icons.Default.Camera, "Focal Length", it) }
                            )
                        )
                    }
                }

                if (exifData?.latitude != null && exifData.longitude != null) {
                    item {
                        BentoExifInfoCard(
                            title = "Location Coordinates",
                            items = listOf(
                                ExifRowData(
                                    Icons.Default.LocationOn,
                                    "GPS",
                                    "%.4f, %.4f".format(exifData.latitude, exifData.longitude)
                                )
                            )
                        )
                    }
                }

                item {
                    BentoExifInfoCard(
                        title = "Storage Path",
                        items = listOf(
                            ExifRowData(Icons.Default.Folder, "Location", item.path.ifEmpty { item.uri.toString() })
                        )
                    )
                }
            }
        }
    }
}

data class ExifRowData(
    val icon: ImageVector,
    val label: String,
    val value: String
)

@Composable
private fun BentoExifInfoCard(
    title: String,
    items: List<ExifRowData>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BentoSurfaceCard)
            .border(1.dp, BentoBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = BentoAccent,
            fontSize = 11.sp,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        items.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = row.icon,
                    contentDescription = null,
                    tint = BentoTextTertiary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = BentoTextSecondary,
                    modifier = Modifier.width(95.dp)
                )
                Text(
                    text = row.value,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = BentoTextPrimary
                )
            }
            if (index < items.size - 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(BentoBorder.copy(alpha = 0.5f))
                        .padding(vertical = 2.dp)
                )
            }
        }
    }
}
