package gg.flyte.discordgenerator

import java.util.*

class DiscordGenerator(
    var title: String = "Export",
    var username: String = "%username%",
    var displayName: String = "%displayName%",
    var date: Date = Date(),
) {
    constructor(init: DiscordGenerator.() -> Unit) : this() {
        apply(init)
    }

    private val htmlContent = StringBuilder(Component.START)

    private val messages = mutableListOf<Component.Message>()

    fun addMessages(vararg messages: Component.Message) {
        this.messages.addAll(messages)
    }

    private fun compileMessages() {
        messages.forEach {
            htmlContent.append(it.asHtml())
        }
    }

    private fun String.replacePlaceholders(): String {
        return this
            .replace("%title%", title)
            .replace("%username%", username)
            .replace("%displayName%", displayName)
            .replace("%date%", Component.dateFormatter.format(date))
            .replace(
                "%datetime%",
                "${Component.dateFormatter.format(date)} at ${Component.datetimeFormatter.format(date)}"
            )
    }

    fun generate(): String {
        compileMessages()
        return htmlContent.append(Component.END).toString().replacePlaceholders()
    }
}
