package com.hopa.capabilities.display

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.view.Surface
import java.io.ByteArrayOutputStream

/**
 * A virtual display owned by the helper. Apps launched onto it never appear on
 * the phone's screen; frames arrive in this process through an ImageReader, so a
 * screenshot does not need the accessibility screenshot API.
 */
class DisplayHost(
    private val displayManager: DisplayManager,
    private val width: Int,
    private val height: Int,
    dpi: Int,
) {
    private val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    private val display: VirtualDisplay = displayManager.createVirtualDisplay(
        "hopa-agent",
        width,
        height,
        dpi,
        reader.surface,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
    )

    val displayId: Int
        get() = display.display.displayId

    /** The newest frame as JPEG, or null when the display has not drawn yet. */
    fun frameJpeg(quality: Int = 85): ByteArray? {
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

    fun release() {
        runCatching { display.release() }
        runCatching { reader.close() }
    }

    companion object {
        /** The surface the helper puts apps on, for tests and diagnostics. */
        fun surfaceOf(host: DisplayHost): Surface = host.reader.surface
    }
}
