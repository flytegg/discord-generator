package gg.flyte.discordgenerator

import java.text.SimpleDateFormat
import java.util.*

class Component {
    companion object {
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy")
        val datetimeFormatter = SimpleDateFormat("HH:mm")
        const val END = "</div></div></body></html>"
    }

    class Document(
        private val title: String,
        private val date: String
    ) : HtmlElement {
        override fun asHtml(): String {
            return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>$title</title>
                    <script src="https://cdn.tailwindcss.com"></script>
                </head>
                <style>
                    @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&display=swap');
                
                    body {
                        font-family: Inter, sans-serif;
                    }
                </style>
                <body class="bg-[#313338] min-h-screen space-y-6">
                    <div class="bg-[#2b2d31] py-2 xl:py-6 px-2 xl:px-24 flex justify-between items-center">
                        <h1 class="text-[#f2f3f5] text-lg xl:text-xl font-semibold">$title</h1>
                        <h1 class="text-[#949ba4] text-base xl:text-lg font-medium text-right">$date</h1>
                    </div>
                    <div class="flex flex-col space-y-6 px-2 xl:px-24 pb-6">
            """.trimIndent()
        }
    }

    class Author(
        val imageUrl: String,
        val name: String,
        val isBot: Boolean
    )

    class Embed(
        private val title: String?,
        private val description: String?
    ) : HtmlElement {
        override fun asHtml(): String {
            return """
                <div class="bg-[#2b2d31] rounded-lg py-3 px-5 space-y-1 mt-1 self-start">
                    ${if (title != null) "<p class=\"text-[#f2f3f5] text-base font-semibold\">$title</p>" else ""}
                    ${if (description != null) "<p class=\"text-[#dbdee1]\">$description</p>" else ""}
                </div>
            """.trimIndent()
        }
    }

    class Image(
        private val imageUrl: String
    ) : HtmlElement {
        override fun asHtml(): String {
            return "<img class=\"rounded-lg h-72 mt-2 self-start\" src=\"$imageUrl\">"
        }
    }

    class Reaction(
        private val imageUrl: String,
        private val amount: Int
    ) : HtmlElement {
        override fun asHtml(): String {
            return """
                <div class="bg-[#2b2d31] flex self-start items-center space-x-1.5 rounded-lg py-1 px-2 mt-1">
                    <img src="$imageUrl" class="w-4 h-4">
                    <p class="text-[#b5bac1] font-semibold text-sm">$amount</p>
                </div>
            """.trimIndent()
        }
    }

    class Message(
        private val author: Author,
        private val timestamp: Long,
        private val content: String? = null,
        private val embeds: List<Embed> = emptyList(),
        private val images: List<Image> = emptyList(),
        private val reactions: List<Reaction> = emptyList()
    ) : HtmlElement {
        override fun asHtml(): String {
            return """
                <div class="flex space-x-4">
                    <img src="${author.imageUrl}" class="rounded-full w-12 h-12">
                    <div class="flex flex-col">
                        <div class="flex space-x-2 items-center">
                            ${
                if (author.isBot) """
                                <div class="flex space-x-1.5 items-center">
                                    <h3 class="text-[#f2f3f5] font-medium">${author.name}</h3>
                                    <p class="bg-[#5865f2] text-xs text-[#f2f3f5] py-0.5 px-1.5 rounded font-medium">BOT</p>
                                </div>
                            """.trimIndent()
                else "<h3 class=\"text-[#f2f3f5] font-medium\">${author.name}</h3>"
            }
                            <p class="text-[#949ba4] text-xs">${timestamp.epochMillisecondsAsFormattedString()}</p>
                        </div>
                        ${if (content != null) "<p class=\"text-[#dbdee1]\">${processContent(content)}</p>" else ""}
                        ${if (embeds.isNotEmpty()) embeds.asHtml() else ""}
                        ${if (images.isNotEmpty()) images.asHtml() else ""}
                        ${if (reactions.isNotEmpty()) reactions.asHtml() else ""}
                    </div>
                </div>
            """.trimIndent()
        }
    }

    interface HtmlElement {
        fun asHtml(): String
    }
}

private val urlRegex = """\b(https?|ftp)://[^\s/$.?#].[^\s]*\b""".toRegex()
private val mentionRegex = """<:mention@([a-zA-Z0-9]+)>""".toRegex()

fun processContent(content: String): String {
    var processedContent = content

    urlRegex.findAll(content).forEach {
        processedContent = processedContent.replace(it.value, "<a class=\"text-[#00A8FC] hover:underline\" href=\"${it.value}\">${it.value}</a>")
    }

    mentionRegex.findAll(content).forEach {
        processedContent = processedContent.replace(it.value, "<span class=\"text-[#C9CDFB] bg-[#3C4270] font-medium p-[2px] rounded\">@${it.groupValues[1]}</span>")
    }

    return processedContent.replace("\n", "<br>")
}

@JvmName("embedsAsHtml")
fun List<Component.Embed>.asHtml(): String {
    return """
            <div class="flex flex-col space-y-2">
                ${joinToString("") { it.asHtml() }}
            </div>
        """.trimIndent()
}

@JvmName("imagesAsHtml")
fun List<Component.Image>.asHtml(): String {
    return joinToString("") { it.asHtml() }
}

@JvmName("reactionsAsHtml")
fun List<Component.Reaction>.asHtml(): String {
    return """
            <div class="flex space-x-1">
                ${joinToString("") { it.asHtml() }}
            </div>
        """.trimIndent()
}

fun Long.epochMillisecondsAsFormattedString(): String {
    Date(this).apply {
        return "${Component.dateFormatter.format(this)} ${Component.datetimeFormatter.format(this)}"
    }
}
