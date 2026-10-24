package zorahm.zochat.privatemsg

import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
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

class PrivateMessageService(
    private val config: ChatConfig,
    private val messages: Messages,
    private val bannedWords: BannedWordsFilter,
    private val placeholders: PlaceholderService,
    private val offline: OfflineMessageRepository,
    private val cooldowns: CooldownService
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
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messages.component("errors.no-permission"))
            return
        }
        val filterResult = bannedWords.checkMessage(rawMessage)
        if (filterResult.isBlocked) {
            sender.sendMessage(mm.deserialize(messages.get("chat.banned-word")))
            return
        }
        val message = filterResult.processedMessage

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
        if (config.isAntiSpamEnabled && !sender.hasPermission(config.spamBypassPermission)) {
            if (cooldowns.isOnCooldown(sender.uniqueId, CooldownService.Channel.PRIVATE, config.privateMessageCooldown)) {
                sender.sendMessage(mm.deserialize(messages.get("chat.spam-warning")))
                return
            }
        }

        if (target == null) {
            if (config.isOfflineMessagesEnabled) {
                offline.save(sender.uniqueId, targetId, message)
                sender.sendMessage(
                    mm.deserialize(messages.get("private-messages.offline-sent").replace("{player}", targetName))
                )
            } else {
                sender.sendMessage(mm.deserialize(messages.get("private-messages.offline-disabled")))
            }
            return
        }

        val timestamp = time.format(Instant.now())
        val messageComponent = placeholders.processPlaceholders(sender, message)
            .hoverEvent(
                HoverEvent.showText(
                    mm.deserialize(messages.get("chat.message-timestamp").replace("{time}", timestamp))
                )
            )

        val incoming = mm.deserialize(messages.get("formats.private-message").replace("{player}", sender.name))
            .replaceText { it.matchLiteral("{message}").replacement(messageComponent) }
            .clickEvent(ClickEvent.suggestCommand("/r "))
            .hoverEvent(HoverEvent.showText(mm.deserialize(messages.get("chat.message-reply-hover"))))
        val outgoing = mm.deserialize(messages.get("formats.private-message-reply").replace("{player}", target.name))
            .replaceText { it.matchLiteral("{message}").replacement(messageComponent) }
            .clickEvent(ClickEvent.suggestCommand("/msg " + target.name + " "))
            .hoverEvent(HoverEvent.showText(mm.deserialize(messages.get("chat.message-resend-hover"))))

        target.sendMessage(incoming)
        sender.sendMessage(outgoing)
        lastMessaged[target.uniqueId] = sender.uniqueId
        lastMessaged[sender.uniqueId] = target.uniqueId
    }

    fun reply(sender: Player, message: String) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messages.component("errors.no-permission"))
            return
        }
        val targetUUID = lastMessaged[sender.uniqueId]
        if (targetUUID == null) {
            sender.sendMessage(mm.deserialize(messages.get("reply.no-target")))
            return
        }
        val online = Bukkit.getPlayer(targetUUID)
        val name = online?.name ?: Bukkit.getOfflinePlayer(targetUUID).name
        // An offline partner gets the reply as an offline message via send(); only refuse when
        // that path is switched off.
        if (name == null || (online == null && !config.isOfflineMessagesEnabled)) {
            sender.sendMessage(mm.deserialize(messages.get("reply.target-offline")))
            return
        }
        send(sender, name, message)
    }

    private companion object {
        const val PERMISSION = "zochat.msg"
    }
}
