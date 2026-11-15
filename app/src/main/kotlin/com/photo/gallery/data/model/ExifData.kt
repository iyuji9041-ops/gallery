package com.photo.gallery.data.model

/**
 * EXIF metadata extracted from an image file.
 */
data class ExifData(
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val dateTaken: String? = null,
    val aperture: String? = null,
    val shutterSpeed: String? = null,
    val iso: String? = null,
    val focalLength: String? = null,
    val resolution: String? = null,
    val flash: String? = null,
    val orientation: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)
