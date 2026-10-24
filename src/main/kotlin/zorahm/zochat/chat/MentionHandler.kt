package zorahm.zochat.chat

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import zorahm.zochat.config.ChatConfig
import zorahm.zochat.config.Messages
import zorahm.zochat.util.Sounds
import java.util.regex.Matcher
import java.util.regex.Pattern

class MentionHandler(
    private val plugin: Plugin,
    private val chatConfig: ChatConfig,
    private val messages: Messages
) {

    class MentionResult(
        val processedMessage: String,
        val mentionedPlayers: List<Player>,
        val hasEveryoneMention: Boolean,
        val hasHereMention: Boolean
    )

    enum class MentionType {
        NORMAL,
        EVERYONE,
        HERE
    }

    fun processMentions(message: String, sender: Player): MentionResult {
        var processedMessage = message
        val mentionedPlayers = HashSet<Player>()
        var hasEveryoneMention = false
        var hasHereMention = false

        val everyoneMatcher = EVERYONE_PATTERN.matcher(message)
        if (everyoneMatcher.find()) {
            if (sender.hasPermission(chatConfig.mentionEveryonePermission)) {
                hasEveryoneMention = true
                // quoteReplacement: a '$' in the configured format would otherwise be read as a group
                // reference and throw, dropping the whole message.
                processedMessage = everyoneMatcher.replaceAll(Matcher.quoteReplacement(chatConfig.mentionEveryoneFormat))
                mentionedPlayers.addAll(Bukkit.getOnlinePlayers().filter { it != sender })
            } else if (chatConfig.isDebugModeEnabled) {
                plugin.logger.info("${sender.name} tried to use @everyone without permission")
            }
        }

        val hereMatcher = HERE_PATTERN.matcher(processedMessage)
        if (hereMatcher.find()) {
            if (sender.hasPermission(chatConfig.mentionHerePermission)) {
                hasHereMention = true
                processedMessage = hereMatcher.replaceAll(Matcher.quoteReplacement(chatConfig.mentionHereFormat))

                val radius = chatConfig.mentionHereRadius.toDouble()
                val senderLoc = sender.location
                mentionedPlayers.addAll(
                    Bukkit.getOnlinePlayers()
                        .filter { it != sender }
                        .filter { it.world == sender.world }
                        .filter { it.location.distanceSquared(senderLoc) <= radius * radius }
                )
            } else if (chatConfig.isDebugModeEnabled) {
                plugin.logger.info("${sender.name} tried to use @here without permission")
            }
        }

        if (!hasEveryoneMention && mentionedPlayers.size < MAX_MENTIONS_PER_MESSAGE) {
            val matcher = MENTION_PATTERN.matcher(processedMessage)
            val sb = StringBuffer()

            while (matcher.find() && mentionedPlayers.size < MAX_MENTIONS_PER_MESSAGE) {
                val mentionedName = matcher.group(1) ?: continue

                if (mentionedName.equals("everyone", ignoreCase = true) ||
                    mentionedName.equals("все", ignoreCase = true) ||
                    mentionedName.equals("all", ignoreCase = true) ||
                    mentionedName.equals("here", ignoreCase = true) ||
                    mentionedName.equals("здесь", ignoreCase = true)
                ) {
                    continue
                }

                val mentionedPlayer = findPlayer(mentionedName, sender)
                if (mentionedPlayer != null && mentionedPlayer != sender && !mentionedPlayers.contains(mentionedPlayer)) {
                    val mentionFormat = chatConfig.mentionFormat.replace("{player}", mentionedPlayer.name)
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(mentionFormat))
                    mentionedPlayers.add(mentionedPlayer)
                } else {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)))
                }
            }
            matcher.appendTail(sb)
            processedMessage = sb.toString()
        }

        return MentionResult(processedMessage, ArrayList(mentionedPlayers), hasEveryoneMention, hasHereMention)
    }

    // canSee: a vanished player must not be found (and pinged) by name — that would out them.
    private fun findPlayer(name: String, sender: Player): Player? {
        val exact = Bukkit.getPlayerExact(name)
        if (exact != null && sender.canSee(exact)) {
            return exact
        }

        val matches = Bukkit.getOnlinePlayers()
            .filter { sender.canSee(it) && it.name.lowercase().startsWith(name.lowercase()) }
        return if (matches.size == 1) matches[0] else null
    }

    fun notifyMentionedPlayers(mentionedPlayers: List<Player>, type: MentionType) {
        if (mentionedPlayers.isEmpty()) return
        val actionBar = messages.component(getMentionMessageKey(type))
        val sound = Sounds.parse(chatConfig.mentionSound)

        for (mentionedPlayer in mentionedPlayers) {
            sound.ifPresent { s ->
                mentionedPlayer.playSound(mentionedPlayer.location, s, 1.0f, 1.0f)
            }
            mentionedPlayer.sendActionBar(actionBar)
        }
    }

    private fun getMentionMessageKey(type: MentionType): String = when (type) {
        MentionType.EVERYONE -> "mention.notify-everyone"
        MentionType.HERE -> "mention.notify-here"
        MentionType.NORMAL -> "mention.notify"
    }

    fun getPlayerSuggestions(partial: String, viewer: CommandSender): List<String> {
        val lower = partial.lowercase()
        val suggestions = ArrayList<String>()

        if (viewer.hasPermission(chatConfig.mentionEveryonePermission) &&
            ("everyone".startsWith(lower) || "все".startsWith(lower))
        ) {
            suggestions.add("everyone")
        }
        if (viewer.hasPermission(chatConfig.mentionHerePermission) &&
            ("here".startsWith(lower) || "здесь".startsWith(lower))
        ) {
            suggestions.add("here")
        }

        suggestions.addAll(
            Bukkit.getOnlinePlayers()
                .filter { viewer !is Player || viewer.canSee(it) }
                .map { it.name }
                .filter { it.lowercase().startsWith(lower) }
                .sorted()
                .take(10)
        )

        return suggestions
    }

    companion object {
        // UNICODE_CHARACTER_CLASS so \w matches non-ASCII names too (e.g. Cyrillic @Ник), not just [a-zA-Z0-9_].
        private val MENTION_PATTERN: Pattern = Pattern.compile("@(\\w+)", Pattern.UNICODE_CHARACTER_CLASS)

        // Whole word only — without the lookahead "@Allan" read as "@all" + "an" and pinged the
        // whole server. UNICODE_CASE because CASE_INSENSITIVE alone folds ASCII only ("@ВСЕ").
        @JvmField
        val EVERYONE_PATTERN: Pattern =
            Pattern.compile("@(everyone|все|all)(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)

        @JvmField
        val HERE_PATTERN: Pattern =
            Pattern.compile("@(here|здесь)(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)

        private const val MAX_MENTIONS_PER_MESSAGE = 5
    }
}
