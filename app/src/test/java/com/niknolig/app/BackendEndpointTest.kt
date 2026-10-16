package com.niknolig.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackendEndpointTest {
    @Test
    fun resolvesVersionedHealthRoute() {
        for (origin in listOf("https://sandbox.example", "https://sandbox.example/")) {
            assertEquals(
                "https://sandbox.example/v1/health",
                BackendEndpoint(origin).healthUrl().toString()
            )
        }
    }

    @Test
    fun rejectsNonHttpsAndNonOriginConfiguration() {
        for (origin in listOf(
            "", "http://sandbox.example", "https://" + "operator@" + "sandbox.example",
            "https://sandbox.example:8443", "https://sandbox.example/v1",
            "https://sandbox.example?token=x", "https://sandbox.example#fragment",
            "https://sandbox.example/\\evil", "https://sandbox.example/\""
        )) {
            assertThrows(IllegalArgumentException::class.java) { BackendEndpoint(origin) }
        }
    }
}
