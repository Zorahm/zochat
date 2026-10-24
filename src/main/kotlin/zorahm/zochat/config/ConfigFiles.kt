package zorahm.zochat.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object ConfigFiles {

    @JvmStatic
    fun saveDefaultIfAbsent(plugin: Plugin, resourcePath: String, target: File) {
        if (!target.exists()) {
            target.parentFile?.mkdirs()
            plugin.saveResource(resourcePath, false)
        }
    }

    // Moves values from renamed keys to their new names before syncWithDefaults deletes the old ones
    // as obsolete. A new key the admin already set wins; the old key is dropped either way.
    @JvmStatic
    fun renameKeys(target: File, renames: Map<String, String>) {
        if (!target.exists()) return
        val current = YamlConfiguration.loadConfiguration(target)
        var changed = false
        for ((oldKey, newKey) in renames) {
            if (!current.contains(oldKey) || current.isConfigurationSection(oldKey)) continue
            if (!current.contains(newKey)) current.set(newKey, current.get(oldKey))
            current.set(oldKey, null)
            changed = true
        }
        if (changed) current.save(target)
    }

    // Sync the user's file to the bundled default schema: add keys present in the default but
    // missing in the user file (keeping the user's existing values), and remove obsolete keys no
    // longer in the default (e.g. settings of removed features). zoChat configs have a fixed
    // schema, so any key not in the default is dead — this replaces the old version-based migration.
    @JvmStatic
    fun syncWithDefaults(plugin: Plugin, resourcePath: String, target: File): Boolean {
        val input = plugin.getResource(resourcePath) ?: return false
        val defaults: FileConfiguration =
            YamlConfiguration.loadConfiguration(InputStreamReader(input, StandardCharsets.UTF_8))
        val current: FileConfiguration = YamlConfiguration.loadConfiguration(target)

        var changed = false
        // Add missing keys, keeping existing user values. getKeys() snapshots the map, so the
        // set() below can't disturb this iteration.
        for (key in defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) {
                continue
            }
            if (!current.contains(key)) {
                current.set(key, defaults.get(key))
                changed = true
            }
        }
        // Remove obsolete keys not present in the default schema.
        for (key in current.getKeys(true)) {
            if (!defaults.contains(key)) {
                current.set(key, null)
                changed = true
            }
        }
        if (changed) {
            try {
                current.save(target)
            } catch (e: IOException) {
                plugin.logger.warning("Failed to sync ${target.name} with defaults: ${e.message}")
            }
        }
        return changed
    }
}
