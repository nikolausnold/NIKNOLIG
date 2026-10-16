package com.niknolig.app

import java.net.URI
import java.net.URL

internal class BackendEndpoint(baseUrl: String) {
    private val origin = URI.create(baseUrl).also {
        require(
            it.scheme == "https" && !it.host.isNullOrBlank() &&
                it.rawAuthority == it.host && it.rawPath in listOf("", "/") &&
                it.rawQuery == null && it.rawFragment == null
        ) { "An HTTPS origin is required" }
    }

    fun healthUrl(): URL = origin.resolve("/v1/health").toURL()
}
