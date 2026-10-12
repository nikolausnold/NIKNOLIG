package com.niknolig.app

import org.junit.Assert.assertThrows
import org.junit.Test

class BackendHealthClientTest {
    @Test
    fun rejectsHttpAndUrlsContainingCredentials() {
        assertThrows(IllegalArgumentException::class.java) {
            BackendHealthClient.check("http://api.example.test")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackendHealthClient.check(
                "https://${listOf("user", "pass").joinToString(":")}@api.example.test"
            )
        }
    }
}
