package gg.flyte.discordgenerator.model

import java.time.Instant

/**
 * A platform-independent snapshot of a channel, ready to be rendered.
 *
 * Adapters (for example `discordgenerator-jda`) build one of these from their own entity types;
 * the renderer never talks to Discord itself.
 */
data class Transcript(
    /** Messages in any order; the renderer sorts them by [Message.timestamp]. */
    val messages: List<Message>,
    val title: String = "Transcript",
    /** Shown under the title, e.g. "Generated 08/11/2026 at 15:42". `null` uses a default. */
    val subtitle: String? = null,
    val guild: GuildInfo? = null,
    val channel: ChannelInfo? = null,
    /** Names/colours for `<@id>`, `<@&id>` and `<#id>` mentions found in message text. */
    val mentions: MentionIndex = MentionIndex(),
    val generatedAt: Instant = Instant.now(),
)

data class GuildInfo(val id: String, val name: String, val iconUrl: String? = null)

data class ChannelInfo(val id: String, val name: String, val topic: String? = null)

data class MentionIndex(
    val users: Map<String, MentionedUser> = emptyMap(),
    val roles: Map<String, MentionedRole> = emptyMap(),
    val channels: Map<String, String> = emptyMap(),
)

data class MentionedUser(val name: String, val color: Int? = null)

data class MentionedRole(val name: String, val color: Int? = null)

data class Author(
    val id: String,
    /** The name to display (nickname, global name or username). */
    val name: String,
    /** The unique username, shown on hover. */
    val username: String? = null,
    val avatarUrl: String? = null,
    /** Highest coloured role, as `0xRRGGBB`. */
    val color: Int? = null,
    val isBot: Boolean = false,
    val isSystem: Boolean = false,
    val isWebhook: Boolean = false,
)

enum class MessageType(val isSystem: Boolean = false) {
    DEFAULT,
    REPLY,
    SLASH_COMMAND,
    CONTEXT_COMMAND,
    THREAD_STARTER_MESSAGE,
    RECIPIENT_ADD(true),
    RECIPIENT_REMOVE(true),
    CALL(true),
    CHANNEL_NAME_CHANGE(true),
    CHANNEL_ICON_CHANGE(true),
    CHANNEL_PINNED_ADD(true),
    GUILD_MEMBER_JOIN(true),
    GUILD_MEMBER_BOOST(true),
    GUILD_BOOST_TIER_1(true),
    GUILD_BOOST_TIER_2(true),
    GUILD_BOOST_TIER_3(true),
    CHANNEL_FOLLOW_ADD(true),
    THREAD_CREATED(true),
    AUTO_MODERATION_ACTION,
    POLL_RESULT,
    UNKNOWN,
}

data class Message(
    val id: String,
    val author: Author,
    val timestamp: Instant,
    val type: MessageType = MessageType.DEFAULT,
    /** Raw Discord markdown. Ignored for Components V2 messages, which carry text in [components]. */
    val content: String = "",
    val editedAt: Instant? = null,
    val embeds: List<Embed> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val stickers: List<Sticker> = emptyList(),
    val reactions: List<Reaction> = emptyList(),
    val components: List<MessageComponent> = emptyList(),
    val reply: ReplyPreview? = null,
    val interaction: InteractionInfo? = null,
    val poll: Poll? = null,
    val forwards: List<ForwardedMessage> = emptyList(),
    val isPinned: Boolean = false,
    val isComponentsV2: Boolean = false,
)

data class ReplyPreview(
    val messageId: String?,
    /** `null` when the referenced message was deleted. */
    val author: Author?,
    val content: String = "",
    val hasAttachments: Boolean = false,
    val hasEmbeds: Boolean = false,
    val isComponentsV2: Boolean = false,
)

data class InteractionInfo(val user: Author?, val commandName: String?)

data class ForwardedMessage(
    val content: String = "",
    val timestamp: Instant? = null,
    val editedAt: Instant? = null,
    val embeds: List<Embed> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val stickers: List<Sticker> = emptyList(),
    val components: List<MessageComponent> = emptyList(),
    val isComponentsV2: Boolean = false,
)

enum class AttachmentKind { IMAGE, VIDEO, AUDIO, FILE }

data class Attachment(
    val id: String,
    val filename: String,
    val url: String,
    val proxyUrl: String? = null,
    val size: Long = 0,
    val contentType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    /** Alt text. */
    val description: String? = null,
    val isSpoiler: Boolean = filename.startsWith("SPOILER_"),
    val isVoiceMessage: Boolean = false,
    val durationSeconds: Double? = null,
) {
    val kind: AttachmentKind get() = kindOf(contentType, filename)

    /** The filename as Discord displays it (without the `SPOILER_` prefix). */
    val displayName: String get() = filename.removePrefix("SPOILER_")
}

/** Media referenced by an embed or a Components V2 component. */
data class Media(
    val url: String,
    val proxyUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val contentType: String? = null,
)

