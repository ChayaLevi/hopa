package com.hopa.engine.prompt

import com.hopa.contracts.ports.InstalledApp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledAppsBlockTest {
    @Test
    fun every_installed_app_is_named_with_its_package() {
        val text = InstalledAppsBlock().text(
            listOf(
                InstalledApp(packageName = "tv.danmaku.bili", label = "Bilibili"),
                InstalledApp(packageName = "com.android.settings", label = "Settings"),
            ),
        )
        assertTrue(text.contains("Bilibili - tv.danmaku.bili"))
        assertTrue(text.contains("Settings - com.android.settings"))
        assertTrue(text.contains("no catalog entry"))
    }

    @Test
    fun the_list_is_capped_and_sorted_by_label() {
        val many = (1..12).map { InstalledApp(packageName = "app.$it", label = "App %02d".format(it)) }
        val text = InstalledAppsBlock(limit = 5).text(many.reversed())
        assertTrue(text.contains("App 01"))
        assertFalse(text.contains("App 06 - app.6"))
        assertTrue(text.indexOf("App 01") < text.indexOf("App 02"))
    }

    @Test
    fun apps_the_catalog_already_describes_are_not_repeated() {
        val text = InstalledAppsBlock().text(
            listOf(
                InstalledApp(packageName = "tv.danmaku.bili", label = "Bilibili"),
                InstalledApp(packageName = "com.example.notes", label = "Notes"),
            ),
            known = setOf("tv.danmaku.bili"),
        )
        assertFalse(text.contains("tv.danmaku.bili"))
        assertTrue(text.contains("Notes - com.example.notes"))
    }

    @Test
    fun nothing_left_to_list_means_no_block_at_all() {
        val text = InstalledAppsBlock().text(
            listOf(InstalledApp(packageName = "tv.danmaku.bili", label = "Bilibili")),
            known = setOf("tv.danmaku.bili"),
        )
        assertTrue(text.isEmpty())
    }

    @Test
    fun an_empty_phone_says_so_instead_of_guessing() {
        val text = InstalledAppsBlock().text(emptyList())
        assertTrue(text.contains("none could be listed"))
        assertTrue(text.contains("Do not guess a package name"))
    }
}
