package zorahm.zochat.discord

import github.scarsz.discordsrv.DiscordSRV
import github.scarsz.discordsrv.api.ListenerPriority
import github.scarsz.discordsrv.api.Subscribe
import github.scarsz.discordsrv.api.events.DiscordGuildMessagePreProcessEvent
import github.scarsz.discordsrv.dependencies.jda.api.entities.Role
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import zorahm.zochat.chat.BannedWordsFilter
import zorahm.zochat.chat.ChatChannel
import zorahm.zochat.config.ChatConfig
import zorahm.zochat.config.Messages

/**
 * The only class that touches DiscordSRV, and it's created only when DiscordSRV is enabled — like
 * PapiHook, so servers without it never resolve the github.scarsz classes.
 */
class DiscordSrvHook(
    private val plugin: Plugin,
    private val config: ChatConfig,
    private val messages: Messages,
    private val bannedWords: BannedWordsFilter,
) : DiscordBridge {

    private val listener = Listener()

    fun start() {
        DiscordSRV.api.subscribe(listener)
    }

    override fun close() {
        DiscordSRV.api.unsubscribe(listener)
    }

    override fun relay(player: Player, channel: ChatChannel, message: String) {
        if (!config.isDiscordEnabled) return
        val gameChannel = DiscordBridge.gameChannel(channel, config.discordSettings) ?: return
        // cancelled = false: zoChat cancels every chat event itself, which DiscordSRV would take as
        // "another plugin blocked this message" (RespectChatPlugins) and drop it.
        DiscordSRV.getPlugin().processChatMessage(player, message, gameChannel, false, null)
    }

    // Public with a public method: DiscordSRV finds @Subscribe handlers by reflection.
    inner class Listener {
        // Fires once per message, after DiscordSRV's own checks (linked channel, bots, blocked users
        // and roles) and before it splits attachments into separate broadcasts — so cancelling it
        // here hands the whole message, attachments included, to zoChat's format.
        @Subscribe(priority = ListenerPriority.NORMAL)
        fun onDiscordMessage(event: DiscordGuildMessagePreProcessEvent) {
            if (event.isCancelled || !config.isDiscordEnabled || !config.isDiscordFormatEnabled) return
            val srv = DiscordSRV.getPlugin()
            // DiscordSRV only broadcasts its global channel to players without a chat-plugin hook;
            // other mapped channels (staff etc.) are left exactly as DiscordSRV handles them.
            if (srv.getDestinationGameChannelNameForTextChannel(event.channel) != config.discordSettings.globalChannel) return
            event.isCancelled = true

            val member = event.member
            val name = member?.effectiveName ?: event.author.name
            val role = member?.let { srv.getSelectedRoles(it).firstOrNull() }?.let {
                DiscordMessageFormat.Role(it.name, it.colorRaw.takeIf { raw -> raw != Role.DEFAULT_COLOR_RAW })
            }
            val text = event.message.contentDisplay
            val attachments = event.message.attachments.map { it.fileName to it.url }
            // DiscordSRV calls this on a JDA thread; the filter reads config and players get messaged
            // from the main thread.
            Bukkit.getScheduler().runTask(plugin, Runnable { deliver(name, role, text, attachments) })
        }
    }

    private fun deliver(name: String, role: DiscordMessageFormat.Role?, text: String, attachments: List<Pair<String, String>>) {
        // Discord users get the same banned-words filter as players; a blocked message stays in Discord.
        val filtered = bannedWords.checkMessage(text)
        if (filtered.isBlocked) return
        if (filtered.processedMessage.isBlank() && attachments.isEmpty()) return
        val body = DiscordMessageFormat.body(filtered.processedMessage, attachments, config.discordMaxLength)
        val line = DiscordMessageFormat.render(messages.get("formats.discord"), name, role, body)
        Bukkit.getOnlinePlayers().forEach { it.sendMessage(line) }
        Bukkit.getConsoleSender().sendMessage(line)
    }
}
