package gg.flyte.discordgenerator

import gg.flyte.discordgenerator.model.Transcript
import gg.flyte.discordgenerator.render.HtmlRenderer
import gg.flyte.discordgenerator.render.RenderOptions

/**
 * Renders [Transcript]s to a single, self-contained HTML page that looks like the Discord client.
 *
 * ```kotlin
 * val html = DiscordGenerator.render(transcript)
 * ```
 */
object DiscordGenerator {

    @JvmStatic
    @JvmOverloads
    fun render(transcript: Transcript, options: RenderOptions = RenderOptions()): String =
        HtmlRenderer(transcript, options).render()

    /** The page CSS, for hosting once and passing as [RenderOptions.stylesheetUrl]. */
    @JvmStatic
    fun stylesheet(): String = STYLESHEET

    /** The page script, for hosting once and passing as [RenderOptions.scriptUrl]. */
    @JvmStatic
    fun script(): String = SCRIPT

    private val STYLESHEET: String by lazy {
        resource("transcript.css")
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""\s*\n\s*"""), "")
            .replace(Regex("""\s*([{}:;,>])\s*"""), "$1")
            .replace(";}", "}")
    }

    private val SCRIPT: String by lazy {
        resource("transcript.js").lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") }
            .joinToString("\n")
    }

    private fun resource(name: String): String =
        DiscordGenerator::class.java.getResourceAsStream("/gg/flyte/discordgenerator/$name")
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Missing resource $name")
}
