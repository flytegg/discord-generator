package gg.flyte.discordgenerator

import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val output = DiscordGenerator {
        title = "Export for joshbker"
        date = "Generated at you can put a date here if you want"

        addMessages(
            Component.Message(
                author = Component.Author(
                    "https://cdn.discordapp.com/avatars/259780560707256321/1aa50313a2f1ae6e9495d8368c653ccf.webp",
                    "josh",
                    false
                ),
                timestamp = 1696948904000L,
                content = "testing yessir yes <:mention@josh> epic https://github.com/flytegg/ls-discord-bot",
                embeds = listOf(
                    Component.Embed("Josh - Support Ticket #8", "Some description"),
                ),
                images = listOf(
                    Component.Image("https://media.tenor.com/BHTQmBYipVEAAAAC/anyon-birthday.gif"),
                ),
                reactions = listOf(
                    Component.Reaction(
                        "https://cdn.discordapp.com/emojis/614661097978462209.webp?size=32&quality=lossless",
                        1
                    ),
                )
            ),
            Component.Message(
                author = Component.Author(
                    "https://cdn.discordapp.com/avatars/259780560707256321/1aa50313a2f1ae6e9495d8368c653ccf.webp",
                    "josh",
                    false
                ),
                timestamp = 1696948904000L,
                content = "testing yessir yes epic",
                embeds = listOf(
                    Component.Embed("Josh - Support Ticket #8", "Some description"),
                ),
                images = listOf(
                    Component.Image("https://media.tenor.com/BHTQmBYipVEAAAAC/anyon-birthday.gif"),
                ),
                reactions = listOf(
                    Component.Reaction(
                        "https://cdn.discordapp.com/emojis/614661097978462209.webp?size=32&quality=lossless",
                        1
                    ),
                )
            )
        )
    }.generate()
    //println(output)
    Files.writeString(Path.of("output.html"), output)
}