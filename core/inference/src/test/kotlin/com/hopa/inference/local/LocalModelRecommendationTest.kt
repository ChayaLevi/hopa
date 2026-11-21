package com.hopa.inference.local

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalModelRecommendationTest {
    private val recommendation = LocalModelRecommendation()
    private val gigabyte = 1_000_000_000L

    @Test
    fun a_twelve_gigabyte_phone_is_offered_the_large_model() {
        val model = recommendation.recommend(ramGb = 12, freeBytes = 40 * gigabyte)
        assertEquals("qwen3-8b", model?.id)
    }

    @Test
    fun an_eight_gigabyte_phone_is_offered_the_balanced_model() {
        val model = recommendation.recommend(ramGb = 8, freeBytes = 20 * gigabyte)
        assertEquals("qwen3-4b", model?.id)
    }

    @Test
    fun a_six_gigabyte_phone_is_offered_the_fast_model() {
        val model = recommendation.recommend(ramGb = 6, freeBytes = 20 * gigabyte)
        assertEquals("qwen3-1.7b-q8", model?.id)
    }

    @Test
    fun a_four_gigabyte_phone_falls_back_to_the_router() {
        val model = recommendation.recommend(ramGb = 4, freeBytes = 20 * gigabyte)
        assertEquals("gemma3-270m", model?.id)
    }

    @Test
    fun a_full_disk_drops_to_whatever_still_fits() {
        val model = recommendation.recommend(ramGb = 12, freeBytes = 300_000_000)
        assertEquals("gemma3-270m", model?.id)
    }

    @Test
    fun no_space_means_no_recommendation() {
        val model = recommendation.recommend(ramGb = 8, freeBytes = 10_000_000)
        assertEquals(null, model)
    }

    @Test
    fun a_roomy_eight_gigabyte_phone_still_gets_a_model_that_fits_its_ram() {
        val model = recommendation.recommend(ramGb = 8, freeBytes = 40 * gigabyte)
        assertTrue((model?.minRamGb ?: 99) <= 8)
    }
}
