# discord-generator

Renders Discord conversations to a single HTML page that looks like the Discord client, e.g. for ticket transcripts.

This is the platform-independent core. If you use JDA, depend on
[`discordgenerator-jda`](https://github.com/flytegg/jda-discord-generator) instead, which converts JDA messages for you.

## Install

```kotlin
repositories {
    maven("https://repo.flyte.gg/releases")
}

dependencies {
    implementation("gg.flyte:discordgenerator:2.0.0")
}
```

## Usage

```kotlin
val html = DiscordGenerator.render(
    Transcript(
        title = "Ticket #4821",
        messages = listOf(
            Message("1", Author("123", "Steve"), Instant.now(), content = "**Hello** <@456>!"),
        ),
        mentions = MentionIndex(users = mapOf("456" to MentionedUser("Dan"))),
    ),
)
```

## What gets rendered

- Discord markdown: headings, `-#` subtext, lists, quotes, code blocks, bold/italic/underline/strike, spoilers,
  masked links, mentions, custom emoji, `<t:…>` timestamps, slash-command mentions
- Embeds (author, fields, thumbnail, image, footer, colour), image/GIF/video link embeds
- Components V2 (Container, Section, TextDisplay, Thumbnail, MediaGallery, File, Separator) and buttons/select menus
- Attachments (image galleries, video, audio, files), stickers, reactions, polls, replies, forwarded messages,
  slash-command headers, system messages (joins, pins, boosts, threads…)
- Message grouping, day dividers, timestamps shown in the viewer's own time zone

All user text is HTML-escaped and links are limited to `http(s)`.

## Expiring attachments

Discord attachment links are signed and stop working about 24 hours after they are created. Every signed link gets
its expiry (from the `ex` query parameter) written into the page. Once it has passed, or the file fails to load for
any other reason, the page swaps in an "Image expired" placeholder showing the filename and size, and shows a banner
explaining why. Avatars, emoji, stickers and external embed media don't expire and keep loading.

To keep attachments permanently, pass a `MediaResolver` that copies the file somewhere you control and returns the
new URL:

```kotlin
val options = RenderOptions(mediaResolver = { request ->
    when (request.kind) {
        MediaKind.ATTACHMENT -> myStorage.copy(request.url, request.filename) // return your URL
        else -> MediaResolver.HOTLINK.resolve(request)
    }
})
```

## Saving space across many transcripts

Each page inlines about 18 KB of CSS and JS. When you store lots of transcripts, host
`DiscordGenerator.stylesheet()` and `DiscordGenerator.script()` once and pass their URLs as
`RenderOptions(stylesheetUrl = …, scriptUrl = …)`.
