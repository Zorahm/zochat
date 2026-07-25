package zorahm.zochat.chat

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import zorahm.zochat.config.ConfigFiles
import java.io.File

/**
 * One `^` placeholder. Built-ins (loc/world/time/health/ping/biome/item) are resolved by
 * [PlaceholderService] from player state; custom ones resolve [value] via PlaceholderAPI and splice
 * it into [format] as `{value}`.
 */
data class PlaceholderDef(
    val name: String,
    val builtin: Boolean,
    val enabled: Boolean,
    val aliases: List<String>,
    val permission: String,
    val format: String,
    val value: String,
)

/** Loads placeholders.yml: the master toggle, built-in/custom definitions and world-name overrides. */
class PlaceholderConfig(private val plugin: Plugin) {

    var isEnabled: Boolean = true
        private set

    private var defs: List<PlaceholderDef> = emptyList()
    private var worldNames: Map<String, String> = emptyMap()

    init {
        reload()
    }

    fun reload() {
        val path = "placeholders.yml"
        val target = File(plugin.dataFolder, path)
        ConfigFiles.saveDefaultIfAbsent(plugin, path, target)
        // No syncWithDefaults: custom placeholder IDs and world-names entries are user-authored keys
        // that the "remove keys not in the default schema" pass would delete.
        val cfg = YamlConfiguration.loadConfiguration(target)

        isEnabled = cfg.getBoolean("enabled", true)

        worldNames = cfg.getConfigurationSection("world-names")?.let { sec ->
            sec.getKeys(false).associateWith { (sec.getString(it) ?: it) }
        } ?: emptyMap()

        val collected = ArrayList<PlaceholderDef>()
        cfg.getConfigurationSection("builtin")?.let { collected += parse(it, builtin = true) }
        cfg.getConfigurationSection("custom")?.let { collected += parse(it, builtin = false) }
        defs = collected
    }

    private fun parse(section: org.bukkit.configuration.ConfigurationSection, builtin: Boolean): List<PlaceholderDef> =
        section.getKeys(false).mapNotNull { name ->
            val s = section.getConfigurationSection(name) ?: return@mapNotNull null
            PlaceholderDef(
                name = name.lowercase(),
                builtin = builtin,
                enabled = s.getBoolean("enabled", true),
                aliases = s.getStringList("aliases"),
                permission = s.getString("permission", "zochat.placeholder.$name") ?: "",
                format = s.getString("format", if (builtin) "" else "{value}") ?: "",
                value = s.getString("value", "") ?: "",
            )
        }

    fun definitions(): List<PlaceholderDef> = defs

    fun worldName(realName: String): String = worldNames[realName] ?: realName
}
