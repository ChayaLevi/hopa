package com.hopa.engine.router

import com.hopa.engine.ports.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRoutesInstalledTest {
    private val installed = listOf(
        InstalledApp(packageName = "tv.danmaku.bili", label = "Bilibili"),
        InstalledApp(packageName = "com.android.settings", label = "Settings"),
    )

    @Test
    fun android_uses_the_catalog_package_when_the_phone_has_it() {
        val bilibili = CatalogRoutes.targets(installed, platform = "android")
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
    fun an_app_with_no_android_target_stays_empty() {
        val discord = CatalogRoutes.targets(platform = "android")
            .first { it.name == "discord" }
        assertEquals("", discord.bundle)
    }

    @Test
    fun an_unconfirmed_package_is_used_only_when_the_phone_has_it() {
        val without = CatalogRoutes.targets(platform = "android")
            .first { it.name == "bilibili" }
        assertEquals("", without.bundle)

        val catalog = CatalogRoutes.targets(installed, platform = "android")
            .first { it.name == "bilibili" }
        assertEquals("tv.danmaku.bili", catalog.bundle)
    }

    @Test
    fun an_unknown_platform_gets_no_launch_target() {
        val bilibili = CatalogRoutes.targets(installed)
            .first { it.name == "bilibili" }
        assertEquals("", bilibili.bundle)
    }

    @Test
    fun the_router_hint_names_the_android_package() {
        val route = AppRouter.route("open bilibili", CatalogRoutes.targets(installed, platform = "android"))
        assertTrue(RouterHint.text(route).contains("\"app\":\"tv.danmaku.bili\""))
    }

    @Test
    fun the_router_hint_refuses_a_target_it_does_not_have() {
        val route = AppRouter.route("open discord", CatalogRoutes.targets(platform = "android"))
        assertTrue(RouterHint.text(route).contains("no launch target is verified"))
    }
}
