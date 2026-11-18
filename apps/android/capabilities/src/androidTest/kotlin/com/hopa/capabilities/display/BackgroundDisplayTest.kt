package com.hopa.capabilities.display

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hopa.capabilities.display.client.ShellClient
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The background display, checked on a real phone. These tests need the helper,
 * which is started from Settings through the PC bridge (or by hand with
 * app_process); without it every case is skipped rather than failed, so a plain
 * CI machine still gets a green run.
 */
@RunWith(AndroidJUnit4::class)
class BackgroundDisplayTest {
    private val client = ShellClient()
    private var displayId = -1

    @Before
    fun connect() {
        assumeTrue(
            "background helper is not running: start it from Settings first",
            client.connect(),
        )
        val created = client.display(720, 1600, 320)
        assumeTrue("helper refused a display: ${created.second}", created.first)
        displayId = created.second.toIntOrNull() ?: -1
        assumeTrue("helper returned no display id", displayId > 0)
    }

    @After
    fun release() {
        if (displayId > 0) {
            client.release()
        }
        client.close()
    }

    @Test
    fun the_display_is_a_real_display_and_draws() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotEquals(
            "a virtual display must not be the phone's own display",
            android.view.Display.DEFAULT_DISPLAY,
            displayId,
        )
        val manager = context.getSystemService(android.hardware.display.DisplayManager::class.java)
        assertTrue("display $displayId is not visible to the app", manager.getDisplay(displayId) != null)

        var frame: ByteArray? = null
        repeat(10) {
            if (frame == null) {
                Thread.sleep(300)
                frame = client.shot()
            }
        }
        assertTrue("the display never produced a frame", frame != null && frame!!.isNotEmpty())
    }

    @Test
    fun an_app_starts_on_the_background_display() {
        val launched = client.launch("com.android.settings")
        assumeTrue("settings is not on this phone: ${launched.second}", launched.first)
        Thread.sleep(2_000)
        assertTrue(
            "the foreground of the background display is ${client.top()}",
            client.top().startsWith("com.android.settings"),
        )
    }


    @Test
    fun the_phone_screen_does_not_change() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val before = instrumentation.uiAutomation.takeScreenshot()
        assumeTrue("cannot read the phone screen", before != null)

        assumeTrue("settings is not on this phone", client.launch("com.android.settings").first)
        Thread.sleep(2_500)
        val after = instrumentation.uiAutomation.takeScreenshot()
        assumeTrue("cannot read the phone screen after the launch", after != null)

        assertTrue(
            "the app opened on the phone's own screen instead of the background display",
            before!!.sameAs(after),
        )
    }

    @Test
    fun a_tap_changes_the_frame() {
        assumeTrue("settings is not on this phone", client.launch("com.android.settings").first)
        Thread.sleep(2_500)
        val before = client.shot()
        client.tap(360, 900)
        Thread.sleep(1_500)
        val after = client.shot()
        assertTrue("no frame before the tap", before != null)
        assertTrue("no frame after the tap", after != null)
        assertTrue(
            "the tap changed nothing on the background display",
            !before.contentEquals(after),
        )
    }
}
