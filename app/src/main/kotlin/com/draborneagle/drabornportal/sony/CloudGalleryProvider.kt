package com.draborneagle.drabornportal.sony

/**
 * Boundary for the undocumented Sony Cloud Gallery integration.
 *
 * v0.1 deliberately keeps this separate from OCR/translation so a Sony API
 * change cannot break the local translation engine.
 */
interface CloudGalleryProvider {
    suspend fun getLatestScreenshot(afterCaptureId: String? = null): CloudCapture?
}

data class CloudCapture(
    val id: String,
    val downloadUrl: String,
    val createdAtEpochMs: Long,
)

/**
 * Placeholder for the independently implemented experimental Sony connector.
 * Authentication tokens must later live in encrypted Android storage and must
 * never be committed or printed to logs.
 */
class SonyCloudGalleryProvider : CloudGalleryProvider {
    override suspend fun getLatestScreenshot(afterCaptureId: String?): CloudCapture? {
        return null
    }
}
