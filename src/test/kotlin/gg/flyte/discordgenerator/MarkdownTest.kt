package gg.flyte.discordgenerator

import gg.flyte.discordgenerator.model.MentionIndex
import gg.flyte.discordgenerator.model.MentionedRole
import gg.flyte.discordgenerator.model.MentionedUser
import gg.flyte.discordgenerator.render.DiscordMarkdown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownTest {
    private val md = DiscordMarkdown(
        MentionIndex(
            users = mapOf("123456789012345678" to MentionedUser("Dan")),
            roles = mapOf("223456789012345678" to MentionedRole("Staff", 0xff0000)),
            channels = mapOf("323456789012345678" to "general"),
        ),
        emojiHtml = { e, _ -> "[emoji:${e.name}]" },
        timestampHtml = { i, s -> "[ts:${i.epochSecond}:$s]" },
    )

    private fun r(s: String) = md.render(s)

    @Test
    fun `escapes html`() {
        val out = r("<script>alert(1)</script> <img src=x onerror=alert(1)> & \"quotes\"")
        assertFalse(out.contains("<script"))
        assertFalse(out.contains("<img"))
        assertTrue(out.contains("&lt;script&gt;"))
        assertTrue(out.contains("&amp;"))
    }

    @Test
    fun `blocks javascript links`() {
        val out = r("[click](javascript:alert(1))")
        assertFalse(out.contains("href=\"javascript"))
    }

    @Test
    fun `escapes inside formatting and code`() {
        assertEquals("<strong>&lt;b&gt;</strong>", r("**<b>**"))
        assertEquals("<code class=\"inline\">&lt;i&gt;</code>", r("`<i>`"))
        assertTrue(r("```html\n<div>\n```").contains("<code>&lt;div&gt;</code>"))
    }

    @Test
    fun `inline formatting`() {
        assertEquals("<strong>bold</strong>", r("**bold**"))
        assertEquals("<em>it</em> <em>it</em>", r("*it* _it_"))
        assertEquals("<u>under</u>", r("__under__"))
        assertEquals("<s>gone</s>", r("~~gone~~"))
        assertEquals("<strong><em>both</em></strong>", r("***both***"))
        assertTrue(r("||secret||").startsWith("<span class=\"spoiler\""))
    }

    @Test
    fun `underscores inside words stay literal`() {
        assertEquals("snake_case_name", r("snake_case_name"))
    }

    @Test
    fun `escaped markdown`() {
        assertEquals("*not italic*", r("\\*not italic\\*"))
    }

    @Test
    fun `blocks`() {
        assertEquals("<h1>Title</h1>body", r("# Title\nbody"))
        assertEquals("<h3>Small</h3>", r("### Small"))
        assertEquals("#nospace", r("#nospace"))
        assertEquals("<div class=\"subtext\">tiny</div>", r("-# tiny"))
        assertEquals("<blockquote>quoted<br>more</blockquote>after", r("> quoted\n> more\nafter"))
        assertEquals("<blockquote>all<br>of it</blockquote>", r(">>> all\nof it"))
        assertEquals("<ul><li>one</li><li>two<ul><li>nested</li></ul></li></ul>", r("- one\n- two\n  - nested"))
        assertEquals("<ol start=\"3\"><li>three</li></ol>", r("3. three"))
        assertEquals("not # a heading", r("not # a heading"))
    }

    @Test
    fun `links`() {
        assertTrue(r("see https://example.com/a_b.").contains("href=\"https://example.com/a_b\""))
        assertTrue(r("see https://example.com/a_b.").endsWith("</a>."))
        assertTrue(r("(https://en.wikipedia.org/wiki/Foo_(bar))").contains("href=\"https://en.wikipedia.org/wiki/Foo_(bar)\""))
        val masked = r("[**docs**](https://example.com)")
        assertTrue(masked.contains("href=\"https://example.com\""))
        assertTrue(masked.contains("<strong>docs</strong>"))
        assertTrue(r("<https://example.com>").contains("href=\"https://example.com\""))
    }

    @Test
    fun `mentions emoji and timestamps`() {
        assertTrue(r("<@123456789012345678>").contains("@Dan"))
        assertTrue(r("<@!999999999999999999>").contains("@unknown-user"))
        assertTrue(r("<@&223456789012345678>").contains("@Staff"))
        assertTrue(r("<@&223456789012345678>").contains("color:#ff0000"))
        assertTrue(r("<#323456789012345678>").contains("#general"))
        assertEquals("[emoji:pepe]", r("<a:pepe:123456789012345678>"))
        assertEquals("[ts:1700000000:R]", r("<t:1700000000:R>"))
        assertEquals("[ts:1700000000:f]", r("<t:1700000000>"))
        assertTrue(r("</ticket close:123456789012345678>").contains("/ticket close"))
        assertTrue(r("@everyone").contains("class=\"mention\""))
    }

    @Test
    fun `newlines`() {
        assertEquals("a<br>b", r("a\nb"))
        assertEquals("a<br>b", r("a\r\nb"))
    }
}
