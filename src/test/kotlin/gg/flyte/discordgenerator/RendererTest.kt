package gg.flyte.discordgenerator

import gg.flyte.discordgenerator.media.DiscordCdn
import gg.flyte.discordgenerator.media.MediaResolver
import gg.flyte.discordgenerator.model.*
import gg.flyte.discordgenerator.render.RenderOptions
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RendererTest {

    private val now = Instant.now()

    private fun signed(path: String, expires: Instant) =
        "https://cdn.discordapp.com/attachments/1/2/$path?ex=${java.lang.Long.toHexString(expires.epochSecond)}&is=0&hm=abc&"

    private val dan = Author("256476609102610433", "Dan", "danislit", avatarUrl = "https://cdn.discordapp.com/embed/avatars/1.png", color = 0xe67e22)
    private val player = Author("730483347599655002", "Steve", "steve123", avatarUrl = "https://cdn.discordapp.com/embed/avatars/3.png")
    private val bot = Author("1000000000000000001", "EnchantedMC", isBot = true, avatarUrl = "https://cdn.discordapp.com/embed/avatars/0.png", color = 0x9b59b6)

    @Test
    fun `parses discord expiry`() {
        val at = Instant.ofEpochSecond(1731111111)
        assertEquals(at, DiscordCdn.expiresAt(signed("a.png", at)))
        assertNull(DiscordCdn.expiresAt("https://cdn.discordapp.com/avatars/1/abc.png"))
        assertNull(DiscordCdn.expiresAt("https://example.com/a.png?ex=6553f1c7"))
    }

    @Test
    fun `expired attachments render placeholders`() {
        val html = DiscordGenerator.render(
            Transcript(
                messages = listOf(
                    Message(
                        "1", player, now,
                        attachments = listOf(
                            Attachment("1", "old.png", signed("old.png", now.minus(1, ChronoUnit.DAYS)), size = 120_000, width = 800, height = 600),
                            Attachment("2", "log.txt", signed("log.txt", now.minus(1, ChronoUnit.DAYS)), size = 2048),
                        ),
                    ),
                ),
            ),
        )
        assertTrue(html.contains("Image expired"))
        assertTrue(html.contains("link expired"))
        assertFalse(html.contains("<img src=\"https://cdn.discordapp.com/attachments/1/2/old.png"))
        assertTrue(html.contains("id=\"expiry-notice\"") && !html.contains("id=\"expiry-notice\" hidden"))
    }

    @Test
    fun `fresh attachments are linked with expiry metadata`() {
        val ex = now.plus(1, ChronoUnit.DAYS)
        val html = DiscordGenerator.render(
            Transcript(listOf(Message("1", player, now, attachments = listOf(Attachment("1", "a.png", signed("a.png", ex), width = 10, height = 10))))),
        )
        assertTrue(html.contains("data-ex=\"${ex.epochSecond}\""))
        assertTrue(html.contains("onerror=\"__mf(this)\""))
    }

    @Test
    fun `resolver can rehost or drop media`() {
        val html = DiscordGenerator.render(
            Transcript(listOf(Message("1", player, now, attachments = listOf(Attachment("1", "a.png", signed("a.png", now), width = 10, height = 10))))),
            RenderOptions(mediaResolver = { req -> if (req.kind == gg.flyte.discordgenerator.media.MediaKind.ATTACHMENT) "https://files.example.com/a.png" else req.url }),
        )
        assertTrue(html.contains("src=\"https://files.example.com/a.png\""))
        assertFalse(html.contains("Image expired"))
    }

    @Test
    fun `names and embeds are escaped`() {
        val evil = Author("1", "<img src=x onerror=alert(1)>")
        val html = DiscordGenerator.render(
            Transcript(
                listOf(Message("1", evil, now, embeds = listOf(Embed(title = "<script>x</script>", url = "javascript:alert(1)")))),
                title = "</title><script>alert(1)</script>",
            ),
        )
        assertFalse(html.contains("<img src=x"))
        assertFalse(html.contains("<script>x"))
        assertFalse(html.contains("</title><script>"))
        assertFalse(html.contains("href=\"javascript"))
    }

    /** Writes a transcript covering every feature to build/sample-transcript.html for eyeballing. */
    @Test
    fun `sample transcript`() {
        val t0 = now.minus(2, ChronoUnit.DAYS)
        fun at(minutes: Long) = t0.plus(minutes, ChronoUnit.MINUTES)
        val expired = now.minus(3, ChronoUnit.HOURS)
        val fresh = now.plus(20, ChronoUnit.HOURS)
        val img = "https://placehold.co/1280x720/5865f2/ffffff/png?text=Screenshot"

        val messages = listOf(
            Message("100", bot, at(0), isComponentsV2 = true, components = listOf(
                Container(accentColor = 0x9b59b6, components = listOf(
                    TextDisplay("### Steve - Support Ticket #4821"),
                    Separator(divider = true, spacing = SeparatorSpacing.LARGE),
                    Section(
                        components = listOf(TextDisplay("Thanks for opening a ticket! A member of <@&223456789012345678> will be with you shortly.\n-# Please describe your issue below.")),
                        accessory = Thumbnail(Media("https://cdn.discordapp.com/embed/avatars/3.png")),
                    ),
                    Separator(),
                    TextDisplay("**Category:** General Support\n**IGN:** `Steve`"),
                    ActionRow(listOf(
                        Button(ButtonStyle.DANGER, "Close", Emoji("🔒")),
                        Button(ButtonStyle.SECONDARY, "Claim"),
                        Button(ButtonStyle.LINK, "Store", url = "https://store.example.com"),
                    )),
                )),
            )),
            Message("101", player, at(1), content = "hey, I can't join the server since the update :(\nit says `Outdated client!` when I connect"),
            Message("102", player, at(1), content = "here's a screenshot from earlier (this link has expired)", attachments = listOf(
                Attachment("1", "image.png", signed("image.png", expired), size = 1_482_113, width = 1920, height = 1080, contentType = "image/png"),
            )),
            Message("103", player, at(2), content = "and these two are still valid", attachments = listOf(
                Attachment("2", "launcher.png", img, size = 412_000, width = 1280, height = 720, contentType = "image/png"),
                Attachment("3", "crash.png", signed("crash.png", expired), size = 98_000, width = 1280, height = 720, contentType = "image/png"),
            )),
            Message("104", player, at(3), attachments = listOf(
                Attachment("4", "latest.log", signed("latest.log", fresh), size = 18_204, contentType = "text/plain"),
                Attachment("5", "old-crash-report.txt", signed("old.txt", expired), size = 3_204, contentType = "text/plain"),
            )),
            Message("105", dan, at(9), content = "Hey <@730483347599655002>! Which version are you on? We support **1.20.4 - 1.21.4**.\n> make sure you're not using a modded client\nCheck <#323456789012345678> for more info.",
                reactions = listOf(Reaction(Emoji("👍"), 2), Reaction(Emoji("pepeok", "1105585766393778256"), 1))),
            Message("106", player, at(10), content = "ohh I'm on 1.19, my bad", reply = ReplyPreview("105", dan, "Hey <@730483347599655002>! Which version are you on?")),
            Message("107", player, at(10), content = "<a:catjam:1109221010373373972>", editedAt = at(11)),
            Message("108", dan, at(12), content = "No worries! Here's a guide:", embeds = listOf(
                Embed(
                    title = "How to update your client", url = "https://example.com/guide", color = 0x5865f2,
                    description = "1. Open the launcher\n2. Select **1.21.4**\n3. Press `Play`\n\n||you can also use ViaVersion||",
                    author = EmbedAuthor("EnchantedMC Wiki", iconUrl = "https://cdn.discordapp.com/embed/avatars/0.png"),
                    fields = listOf(EmbedField("Java", "1.21.4", true), EmbedField("Bedrock", "1.21.50", true), EmbedField("Discord", "[Join](https://discord.gg/x)", true), EmbedField("Note", "Restart the launcher after updating.")),
                    thumbnail = Media("https://cdn.discordapp.com/embed/avatars/2.png", width = 128, height = 128),
                    footer = EmbedFooter("EnchantedMC", "https://cdn.discordapp.com/embed/avatars/0.png"), timestamp = at(12),
                ),
            )),
            Message("109", player, at(15), content = "https://tenor.com/view/thanks", embeds = listOf(
                Embed(type = "gifv", url = "https://tenor.com/view/thanks", video = Media("https://media.tenor.com/doesnotexist.mp4", width = 498, height = 280)),
            )),
            Message("110", bot, at(16), type = MessageType.SLASH_COMMAND, interaction = InteractionInfo(dan, "close"), content = "Closing ticket..",
                poll = Poll("Was this helpful?", listOf(PollAnswer("Yes", Emoji("✅"), 7), PollAnswer("No", Emoji("❌"), 1)), isFinalized = true)),
            Message("111", dan, at(17), type = MessageType.CHANNEL_PINNED_ADD),
            Message("112", dan, at(18), forwards = listOf(ForwardedMessage(content = "Server maintenance tonight at **22:00** <t:1731099600:R>", timestamp = at(-600)))),
            Message("113", player, at(19), content = "<script>alert('xss')</script> lol", stickers = listOf(Sticker("1", "Wave", StickerFormat.LOTTIE, ""))),
            Message("114", player, at(20), content = "🎉🎉"),
            Message("115", player, at(21), content = "voice note", attachments = listOf(
                Attachment("6", "voice-message.ogg", signed("voice.ogg", fresh), size = 40_000, contentType = "audio/ogg", isVoiceMessage = true),
                Attachment("7", "SPOILER_secret.png", img, size = 412_000, width = 1280, height = 720, contentType = "image/png"),
            )),
        )

        val html = DiscordGenerator.render(
            Transcript(
                messages = messages,
                title = "Ticket #4821 — Steve",
                guild = GuildInfo("1", "EnchantedMC", "https://cdn.discordapp.com/embed/avatars/4.png"),
                channel = ChannelInfo("2", "ticket-4821", "General support ticket"),
                mentions = MentionIndex(
                    users = mapOf("730483347599655002" to MentionedUser("Steve")),
                    roles = mapOf("223456789012345678" to MentionedRole("Support Team", 0x3498db)),
                    channels = mapOf("323456789012345678" to "server-info"),
                ),
            ),
            RenderOptions(zoneId = ZoneId.of("Europe/London"), mediaResolver = MediaResolver.HOTLINK),
        )
        assertFalse(html.contains("<script>alert('xss')"))
        System.getProperty("sampleOutput")?.let { File(it).apply { parentFile.mkdirs() }.writeText(html) }
    }
}
