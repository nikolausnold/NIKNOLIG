package com.niknolig.app

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets

internal object BackendHealthClient {
    private const val EXPECTED_RESPONSE =
        """{"status":"ok","service":"niknolig-api","apiVersion":1}"""
    private const val MAX_RESPONSE_BYTES = 512

    fun check(baseUrl: String): Boolean {
        val baseUri = URI(baseUrl)
        require(
            baseUri.scheme == "https" &&
                !baseUri.host.isNullOrBlank() &&
                baseUri.userInfo == null &&
                baseUri.query == null &&
                baseUri.fragment == null
        ) {
            "The API base URL must use HTTPS."
        }

        val connection = URL("${baseUrl.trimEnd('/')}/v1/health")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECTION_TIMEOUT_MS
            connection.readTimeout = CONNECTION_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return false
            if (connection.contentType?.substringBefore(';') != "application/json") return false

            val body = connection.inputStream.use { input ->
                val output = ByteArray(MAX_RESPONSE_BYTES + 1)
                var total = 0
                while (total < output.size) {
                    val read = input.read(output, total, output.size - total)
                    if (read == -1) break
                    total += read
                }
                if (total > MAX_RESPONSE_BYTES) return false
                String(output, 0, total, StandardCharsets.UTF_8).trim()
            }
            return body == EXPECTED_RESPONSE
        } finally {
            connection.disconnect()
        }
    }

    private const val CONNECTION_TIMEOUT_MS = 5_000
}
