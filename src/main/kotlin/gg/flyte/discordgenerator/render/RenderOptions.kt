package gg.flyte.discordgenerator.render

import gg.flyte.discordgenerator.media.MediaResolver
import java.time.ZoneId
import java.util.Locale

data class RenderOptions @JvmOverloads constructor(
    /**
     * Time zone for the timestamps written into the HTML and for the day dividers. Timestamps are re-formatted in
     * the viewer's own time zone and locale by the page's script, so this mostly matters with scripts disabled.
     */
    val zoneId: ZoneId = ZoneId.systemDefault(),
    val locale: Locale = Locale.UK,
    /** Show the newest message at the top instead of the bottom. */
    val newestFirst: Boolean = false,
    /** Merge consecutive messages from the same author (within 7 minutes) like the Discord client does. */
    val groupMessages: Boolean = true,
    /** Decides which URL is written for every image, video and file. See [MediaResolver]. */
    val mediaResolver: MediaResolver = MediaResolver.HOTLINK,
    /**
     * Link to a hosted copy of [gg.flyte.discordgenerator.DiscordGenerator.stylesheet] instead of inlining it.
     * Saves roughly 10 KB per transcript, which adds up over thousands of tickets.
     */
    val stylesheetUrl: String? = null,
    /** Same as [stylesheetUrl], for [gg.flyte.discordgenerator.DiscordGenerator.script]. */
    val scriptUrl: String? = null,
    val showFooter: Boolean = true,
)
