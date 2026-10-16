package com.niknolig.app

import org.json.JSONObject
import javax.net.ssl.HttpsURLConnection

internal class BackendClient(private val endpoint: BackendEndpoint) {
    fun checkHealth(): Boolean {
        val connection = endpoint.healthUrl().openConnection() as HttpsURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "NIKNOLIG-SandboxCheck/1")
            if (connection.responseCode != 200 ||
                connection.contentType?.substringBefore(';') != "application/json"
            ) {
                false
            } else {
                connection.inputStream.use { input ->
                    val bytes = ByteArray(4_097)
                    var size = 0
                    val deadline = System.nanoTime() + 5_000_000_000L
                    while (size < bytes.size) {
                        if (System.nanoTime() >= deadline || Thread.currentThread().isInterrupted) {
                            return@use false
                        }
                        val count = input.read(bytes, size, bytes.size - size)
                        if (count == -1) break
                        size += count
                    }
                    if (size > 4_096) {
                        false
                    } else {
                        val response = JSONObject(String(bytes, 0, size, Charsets.UTF_8))
                        response.optString("status") == "ok" &&
                            response.optString("apiVersion") == "v1" &&
                            response.optString("mode") == "sandbox"
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
