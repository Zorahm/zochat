package zorahm.zochat.bubble

import org.bukkit.Color
import org.bukkit.entity.Display
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import zorahm.zochat.config.ConfigFiles
import java.io.File

class BubbleConfig(private val plugin: Plugin) {

    var isEnabled: Boolean = false; private set
    var trigger: TriggerType = TriggerType.CHAT; private set
    var format: String = "{playerName}: {message}"; private set

    var billboard: Display.Billboard = Display.Billboard.CENTER; private set
    var seeThrough: Boolean = false; private set
    var textShadow: Boolean = false; private set
    var lineWidth: Int = 200; private set
    var headDistance: Double = 0.5; private set
    var scale: Float = 1.0f; private set

    var backgroundColor: Color = Color.BLACK; private set
    var backgroundOpacity: Int = 0; private set

    var timePerSymbol: Double = 0.1; private set
    var minimumTime: Double = 3.0; private set

    var permission: String = ""; private set
    var minSymbols: Int = 1; private set
    var worlds: List<String> = emptyList(); private set
    var seeOwnBubble: Boolean = true; private set

    init {
        reload()
    }

    fun reload() {
        val resourcePath = "bubble.yml"
        val target = File(plugin.dataFolder, resourcePath)
        ConfigFiles.saveDefaultIfAbsent(plugin, resourcePath, target)
        ConfigFiles.syncWithDefaults(plugin, resourcePath, target)
        val cfg = YamlConfiguration.loadConfiguration(target)

        isEnabled = cfg.getBoolean("enabled", true)
        trigger = TriggerType.fromString(cfg.getString("trigger-type"))
        format = cfg.getString("format", "{playerName}: {message}") ?: "{playerName}: {message}"

        billboard = parseBillboard(cfg.getString("billboard"))
        seeThrough = cfg.getBoolean("see-through", false)
        textShadow = cfg.getBoolean("text-shadow", false)
        lineWidth = cfg.getInt("line-width", 200)
        headDistance = cfg.getDouble("head-distance", 0.5)
        scale = cfg.getDouble("scale", 1.0).toFloat()

        backgroundColor = parseColor(cfg.getString("background.color"))
        backgroundOpacity = cfg.getInt("background.opacity", 0).coerceIn(0, 255)

        timePerSymbol = cfg.getDouble("time-per-symbol", 0.1)
        minimumTime = cfg.getDouble("minimum-time", 3.0)

        permission = cfg.getString("requirements.permission", "") ?: ""
        minSymbols = cfg.getInt("requirements.min-symbols", 1)
        worlds = cfg.getStringList("requirements.worlds")
        seeOwnBubble = cfg.getBoolean("requirements.see-own-bubble", true)
    }

    private fun parseBillboard(value: String?): Display.Billboard = when (value?.trim()?.uppercase()) {
        "FIXED" -> Display.Billboard.FIXED
        "VERTICAL" -> Display.Billboard.VERTICAL
        "HORIZONTAL" -> Display.Billboard.HORIZONTAL
        else -> Display.Billboard.CENTER
    }

    private fun parseColor(hex: String?): Color = try {
        Color.fromRGB((hex?.trim()?.removePrefix("#") ?: "000000").toInt(16))
    } catch (e: NumberFormatException) {
        Color.BLACK
    }
}
