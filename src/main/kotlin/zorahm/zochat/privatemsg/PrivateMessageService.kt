package zorahm.zochat.privatemsg

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import zorahm.zochat.badge.Badges
import zorahm.zochat.chat.BannedWordsFilter
import zorahm.zochat.chat.CooldownService
import zorahm.zochat.chat.PlaceholderService
import zorahm.zochat.config.ChatConfig
import zorahm.zochat.config.Messages
import zorahm.zochat.storage.OfflineMessageRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * /msg and /reply between players, and between a player and the console. The console takes part
 * under [CONSOLE_ID] so the reply map and the offline-message table need no special shape for it;
 * players see it as `console.prefix` + `console.name`.
 */
class PrivateMessageService(
    private val config: ChatConfig,
    private val messages: Messages,
    private val bannedWords: BannedWordsFilter,
    private val placeholders: PlaceholderService,
    private val offline: OfflineMessageRepository,
    private val cooldowns: CooldownService,
    private val badges: Badges
) {
    private val lastMessaged = HashMap<UUID, UUID>()
    private val mm = MiniMessage.miniMessage()
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    // Only the quitter's own entry goes: a partner's entry pointing at them is kept, so /r still
    // reaches them through offline messages instead of claiming there's no one to reply to.
    fun clearLastMessaged(player: UUID) {
        lastMessaged.remove(player)
    }

    fun send(sender: Player, targetName: String, rawMessage: String) {
        val message = checkPlayerText(sender, rawMessage) ?: return

        // A vanished player is treated as offline — "delivered" vs "offline" would out them.
        val target = Bukkit.getPlayerExact(targetName)?.takeIf { sender.canSee(it) }
        // getOfflinePlayerIfCached avoids the deprecated, main-thread-blocking name lookup;
        // null means the player has never been seen on this server.
        val targetId = target?.uniqueId ?: Bukkit.getOfflinePlayerIfCached(targetName)?.uniqueId
        if (targetId == null) {
            sender.sendMessage(
                mm.deserialize(messages.get("private-messages.player-not-found").replace("{player}", targetName))
            )
            return
        }
        if (targetId == sender.uniqueId) {
            sender.sendMessage(messages.component("private-messages.self"))
            return
        }

        // After the target checks, so a mistyped name doesn't burn the cooldown.
        if (isOnCooldown(sender)) return

        if (target == null) {
            // Remember the partner here too, so /r keeps the conversation going (as offline messages).
            if (saveOffline(sender, sender.uniqueId, targetId, targetName, message)) lastMessaged[sender.uniqueId] = targetId
            return
        }

        val body = playerBody(sender, message)
        target.sendMessage(incoming(sender.name, body, badges.forPlayers))
        sender.sendMessage(
            outgoing(target.name, body, badges.forPlayers)
                .clickEvent(ClickEvent.suggestCommand("/msg " + target.name + " "))
                .hoverEvent(HoverEvent.showText(messages.component("chat.message-resend-hover")))
        )
        lastMessaged[target.uniqueId] = sender.uniqueId
        lastMessaged[sender.uniqueId] = target.uniqueId
    }

    /** /msg typed in the console (or by a command block / RCON — they all speak as the console). */
    fun sendFromConsole(console: CommandSender, targetName: String, rawMessage: String) {
        // The console is trusted: no filter, no cooldown, and it may message vanished players. Its
        // text is still shown as plain text, the same as players' and as offline delivery shows it.
        val target = Bukkit.getPlayerExact(targetName)
        if (target == null) {
            val targetId = Bukkit.getOfflinePlayerIfCached(targetName)?.uniqueId
            if (targetId == null) {
                console.sendMessage(
                    mm.deserialize(messages.get("private-messages.player-not-found").replace("{player}", targetName))
                )
                return
            }
            if (saveOffline(console, CONSOLE_ID, targetId, targetName, rawMessage)) lastMessaged[CONSOLE_ID] = targetId
            return
        }

        val body = Component.text(rawMessage).hoverEvent(timestampHover(Instant.now()))
        target.sendMessage(incoming(consoleDisplay(), body, badges.forPlayers))
        console.sendMessage(outgoing(target.name, body, badges.forConsole))
        lastMessaged[target.uniqueId] = CONSOLE_ID
        lastMessaged[CONSOLE_ID] = target.uniqueId
    }

    fun reply(sender: Player, message: String) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messages.component("errors.no-permission"))
            return
        }
        val targetUUID = lastMessaged[sender.uniqueId]
        if (targetUUID == null) {
            sender.sendMessage(messages.component("reply.no-target"))
            return
        }
        if (targetUUID == CONSOLE_ID) {
            sendToConsole(sender, message)
            return
        }
        val name = partnerName(targetUUID)
        if (name == null) {
            sender.sendMessage(messages.component("reply.target-offline"))
            return
        }
        send(sender, name, message)
    }

    fun replyFromConsole(console: CommandSender, message: String) {
        val targetUUID = lastMessaged[CONSOLE_ID]
        if (targetUUID == null) {
            console.sendMessage(messages.component("reply.no-target"))
            return
        }
        val name = partnerName(targetUUID)
        if (name == null) {
            console.sendMessage(messages.component("reply.target-offline"))
            return
        }
        sendFromConsole(console, name, message)
    }

    /** Offline messages drained on join; the caller has already checked the player is online. */
    fun deliverOffline(player: Player, list: List<OfflineMessageRepository.OfflineMessage>) {
        if (list.isEmpty()) return
        player.sendMessage(
            mm.deserialize(messages.get("chat.offline-messages-header").replace("{count}", list.size.toString()))
        )
        for (om in list) {
            val senderName = if (om.sender == CONSOLE_ID) {
                consoleDisplay()
            } else {
                Bukkit.getOfflinePlayer(om.sender).name ?: "Unknown"
            }
            // Escape the stored sender text — it's the literal message and must not be re-parsed as
            // MiniMessage (would let tags injected into a PM render on delivery).
            val body = mm.deserialize(mm.escapeTags(om.message)).hoverEvent(timestampHover(Instant.ofEpochMilli(om.sentAtMillis)))
            player.sendMessage(incoming(senderName, body, badges.forPlayers))
        }
    }

    // A player's /r to the console: the console reads it in its log and can answer with /r.
    private fun sendToConsole(sender: Player, rawMessage: String) {
        val message = checkPlayerText(sender, rawMessage) ?: return
        if (isOnCooldown(sender)) return
        val body = playerBody(sender, message)
        Bukkit.getConsoleSender().sendMessage(incoming(sender.name, body, badges.forConsole))
        sender.sendMessage(outgoing(consoleDisplay(), body, badges.forPlayers))
        lastMessaged[CONSOLE_ID] = sender.uniqueId
        lastMessaged[sender.uniqueId] = CONSOLE_ID
    }

    /** Permission + banned-words filter; null (after telling the sender why) when it must not go out. */
    private fun checkPlayerText(sender: Player, rawMessage: String): String? {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messages.component("errors.no-permission"))
            return null
        }
        val filterResult = bannedWords.checkMessage(rawMessage)
        if (filterResult.isBlocked) {
            sender.sendMessage(messages.component("chat.banned-word"))
            return null
        }
        return filterResult.processedMessage
    }

    private fun isOnCooldown(sender: Player): Boolean {
        if (!config.isAntiSpamEnabled || sender.hasPermission(config.spamBypassPermission)) return false
        if (!cooldowns.isOnCooldown(sender.uniqueId, CooldownService.Channel.PRIVATE, config.privateMessageCooldown)) {
            return false
        }
        sender.sendMessage(messages.component("chat.spam-warning"))
        return true
    }

    /** False when offline messages are switched off (the sender has been told). */
    private fun saveOffline(from: CommandSender, fromId: UUID, toId: UUID, targetName: String, message: String): Boolean {
        if (!config.isOfflineMessagesEnabled) {
            from.sendMessage(messages.component("private-messages.offline-disabled"))
            return false
        }
        offline.save(fromId, toId, message)
        from.sendMessage(mm.deserialize(messages.get("private-messages.offline-sent").replace("{player}", targetName)))
        return true
    }

    private fun partnerName(id: UUID): String? {
        val online = Bukkit.getPlayer(id)
        // An offline partner gets the reply as an offline message; only refuse when that's off.
        if (online == null && !config.isOfflineMessagesEnabled) return null
        return online?.name ?: Bukkit.getOfflinePlayer(id).name
    }

    private fun playerBody(sender: Player, message: String): Component =
        placeholders.processPlaceholders(sender, message).hoverEvent(timestampHover(Instant.now()))

    private fun timestampHover(at: Instant) =
        HoverEvent.showText(mm.deserialize(messages.get("chat.message-timestamp").replace("{time}", time.format(at))))

    // {player} is spliced into the format string: player names use the safe account charset and the
    // console display is admin config (MiniMessage, may hold a <badge>), so neither injects anything.
    private fun line(formatKey: String, name: String, body: Component, tags: TagResolver): Component =
        mm.deserialize(messages.get(formatKey).replace("{player}", name), tags)
            .replaceText { it.matchLiteral("{message}").replacement(body) }

    private fun incoming(from: String, body: Component, tags: TagResolver): Component =
        line("formats.private-message", from, body, tags)
            .clickEvent(ClickEvent.suggestCommand("/r "))
            .hoverEvent(HoverEvent.showText(messages.component("chat.message-reply-hover")))

    private fun outgoing(to: String, body: Component, tags: TagResolver): Component =
        line("formats.private-message-reply", to, body, tags)

    private fun consoleDisplay(): String = config.consoleDisplay

    companion object {
        /** Stands in for the console in the reply map and as an offline message's sender. */
        @JvmField
        val CONSOLE_ID: UUID = UUID(0L, 0L)

        private const val PERMISSION = "zochat.msg"
    }
}
