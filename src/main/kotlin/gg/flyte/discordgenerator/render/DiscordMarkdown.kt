package gg.flyte.discordgenerator.render

import gg.flyte.discordgenerator.model.Emoji
import gg.flyte.discordgenerator.model.MentionIndex
import java.time.Instant
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Converts Discord-flavoured markdown to HTML.
 *
 * Every piece of user text goes through [esc]; the only markup in the output is markup this class writes itself.
 * The rules follow Discord's client (which is built on simple-markdown) closely enough for transcripts:
 * headings, subtext, lists, block quotes, code blocks, bold/italic/underline/strike, spoilers, masked links,
 * autolinks, mentions, custom emoji, timestamps and slash-command mentions.
 */
internal class DiscordMarkdown(
    private val mentions: MentionIndex,
    private val emojiHtml: (emoji: Emoji, jumbo: Boolean) -> String,
    private val timestampHtml: (instant: Instant, style: Char) -> String,
) {
    private data class State(
        /** No block-level rules (headings, lists, quotes). */
        val inline: Boolean,
        val inQuote: Boolean = false,
        /** Inside a masked link's label: no nested links. */
        val inLink: Boolean = false,
        val jumbo: Boolean = false,
    )

    private class Match(val html: String, val end: Int)

    /**
     * @param inline disable block rules, for single-line contexts such as embed titles and reply previews.
     * @param jumbo render custom emoji large (message is emoji-only).
     */
    fun render(source: String, inline: Boolean = false, jumbo: Boolean = false): String {
        if (source.isEmpty()) return ""
        return parse(source.replace("\r\n", "\n"), State(inline = inline, jumbo = jumbo))
    }

    private fun parse(src: String, st: State): String {
        val out = StringBuilder(src.length + 32)
        var pos = 0
        while (pos < src.length) {
            val lineStart = pos == 0 || src[pos - 1] == '\n'
            val match = (if (lineStart && !st.inline) block(src, pos, st) else null) ?: inline(src, pos, st)
            if (match != null && match.end > pos) {
                out.append(match.html)
                pos = match.end
            } else {
                val end = textEnd(src, pos + 1)
                out.append(esc(src.substring(pos, end)))
                pos = end
            }
        }
        return out.toString()
    }

    // ---------------------------------------------------------------- block rules

    private fun block(src: String, pos: Int, st: State): Match? {
        if (!st.inQuote) {
            QUOTE_MULTI.at(src, pos)?.let { m ->
                val inner = m.group(1)
                if (inner.isNotBlank()) {
                    return Match("<blockquote>${parse(inner, st.copy(inQuote = true))}</blockquote>", src.length)
                }
            }
            QUOTE.at(src, pos)?.let { m ->
                val inner = m.group().trimEnd('\n').split('\n')
                    .joinToString("\n") { it.replaceFirst(QUOTE_PREFIX, "") }
                if (inner.isNotBlank()) {
                    return Match("<blockquote>${parse(inner, st.copy(inQuote = true))}</blockquote>", m.end())
                }
            }
        }
        HEADING.at(src, pos)?.let { m ->
            val level = m.group(1).length
            return Match("<h$level>${parse(m.group(2), st.copy(inline = true))}</h$level>", m.end())
        }
        SUBTEXT.at(src, pos)?.let { m ->
            return Match("<div class=\"subtext\">${parse(m.group(1), st.copy(inline = true))}</div>", m.end())
        }
        LIST.at(src, pos)?.let { m ->
            val items = m.group().trimEnd('\n').split('\n').mapNotNull { line ->
                LIST_ITEM.matcher(line).takeIf { it.matches() }?.let {
                    val marker = it.group(2)
                    ListItem(
                        indent = it.group(1).replace("\t", "    ").length,
                        ordered = marker.endsWith('.'),
                        number = marker.removeSuffix(".").toIntOrNull() ?: 1,
                        text = it.group(3),
                    )
                }
            }
            if (items.isNotEmpty()) {
                val sb = StringBuilder()
                buildList(items, 0, sb, st)
                return Match(sb.toString(), m.end())
            }
        }
        return null
    }

    private class ListItem(val indent: Int, val ordered: Boolean, val number: Int, val text: String)

    /** Renders items sharing the first item's indent (plus deeper, nested items). Returns the next unconsumed index. */
    private fun buildList(items: List<ListItem>, start: Int, out: StringBuilder, st: State): Int {
        val first = items[start]
        val base = first.indent
        val tag = if (first.ordered) "ol" else "ul"
        out.append(if (first.ordered && first.number != 1) "<ol start=\"${first.number}\">" else "<$tag>")
        var i = start
        while (i < items.size && items[i].indent >= base) {
            if (items[i].indent > base) {
                // Deeper item with no parent at this level yet; render it as its own nested list.
                i = buildList(items, i, out, st)
                continue
            }
            out.append("<li>").append(parse(items[i].text, st.copy(inline = true)))
            i++
            if (i < items.size && items[i].indent > base) i = buildList(items, i, out, st)
            out.append("</li>")
        }
        out.append("</$tag>")
        return i
    }

    // ---------------------------------------------------------------- inline rules

    private fun inline(src: String, pos: Int, st: State): Match? {
        val inner = st.copy(inline = true)
        return when (src[pos]) {
            '\\' -> ESCAPE.at(src, pos)?.let { Match(esc(it.group(1)), it.end()) }

            '`' -> CODE_BLOCK.at(src, pos)?.let {
                val lang = it.group(1)?.let { l -> " data-lang=\"${esc(l)}\"" } ?: ""
                Match("<pre class=\"codeblock\"$lang><code>${esc(it.group(2))}</code></pre>", it.end())
            } ?: CODE_INLINE.at(src, pos)?.let {
                Match("<code class=\"inline\">${esc(it.group(2).trim().ifEmpty { it.group(2) })}</code>", it.end())
            }

            '<' -> angle(src, pos, st)

            '[' -> if (st.inLink) null else MASKED_LINK.at(src, pos)?.let {
                val url = it.group(2)
                Match(
                    "<a href=\"${href(url)}\" title=\"${esc(url)}\" target=\"_blank\" rel=\"noopener noreferrer\">" +
                        "${parse(it.group(1), inner.copy(inLink = true))}</a>",
                    it.end(),
                )
            }

            'h', 'H' -> if (st.inLink) null else bareUrl(src, pos)

            '@' -> EVERYONE.at(src, pos)?.let { Match("<span class=\"mention\">@${it.group(1)}</span>", it.end()) }

            '|' -> SPOILER.at(src, pos)?.let {
                Match("<span class=\"spoiler\" onclick=\"this.classList.add('shown')\">${parse(it.group(1), inner)}</span>", it.end())
            }

            '*' -> BOLD.at(src, pos)?.let { Match("<strong>${parse(it.group(1), inner)}</strong>", it.end()) }
                ?: ITALIC_STAR.at(src, pos)?.let { Match("<em>${parse(it.group(1), inner)}</em>", it.end()) }

            '_' -> UNDERLINE.at(src, pos)?.let { Match("<u>${parse(it.group(1), inner)}</u>", it.end()) }
                ?: ITALIC_UNDERSCORE.at(src, pos)?.let { Match("<em>${parse(it.group(1), inner)}</em>", it.end()) }

            '~' -> STRIKE.at(src, pos)?.let { Match("<s>${parse(it.group(1), inner)}</s>", it.end()) }

            '\n' -> Match("<br>", pos + 1)

            else -> null
        }
    }

    private fun angle(src: String, pos: Int, st: State): Match? {
        if (!st.inLink) AUTOLINK.at(src, pos)?.let { return Match(link(it.group(1), esc(it.group(1))), it.end()) }

        USER_MENTION.at(src, pos)?.let {
            val user = mentions.users[it.group(1)]
            val style = user?.color?.let(::mentionStyle) ?: ""
            return Match("<span class=\"mention\"$style>@${esc(user?.name ?: "unknown-user")}</span>", it.end())
        }
        ROLE_MENTION.at(src, pos)?.let {
            val role = mentions.roles[it.group(1)]
            val style = role?.color?.let(::mentionStyle) ?: ""
            return Match("<span class=\"mention\"$style>@${esc(role?.name ?: "deleted-role")}</span>", it.end())
        }
        CHANNEL_MENTION.at(src, pos)?.let {
            val name = mentions.channels[it.group(1)] ?: "unknown"
            return Match("<span class=\"mention channel\">#${esc(name)}</span>", it.end())
        }
        CUSTOM_EMOJI.at(src, pos)?.let {
            val emoji = Emoji(name = it.group(2), id = it.group(3), animated = it.group(1) == "a")
            return Match(emojiHtml(emoji, st.jumbo), it.end())
        }
        TIMESTAMP.at(src, pos)?.let {
            val seconds = it.group(1).toLongOrNull() ?: return null
            val style = it.group(2)?.firstOrNull() ?: 'f'
            val instant = runCatching { Instant.ofEpochSecond(seconds) }.getOrNull() ?: return null
            return Match(timestampHtml(instant, style), it.end())
        }
        SLASH_COMMAND.at(src, pos)?.let {
            return Match("<span class=\"mention\">/${esc(it.group(1))}</span>", it.end())
        }
        GUILD_NAV.at(src, pos)?.let {
            val label = when (it.group(1)) {
                "customize" -> "Channels & Roles"
                "browse" -> "Browse Channels"
                "guide" -> "Server Guide"
                else -> "Linked Roles"
            }
            return Match("<span class=\"mention channel\">${esc(label)}</span>", it.end())
        }
        return null
    }

    private fun bareUrl(src: String, pos: Int): Match? {
        val m = URL.at(src, pos) ?: return null
        var url = m.group()
        // Trim trailing punctuation, and closing brackets that weren't opened inside the URL.
        while (url.isNotEmpty()) {
            val last = url.last()
            url = when {
                last in ".,:;!?\"'" -> url.dropLast(1)
                last == ')' && url.count { it == '(' } < url.count { it == ')' } -> url.dropLast(1)
                last == ']' && url.count { it == '[' } < url.count { it == ']' } -> url.dropLast(1)
                else -> break
            }
        }
        if (url.length <= "https://".length) return null
        return Match(link(url, esc(url)), pos + url.length)
    }

    private fun link(url: String, labelHtml: String): String =
        "<a href=\"${href(url)}\" target=\"_blank\" rel=\"noopener noreferrer\">$labelHtml</a>"

    private fun mentionStyle(color: Int): String {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return " style=\"color:${hexColor(color)};background:rgba($r,$g,$b,.12)\""
    }

    /** First index at or after [from] where another rule might match. */
    private fun textEnd(src: String, from: Int): Int {
        var i = from
        while (i < src.length) {
            val c = src[i]
            if (c in SPECIAL) return i
            if ((c == 'h' || c == 'H') && src.regionMatches(i, "http", 0, 4, ignoreCase = true)) return i
            i++
        }
        return src.length
    }

    private companion object {
        const val SPECIAL = "\\`<[@|*_~\n"

        fun p(regex: String): Pattern = Pattern.compile(regex)

        /** Matches [this] exactly at [pos] (lookbehinds/`\b` can still see text before [pos]). */
        fun Pattern.at(src: String, pos: Int): Matcher? {
            val m = matcher(src)
            m.region(pos, src.length)
            m.useTransparentBounds(true)
            m.useAnchoringBounds(false)
            return if (m.lookingAt()) m else null
        }

        val QUOTE_MULTI = p("""[ \t]*>>>[ \t\n]([\s\S]*)""")
        val QUOTE = p("""(?:[ \t]*>(?:[ \t][^\n]*)?(?:\n|$))+""")
        val QUOTE_PREFIX = Regex("""^[ \t]*> ?""")
        val HEADING = p("""(#{1,3})[ \t]+([^\n]*?\S)[ \t]*(?:\n|$)""")
        val SUBTEXT = p("""-#[ \t]+([^\n]*?\S)[ \t]*(?:\n|$)""")
        val LIST = p("""(?:[ \t]*(?:[*-]|\d{1,9}\.)[ \t]+\S[^\n]*(?:\n|$))+""")
        val LIST_ITEM = p("""([ \t]*)([*-]|\d{1,9}\.)[ \t]+(.*)""")

        val ESCAPE = p("""\\([^0-9A-Za-z\s])""")
        val CODE_BLOCK = p("""```(?:([A-Za-z0-9_+\-.#]+)[ \t]*\n)?\n?([\s\S]*?)\n?```""")
        val CODE_INLINE = p("""(`+)([\s\S]*?[^`])\1(?!`)""")
        val AUTOLINK = p("""<(https?://[^\s>]+)>""")
        val USER_MENTION = p("""<@!?(\d{15,21})>""")
        val ROLE_MENTION = p("""<@&(\d{15,21})>""")
        val CHANNEL_MENTION = p("""<#(\d{15,21})>""")
        val CUSTOM_EMOJI = p("""<(a?):([A-Za-z0-9_~]{1,32}):(\d{15,21})>""")
        val TIMESTAMP = p("""<t:(-?\d{1,13})(?::([tTdDfFsSR]))?>""")
        val SLASH_COMMAND = p("""</([^\s:<>/][^:<>]{0,99}):(\d{15,21})>""")
        val GUILD_NAV = p("""<id:(customize|browse|guide|linked-roles)(?::\d+)?>""")
        val MASKED_LINK = p(
            """\[((?:[^\[\]\n]|\[[^\[\]\n]*\])+)\]\(\s*<?(https?://[^\s<>()]+(?:\([^\s<>()]*\)[^\s<>()]*)*)>?(?:\s+"[^"\n]*")?\s*\)""",
        )
        val URL = p("""https?://[^\s<]+""")
        val EVERYONE = p("""@(everyone|here)\b""")
        val SPOILER = p("""\|\|([\s\S]+?)\|\|""")
        val BOLD = p("""\*\*([\s\S]+?)\*\*(?!\*)""")
        val ITALIC_STAR = p("""\*(?=\S)((?:\*\*|\\[\s\S]|\s+(?:\\[\s\S]|[^\s*\\]|\*\*)|[^\s*\\])+?)\*(?!\*)""")
        val UNDERLINE = p("""__([\s\S]+?)__(?!_)""")
        val ITALIC_UNDERSCORE = p("""\b_((?:__|\\[\s\S]|[^\\_])+?)_\b""")
        val STRIKE = p("""~~([\s\S]+?)~~""")
    }
}
