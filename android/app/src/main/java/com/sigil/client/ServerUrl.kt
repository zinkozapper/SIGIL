package com.sigil.client

import java.net.URI

fun normalizeServerUrl(input: String, portInput: String): String? {
    val value = input.trim()
    val portText = portInput.trim()
    val port = portText.toIntOrNull()
    if (value.isEmpty() || portText.any { it !in '0'..'9' } || port == null || port !in 1..65535) return null
    val url = if (value.contains("://")) value else "http://$value"
    return try {
        val uri = URI(url)
        if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrEmpty() ||
            uri.userInfo != null || uri.port != -1 || uri.rawAuthority.endsWith(":") || uri.rawQuery != null ||
            uri.rawFragment != null || (uri.rawPath != "" && uri.rawPath != "/")) null
        else "${url.trimEnd('/')}:$port"
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: java.net.URISyntaxException) {
        null
    }
}
