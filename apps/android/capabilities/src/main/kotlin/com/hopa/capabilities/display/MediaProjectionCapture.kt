package com.hopa.capabilities.display

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import java.io.ByteArrayOutputStream

/**
 * Screen capture through MediaProjection: the only screenshot path on Android 10
 * (API 29), because AccessibilityService.takeScreenshot() needs API 30. The user
 * grants it once through the system consent dialog; [ScreenCaptureService] holds
 * a foreground service while the projection is live. On API 30+ the accessibility
 * screenshot is used instead and this stays dormant.
 *
 * A single mirrored virtual display feeds frames into an ImageReader; [capture]
 * reads the newest frame as JPEG, the same way [DisplayHost] does for the shell
 * display.
 */
object MediaProjectionCapture {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var width = 0
    private var height = 0

    val isActive: Boolean
        @Synchronized get() = projection != null

    /** Called by the foreground service once the user has consented. */
    @Synchronized
    fun start(context: Context, resultCode: Int, data: Intent) {
        if (projection != null) {
            return
        }
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
            as? MediaProjectionManager ?: return
        val proj = runCatching { manager.getMediaProjection(resultCode, data) }.getOrNull() ?: return
        val metrics = context.resources.displayMetrics
        width = metrics.widthPixels
        height = metrics.heightPixels
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        proj.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() = stop()
            },
            null,
        )
        this.display = proj.createVirtualDisplay(
            "hopa-shot",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            null,
        )
        this.reader = reader
        this.projection = proj
    }

    /** The newest frame as JPEG, or null when nothing has been drawn yet. */
    @Synchronized
    fun capture(quality: Int = 85): ByteArray? {
        val reader = reader ?: return null
        val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return null
        return try {
            val plane = image.planes[0]
            val strideWidth = plane.rowStride / plane.pixelStride
            val bitmap = Bitmap.createBitmap(strideWidth, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(plane.buffer.rewind())
            val cropped = if (strideWidth == width) {
                bitmap
            } else {
                Bitmap.createBitmap(bitmap, 0, 0, width, height).also { bitmap.recycle() }
            }
            val out = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, quality, out)
            cropped.recycle()
            out.toByteArray()
        } finally {
            image.close()
        }
    }

    @Synchronized
    fun stop() {
        runCatching { display?.release() }
        runCatching { reader?.close() }
        runCatching { projection?.stop() }
        display = null
        reader = null
        projection = null
    }
}
