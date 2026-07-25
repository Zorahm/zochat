package zorahm.zochat.guard

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import zorahm.zochat.config.ConfigFiles
import java.io.File

class CommandGuardConfig(private val plugin: Plugin) {

    var isEnabled: Boolean = true; private set
    var hideFromTab: Boolean = true; private set
    var notifyMessage: Boolean = true; private set
    var consoleLog: Boolean = true; private set
    var playSound: Boolean = true; private set
    var sound: String = "block.note_block.bass"; private set
    lateinit var guard: CommandGuard; private set

    init {
        reload()
    }

    fun reload() {
        val resourcePath = "commands.yml"
        val target = File(plugin.dataFolder, resourcePath)
        ConfigFiles.saveDefaultIfAbsent(plugin, resourcePath, target)
        // Safe here: `blocked` is a fixed key whose value is a user-edited list, not user-authored
        // keys — so add-missing/remove-obsolete never touches the list contents (unlike placeholders.yml).
        ConfigFiles.syncWithDefaults(plugin, resourcePath, target)
        val cfg = YamlConfiguration.loadConfiguration(target)

        isEnabled = cfg.getBoolean("enabled", true)
        val blockNamespaced = cfg.getBoolean("block-namespaced", true)
        hideFromTab = cfg.getBoolean("hide-from-tab", true)
        notifyMessage = cfg.getBoolean("notify.message", true)
        consoleLog = cfg.getBoolean("notify.console-log", true)
        playSound = cfg.getBoolean("notify.sound", true)
        sound = cfg.getString("sound", "block.note_block.bass") ?: "block.note_block.bass"
        guard = CommandGuard(cfg.getStringList("blocked"), blockNamespaced)
    }
}
