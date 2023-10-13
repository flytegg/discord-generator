# discord-generator
Generate Discord messages on a HTML canvas, e.g. for ticket transcripts or message lookup results.

The following example is how you can use the library to generate HTML output:
```kt
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
```
This above produces this HTML document:
![image](https://github.com/flytegg/discord-generator/assets/43449531/d7755e6a-7f4f-4f78-b225-d9d6860ebc59)
