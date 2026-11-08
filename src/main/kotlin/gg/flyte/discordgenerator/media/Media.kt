package gg.flyte.discordgenerator.media

import java.net.URI
import java.time.Instant

/** What a piece of media is used for. Lets a [MediaResolver] treat e.g. avatars differently from attachments. */
enum class MediaKind { AVATAR, GUILD_ICON, EMOJI, STICKER, ATTACHMENT, EMBED, COMPONENT }

data class MediaRequest(
    val kind: MediaKind,
    val url: String,
    val proxyUrl: String? = null,
    val filename: String? = null,
    val contentType: String? = null,
    val size: Long? = null,
)

/**
 * Decides which URL ends up in the HTML for every image, video and file.
 *
 * The default [HOTLINK] resolver links straight to Discord. To keep attachments alive forever, supply a resolver
 * that downloads [MediaRequest.url] to your own storage and returns the new URL. Return `null` to render the
 * "unavailable" placeholder instead.
 */
fun interface MediaResolver {
    fun resolve(request: MediaRequest): String?

    companion object {
        /** Links directly to Discord: the original CDN URL for attachments, Discord's media proxy for embeds. */
        @JvmField
        val HOTLINK: MediaResolver = MediaResolver { request ->
            when (request.kind) {
                // External embed media is hotlink-protected more often than not, so use Discord's proxy copy.
                MediaKind.EMBED -> request.proxyUrl ?: request.url
                else -> request.url
            }
        }
    }
}

/** Helpers for Discord's signed CDN links. */
object DiscordCdn {
    private val HOSTS = setOf("cdn.discordapp.com", "media.discordapp.net", "cdn.discord.com", "media.discord.net")

    /** Whether [url] points at Discord's own CDN or media proxy. */
    @JvmStatic
    fun isDiscordHost(url: String): Boolean = host(url) in HOSTS

    /**
     * When a signed attachment link stops working, read from its `ex` (hex unix seconds) query parameter.
     * `null` for URLs that don't expire (avatars, emoji, stickers, non-Discord hosts).
     */
    @JvmStatic
    fun expiresAt(url: String): Instant? {
        if (!isDiscordHost(url)) return null
        val query = runCatching { URI(url).rawQuery }.getOrNull() ?: return null
        val ex = query.split('&').firstOrNull { it.startsWith("ex=") }?.substring(3) ?: return null
        return ex.toLongOrNull(16)?.let(Instant::ofEpochSecond)
    }

    /** Discord's default avatar for a user ID (new username system). */
    @JvmStatic
    fun defaultAvatarUrl(userId: String?): String {
        val index = userId?.toLongOrNull()?.let { (it ushr 22) % 6 } ?: 0
        return "https://cdn.discordapp.com/embed/avatars/$index.png"
    }

    private fun host(url: String): String? = runCatching { URI(url).host?.lowercase() }.getOrNull()
}
