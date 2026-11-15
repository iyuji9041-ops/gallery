package com.photo.gallery.data.mediastore

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.photo.gallery.data.model.ExifData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

object ExifHelper {

    suspend fun extractExif(context: Context, uri: Uri, path: String?): ExifData = withContext(Dispatchers.IO) {
        try {
            var inputStream: InputStream? = null
            val exifInterface = try {
                if (path != null && File(path).exists()) {
                    ExifInterface(path)
                } else {
                    inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) ExifInterface(inputStream) else null
                }
            } catch (e: Exception) {
                null
            } finally {
                inputStream?.close()
            }

            if (exifInterface == null) return@withContext ExifData()

            val make = exifInterface.getAttribute(ExifInterface.TAG_MAKE)
            val model = exifInterface.getAttribute(ExifInterface.TAG_MODEL)
            val date = exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exifInterface.getAttribute(ExifInterface.TAG_DATETIME)
            val fNumber = exifInterface.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { "f/$it" }
            val exposure = exifInterface.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let {
                val value = it.toDoubleOrNull()
                if (value != null && value < 1.0 && value > 0) {
                    "1/${(1.0 / value).toInt()}s"
                } else {
                    "${it}s"
                }
            }
            val iso = exifInterface.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                ?: exifInterface.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
            val focalLength = exifInterface.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let {
                val num = it.split("/").firstOrNull()?.toDoubleOrNull()
                val den = it.split("/").getOrNull(1)?.toDoubleOrNull() ?: 1.0
                if (num != null) "%.1f mm".format(num / den) else "${it}mm"
            }
            val width = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)
            val height = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)
            val resolution = if (!width.isNullOrEmpty() && !height.isNullOrEmpty()) "${width} × ${height}" else null

            val latLong = FloatArray(2)
            val hasLatLong = exifInterface.getLatLong(latLong)

            ExifData(
                cameraMake = make,
                cameraModel = model,
                dateTaken = date,
                aperture = fNumber,
                shutterSpeed = exposure,
                iso = iso?.let { "ISO $it" },
                focalLength = focalLength,
                resolution = resolution,
                latitude = if (hasLatLong) latLong[0].toDouble() else null,
                longitude = if (hasLatLong) latLong[1].toDouble() else null
            )
        } catch (e: Exception) {
            ExifData()
        }
    }
}
