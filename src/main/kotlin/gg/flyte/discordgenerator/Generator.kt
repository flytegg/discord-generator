package gg.flyte.discordgenerator

import java.util.*

class DiscordGenerator(
    var title: String = "No title"
) {
    constructor(init: DiscordGenerator.() -> Unit) : this() {
        apply(init)
    }

    private val htmlContent = StringBuilder()

    private val messages = mutableListOf<Component.Message>()

    fun addMessages(vararg messages: Component.Message) {
        this.messages.addAll(messages)
    }

    private fun setupDocument() {
        htmlContent.append(Component.Document(title, Date()).asHtml())
    }

    private fun compileMessages() {
        messages.forEach { htmlContent.append(it.asHtml()) }
    }

    fun generate(): String {
        setupDocument()
        compileMessages()
        return htmlContent.append(Component.END).toString()
    }
}
