package com.hopa.capabilities.display

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Build
import android.view.Surface
import java.io.ByteArrayOutputStream

/**
 * A virtual display owned by the helper. Apps launched onto it never appear on
 * the phone's screen; frames arrive in this process through an ImageReader, so a
 * screenshot does not need the accessibility screenshot API.
 *
 * The flags follow the set a shell-run display needs on current Android: a
 * public display that owns its content, supports touch, and does not leave a
 * "trusted display" permission behind. The trusted extras (own display group,
 * always unlocked, own focus) are tried first and dropped on SecurityException,
 * because not every build lets shell add a trusted display.
 */
class DisplayHost(
    private val displayManager: DisplayManager,
    private val width: Int,
    private val height: Int,
    private val dpi: Int,
) {
    private val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    private val display: VirtualDisplay = create()

    val displayId: Int
        get() = display.display.displayId

    init {
        windowing(displayId)
    }

    /**
     * Fullscreen windowing mode (1) and a local IME policy (0). Without them an
     * activity can open in a window that does not match the display size, which
     * makes every tap land somewhere else.
     */
    private fun windowing(id: Int) {
        runCatching {
            val wm = Class.forName("android.view.WindowManagerGlobal")
                .getMethod("getWindowManagerService")
                .invoke(null) ?: return@runCatching
            val int = Int::class.javaPrimitiveType
            wm.javaClass.getMethod("setWindowingMode", int, int).invoke(wm, id, 1)
            wm.javaClass.getMethod("setDisplayImePolicy", int, int).invoke(wm, id, 0)
        }.onFailure {
            runCatching {
                ProcessBuilder("sh", "-c", "wm set-display-windowing-mode -d $id 1")
                    .start()
                    .waitFor()
            }
        }
    }

    private fun create(): VirtualDisplay {
        val attempts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(flags(trusted = true), flags(trusted = false))
        } else {
            listOf(flags(trusted = false))
        }
        var last: Throwable? = null
        for (flags in attempts) {
            try {
                val created = displayManager.createVirtualDisplay(
                    "hopa-agent",
                    width,
                    height,
                    dpi,
                    reader.surface,
                    flags,
                )
                if (created != null) {
                    System.err.println("display flags: ${Integer.toHexString(flags)}")
                    System.err.flush()
                    return created
                }
            } catch (error: SecurityException) {
                // ADD_TRUSTED_DISPLAY is not granted to every shell; the plain
                // flags still give a public display, just without the extras.
                last = error
            }
        }
        throw IllegalStateException(
            "createVirtualDisplay refused: ${last?.message ?: "no display returned"}",
        )
    }

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

        /**
         * PUBLIC keeps the display launchable by ActivityManager; OWN_CONTENT_ONLY
         * stops it mirroring the phone; SUPPORTS_TOUCH is what lets injected taps
         * land on an app instead of being swallowed.
         */
        fun flags(trusted: Boolean): Int {
            var flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                SUPPORTS_TOUCH or
                ROTATES_WITH_CONTENT or
                DESTROY_CONTENT_ON_REMOVAL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                flags = flags or TOUCH_FEEDBACK_DISABLED
            }
            if (trusted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                flags = flags or TRUSTED or OWN_DISPLAY_GROUP or ALWAYS_UNLOCKED
            }
            if (trusted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                flags = flags or OWN_FOCUS or STEAL_TOP_FOCUS_DISABLED
            }
            return flags
        }

        // Not all of these are public constants on every SDK level.
        private const val SUPPORTS_TOUCH = 1 shl 6
        private const val ROTATES_WITH_CONTENT = 1 shl 7
        private const val DESTROY_CONTENT_ON_REMOVAL = 1 shl 8
        private const val TOUCH_FEEDBACK_DISABLED = 1 shl 13
        private const val TRUSTED = 1 shl 10
        private const val OWN_DISPLAY_GROUP = 1 shl 11
        private const val ALWAYS_UNLOCKED = 1 shl 12
        private const val OWN_FOCUS = 1 shl 14
        private const val STEAL_TOP_FOCUS_DISABLED = 1 shl 16
    }
}
