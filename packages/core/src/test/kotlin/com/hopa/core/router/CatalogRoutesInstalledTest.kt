package com.hopa.core.router

import com.hopa.core.ports.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRoutesInstalledTest {
    private val installed = listOf(
        InstalledApp(packageName = "tv.danmaku.bili", label = "Bilibili"),
        InstalledApp(packageName = "com.android.settings", label = "Settings"),
    )

    @Test
    fun android_reads_the_android_package_from_the_catalog() {
        val bilibili = CatalogRoutes.targets(platform = "android")
            .first { it.name == "bilibili" }
        assertEquals("tv.danmaku.bili", bilibili.bundle)
    }

    @Test
    fun the_harmony_bundle_is_not_offered_to_android_or_windows() {
        val harmony = CatalogRoutes.targets(platform = "harmonyos")
            .first { it.name == "bilibili" }
        assertTrue(harmony.bundle.isNotEmpty())
        assertTrue(harmony.bundle != "tv.danmaku.bili")

        val windows = CatalogRoutes.targets(platform = "windows")
            .first { it.name == "bilibili" }
        assertEquals("", windows.bundle)
    }

    @Test
    fun an_app_that_is_not_installed_and_has_no_android_package_stays_empty() {
        val gallery = CatalogRoutes.targets(platform = "android")
            .first { it.name == "gallery" }
        assertEquals("", gallery.bundle)
    }

    @Test
    fun an_unknown_platform_gets_no_launch_target() {
        val bilibili = CatalogRoutes.targets(installed)
            .first { it.name == "bilibili" }
        assertEquals("", bilibili.bundle)
    }

    @Test
    fun the_router_hint_names_the_android_package() {
        val route = AppRouter.route("open bilibili", CatalogRoutes.targets(platform = "android"))
        assertTrue(RouterHint.text(route).contains("\"app\":\"tv.danmaku.bili\""))
    }

    @Test
    fun the_router_hint_refuses_a_target_it_does_not_have() {
        val route = AppRouter.route("open gallery", CatalogRoutes.targets(platform = "android"))
        assertTrue(RouterHint.text(route).contains("no launch target is verified"))
    }
}
