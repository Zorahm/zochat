package zorahm.zochat.config

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File

class Messages(private val plugin: Plugin, language: String?) {

    private val mm: MiniMessage = MiniMessage.miniMessage()
    private lateinit var cfg: FileConfiguration
    private lateinit var fileName: String

    init {
        load(language)
    }

    fun load(language: String?) {
        val lang = languageOf(language)
        if (language != null && !language.equals(lang, ignoreCase = true)) {
            plugin.logger.warning("Unknown language '$language', falling back to '$lang'")
        }
        fileName = fileFor(lang)
        val target = File(plugin.dataFolder, fileName)
        ConfigFiles.saveDefaultIfAbsent(plugin, fileName, target)
        ConfigFiles.syncWithDefaults(plugin, fileName, target)
        cfg = YamlConfiguration.loadConfiguration(target)
    }

    operator fun get(key: String): String {
        val v = cfg.getString(key)
        if (v == null) {
            plugin.logger.warning("Missing message key: $key in $fileName")
            return "<red>Missing message: $key</red>"
        }
        return v
    }

    fun component(key: String): Component = mm.deserialize(get(key))

    fun getList(key: String): List<Component> = cfg.getStringList(key).map { mm.deserialize(it) }

    companion object {
        @JvmStatic
        fun languageOf(configured: String?): String =
            configured?.lowercase()?.takeIf { it == "ru" || it == "en" } ?: "en"

        @JvmStatic
        fun fileFor(language: String): String = "messages/messages_$language.yml"
    }
}
