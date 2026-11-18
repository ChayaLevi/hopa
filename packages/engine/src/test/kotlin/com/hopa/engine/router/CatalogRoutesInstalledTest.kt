package com.hopa.engine.router

import com.hopa.contracts.ports.InstalledApp
import com.hopa.engine.testing.TestKnowledge
import com.hopa.knowledge.ApprovalPolicyData
import com.hopa.knowledge.CatalogApp
import com.hopa.knowledge.CatalogTarget
import com.hopa.knowledge.Knowledge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRoutesInstalledTest {
    private val installed = listOf(
        InstalledApp(packageName = "tv.danmaku.bili", label = "Bilibili"),
        InstalledApp(packageName = "com.android.settings", label = "Settings"),
    )

    private fun targets(
        installed: List<InstalledApp> = emptyList(),
        platform: String = "",
        knowledge: Knowledge = TestKnowledge.value,
    ) = CatalogRoutes.targets(installed, platform, knowledge)

    @Test
    fun a_supplied_knowledge_replaces_the_bundled_catalog() {
        val empty = CatalogTarget(id = "", detail = "", verified = false)
        val knowledge = Knowledge(
            version = "test",
            catalog = listOf(
                CatalogApp(
                    id = "tiny",
                    display = "Tiny",
                    aliases = emptyList(),
                    purpose = "",
                    actions = emptyList(),
                    harmonyos = empty,
                    android = CatalogTarget("com.tiny", "", true),
                    windows = empty,
                ),
            ),
            sites = emptyList(),
            approval = ApprovalPolicyData,
            promptRules = emptyList(),
        )

        val routes = targets(platform = "android", knowledge = knowledge)

        assertEquals(listOf("tiny"), routes.map { it.name })
        assertEquals("com.tiny", routes.single().bundle)
    }

    @Test
    fun android_uses_the_catalog_package_when_the_phone_has_it() {
        val bilibili = targets(installed, platform = "android")
            .first { it.name == "bilibili" }
        assertEquals("tv.danmaku.bili", bilibili.bundle)
    }

    @Test
    fun the_harmony_bundle_is_not_offered_to_android_or_windows() {
        val harmony = targets(platform = "harmonyos")
            .first { it.name == "bilibili" }
        assertTrue(harmony.bundle.isNotEmpty())
        assertTrue(harmony.bundle != "tv.danmaku.bili")

        val windows = targets(platform = "windows")
            .first { it.name == "bilibili" }
        assertEquals("", windows.bundle)
    }

    @Test
    fun an_app_with_no_android_target_stays_empty() {
        val discord = targets(platform = "android")
            .first { it.name == "discord" }
        assertEquals("", discord.bundle)
    }

    @Test
    fun an_unconfirmed_package_is_used_only_when_the_phone_has_it() {
        val without = targets(platform = "android")
            .first { it.name == "bilibili" }
        assertEquals("", without.bundle)

        val catalog = targets(installed, platform = "android")
            .first { it.name == "bilibili" }
        assertEquals("tv.danmaku.bili", catalog.bundle)
    }

    @Test
    fun an_unknown_platform_gets_no_launch_target() {
        val bilibili = targets(installed)
            .first { it.name == "bilibili" }
        assertEquals("", bilibili.bundle)
    }

    @Test
    fun the_router_hint_names_the_android_package() {
        val route = AppRouter.route("open bilibili", targets(installed, platform = "android"))
        assertTrue(RouterHint.text(route).contains("\"app\":\"tv.danmaku.bili\""))
    }

    @Test
    fun the_router_hint_refuses_a_target_it_does_not_have() {
        val route = AppRouter.route("open discord", targets(platform = "android"))
        assertTrue(RouterHint.text(route).contains("no launch target is verified"))
    }
}
