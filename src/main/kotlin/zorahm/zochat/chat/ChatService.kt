package zorahm.zochat.chat

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.luckperms.api.LuckPerms
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import zorahm.zochat.badge.Badges
import zorahm.zochat.bubble.BubbleService
import zorahm.zochat.config.ChatConfig
import zorahm.zochat.config.Messages
import zorahm.zochat.discord.DiscordBridge
import zorahm.zochat.storage.ChatLogRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ChatService(
    private val config: ChatConfig,
    private val messages: Messages,
    private val luckPerms: LuckPerms,
    private val bannedWords: BannedWordsFilter,
    private val mentions: MentionHandler,
    private val placeholders: PlaceholderService,
    private val papi: PapiHook,
    private val chatLog: ChatLogRepository,
    private val cooldowns: CooldownService,
    private val bubble: BubbleService,
    private val badges: Badges,
    private val discord: DiscordBridge
) {

    private val mm: MiniMessage = MiniMessage.miniMessage()
    private val plainText: PlainTextComponentSerializer = PlainTextComponentSerializer.plainText()
    private val time: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    fun send(sender: Player, rawMessage: String, channel: ChatChannel) {
        val playerId = sender.uniqueId

        if (!sender.hasPermission(channel.permission)) {
            sender.sendMessage(messages.component("chat.no-permission"))
            return
        }

        // Filter before the cooldown so a message rejected for a banned word doesn't burn the cooldown.
        val filterResult = bannedWords.checkMessage(rawMessage)
        if (filterResult.isBlocked) {
            sender.sendMessage(mm.deserialize(messages.get("chat.banned-word")))
            return
        }
        val message = filterResult.processedMessage

        if (config.isAntiSpamEnabled && !sender.hasPermission(config.spamBypassPermission)) {
            val cdChannel = if (channel == ChatChannel.GLOBAL) {
                CooldownService.Channel.GLOBAL
            } else {
                CooldownService.Channel.LOCAL
            }
            val cooldown = if (channel == ChatChannel.GLOBAL) {
                config.globalChatCooldown
            } else {
                config.localChatCooldown
            }
            if (cooldowns.isOnCooldown(playerId, cdChannel, cooldown)) {
                sender.sendMessage(mm.deserialize(messages.get("chat.spam-warning")))
                return
            }
        }

        // Escape once, then stay at the string level until the final deserialize. Mentions run
        // BEFORE any placeholder expansion so an '@' inside an expanded value (a PAPI value, an
        // anvil-renamed item in ^item) can never trigger a mention; it also drops the old fragile
        // Component -> serialize -> regex -> deserialize round-trip.
        val escaped = mm.escapeTags(message)
        val mentionResult = mentions.processMentions(escaped, sender)
        var body = mentionResult.processedMessage
        // Players typing %...% is opt-in and permission-gated (abuse risk). The DB log below keeps
        // `message`, the player's literal text, not the resolved values.
        if (config.isPlaceholderApiEnabled && config.isPlaceholderApiPlayerMessagesEnabled &&
            sender.hasPermission(config.placeholderApiPlayerPermission)
        ) {
            body = papi.applyToPlayerText(sender, body)
        }
        val processedMessageComponent = placeholders.processEscaped(sender, body)

        // null = everyone online. Mentions only ping players who will actually receive the message
        // — a local "@everyone" used to ping the whole server while only neighbours saw it.
        val nearby = if (channel == ChatChannel.LOCAL) localRecipients(sender) else null
        val mentionType = when {
            mentionResult.hasEveryoneMention -> MentionHandler.MentionType.EVERYONE
            mentionResult.hasHereMention -> MentionHandler.MentionType.HERE
            else -> MentionHandler.MentionType.NORMAL
        }
        val pinged = if (nearby == null) {
            mentionResult.mentionedPlayers
        } else {
            mentionResult.mentionedPlayers.filter { it in nearby }
        }
        mentions.notifyMentionedPlayers(pinged, mentionType)

        chatLog.log(sender.uniqueId, message)

        val user = luckPerms.userManager.getUser(playerId)
        val prefix = user?.cachedData?.metaData?.prefix ?: ""
        val suffix = user?.cachedData?.metaData?.suffix ?: ""

        val format = messages.get(if (channel == ChatChannel.LOCAL) "formats.local-chat" else "formats.global-chat")

        val timestamp = time.format(Instant.now())

        var inlined = inlineMeta(format, prefix, suffix)

        // Expand %...% in the format (e.g. %vault_prefix%, %player_world%). The {player}/{message}
        // literals contain no '%' so they survive to the Component replacements below untouched.
        if (config.isPlaceholderApiEnabled && config.isPlaceholderApiFormatEnabled) {
            inlined = papi.apply(sender, inlined)
        }

        val playerNameComponent = Component.text(sender.name)
            .clickEvent(ClickEvent.suggestCommand("/msg " + sender.name + " "))
            .hoverEvent(
                HoverEvent.showText(
                    mm.deserialize(messages.get("chat.message-player").replace("{player}", sender.name))
                )
            )
        val messageComponent = processedMessageComponent
            .hoverEvent(
                HoverEvent.showText(
                    mm.deserialize(messages.get("chat.message-timestamp").replace("{time}", timestamp))
                )
            )

        // Built twice and sent separately instead of Bukkit.broadcast(): a <badge:...> in a prefix is
        // a resource pack glyph for players but must be a text label in the console, which has no pack.
        // Sending to the console explicitly also puts local chat in the console and server log.
        val toPlayers = assemble(inlined, playerNameComponent, messageComponent, badges.forPlayers)
        (nearby ?: Bukkit.getOnlinePlayers()).forEach { it.sendMessage(toPlayers) }
        Bukkit.getConsoleSender().sendMessage(assemble(inlined, playerNameComponent, messageComponent, badges.forConsole))

        // The text as players just saw it — banned words censored, ^placeholders expanded — not the raw input.
        discord.relay(sender, channel, plainText.serialize(processedMessageComponent))

        // Bubble is shown in addition to chat; it gates itself on enabled + CHAT trigger. Pass the
        // filtered message so the bubble censors banned words just like chat does.
        bubble.onChat(sender, message)
    }

    /** /bubble text: it hangs over the player's head for everyone around, so it gets chat's filter and anti-spam. */
    fun sendBubble(sender: Player, rawMessage: String) {
        val filtered = bannedWords.checkMessage(rawMessage)
        if (filtered.isBlocked) {
            sender.sendMessage(messages.component("chat.banned-word"))
            return
        }
        if (config.isAntiSpamEnabled && !sender.hasPermission(config.spamBypassPermission) &&
            cooldowns.isOnCooldown(sender.uniqueId, CooldownService.Channel.BUBBLE, config.bubbleCooldown)
        ) {
            sender.sendMessage(messages.component("chat.spam-warning"))
            return
        }
        bubble.onCommand(sender, filtered.processedMessage)
    }

    private fun localRecipients(sender: Player): Set<Player> {
        val radius = config.localChatRadius.toDouble()
        val origin = sender.location
        return Bukkit.getOnlinePlayers()
            .filter { it.world == sender.world && it.location.distanceSquared(origin) <= radius * radius }
            .toHashSet()
    }

    companion object {
        private val MINI: MiniMessage = MiniMessage.miniMessage()

        // Inline prefix/suffix as MiniMessage so an unclosed colour/gradient in them flows into
        // what follows in the format — e.g. a <#hex> suffix recolours {message}. LuckPerms meta may
        // hold legacy codes (&c, §c, &#rrggbb, §x§r§r...) that MiniMessage would print raw (issue #3),
        // so they're converted first; MiniMessage tags in the meta pass through untouched.
        @JvmStatic
        fun inlineMeta(format: String, prefix: String, suffix: String): String = format
            .replace("{prefix}", PrefixFormatter.toMiniMessage(prefix))
            .replace("{suffix}", PrefixFormatter.toMiniMessage(suffix))

        // {player}/{message} stay components so player input is never parsed as MiniMessage,
        // yet they still inherit any open colour from the inlined prefix/suffix.
        @JvmStatic
        fun assemble(inlined: String, player: Component, message: Component, tags: TagResolver): Component =
            MINI.deserialize(inlined, tags)
                .replaceText { it.matchLiteral("{player}").replacement(player) }
                .replaceText { it.matchLiteral("{message}").replacement(message) }
    }
}
