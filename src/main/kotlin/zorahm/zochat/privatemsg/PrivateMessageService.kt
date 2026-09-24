package zorahm.zochat.privatemsg

import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import zorahm.zochat.chat.BannedWordsFilter
import zorahm.zochat.chat.CooldownService
import zorahm.zochat.chat.PlaceholderService
import zorahm.zochat.config.ChatConfig
import zorahm.zochat.config.Messages
import zorahm.zochat.storage.OfflineMessageRepository
import zorahm.zochat.util.PlayerText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class PrivateMessageService(
    private val plugin: Plugin,
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

    fun clearLastMessaged(player: UUID) {
        lastMessaged.remove(player)
        lastMessaged.entries.removeIf { it.value == player }
    }

    fun send(sender: Player, targetName: String, rawMessage: String) {
        val filterResult = bannedWords.checkMessage(rawMessage)
        if (filterResult.isBlocked) {
            sender.sendMessage(mm.deserialize(messages.get("chat.banned-word")))
            return
        }
        val message = filterResult.processedMessage

        // Resolve the target BEFORE the cooldown: a typo'd name used to burn the cooldown too.
        val target = Bukkit.getPlayerExact(targetName)
        // getOfflinePlayerIfCached avoids the deprecated, main-thread-blocking name lookup;
        // null means the player has never been seen on this server.
        val offlineTarget = if (target == null) Bukkit.getOfflinePlayerIfCached(targetName) else null
        if (target == null && offlineTarget == null) {
            sender.sendMessage(
                mm.deserialize(
                    messages.get("private-messages.player-not-found").replace("{player}", PlayerText.escape(targetName))
                )
            )
            return
        }
        if ((target?.uniqueId ?: offlineTarget?.uniqueId) == sender.uniqueId) {
            sender.sendMessage(mm.deserialize(messages.get("private-messages.self")))
            return
        }

        if (config.isAntiSpamEnabled && !sender.hasPermission(config.spamBypassPermission)) {
            if (cooldowns.isOnCooldown(sender.uniqueId, CooldownService.Channel.PRIVATE, config.privateMessageCooldown)) {
                sender.sendMessage(mm.deserialize(messages.get("chat.spam-warning")))
                return
            }
        }

        if (target == null) {
            sendOffline(sender, offlineTarget!!.uniqueId, targetName, message)
            return
        }

        val timestamp = time.format(Instant.ofEpochMilli(System.currentTimeMillis()))
        val messageComponent = placeholders.processPlaceholders(sender, message)
            .hoverEvent(
                HoverEvent.showText(
                    mm.deserialize(messages.get("chat.message-timestamp").replace("{time}", timestamp))
                )
            )

        val incoming = mm.deserialize(config.privateMessageFormat.replace("{player}", sender.name))
            .replaceText { it.matchLiteral("{message}").replacement(messageComponent) }
            .clickEvent(ClickEvent.suggestCommand("/r "))
            .hoverEvent(HoverEvent.showText(mm.deserialize(messages.get("chat.message-reply-hover"))))
        val outgoing = mm.deserialize(config.privateMessageReplyFormat.replace("{player}", target.name))
            .replaceText { it.matchLiteral("{message}").replacement(messageComponent) }
            .clickEvent(ClickEvent.suggestCommand("/msg " + target.name + " "))
            .hoverEvent(HoverEvent.showText(mm.deserialize(messages.get("chat.message-resend-hover"))))

        target.sendMessage(incoming)
        sender.sendMessage(outgoing)
        lastMessaged[target.uniqueId] = sender.uniqueId
        lastMessaged[sender.uniqueId] = target.uniqueId
    }

    private fun sendOffline(sender: Player, receiver: UUID, targetName: String, message: String) {
        if (!config.isOfflineMessagesEnabled) {
            sender.sendMessage(mm.deserialize(messages.get("private-messages.offline-disabled")))
            return
        }
        offline.save(sender.uniqueId, receiver, message, config.offlineMessagesMaxPerPlayer) { result ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val key = when (result) {
                    OfflineMessageRepository.SaveResult.SAVED -> "private-messages.offline-sent"
                    OfflineMessageRepository.SaveResult.INBOX_FULL -> "private-messages.offline-full"
                    else -> "private-messages.offline-failed"
                }
                sender.sendMessage(mm.deserialize(messages.get(key).replace("{player}", targetName)))
            })
        }
    }

    fun reply(sender: Player, message: String) {
        val targetUUID = lastMessaged[sender.uniqueId]
        if (targetUUID == null) {
            sender.sendMessage(mm.deserialize(messages.get("reply.no-target")))
            return
        }
        val target = Bukkit.getPlayer(targetUUID)
        if (target == null || !target.isOnline) {
            sender.sendMessage(mm.deserialize(messages.get("reply.target-offline")))
            return
        }
        send(sender, target.name, message)
    }
}