data class Embed(
    /** Discord embed type: `rich`, `image`, `video`, `gifv`, `article`, `link`, `auto_moderation_message`, `poll_result`. */
    val type: String = "rich",
    val title: String? = null,
    val url: String? = null,
    val description: String? = null,
    val color: Int? = null,
    val timestamp: Instant? = null,
    val author: EmbedAuthor? = null,
    val footer: EmbedFooter? = null,
    val fields: List<EmbedField> = emptyList(),
    val image: Media? = null,
    val thumbnail: Media? = null,
    val video: Media? = null,
    val provider: EmbedProvider? = null,
)

data class EmbedAuthor(val name: String, val url: String? = null, val iconUrl: String? = null, val proxyIconUrl: String? = null)

data class EmbedFooter(val text: String, val iconUrl: String? = null, val proxyIconUrl: String? = null)

data class EmbedField(val name: String, val value: String, val inline: Boolean = false)

data class EmbedProvider(val name: String?, val url: String? = null)

data class Emoji(
    /** Unicode character(s) for standard emoji, the name for custom emoji. */
    val name: String,
    /** Set for custom emoji. */
    val id: String? = null,
    val animated: Boolean = false,
) {
    val isCustom: Boolean get() = id != null

    val imageUrl: String?
        get() = id?.let { "https://cdn.discordapp.com/emojis/$it.${if (animated) "gif" else "png"}?size=96" }
}

data class Reaction(val emoji: Emoji, val count: Int)

enum class StickerFormat { PNG, APNG, GIF, LOTTIE, UNKNOWN }

data class Sticker(val id: String, val name: String, val format: StickerFormat, val url: String)

data class Poll(
    val question: String,
    val answers: List<PollAnswer>,
    val expiresAt: Instant? = null,
    val isFinalized: Boolean = false,
    val allowMultiselect: Boolean = false,
) {
    val totalVotes: Int get() = answers.sumOf { it.votes }
}

data class PollAnswer(val text: String?, val emoji: Emoji? = null, val votes: Int = 0)

/** Legacy (action row) and Components V2 message components. */
sealed interface MessageComponent

data class ActionRow(val components: List<MessageComponent>) : MessageComponent

enum class ButtonStyle { PRIMARY, SECONDARY, SUCCESS, DANGER, LINK, PREMIUM }

data class Button(
    val style: ButtonStyle,
    val label: String? = null,
    val emoji: Emoji? = null,
    val url: String? = null,
    val disabled: Boolean = false,
) : MessageComponent

data class SelectMenu(
    val placeholder: String? = null,
    /** Labels of options selected by default, shown in place of the placeholder. */
    val defaultLabels: List<String> = emptyList(),
    val disabled: Boolean = false,
) : MessageComponent

data class TextDisplay(val content: String) : MessageComponent

data class Section(
    val components: List<MessageComponent>,
    /** A [Thumbnail] or [Button]. */
    val accessory: MessageComponent? = null,
) : MessageComponent

data class Thumbnail(val media: Media, val description: String? = null, val spoiler: Boolean = false) : MessageComponent

data class MediaGallery(val items: List<MediaGalleryItem>) : MessageComponent

data class MediaGalleryItem(val media: Media, val description: String? = null, val spoiler: Boolean = false)

data class FileDisplay(
    val media: Media,
    val filename: String? = null,
    val size: Long? = null,
    val spoiler: Boolean = false,
) : MessageComponent

enum class SeparatorSpacing { SMALL, LARGE }

data class Separator(val divider: Boolean = true, val spacing: SeparatorSpacing = SeparatorSpacing.SMALL) : MessageComponent

data class Container(
    val components: List<MessageComponent>,
    val accentColor: Int? = null,
    val spoiler: Boolean = false,
) : MessageComponent

/** A component type this library doesn't know how to draw yet. */
data class UnknownComponent(val type: String) : MessageComponent

private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "webp", "avif", "bmp", "apng")
private val VIDEO_EXT = setOf("mp4", "webm", "mov", "m4v", "mkv")
private val AUDIO_EXT = setOf("mp3", "ogg", "oga", "wav", "flac", "m4a", "opus", "aac")

internal fun kindOf(contentType: String?, filename: String?): AttachmentKind {
    val type = contentType?.substringBefore(';')?.trim()?.lowercase()
    when {
        type == null -> Unit
        type.startsWith("image/") -> return AttachmentKind.IMAGE
        type.startsWith("video/") -> return AttachmentKind.VIDEO
        type.startsWith("audio/") -> return AttachmentKind.AUDIO
    }
    val ext = filename?.substringBefore('?')?.substringAfterLast('.', "")?.lowercase() ?: ""
    return when (ext) {
        in IMAGE_EXT -> AttachmentKind.IMAGE
        in VIDEO_EXT -> AttachmentKind.VIDEO
        in AUDIO_EXT -> AttachmentKind.AUDIO
        else -> AttachmentKind.FILE
    }
}
