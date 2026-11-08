package gg.flyte.discordgenerator.render

import gg.flyte.discordgenerator.DiscordGenerator
import gg.flyte.discordgenerator.media.DiscordCdn
import gg.flyte.discordgenerator.media.MediaKind
import gg.flyte.discordgenerator.media.MediaRequest
import gg.flyte.discordgenerator.model.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Renders one [Transcript]. Not thread-safe; create one per render. */
internal class HtmlRenderer(private val transcript: Transcript, private val options: RenderOptions) {

    private val now = Instant.now()
    private val zone = options.zoneId
    private val locale = options.locale
    private val fmtDate = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale).withZone(zone)
    private val fmtTime = DateTimeFormatter.ofPattern("HH:mm", locale).withZone(zone)
    private val fmtTimeSec = DateTimeFormatter.ofPattern("HH:mm:ss", locale).withZone(zone)
    private val fmtDateLong = DateTimeFormatter.ofPattern("d MMMM yyyy", locale).withZone(zone)
    private val fmtDateTime = DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm", locale).withZone(zone)
    private val fmtDateTimeFull = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy HH:mm", locale).withZone(zone)

    /** Earliest moment a signed Discord link in this transcript stops working. */
    private var earliestExpiry: Instant? = null
    private var anyExpired = false

    private val markdown = DiscordMarkdown(transcript.mentions, ::emojiHtml, ::timestampHtml)

    fun render(): String {
        val messages = transcript.messages.sortedWith(compareBy<Message> { it.timestamp }.thenBy { it.id.toLongOrNull() ?: 0 })
        val body = renderMessages(messages)

        val sb = StringBuilder(body.length + 16_384)
        sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        sb.append("<meta name=\"robots\" content=\"noindex, nofollow\">")
        sb.append("<meta name=\"referrer\" content=\"no-referrer\">")
        sb.append("<title>").append(esc(transcript.title)).append("</title>")
        if (options.stylesheetUrl != null) {
            sb.append("<link rel=\"stylesheet\" href=\"").append(esc(options.stylesheetUrl)).append("\">")
        } else {
            sb.append("<style>").append(DiscordGenerator.stylesheet()).append("</style>")
        }
        if (options.scriptUrl != null) {
            sb.append("<script src=\"").append(esc(options.scriptUrl)).append("\"></script>")
        } else {
            sb.append("<script>").append(DiscordGenerator.script()).append("</script>")
        }
        sb.append("</head><body>")
        // First in <body> so it already exists when an early image error asks for a placeholder.
        sb.append("<template id=\"tpl-gone\">").append(goneHtml("", "", "", "")).append("</template>")
        sb.append(header(messages.size))
        sb.append(expiryNotice())
        sb.append("<main class=\"chat").append(if (options.newestFirst) " newest-first" else "").append("\">")
        sb.append(body)
        sb.append("</main>")
        if (options.showFooter) {
            sb.append("<footer class=\"foot\">")
                .append(messages.size).append(if (messages.size == 1) " message" else " messages")
                .append(" &middot; exported ").append(timeTag(transcript.generatedAt, 'm'))
                .append("</footer>")
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    // ------------------------------------------------------------------ page chrome

    private fun header(count: Int): String {
        val sb = StringBuilder("<header class=\"top\">")
        transcript.guild?.iconUrl?.let { resolve(MediaRequest(MediaKind.GUILD_ICON, it)) }?.let {
            sb.append("<img class=\"guild-icon\" src=\"").append(esc(it)).append("\" alt=\"\" onerror=\"this.remove()\">")
        }
        sb.append("<div class=\"top-text\">")
        val crumbs = listOfNotNull(transcript.guild?.name, transcript.channel?.name?.let { "#$it" })
        if (crumbs.isNotEmpty()) sb.append("<div class=\"crumbs\">").append(esc(crumbs.joinToString(" / "))).append("</div>")
        sb.append("<h1>").append(esc(transcript.title)).append("</h1>")
        sb.append("<div class=\"sub\">")
        if (transcript.subtitle != null) {
            sb.append(esc(transcript.subtitle))
        } else {
            sb.append("Generated ").append(timeTag(transcript.generatedAt, 'm'))
                .append(" &middot; ").append(count).append(if (count == 1) " message" else " messages")
        }
        sb.append("</div>")
        transcript.channel?.topic?.takeIf { it.isNotBlank() }?.let {
            sb.append("<div class=\"topic\">").append(markdown.render(it, inline = true)).append("</div>")
        }
        sb.append("</div></header>")
        return sb.toString()
    }

    private fun expiryNotice(): String {
        val ex = earliestExpiry
        if (ex == null && !anyExpired) return ""
        val text = "Some attachments in this transcript have expired. Discord only keeps attachment links working " +
            "for about 24 hours, so images and files sent in this ticket can no longer be loaded."
        val exAttr = ex?.let { " data-ex=\"${it.epochSecond}\"" } ?: ""
        val hidden = if (anyExpired) "" else " hidden"
        return "<div class=\"notice\" id=\"expiry-notice\"$exAttr$hidden>${icon(ICON_CLOCK)}<span>${esc(text)}</span></div>"
    }

    // ------------------------------------------------------------------ messages

    private fun renderMessages(messages: List<Message>): String {
        val sb = StringBuilder()
        var previous: Message? = null
        var previousDay: LocalDate? = null
        for (message in messages) {
            val day = message.timestamp.atZone(zone).toLocalDate()
            if (day != previousDay) {
                sb.append("<div class=\"divider\"><span>").append(esc(fmtDateLong.format(message.timestamp))).append("</span></div>")
                previous = null
            }
            sb.append(renderMessage(message, continued = isContinuation(previous, message)))
            previous = message
            previousDay = day
        }
        return sb.toString()
    }

    private fun isContinuation(previous: Message?, message: Message): Boolean {
        if (!options.groupMessages || previous == null) return false
        if (previous.author.id != message.author.id || previous.author.name != message.author.name) return false
        if (message.type.isSystem || previous.type.isSystem) return false
        if (message.reply != null || message.interaction != null) return false
        return Duration.between(previous.timestamp, message.timestamp) < Duration.ofMinutes(7)
    }

    private fun renderMessage(m: Message, continued: Boolean): String {
        if (m.type.isSystem) return systemMessage(m)

        val sb = StringBuilder()
        sb.append("<div class=\"msg").append(if (continued) " cont" else "").append("\" id=\"m-").append(esc(m.id)).append("\">")
        m.reply?.let { sb.append(replyBar(it)) }
        if (m.reply == null) m.interaction?.let { sb.append(interactionBar(it)) }
        sb.append("<div class=\"msg-row\"><div class=\"gutter\">")
        if (continued) {
            sb.append(timeTag(m.timestamp, 't', "hover-time"))
        } else {
            sb.append(avatar(m.author, "avatar"))
        }
        sb.append("</div><div class=\"msg-body\">")
        if (!continued) {
            sb.append("<div class=\"msg-head\">").append(authorName(m.author))
            sb.append(timeTag(m.timestamp, 'm', "stamp")).append("</div>")
        }
        sb.append(messageContent(m.content, m.embeds, m.attachments, m.stickers, m.components, m.isComponentsV2, m.editedAt))
        m.forwards.forEach { sb.append(forward(it)) }
        m.poll?.let { sb.append(poll(it)) }
        if (m.reactions.isNotEmpty()) sb.append(reactions(m.reactions))
        sb.append("</div></div></div>")
        return sb.toString()
    }

    /** Content, attachments, stickers, embeds and components: shared by messages and forwarded snapshots. */
    private fun messageContent(
        content: String,
        embeds: List<Embed>,
        attachments: List<Attachment>,
        stickers: List<Sticker>,
        components: List<MessageComponent>,
        isComponentsV2: Boolean,
        editedAt: Instant?,
    ): String {
        val sb = StringBuilder()
        if (isComponentsV2) {
            if (components.isNotEmpty()) {
                sb.append("<div class=\"v2\">")
                components.forEach { sb.append(component(it, attachments)) }
                sb.append("</div>")
            }
            if (editedAt != null) sb.append(editedTag(editedAt))
            return sb.toString()
        }

        // Discord hides a message that is nothing but a link to the image/GIF it embeds.
        val hideContent = content.isNotBlank() && embeds.any { e ->
            (e.type == "image" || e.type == "gifv") && e.url != null && content.trim() == e.url
        }
        if (content.isNotBlank() && !hideContent) {
            val jumbo = isEmojiOnly(content)
            sb.append("<div class=\"content md").append(if (jumbo) " jumbo" else "").append("\">")
            sb.append(markdown.render(content, jumbo = jumbo))
            if (editedAt != null) sb.append(editedTag(editedAt))
            sb.append("</div>")
        } else if (editedAt != null && content.isNotBlank()) {
            sb.append(editedTag(editedAt))
        }

        if (attachments.isNotEmpty()) sb.append(attachmentsHtml(attachments))
        if (stickers.isNotEmpty()) sb.append(stickersHtml(stickers))
        embeds.forEach { sb.append(embed(it)) }
        if (components.isNotEmpty()) {
            sb.append("<div class=\"legacy-components\">")
            components.forEach { sb.append(component(it, attachments)) }
            sb.append("</div>")
        }
        return sb.toString()
    }

    private fun systemMessage(m: Message): String {
        val who = "<span class=\"sys-name\"${m.author.color?.let { " style=\"color:${hexColor(it)}\"" } ?: ""}>${esc(m.author.name)}</span>"
        val guild = esc(transcript.guild?.name ?: "the server")
        val detail = "<strong>${markdown.render(m.content, inline = true)}</strong>"
        val (iconSvg, text) = when (m.type) {
            MessageType.GUILD_MEMBER_JOIN -> ICON_JOIN to "$who joined the server."
            MessageType.CHANNEL_PINNED_ADD -> ICON_PIN to "$who pinned a message to this channel."
            MessageType.THREAD_CREATED -> ICON_THREAD to "$who started a thread: $detail"
            MessageType.CHANNEL_NAME_CHANGE -> ICON_PENCIL to "$who changed the channel name: $detail"
            MessageType.CHANNEL_ICON_CHANGE -> ICON_PENCIL to "$who changed the channel icon."
            MessageType.RECIPIENT_ADD -> ICON_JOIN to "$who added someone to the group."
            MessageType.RECIPIENT_REMOVE -> ICON_LEAVE to "$who removed someone from the group."
            MessageType.CALL -> ICON_CALL to "$who started a call."
            MessageType.CHANNEL_FOLLOW_ADD -> ICON_JOIN to "$who has added $detail to this channel."
            MessageType.GUILD_MEMBER_BOOST -> ICON_BOOST to "$who just boosted the server!"
            MessageType.GUILD_BOOST_TIER_1 -> ICON_BOOST to "$who just boosted the server! $guild has achieved <strong>Level 1!</strong>"
            MessageType.GUILD_BOOST_TIER_2 -> ICON_BOOST to "$who just boosted the server! $guild has achieved <strong>Level 2!</strong>"
            MessageType.GUILD_BOOST_TIER_3 -> ICON_BOOST to "$who just boosted the server! $guild has achieved <strong>Level 3!</strong>"
            else -> ICON_INFO to "$who ${markdown.render(m.content, inline = true)}"
        }
        return "<div class=\"msg system\" id=\"m-${esc(m.id)}\"><div class=\"msg-row\"><div class=\"gutter\">${icon(iconSvg)}</div>" +
            "<div class=\"msg-body\"><span class=\"sys-text\">$text</span> ${timeTag(m.timestamp, 'm', "stamp")}</div></div></div>"
    }

    private fun replyBar(reply: ReplyPreview): String {
        val sb = StringBuilder("<div class=\"reply\"><span class=\"reply-spine\"></span>")
        val author = reply.author
        if (author == null) {
            sb.append("<span class=\"reply-deleted\">Original message was deleted</span>")
        } else {
            sb.append(avatar(author, "reply-avatar"))
            sb.append(authorName(author, small = true))
            val target = reply.messageId?.let { "#m-${esc(it)}" }
            sb.append(if (target != null) "<a class=\"reply-text\" href=\"$target\">" else "<span class=\"reply-text\">")
            when {
                reply.content.isNotBlank() -> sb.append(markdown.render(reply.content.lineSequence().first(), inline = true))
                reply.hasAttachments -> sb.append("<em>Click to see attachment</em>")
                reply.hasEmbeds || reply.isComponentsV2 -> sb.append("<em>Click to see message</em>")
                else -> sb.append("<em>Click to see message</em>")
            }
            sb.append(if (target != null) "</a>" else "</span>")
        }
        return sb.append("</div>").toString()
    }

    private fun interactionBar(info: InteractionInfo): String {
        val sb = StringBuilder("<div class=\"reply\"><span class=\"reply-spine\"></span>")
        info.user?.let { sb.append(avatar(it, "reply-avatar")).append(authorName(it, small = true)) }
        sb.append("<span class=\"reply-text\">")
        if (info.commandName != null) {
            sb.append("used <span class=\"cmd\">/").append(esc(info.commandName)).append("</span>")
        } else {
            sb.append("used a command")
        }
        return sb.append("</span></div>").toString()
    }

    private fun forward(f: ForwardedMessage): String {
        val sb = StringBuilder("<div class=\"forward\"><div class=\"fwd-label\">").append(icon(ICON_FORWARD)).append("<em>Forwarded</em></div>")
        sb.append(messageContent(f.content, f.embeds, f.attachments, f.stickers, f.components, f.isComponentsV2, f.editedAt))
        f.timestamp?.let { sb.append("<div class=\"fwd-meta\">").append(timeTag(it, 'm')).append("</div>") }
        return sb.append("</div>").toString()
    }

    private fun authorName(author: Author, small: Boolean = false): String {
        val style = author.color?.let { " style=\"color:${hexColor(it)}\"" } ?: ""
        val title = author.username?.let { " title=\"${esc(it)}\"" } ?: ""
        val sb = StringBuilder("<span class=\"name").append(if (small) " small" else "").append("\"").append(style).append(title).append(">")
            .append(esc(author.name)).append("</span>")
        when {
            author.isSystem -> sb.append("<span class=\"tag\">SYSTEM</span>")
            author.isBot || author.isWebhook -> sb.append("<span class=\"tag\">APP</span>")
        }
        return sb.toString()
    }

    private fun avatar(author: Author, cls: String): String {
        val fallback = DiscordCdn.defaultAvatarUrl(author.id)
        val src = author.avatarUrl?.let { resolve(MediaRequest(MediaKind.AVATAR, it)) } ?: fallback
        return "<img class=\"$cls\" src=\"${esc(src)}\" data-def=\"${esc(fallback)}\" alt=\"\" loading=\"lazy\" onerror=\"__af(this)\">"
    }

    private fun editedTag(editedAt: Instant): String =
        "<span class=\"edited\" title=\"${esc(fmtDateTimeFull.format(editedAt))}\">(edited)</span>"

    // ------------------------------------------------------------------ attachments & media

    private fun attachmentsHtml(attachments: List<Attachment>): String {
        val visual = attachments.filter { it.kind == AttachmentKind.IMAGE || it.kind == AttachmentKind.VIDEO }
        val other = attachments - visual.toSet()
        val sb = StringBuilder("<div class=\"attachments\">")
        if (visual.size == 1) {
            sb.append(media(visual[0].toSpec(), maxW = 550, maxH = 350))
        } else if (visual.size > 1) {
            sb.append(gallery(visual.map { it.toSpec() }))
        }
        other.forEach { sb.append(media(it.toSpec(), 0, 0)) }
        return sb.append("</div>").toString()
    }

    private fun gallery(items: List<MediaSpec>): String {
        if (items.size == 1) return media(items[0], maxW = 550, maxH = 350)
        val layout = when (items.size) {
            2 -> "n2"
            3 -> "n3"
            4 -> "n4"
            else -> "nx"
        }
        val sb = StringBuilder("<div class=\"gallery $layout\">")
        items.forEach { sb.append(media(it, 0, 0, inGrid = true)) }
        return sb.append("</div>").toString()
    }

    private enum class Display(val label: String) { IMAGE("Image"), GIF("GIF"), VIDEO("Video"), GIFV("GIF"), AUDIO("Audio"), FILE("Attachment") }

    private class MediaSpec(
        val kind: MediaKind,
        val url: String,
        val proxyUrl: String? = null,
        val filename: String? = null,
        val contentType: String? = null,
        val size: Long? = null,
        val width: Int? = null,
        val height: Int? = null,
        val alt: String? = null,
        val spoiler: Boolean = false,
        val display: Display,
    )

    private fun displayOf(contentType: String?, name: String?): Display {
        val gif = contentType?.lowercase()?.startsWith("image/gif") == true ||
            name?.substringBefore('?')?.lowercase()?.endsWith(".gif") == true
        return when (kindOf(contentType, name)) {
            AttachmentKind.IMAGE -> if (gif) Display.GIF else Display.IMAGE
            AttachmentKind.VIDEO -> Display.VIDEO
            AttachmentKind.AUDIO -> Display.AUDIO
            AttachmentKind.FILE -> Display.FILE
        }
    }

    private fun Attachment.toSpec() = MediaSpec(
        kind = MediaKind.ATTACHMENT, url = url, proxyUrl = proxyUrl, filename = displayName, contentType = contentType,
        size = size, width = width, height = height, alt = description, spoiler = isSpoiler,
        display = displayOf(contentType, filename),
    )

    private fun Media.toSpec(kind: MediaKind, spoiler: Boolean = false, alt: String? = null, display: Display? = null) = MediaSpec(
        kind = kind, url = url, proxyUrl = proxyUrl, filename = fileNameFromUrl(url), contentType = contentType,
        width = width, height = height, alt = alt, spoiler = spoiler,
        display = display ?: displayOf(contentType, url).let { if (it == Display.FILE) Display.IMAGE else it },
    )

    /**
     * Renders an image/video/file. Signed Discord links get a `data-ex` attribute so the page script can swap in a
     * placeholder once they've expired; anything that fails to load at all gets the same treatment via `onerror`.
     */
    private fun media(spec: MediaSpec, maxW: Int, maxH: Int, inGrid: Boolean = false, extraClass: String = ""): String {
        val sizeLabel = humanSize(spec.size)
        val name = spec.filename ?: ""
        val isCard = spec.display == Display.FILE || spec.display == Display.AUDIO
        val box = if (inGrid || isCard) null else fit(spec.width, spec.height, maxW, maxH)
        val style = when {
            box != null -> "width:${box.first}px;aspect-ratio:${box.first}/${box.second}"
            !inGrid && !isCard && maxW > 0 -> "max-width:${maxW}px"
            else -> ""
        }

        val resolved = resolve(MediaRequest(spec.kind, spec.url, spec.proxyUrl, spec.filename, spec.contentType, spec.size))
        val expiry = resolved?.let(DiscordCdn::expiresAt)
        if (expiry != null && (earliestExpiry == null || expiry < earliestExpiry)) earliestExpiry = expiry
        val expired = expiry != null && !expiry.isAfter(now)
        if (expired) anyExpired = true

        if (resolved == null || (expired && !isCard)) {
            return goneHtml(spec.display.label + if (expired) " expired" else " unavailable", detail(name, sizeLabel), note(expired), style, extraClass)
        }

        val attrs = buildString {
            append(" data-media data-kind=\"").append(spec.display.name.lowercase()).append("\"")
            append(" data-label=\"").append(esc(spec.display.label)).append("\"")
            if (name.isNotEmpty()) append(" data-name=\"").append(esc(name)).append("\"")
            if (sizeLabel.isNotEmpty()) append(" data-size=\"").append(esc(sizeLabel)).append("\"")
            if (expiry != null) append(" data-ex=\"").append(expiry.epochSecond).append("\"")
            if (style.isNotEmpty()) append(" style=\"").append(style).append("\"")
        }
        val src = esc(resolved)
        val alt = esc(spec.alt ?: "")
        // A second chance for embed media: if Discord's proxy copy fails, try the original external URL.
        val fallback = if (spec.kind == MediaKind.EMBED && resolved != spec.url) safeUrl(spec.url)?.let { " data-fallback=\"${esc(it)}\"" } ?: "" else ""
        val spoiler = if (spec.spoiler) " spoiler-media" else ""
        val cls = (if (extraClass.isEmpty()) "" else " $extraClass") + spoiler

        return when (spec.display) {
            Display.IMAGE, Display.GIF ->
                "<div class=\"media$cls\"$attrs><a href=\"$src\" target=\"_blank\" rel=\"noopener noreferrer\">" +
                    "<img src=\"$src\" alt=\"$alt\" loading=\"lazy\"$fallback onerror=\"__mf(this)\"></a></div>"

            Display.VIDEO ->
                "<div class=\"media video$cls\"$attrs><video src=\"$src\" controls preload=\"metadata\"$fallback onerror=\"__mf(this)\"></video></div>"

            Display.GIFV ->
                "<div class=\"media$cls\"$attrs><video src=\"$src\" autoplay loop muted playsinline preload=\"auto\"$fallback onerror=\"__mf(this)\"></video></div>"

            Display.AUDIO, Display.FILE -> {
                val link = if (expired) "<span class=\"file-name\">${esc(name)}</span>"
                else "<a class=\"file-name\" href=\"$src\" target=\"_blank\" rel=\"noopener noreferrer\">${esc(name)}</a>"
                val meta = listOf(sizeLabel, if (expired) "link expired" else "").filter { it.isNotEmpty() }.joinToString(" &middot; ")
                val player = if (spec.display == Display.AUDIO && !expired) "<audio src=\"$src\" controls preload=\"none\" onerror=\"__mf(this)\"></audio>" else ""
                "<div class=\"file${if (expired) " expired" else ""}$cls\"$attrs>${icon(if (spec.display == Display.AUDIO) ICON_AUDIO else ICON_FILE)}" +
                    "<div class=\"file-info\">$link<div class=\"file-size\">$meta</div>$player</div></div>"
            }
        }
    }

    private fun detail(name: String, size: String) = listOf(name, size).filter { it.isNotEmpty() }.joinToString(" · ")

    private fun note(expired: Boolean) =
        if (expired) "Discord attachment links expire about 24 hours after they're created."
        else "This file could not be loaded. It may have been deleted."

    /** The placeholder shown in place of expired or missing media. Also used (empty) as the page script's template. */
    private fun goneHtml(title: String, detail: String, note: String, style: String, extraClass: String = ""): String =
        "<div class=\"gone${if (extraClass.isEmpty()) "" else " $extraClass"}\"${if (style.isEmpty()) "" else " style=\"$style\""}>" +
            icon(ICON_BROKEN_IMAGE) +
            "<div class=\"gone-text\"><div class=\"gone-title\">${esc(title)}</div><div class=\"gone-name\">${esc(detail)}</div>" +
            "<div class=\"gone-note\">${esc(note)}</div></div></div>"

    private fun fit(w: Int?, h: Int?, maxW: Int, maxH: Int): Pair<Int, Int>? {
        if (w == null || h == null || w <= 0 || h <= 0 || maxW <= 0 || maxH <= 0) return null
        val scale = minOf(1.0, maxW.toDouble() / w, maxH.toDouble() / h)
        return (w * scale).roundToInt().coerceAtLeast(1) to (h * scale).roundToInt().coerceAtLeast(1)
    }

    private fun resolve(request: MediaRequest): String? =
        safeUrl(runCatching { options.mediaResolver.resolve(request) }.getOrNull())

    private fun stickersHtml(stickers: List<Sticker>): String {
        val sb = StringBuilder("<div class=\"stickers\">")
        for (s in stickers) {
            if (s.format == StickerFormat.LOTTIE || s.format == StickerFormat.UNKNOWN) {
                sb.append("<div class=\"sticker-missing\">").append(icon(ICON_STICKER)).append(esc(s.name)).append("</div>")
                continue
            }
            val src = resolve(MediaRequest(MediaKind.STICKER, s.url)) ?: continue
            sb.append("<img class=\"sticker\" src=\"").append(esc(src)).append("\" alt=\"").append(esc(s.name))
                .append("\" title=\"").append(esc(s.name)).append("\" loading=\"lazy\" onerror=\"__ef(this)\">")
        }
        return sb.append("</div>").toString()
    }

    // ------------------------------------------------------------------ embeds

    private fun embed(e: Embed): String {
        val isPlain = e.title == null && e.description == null && e.author == null && e.fields.isEmpty() && e.footer == null
        // Bare image/GIF links render as just the media, like the client.
        if (isPlain && e.type == "image") {
            val m = e.thumbnail ?: e.image ?: return ""
            return "<div class=\"embed-media\">${media(m.toSpec(MediaKind.EMBED), 400, 300)}</div>"
        }
        if (isPlain && e.type == "gifv") {
            val v = e.video ?: return ""
            return "<div class=\"embed-media\">${media(v.toSpec(MediaKind.EMBED, display = Display.GIFV), 400, 300)}</div>"
        }

        val sb = StringBuilder("<div class=\"embed\"")
        e.color?.let { sb.append(" style=\"border-left-color:").append(hexColor(it)).append("\"") }
        sb.append("><div class=\"embed-grid\"><div class=\"embed-main\">")
        e.provider?.name?.takeIf { it.isNotBlank() }?.let {
            sb.append("<div class=\"embed-provider\">").append(linkOrText(e.provider.url, esc(it))).append("</div>")
        }
        e.author?.let { a ->
            sb.append("<div class=\"embed-author\">")
            (a.proxyIconUrl ?: a.iconUrl)?.let { url ->
                resolve(MediaRequest(MediaKind.EMBED, a.iconUrl ?: url, a.proxyIconUrl))?.let {
                    sb.append("<img src=\"").append(esc(it)).append("\" alt=\"\" onerror=\"this.remove()\">")
                }
            }
            sb.append(linkOrText(a.url, esc(a.name))).append("</div>")
        }
        e.title?.let { sb.append("<div class=\"embed-title\">").append(linkOrText(e.url, markdown.render(it, inline = true))).append("</div>") }
        e.description?.let { sb.append("<div class=\"embed-desc md\">").append(markdown.render(it)).append("</div>") }
        if (e.fields.isNotEmpty()) sb.append(fields(e.fields))
        sb.append("</div>")

        val bigVideo = e.type == "video" && e.video != null
        if (e.thumbnail != null && !bigVideo) {
            sb.append("<div class=\"embed-thumb\">").append(media(e.thumbnail.toSpec(MediaKind.EMBED), 80, 80)).append("</div>")
        }
        sb.append("</div>")

        e.image?.let { sb.append("<div class=\"embed-image\">").append(media(it.toSpec(MediaKind.EMBED), 400, 300)).append("</div>") }
        if (bigVideo) {
            val video = e.video!!
            val isFile = kindOf(video.contentType, video.url) == AttachmentKind.VIDEO
            if (isFile) {
                sb.append("<div class=\"embed-image\">").append(media(video.toSpec(MediaKind.EMBED, display = Display.VIDEO), 400, 300)).append("</div>")
            } else {
                // YouTube & co: a linked thumbnail instead of an iframe (keeps the page light and offline-friendly).
                val thumb = e.thumbnail
                if (thumb != null) {
                    sb.append("<a class=\"embed-image video-thumb\" href=\"").append(href(e.url ?: video.url))
                        .append("\" target=\"_blank\" rel=\"noopener noreferrer\">")
                        .append(media(thumb.toSpec(MediaKind.EMBED), 400, 300)).append(icon(ICON_PLAY)).append("</a>")
                }
            }
        }

        if (e.footer != null || e.timestamp != null) {
            sb.append("<div class=\"embed-footer\">")
            e.footer?.let { f ->
                (f.proxyIconUrl ?: f.iconUrl)?.let { url ->
                    resolve(MediaRequest(MediaKind.EMBED, f.iconUrl ?: url, f.proxyIconUrl))?.let {
                        sb.append("<img src=\"").append(esc(it)).append("\" alt=\"\" onerror=\"this.remove()\">")
                    }
                }
                sb.append("<span>").append(esc(f.text)).append("</span>")
            }
            e.timestamp?.let {
                if (e.footer != null) sb.append("<span class=\"dot\">&bull;</span>")
                sb.append(timeTag(it, 'm'))
            }
            sb.append("</div>")
        }
        return sb.append("</div>").toString()
    }

    /** Groups inline fields into rows of up to three, like the client. */
    private fun fields(fields: List<EmbedField>): String {
        val sb = StringBuilder("<div class=\"embed-fields\">")
        var row = mutableListOf<EmbedField>()
        fun flush() {
            if (row.isEmpty()) return
            sb.append("<div class=\"field-row\">")
            row.forEach { f ->
                sb.append("<div class=\"field\"><div class=\"field-name\">").append(markdown.render(f.name, inline = true))
                    .append("</div><div class=\"field-value md\">").append(markdown.render(f.value)).append("</div></div>")
            }
            sb.append("</div>")
            row = mutableListOf()
        }
        for (f in fields) {
            if (!f.inline) {
                flush()
                row.add(f)
                flush()
            } else {
                row.add(f)
                if (row.size == 3) flush()
            }
        }
        flush()
        return sb.append("</div>").toString()
    }

    private fun linkOrText(url: String?, html: String): String =
        if (safeUrl(url) != null) "<a href=\"${href(url)}\" target=\"_blank\" rel=\"noopener noreferrer\">$html</a>" else html

    // ------------------------------------------------------------------ components (legacy + V2)

    private fun component(c: MessageComponent, attachments: List<Attachment>): String = when (c) {
        is ActionRow -> "<div class=\"action-row\">${c.components.joinToString("") { component(it, attachments) }}</div>"
        is Button -> button(c)
        is SelectMenu -> {
            val text = c.defaultLabels.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: c.placeholder ?: "Make a selection"
            val placeholder = if (c.defaultLabels.isEmpty()) " placeholder" else ""
            "<div class=\"select${if (c.disabled) " disabled" else ""}\"><span class=\"select-text$placeholder\">${esc(text)}</span>${icon(ICON_CHEVRON)}</div>"
        }

        is TextDisplay -> "<div class=\"text-display md\">${markdown.render(c.content)}</div>"
        is Section -> {
            val accessory = c.accessory?.let {
                "<div class=\"section-accessory\">${component(it, attachments)}</div>"
            } ?: ""
            "<div class=\"section\"><div class=\"section-content\">${c.components.joinToString("") { component(it, attachments) }}</div>$accessory</div>"
        }

        is Thumbnail -> media(c.media.toSpec(MediaKind.COMPONENT, c.spoiler, c.description), 85, 85, extraClass = "v2-thumb")
        is MediaGallery -> gallery(c.items.map { it.media.toSpec(MediaKind.COMPONENT, it.spoiler, it.description) })
        is FileDisplay -> {
            val attachment = attachments.firstOrNull { it.url == c.media.url }
            val name = c.filename ?: attachment?.displayName ?: fileNameFromUrl(c.media.url)
            media(
                MediaSpec(
                    kind = MediaKind.COMPONENT, url = c.media.url, proxyUrl = c.media.proxyUrl, filename = name,
                    contentType = c.media.contentType, size = c.size ?: attachment?.size, spoiler = c.spoiler,
                    display = Display.FILE,
                ),
                0, 0,
            )
        }

        is Separator -> "<div class=\"separator${if (c.divider) " divider" else ""}${if (c.spacing == SeparatorSpacing.LARGE) " large" else ""}\"></div>"
        is Container -> {
            val style = c.accentColor?.let { " style=\"border-left-color:${hexColor(it)}\"" } ?: ""
            val accent = if (c.accentColor != null) " accent" else ""
            "<div class=\"v2-container$accent${if (c.spoiler) " spoiler-media" else ""}\"$style>${c.components.joinToString("") { component(it, attachments) }}</div>"
        }

        is UnknownComponent -> "<div class=\"unknown-component\">Unsupported component (${esc(c.type)})</div>"
    }

    private fun button(b: Button): String {
        val style = b.style.name.lowercase()
        val disabled = if (b.disabled) " disabled" else ""
        val inner = buildString {
            b.emoji?.let { append(emojiHtml(it, false)) }
            b.label?.let { append("<span>").append(esc(it)).append("</span>") }
            if (b.style == ButtonStyle.LINK) append(icon(ICON_EXTERNAL))
        }
        return if (b.style == ButtonStyle.LINK && safeUrl(b.url) != null) {
            "<a class=\"btn btn-$style$disabled\" href=\"${href(b.url)}\" target=\"_blank\" rel=\"noopener noreferrer\">$inner</a>"
        } else {
            "<span class=\"btn btn-$style$disabled\">$inner</span>"
        }
    }

    // ------------------------------------------------------------------ reactions, polls, emoji, time

    private fun reactions(reactions: List<Reaction>): String {
        val sb = StringBuilder("<div class=\"reactions\">")
        reactions.forEach {
            sb.append("<span class=\"reaction\" title=\"").append(esc(if (it.emoji.isCustom) ":${it.emoji.name}:" else it.emoji.name)).append("\">")
                .append(emojiHtml(it.emoji, false)).append("<span class=\"count\">").append(it.count).append("</span></span>")
        }
        return sb.append("</div>").toString()
    }

    private fun poll(p: Poll): String {
        val total = p.totalVotes
        val winning = p.answers.maxOfOrNull { it.votes } ?: 0
        val sb = StringBuilder("<div class=\"poll\"><div class=\"poll-q\">").append(esc(p.question)).append("</div>")
        sb.append("<div class=\"poll-sub\">").append(if (p.allowMultiselect) "Select one or more answers" else "Select one answer").append("</div>")
        p.answers.forEach { a ->
            val pct = if (total > 0) (a.votes * 100.0 / total).roundToInt() else 0
            val win = p.isFinalized && a.votes == winning && winning > 0
            sb.append("<div class=\"poll-answer").append(if (win) " winner" else "").append("\"><div class=\"poll-bar\" style=\"width:").append(pct).append("%\"></div>")
            sb.append("<span class=\"poll-text\">")
            a.emoji?.let { sb.append(emojiHtml(it, false)) }
            sb.append(esc(a.text ?: "")).append("</span><span class=\"poll-votes\">").append(a.votes)
                .append(if (a.votes == 1) " vote" else " votes").append(" &middot; ").append(pct).append("%</span></div>")
        }
        sb.append("<div class=\"poll-foot\">").append(total).append(if (total == 1) " vote" else " votes").append(" &middot; ")
        when {
            p.isFinalized -> sb.append("Poll closed")
            p.expiresAt != null && p.expiresAt.isBefore(now) -> sb.append("Poll ended ").append(timeTag(p.expiresAt, 'R'))
            p.expiresAt != null -> sb.append("Ends ").append(timeTag(p.expiresAt, 'R'))
        }
        return sb.append("</div></div>").toString()
    }

    private fun emojiHtml(emoji: Emoji, jumbo: Boolean): String {
        val url = emoji.imageUrl ?: return "<span class=\"emoji-u\">${esc(emoji.name)}</span>"
        val src = resolve(MediaRequest(MediaKind.EMOJI, url)) ?: return esc(":${emoji.name}:")
        val alt = esc(":${emoji.name}:")
        return "<img class=\"emoji${if (jumbo) " jumbo" else ""}\" src=\"${esc(src)}\" alt=\"$alt\" title=\"$alt\" loading=\"lazy\" onerror=\"__ef(this)\">"
    }

    private fun timestampHtml(instant: Instant, style: Char): String = timeTag(instant, style, "ts")

    /**
     * A `<time>` element. The text is formatted server-side in [RenderOptions.zoneId]; the page script re-formats it
     * in the viewer's locale and time zone. Styles are Discord's (`t T d D f F R`) plus `m` for message headers.
     */
    private fun timeTag(instant: Instant, style: Char, cls: String? = null): String {
        val text = when (style) {
            't' -> fmtTime.format(instant)
            'T' -> fmtTimeSec.format(instant)
            'd' -> fmtDate.format(instant)
            'D' -> fmtDateLong.format(instant)
            'F' -> fmtDateTimeFull.format(instant)
            'R' -> relative(instant)
            'm' -> "${fmtDate.format(instant)} ${fmtTime.format(instant)}"
            else -> fmtDateTime.format(instant)
        }
        val c = cls?.let { " class=\"$it\"" } ?: ""
        return "<time$c datetime=\"$instant\" data-ts=\"${instant.toEpochMilli()}\" data-f=\"$style\" title=\"${esc(fmtDateTimeFull.format(instant))}\">${esc(text)}</time>"
    }

    private fun relative(instant: Instant): String {
        val seconds = Duration.between(now, instant).seconds
        val abs = kotlin.math.abs(seconds)
        val (value, unit) = when {
            abs < 60 -> abs to "second"
            abs < 3600 -> abs / 60 to "minute"
            abs < 86_400 -> abs / 3600 to "hour"
            abs < 2_592_000 -> abs / 86_400 to "day"
            abs < 31_536_000 -> abs / 2_592_000 to "month"
            else -> abs / 31_536_000 to "year"
        }
        val plural = if (value == 1L) unit else "${unit}s"
        return if (seconds < 0) "$value $plural ago" else "in $value $plural"
    }

    private fun icon(svg: String) = svg

    private fun isEmojiOnly(content: String): Boolean {
        val withoutCustom = CUSTOM_EMOJI_TOKEN.replace(content, "\u0000")
        if (withoutCustom.isBlank()) return false
        var count = 0
        var i = 0
        while (i < withoutCustom.length) {
            val cp = withoutCustom.codePointAt(i)
            i += Character.charCount(cp)
            when {
                cp == 0 -> count++
                Character.isWhitespace(cp) -> Unit
                cp == 0x200D || cp == 0xFE0F || cp == 0x20E3 || cp in 0x1F3FB..0x1F3FF || cp in 0xE0020..0xE007F -> Unit
                isEmojiCodePoint(cp) -> count++
                else -> return false
            }
        }
        return count in 1..30
    }

    private fun isEmojiCodePoint(cp: Int): Boolean =
        cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2300..0x23FF || cp in 0x2B00..0x2BFF ||
            cp in 0x1F1E6..0x1F1FF || cp == 0x00A9 || cp == 0x00AE || cp in 0x2100..0x214F || cp in 0x2190..0x21FF ||
            cp in 0x3030..0x303D || cp == 0x3297 || cp == 0x3299

    private fun fileNameFromUrl(url: String): String =
        url.substringBefore('?').substringBefore('#').substringAfterLast('/').ifEmpty { "file" }

    private companion object {
        val CUSTOM_EMOJI_TOKEN = Regex("""<a?:[A-Za-z0-9_~]{1,32}:\d{15,21}>""")

        fun svg(path: String, viewBox: String = "0 0 24 24") =
            "<svg class=\"ico\" viewBox=\"$viewBox\" aria-hidden=\"true\"><path fill=\"currentColor\" d=\"$path\"/></svg>"

        val ICON_BROKEN_IMAGE = svg("M21 5v6.59l-2.29-2.3a1 1 0 0 0-1.42 0L14 12.59l-3.29-3.3a1 1 0 0 0-1.42 0L3 15.59V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2Zm-18 13.41 6.29-6.3 3.29 3.3a1 1 0 0 0 1.42 0L17.29 12l3.71 3.71V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-.59ZM2.71 1.29l20 20-1.42 1.42-20-20 1.42-1.42Z")
        val ICON_FILE = svg("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6Zm4 18H6V4h7v5h5v11Z")
        val ICON_AUDIO = svg("M12 3v10.55A4 4 0 1 0 14 17V7h4V3h-6Z")
        val ICON_STICKER = svg("M12 2a10 10 0 1 0 10 10V11h-5a6 6 0 0 1-6-6V2h1Zm1 .07V5a4 4 0 0 0 4 4h4.93A10 10 0 0 0 13 2.07Z")
        val ICON_CLOCK = svg("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm1 10.41V6h-2v7.41l4.29 4.3 1.42-1.42L13 12.41Z")
        val ICON_JOIN = svg("M4 12h12.17l-4.58-4.59L13 6l7 7-7 7-1.41-1.41L16.17 14H4v-2Z")
        val ICON_LEAVE = svg("M20 12H7.83l4.58-4.59L11 6l-7 7 7 7 1.41-1.41L7.83 14H20v-2Z")
        val ICON_PIN = svg("M16 3v2h-1v6l3 3v2h-5v6l-1 1-1-1v-6H6v-2l3-3V5H8V3h8Z")
        val ICON_THREAD = svg("M5 3h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H9l-4 4v-4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z")
        val ICON_PENCIL = svg("M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25ZM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83Z")
        val ICON_CALL = svg("M6.6 10.8a15.1 15.1 0 0 0 6.6 6.6l2.2-2.2a1 1 0 0 1 1-.25c1.1.37 2.3.57 3.6.57a1 1 0 0 1 1 1V20a1 1 0 0 1-1 1A17 17 0 0 1 3 4a1 1 0 0 1 1-1h3.5a1 1 0 0 1 1 1c0 1.25.2 2.45.57 3.57a1 1 0 0 1-.25 1L6.6 10.8Z")
        val ICON_BOOST = svg("M12 2 4 7v10l8 5 8-5V7l-8-5Zm0 3.24L17 8.4v7.2L12 18.76 7 15.6V8.4l5-3.16Z")
        val ICON_INFO = svg("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm1 15h-2v-6h2v6Zm0-8h-2V7h2v2Z")
        val ICON_FORWARD = svg("M14 5l7 7-7 7v-4.1C9 14.9 5.5 16.5 3 20c1-5 4-10 11-11V5Z")
        val ICON_PLAY = "<svg class=\"play\" viewBox=\"0 0 24 24\" aria-hidden=\"true\"><circle cx=\"12\" cy=\"12\" r=\"12\" fill=\"rgba(0,0,0,.6)\"/><path fill=\"#fff\" d=\"M9.5 7.5v9l7-4.5-7-4.5Z\"/></svg>"
        val ICON_EXTERNAL = svg("M10 5V3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-5h-2v5H5V5h5Zm4-2v2h3.59l-9.3 9.29 1.42 1.42L19 6.41V10h2V3h-7Z")
        val ICON_CHEVRON = svg("M7.41 8.58 12 13.17l4.59-4.59L18 10l-6 6-6-6 1.41-1.42Z")
    }
}
