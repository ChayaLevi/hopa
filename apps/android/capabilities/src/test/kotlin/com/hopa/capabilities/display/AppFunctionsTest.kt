package com.hopa.capabilities.display

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppFunctionsTest {
    @Test
    fun `schema names are collected once`() {
        val json = """
            {
              "com.example.app": [
                {
                  "AppFunctionStaticMetadata-1": {
                    "schemaName": ["openSettings", "readList"],
                    "serviceName": ["com.example.Service"]
                  }
                },
                {
                  "AppFunctionStaticMetadata-2": {
                    "schemaName": ["openSettings"]
                  }
                }
              ]
            }
        """.trimIndent()
        assertEquals(listOf("openSettings", "readList"), AppFunctions.names(json))
    }

    @Test
    fun `a helper error has no names`() {
        assertTrue(AppFunctions.names("err app functions are not supported").isEmpty())
    }
}
