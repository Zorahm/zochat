package zorahm.zochat.advancement

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import zorahm.zochat.config.ConfigFiles
import java.io.File

/** Loads advancements.yml: the master toggle and one format per advancement frame (task/goal/challenge). */
class AdvancementConfig(private val plugin: Plugin) {

    var isEnabled: Boolean = true; private set
    private var formats: Map<String, String> = emptyMap()

    init {
        reload()
    }

    fun reload() {
        val resourcePath = "advancements.yml"
        val target = File(plugin.dataFolder, resourcePath)
        ConfigFiles.saveDefaultIfAbsent(plugin, resourcePath, target)
        // Safe here: the schema is fixed (formats.task/goal/challenge), no user-authored keys to lose.
        ConfigFiles.syncWithDefaults(plugin, resourcePath, target)
        val cfg = YamlConfiguration.loadConfiguration(target)

        isEnabled = cfg.getBoolean("enabled", true)
        formats = FRAMES.associateWith { cfg.getString("formats.$it", "") ?: "" }
    }

    /** Format for a frame name as Paper reports it (TASK/GOAL/CHALLENGE); "" means don't announce. */
    fun format(frame: String): String = formats[frame.lowercase()] ?: formats["task"] ?: ""

    private companion object {
        val FRAMES = listOf("task", "goal", "challenge")
    }
}
