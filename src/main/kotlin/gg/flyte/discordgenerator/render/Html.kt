package gg.flyte.discordgenerator.render

import java.net.URI

/** Escapes text for use in HTML element content and quoted attribute values. */
internal fun esc(text: String?): String {
    if (text.isNullOrEmpty()) return ""
    val sb = StringBuilder(text.length + 16)
    for (c in text) {
        when (c) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '"' -> sb.append("&quot;")
            '\'' -> sb.append("&#39;")
            else -> sb.append(c)
        }
    }
    return sb.toString()
}

/** Returns [url] if it is a plain http(s) link, otherwise `null`. Blocks `javascript:`, `data:` and friends. */
internal fun safeUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val trimmed = url.trim()
    val scheme = runCatching { URI(trimmed).scheme?.lowercase() }.getOrNull()
        ?: trimmed.substringBefore(':', "").lowercase().takeIf { it.isNotEmpty() && trimmed.contains("://") }
    return if (scheme == "http" || scheme == "https") trimmed else null
}

/** An escaped, safe `href` value, or `#` when the URL isn't safe. */
internal fun href(url: String?): String = esc(safeUrl(url) ?: "#")

internal fun hexColor(color: Int): String = "#%06x".format(color and 0xFFFFFF)

internal fun humanSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return ""
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "$bytes B" else "%.2f %s".format(value, units[unit])
}
