package gg.flyte.discordgenerator

import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val output = DiscordGenerator {
        title = "Export for joshbker"

        addMessages(
            Component.Message(
                author = Component.Author(
                    "https://cdn.discordapp.com/guilds/1055283692668469341/users/259780560707256321/avatars/df7191cbe92b582ff1abc271aea76e8a.webp?size=128",
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
                    "https://cdn.discordapp.com/guilds/1055283692668469341/users/259780560707256321/avatars/df7191cbe92b582ff1abc271aea76e8a.webp?size=128",
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