package com.hopa.feature.launchpad

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlayStorePageTest {
    @Test
    fun a_package_gets_a_details_page() {
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.tencent.mm",
            PlayStorePage.url("com.tencent.mm"),
        )
    }

    @Test
    fun surrounding_space_is_ignored() {
        assertEquals(
            "https://play.google.com/store/apps/details?id=tv.danmaku.bili",
            PlayStorePage.url("  tv.danmaku.bili  "),
        )
    }

    @Test
    fun a_single_word_is_not_a_package() {
        assertNull(PlayStorePage.url("settings"))
        assertNull(PlayStorePage.url("com..example"))
        assertNull(PlayStorePage.url(""))
    }
}
