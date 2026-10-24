package zorahm.zochat.presence

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import zorahm.zochat.config.ConfigFiles
import java.io.File

class WelcomeMessages(private val plugin: Plugin) {

    private var cfg: FileConfiguration? = null
    private val mm: MiniMessage = MiniMessage.miniMessage()

    init {
        load()
    }

    fun getWelcomeMessages(playerName: String): List<Component> {
        val current = cfg ?: return emptyList()
        val messages = current.getStringList("messages")
        if (messages.isEmpty()) {
            return emptyList()
        }
        return messages.map { mm.deserialize(it.replace("{player}", playerName)) }
    }

    val isEnabled: Boolean
        get() = cfg?.getBoolean("enabled", false) ?: false

    val delay: Long
        get() = cfg?.getLong("delay", 20L) ?: 20L

    fun reload() {
        load()
    }

    private fun load() {
        val resourcePath = "welcome_messages/welcome_messages.yml"
        val target = File(plugin.dataFolder, resourcePath)
        ConfigFiles.saveDefaultIfAbsent(plugin, resourcePath, target)
        ConfigFiles.syncWithDefaults(plugin, resourcePath, target)
        cfg = YamlConfiguration.loadConfiguration(target)
    }
}
