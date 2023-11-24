package gg.flyte.discordgenerator

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class DiscordGenerator(
    var title: String = "No title",
    var date: String = "Generated ${Component.dateFormatter.format(Date())} at ${Component.datetimeFormatter.format(Date())}",
    var reversed: Boolean = false
) {
    constructor(init: DiscordGenerator.() -> Unit) : this() {
        apply(init)
    }

    private val htmlContent = StringBuilder()

    private val messages = mutableListOf<Component.Message>()

    fun addMessages(vararg messages: Component.Message) = this.messages.addAll(messages)

    fun addMessages(messages: List<Component.Message>) = this.messages.addAll(messages)

    private fun setupDocument() = htmlContent.append(Component.Document(title, date).asHtml())

    private fun compileMessages() =
        (if (reversed) messages.reversed() else messages).forEach { htmlContent.append(it.asHtml()) }

    fun generate(): String {
        setupDocument()
        compileMessages()
        return htmlContent.append(Component.END).toString()
    }
}
