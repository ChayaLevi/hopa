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
    fun a_catalog_name_finds_the_package_that_is_actually_installed() {
        val bilibili = CatalogRoutes.targets(installed)
            .first { it.name == "bilibili" }
        assertEquals("tv.danmaku.bili", bilibili.bundle)
    }

    @Test
    fun an_app_that_is_not_installed_keeps_an_empty_package() {
        val bilibili = CatalogRoutes.targets(emptyList())
            .first { it.name == "bilibili" }
        assertEquals("", bilibili.bundle)
    }

    @Test
    fun the_router_hint_names_the_installed_package() {
        val route = AppRouter.route("open bilibili", CatalogRoutes.targets(installed))
        assertTrue(RouterHint.text(route).contains("\"app\":\"tv.danmaku.bili\""))
    }

    @Test
    fun the_router_hint_refuses_a_package_it_does_not_know() {
        val route = AppRouter.route("open appgallery", CatalogRoutes.targets(emptyList()))
        assertTrue(RouterHint.text(route).contains("no Android package is verified"))
    }
}
