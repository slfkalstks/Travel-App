package com.travel.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import io.ktor.http.URLProtocol
import io.ktor.http.Url

/** Only public web links are allowed; custom app schemes and embedded credentials are rejected. */
fun isExternalWebUrl(value: String): Boolean = runCatching {
    require(Regex("^https?://[^/?#]+", RegexOption.IGNORE_CASE).containsMatchIn(value))
    val url = Url(value)
    (url.protocol == URLProtocol.HTTP || url.protocol == URLProtocol.HTTPS) &&
        url.host.isNotBlank() && url.user == null && url.password == null &&
        !value.any { it.isWhitespace() || it.code < 32 }
}.getOrDefault(false)

/** Uses Compose's platform URI handler: Android ACTION_VIEW, iOS system URL opening. */
@Composable
fun rememberExternalUrlLauncher(): (String) -> Boolean {
    val handler = LocalUriHandler.current
    return remember(handler) {
        { url ->
            isExternalWebUrl(url) && runCatching { handler.openUri(url) }.isSuccess
        }
    }
}
